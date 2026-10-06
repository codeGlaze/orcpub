(ns orcpub.dnd.e5.ledger-test
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.ledger :as ledger]))

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
      (let [{:keys [character error]} (ledger/read-character text)]
        (is (nil? character) (pr-str text))
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
