;; What the server hands out to someone who is not the owner.
(ns orcpub.routes.public-reads-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.routes :as routes]
            [orcpub.route-map :as route-map]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.magic-items :as mi5e])
  (:import [java.util UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:public-reads-test-" (UUID/randomUUID))
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

(deftest an-item-is-readable-by-its-owner-only
  (with-conn conn
    (setup! conn)
    (let [id (create! conn {::mi5e/name "Alice's Cloak" ::mi5e/owner "alice"
                            ::mi5e/type :wondrous-item ::mi5e/rarity :rare})
          read-as (fn [username]
                    (routes/get-item {:db (d/db conn) :username username :path-params {:id id}}))]
      (is (= 200 (:status (read-as "alice"))))
      (is (= "Alice's Cloak" (::mi5e/name (:body (read-as "alice")))))
      (is (= 404 (:status (read-as "bob"))) "another account gets what a missing item gets")
      (is (= 404 (:status (read-as nil)))))))

(deftest reading-an-item-by-id-needs-a-login
  (let [path  (route-map/path-for route-map/dnd-e5-item-route :id ":id")
        route (first (filter #(and (= :get (:method %)) (= path (:path %))) routes/routes))]
    (is (some? route) (str "no GET route at " path))
    (is (some #{:check-auth} (map :name (:interceptors route))))))

(deftest a-character-read-names-its-owner-by-username-only
  (with-conn conn
    (setup! conn)
    (let [owner-of (fn [id] (let [{:keys [status body]} (routes/get-character-for-id (d/db conn) id)]
                              (is (= 200 status))
                              (get body ::se/owner ::absent)))]
      (is (= "alice" (owner-of (create! conn {::se/owner "alice"}))))
      (is (= "alice" (owner-of (create! conn {::se/owner "alice@test.com"})))
          "saved in May 2017 under the email the owner logged in with")
      (is (= ::absent (owner-of (create! conn {::se/owner "gone@test.com"})))
          "an email with no account behind it is not shown either"))))
