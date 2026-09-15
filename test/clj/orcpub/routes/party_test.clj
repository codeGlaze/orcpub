;; What a party can hold, and how it keeps the share token of a character added from a share link.
(ns orcpub.routes.party-test
  (:require [clojure.test :refer [deftest is]]
            [datomic.api :as d]
            [orcpub.routes.party :as party-routes]
            [orcpub.routes.share :as share]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.party :as party]
            [orcpub.dnd.e5.magic-items :as mi5e])
  (:import [java.util UUID]))

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:party-test-" (UUID/randomUUID))
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

(defn- bobs-character! [conn name]
  (create! conn {::se/owner "bob" ::se/summary {:orcpub.dnd.e5.character/character-name name}}))

(defn- shared! [conn character-id]
  (:body (share/create-token {:db (d/db conn) :conn conn :identity {:user "bob"} :path-params {:id character-id}})))

(defn- new-party! [conn & [extra]]
  (party-routes/create-party {:db (d/db conn) :conn conn :identity {:user "alice"}
                              :transit-params (merge {::party/name "Table"} extra)}))

(defn- add! [conn party-id params]
  (party-routes/add-character {:db (d/db conn) :conn conn :identity {:user "alice"}
                               :transit-params params :path-params {:id party-id}}))

(defn- listed [conn]
  (->> (party-routes/parties {:db (d/db conn) :identity {:user "alice"}}) :body first ::party/character-ids))

(deftest only-characters-can-join-a-party
  (with-conn conn
    (setup! conn)
    (let [party-id (:db/id (:body (new-party! conn)))
          item     (create! conn {::mi5e/name "Cloak" ::mi5e/owner "bob"})
          bard     (bobs-character! conn "Wren")]
      (is (= 400 (:status (add! conn party-id item))) "a custom item")
      (is (= 400 (:status (add! conn party-id party-id))) "the party itself")
      (is (= 400 (:status (add! conn party-id "not an id"))))
      (is (= 400 (:status (new-party! conn {::party/character-ids #{item}}))) "nor when creating one")
      (is (= 200 (:status (add! conn party-id bard))) "someone else's character, as from a link")
      (is (= [bard] (map :db/id (listed conn)))))))

(deftest a-character-added-from-a-share-link-keeps-its-token
  (with-conn conn
    (setup! conn)
    (let [party-id (:db/id (:body (new-party! conn)))
          bard     (bobs-character! conn "Wren")
          token    (shared! conn bard)]
      (is (= 200 (:status (add! conn party-id {:character-id bard :share-token token}))))
      (is (= token (:orcpub.party-share/token (first (listed conn)))))
      (add! conn party-id {:character-id bard :share-token token})
      (is (= 1 (count (d/q '[:find [?t ...] :where [?t :orcpub.party-share/token]] (d/db conn))))
          "adding it again keeps one token"))))

(deftest a-token-that-no-longer-works-is-not-kept-or-listed
  (with-conn conn
    (setup! conn)
    (let [party-id (:db/id (:body (new-party! conn)))
          bard     (bobs-character! conn "Wren")
          rogue    (bobs-character! conn "Nix")
          token    (shared! conn bard)]
      (shared! conn rogue)
      (add! conn party-id {:character-id rogue :share-token "AAAAAAAAAAAAAAAAAAAAAA"})
      (is (nil? (:orcpub.party-share/token (first (listed conn)))) "a wrong token is not kept")
      (add! conn party-id {:character-id bard :share-token token})
      (share/new-token {:db (d/db conn) :conn conn :identity {:user "bob"} :path-params {:id bard}})
      (is (every? #(nil? (:orcpub.party-share/token %)) (listed conn))
          "after New link the party lists no token, so the row loads no homebrew")
      (is (= #{bard rogue} (set (map :db/id (listed conn)))) "both characters stay in the party")
      (is (empty? (d/q '[:find [?t ...] :where [?t :orcpub.party-share/token]] (d/db conn)))
          "and the token it saved for the bard is deleted"))))

(deftest removing-a-character-drops-its-token
  (with-conn conn
    (setup! conn)
    (let [party-id (:db/id (:body (new-party! conn)))
          bard     (bobs-character! conn "Wren")]
      (add! conn party-id {:character-id bard :share-token (shared! conn bard)})
      (party-routes/remove-character {:db (d/db conn) :conn conn :identity {:user "alice"}
                                      :path-params {:id party-id :character-id (str bard)}})
      (is (empty? (listed conn)))
      (is (empty? (d/q '[:find [?t ...] :where [?t :orcpub.party-share/token]] (d/db conn)))))))

(deftest a-new-party-can-start-with-a-shared-character
  (with-conn conn
    (setup! conn)
    (let [bard  (bobs-character! conn "Wren")
          token (shared! conn bard)]
      (is (= 200 (:status (new-party! conn {::party/character-ids #{bard}
                                            ::party/shared-tokens [{:orcpub.party-share/character bard
                                                                    :orcpub.party-share/token token}]}))))
      (is (= token (:orcpub.party-share/token (first (listed conn))))))))
