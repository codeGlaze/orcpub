(ns orcpub.dnd.e5.warlock-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.modifiers :as mod5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

;;; ---------------------------------------------------------------------------
;;; Inline configs for data that lives in .cljs or is #_ commented in prod.
;;; See docs/ENTITY-BUILD.md for format reference.
;;; ---------------------------------------------------------------------------

(def test-languages
  [{:name "Common" :key :common}
   {:name "Elvish" :key :elvish}
   {:name "Dwarvish" :key :dwarvish}
   {:name "Giant" :key :giant}
   {:name "Gnomish" :key :gnomish}
   {:name "Goblin" :key :goblin}
   {:name "Halfling" :key :halfling}
   {:name "Orc" :key :orc}
   {:name "Abyssal" :key :abyssal}
   {:name "Celestial" :key :celestial}
   {:name "Draconic" :key :draconic}
   {:name "Deep Speech" :key :deep-speech}
   {:name "Infernal" :key :infernal}
   {:name "Primordial" :key :primordial}
   {:name "Sylvan" :key :sylvan}
   {:name "Undercommon" :key :undercommon}])

(def language-map (common/map-by-key test-languages))

;; Elf race config (from spell_subs.cljs elf-option-cfg, Drow subrace uncommented)
(def elf-race-cfg
  {:name "Elf"
   :key :elf
   :abilities {:orcpub.dnd.e5.character/dex 2}
   :size :medium
   :speed 30
   :languages ["Elvish" "Common"]
   :darkvision 60
   :modifiers [(mod5e/saving-throw-advantage [:charmed])
               (mod5e/immunity :magical-sleep)
               (mod5e/skill-proficiency :perception)]
   :subraces
   [{:name "Dark Elf (Drow)"
     :abilities {:orcpub.dnd.e5.character/cha 1}
     :darkvision 120
     :modifiers [(mod5e/weapon-proficiency :rapier)
                 (mod5e/weapon-proficiency :shortsword)
                 (mod5e/weapon-proficiency :crossbow-hand)]
     :traits [{:name "Sunlight Sensitivity"
               :summary "Disadvantage on attack and perception rolls in direct sunlight"}]}]
   :traits [{:name "Fey Ancestry" :summary "advantage on charmed saves; immune to sleep magic"}
            {:name "Trance" :summary "Trance 4 hrs. instead of sleep 8"}]})

;; Spy background (Criminal variant, from spell_subs.cljs)
(def spy-bg-cfg
  {:name "Spy"
   :key :spy
   :profs {:skill {:deception true :stealth true}}
   :traits [{:name "Criminal Contact"
             :summary "reliable criminal underworld contact"}]})

;; Keen Mind feat (from options.cljc:1311, #_ commented in prod)
;; Uses feat-option-from-cfg format: :ability-increases is a SET of ability keys
(def keen-mind-cfg
  {:name "Keen Mind"
   :key :keen-mind
   :description "increase INT by 1; always know north; recall anything within a month"
   :ability-increases #{:orcpub.dnd.e5.character/int}})

;;; ---------------------------------------------------------------------------
;;; Template — real cljc data + inline configs
;;; ---------------------------------------------------------------------------

(def test-template
  (t5e/template
   (t5e/template-selections
    nil                                   ; magic-weapon-options
    nil                                   ; magic-armor-options
    nil                                   ; other-magic-item-options
    weapons5e/weapons-map                 ; weapon-map
    weapons5e/weapons                     ; custom-and-standard-weapons
    sl5e/spell-lists                      ; spell-lists (real PHB data)
    spells5e/spell-map                    ; spells-map (real spell definitions)
    [spy-bg-cfg]                          ; backgrounds
    [elf-race-cfg]                        ; races
    [(classes5e/warlock-option            ; classes (pre-built option)
      sl5e/spell-lists
      spells5e/spell-map
      {}                                  ; no plugin subclasses
      language-map
      weapons5e/weapons-map
      []                                  ; no plugin invocations
      [])]                                ; no plugin boons
    [keen-mind-cfg]                        ; feats
    language-map)))                        ; language-map

;;; ---------------------------------------------------------------------------
;;; Raw entity — a level-10 Drow Elf Warlock / Spy / Keen Mind
;;; ---------------------------------------------------------------------------

(def base-abilities
  {:orcpub.dnd.e5.character/str 10
   :orcpub.dnd.e5.character/dex 11
   :orcpub.dnd.e5.character/con 11
   :orcpub.dnd.e5.character/int 15
   :orcpub.dnd.e5.character/wis 14
   :orcpub.dnd.e5.character/cha 15})

(def selected-skill-profs #{:intimidation :history})

(def warlock-entity
  {:orcpub.entity/options
   {:race
    {:orcpub.entity/key :elf
     :orcpub.entity/options
     {:subrace {:orcpub.entity/key :dark-elf-drow-}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value base-abilities}
    :alignment {:orcpub.entity/key :chaotic-neutral}
    :background
    {:orcpub.entity/key :spy}
    :feats
    [{:orcpub.entity/key :keen-mind}]
    :class
    [{:orcpub.entity/key :warlock
      :orcpub.entity/options
      {:skill-proficiency
       (mapv
        (fn [kw] {:orcpub.entity/key kw})
        selected-skill-profs)
       :levels
       [{:orcpub.entity/key :level-1
         :orcpub.entity/options
         {:otherworldly-patron {:orcpub.entity/key :the-archfey}}}
        {:orcpub.entity/key :level-2
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-3
         :orcpub.entity/options
         {:pact-boon
          {:orcpub.entity/key :pact-of-the-tome
           :orcpub.entity/options
           {:book-of-shadows-cantrips
            [{:orcpub.entity/key :blade-ward}
             {:orcpub.entity/key :spare-the-dying}
             {:orcpub.entity/key :thorn-whip}]}}
          :hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-4
         :orcpub.entity/options
         {:asi-or-feat {:orcpub.entity/key :feat}
          :hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-5
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-6
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-7
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-8
         :orcpub.entity/options
         {:asi-or-feat {:orcpub.entity/key :feat}
          :hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-9
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}
        {:orcpub.entity/key :level-10
         :orcpub.entity/options
         {:hit-points
          {:orcpub.entity/key :average :orcpub.entity/value 5}}}]
       :eldritch-invocations
       [{:orcpub.entity/key :book-of-ancient-secrets
         :orcpub.entity/options
         {:book-of-ancient-secrets-rituals
          [{:orcpub.entity/key :detect-poison-and-disease}
           {:orcpub.entity/key :illusory-script}]}}
        {:orcpub.entity/key :agonizing-blast}
        {:orcpub.entity/key :beast-speech}
        {:orcpub.entity/key :eyes-of-the-rune-keeper}
        {:orcpub.entity/key :mire-the-mind}]
       :warlock-cantrips-known
       [{:orcpub.entity/key :friends}
        {:orcpub.entity/key :minor-illusion}
        {:orcpub.entity/key :prestidigitation}
        {:orcpub.entity/key :eldritch-blast}]
       :warlock-spells-known
       [{:orcpub.entity/key :charm-person}
        {:orcpub.entity/key :hellish-rebuke}
        {:orcpub.entity/key :comprehend-languages}
        {:orcpub.entity/key :ray-of-enfeeblement}
        {:orcpub.entity/key :sleep}
        {:orcpub.entity/key :hold-monster}
        {:orcpub.entity/key :dominate-person}
        {:orcpub.entity/key :crown-of-madness}
        {:orcpub.entity/key :fear}
        {:orcpub.entity/key :faerie-fire}]
       :starting-equipment-equipment-pack
       {:orcpub.entity/key :dungeoneers-pack}
       :starting-equipment-simple-weapon {:orcpub.entity/key :dagger}
       :starting-equipment-spellcasting-equipment
       {:orcpub.entity/key :component-pouch}
       :starting-equipment-weapon
       {:orcpub.entity/key :any-simple-weapon
        :orcpub.entity/options
        {:starting-equipment-simple-weapon
         {:orcpub.entity/key :shortbow}}}}}]}})

;;; ---------------------------------------------------------------------------
;;; Tests
;;; ---------------------------------------------------------------------------

(defn has-spell? [built-char level class-nm spell-key]
  (let [spells-known (char5e/spells-known built-char)]
    (get-in spells-known [level [class-nm spell-key]])))

(deftest build-smoke-test
  (testing "entity/build doesn't throw with a real template"
    (let [built (entity/build warlock-entity test-template)]
      (is (some? built) "build should return a non-nil result"))))

(deftest warlock-ability-scores
  (testing "ability scores include racial and feat bonuses"
    (let [built (entity/build warlock-entity test-template)
          abilities (char5e/ability-values built)]
      ;; STR: 10 base, no bonuses
      (is (= 10 (:orcpub.dnd.e5.character/str abilities)))
      ;; DEX: 11 base + 2 elf racial
      (is (= 13 (:orcpub.dnd.e5.character/dex abilities)))
      ;; CON: 11 base, no bonuses
      (is (= 11 (:orcpub.dnd.e5.character/con abilities)))
      ;; INT: 15 base + 1 keen mind feat
      (is (= 16 (:orcpub.dnd.e5.character/int abilities)))
      ;; WIS: 14 base, no bonuses
      (is (= 14 (:orcpub.dnd.e5.character/wis abilities)))
      ;; CHA: 15 base + 1 drow subrace
      (is (= 16 (:orcpub.dnd.e5.character/cha abilities))))))

(deftest warlock-race-and-subrace
  (testing "race and subrace names are set correctly"
    (let [built (entity/build warlock-entity test-template)]
      (is (= "Elf" (char5e/race built)))
      (is (= "Dark Elf (Drow)" (char5e/subrace built))))))

(deftest warlock-skill-proficiencies
  (testing "skill profs from elf, spy background, and warlock class"
    (let [built (entity/build warlock-entity test-template)
          skill-profs (char5e/skill-proficiencies built)]
      ;; elf: perception
      (is (get skill-profs :perception) "elf should grant perception")
      ;; spy background: deception, stealth
      (is (get skill-profs :deception) "spy should grant deception")
      (is (get skill-profs :stealth) "spy should grant stealth")
      ;; warlock class selection: intimidation, history
      (is (get skill-profs :intimidation) "warlock should grant intimidation")
      (is (get skill-profs :history) "warlock should grant history")
      ;; 5 total
      (is (= 5 (count skill-profs))))))

(deftest warlock-class-levels
  (testing "warlock has 10 levels"
    (let [built (entity/build warlock-entity test-template)
          levels (char5e/levels built)]
      (is (= 10 (get-in levels [:warlock :class-level]))))))

(deftest warlock-speed
  (testing "elf base speed is 30"
    (let [built (entity/build warlock-entity test-template)]
      (is (= 30 (char5e/base-land-speed built))))))

(deftest warlock-spells
  (testing "special spells from invocations and pacts appear in spells-known"
    (let [built (entity/build warlock-entity test-template)]
      ;; Book of Ancient Secrets ritual (the original test's assertion)
      (is (has-spell? built 1 "Warlock" :illusory-script))
      ;; Book of Shadows cantrip
      (is (has-spell? built 0 "Warlock" :spare-the-dying))
      ;; Beast Speech invocation grants speak-with-animals at will
      (is (has-spell? built 1 "Warlock" :speak-with-animals)))))
