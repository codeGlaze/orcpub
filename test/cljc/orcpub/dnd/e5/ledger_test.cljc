(ns orcpub.dnd.e5.ledger-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.set]
            [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.ledger :as ledger]
            [orcpub.dnd.e5.picks :as picks]))

(def tide-pak
  "The saved Tide Pak wizard from orphan-clear.js, as the server returns it (ids trimmed)."
  (pr-str
   {:db/id 17592186045427
    :orcpub.entity.strict/owner "kaylee"
    :orcpub.entity.strict/values {:orcpub.dnd.e5.character/character-name "Beren Dale"}
    :orcpub.entity.strict/selections
    [{:orcpub.entity.strict/key :ability-scores
      :orcpub.entity.strict/option {:orcpub.entity.strict/key :standard-scores
                                    :orcpub.entity.strict/map-value {:orcpub.dnd.e5.character/str 15}}}
     {:orcpub.entity.strict/key :class
      :orcpub.entity.strict/options
      [{:orcpub.entity.strict/key :wizard
        :orcpub.entity.strict/selections
        [{:orcpub.entity.strict/key :levels
          :orcpub.entity.strict/options
          [{:orcpub.entity.strict/key :level-1} {:orcpub.entity.strict/key :level-2}
           {:orcpub.entity.strict/key :level-3}
           {:orcpub.entity.strict/key :level-4
            :orcpub.entity.strict/selections
            [{:orcpub.entity.strict/key :asi-or-feat
              :orcpub.entity.strict/option {:orcpub.entity.strict/key :feat}}]}]}
         {:orcpub.entity.strict/key :wizard-spells-known
          :orcpub.entity.strict/options [{:orcpub.entity.strict/key :brine-lash}]}]}]}
     {:orcpub.entity.strict/key :race :orcpub.entity.strict/option {:orcpub.entity.strict/key :tidefolk}}
     {:orcpub.entity.strict/key :feats
      :orcpub.entity.strict/options [{:orcpub.entity.strict/key :tidebreaker}]}]}))

(defn- character
  "The `tide-pak` fixture read as the page reads it (`ledger/read-character`)."
  []
  (:character (ledger/read-character tide-pak)))

(deftest rows-list-every-stored-pick-parents-first
  (let [rows (ledger/rows (character))
        by-address (into {} (map (juxt :address identity)) rows)]
    (is (= 10 (count rows)))
    (is (= {:address [:class :wizard :wizard-spells-known :brine-lash] :depth 1
            :section "Wizard Spells Known" :choice "Brine Lash" :key :brine-lash :value nil
            :below 0 :line "Class › Wizard › Wizard Spells Known › Brine Lash"}
           (by-address [:class :wizard :wizard-spells-known :brine-lash])))
    (testing "how many picks sit under each"
      (is (= 6 (:below (by-address [:class :wizard]))))
      (is (= 1 (:below (by-address [:class :wizard :levels :level-4]))))
      (is (= 0 (:below (by-address [:feats :tidebreaker])))))
    (testing "depth follows nesting"
      (is (= [0 1 2] (map (comp :depth by-address)
                          [[:class :wizard] [:class :wizard :levels :level-4]
                           [:class :wizard :levels :level-4 :asi-or-feat :feat]]))))
    (is (= "{:orcpub.dnd.e5.character/str 15}"
           (:value (by-address [:ability-scores :standard-scores])))
        "a stored value is shown as text")))

(deftest support-text-names-the-character-then-each-path
  (let [text (ledger/support-text "Character 1 (Beren Dale)" (ledger/rows (character)))]
    (is (= "Character 1 (Beren Dale)" (first (str/split-lines text))))
    (is (some #{"Feats › Tidebreaker"} (str/split-lines text)))
    (is (= "Beren Dale" (ledger/character-name (character))))))

(deftest read-character-reads-what-the-app-reads
  (is (map? (::entity/options (character))))
  (testing "a bare-colon key is repaired, as the app does"
    (let [text (str/replace tide-pak ":tidebreaker" ":")]
      (is (some #(= :unnamed-1 (:key %)) (ledger/rows (:character (ledger/read-character text)))))))
  (testing "what cannot be read is an error, never a blank character"
    (doseq [text ["{:a" "[1 2 3]" "{:name \"x\"}" ""]]
      (let [{parsed :character error :error} (ledger/read-character text)]
        (is (nil? parsed) (pr-str text))
        (is (string? error) (pr-str text))))))

(deftest only-the-owner-by-username-or-email
  (let [c (assoc (character) ::entity/owner "kaylee")]
    (is (ledger/owner? c {:user-data {:username "kaylee" :email "k@example.com"}}))
    (is (ledger/owner? (assoc c ::entity/owner "k@example.com")
                       {:user-data {:username "kaylee" :email "k@example.com"}}))
    (is (not (ledger/owner? c {:user-data {:username "zoe" :email "z@example.com"}})))
    (is (not (ledger/owner? c nil)) "logged out")
    (is (not (ledger/owner? (dissoc c ::entity/owner) {:user-data {:username nil :email nil}}))
        "no owner and no login never match")))

(def stored-with-ids
  "A saved character as the server returns it: every selection and option with its :db/id,
   plus a summary and the browser draft's :changed flag."
  {:db/id 1
   :changed true
   :orcpub.entity.strict/owner "kaylee"
   :orcpub.entity.strict/values {:db/id 2 :orcpub.dnd.e5.character/character-name "Beren Dale"}
   :orcpub.entity.strict/summary {:db/id 3 :orcpub.dnd.e5.character/race-name "Tidefolk"}
   :orcpub.entity.strict/selections
   [{:db/id 10 :orcpub.entity.strict/key :ability-scores
     :orcpub.entity.strict/option {:db/id 11 :orcpub.entity.strict/key :standard-scores
                                   :orcpub.entity.strict/map-value {:db/id 12 :orcpub.dnd.e5.character/str 15}}}
    {:db/id 20 :orcpub.entity.strict/key :class
     :orcpub.entity.strict/options
     [{:db/id 21 :orcpub.entity.strict/key :wizard
       :orcpub.entity.strict/selections
       [{:db/id 22 :orcpub.entity.strict/key :wizard-spells-known
         :orcpub.entity.strict/options [{:db/id 23 :orcpub.entity.strict/key :brine-lash}
                                        {:db/id 24 :orcpub.entity.strict/key :magic-missile}]}]}]}
    {:db/id 30 :orcpub.entity.strict/key :race :orcpub.entity.strict/option {:db/id 31 :orcpub.entity.strict/key :tidefolk}}
    {:db/id 40 :orcpub.entity.strict/key :feats
     :orcpub.entity.strict/options [{:db/id 41 :orcpub.entity.strict/key :tidebreaker}]}]})

(defn- ids
  "Every :db/id in `m`."
  [m]
  (entity/db-ids m))

(deftest saving-unchanged-writes-back-exactly-what-was-stored
  (let [text (pr-str stored-with-ids)]
    (is (= stored-with-ids
           (ledger/save-data (ledger/read-stored text) (:character (ledger/read-character text)))))))

(deftest saving-a-removal-drops-exactly-that-pick
  (let [text (pr-str stored-with-ids)
        opened (:character (ledger/read-character text))]
    (testing "one of several: only its option goes"
      (let [saved (ledger/save-data (ledger/read-stored text)
                                    (:character (picks/remove-at opened [:class :wizard :wizard-spells-known :brine-lash])))]
        (is (= #{23} (clojure.set/difference (ids stored-with-ids) (ids saved))))
        (is (= (:orcpub.entity.strict/summary stored-with-ids) (:orcpub.entity.strict/summary saved)) "the summary is kept")
        (is (true? (:changed saved)) "the draft's :changed flag is kept")))
    (testing "a single pick: its selection goes with it, so no empty selection is left"
      (let [saved (ledger/save-data (ledger/read-stored text)
                                    (:character (picks/remove-at opened [:race :tidefolk])))]
        (is (= #{30 31} (clojure.set/difference (ids stored-with-ids) (ids saved))))))
    (testing "a parent takes its children"
      (let [saved (ledger/save-data (ledger/read-stored text)
                                    (:character (picks/remove-at opened [:class :wizard])))]
        (is (= #{21 22 23 24} (clojure.set/difference (ids stored-with-ids) (ids saved))))))
    (testing "removing and putting back is no change at all"
      (let [{removed-char :character removed :removed} (picks/remove-at opened [:feats :tidebreaker])]
        (is (= stored-with-ids
               (ledger/save-data (ledger/read-stored text) (picks/put-at removed-char removed))))))))

(deftest a-change-since-opening-is-noticed-but-list-order-is-not
  (let [stored (ledger/read-stored (pr-str stored-with-ids))]
    (is (ledger/same-stored? stored (ledger/read-stored (pr-str stored-with-ids))))
    (is (ledger/same-stored? stored (update stored :orcpub.entity.strict/selections (comp vec reverse)))
        "the same selections in another order are the same character")
    (is (not (ledger/same-stored? stored (assoc-in stored [:orcpub.entity.strict/values :orcpub.dnd.e5.character/character-name] "Renamed"))))
    (is (not (ledger/same-stored? stored (update stored :orcpub.entity.strict/selections pop)))
        "a pick removed elsewhere is a change")))

(deftest pending-removals-apply-again-to-a-fresh-copy
  (let [opened (:character (ledger/read-character (pr-str stored-with-ids)))
        pend (fn [address line] (assoc (:removed (picks/remove-at opened address)) :line line))
        pending [(pend [:feats :tidebreaker] "Feats › Tidebreaker")
                 (pend [:class :wizard :wizard-spells-known :brine-lash] "Class › Wizard › Wizard Spells Known › Brine Lash")]
        fresh (:character (picks/remove-at opened [:class :wizard]))
        {again :character lost :lost kept :pending} (ledger/reapply fresh pending)]
    (is (= ["Class › Wizard › Wizard Spells Known › Brine Lash"] lost) "the spell went with its class elsewhere")
    (is (= [[:feats :tidebreaker]] (map :address kept)))
    (is (= "Feats › Tidebreaker" (:line (first kept))) "a kept record keeps its line for the list")
    (is (not-any? #(= [:feats :tidebreaker] (first %)) (picks/addresses again)))))

(deftest only-the-race-a-class-and-the-ability-scores-warn
  (let [rows (into {} (map (juxt :address identity)) (ledger/rows (:character (ledger/read-character (pr-str stored-with-ids)))))]
    (is (= "Remove Wizard and the 2 choices under it? The builder will ask for a class again."
           (ledger/warning (rows [:class :wizard]))))
    (is (= "Remove Tidefolk? The builder will ask for a race again." (ledger/warning (rows [:race :tidefolk]))))
    (is (re-find #"will not save this character" (ledger/warning (rows [:ability-scores :standard-scores]))))
    (is (nil? (ledger/warning (rows [:feats :tidebreaker]))))
    (is (nil? (ledger/warning (rows [:class :wizard :wizard-spells-known :brine-lash]))) "a choice under a class does not warn")))
