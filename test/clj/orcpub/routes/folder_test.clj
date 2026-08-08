(ns orcpub.routes.folder-test
  (:require [clojure.test :refer [deftest is testing]]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.routes :as routes]
            [orcpub.routes.folder :as folder]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.character :as char5e])
  (:import [java.util UUID]))

;; ---------------------------------------------------------------------------
;; Test helpers

(defmacro with-conn [conn-binding & body]
  `(let [uri# (str "datomic:mem:folder-test-" (UUID/randomUUID))
         ~conn-binding (do
                         (d/create-database uri#)
                         (d/connect uri#))]
     (try ~@body
          (finally (d/delete-database uri#)))))

(defn setup-db! [conn]
  @(d/transact conn schema/all-schemas)
  @(d/transact conn [{:orcpub.user/username "alice"
                      :orcpub.user/email    "alice@test.com"}
                     {:orcpub.user/username "bob"
                      :orcpub.user/email    "bob@test.com"}]))

(defn test-character []
  {::se/selections
   [{::se/key :ability-scores
     ::se/option
     {::se/key :standard-scores
      ::se/map-value
      {::char5e/str 15
       ::char5e/dex 14
       ::char5e/con 13
       ::char5e/int 12
       ::char5e/wis 10
       ::char5e/cha 8}}}
    {::se/key :class
     ::se/options
     [{::se/key :fighter
       ::se/selections
       [{::se/key :levels
         ::se/options [{::se/key :level-1}]}]}]}]
   ::se/summary
   {::char5e/character-name "Aragorn"
    ::char5e/classes        [{::char5e/class-name "Fighter"
                              ::char5e/level      1}]}})

(defn save-char! [conn username]
  (:body (routes/do-save-character (d/db conn) conn (test-character) {:user username})))

(defn create-folder! [conn username folder-name]
  (:body (folder/create-folder {:conn           conn
                                :identity       {:user username}
                                :transit-params {::folder5e/name folder-name}})))

;; ---------------------------------------------------------------------------
;; create-folder

(deftest test-create-folder
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          result      (folder/create-folder {:conn           mocked-conn
                                             :identity       {:user "alice"}
                                             :transit-params {::folder5e/name "Campaign 1"}})]
      (testing "returns HTTP 200"
        (is (= 200 (:status result))))
      (testing "body has correct name"
        (is (= "Campaign 1" (::folder5e/name (:body result)))))
      (testing "body has correct owner"
        (is (= "alice" (::folder5e/owner (:body result)))))
      (testing "body has a db/id"
        (is (int? (:db/id (:body result))))))))

(deftest test-create-folder--nil-name-defaults
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          result      (folder/create-folder {:conn           mocked-conn
                                             :identity       {:user "alice"}
                                             :transit-params {}})]
      (testing "returns HTTP 200"
        (is (= 200 (:status result))))
      (testing "name defaults to New Folder"
        (is (= "New Folder" (::folder5e/name (:body result))))))))

;; ---------------------------------------------------------------------------
;; folders (list)

(deftest test-list-folders
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)]
      (create-folder! mocked-conn "alice" "Alice Folder 1")
      (create-folder! mocked-conn "alice" "Alice Folder 2")
      (create-folder! mocked-conn "bob"   "Bob Folder")
      (testing "alice sees only her own folders"
        (let [body (:body (folder/folders {:db       (d/db mocked-conn)
                                           :identity {:user "alice"}}))]
          (is (= 2 (count body)))
          (is (= #{"Alice Folder 1" "Alice Folder 2"}
                 (into #{} (map ::folder5e/name) body)))))
      (testing "bob sees only his own folder"
        (let [body (:body (folder/folders {:db       (d/db mocked-conn)
                                           :identity {:user "bob"}}))]
          (is (= 1 (count body)))
          (is (= "Bob Folder" (::folder5e/name (first body)))))))))

;; ---------------------------------------------------------------------------
;; update-folder-name

(deftest test-update-folder-name
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          created     (create-folder! mocked-conn "alice" "Old Name")
          folder-id   (:db/id created)
          result      (folder/update-folder-name {:conn           mocked-conn
                                                  :transit-params "New Name"
                                                  :path-params    {:id folder-id}})]
      (testing "returns HTTP 200"
        (is (= 200 (:status result))))
      (testing "folder name is updated"
        (is (= "New Name" (::folder5e/name (:body result))))))))

;; ---------------------------------------------------------------------------
;; update-folder-name — validation

(deftest test-update-folder-name--blank-rejected
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          created     (create-folder! mocked-conn "alice" "Original")
          folder-id   (:db/id created)]
      (testing "empty string returns 400"
        (is (= 400 (:status (folder/update-folder-name
                              {:conn mocked-conn
                               :transit-params ""
                               :path-params {:id folder-id}})))))
      (testing "whitespace-only returns 400"
        (is (= 400 (:status (folder/update-folder-name
                              {:conn mocked-conn
                               :transit-params "   "
                               :path-params {:id folder-id}})))))
      (testing "nil coerced to blank returns 400"
        (is (= 400 (:status (folder/update-folder-name
                              {:conn mocked-conn
                               :transit-params nil
                               :path-params {:id folder-id}})))))
      (testing "name is unchanged after rejected renames"
        (let [db-name (::folder5e/name (d/pull (d/db mocked-conn)
                                               [::folder5e/name] folder-id))]
          (is (= "Original" db-name)))))))

(deftest test-update-folder-name--trims-whitespace
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          created     (create-folder! mocked-conn "alice" "Old")
          folder-id   (:db/id created)
          result      (folder/update-folder-name {:conn mocked-conn
                                                  :transit-params "  Trimmed  "
                                                  :path-params {:id folder-id}})]
      (testing "returns 200"
        (is (= 200 (:status result))))
      (testing "name is trimmed"
        (is (= "Trimmed" (::folder5e/name (:body result))))))))

;; ---------------------------------------------------------------------------
;; add-character

(deftest test-add-character
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          saved-char  (save-char! mocked-conn "alice")
          char-id     (:db/id saved-char)
          folder      (create-folder! mocked-conn "alice" "My Folder")
          folder-id   (:db/id folder)
          result      (folder/add-character {:db             (d/db mocked-conn)
                                             :conn           mocked-conn
                                             :transit-params char-id
                                             :path-params    {:id folder-id}})]
      (testing "returns HTTP 200"
        (is (= 200 (:status result))))
      (testing "folder now contains the character"
        (let [char-ids (->> (:body result)
                            ::folder5e/character-ids
                            (map :db/id)
                            set)]
          (is (contains? char-ids char-id)))))))

(deftest test-add-character-at-most-one-folder
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          saved-char  (save-char! mocked-conn "alice")
          char-id     (:db/id saved-char)
          folder-a    (create-folder! mocked-conn "alice" "Folder A")
          folder-b    (create-folder! mocked-conn "alice" "Folder B")]
      ;; Add character to Folder A
      (folder/add-character {:db             (d/db mocked-conn)
                             :conn           mocked-conn
                             :transit-params char-id
                             :path-params    {:id (:db/id folder-a)}})
      ;; Add same character to Folder B — should remove from A first
      (folder/add-character {:db             (d/db mocked-conn)
                             :conn           mocked-conn
                             :transit-params char-id
                             :path-params    {:id (:db/id folder-b)}})
      (let [db         (d/db mocked-conn)
            a-chars    (d/q '[:find [?c ...]
                              :in $ ?f
                              :where [?f :orcpub.dnd.e5.folder/character-ids ?c]]
                            db (:db/id folder-a))
            b-chars    (d/q '[:find [?c ...]
                              :in $ ?f
                              :where [?f :orcpub.dnd.e5.folder/character-ids ?c]]
                            db (:db/id folder-b))]
        (testing "character is no longer in Folder A"
          (is (empty? a-chars)))
        (testing "character is in Folder B"
          (is (= [char-id] b-chars)))))))

;; ---------------------------------------------------------------------------
;; remove-character

(deftest test-remove-character
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          saved-char  (save-char! mocked-conn "alice")
          char-id     (:db/id saved-char)
          folder      (create-folder! mocked-conn "alice" "My Folder")
          folder-id   (:db/id folder)]
      ;; First add the character
      (folder/add-character {:db             (d/db mocked-conn)
                             :conn           mocked-conn
                             :transit-params char-id
                             :path-params    {:id folder-id}})
      ;; Then remove it
      (let [result (folder/remove-character {:conn        mocked-conn
                                             :path-params {:id           folder-id
                                                           :character-id (str char-id)}})]
        (testing "returns HTTP 200"
          (is (= 200 (:status result))))
        (testing "folder no longer contains the character"
          (let [char-ids (->> (:body result)
                              ::folder5e/character-ids
                              (map :db/id)
                              set)]
            (is (not (contains? char-ids char-id)))))))))

;; ---------------------------------------------------------------------------
;; delete-folder

(deftest test-delete-folder
  (with-conn conn
    (setup-db! conn)
    (let [mocked-conn (dm/fork-conn conn)
          saved-char  (save-char! mocked-conn "alice")
          char-id     (:db/id saved-char)
          folder      (create-folder! mocked-conn "alice" "Doomed Folder")
          folder-id   (:db/id folder)]
      ;; Add a character to the folder before deleting
      (folder/add-character {:db             (d/db mocked-conn)
                             :conn           mocked-conn
                             :transit-params char-id
                             :path-params    {:id folder-id}})
      (let [result (folder/delete-folder {:conn        mocked-conn
                                          :path-params {:id folder-id}})]
        (testing "returns HTTP 200"
          (is (= 200 (:status result))))
        (testing "folder entity is gone from db"
          (let [folder-entity (d/pull (d/db mocked-conn) '[*] folder-id)]
            (is (= {:db/id folder-id} folder-entity))))
        (testing "character entity is NOT deleted"
          (let [char-entity (d/pull (d/db mocked-conn) '[*] char-id)]
            (is (some? (::se/owner char-entity)))))))))
