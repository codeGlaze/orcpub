(ns orcpub.dnd.e5.option-sources-characterization-test
  "CHARACTERIZATION of the option-source gate — the trap sitting in front of any
   plan that tags content with a real `:source` (SRD 5.2.x, or reviving the book
   plugins).

   The claim under test, stated so it can fail:

     `?option-sources` is never populated at runtime, so the one live call to
     `opt5e/using-source?` fails CLOSED — a spell carrying any `:source` other
     than `:phb` is silently dropped from a `:known-mode :all` caster's known
     spells, with no error and no user-visible signal.

   Why it's shaped this way (traced, not assumed):
     - The gate is `options.cljc` in the class spellcasting block, inside
       `(when (= :all (:known-mode spellcasting)) ...)`, passed as a CONDITION to
       the `spells-known-cfg` macro.
     - `mods/apply-modifiers` does `passes-conds? (every? #(% e) conditions)` and
       SKIPS the modifier when false — hence silent, not an error.
     - `:known-mode :all` is cleric / druid / paladin only, so those three classes
       are the whole blast radius. Cleric is the probe here.
     - The only two writers of `?option-sources` (`homebrew-plugin` and
       `optional-content-selection`, template.cljc) are `#_`-discarded, and have
       been since 2bad9a6d (Larry Christensen, 2017-10-26, \"clean up some
       performance issues\"). Nothing else sets it.

   These tests PIN CURRENT BEHAVIOR, including the broken part. When the source
   picker is revived, `srd-520-tagged-spell-is-silently-dropped` is expected to
   flip — that flip is the fix landing, and this test is how you'll know."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

;; ── the pure gate ───────────────────────────────────────────────────────────

(deftest using-source?-is-phb-or-nothing
  (testing "with no option-sources selected (the live state), only untagged and :phb pass"
    (is (true? (boolean (opt5e/using-source? nil nil)))
        "untagged content passes — this is why the SRD-only app works at all")
    (is (true? (boolean (opt5e/using-source? nil :phb)))
        ":phb passes unconditionally — it is a 'do not filter me' sentinel, not a source")
    (is (false? (boolean (opt5e/using-source? nil :srd-520)))
        "ANY other source fails closed when option-sources is empty")
    (is (false? (boolean (opt5e/using-source? nil :scag)))
        "including the book sources the discarded picker used to enable"))
  (testing "a populated option-sources would let it through — the mechanism is sound, its input is dead"
    (is (true? (boolean (opt5e/using-source? {:srd-520 true} :srd-520))))))

;; ── the consequence, through a real build ───────────────────────────────────

(def probe-spells
  "Three otherwise-identical level-1 cleric spells differing ONLY by :source."
  {:probe-untagged {:name "Probe Untagged" :level 1 :school "evocation"
                    :casting-time "1 action" :range "30 feet" :duration "1 round"
                    :components {:verbal true}
                    :description "Probe spell with no :source key."}
   :probe-phb      {:name "Probe Phb" :level 1 :school "evocation"
                    :casting-time "1 action" :range "30 feet" :duration "1 round"
                    :components {:verbal true} :source :phb
                    :description "Probe spell tagged :phb."}
   :probe-srd520   {:name "Probe Srd520" :level 1 :school "evocation"
                    :casting-time "1 action" :range "30 feet" :duration "1 round"
                    :components {:verbal true} :source :srd-520
                    :description "Probe spell tagged :srd-520, as the SRD 5.2 import tags it."}})

(def spells-map (merge spells5e/spell-map probe-spells))

(def spell-lists
  (update-in sl5e/spell-lists [:cleric 1] concat (keys probe-spells)))

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(def cleric-option
  (classes5e/cleric-option spell-lists spells-map {} language-map weapons5e/weapons-map))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil
    weapons5e/weapons-map weapons5e/weapons
    spell-lists spells-map
    [] []                ; backgrounds, races
    [cleric-option]      ; classes
    []                   ; feats
    language-map)))

(def cleric-entity
  {:orcpub.entity/options
   {:ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value {::char5e/str 10 ::char5e/dex 10 ::char5e/con 10
                           ::char5e/int 10 ::char5e/wis 16 ::char5e/cha 10}}
    :class
    [{:orcpub.entity/key :cleric
      :orcpub.entity/options
      {:levels [{:orcpub.entity/key :level-1
                 :orcpub.entity/options
                 {:hit-points {:orcpub.entity/key :average :orcpub.entity/value 5}}}]}}]}})

(def built (delay (entity/build cleric-entity test-template)))

(defn known-keys []
  (set (map second (mapcat keys (vals (char5e/spells-known @built))))))

(deftest option-sources-is-never-populated
  (testing "the ROOT CAUSE: nothing writes ?option-sources, so the gate's input is always empty"
    (is (some? @built) "the cleric must build at all")
    (is (empty? (char5e/option-sources @built))
        "no selection populates :option-sources — both writers are #_-discarded in template.cljc")))

(deftest probe-spells-are-on-the-cleric-list
  (testing "all three probes ARE offered — so a miss below is the gate, not a missing list entry"
    (let [lvl-1 (set (get-in spell-lists [:cleric 1]))]
      (is (contains? lvl-1 :probe-untagged))
      (is (contains? lvl-1 :probe-phb))
      (is (contains? lvl-1 :probe-srd520)))))

(deftest untagged-and-phb-spells-are-known
  (testing "the control: identical spells with no source / :phb reach the built sheet"
    (let [known (known-keys)]
      (is (contains? known :probe-untagged))
      (is (contains? known :probe-phb)))))

(deftest srd-520-tagged-spell-is-silently-dropped
  (testing "THE BUG: same spell, only :source differs, and it vanishes with no error"
    (is (not (contains? (known-keys) :probe-srd520))
        (str "EXPECTED FAILURE ONCE FIXED. A :source :srd-520 spell is dropped from a "
             "cleric's known spells because ?option-sources is empty. Tagging SRD 5.2 "
             "content before reviving the source picker would look like a broken import."))))
