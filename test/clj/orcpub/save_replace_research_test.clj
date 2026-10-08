(ns orcpub.save-replace-research-test
  "Pins the character save route (`routes/do-save-character`) when the stored character fails
   `::se/entity`: the save replaces its contents and keeps its id. Each deftest is named for its
   claim. character-rescue.md, \"Save replaces an invalid character\"."
  (:require [clojure.spec.alpha :as spec]
            [clojure.test :refer [deftest testing is]]
            [datomic.api :as d]
            [orcpub.common :as common]
            [orcpub.db.schema :as schema]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.routes :as routes])
  (:import [java.util UUID]))

(defn- fresh-conn
  "A connection to a new in-memory database with the app's schema and the user kaylee."
  []
  (let [uri (str "datomic:mem:save-replace-" (UUID/randomUUID))]
    (d/create-database uri)
    (let [conn (d/connect uri)]
      @(d/transact conn schema/all-schemas)
      @(d/transact conn [{:orcpub.user/username "kaylee" :orcpub.user/email "kaylee@example.com"}])
      conn)))

(defn- character
  "A strict character with a race and a feat, plus `extra` selections."
  [& extra]
  {::se/values {:orcpub.dnd.e5.character/character-name "Beren"}
   ::se/summary {:orcpub.dnd.e5.character/character-name "Beren"}
   ::se/selections (into [{::se/key :race ::se/option {::se/key :tidefolk}}
                          {::se/key :feats ::se/options [{::se/key :tidebreaker}]}]
                         extra)})

(defn- store!
  "Transact `strict` as kaylee's character directly, as data from before today's checks would sit,
   with a folder, a party and a share record pointing at it. Returns its id."
  [conn strict]
  (let [id (:db/id (routes/create-new-character conn strict "kaylee"))]
    @(d/transact conn [{:orcpub.dnd.e5.folder/owner "kaylee" :orcpub.dnd.e5.folder/name (str "F" id)
                        :orcpub.dnd.e5.folder/character-ids [id]}
                       {:orcpub.dnd.e5.party/owner "kaylee" :orcpub.dnd.e5.party/name (str "P" id)
                        :orcpub.dnd.e5.party/character-ids [id]}
                       {:orcpub.share/character id :orcpub.share/token (str "t" id)}])
    id))

(defn- without
  "The stored character at `id` as the client would send it back, minus the LAST selection keyed `k`."
  [conn id k]
  (update (d/pull (d/db conn) '[*] id) ::se/selections
          (fn [ss] (let [i (last (keep-indexed (fn [i s] (when (= k (::se/key s)) i)) ss))]
                     (into (subvec ss 0 i) (subvec ss (inc i)))))))

(defn- save!
  "Save `strict` as kaylee through the route's own function."
  [conn strict]
  (routes/do-save-character (d/db conn) conn strict {:user "kaylee"}))

(defn- links
  "Where the folder, party and share made for `id` now point."
  [conn id]
  (let [db (d/db conn)]
    {:folder (d/q '[:find ?c . :in $ ?n :where [?f :orcpub.dnd.e5.folder/name ?n] [?f :orcpub.dnd.e5.folder/character-ids ?c]] db (str "F" id))
     :party (d/q '[:find ?c . :in $ ?n :where [?p :orcpub.dnd.e5.party/name ?n] [?p :orcpub.dnd.e5.party/character-ids ?c]] db (str "P" id))
     :share (d/q '[:find ?c . :in $ ?t :where [?s :orcpub.share/token ?t] [?s :orcpub.share/character ?c]] db (str "t" id))}))

(deftest r1-a-valid-stored-character-keeps-its-id-and-links
  (let [conn (fresh-conn) id (store! conn (character))
        {:keys [status body]} (save! conn (without conn id :feats))]
    (is (= 200 status))
    (is (= id (:db/id body)))
    (is (= {:folder id :party id :share id} (links conn id)))))

(deftest r2-fixing-an-invalid-stored-character-keeps-its-id-and-replaces-its-contents
  (doseq [[label bad] [["a key starting with a digit" {::se/key :1st-pick ::se/option {::se/key :x}}]
                       ["two selections with one key" {::se/key :race ::se/option {::se/key :x}}]]]
    (testing label
      (let [conn (fresh-conn) id (store! conn (character bad))
            before (d/pull (d/db conn) '[*] id)
            old-children (disj (set (map :db/id (concat (::se/selections before) [(::se/values before) (::se/summary before)]))) nil)
            _ (is (not (spec/valid? ::se/entity before)) "the stored copy fails the check")
            {:keys [status body]} (save! conn (without conn id (::se/key bad)))
            after (d/pull (d/db conn) '[*] id)]
        (is (= 200 status))
        (is (= id (:db/id body)) "the same id")
        (is (spec/valid? ::se/entity after) "the stored copy now passes the check")
        (is (= 1 (count (filter #(= :race (::se/key %)) (::se/selections after)))))
        (is (not (re-find #":1st-pick" (pr-str after))))
        (is (empty? (filter #(seq (dissoc (d/pull (d/db conn) '[*] %) :db/id)) old-children))
            "none of the old contents is left behind")
        (is (= 200 (:status (routes/get-character-for-id (d/db conn) id))) "its address still answers")
        (testing "R5: folders, parties and share records still point at it"
          (is (= {:folder id :party id :share id} (links conn id))))
        (testing "R6: its contents are what was sent"
          (is (= "Beren" (get-in after [::se/values :orcpub.dnd.e5.character/character-name])))
          (is (= "Beren" (get-in after [::se/summary :orcpub.dnd.e5.character/character-name])))
          (is (= "kaylee" (::se/owner after)))
          (is (= :character (::se/type after))))))))

(deftest r3-saving-an-invalid-character-that-is-still-invalid-is-refused-and-changes-nothing
  (let [conn (fresh-conn)
        id (store! conn (character {::se/key :1st-pick ::se/option {::se/key :x}}))
        before (d/pull (d/db conn) '[*] id)
        {:keys [status]} (save! conn (without conn id :feats))]
    (is (= 400 status))
    (is (= before (d/pull (d/db conn) '[*] id)))))

(deftest r4-the-save-route-refuses-to-create-an-invalid-character
  (let [conn (fresh-conn)]
    (is (= 400 (:status (save! conn (character {::se/key :1st-pick ::se/option {::se/key :x}})))))
    (is (= 400 (:status (save! conn (character {::se/key :race ::se/option {::se/key :x}})))))))

(deftest r7-the-apps-own-round-trip-merges-duplicate-selections-but-keeps-a-digit-key
  (let [conn (fresh-conn)]
    (testing "duplicates: the app's next save is valid, takes the replace path, and keeps the id"
      (let [id (store! conn (character {::se/key :race ::se/option {::se/key :x}}))
            round-trip (char5e/to-strict (char5e/from-strict (d/pull (d/db conn) '[*] id)))]
        (is (spec/valid? ::se/entity round-trip))
        (is (= id (:db/id (:body (save! conn round-trip)))))
        (is (= {:folder id :party id :share id} (links conn id)))))
    (testing "a digit key: the app's next save is still invalid, so it is refused"
      (let [id (store! conn (character {::se/key :1st-pick ::se/option {::se/key :x}}))
            round-trip (char5e/to-strict (char5e/from-strict (d/pull (d/db conn) '[*] id)))]
        (is (not (spec/valid? ::se/entity round-trip)))
        (is (= 400 (:status (save! conn round-trip))))))))

(deftest r8-a-tab-holding-an-older-copy-saves-to-the-same-character
  (let [conn (fresh-conn)
        id (store! conn (character {::se/key :race ::se/option {::se/key :x}}))
        first-save (without conn id :race)
        other-tab (update (without conn id :race) ::se/values assoc :orcpub.dnd.e5.character/character-name "Renamed")]
    (is (= id (:db/id (:body (save! conn first-save)))))
    (let [{:keys [status body]} (save! conn other-tab)
          after (d/pull (d/db conn) '[*] id)]
      (is (= 200 status) "no longer refused as not yours")
      (is (= id (:db/id body)))
      (is (= "Renamed" (get-in after [::se/values :orcpub.dnd.e5.character/character-name])) "the later save wins, as between any two tabs")
      (is (spec/valid? ::se/entity after))
      (is (= 1 (count (filter #(= :race (::se/key %)) (::se/selections after)))) "the duplicate does not come back")
      (is (= {:folder id :party id :share id} (links conn id))))))

(deftest r11-another-players-party-keeps-the-character
  (let [conn (fresh-conn)
        id (store! conn (character {::se/key :race ::se/option {::se/key :x}}))]
    @(d/transact conn [{:orcpub.dnd.e5.party/owner "zoe" :orcpub.dnd.e5.party/name "Zoe's table"
                        :orcpub.dnd.e5.party/character-ids [id]}])
    (save! conn (without conn id :race))
    (is (= id (d/q '[:find ?c . :where [?p :orcpub.dnd.e5.party/name "Zoe's table"] [?p :orcpub.dnd.e5.party/character-ids ?c]] (d/db conn))))))

(deftest r9-the-save-check-refuses-a-pick-whose-key-starts-with-a-digit
  ;; The app cannot make such a pick today: the homebrew loader sets aside items whose key does
  ;; not start with a letter (library/invalid-keys; e2e digit-key-save.js). Older stored data can.
  (is (not (spec/valid? ::se/key :1st-strike)))
  (is (= 400 (:status (save! (fresh-conn) (character {::se/key :feats-2 ::se/options [{::se/key :1st-strike}]})))))
  (is (= :2nd-wind (common/name-to-kw "2nd Wind"))
      "a name's key is not repaired on the way in; the loader quarantines it and offers a rename"))

(deftest r10-two-saves-from-the-same-old-copy-store-two-selections-with-one-key
  (let [conn (fresh-conn)
        id (store! conn (character))
        stale-db (d/db conn)
        with-skills (fn [skill] (update (d/pull stale-db '[*] id) ::se/selections conj
                                        {::se/key :skills ::se/options [{::se/key skill}]}))]
    (is (= 200 (:status (routes/do-save-character stale-db conn (with-skills :stealth) {:user "kaylee"}))))
    (is (= 200 (:status (routes/do-save-character stale-db conn (with-skills :insight) {:user "kaylee"}))))
    (let [stored (d/pull (d/db conn) '[*] id)]
      (is (= 2 (count (filter #(= :skills (::se/key %)) (::se/selections stored)))))
      (is (not (spec/valid? ::se/entity stored)) "the stored character now fails the check"))))
