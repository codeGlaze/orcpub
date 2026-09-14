;; Who can store a share snapshot, what the server checks and keeps, and how a new link revokes old ones.
(ns orcpub.routes.share-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.routes.share :as share]
            [orcpub.route-map :as route-map]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se])
  (:import [java.util UUID]
           [java.io ByteArrayInputStream]))

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

(defn- blob [seed] (byte-array (concat [2] (.getBytes (str seed "-and-a-sixteen-byte-tag") "UTF-8"))))

;; The browser derives an id from the key; any 22 base64url characters stand in for one here.
(defn- id-for [seed] (subs (str seed "0123456789abcdefghijklmnop") 0 22))

(defn- put! [conn username character-id seed & [b]]
  (share/put-share {:db (d/db conn) :conn conn :identity {:user username}
                    :path-params {:id character-id :share (id-for seed)}
                    :body (ByteArrayInputStream. ^bytes (or b (blob seed)))}))

(defn- fetch [conn character-id seed]
  (share/get-share {:db (d/db conn) :path-params {:id character-id :share (id-for seed)}}))

(defn- salt-request [f conn username character-id]
  (f {:db (d/db conn) :conn conn :identity {:user username} :path-params {:id character-id}}))

(defn- stored [conn]
  (count (d/q '[:find [?e ...] :where [?e :orcpub.share/id]] (d/db conn))))

(deftest the-owner-stores-a-snapshot-and-anyone-can-fetch-it
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (is (= 200 (:status (put! conn "alice" id "One"))))
      (is (= (seq (blob "One")) (seq (.readAllBytes ^java.io.InputStream (:body (fetch conn id "One"))))))
      (is (= 200 (:status (put! conn "alice" id "One"))) "the same snapshot again")
      (is (= 1 (stored conn)) "is not stored twice")
      (is (= 404 (:status (fetch conn id "Other")))))))

(deftest only-the-characters-owner-can-store-one
  (with-conn conn
    (setup! conn)
    (let [alices (create! conn {::se/owner "alice"})
          by-email (create! conn {::se/owner "alice@test.com"})]
      (is (= 404 (:status (put! conn "bob" alices "One"))))
      (is (= 404 (:status (put! conn nil alices "One"))))
      (is (zero? (stored conn)))
      (is (= 200 (:status (put! conn "alice" by-email "One")))
          "a character saved under its owner's email address"))))

(deftest a-snapshot-must-look-like-one
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})
          put-as (fn [share b] (share/put-share {:db (d/db conn) :conn conn :identity {:user "alice"}
                                                 :path-params {:id id :share share}
                                                 :body (ByteArrayInputStream. ^bytes b)}))]
      (is (= 400 (:status (put-as "not an id" (blob "One")))))
      (is (= 400 (:status (put! conn "alice" id "One" (byte-array (concat [1] (.getBytes "an-embedded-link-payload" "UTF-8"))))))
          "the wrong version byte")
      (is (= 400 (:status (put! conn "alice" id "One" (byte-array [2 1 2 3])))) "too short to hold the tag")
      (is (= 413 (:status (put! conn "alice" id "One" (byte-array (inc share/max-blob-bytes) (byte 2))))))
      (is (zero? (stored conn))))))

(deftest a-character-keeps-its-newest-snapshots
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (with-redefs [share/max-shares-per-character 3]
        (doseq [s ["One" "Two" "Three" "Four"]]
          (is (= 200 (:status (put! conn "alice" id s))))))
      (is (= 404 (:status (fetch conn id "One"))) "the oldest made room")
      (is (every? #(= 200 (:status (fetch conn id %))) ["Two" "Three" "Four"])))))

(deftest an-account-has-a-storage-quota
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (with-redefs [share/max-bytes-per-owner 30]
        (is (= 200 (:status (put! conn "alice" id "One"))))
        (is (= 413 (:status (put! conn "alice" id "Two"))))))))

(deftest a-characters-salt-stays-until-a-new-link-replaces-it
  (with-conn conn
    (setup! conn)
    (let [id    (create! conn {::se/owner "alice"})
          first (:body (salt-request share/get-salt conn "alice" id))]
      (is (re-matches #"[A-Za-z0-9_-]{43}" first))
      (is (= first (:body (salt-request share/get-salt conn "alice" id))) "the same salt in the next session")
      (is (= 404 (:status (salt-request share/get-salt conn "bob" id))) "nobody else's to see")
      (is (= 404 (:status (salt-request share/new-salt conn "bob" id))) "or to replace")
      (put! conn "alice" id "One")
      (put! conn "alice" id "Two")
      (let [replaced (:body (salt-request share/new-salt conn "alice" id))]
        (is (not= first replaced))
        (is (= replaced (:body (salt-request share/get-salt conn "alice" id))))
        (is (zero? (stored conn)) "every earlier snapshot is gone, so every earlier link stops loading")))))

(deftest deleting-a-character-deletes-its-snapshots-and-salt
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (salt-request share/get-salt conn "alice" id)
      (put! conn "alice" id "One")
      (is (= 200 (:status (routes/delete-character {:db (d/db conn) :conn conn :identity {:user "alice"}
                                                     :path-params {:id (str id)}}))))
      (is (zero? (stored conn)))
      (is (empty? (d/q '[:find [?e ...] :where [?e :orcpub.share-salt/character]] (d/db conn)))))))

(deftest storing-and-salts-need-a-login-and-fetching-does-not
  (let [auth? (fn [path method] (some->> routes/routes
                                         (filter #(and (= method (:method %)) (= path (:path %))))
                                         first :interceptors (map :name) (some #{:check-auth})))
        share-path (route-map/path-for route-map/dnd-e5-char-share-route :id ":id" :share ":share")
        salt-path  (route-map/path-for route-map/dnd-e5-char-share-salt-route :id ":id")]
    (is (auth? share-path :put))
    (is (nil? (auth? share-path :get)))
    (is (auth? salt-path :get))
    (is (auth? salt-path :post))))
