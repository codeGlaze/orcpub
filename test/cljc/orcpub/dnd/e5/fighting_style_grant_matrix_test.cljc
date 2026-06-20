(ns orcpub.dnd.e5.fighting-style-grant-matrix-test
  "Proves the 4 GRANT MODES end-to-end on the feat silo — a character is built and the right
   fighting style lands on the sheet: ALL / FILTERED / SPECIFIC / CUSTOM. The grant compiler
   (opt5e/grant-selection) is silo-agnostic (see fighting-style-feat-e2e-test/grant-is-bucket-agnostic),
   so these modes establish the capability for every other silo too; wiring each remaining silo's
   one-line :grant hook (background/race/subrace/class/subclass) is the mechanical next increment.

   NOTE: built-in Dueling carries a cljs-only condition (@re-frame.db/app-db), so it is only OFFERED
   in compile-level checks here, never selected in a JVM build. JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

;; MODE 4 — a max-flexibility CUSTOM fighting style authored as data (name + a :props mechanic).
(def custom-style
  (opt5e/fighting-style-option
   {:name "Tidewalker" :key :tidewalker
    :description "You gain a swimming speed of 30 feet." :props {:swimming-speed 30}}))

;; the open pool: built-in styles ++ the custom one
(def pools
  {:fighting-styles {:name "Fighting Style"
                     :options (concat opt5e/fighting-style-options [custom-style])}})

;; ---------------------------------------------------------------------------
;; Compile level: each mode offers the right options
;; ---------------------------------------------------------------------------
(defn- offered [grant] (set (map ::t/name (::t/options (opt5e/grant-selection grant pools)))))

(deftest the-four-modes-offer-the-right-options
  (testing "ALL — every built-in style plus the custom one"
    (let [o (offered {:from :fighting-styles})]
      (is (contains? o "Archery")) (is (contains? o "Dueling")) (is (contains? o "Tidewalker"))))
  (testing "FILTERED — only the allowed subset"
    (is (= #{"Archery" "Defense"} (offered {:from :fighting-styles :filter #{:archery :defense}}))))
  (testing "SPECIFIC — exactly the one named (built-in)"
    (is (= #{"Dueling"} (offered {:from :fighting-styles :key :dueling}))))
  (testing "CUSTOM — the homebrew style, addressable by key"
    (is (= #{"Tidewalker"} (offered {:from :fighting-styles :key :tidewalker})))))

;; ---------------------------------------------------------------------------
;; End-to-end on the FEAT silo: build a character per mode, the chosen style's mechanic lands
;; ---------------------------------------------------------------------------
(defn- feat-of [pool grant]
  (opt5e/feat-option-from-cfg language-map spells5e/spell-map sl5e/spell-lists weapons5e/weapons-map {}
                              pool {:name "Style Adept" :key :style-adept :grant grant}))

(defn- template-of [pool grant]
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             sl5e/spell-lists spells5e/spell-map [] [] [] [] language-map)
    [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                       :options [(feat-of pool grant)] :min 1 :max 1})])))

(def ^:private abilities
  {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10 :orcpub.dnd.e5.character/con 10
   :orcpub.dnd.e5.character/int 10 :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10})

(defn- build-with [pool grant style-key]
  (entity/build
   {:orcpub.entity/options
    {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
     :bonus-feat {:orcpub.entity/key :style-adept
                  :orcpub.entity/options {:fighting-style {:orcpub.entity/key style-key}}}}}
   (template-of pool grant)))

(defn- trait-names [built] (set (map :name (char5e/traits built))))

(deftest feat-grants-each-mode-end-to-end
  (testing "ALL — pick any offered style; the custom one's mechanic lands"
    (is (= 30 (char5e/base-swimming-speed (build-with pools {:from :fighting-styles} :tidewalker)))))
  (testing "FILTERED — pick an allowed style; its trait lands"
    (is (contains? (trait-names (build-with pools {:from :fighting-styles :filter #{:archery :defense}} :archery))
                   "Archery Fighting Style")))
  (testing "SPECIFIC — the single granted built-in style lands"
    (is (contains? (trait-names (build-with pools {:from :fighting-styles :key :archery} :archery))
                   "Archery Fighting Style")))
  (testing "CUSTOM — the homebrew style's mechanic lands"
    (is (= 30 (char5e/base-swimming-speed (build-with pools {:from :fighting-styles :key :tidewalker} :tidewalker))))))

;; ---------------------------------------------------------------------------
;; MANY custom styles — once created, they are reachable by ALL / FILTER / SPECIFIC too
;; (answers "could I make a hundred custom styles and have them selectable/filterable?").
;; 20 here stands in for arbitrary N — the pool is plain concat + the filter is by ::t/key,
;; so it's O(N) and uniform; nothing caps the count. Each needs a distinct key (from its name).
;; ---------------------------------------------------------------------------
(def ^:private many-custom
  (mapv (fn [i]
          (opt5e/fighting-style-option
           {:name (str "Custom Style " i) :key (keyword (str "custom-" i))
            :description (str "Custom number " i ".")
            :props {:swimming-speed (* 10 i)}}))      ; distinct, readable mechanic per style
        (range 1 21)))

(def ^:private big-pool
  {:fighting-styles {:name "Fighting Style"
                     :options (concat opt5e/fighting-style-options many-custom)}})

(deftest many-custom-styles-are-reachable-by-every-mode
  (let [offered* (fn [grant] (set (map ::t/name (::t/options (opt5e/grant-selection grant big-pool)))))]
    (testing "ALL — every one of the 20 customs is offered (plus the built-ins)"
      (let [o (offered* {:from :fighting-styles})]
        (is (= 20 (count (filter #(str/starts-with? % "Custom Style ") o))))
        (is (contains? o "Custom Style 1")) (is (contains? o "Custom Style 20")) (is (contains? o "Archery"))))
    (testing "FILTERED — a filter over custom keys offers exactly those customs"
      (is (= #{"Custom Style 3" "Custom Style 7" "Custom Style 15"}
             (offered* {:from :fighting-styles :filter #{:custom-3 :custom-7 :custom-15}}))))
    (testing "SPECIFIC — granting one custom by key offers exactly it"
      (is (= #{"Custom Style 12"} (offered* {:from :fighting-styles :key :custom-12})))))
  (testing "END-TO-END — a character can pick an arbitrary custom (Custom Style 7) and its mechanic lands"
    (is (= 70 (char5e/base-swimming-speed (build-with big-pool {:from :fighting-styles} :custom-7))))))
