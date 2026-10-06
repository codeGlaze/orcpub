(ns orcpub.save-replace-research-test
  "RESEARCH: pins TODAY's behaviour of the character save route (`routes/do-save-character`)
   when the stored character fails `::se/entity`. Each deftest is named for its claim and asserts
   what happens now, not what should. character-rescue.md, \"Save replaces an invalid character\"."
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

(deftest r2-fixing-an-invalid-stored-character-gives-it-a-new-id
  (doseq [[label bad] [["a key starting with a digit" {::se/key :1st-pick ::se/option {::se/key :x}}]
                       ["two selections with one key" {::se/key :race ::se/option {::se/key :x}}]]]
    (testing label
      (let [conn (fresh-conn) id (store! conn (character bad))
            _ (is (not (spec/valid? ::se/entity (d/pull (d/db conn) '[*] id))) "the stored copy fails the check")
            {:keys [status body]} (save! conn (without conn id (::se/key bad)))
            new-id (:db/id body)]
        (is (= 200 status))
        (is (not= id new-id) "a new id")
        (is (nil? (::se/owner (d/pull (d/db conn) '[*] id))) "the old id is no longer a character")
        (is (= 400 (:status (routes/get-character-for-id (d/db conn) id))) "its address now answers 400")
        (testing "R5: links by reference lose it; links by number point at the dead id"
          (is (= {:folder nil :party nil :share id} (links conn id))))
        (testing "R6: the new one is what was sent"
          (is (= "Beren" (get-in body [::se/values :orcpub.dnd.e5.character/character-name])))
          (is (= "kaylee" (::se/owner body))))))))

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
    (testing "duplicates: the app's next save is valid, so it takes the replace path"
      (let [id (store! conn (character {::se/key :race ::se/option {::se/key :x}}))
            round-trip (char5e/to-strict (char5e/from-strict (d/pull (d/db conn) '[*] id)))]
        (is (spec/valid? ::se/entity round-trip))
        (is (not= id (:db/id (:body (save! conn round-trip)))) "the app's own save changes the id")))
    (testing "a digit key: the app's next save is still invalid, so it is refused"
      (let [id (store! conn (character {::se/key :1st-pick ::se/option {::se/key :x}}))
            round-trip (char5e/to-strict (char5e/from-strict (d/pull (d/db conn) '[*] id)))]
        (is (not (spec/valid? ::se/entity round-trip)))
        (is (= 400 (:status (save! conn round-trip))))))))

(deftest r8-a-tab-still-holding-the-old-id-is-refused-as-not-yours
  (let [conn (fresh-conn)
        id (store! conn (character {::se/key :race ::se/option {::se/key :x}}))
        first-save (without conn id :race)
        other-tab (update (without conn id :race) ::se/values assoc :orcpub.dnd.e5.character/character-name "Renamed")]
    (is (not= id (:db/id (:body (save! conn first-save)))) "the first save moves it to a new id")
    (let [{:keys [status body]} (save! conn other-tab)]
      (is (= 401 status))
      (is (= "You do not own this character" body)))))

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
