(ns orcpub.dnd.e5.multiclass-hidden-pick-test
  "The real-world case of `hidden-selection-picks.md`: a skill picked from a class's MULTICLASS
   options, after that class becomes the character's first class.

   `class-option` offers two mutually exclusive skill selections per class —
     :skill-proficiency            gated `first-class?`              (choose 4, for a rogue)
     :multiclass-skill-proficiency gated `(complement first-class?)` (choose 1)
   Both are always in the template; the gate only decides which the builder SHOWS.

   Reorder the classes and the gate flips. The question is what happens to the pick already
   stored under the arm that just lost its gate."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

(def ^:private abilities
  {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
   ::char5e/int 10 ::char5e/wis 10 ::char5e/cha 10})

(def ^:private the-template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     []                                                    ; backgrounds
     []                                                    ; races
     [(classes5e/fighter-option sl5e/spell-lists spells5e/spell-map {} language-map
                                weapons5e/weapons-map)
      (classes5e/rogue-option sl5e/spell-lists spells5e/spell-map {} language-map
                              weapons5e/weapons-map)]
     []                                                    ; feats
     language-map))))

(defn- lvl [n] {:orcpub.entity/key (keyword (str "level-" n))
                :orcpub.entity/options {:hit-points {:orcpub.entity/key :average
                                                     :orcpub.entity/value 5}}})

;; The rogue level, carrying a MULTICLASS skill pick — what a player stores when rogue is the
;; second class.
(def ^:private rogue-multiclassed
  {:orcpub.entity/key :rogue
   :orcpub.entity/options {:multiclass-skill-proficiency [{:orcpub.entity/key :athletics}]
                           :levels [(lvl 1)]}})

(def ^:private fighter-first
  {:orcpub.entity/key :fighter
   :orcpub.entity/options {:skill-proficiency [{:orcpub.entity/key :survival}
                                               {:orcpub.entity/key :intimidation}]
                           :levels [(lvl 1)]}})

(defn- build [classes]
  (entity/build
   {:orcpub.entity/options
    {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
     :class classes}}
   @the-template))

(defn- offered
  "The skill-proficiency selections the builder would SHOW — after prereq filtering."
  [classes]
  (let [char {:orcpub.entity/options
              {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
               :class classes}}]
    (->> (entity/get-all-selections-2 @the-template (entity/make-path-map char) (build classes))
         (filter #(contains? (::t/tags %) :skill-profs))
         (map (fn [s] [(::t/name s) (::entity/path s)])))))

(def ^:private rogue-first
  "A rogue taken FIRST — four skills from its own arm."
  {:orcpub.entity/key :rogue
   :orcpub.entity/options {:skill-proficiency [{:orcpub.entity/key :stealth}
                                               {:orcpub.entity/key :perception}
                                               {:orcpub.entity/key :acrobatics}
                                               {:orcpub.entity/key :deception}]
                           :levels [(lvl 1)]}})

(def ^:private rogue-both-arms
  "The realistic wreck: a rogue carrying BOTH a first-class pick and a multiclass pick, because
   the character was multiclassed and then the other class was dropped."
  {:orcpub.entity/key :rogue
   :orcpub.entity/options {:skill-proficiency [{:orcpub.entity/key :stealth}
                                               {:orcpub.entity/key :perception}
                                               {:orcpub.entity/key :acrobatics}
                                               {:orcpub.entity/key :deception}]
                           :multiclass-skill-proficiency [{:orcpub.entity/key :athletics}]
                           :levels [(lvl 1)]}})

(defn- skills-of [classes]
  (set (keys (char5e/skill-proficiencies (build classes)))))

(deftest class-skill-matrix-characterization
  ;; Part A step 1 of plan-hidden-pick-fix-and-grant-fields.md. Pins every class configuration
  ;; BEFORE the modifiers gain their per-arm conditions, so the fix shows as a diff in expected
  ;; values. Only the two rows marked BUG may move.
  ;;
  ;; Fighter is the control: it has no :multiclass-skill-options at all (only bard, ranger and
  ;; rogue do), so a fighter can never carry a stale multiclass pick.
  (testing "single class, own arm — must not move"
    (is (= #{:intimidation :survival} (skills-of [fighter-first])))
    (is (= #{:stealth :perception :acrobatics :deception} (skills-of [rogue-first]))))

  (testing "legitimately multiclassed — must not move"
    (is (= #{:athletics :intimidation :survival} (skills-of [fighter-first rogue-multiclassed]))
        "the rogue's multiclass skill applies on top of the fighter's two"))

  (testing "FIXED — a multiclass pick stops applying once the rogue is the only class"
    (is (= #{} (skills-of [rogue-multiclassed]))
        "was #{:athletics}; the modifier now carries [(not= cls-kw (first ?classes))]"))

  (testing "FIXED — both arms stored, rogue alone: the stale multiclass skill drops"
    (is (= #{:stealth :perception :acrobatics :deception}
           (skills-of [rogue-both-arms]))
        "was that plus :athletics; the four first-class picks are untouched")))

(deftest ^:diagnostic report-multiclass-reorder
  (println "\n=== a rogue's MULTICLASS skill pick, before and after rogue becomes first class ===")
  (doseq [[label classes] [["fighter first, rogue multiclassed" [fighter-first rogue-multiclassed]]
                           ["fighter dropped, rogue is first"   [rogue-multiclassed]]]]
    (println "\n" label)
    (println "   skills: " (pr-str (sort (keys (char5e/skill-proficiencies (build classes))))))
    (doseq [[nm path] (offered classes)]
      (println (format "   offered: %-34s at %s" nm (pr-str path))))))

(defn- offered-names [classes] (set (map first (offered classes))))
(defn- offered-paths [classes] (set (map second (offered classes))))

(deftest a-multiclass-skill-pick-stops-applying-when-it-becomes-the-first-class
  (let [multiclassed [fighter-first rogue-multiclassed]
        rogue-only   [rogue-multiclassed]]

    (testing "multiclassed: the rogue offers its MULTICLASS skill pick, and it lands"
      (is (contains? (offered-paths multiclassed) [:class :rogue :multiclass-skill-proficiency]))
      (is (= [:athletics :intimidation :survival]
             (sort (keys (char5e/skill-proficiencies (build multiclassed)))))))

    (testing "drop the fighter and the FIGHTER's skills go, correctly — the class left the template"
      (is (not (contains? (set (keys (char5e/skill-proficiencies (build rogue-only))))
                          :intimidation))))

    (testing "FIXED — the rogue's multiclass pick no longer applies once rogue is first"
      (is (not (contains? (set (keys (char5e/skill-proficiencies (build rogue-only)))) :athletics))
          "before the fix this was the defect: the skill stayed with no control to remove it"))

    (testing "the control is still gone, and that is now harmless"
      (is (not (contains? (offered-paths rogue-only)
                          [:class :rogue :multiclass-skill-proficiency]))
          "the multiclass selection is still filtered out by its (complement first-class?) prereq
           — but the stored pick it held no longer reaches the sheet, so there is nothing
           stranded behind the missing control"))

    (testing "meanwhile the first-class selection opens, on top of the skill already held"
      (is (contains? (offered-paths rogue-only) [:class :rogue :skill-proficiency])
          "a rogue chooses 4 skills as a first class — so this character can reach 5"))))

;; ---------------------------------------------------------------------------
;; The same gate, on STARTING EQUIPMENT — five of the nine prereq-fn sites.
;;
;; You get starting equipment from your FIRST class only, so every equipment selection carries
;; `first-class?` (options.cljc:2672, :2682, :2735, :2798, :2811). Reorder the classes and the
;; gate flips exactly as it does for skills — but the payload is gear, which a player sees.
;; ---------------------------------------------------------------------------

(def ^:private fighter-with-pack
  {:orcpub.entity/key :fighter
   :orcpub.entity/options {:starting-equipment-equipment-pack
                           [{:orcpub.entity/key :explorers-pack}]
                           :levels [(lvl 1)]}})

(def ^:private rogue-plain
  {:orcpub.entity/key :rogue :orcpub.entity/options {:levels [(lvl 1)]}})

(defn- equipment-keys [classes]
  (set (keys (char5e/normal-equipment-inventory (build classes)))))

(defn- equipment-selections [classes]
  (let [char {:orcpub.entity/options
              {:ability-scores {:orcpub.entity/key :standard-roll :orcpub.entity/value abilities}
               :class classes}}]
    (->> (entity/get-all-selections-2 @the-template (entity/make-path-map char) (build classes))
         (filter #(contains? (::t/tags %) :equipment))
         (map ::entity/path)
         set)))

(deftest ^:diagnostic report-equipment-across-a-class-reorder
  (println "\n=== starting equipment, when the granting class stops being first ===")
  (doseq [[label classes] [["fighter first"            [fighter-with-pack rogue-plain]]
                           ["rogue first, fighter 2nd" [rogue-plain fighter-with-pack]]]]
    (println "\n" label)
    (println "   equipment:" (pr-str (sort (equipment-keys classes))))
    (println "   equipment selections offered:" (count (equipment-selections classes)))
    (doseq [p (sort-by str (equipment-selections classes))]
      (println "     " (pr-str p)))))

(deftest starting-equipment-survives-the-class-losing-first-place
  (let [fighter-1st [fighter-with-pack rogue-plain]
        rogue-1st   [rogue-plain fighter-with-pack]
        pack        #{:backpack :bedroll :mess-kit :rations-1-day- :rope-hempen
                      :tinderbox :torch :waterskin}]

    (testing "fighter first: its explorer's pack is on the sheet and its selections are offered"
      (is (= pack (equipment-keys fighter-1st)))
      (is (contains? (equipment-selections fighter-1st)
                     [:class :fighter :starting-equipment-equipment-pack])))

    (testing "rogue first: the fighter's pack is STILL on the sheet"
      (is (= pack (equipment-keys rogue-1st))))

    (testing "…while the control that granted it is gone"
      (is (not (contains? (equipment-selections rogue-1st)
                          [:class :fighter :starting-equipment-equipment-pack]))
          "the fighter's equipment selection is filtered out by first-class?"))

    (testing "…and the rogue's own starting equipment opens on top of it"
      (is (contains? (equipment-selections rogue-1st)
                     [:class :rogue :starting-equipment-equipment-pack])
          "so the character can hold two classes' starting packs"))))

(deftest ^:diagnostic is-class-order-just-the-raw-option-order
  (println "\n=== does ?classes order track the raw :class vector? ===")
  (doseq [[label classes] [["[fighter rogue]" [fighter-first rogue-plain]]
                           ["[rogue fighter]" [rogue-plain fighter-first]]
                           ["[rogue]"         [rogue-plain]]]]
    (println (format "  raw %-18s -> built ?classes %s"
                     label (pr-str (char5e/classes (build classes)))))))
