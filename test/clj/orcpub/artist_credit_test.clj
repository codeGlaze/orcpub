(ns orcpub.artist-credit-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [datomic.api :as d]
            [orcpub.artist-credit :as ac]
            [orcpub.fork.branding :as branding]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.db.schema :as schema]
            [orcpub.crypto]
            [orcpub.artist-email]
            [orcpub.heartbeat]
            [orcpub.routes])
  (:import [java.util UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:artist-credit-test-" (UUID/randomUUID))
         ~conn-binding (do (d/create-database uri#) (d/connect uri#))]
     (try @(d/transact ~conn-binding schema/all-schemas)
          ~@body
          (finally (d/delete-database uri#)))))

(use-fixtures :each
  (fn [t]
    (let [before (pa/current-overrides)]
      (try (t) (finally (pa/set-artist-overrides! before))))))

(defn- add-artist! [conn username artist]
  @(d/transact conn [(cond-> {:orcpub.user/username username
                               :orcpub.user/email (str username "@example.test")}
                        artist (assoc :orcpub.user/artist artist))])
  (d/q '[:find ?e . :in $ ?u :where [?e :orcpub.user/username ?u]] (d/db conn) username))

(deftest links-must-be-https
  (is (nil? (ac/url-problem "https://fusspot.rip/")))
  (is (= "Links must start with https://" (ac/url-problem "http://fusspot.rip/")))
  (is (= "Links must start with https://" (ac/url-problem "fusspot.rip")))
  (is (= "Links must start with https://" (ac/url-problem "javascript:alert(1)")))
  (is (some? (ac/url-problem "https://user@evil.test/")) "no credentials-in-URL tricks")
  (is (some? (ac/url-problem "https://evil.test\\x.twitch.tv/"))
      "a backslash is a path separator to a browser: this opens evil.test, not Twitch")
  (is (some? (ac/url-problem "https://evil.test%2f.twitch.tv/")) "nor an encoded one")
  (is (nil? (ac/url-problem "https://xn--bcher-kva.example/")) "an international domain in its ASCII form is fine"))

(deftest the-icon-comes-from-the-link
  (is (= "twitch" (ac/icon-for-url "https://www.twitch.tv/fusspot")))
  (is (= "bluesky" (ac/icon-for-url "https://bsky.app/profile/fusspot.rip")))
  (is (= "kofi" (ac/icon-for-url "https://ko-fi.com/fusspot")))
  (is (= "site" (ac/icon-for-url "https://fusspot.rip/")))
  (is (= "site" (ac/icon-for-url "https://twitch.tv.evil.test/fusspot"))
      "a lookalike host gets the plain globe, not the Twitch mark"))

(deftest checking-an-edit
  (testing "a good edit is stored with each link's own icon, label and colour"
    (is (= {:credit {:artist/name "Fuss"
                     :artist/links [{:link/label "Twitch" :link/icon "twitch"
                                     :link/color "#9146ff" :link/url "https://www.twitch.tv/fusspot"}
                                    {:link/label "Site" :link/icon "site"
                                     :link/color "#f0a100" :link/url "https://fusspot.rip/"}]}}
           (ac/check {:name "  Fuss " :link ""
                      :links [{:url "https://www.twitch.tv/fusspot"}
                              {:url " "}
                              {:icon "twitch" :url "https://fusspot.rip/"}]}))
        "blank rows drop out, and a claimed icon is ignored"))
  (testing "problems are named per field"
    (let [{:keys [errors]} (ac/check {:name (apply str (repeat 61 "x"))
                                      :link "ftp://x.test"
                                      :links [{:url "http://myspace.com/x"}]})]
      (is (contains? errors :name))
      (is (contains? errors :link))
      (is (= {0 "Links must start with https://"} (:link-errors errors)))))
  (is (= {:links "Up to 4 links"}
         (:errors (ac/check {:links (repeat 5 {:url "https://a.test/"})})))))

(deftest stored-values-are-rechecked-on-the-way-out
  (is (= {:artist/name "Fuss"}
         (ac/read-stored (pr-str {:artist/name "Fuss"
                                  :artist/link "javascript:alert(1)"
                                  :artist/links [{:link/icon "site" :link/url "data:text/html,x"}]}))))
  (is (= "site" (-> (ac/read-stored (pr-str {:artist/links [{:link/icon "twitch" :link/url "https://evil.test/"}]}))
                    :artist/links first :link/icon))
      "a stored icon that disagrees with its link is re-derived")
  (is (nil? (ac/read-stored "#=(java.lang.System/exit 0)")) "no reader evaluation")
  (is (nil? (ac/read-stored "not edn {"))))

(deftest config-beats-the-artist-beats-the-code
  (with-conn conn
    (let [id (add-artist! conn "fusspot" :house-pack)]
      (is (:saved (ac/save! conn id {:name "Fuss" :link "https://fuss.example/"})))
      (testing "the artist's edit shows"
        (is (= "Fuss" (:artist/name (pa/artist-info :house-pack))))
        (is (= "https://fuss.example/" (:artist/link (pa/artist-info :house-pack)))))
      (testing "and the deployment's config wins, field by field"
        (with-redefs [branding/portrait-artists {:house-pack {:artist/name "Fusspot (config)"}}]
          @(d/transact conn [{:db/id id :orcpub.user/username "fusspot"}]) ; move the db on
          (ac/refresh! (d/db conn))
          (is (= "Fusspot (config)" (:artist/name (pa/artist-info :house-pack))))
          (is (= "https://fuss.example/" (:artist/link (pa/artist-info :house-pack))))
          (is (= #{:artist/name} (:locked (ac/credit-for-account (d/db conn) id)))
              "the settings page can say which fields the site has fixed")))
      (testing "clearing the edit goes back to the code's default"
        (ac/save! conn id {})
        (is (= "Fusspot" (:artist/name (pa/artist-info :house-pack))))))))

(deftest only-artists-can-save-and-bad-edits-change-nothing
  (with-conn conn
    (let [plain (add-artist! conn "someone" nil)
          fuss (add-artist! conn "fusspot" :house-pack)]
      (is (:errors (ac/save! conn plain {:name "Me"})))
      (is (:errors (ac/save! conn fuss {:link "http://insecure.test"})))
      (is (nil? (:own (ac/credit-for-account (d/db conn) fuss))))
      (ac/save! conn fuss {:name "Fuss"})
      (is (:unchanged (ac/save! conn fuss {:name "Fuss"})) "so no change email for a no-op save"))))

(deftest an-edit-saved-elsewhere-reaches-this-container
  (with-conn conn
    (let [id (add-artist! conn "fusspot" :house-pack)]
      (ac/refresh! (d/db conn))
      ;; another container writes it directly
      @(d/transact conn [{:db/id id :orcpub.user/artist-credit (pr-str {:artist/name "Fuss"})}])
      (ac/refresh! (d/db conn))
      (is (= "Fuss" (:artist/name (pa/artist-info :house-pack)))))))

;; ---------------------------------------------------------------------------
;; The account endpoints
;; ---------------------------------------------------------------------------

(def test-keys
  (orcpub.crypto/parse-keys
   (str "k1:" (.encodeToString (java.util.Base64/getEncoder) (byte-array 32 (byte 3))))))

(defn- req [conn username params]
  {:db (d/db conn) :conn conn :identity {:user username} :transit-params params})

(deftest an-artist-saves-their-credit-and-both-sides-hear
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [told (atom [])]
      (with-redefs [orcpub.artist-email/credit-changed-tx #(do (swap! told conj %) [])
                    clojure.core/future-call (fn [f] (f) (delay nil))]
        (let [bad (orcpub.routes/update-artist-credit
                   (req conn "fusspot" {:link "http://x.test"}))
              good (orcpub.routes/update-artist-credit
                    (req conn "fusspot" {:name "Fuss"
                                         :links [{:url "https://twitch.tv/fusspot"}]}))
              again (orcpub.routes/update-artist-credit
                     (req conn "fusspot" {:name "Fuss"
                                          :links [{:url "https://twitch.tv/fusspot"}]}))]
          (is (= 400 (:status bad)))
          (is (= "Links must start with https://" (get-in bad [:body :errors :link])))
          (is (= 200 (:status good)))
          (is (= "Fuss" (get-in good [:body :current :artist/name])))
          (is (= 200 (:status again)))
          (is (= 1 (count @told)) "one notice per real change, none for a failed or no-op save")
          (is (= {:artist/name "Fuss"} (select-keys (:after (first @told)) [:artist/name]))))))))

(def ^:private t0 (java.util.Date. 1760000000000))
(defn- at "`m` minutes after t0." [m] (java.util.Date. (+ (.getTime ^java.util.Date t0) (* m 60000))))

(defn- flush!
  "Send the credit-change batches that are due on `conn` at time `t`; nothing when there is no sender."
  [conn t]
  (when-let [f (try (requiring-resolve 'orcpub.artist-credit-notices/flush-due!) (catch Exception _ nil))]
    (with-redefs [orcpub.heartbeat/now (constantly t)] (f conn))))

(defmacro ^:private with-emails
  "Run `body` with credit emails collected in `sent` (as [minute before-name after-name]) instead of sent."
  [sent clock & body]
  `(with-redefs [orcpub.artist-email/credit-changed-tx
                 (fn [e#] (swap! ~sent conj [@~clock (:artist/name (:before e#)) (:artist/name (:after e#))]) [])
                 orcpub.heartbeat/now (fn [] (at @~clock))
                 clojure.core/future-call (fn [f#] (f#) (delay nil))]
     ~@body))

(defn- save-name!
  "Save the credit name `n` as fusspot through the route; returns the response."
  [conn n]
  (orcpub.routes/update-artist-credit (req conn "fusspot" {:name n})))

(deftest a-single-change-is-emailed-at-once
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [sent (atom []) clock (atom 0)]
      (with-emails sent clock
        (is (= 200 (:status (save-name! conn "Fuss One"))))
        (is (= [[0 nil "Fuss One"]] @sent) "straight away, to the artist and the admin")
        (reset! clock 30) (flush! conn (at 30))
        (is (= 1 (count @sent)) "and nothing follows it")))))

(deftest a-burst-is-one-email-now-and-one-batch
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [sent (atom []) clock (atom 0)]
      (with-emails sent clock
        (save-name! conn "One")
        (reset! clock 1) (is (= 200 (:status (save-name! conn "Two"))) "a change in the window is saved")
        (reset! clock 2) (save-name! conn "Three")
        (is (= [[0 nil "One"]] @sent) "only the first went at once")
        (flush! conn (at 9))
        (is (= 1 (count @sent)) "the batch waits for the window to end")
        (flush! conn (at 10))
        (is (= [[0 nil "One"] [0 "One" "Three"]] (mapv #(assoc % 0 0) @sent))
            "one email for the rest, from before the first of them to the last")
        (flush! conn (at 11))
        (is (= 2 (count @sent)) "sent once")))))

(deftest editing-more-cannot-hold-the-batch-back
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [sent (atom []) clock (atom 0)]
      (with-emails sent clock
        (save-name! conn "A")
        (doseq [[m n] [[4 "B"] [8 "C"]]] (reset! clock m) (save-name! conn n))
        (flush! conn (at 10))
        (is (= 2 (count @sent)) "the window ends ten minutes after the first change, however many follow")
        (reset! clock 12) (save-name! conn "D")
        (is (= 3 (count @sent)) "the next change after it is a first change again: emailed at once")))))

(deftest a-batch-is-sent-once-whoever-flushes
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [sent (atom []) clock (atom 0)]
      (with-emails sent clock
        (save-name! conn "A") (reset! clock 1) (save-name! conn "B")
        (let [fs (doall (repeatedly 3 #(future (flush! conn (at 10)))))] (run! deref fs))
        (is (= 2 (count @sent)) "three containers flushing at once send it once")))))

(deftest saves-are-capped-against-abuse
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (let [sent (atom []) clock (atom 0)]
      (with-emails sent clock
        (dotimes [i 60] (save-name! conn (str "N" i)))
        (let [r (save-name! conn "one too many")]
          (is (= 429 (:status r)))
          (is (some? (get-in r [:headers "Retry-After"]))))
        (reset! clock 61)
        (is (= 200 (:status (save-name! conn "an hour on"))))))))

(deftest a-non-artist-cannot-save-a-credit
  (with-conn conn
    (add-artist! conn "someone" nil)
    (is (= 400 (:status (orcpub.routes/update-artist-credit (req conn "someone" {:name "Fusspot"})))))
    (is (empty? (ac/db-edits (d/db conn))))))

(deftest the-preferred-name-round-trips-encrypted
  (with-conn conn
    (let [id (add-artist! conn "someone" nil)]
      (with-redefs [orcpub.crypto/configured-keys (constantly test-keys)]
        (let [r (orcpub.routes/update-user-preferences (req conn "someone" {:preferred-name "  Kaylee  "}))]
          (is (= "Kaylee" (get-in r [:body :preferred-name])))
          (is (not= "Kaylee" (:orcpub.user/preferred-name (d/entity (d/db conn) id))) "not readable in the db")
          (is (= "Kaylee" (:preferred-name (orcpub.routes/user-body (d/db conn) (d/pull (d/db conn) '[*] id))))))
        (testing "blank clears it"
          (orcpub.routes/update-user-preferences (req conn "someone" {:preferred-name ""}))
          (is (nil? (:orcpub.user/preferred-name (d/entity (d/db conn) id)))))
        (is (= 400 (:status (orcpub.routes/update-user-preferences
                             (req conn "someone" {:preferred-name (apply str (repeat 41 "x"))}))))))
      (testing "without a key it refuses rather than storing it readable"
        (with-redefs [orcpub.crypto/configured-keys (constantly nil)]
          (is (= 400 (:status (orcpub.routes/update-user-preferences
                               (req conn "someone" {:preferred-name "Kaylee"})))))
          (is (nil? (:orcpub.user/preferred-name (d/entity (d/db conn) id)))))))))

(deftest site-emails-greet-by-the-preferred-name
  (with-conn conn
    (let [id (add-artist! conn "someone" nil)
          sent (atom nil)]
      (with-redefs [orcpub.crypto/configured-keys (constantly test-keys)
                    orcpub.email/reset-message (fn [_ user _] (reset! sent user)
                                                {:to (:email user) :subject "reset" :body []})]
        (orcpub.routes/do-send-password-reset id "someone@example.test" conn {:headers {"host" "x.test"} :scheme :https})
        (is (= "someone" (:first-and-last-name @sent)) "no preferred name yet: greeted by username")
        (orcpub.routes/update-user-preferences (req conn "someone" {:preferred-name "Kaylee"}))
        (orcpub.routes/do-send-password-reset id "someone@example.test" conn {:headers {"host" "x.test"} :scheme :https})
        (is (= "Kaylee" (:first-and-last-name @sent)))))))

(deftest the-artist-turns-their-page-and-its-parts-off
  (with-conn conn
    (let [id (add-artist! conn "fusspot" :house-pack)]
      (testing "the page"
        (is (:saved (ac/save! conn id {:profile? false})))
        (is (false? (:artist/profile? (pa/artist-info :house-pack))))
        (is (nil? (orcpub.dnd.e5.artist-profile/artist-by-slug "fusspot")) "no page")
        (is (false? (get-in (ac/credit-for-account (d/db conn) id) [:own :artist/profile?]))
            "the settings page reads the switch back"))
      (testing "and, with the page on, its parts one by one"
        (is (:saved (ac/save! conn id {:show-examples? false :show-links? false :show-count? false})))
        (is (= {:artist/show-examples? false :artist/show-links? false :artist/show-count? false}
               (select-keys (pa/artist-info :house-pack)
                            [:artist/show-examples? :artist/show-links? :artist/show-count?])))
        (is (some? (orcpub.dnd.e5.artist-profile/artist-by-slug "fusspot")) "the page is back")))))

(deftest the-deployment-can-force-a-page-off-but-not-on
  (with-conn conn
    (let [id (add-artist! conn "fusspot" :house-pack)]
      (ac/save! conn id {:profile? false})
      (with-redefs [branding/portrait-artists {:house-pack {:artist/profile? true}}]
        @(d/transact conn [{:db/id id :orcpub.user/username "fusspot"}])
        (ac/refresh! (d/db conn))
        (is (false? (:artist/profile? (pa/artist-info :house-pack))) "the artist's off stands"))
      (ac/save! conn id {:profile? true})
      (with-redefs [branding/portrait-artists {:house-pack {:artist/profile? false}}]
        @(d/transact conn [{:db/id id :orcpub.user/username "fusspot"}])
        (ac/refresh! (d/db conn))
        (is (false? (:artist/profile? (pa/artist-info :house-pack))) "the deployment's off stands")))))

(deftest the-page-switches-arrive-through-the-route
  (with-conn conn
    (add-artist! conn "fusspot" :house-pack)
    (with-redefs [orcpub.artist-email/credit-changed-tx (fn [_] [])
                  clojure.core/future-call (fn [f] (f) (delay nil))]
      (is (= 200 (:status (orcpub.routes/update-artist-credit
                           (req conn "fusspot" {:name "" :link "" :links [] :profile? false :show-count? true})))))
      (is (false? (:artist/profile? (pa/artist-info :house-pack))) "what My Account sends is what is saved"))))
