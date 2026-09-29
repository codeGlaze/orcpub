(ns orcpub.artist-accounts-test
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [buddy.hashers :as hashers]
            [orcpub.artist-accounts :as aa]
            [orcpub.crypto :as crypto]
            [orcpub.db.schema :as schema]
            [orcpub.routes :as routes])
  (:import [java.util UUID Base64 Date]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:artist-accounts-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try @(d/transact ~conn-binding schema/all-schemas)
          ~@body
          (finally (d/delete-database uri#)))))

(defn- recorder
  "A notify map that records every call instead of emailing."
  []
  (let [calls (atom [])]
    [calls (into {} (for [k [:created :upgraded :unlinked]]
                      [k (fn [m] (swap! calls conj [k (:artist-id m)
                                                    (get-in m [:user :orcpub.user/username])]))]))]))

(defn- add-user! [conn username email verified?]
  @(d/transact conn [{:orcpub.user/username username
                      :orcpub.user/email email
                      :orcpub.user/password (hashers/encrypt "pw123456")
                      :orcpub.user/verified? verified?}])
  (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) username))

(defn- artist-of [conn username]
  (d/q '[:find ?a . :in $ ?u :where [?e :orcpub.user/username ?u] [?e :orcpub.user/artist ?a]]
       (d/db conn) username))

(defn- pull-user [conn username]
  (d/pull (d/db conn) '[*]
          (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) username)))

(defn- users [conn]
  (d/q '[:find [?u ...] :where [_ :orcpub.user/username ?u]] (d/db conn)))

(def fuss {:house-pack {:account "hello@fusspot.rip" :username "fusspot"}})

(deftest config-parsing
  (let [c (aa/parse-config (str "{\"house-pack\": {\"name\": \"Fusspot\", \"account\": \" Hello@Fusspot.RIP \","
                                " \"preferred_name\": \"Fuss\", \"welcome_note\": \"We did it!\"},"
                                " \"gone\": {\"account\": null},"
                                " \"credit-only\": {\"name\": \"Someone\"}}"))]
    (is (= {:account "hello@fusspot.rip" :preferred-name "Fuss" :welcome-note "We did it!"}
           (:house-pack c)) "emails are trimmed and lower-cased")
    (is (= :revoke (get-in c [:gone :account])) "an explicit null revokes")
    (is (not (contains? c :credit-only)) "an entry with no account key is credit-only")
    (is (nil? (aa/parse-config "{not json")) "malformed config is ignored, not fatal")))

(deftest a-missing-account-is-created-locked-and-linked
  (with-conn conn
    (let [[calls notify] (recorder)
          before (Date.)]
      (is (= {:house-pack :created} (aa/reconcile! conn fuss notify)))
      (is (= :house-pack (artist-of conn "fusspot")))
      (let [u (pull-user conn "fusspot")]
        (is (false? (:orcpub.user/verified? u)) "confirmed only once she uses the emailed link")
        (is (string? (:orcpub.user/password-reset-key u)))
        (let [days (/ (- (.getTime (:orcpub.user/password-reset-expires u)) (.getTime before))
                      (* 24 60 60 1000.0))]
          (is (< 6.9 days 7.1) "the welcome link lasts a week, not the usual 24 hours")))
      (is (= [[:created :house-pack "fusspot"]] @calls)))))

(deftest restarts-never-resend
  (with-conn conn
    (let [[calls notify] (recorder)]
      (aa/reconcile! conn fuss notify)
      (is (= {:house-pack :already} (aa/reconcile! conn fuss notify)))
      (is (= {:house-pack :already} (aa/reconcile! conn fuss notify)))
      (is (= 1 (count @calls)))
      (is (= ["fusspot"] (users conn))))))

(deftest two-containers-starting-at-once-make-one-account
  (with-conn conn
    (let [[calls notify] (recorder)
          spec (:house-pack fuss)]
      (is (= :created (aa/create! conn :house-pack spec notify)))
      (is (= :raced (aa/create! conn :house-pack spec notify))
          "the second creation fails on the unique claim instead of duplicating")
      (is (= 1 (count (users conn))))
      (is (= 1 (count @calls))))))

(deftest a-confirmed-account-is-upgraded
  (with-conn conn
    (let [[calls notify] (recorder)]
      (add-user! conn "fusspotart" "Hello@fusspot.rip" true)
      (is (= {:house-pack :linked} (aa/reconcile! conn fuss notify)))
      (is (= :house-pack (artist-of conn "fusspotart")) "email matching ignores case")
      (is (= [[:upgraded :house-pack "fusspotart"]] @calls))
      (is (= ["fusspotart"] (users conn)) "no second account is made"))))

(deftest an-unconfirmed-account-links-when-confirmed
  (with-conn conn
    (let [[calls notify] (recorder)
          id (add-user! conn "fusspotart" "hello@fusspot.rip" false)]
      (testing "someone signing up with her address cannot inherit her credit
                until they prove the inbox is theirs"
        (is (= {:house-pack :waiting} (aa/reconcile! conn fuss notify)))
        (is (nil? (artist-of conn "fusspotart"))))
      @(d/transact conn [{:db/id id :orcpub.user/verified? true}])
      (is (= :linked (aa/on-email-confirmed! conn id fuss notify)))
      (is (= :house-pack (artist-of conn "fusspotart")))
      (is (= [[:upgraded :house-pack "fusspotart"]] @calls)))))

(deftest changing-the-configured-account-moves-the-link
  (with-conn conn
    (let [[calls notify] (recorder)]
      (add-user! conn "old" "old@example.test" true)
      (add-user! conn "new" "new@example.test" true)
      (aa/reconcile! conn {:house-pack {:account "old@example.test"}} notify)
      (aa/reconcile! conn {:house-pack {:account "new@example.test"}} notify)
      (is (= :house-pack (artist-of conn "new")))
      (is (nil? (artist-of conn "old")) "the config is the one record of who speaks for her")
      (is (= [[:upgraded :house-pack "old"] [:unlinked :house-pack "old"] [:upgraded :house-pack "new"]]
             (sort-by #(case (first %) :upgraded (if (= "old" (last %)) 0 2) 1) @calls))))))

(deftest null-revokes-and-removal-does-not
  (with-conn conn
    (let [[calls notify] (recorder)]
      (add-user! conn "fusspotart" "hello@fusspot.rip" true)
      (aa/reconcile! conn fuss notify)
      (testing "deleting the entry from the config changes nothing"
        (is (= {} (aa/reconcile! conn {} notify)))
        (is (= :house-pack (artist-of conn "fusspotart"))))
      (testing "account null unlinks and says so"
        (is (= {:house-pack :revoked} (aa/reconcile! conn {:house-pack {:account :revoke}} notify)))
        (is (nil? (artist-of conn "fusspotart")))
        (is (= [:unlinked :house-pack "fusspotart"] (last @calls)))))))

(deftest unlinking-twice-fails-the-second-time
  (testing "the guard that stops two containers both emailing an unlink"
    (with-conn conn
      (let [id (add-user! conn "a" "a@example.test" true)]
        @(d/transact conn [{:db/id id :orcpub.user/artist :house-pack}])
        @(d/transact conn [[:orcpub.fn/retract-if-equal id :orcpub.user/artist :house-pack]])
        (is (thrown? Exception
                     @(d/transact conn [[:orcpub.fn/retract-if-equal id :orcpub.user/artist :house-pack]])))))))

(deftest an-account-speaking-for-another-artist-is-left-alone
  (with-conn conn
    (let [[calls notify] (recorder)
          id (add-user! conn "shared" "hello@fusspot.rip" true)]
      @(d/transact conn [{:db/id id :orcpub.user/artist :someone-else}])
      (is (= {:house-pack :other-artist} (aa/reconcile! conn fuss notify)))
      (is (= :someone-else (artist-of conn "shared")))
      (is (empty? @calls)))))

(deftest unknown-artists-are-skipped
  (with-conn conn
    (let [[calls notify] (recorder)]
      (is (= {:nobody :unknown-artist}
             (aa/reconcile! conn {:nobody {:account "x@example.test"}} notify)))
      (is (empty? (users conn))))))

(deftest a-taken-username-gets-a-number
  (with-conn conn
    (let [[_ notify] (recorder)]
      (add-user! conn "fusspot" "someone@example.test" true)
      (aa/reconcile! conn fuss notify)
      (is (= :house-pack (artist-of conn "fusspot2"))))))

(deftest the-preferred-name-is-stored-encrypted
  (with-conn conn
    (let [[_ notify] (recorder)
          ks (crypto/parse-keys (str "k1:" (.encodeToString (Base64/getEncoder) (byte-array 32 (byte 7)))))]
      (with-redefs [crypto/configured-keys (constantly ks)]
        (aa/reconcile! conn {:house-pack (assoc (:house-pack fuss) :preferred-name "Fuss")} notify)
        (let [u (pull-user conn "fusspot")]
          (is (not= "Fuss" (:orcpub.user/preferred-name u)) "not stored readable")
          (is (= "Fuss" (aa/preferred-name u)) "but the site can read it back"))))))

(defn- days-ago [n] (Date. (- (System/currentTimeMillis) (* n 24 60 60 1000))))
(defn- days-ahead [n] (Date. (+ (System/currentTimeMillis) (* n 24 60 60 1000))))

(deftest a-welcome-link-outlasts-an-ordinary-reset
  (testing "an ordinary reset link still expires after 24 hours"
    (is (routes/reset-link-expired? {:orcpub.user/password-reset-sent (days-ago 2)})))
  (testing "a welcome link sent two days ago is still good for the rest of its week"
    (is (not (routes/reset-link-expired? {:orcpub.user/password-reset-sent (days-ago 2)
                                           :orcpub.user/password-reset-expires (days-ahead 5)}))))
  (testing "and does expire at the end of it"
    (is (routes/reset-link-expired? {:orcpub.user/password-reset-sent (days-ago 8)
                                      :orcpub.user/password-reset-expires (days-ago 1)}))))

(deftest confirming-the-configured-address-links-the-account
  (with-conn conn
    (let [id (add-user! conn "fusspotart" "hello@fusspot.rip" false)]
      @(d/transact conn [{:db/id id
                          :orcpub.user/verification-key "k-123"
                          :orcpub.user/verification-sent (Date.)}])
      (with-redefs [aa/configured (constantly fuss)]
        (routes/verify {:query-params {:key "k-123"} :db (d/db conn) :conn conn}))
      (is (= :house-pack (artist-of conn "fusspotart"))))))
