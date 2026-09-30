(ns orcpub.routes.party-test
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [orcpub.routes.folder-test :as ft]
            [orcpub.routes.party :as party]
            [orcpub.dnd.e5.party :as party5e]))

(defn- member-ids [party]
  (set (map :db/id (::party5e/character-ids party))))

(deftest test-add-and-remove-character-return-the-updated-party
  (ft/with-conn conn
    (ft/setup-db! conn)
    (let [char-id  (:db/id (ft/save-char! conn "alice"))
          party-id (:db/id (:body (party/create-party
                                   {:conn           conn
                                    :identity       {:user "alice"}
                                    :transit-params {::party5e/name "Crew"}})))
          ;; `:db` is the snapshot the db-interceptor takes before the handler runs.
          added    (party/add-character {:db             (d/db conn)
                                         :conn           conn
                                         :identity       {:user "alice"}
                                         :transit-params char-id
                                         :path-params    {:id party-id}})]
      (testing "add-character answers with the party including the new member"
        (is (= 200 (:status added)))
        (is (= #{char-id} (member-ids (:body added)))))
      (let [removed (party/remove-character {:db          (d/db conn)
                                             :conn        conn
                                             :identity    {:user "alice"}
                                             :path-params {:id           party-id
                                                           :character-id (str char-id)}})]
        (testing "remove-character answers with the party without the removed member"
          (is (= 200 (:status removed)))
          (is (empty? (member-ids (:body removed)))))))))
