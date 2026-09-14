;; A character's shared homebrew: who can share it, what an upload must be, how New link revokes, and what
;; pruning may delete.
(ns orcpub.routes.share-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.edn :as edn]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.routes.party :as party-routes]
            [orcpub.routes.share :as share]
            [orcpub.dnd.e5.party :as party]
            [orcpub.config :as config]
            [orcpub.route-map :as route-map]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se])
  (:import [java.util UUID]
           [java.util.zip GZIPInputStream GZIPOutputStream]
           [java.io ByteArrayInputStream ByteArrayOutputStream]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:share-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn- setup! [conn]
  @(d/transact conn schema/all-schemas)
  @(d/transact conn [{:orcpub.user/username "alice" :orcpub.user/email "alice@test.com"}
                     {:orcpub.user/username "bob" :orcpub.user/email "bob@test.com"}]))

(defn- create! [conn entity]
  (let [{:keys [tempids db-after]} @(d/transact conn [(assoc entity :db/id "new")])]
    (d/resolve-tempid db-after tempids "new")))

(defn- homebrew [description]
  {"Tongues" {:orcpub.dnd.e5/languages {:e2e-cant {:name "Cant" :key :e2e-cant :option-pack "Tongues"
                                                    :description description}}}})

(defn- gz ^bytes [^String s]
  (let [out (ByteArrayOutputStream.)]
    (with-open [z (GZIPOutputStream. out)] (.write z (.getBytes s "UTF-8")))
    (.toByteArray out)))

(defn- request [conn username id & [extra]]
  (merge {:db (d/db conn) :conn conn :identity {:user username} :path-params {:id id}} extra))

(defn- token! [conn username id] (share/get-token (request conn username id)))

(defn- share! [conn username id] (share/create-token (request conn username id)))

(defn- put! [conn username id token ^bytes upload]
  (share/put-share (request conn username id {:path-params {:id id :token token}
                                              :body (ByteArrayInputStream. upload)})))

(defn- load-share [conn id token]
  (let [{:keys [status body]} (share/get-share {:db (d/db conn) :conn conn :path-params {:id id :token token}})]
    (if (= 200 status)
      (edn/read-string (slurp (GZIPInputStream. body) :encoding "UTF-8"))
      status)))

(deftest nothing-is-stored-until-the-character-is-shared
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (is (= 404 (:status (token! conn "alice" id))) "asking, as every page view does, makes nothing")
      (is (empty? (d/q '[:find [?e ...] :where [?e :orcpub.share/character]] (d/db conn))))
      (let [token (:body (share! conn "alice" id))]
        (is (re-matches #"[A-Za-z0-9_-]{22}" token) "Share link makes the token")
        (is (= token (:body (share! conn "alice" id))) "pressing it again keeps it")
        (is (= token (:body (token! conn "alice" id))))))))

(deftest the-owner-shares-homebrew-and-the-link-loads-it
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          token (:body (share! conn "alice" id))]
      (is (re-matches #"[A-Za-z0-9_-]{22}" token))
      (is (= 404 (load-share conn id token)) "nothing shared yet")
      (is (= 200 (:status (put! conn "alice" id token (gz (pr-str (homebrew "First")))))))
      (is (= (homebrew "First") (load-share conn id token)))
      (is (= 404 (load-share conn id "AAAAAAAAAAAAAAAAAAAAAA")) "a wrong token loads nothing"))))

(deftest sharing-again-replaces-the-homebrew-and-keeps-the-link
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          token (:body (share! conn "alice" id))]
      (put! conn "alice" id token (gz (pr-str (homebrew "First"))))
      (is (= 200 (:status (put! conn "alice" id token (gz (pr-str (homebrew "Second")))))))
      (is (= token (:body (token! conn "alice" id))) "the same token in the next session")
      (is (= (homebrew "Second") (load-share conn id token)))
      (is (= 1 (count (d/q '[:find [?e ...] :where [?e :orcpub.share/character]] (d/db conn)))) "one copy"))))

(deftest only-the-characters-owner-can-share
  (with-conn conn
    (setup! conn)
    (let [id       (create! conn {::se/owner "alice"})
          by-email (create! conn {::se/owner "alice@test.com"})
          token    (:body (share! conn "alice" id))]
      (is (= 404 (:status (token! conn "bob" id))))
      (is (= 404 (:status (share! conn "bob" id))) "nor share someone else's character")
      (is (= 404 (:status (token! conn nil id))))
      (is (= 404 (:status (put! conn "bob" id token (gz (pr-str (homebrew "Bob's")))))))
      (is (= 404 (load-share conn id token)) "bob stored nothing")
      (is (= 200 (:status (share! conn "alice" by-email))) "a character saved under its owner's email"))))

(deftest an-upload-is-checked-like-an-import
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          token (:body (share! conn "alice" id))]
      (is (= 404 (:status (put! conn "alice" id "AAAAAAAAAAAAAAAAAAAAAA" (gz (pr-str (homebrew "x"))))))
          "not the character's token")
      (is (= 400 (:status (put! conn "alice" id token (.getBytes "not compressed" "UTF-8")))))
      (is (= 400 (:status (put! conn "alice" id token (gz "(((")))) "not readable data")
      (is (= 400 (:status (put! conn "alice" id token (gz (pr-str {:not "homebrew"}))))) "nothing homebrew-shaped")
      (is (= 400 (:status (put! conn "alice" id token (gz "#=(java.lang.System/exit 0)")))) "code is not data")
      (with-redefs [share/max-upload-bytes (constantly 20)]
        (is (= 413 (:status (put! conn "alice" id token (gz (pr-str (homebrew "x"))))))))
      (with-redefs [share/max-text-bytes (constantly 20)]
        (is (= 413 (:status (put! conn "alice" id token (gz (pr-str (homebrew "x"))))))))
      (is (= 400 (:status (put! conn "alice" id token
                                 (gz (pr-str (assoc-in (homebrew "x") ["Tongues" :orcpub.dnd.e5/languages :e2e-cant :image]
                                                       "data:image/png;base64,iVBORw0KGgo"))))))
          "a pasted image, which the whitelist would empty, is refused rather than rewritten")
      (is (= 400 (:status (put! conn "alice" id token (gz (pr-str {"Tongues" {:orcpub.dnd.e5/languages {:9-lives {:name "x"}}}})))))
          "an entry the whitelist would drop")
      (is (= 404 (load-share conn id token)) "none of those was stored"))))

(deftest an-unchanged-upload-is-not-checked-or-written-again
  (with-conn conn
    (setup! conn)
    (let [id     (create! conn {::se/owner "alice"})
          token  (:body (share! conn "alice" id))
          upload (gz (pr-str (homebrew "First")))]
      (put! conn "alice" id token upload)
      (let [before (d/basis-t (d/db conn))]
        (with-redefs [share/max-text-bytes (fn [] (throw (ex-info "the upload was unpacked" {})))]
          (is (= 200 (:status (put! conn "alice" id token upload)))))
        (is (= before (d/basis-t (d/db conn))) "nothing was written")))))

(deftest the-token-carries-the-caps
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})
          {:keys [headers]} (share! conn "alice" id)]
      (is (= (str (share/max-upload-bytes)) (get headers "X-Share-Max-Upload-Bytes")))
      (is (= (str (share/max-text-bytes)) (get headers "X-Share-Max-Text-Bytes"))))))

(deftest an-account-has-a-storage-quota
  (with-conn conn
    (setup! conn)
    (let [first-id  (create! conn {::se/owner "alice"})
          second-id (create! conn {::se/owner "alice"})
          upload    (gz (pr-str (homebrew "Shared by both")))]
      (put! conn "alice" first-id (:body (share! conn "alice" first-id)) upload)
      (let [one (d/q '[:find ?s . :where [_ :orcpub.share/size ?s]] (d/db conn))]
        (with-redefs [share/max-bytes-per-owner (constantly (+ one 10))]
          (is (= 413 (:status (put! conn "alice" second-id (:body (share! conn "alice" second-id)) upload)))))))))

(deftest new-link-revokes-every-earlier-link
  (with-conn conn
    (setup! conn)
    (let [id  (create! conn {::se/owner "alice"})
          old (:body (share! conn "alice" id))]
      (put! conn "alice" id old (gz (pr-str (homebrew "First"))))
      (is (= 404 (:status (share/new-token (request conn "bob" id)))) "nobody else can")
      (let [fresh (:body (share/new-token (request conn "alice" id)))]
        (is (not= old fresh))
        (is (= fresh (:body (share! conn "alice" id))))
        (is (= 404 (load-share conn id old)) "the old link loads nothing")
        (is (= 404 (load-share conn id fresh)) "and the homebrew is gone until it is shared again")
        (put! conn "alice" id fresh (gz (pr-str (homebrew "First"))))
        (is (= (homebrew "First") (load-share conn id fresh)))))))

(deftest deleting-a-character-deletes-its-shared-homebrew
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (put! conn "alice" id (:body (share! conn "alice" id)) (gz (pr-str (homebrew "First"))))
      (is (= 200 (:status (routes/delete-character {:db (d/db conn) :conn conn :identity {:user "alice"}
                                                     :path-params {:id (str id)}}))))
      (is (empty? (d/q '[:find [?e ...] :where [?e :orcpub.share/character]] (d/db conn)))))))

(deftest sharing-needs-a-login-and-loading-does-not
  (let [auth? (fn [path method] (some->> routes/routes
                                         (filter #(and (= method (:method %)) (= path (:path %))))
                                         first :interceptors (map :name) (some #{:check-auth})))
        share-path (route-map/path-for route-map/dnd-e5-char-share-route :id ":id" :token ":token")
        token-path (route-map/path-for route-map/dnd-e5-char-share-token-route :id ":id")]
    (is (auth? share-path :put))
    (is (nil? (auth? share-path :get)))
    (is (auth? token-path :get))
    (is (auth? token-path :put))
    (is (auth? token-path :post))))

(defn- days-later [^java.util.Date d n] (java.util.Date. (+ (.getTime d) (* n 24 60 60 1000))))

(deftest a-share-nobody-uses-is-deleted-and-use-keeps-it
  (with-conn conn
    (setup! conn)
    (let [t0     (java.util.Date.)
          quiet  (create! conn {::se/owner "alice"})
          busy   (create! conn {::se/owner "alice"})
          shared (with-redefs [share/now (constantly t0)]
                   (into {} (for [id [quiet busy]]
                              (let [token (:body (share! conn "alice" id))]
                                (put! conn "alice" id token (gz (pr-str (homebrew "First"))))
                                [id token]))))]
      (with-redefs [share/now (constantly (days-later t0 100))]
        (is (= (homebrew "First") (load-share conn busy (shared busy))) "opening the link at day 100"))
      (with-redefs [share/now (constantly (days-later t0 181))]
        (is (zero? (share/prune! conn)) "use is recorded at most daily, so a day more is allowed"))
      (with-redefs [share/now (constantly (days-later t0 182))]
        (is (= 1 (share/prune! conn)))
        (is (= 404 (load-share conn quiet (shared quiet))) "182 days unused: gone")
        (is (= (homebrew "First") (load-share conn busy (shared busy))) "82 days since it was last opened")))))

(deftest use-is-recorded-at-most-daily
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          token (:body (share! conn "alice" id))]
      (put! conn "alice" id token (gz (pr-str (homebrew "First"))))
      (load-share conn id token)
      (let [before (d/basis-t (d/db conn))]
        (load-share conn id token)
        (load-share conn id token)
        (is (= before (d/basis-t (d/db conn))) "opening the link again the same day writes nothing")))))

(deftest the-sweep-deletes-only-unused-shares
  (with-conn conn
    (setup! conn)
    (let [t0 (java.util.Date.)
          ids (repeatedly 2 #(create! conn {::se/owner "alice"}))]
      (with-redefs [share/now (constantly t0)]
        (doseq [id ids] (share! conn "alice" id)))
      (with-redefs [share/now (constantly (days-later t0 90))]
        (token! conn "alice" (second ids)))
      (with-redefs [share/now (constantly (days-later t0 200))]
        (is (= 1 (share/prune! conn))))
      (is (= [(second ids)] (d/q '[:find [?c ...] :where [_ :orcpub.share/character ?c]] (d/db conn)))))))

(deftest zero-days-keeps-shares
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          token (with-redefs [share/now (constantly (java.util.Date. 0))] (:body (share! conn "alice" id)))]
      (with-redefs [config/get-share-prune-days (constantly 0)]
        (is (zero? (share/prune! conn)))
        (is (= token (:body (token! conn "alice" id))))))))

(defn- at [^java.util.Date t f] (with-redefs [share/now (constantly t)] (f)))

(defn- minutes-later [^java.util.Date d n] (java.util.Date. (+ (.getTime d) (* n 60 1000))))

(deftest a-request-without-the-token-is-not-use
  (with-conn conn
    (setup! conn)
    (let [t0    (java.util.Date.)
          id    (create! conn {::se/owner "alice"})
          token (at t0 #(let [token (:body (share! conn "alice" id))]
                          (put! conn "alice" id token (gz (pr-str (homebrew "First"))))
                          token))]
      (at (days-later t0 100)
          #(let [before (d/basis-t (d/db conn))]
             (is (= 404 (load-share conn id "AAAAAAAAAAAAAAAAAAAAAA")) "a wrong token")
             (is (= 404 (load-share conn id nil)) "no token")
             (is (= 404 (:status (token! conn "bob" id))))
             (is (= 404 (:status (share! conn "bob" id))))
             (is (= 404 (:status (put! conn "bob" id token (gz (pr-str (homebrew "Bob's"))))))
                 "someone else, even with the token")
             (is (= 404 (:status (put! conn "alice" id "AAAAAAAAAAAAAAAAAAAAAA" (gz (pr-str (homebrew "First")))))))
             (is (= before (d/basis-t (d/db conn))) "none of them wrote anything")))
      (is (= 1 (at (days-later t0 182) #(share/prune! conn))) "so none of them kept the share alive"))))

(deftest the-sweep-deletes-share-records-and-nothing-else
  (with-conn conn
    (setup! conn)
    (let [t0    (java.util.Date.)
          id    (create! conn {::se/owner "alice"})
          token (at t0 #(:body (share! conn "alice" id)))]
      (is (= 200 (:status (party-routes/create-party
                           {:db (d/db conn) :conn conn :identity {:user "bob"}
                            :transit-params {::party/name "Table" ::party/character-ids #{id}
                                             ::party/shared-tokens [{:orcpub.party-share/character id
                                                                     :orcpub.party-share/token token}]}}))))
      (create! conn {:orcpub.share/character 42 :orcpub.share/owner "alice" :orcpub.share/token "unrecorded"})
      (is (= 1 (at (days-later t0 400) #(share/prune! conn))))
      (let [db (d/db conn)]
        (is (= "alice" (::se/owner (d/pull db [::se/owner] id))) "the character stays")
        (is (= [token] (d/q '[:find [?t ...] :where [_ :orcpub.party-share/token ?t]] db)) "the party's entry stays")
        (is (nil? (->> (party-routes/parties {:db db :identity {:user "bob"}})
                       :body first ::party/character-ids first :orcpub.party-share/token))
            "but lists no token, so the party page loads no homebrew")
        (is (= [42] (d/q '[:find [?c ...] :where [_ :orcpub.share/character ?c]] db))
            "a share with no recorded use is kept")))))

(deftest time-the-server-was-off-does-not-count
  (with-conn conn
    (setup! conn)
    (let [t0      (java.util.Date.)
          id      (create! conn {::se/owner "alice"})
          beat!   (fn [t] (at t #(share/record-beat! conn)))
          outages #(count (d/q '[:find ?o :where [?o :orcpub.share-outage/from]] (d/db conn)))]
      (at t0 #(share! conn "alice" id))
      (beat! (days-later t0 10))
      (beat! (minutes-later (days-later t0 10) 60))
      (is (zero? (outages)) "hourly beats")
      (beat! (days-later t0 100))
      (is (= 1 (outages)) "no beat for 90 days: the server was off")
      (beat! (days-later t0 50))
      (is (= 1 (outages)) "a clock set back is not an outage")
      (is (zero? (at (days-later t0 185) #(share/prune! conn))) "185 days, 90 of them off")
      (is (= 1 (at (days-later t0 275) #(share/prune! conn))) "275 days, 90 of them off, is 185 unused"))))
