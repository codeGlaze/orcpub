(ns orcpub.dnd.e5.fighting-style-grant-matrix-test
  "Proves the 4 GRANT MODES end-to-end on the feat silo — a character is built and the right
   fighting style lands on the sheet: ALL / FILTERED / SPECIFIC / CUSTOM. The grant compiler
   (opt5e/grant-selection) is silo-agnostic (see fighting-style-feat-e2e-test/grant-is-bucket-agnostic),
   so these modes establish the capability for every other silo too; wiring each remaining silo's
   one-line :grant hook (background/race/subrace/class/subclass) is the mechanical next increment.

   NOTE: built-in Dueling carries a cljs-only condition (@re-frame.db/app-db), so it is only OFFERED
   in compile-level checks here, never selected in a JVM build. JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
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
(defn- feat-of [grant]
  (opt5e/feat-option-from-cfg language-map spells5e/spell-map sl5e/spell-lists weapons5e/weapons-map {}
                              pools {:name "Style Adept" :key :style-adept :grant grant}))

(defn- template-of [grant]
  (t5e/template
   (concat
    (t5e/template-selections nil nil nil weapons5e/weapons-map weapons5e/weapons
                             sl5e/spell-lists spells5e/spell-map [] [] [] [] language-map)
    [(t/selection-cfg {:name "Bonus Feat" :key :bonus-feat :tags #{:feats}
                       :options [(feat-of grant)] :min 1 :max 1})])))

(def ^:private abilities
  {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 10 :orcpub.dnd.e5.character/con 10
   :orcpub.dnd.e5.character/int 10 :orcpub.dnd.e5.character/wis 10 :orcpub.dnd.e5.character/cha 10})

(defn- build-with [grant style-key]
  (entity/build
   {:orcpub.entity/options
    {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
     :bonus-feat {:orcpub.entity/key :style-adept
                  :orcpub.entity/options {:fighting-style {:orcpub.entity/key style-key}}}}}
   (template-of grant)))

(defn- trait-names [built] (set (map :name (char5e/traits built))))

(deftest feat-grants-each-mode-end-to-end
  (testing "ALL — pick any offered style; the custom one's mechanic lands"
    (is (= 30 (char5e/base-swimming-speed (build-with {:from :fighting-styles} :tidewalker)))))
  (testing "FILTERED — pick an allowed style; its trait lands"
    (is (contains? (trait-names (build-with {:from :fighting-styles :filter #{:archery :defense}} :archery))
                   "Archery Fighting Style")))
  (testing "SPECIFIC — the single granted built-in style lands"
    (is (contains? (trait-names (build-with {:from :fighting-styles :key :archery} :archery))
                   "Archery Fighting Style")))
  (testing "CUSTOM — the homebrew style's mechanic lands"
    (is (= 30 (char5e/base-swimming-speed (build-with {:from :fighting-styles :key :tidewalker} :tidewalker))))))
