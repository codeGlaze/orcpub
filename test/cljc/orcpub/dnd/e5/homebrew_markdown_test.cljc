(ns orcpub.dnd.e5.homebrew-markdown-test
  "The Markdown export of a homebrew source: the escape helpers, the Wayfarer subclass of
   test/fixtures/coverage-pak.orcbrew, a full class with a nested subclass, unknown modifier
   types, and junk in every field (nothing throws, nothing prints nil)."
  (:require #?(:clj [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            [clojure.string :as str]
            [clojure.walk :as walk]
            #?(:clj [clojure.edn :as edn]
               :cljs [cljs.reader :as reader])
            #?(:clj [orcpub.fixtures :refer [fixture-text]]
               :cljs [orcpub.fixtures :refer-macros [fixture-text]])
            [orcpub.dnd.e5.homebrew-markdown :as md]))

(defn- read-edn
  "The first form of `text`, read with the reader the platform's app uses for .orcbrew files."
  [text]
  #?(:clj (edn/read-string {:default tagged-literal} text)
     :cljs (reader/read-string text)))

(def ^:private pak
  "The Coverage Pak source: {content-type {key item}}."
  (get (read-edn (fixture-text "test/fixtures/coverage-pak.orcbrew")) "Coverage Pak"))

(def ^:private wayfarer (get-in pak [:orcpub.dnd.e5/subclasses :wayfarer]))

(def ^:private ctx {:selections (:orcpub.dnd.e5/selections pak)})

(defn- lines
  "`s` split into its lines."
  [s]
  (str/split-lines s))

(defn- has-line?
  "True when `out` has a line equal to `line`."
  [out line]
  (boolean (some #{line} (lines out))))

(defn- has-line-starting?
  "True when `out` has a line that starts with `prefix`."
  [out prefix]
  (boolean (some #(str/starts-with? % prefix) (lines out))))

(def ^:private not-text
  "What an export must never print for a missing or broken value."
  #"(?i)\b(nil|null|undefined|nan|infinity)\b")

(defn- clean?
  "True when `out` is a string with Unix line endings, one final newline, no trailing spaces, no
   run of blank lines and no printed placeholder for a missing value."
  [out]
  (and (string? out)
       (str/ends-with? out "\n")
       (not (str/ends-with? out "\n\n"))
       (not (str/includes? out "\r"))
       (not (str/includes? out "\n\n\n"))
       (not-any? #(re-find #"[ \t]$" %) (lines out))
       (nil? (re-find not-text out))))

(def ^:private full-class
  "A class using every field the Markdown writer reads."
  {:key :spellblade :name "Spellblade" :hit-die 8 :subclass-level 3 :subclass-title "Blade Oath"
   :ability-increase-levels [4 8 12]
   :profs {:armor {:light true :medium true} :weapon {:martial true} :tool {:thieves-tools true}
           :save {:orcpub.dnd.e5.character/int true :orcpub.dnd.e5.character/con true}
           :skill-options {:choose 2 :options {:arcana true :athletics true}}}
   :weapons {:dagger 2} :armor {:leather 1} :equipment {:explorers-pack 1}
   :weapon-choices [{:name "Weapon 1" :options {:rapier 1 :shortsword 1}}]
   :armor-choices [{:options {:shield 1}}]
   :equipment-choices [{:name "Pack" :options {:dungeoneers-pack 1 :scholars-pack 1}}]
   :spellcasting {:level-factor 2 :known-mode :schedule :ability :orcpub.dnd.e5.character/int
                  :spell-list-kw :wizard :cantrips-known {3 2 10 3} :spells-known {3 3 5 4}
                  :spell-list {1 #{:shield :magic-missile}}}
   :traits [{:name "Weave Strike" :level 1 :type :b-action
             :description "First paragraph.\nSecond paragraph."}
            {:name "Arcane Surge" :level 5 :description "Once per rest."}]
   :level-modifiers [{:type :num-attacks :level 5 :value 2}]
   :level-selections [{:type :weave-styles :level 2 :num 1}]
   :subclasses [{:key :oath :name "Oath of Steel" :level-modifiers [{:type :num-attacks :level 3 :value 2}]
                 :traits [{:name "Steel Vow" :level 3 :description "You swear."}]
                 :cleric-spells {1 {0 :bless 1 :cure-wounds} 2 {0 :aid}}}]})

;; ---------------------------------------------------------------------------------------------

(deftest escape-helper
  (testing "each special character is made literal"
    (is (= "a\\\\b" (md/escape-md "a\\b")))
    (is (= "a\\*b" (md/escape-md "a*b")))
    (is (= "a\\_b" (md/escape-md "a_b")))
    (is (= "a\\`b" (md/escape-md "a`b")))
    (is (= "a\\<b" (md/escape-md "a<b")))
    (is (= "a\\[b\\]" (md/escape-md "a[b]"))))
  (testing "# and > only at the start of a line"
    (is (= "\\# title" (md/escape-md "# title")))
    (is (= "a # b" (md/escape-md "a # b")))
    (is (= "\\> quote" (md/escape-md "> quote")))
    (is (= "a > b" (md/escape-md "a > b")))
    (is (= "one\n\\# two\n\\> three" (md/escape-md "one\n# two\n> three"))))
  (testing "nil is empty, line breaks are kept as \\n, trailing spaces go"
    (is (= "" (md/escape-md nil)))
    (is (= "a\nb\nc" (md/escape-md "a\r\nb\rc")))
    (is (= "a\nb" (md/escape-md "a  \nb \t"))))
  (testing "| only matters in a table cell"
    (is (= "a|b" (md/escape-md "a|b")))
    (is (= "a\\|b" (md/escape-cell "a|b")))
    (is (= "a b" (md/escape-cell "a\nb")))
    (is (= "" (md/escape-cell nil)))))

(deftest wayfarer-subclass
  (let [out (md/subclass->markdown wayfarer ctx)]
    (testing "heading and the italic line"
      (is (= "# Wayfarer" (first (lines out))))
      (is (has-line? out "*Ranger subclass*")))
    (testing "the table has a row for each level the subclass has, and no others"
      (is (has-line? out "| Level | Features |"))
      (is (has-line? out "| 3rd | Wayfarer Lore, Waymark, Wayfarer Trick (choose 1) |"))
      (is (has-line? out "| 7th | Quick Step, Wayfarer Route (choose 2) |"))
      (is (has-line? out "| 18th | See below |"))
      (is (not (has-line-starting? out "| 1st |")))
      (is (not (has-line-starting? out "| 4th |"))))
    (testing "the table names features only: benefits stay in the Gained lists"
      (is (empty? (filter #(and (str/starts-with? % "| ") (str/includes? % "Proficiency")) (lines out))))
      (is (has-line? out "- Weapon Proficiency: All martial weapons")))
    (testing "features in level order, with their activation"
      (is (has-line? out "***Wayfarer Lore.*** Coverage: a trait with no action type."))
      (is (has-line? out "***Waymark (Action).*** Coverage: an action."))
      (is (has-line? out "***Quick Step (Bonus Action).*** Coverage: a bonus action."))
      (is (has-line? out "***Turn Aside (Reaction).*** Coverage: a reaction."))
      (is (< (str/index-of out "### 3rd Level") (str/index-of out "### 7th Level")
             (str/index-of out "### 11th Level") (str/index-of out "### 18th Level"))))
    (testing "modifiers are written as readable lines at their level"
      (is (has-line? out "- Weapon Proficiency: All martial weapons"))
      (is (has-line? out "- Spell: Guidance (Wisdom)"))
      (is (has-line? out "- Number of Attacks: 2"))
      (is (has-line? out "- Swimming Speed: 30 ft."))
      (is (has-line? out "- Damage Resistance: Cold"))
      (is (has-line? out "- Saving Throw Advantage: Frightened"))
      (is (< (str/index-of out "### 7th Level") (str/index-of out "- Number of Attacks: 2")
             (str/index-of out "### 11th Level") (str/index-of out "- Damage Resistance: Cold"))))
    (testing "profs, and choices stated where they occur with their options"
      (is (has-line? out "- **Skill Choices:** Choose 1 from History, Insight, Medicine"))
      (is (has-line? out "***Wayfarer Route.*** Choose 2:"))
      (is (has-line? out "- ***High Road.*** Coverage: a two-pick choice at level 7.")))
    (testing "a choice the source does not hold is named from its key"
      (is (has-line? (md/subclass->markdown wayfarer) "| 3rd | Wayfarer Lore, Waymark, Wayfarer Tricks (choose 1) |")))
    (is (clean? out))))

(deftest source-export
  (let [out (md/source->markdown pak)]
    (is (= "# Coverage Pak" (first (lines out))))
    (is (has-line? out "## Wayfarer"))
    (is (has-line? out "*Ranger subclass*"))
    (is (has-line? out "***Wayfarer Route.*** Choose 2:"))
    (is (clean? out)))
  (testing "a given title wins"
    (is (= "# My Pack" (first (lines (md/source->markdown pak "My Pack"))))))
  (testing "classes come before subclasses, items by name, and the class names its subclass"
    (let [out (md/source->markdown
               {:orcpub.dnd.e5/subclasses {:b {:name "Beta" :class :zealot}
                                           :a {:name "Alpha" :class :zealot}}
                :orcpub.dnd.e5/classes {:zealot {:name "Zealot Prime" :option-pack "Pack Z"}
                                        :abbot {:name "Abbot"}}
                :orcpub.dnd.e5/spells {:x {:name "Skipped Spell"}}})
          at #(str/index-of out %)]
      (is (= "# Pack Z" (first (lines out))))
      (is (< (at "## Abbot") (at "## Zealot Prime") (at "## Alpha") (at "## Beta")))
      (is (has-line? out "*Zealot Prime subclass*"))
      (is (nil? (at "Skipped Spell")))))
  (testing "the table of formatters pins the types written and their order"
    (is (= [:orcpub.dnd.e5/classes :orcpub.dnd.e5/subclasses] (mapv first md/formatters)))))

(deftest full-class-export
  (let [out (md/class->markdown full-class)
        class-part (first (str/split out #"## Subclass: "))
        rows (filter #(re-find #"^\| \d+(st|nd|rd|th) \|" %) (lines class-part))]
    (is (= "# Spellblade" (first (lines out))))
    (is (has-line? out "*Class*"))
    (testing "basics"
      (is (has-line? out "- **Hit Die:** d8"))
      (is (has-line? out "- **Armor (starting class only):** Light, Medium"))
      (is (has-line? out "- **Saving Throws:** Constitution, Intelligence"))
      (is (has-line? out "- **Skill Choices:** Choose 2 from Arcana, Athletics"))
      (is (has-line? out "- **Subclass:** chosen at 3rd level: Blade Oath"))
      (is (has-line? out "- **Weapons:** Dagger (2)"))
      (is (has-line? out "- **Weapon 1:** choose one of Rapier, Shortsword"))
      (is (has-line? out "- **Armor choice:** choose one of Shield")))
    (testing "spellcasting"
      (is (has-line? out "- **Spellcasting Ability:** Intelligence"))
      (is (has-line? out "- **Caster Type:** Half caster"))
      (is (has-line? out "- **Cantrips Known:** 2 at 3rd level, 3 at 10th level"))
      (is (has-line? out "- **1st-Level Spell List:** Magic Missile, Shield")))
    (testing "the table holds the 20 levels"
      (is (= 20 (count rows)))
      (is (= "| 1st | Weave Strike |" (first rows)))
      (is (= "| 2nd | Weave Styles (choose 1) |" (second rows)))
      (is (= "| 3rd | Blade Oath |" (nth rows 2)))
      (is (= "| 4th | Ability Score Improvement |" (nth rows 3)))
      (is (= "| 5th | Arcane Surge |" (nth rows 4)))
      (is (= "| 20th | - |" (last rows))))
    (testing "features, with further paragraphs after a blank line"
      (is (str/includes? out "***Weave Strike (Bonus Action).*** First paragraph.\n\nSecond paragraph.")))
    (testing "the nested subclass is a section, its headings one level down"
      (is (has-line? out "## Subclass: Oath of Steel"))
      (is (has-line? out "*Spellblade subclass*"))
      (is (has-line? out "### Features"))
      (is (has-line? out "#### 3rd Level"))
      (is (has-line? out "- **Domain Spells, 1st level (always prepared):** Bless, Cure Wounds"))
      (is (has-line? out "- **Domain Spells, 2nd level (always prepared):** Aid"))
      (is (< (str/index-of out "## Subclass: Oath of Steel") (str/index-of out "***Steel Vow.***"))))
    (is (clean? out))))

(deftest unknown-modifier-types-fall-back
  (is (= "Mystery Power: Fire" (md/modifier->text {:type :mystery-power :value :fire})))
  (is (= "Mystery Power" (md/modifier->text {:type :mystery-power})))
  (is (= "Mystery Power: 3, Fire" (md/modifier->text {:type :mystery-power :value [3 :fire]})))
  (is (= "Mystery Power: Range: 30, Type: Cold"
         (md/modifier->text {:type :mystery-power :value {:type :cold :range 30}})))
  (is (= "Weapon Proficiency: Rapier" (md/modifier->text {:type :weapon-prof :value :rapier})))
  (is (nil? (md/modifier->text {:value 3})))
  (is (nil? (md/modifier->text "text")))
  (testing "inside a subclass"
    (let [out (md/subclass->markdown {:name "X" :class :ranger
                                      :level-modifiers [{:type :mystery-power :level 4 :value :fire}]})]
      (is (has-line? out "- Mystery Power: Fire"))
      (is (has-line? out "| 4th | See below |"))))
  (testing "every modifier type in the coverage pak has a label"
    (let [types (for [ct [:orcpub.dnd.e5/subclasses :orcpub.dnd.e5/classes]
                      item (vals (get pak ct))
                      m (:level-modifiers item)]
                  (:type m))]
      (is (seq types))
      (is (every? md/modifier-labels types)))))

(def ^:private junk-values
  "Values of the wrong type for almost any field."
  [nil 42 -1 2.5 #?(:clj Double/NaN :cljs js/NaN) "text" "" :kw [] [nil] [1 "a"] {} {:a 1}
   #{:a} true false '(1 2) {:name nil} [{}] [[]] {:choose "x" :options 3} {1 {2 3}}])

(def ^:private junk-fields
  "Every field of a class or subclass the writer reads."
  [:name :key :class :hit-die :subclass-level :subclass-title :ability-increase-levels
   :level-modifiers :level-selections :traits :profs :spellcasting :weapons :armor :equipment
   :weapon-choices :armor-choices :equipment-choices :subclasses :cleric-spells :paladin-spells
   :warlock-spells :option-pack :help :subclass-help :equipment-selections :selections
   :ability-increases :save-proficiencies])

(def ^:private junk-inner
  "Junk where a card, a modifier, a selection, a prof or a spellcasting value belongs."
  (concat
   (for [j junk-values] [:traits [j]])
   (for [j junk-values] [:level-modifiers [j]])
   (for [j junk-values] [:level-selections [j]])
   (for [j junk-values, k [:armor :weapon :tool :save :skill :skill-options :language-options
                           :skill-expertise-options :tool-options]]
     [:profs {k j}])
   (for [j junk-values, k [:level-factor :known-mode :ability :spell-list-kw :cantrips-known
                           :spells-known :spell-list]]
     [:spellcasting {k j}])
   (for [j junk-values, k [:name :description :level :type :summary]] [:traits [{k j}]])
   (for [j junk-values, k [:type :level :value]] [:level-modifiers [(assoc {:type :spell :level 3} k j)]])
   (for [j junk-values, k [:type :level :num]] [:level-selections [(assoc {:type :weave-styles} k j)]])
   (for [j junk-values] [:subclasses [j]])
   (for [j junk-values] [:subclasses [{:name "Sub" :traits [j] :cleric-spells {1 j}}]])))

(deftest junk-never-throws-and-never-prints-nil
  (testing "a wrong value in each field of a class and of a subclass"
    (doseq [f junk-fields, j junk-values
            :let [bad (assoc full-class f j)]]
      (is (clean? (md/class->markdown bad)) (pr-str f j))
      (is (clean? (md/subclass->markdown bad)) (pr-str f j))))
  (testing "a wrong value inside a card, a modifier, a selection or a prof"
    (doseq [[f v] junk-inner
            :let [bad (assoc full-class f v)]]
      (is (clean? (md/class->markdown bad)) (pr-str f v))
      (is (clean? (md/subclass->markdown bad)) (pr-str f v))))
  (testing "a wrong value where the item or the source belongs"
    (doseq [j junk-values]
      (is (clean? (md/class->markdown j)) (pr-str j))
      (is (clean? (md/subclass->markdown j)) (pr-str j))
      (is (clean? (md/source->markdown j)) (pr-str j))
      (is (clean? (md/source->markdown {:orcpub.dnd.e5/classes j :orcpub.dnd.e5/subclasses j
                                        :orcpub.dnd.e5/selections j})) (pr-str j))
      (is (clean? (md/source->markdown {:orcpub.dnd.e5/classes {:a j}
                                        :orcpub.dnd.e5/subclasses {:b j :c {:class j}}})) (pr-str j))))
  (testing "an empty source and a source with only content types the writer skips"
    (is (= "# Homebrew\n\n*Nothing to export yet.*\n" (md/source->markdown {})))
    (is (= "# Homebrew\n\n*Nothing to export yet.*\n" (md/source->markdown nil)))
    (is (= (str "# Homebrew\n\n*This file covers classes and subclasses only. Not included: "
                "Spells (1).*\n\n*Nothing to export yet.*\n")
           (md/source->markdown {:orcpub.dnd.e5/spells {:a {:name "A"}}}))))
  (testing "a level that is missing is level 1, one that is not 1 to 20 is set apart"
    (let [out (md/class->markdown {:name "L" :traits [{:name "No Level" :description "a"}
                                                      {:name "Bad Level" :level "three" :description "b"}
                                                      {:name "Huge Level" :level 25 :description "c"}]})]
      (is (has-line? out "| 1st | No Level |"))
      (is (has-line? out "### Features without a valid level"))
      (is (has-line? out "***Bad Level.*** b"))
      (is (clean? out)))))

(deftest output-is-deterministic
  (let [reverse-maps (fn [x] (walk/postwalk #(if (map? %) (into (array-map) (reverse (seq %))) %) x))
        scrambled (reverse-maps full-class)]
    (is (= (md/class->markdown full-class) (md/class->markdown full-class)))
    (is (= (md/class->markdown full-class) (md/class->markdown scrambled)))
    (is (= (md/source->markdown pak) (md/source->markdown (reverse-maps pak))))))

(deftest user-text-is-escaped
  (let [item {:name "Pipe|Star*" :class :ranger
              :traits [{:name "A|B *C*" :level 3
                        :description "Uses a_b | and *bold*.\n# Not a heading\n> Not a quote\n<script>[x](y)"}]
              :level-modifiers [{:type :weapon-prof :level 3 :value "a|b"}]}
        out (md/subclass->markdown item)]
    (testing "a name"
      (is (= "# Pipe|Star\\*" (first (lines out))))
      (is (has-line? out "| 3rd | A\\|B \\*C\\* |"))
      (is (has-line? out "***A|B \\*C\\*.*** Uses a\\_b | and \\*bold\\*.") (pr-str (lines out))))
    (testing "a description, line by line"
      (is (str/includes? out "\n\n\\# Not a heading\n\n"))
      (is (str/includes? out "\n\n\\> Not a quote\n\n"))
      (is (str/includes? out "\n\n\\<script>\\[x\\](y)\n")))
    (testing "a pipe in a modifier value is plain in its bullet and never reaches the table"
      (is (has-line? out "- Weapon Proficiency: a|b"))
      (is (not (str/includes? out "Weapon Proficiency: a\\|b"))))
    (is (clean? out))))

(deftest export-file-download
  (let [f (md/export-file pak "Coverage Pak")]
    (testing "name, type and text of the download"
      (is (= "Coverage Pak.md" (:filename f)))
      (is (= "text/markdown;charset=utf-8" (:content-type f)))
      (is (= (md/source->markdown pak "Coverage Pak") (:text f)))
      (is (str/starts-with? (:text f) "# Coverage Pak\n")))
    (is (= (md/left-out pak) [] ) "the coverage pak holds only what the writer formats")
    (is (nil? (:notice f)) "a source with only classes and subclasses has nothing to warn about"))
  (testing "the title defaults to the source's :option-pack"
    (is (= "Coverage Pak.md" (:filename (md/export-file pak nil)))))
  (testing "a source with nothing the writer formats still gives a file, and says why it is empty"
    (let [f (md/export-file {:orcpub.dnd.e5/spells {:x {:name "Skipped Spell" :option-pack "Sp"}}} nil)]
      (is (= "Sp.md" (:filename f)))
      (is (has-line? (:text f) "*Nothing to export yet.*"))
      (is (str/includes? (:notice f) "classes and subclasses only")))
    (is (str/includes? (:notice (md/export-file {} "Empty")) "classes and subclasses only"))))

(deftest block-markers-in-descriptions-stay-text
  (let [out (md/subclass->markdown
             {:name "S" :class :ranger
              :traits [{:name "T" :level 3
                        :description "Intro.\n~~~\n- not a list\n+ nor this\n===\n1. not ordered\n2) nor this"}
                       {:name "After" :level 3 :description "Still a feature."}]})]
    (is (str/includes? out "\n\n\\~~~\n\n"))
    (is (str/includes? out "\n\n\\- not a list\n\n"))
    (is (str/includes? out "\n\n\\+ nor this\n\n"))
    (is (str/includes? out "\n\n\\===\n\n"))
    (is (str/includes? out "\n\n1\\. not ordered\n\n"))
    (is (str/includes? out "\n\n2\\) nor this\n\n"))
    (is (has-line? out "***After.*** Still a feature."))
    (is (clean? out))))

(deftest class-description-and-subclass-flavor
  (let [out (md/class->markdown (assoc full-class
                                       :help "A warrior-mage.\nSecond *line*."
                                       :subclass-help "Your oath shapes you."))]
    (is (str/includes? out "*Class*\n\nA warrior-mage.\n\nSecond \\*line\\*.\n\n- **Hit Die:** d8"))
    (is (has-line? out "Your oath shapes you."))
    (is (< (str/index-of out "- **Subclass:**") (str/index-of out "Your oath shapes you.")
           (str/index-of out "## Equipment")))
    (is (clean? out))))

(deftest rich-equipment-choices
  (let [out (md/class->markdown
             (assoc full-class :equipment-selections
                    [{:name "Arms"
                      :options [{:name "Blade and board"
                                 :grants [{:kind :weapon :key :longsword :qty 1}
                                          {:kind :armor :key :shield :qty 1}]}
                                {:name "Two axes" :grants [{:kind :weapon :key :handaxe :qty 2}]
                                 :choose [{:from :martial}]}
                                {:grants [{:kind :equipment :key :holy-symbol}]}]}
                     {:options [{:name "Pack" :choose [{:from :pack}]}]}]))]
    (is (has-line? out (str "- **Arms:** choose one of *Blade and board* (Longsword, Shield); "
                            "*Two axes* (Handaxe (2), plus any martial weapon); Holy Symbol")))
    (is (has-line? out "- **Equipment choice:** choose one of *Pack* (plus an equipment pack)"))
    (is (clean? out))))

(deftest raw-template-choices
  (let [sel [{:name "Fighting Style" :num 1 :options [{:name "Archery"} {:name "Defense"}]}
             {:name "Bonus Trick" :options []} {:options [{:name "Unnamed"}]}]
        cls (md/class->markdown (assoc full-class :selections sel))
        sub (md/subclass->markdown {:name "S" :class :ranger :selections sel})]
    (doseq [out [cls sub]]
      (is (has-line? out "- **Fighting Style:** choose 1 from Archery, Defense"))
      (is (has-line? out "- **Bonus Trick:** choose 1"))
      (is (not (str/includes? out "Unnamed")))
      (is (clean? out)))
    (is (has-line? cls "## Other Choices"))))

(deftest subclass-ability-and-save-grants
  (let [c :orcpub.dnd.e5.character/con
        out (md/subclass->markdown
             {:name "S" :class :ranger
              :ability-increases [[2 :orcpub.dnd.e5.character/wis] [1 :mental :save]
                                  [1 [:str :dex]] [-1 c]]
              :save-proficiencies [[1 c] [1 :martial]]})]
    (is (has-line? out (str "- **Ability Score Increase:** +2 to Wisdom, +1 to one of "
                            "Intelligence, Wisdom or Charisma, and proficiency in that saving "
                            "throw, +1 to one of Dexterity or Strength, -1 to Constitution")))
    (is (has-line? out (str "- **Saving Throw Proficiency:** Constitution, choose 1 of Strength, "
                            "Dexterity or Constitution")))
    (is (clean? out)))
  (testing "the released set form"
    (is (has-line? (md/subclass->markdown {:name "S" :ability-increases
                                           #{:orcpub.dnd.e5.character/str :saves?}})
                   "- **Ability Score Increase:** +1 to Strength, and proficiency in that saving throw"))))

(deftest selections-from-other-sources
  (let [src {:orcpub.dnd.e5/classes
             {:k {:name "K" :level-selections [{:type :styles :level 1 :num 1}]}}}
        lib {"Other" {:orcpub.dnd.e5/selections
                      {:styles {:name "Styles" :options [{:name "Bold" :description "Big."}]}}}}]
    (is (has-line? (md/source->markdown src "S") "***Styles.*** Choose 1"))
    (let [out (md/source->markdown src "S" lib)]
      (is (has-line? out "***Styles.*** Choose 1:"))
      (is (has-line? out "- ***Bold.*** Big.")))
    (is (= (md/source->markdown src "S" lib) (:text (md/export-file src "S" lib))))
    (testing "the source's own selection wins"
      (is (has-line? (md/source->markdown
                      (assoc src :orcpub.dnd.e5/selections
                             {:styles {:name "Own Styles" :options [{:name "Mine"}]}})
                      "S" lib)
                     "- ***Mine.***")))))

(deftest mixed-sources-name-what-they-leave-out
  (let [src {:orcpub.dnd.e5/classes {:k {:name "K" :level-selections [{:type :used :level 1}]}}
             :orcpub.dnd.e5/feats {:a {:name "A"} :b {:name "B"}}
             :orcpub.dnd.e5/spells {:s {:name "S"}}
             :orcpub.dnd.e5/selections {:used {:name "Used"} :spare {:name "Spare"}}}
        f (md/export-file src "Mix")]
    (is (= [["Feats" 2] ["Selections" 1] ["Spells" 1]] (md/left-out src)))
    (is (= "*This file covers classes and subclasses only. Not included: Feats (2), Selections (1), Spells (1).*"
           (second (remove str/blank? (lines (:text f))))))
    (is (= "The Markdown file covers classes and subclasses only. Not included: Feats (2), Selections (1), Spells (1)."
           (:notice f)))
    (is (clean? (:text f)))))

(deftest class-proficiencies-keep-their-multiclass-flag
  ;; The SRD fighter's shape: false = also when multiclassing in, true = starting class only.
  (let [profs {:armor {:light false :medium false :heavy true :shields false}
               :weapon {:simple false :martial false}
               :tool {:smiths-tools true}}
        cls (md/class->markdown {:name "Warrior" :profs profs})
        sub (md/subclass->markdown {:name "Path" :class :warrior :profs profs})]
    (testing "a class splits them"
      (is (has-line? cls "- **Armor:** Light, Medium, Shields"))
      (is (has-line? cls "- **Armor (starting class only):** Heavy"))
      (is (has-line? cls "- **Weapons:** Martial, Simple"))
      (is (has-line? cls "- **Tools (starting class only):** Smiths Tools"))
      (is (not (has-line-starting? cls "- **Weapons (starting class only):**"))))
    (testing "a subclass grants every key"
      (is (has-line? sub "- **Armor:** Heavy, Light, Medium, Shields"))
      (is (has-line? sub "- **Weapons:** Martial, Simple"))
      (is (not (str/includes? sub "starting class only"))))
    (is (clean? cls))
    (is (clean? sub))))
