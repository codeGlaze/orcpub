;; Who can store a share snapshot, what the server checks, and what it keeps.
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

(defn- put! [conn username character-id ^bytes b & [claimed-id]]
  (share/put-share {:db (d/db conn) :conn conn :identity {:user username}
                    :path-params {:id character-id :share (or claimed-id (share/share-id b))}
                    :body (ByteArrayInputStream. b)}))

(defn- fetch [conn character-id b]
  (share/get-share {:db (d/db conn) :path-params {:id character-id :share (share/share-id b)}}))

(defn- stored [conn]
  (count (d/q '[:find [?e ...] :where [?e :orcpub.share/id]] (d/db conn))))

(deftest the-owner-stores-a-snapshot-and-anyone-can-fetch-it
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (is (= 200 (:status (put! conn "alice" id (blob "One")))))
      (is (= (seq (blob "One")) (seq (.readAllBytes ^java.io.InputStream (:body (fetch conn id (blob "One")))))))
      (is (= 200 (:status (put! conn "alice" id (blob "One")))) "the same content again")
      (is (= 1 (stored conn)) "is not stored twice")
      (is (= 404 (:status (fetch conn id (blob "Other"))))))))

(deftest only-the-characters-owner-can-store-one
  (with-conn conn
    (setup! conn)
    (let [alices (create! conn {::se/owner "alice"})
          by-email (create! conn {::se/owner "alice@test.com"})]
      (is (= 404 (:status (put! conn "bob" alices (blob "One")))))
      (is (= 404 (:status (put! conn nil alices (blob "One")))))
      (is (zero? (stored conn)))
      (is (= 200 (:status (put! conn "alice" by-email (blob "One"))))
          "a character saved under its owner's email address"))))

(deftest a-snapshot-must-be-the-blob-its-id-names
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (is (= 400 (:status (put! conn "alice" id (blob "One") (share/share-id (blob "Two"))))))
      (is (= 400 (:status (put! conn "alice" id (byte-array (concat [1] (.getBytes "an-embedded-link-payload" "UTF-8"))))))
          "the wrong version byte")
      (is (= 400 (:status (put! conn "alice" id (byte-array [2 1 2 3])))) "too short to hold the tag")
      (is (= 413 (:status (put! conn "alice" id (byte-array (inc share/max-blob-bytes) (byte 2))))))
      (is (zero? (stored conn))))))

(deftest a-character-keeps-its-newest-snapshots
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (with-redefs [share/max-shares-per-character 3]
        (doseq [s ["One" "Two" "Three" "Four"]]
          (is (= 200 (:status (put! conn "alice" id (blob s)))))))
      (is (= 404 (:status (fetch conn id (blob "One")))) "the oldest made room")
      (is (every? #(= 200 (:status (fetch conn id (blob %)))) ["Two" "Three" "Four"])))))

(deftest an-account-has-a-storage-quota
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (with-redefs [share/max-bytes-per-owner 30]
        (is (= 200 (:status (put! conn "alice" id (blob "One")))))
        (is (= 413 (:status (put! conn "alice" id (blob "Two")))))))))

(deftest deleting-a-character-deletes-its-snapshots
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::se/owner "alice"})]
      (put! conn "alice" id (blob "One"))
      (is (= 200 (:status (routes/delete-character {:db (d/db conn) :conn conn :identity {:user "alice"}
                                                     :path-params {:id (str id)}}))))
      (is (zero? (stored conn))))))

(deftest storing-needs-a-login-and-fetching-does-not
  (let [path   (route-map/path-for route-map/dnd-e5-char-share-route :id ":id" :share ":share")
        auth?  (fn [method] (some->> routes/routes
                                     (filter #(and (= method (:method %)) (= path (:path %))))
                                     first :interceptors (map :name) (some #{:check-auth})))]
    (is (auth? :put))
    (is (nil? (auth? :get)))))
