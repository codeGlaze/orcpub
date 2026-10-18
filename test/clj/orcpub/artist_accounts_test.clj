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

(def fuss {:house-pack {:account "artist@example.test" :username "fusspot"}})

(deftest config-parsing
  (let [c (aa/parse-config (str "{\"house-pack\": {\"name\": \"Fusspot\", \"account\": \" Artist@Example.TEST \","
                                " \"preferred_name\": \"Fuss\", \"welcome_note\": \"We did it!\"},"
                                " \"gone\": {\"account\": null},"
                                " \"credit-only\": {\"name\": \"Someone\"}}"))]
    (is (= {:account (clojure.string/lower-case "artist@example.TEST") :preferred-name "Fuss" :welcome-note "We did it!"}
           (:house-pack c))
        "emails are trimmed and lower-cased the way registration stores them, so the two always match")
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
          (is (< 6.9 days 7.1) "the welcome link lasts a week, not the usual two hours")))
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
      (add-user! conn "fusspotart" "Artist@example.test" true)
      (is (= {:house-pack :linked} (aa/reconcile! conn fuss notify)))
      (is (= :house-pack (artist-of conn "fusspotart")) "email matching ignores case")
      (is (= [[:upgraded :house-pack "fusspotart"]] @calls))
      (is (= ["fusspotart"] (users conn)) "no second account is made"))))

(deftest an-unconfirmed-account-is-claimed-by-the-welcome
  (with-conn conn
    (let [sent (atom [])
          notify {:created (fn [m] (swap! sent conj m)) :upgraded (fn [_]) :unlinked (fn [_])}
          id (add-user! conn "fusspotart" "artist@example.test" false)]
      @(d/transact conn [{:db/id id :orcpub.user/verification-key "pending-confirm"
                          :orcpub.user/verification-sent (Date.)}])
      (testing "whoever registered her address chose its password, and anyone can resend its
                confirmation, so confirming cannot be what hands over her credit"
        (is (= {:house-pack :claimed} (aa/reconcile! conn fuss notify)))
        (let [u (pull-user conn "fusspotart")]
          (is (= :house-pack (:orcpub.user/artist u)))
          (is (not (hashers/check "pw123456" (:orcpub.user/password u))) "the registrant's password is gone")
          (is (nil? (:orcpub.user/verification-key u)) "and so is their confirmation link")
          (is (false? (:orcpub.user/verified? u)))
          (is (= (:db/id u) (:db/id (routes/first-user-by (d/db conn) routes/user-by-password-reset-key-query
                                                          (routes/hash-reset-key (:reset-key (first @sent))))))
              "the welcome sent to the address is what opens it")
          (is (some? (:orcpub.user/artist-welcome-sent u)))))
      (is (= {:house-pack :already} (aa/reconcile! conn fuss notify)))
      (is (= 1 (count @sent))))))

(deftest a-welcome-that-did-not-go-out-is-sent-again
  (with-conn conn
    (let [tries (atom [])
          deliver? (atom false)
          notify {:created (fn [m] (swap! tries conj (:reset-key m)) @deliver?)
                  :upgraded (fn [_]) :unlinked (fn [_])}]
      (is (= {:house-pack :created} (aa/reconcile! conn fuss notify)))
      (is (nil? (:orcpub.user/artist-welcome-sent (pull-user conn "fusspot"))) "the mail server refused it")
      (reset! deliver? true)
      (is (= {:house-pack :resent} (aa/reconcile! conn fuss notify)) "the next start tries again")
      (is (not= (first @tries) (second @tries)) "with a new key, since the first is not kept")
      (is (= (:db/id (pull-user conn "fusspot"))
             (:db/id (routes/first-user-by (d/db conn) routes/user-by-password-reset-key-query
                                           (routes/hash-reset-key (second @tries)))))
          "and the new key is the one that works")
      (is (= {:house-pack :already} (aa/reconcile! conn fuss notify)) "once sent, never again")
      (is (= 2 (count @tries)))
      (is (= ["fusspot"] (users conn))))))

(deftest only-a-lost-race-is-taken-for-one
  (let [lost-race? @#'aa/lost-race?]
    (is (not (lost-race? (ex-info "because the case was wrong" {}))) "words that merely contain cas")
    (is (not (lost-race? (Exception. "unique words" (ex-info "compare" {})))))
    (with-conn conn
      (let [id (add-user! conn "a" "a@example.test" true)
            caught (try @(d/transact conn [[:db/cas id :orcpub.user/artist :x :y]]) nil
                        (catch Exception e e))]
        (is (lost-race? caught) "a real cas refusal")
        (is (false? (@#'aa/try-transact conn [[:orcpub.fn/retract-if-equal id :orcpub.user/artist :x]]))
            "and the guarded removal's")
        (is (thrown? Exception (@#'aa/try-transact conn [{:db/id id :orcpub.user/email 42}]))
            "anything else is thrown, not swallowed as a race")))))

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
      (add-user! conn "fusspotart" "artist@example.test" true)
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
          id (add-user! conn "shared" "artist@example.test" true)]
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

(deftest a-first-confirmation-does-not-link-the-account
  (testing "the registrant chose the password; confirming proves only the address"
    (with-conn conn
      (let [id (add-user! conn "fusspotart" "artist@example.test" false)]
        @(d/transact conn [{:db/id id
                            :orcpub.user/verification-key "k-123"
                            :orcpub.user/verification-sent (Date.)}])
        (with-redefs [aa/configured (constantly fuss)]
          (routes/verify {:query-params {:key "k-123"} :db (d/db conn) :conn conn}))
        (is (true? (:orcpub.user/verified? (pull-user conn "fusspotart"))))
        (is (nil? (artist-of conn "fusspotart")))))))

(deftest confirming-a-change-to-the-configured-address-links-the-account
  (with-conn conn
    (let [id (add-user! conn "fusspotart" "old@example.test" true)]
      @(d/transact conn [{:db/id id
                          :orcpub.user/pending-email "artist@example.test"
                          :orcpub.user/verification-key "k-456"
                          :orcpub.user/verification-sent (Date.)}])
      (with-redefs [aa/configured (constantly fuss)]
        (routes/verify {:query-params {:key "k-456"} :db (d/db conn) :conn conn}))
      (is (= :house-pack (artist-of conn "fusspotart"))))))

(deftest a-welcome-link-works-with-hashed-reset-keys
  ;; Reset keys are stored as a digest and looked up by it; a welcome link
  ;; written any other way is a link the reset page cannot find. It also has
  ;; its own week-long expiry, which the two-hour rule must not cut short.
  (with-conn conn
    (let [sent (atom nil)
          notify {:created (fn [m] (reset! sent m)) :upgraded (fn [_]) :unlinked (fn [_])}]
      (aa/reconcile! conn fuss notify)
      (let [emailed (:reset-key @sent)
            u (pull-user conn "fusspot")]
        (is (string? emailed))
        (is (not= emailed (:orcpub.user/password-reset-key u)) "the table holds a digest, not the emailed key")
        (is (= (:db/id u) (:db/id (routes/first-user-by (d/db conn) routes/user-by-password-reset-key-query
                                                        (routes/hash-reset-key emailed))))
            "the reset page finds the account from the key in the email")
        (let [three-hours-ago (Date. (- (System/currentTimeMillis) (* 3 3600 1000)))
              aged (assoc u :orcpub.user/password-reset-sent three-hours-ago)]
          (is (not (routes/reset-link-expired? aged)) "three hours on, past an ordinary link's two, it still works")
          (is (routes/reset-link-expired? (dissoc aged :orcpub.user/password-reset-expires))
              "an ordinary link of the same age has expired"))))))

(deftest a-username-keeps-every-letter-whatever-the-locale
  ;; a Turkish JVM lower-cases I to a dotless i, which the letters-only filter then drops
  (is (= "irisfusspot" (aa/username-base "IRIS Fusspot"))))
