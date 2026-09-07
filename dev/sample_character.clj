(ns sample-character
  "Builds fully-populated characters and writes them through the real export
   path, for eyeballing template changes against sheets that have data on them.

   An empty form hides most problems: missing fields, clipped values, fields
   sitting over the wrong printed label. Both fixtures fill every spell row the
   template offers so the spell pages get judged at their worst case.

     lein with-profile init-db run -m clojure.main dev/sample_character.clj

   Writes target/sample-wizard.pdf   (level 20 single-class caster, 1 spell page)
      and target/sample-multi.pdf    (four 5-level casters, 4 spell pages)

   The second one exists because one spellcasting class only ever exercises the
   \"-1\" field suffix. Four classes exercise -1 through -4 and the sheet4
   template, which nothing else here touches.

   Dev tooling; nothing in src depends on it."
  (:require [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.spell-lists :as sl]
            [clojure.java.io :as io])
  (:import (org.apache.pdfbox Loader)
           (java.io FileOutputStream)))

;; A trap worth knowing before editing the field maps below. The template's
;; field NAMES do not describe what the app puts in them:
;;
;;   features-and-traits    <- the EQUIPPED item list  (prints under EQUIPMENT)
;;   treasure               <- unequipped items and valuables (page 2)
;;   features-and-traits-2  <- the actual features, actions and reactions,
;;                             on the continuation page (prints under
;;                             FEATURES & TRAITS)
;;   equipment              <- nothing. pdf_spec never emits this key; the box
;;                             it sits in carries the coin fields only.
;;
;;
;; Same trap in the ability boxes: `cha` holds the MODIFIER (the big box) and
;; `cha-mod` holds the SCORE (the small circle). Verified against a production
;; export, not inferred.
;;
;; See pdf_spec/equipment-fields and pdf_spec/traits-fields. Fill these the way
;; the app does, or the fixture tests a layout no real character produces.

;; How many spell-name rows the style 1 spell page provides at each level.
;; Level 3 offers 13 boxes but has no spells-3-11 field; write-fields! reports it
;; as unplaceable on every run.
(def ^:private rows {0 8, 1 12, 2 13, 3 13, 4 13, 5 9, 6 9, 7 9, 8 7, 9 7})

(defn- spell-name [k]
  (or (:name (get spells/spell-map k)) (name k)))

(defn- spell-fields
  "Fill the page's rows for one spellcasting class, at the levels it can cast.
   `suffix` is the 1-based class index the template appends to every name."
  [{:keys [list max-spell-level]} suffix]
  (let [available (get sl/spell-lists list)]
    (into {}
          (for [level (range 0 (inc max-spell-level))
                :let [names (sort (map spell-name (get available level)))]
                [idx nm] (map-indexed vector (take (get rows level) names))]
            [(keyword (str "spells-" level "-" (inc idx) "-" suffix)) nm]))))

(defn- caster-fields
  "The header block and slot row for one spellcasting class."
  [{:keys [name-str ability dc attack slots]} suffix]
  (merge
    {(keyword (str "spellcasting-class-" suffix))   name-str
     (keyword (str "spellcasting-ability-" suffix)) ability
     (keyword (str "spell-save-dc-" suffix))        (str dc)
     (keyword (str "spell-attack-bonus-" suffix))   attack}
    (into {} (for [[level n] slots]
               [(keyword (str "spell-slots-" level "-" suffix)) (str n)]))))

(defn- spellcasting-fields [casters]
  (apply merge
         (map-indexed (fn [i c]
                        (merge (caster-fields c (inc i))
                               (spell-fields c (inc i))))
                      casters)))

;; ─── Fixture 1: level 20 single-class evoker ───────────────────────────────

(def ^:private wizard-20
  {:file "target/sample-wizard.pdf"
   :template "fillable-char-sheetstyle-1-1-spells.pdf"
   :casters [{:name-str "Wizard" :list :wizard :ability "Intelligence"
              :dc 19 :attack "+11" :max-spell-level 9
              :slots {1 4, 2 3, 3 3, 4 3, 5 3, 6 2, 7 2, 8 1, 9 1}}]
   :fields
   {:character-name "Ysolde Vantreaux" :character-name-2 "Ysolde Vantreaux"
    :class-level "Wizard 20 (School of Evocation)" :background "Sage"
    :player-name "fixture" :race "High Elf" :alignment "Neutral Good" :xp "355,000"
    :str "-1" :str-mod "8" :dex "+2" :dex-mod "14" :con "+3" :con-mod "16"
    :int "+5" :int-mod "20" :wis "+1" :wis-mod "13" :cha "+0" :cha-mod "10"
    :str-save "-1" :dex-save "+2" :con-save "+3"
    :int-save "+11" :wis-save "+7" :cha-save "+0"
    :int-save-check true :wis-save-check true
    :ac "15" :initiative "+2" :speed "30 ft." :hp-max "122" :hp-current "122"
    :hp-temp "0" :hd "20d6" :prof-bonus "+6" :passive "11" :inspiration "1"
    :arcana "+11" :arcana-check true :history "+11" :history-check true
    :investigation "+11" :investigation-check true :insight "+7" :insight-check true
    :perception "+1" :medicine "+1" :nature "+5" :religion "+5"
    :age "241" :height "5'11\"" :weight "134 lb."
    :eyes "Pale grey" :skin "Fair" :hair "Silver, braided"
    :weapon-name-1 "Quarterstaff" :weapon-attack-bonus-1 "+5" :weapon-damage-1 "1d6+3 bludgeoning"
    :weapon-name-2 "Dagger" :weapon-attack-bonus-2 "+8" :weapon-damage-2 "1d4+2 piercing"
    :weapon-name-3 "Fire Bolt (cantrip)" :weapon-attack-bonus-3 "+11" :weapon-damage-3 "4d10 fire"
    :gp "1,240" :pp "60" :cp "0" :sp "0" :ep "0"
    :personality-traits "Speaks to books as though they can hear her. They occasionally answer."
    :ideals "Knowledge withheld is knowledge wasted."
    :bonds "The Vantreaux archive burned. She is rewriting it from memory, one volume a year."
    :flaws "Assumes she is the smartest person in the room, and is usually right."
    :backstory (str "Apprenticed at eleven to a conjurer who did not survive his own summoning. "
                    "Finished her training alone, on stolen notes.")
    :allies "The Candlewrights; Archivist Bell; a copper dragon who resents owing her a favour."
    :other-profs "Common, Elvish, Draconic, Celestial, Deep Speech. Calligrapher's supplies."
    :features-and-traits-2 (str "Arcane Recovery. Evocation Savant. Sculpt Spells. Potent Cantrip. "
                              "Empowered Evocation. Overchannel. Spell Mastery. Signature Spells.")
    :features-and-traits "Quarterstaff, dagger, component pouch, scholar's pack, spellbook, bedroll, ink and pens."
    :treasure "Ring of Spell Storing, Wand of the War Mage +2, Robe of the Archmagi, Ioun Stone (Mastery)"
    :attacks-and-spellcasting "Evocation save DC 19. Overchannel 1/long rest."}})

;; ─── Fixture 2: warlock 5 / sorcerer 5 / wizard 5 / cleric 5 ───────────────
;;
;; Caster level for slots is 15 (the three full casters; warlock's pact magic is
;; tracked separately), so slots run to 8th level. But no class here is above
;; level 5, so nothing above 3rd level can be prepared. High slots with empty
;; rows above 3rd is CORRECT for this build, not a bug -- it is exactly the
;; multiclass case worth looking at on a printed sheet.

(def ^:private multiclass-slots {1 4, 2 3, 3 3, 4 3, 5 2, 6 1, 7 1, 8 1})
(def ^:private pact-slots {3 2})

(def ^:private multi-20
  {:file "target/sample-multi.pdf"
   :template "fillable-char-sheetstyle-1-1-spells.pdf"
   :grow-to 4
   :casters [{:name-str "Warlock 5 (Great Old One, Pact of the Tome)" :list :warlock
              :ability "Charisma" :dc 16 :attack "+8" :max-spell-level 3
              :slots pact-slots}
             {:name-str "Sorcerer 5 (Draconic Bloodline)" :list :sorcerer
              :ability "Charisma" :dc 16 :attack "+8" :max-spell-level 3
              :slots multiclass-slots}
             {:name-str "Wizard 5 (School of Divination)" :list :wizard
              :ability "Intelligence" :dc 15 :attack "+7" :max-spell-level 3
              :slots multiclass-slots}
             {:name-str "Cleric 5 (Knowledge Domain)" :list :cleric
              :ability "Wisdom" :dc 14 :attack "+6" :max-spell-level 3
              :slots multiclass-slots}]
   :fields
   {:character-name "Corvin Ashgrave" :character-name-2 "Corvin Ashgrave"
    :class-level "Warlock 5 / Sorcerer 5 / Wizard 5 / Cleric 5" :background "Charlatan"
    :player-name "fixture" :race "Half-Elf" :alignment "Chaotic Neutral" :xp "355,000"
    :str "+0" :str-mod "10" :dex "+2" :dex-mod "14" :con "+2" :con-mod "14"
    :int "+3" :int-mod "16" :wis "+2" :wis-mod "14" :cha "+4" :cha-mod "18"
    :str-save "+0" :dex-save "+2" :con-save "+2"
    :int-save "+7" :wis-save "+6" :cha-save "+10"
    :int-save-check true :wis-save-check true :cha-save-check true
    :ac "13" :initiative "+2" :speed "30 ft." :hp-max "104" :hp-current "104"
    :hp-temp "12" :hd "5d8 / 5d6 / 5d6 / 5d8" :prof-bonus "+6" :passive "12" :inspiration "0"
    :arcana "+9" :arcana-check true :religion "+9" :religion-check true
    :deception "+10" :deception-check true :persuasion "+10" :persuasion-check true
    :history "+9" :insight "+8" :investigation "+3" :perception "+2"
    :age "34" :height "5'8\"" :weight "150 lb."
    :eyes "One black, one pale" :skin "Ashen" :hair "Black, close-cropped"
    :weapon-name-1 "Eldritch Blast (2 beams)" :weapon-attack-bonus-1 "+8" :weapon-damage-1 "1d10+4 force each"
    :weapon-name-2 "Quarterstaff" :weapon-attack-bonus-2 "+6" :weapon-damage-2 "1d6 bludgeoning"
    :weapon-name-3 "Sacred Flame (DC 14)" :weapon-attack-bonus-3 "--" :weapon-damage-3 "2d8 radiant"
    :gp "310" :sp "45" :cp "18" :ep "0" :pp "6"
    :personality-traits "Collects other people's holy symbols. Will not say why."
    :ideals "Every power will answer to someone who asks in enough languages."
    :bonds "Owes a patron he has never seen and cannot stop hearing."
    :flaws "Cannot leave an offered bargain unexamined, however plainly it is a trap."
    :backstory (str "Took orders, broke them, took a pact, broke that too, and studied enough "
                    "to know he has not gotten away with either. Four powers have a claim on "
                    "him and none of them have compared notes yet.")
    :allies "The Ashgrave estate (in probate); a cleric of Oghma who wants his notes burned."
    :other-profs "Common, Elvish, Infernal, Deep Speech, Celestial. Forgery kit, playing cards."
    :features-and-traits-2 (str "Pact Magic (2 slots, 3rd level, short rest). Awakened Mind. "
                              "Entropic Ward. Book of Ancient Secrets. Font of Magic (5 points). "
                              "Twinned and Quickened Spell. Arcane Recovery. Portent (2 dice). "
                              "Channel Divinity 1/rest. Knowledge of the Ages. Fey Ancestry.")
    :features-and-traits "Component pouch, Book of Shadows, spellbook, holy symbol, forgery kit, three unmatched rings."
    :treasure "Rod of the Pact Keeper +1, Pearl of Power, Cloak of Protection"
    :attacks-and-spellcasting
    (str "Four spell lists, three save DCs. Pact slots (2 x 3rd) recharge on a short rest and are "
         "tracked apart from the shared slots. Caster level 15 for slots; no class above 5th, so "
         "nothing above 3rd level can be prepared.")}})


;; ─── Fixture 3: eight spellcasting classes, twenty levels ──────────────────
;;
;; bard 2 / cleric 2 / druid 2 / paladin 4 / ranger 4 / sorcerer 2 / warlock 2 /
;; wizard 2. The template provides SIX spellcasting sections (-1 through -6), so
;; two of these eight have nowhere to print. write-fields! skips names it cannot
;; find without saying anything, so they vanish silently -- which is the point of
;; this fixture. build! reports what it dropped.
;;
;; Caster level for shared slots is 14: five full casters at 2 (=10) plus two
;; half casters at 4 (=2 each). Warlock pact magic is separate. But no class is
;; above 4th level, so NOTHING above 1st level can be prepared, while slots run
;; to 7th. An extreme version of the multiclass shape in fixture 2.

(def ^:private wide-slots {1 4, 2 3, 3 3, 4 3, 5 2, 6 1, 7 1})

(defn- caster [name-str list ability dc attack slots]
  {:name-str name-str :list list :ability ability :dc dc :attack attack
   :max-spell-level 1 :slots slots})

(def ^:private multi-8
  {:file "target/sample-eight.pdf"
   :template "fillable-char-sheetstyle-1-1-spells.pdf"
   :grow-to 6
   :casters [(caster "Bard 2 (College of Lore)"        :bard     "Charisma"     14 "+6" wide-slots)
             (caster "Cleric 2 (Life Domain)"          :cleric   "Wisdom"       13 "+5" wide-slots)
             (caster "Druid 2 (Circle of the Land)"    :druid    "Wisdom"       13 "+5" wide-slots)
             (caster "Paladin 4 (Oath of Devotion)"    :paladin  "Charisma"     14 "+6" wide-slots)
             (caster "Ranger 4 (Hunter)"               :ranger   "Wisdom"       13 "+5" wide-slots)
             (caster "Sorcerer 2 (Wild Magic)"         :sorcerer "Charisma"     14 "+6" wide-slots)
             (caster "Warlock 2 (Fiend, Pact of Blade)" :warlock "Charisma"     14 "+6" {1 2})
             (caster "Wizard 2 (School of Abjuration)" :wizard   "Intelligence" 13 "+5" wide-slots)]
   :fields
   {:character-name "Wren Sixpence" :character-name-2 "Wren Sixpence"
    :class-level "Brd2/Clr2/Drd2/Pal4/Rgr4/Sor2/Wlk2/Wiz2"
    :background "Guild Artisan" :player-name "fixture" :race "Human (Variant)"
    :alignment "Chaotic Good" :xp "355,000"
    :str "+1" :str-mod "12" :dex "+1" :dex-mod "13" :con "+2" :con-mod "14"
    :int "+1" :int-mod "12" :wis "+2" :wis-mod "14" :cha "+3" :cha-mod "16"
    :str-save "+1" :dex-save "+1" :con-save "+2"
    :int-save "+1" :wis-save "+8" :cha-save "+9"
    :wis-save-check true :cha-save-check true
    :ac "16" :initiative "+1" :speed "30 ft." :hp-max "134" :hp-current "134"
    :hp-temp "0" :hd "2d8/2d8/2d8/4d10/4d10/2d6/2d8/2d6" :prof-bonus "+6"
    :passive "12" :inspiration "1"
    :religion "+7" :religion-check true :nature "+8" :nature-check true
    :persuasion "+9" :persuasion-check true :performance "+9" :performance-check true
    :arcana "+7" :medicine "+8" :insight "+8" :survival "+8" :history "+1"
    :age "29" :height "5'6\"" :weight "142 lb."
    :eyes "Hazel" :skin "Weathered" :hair "Brown, in need of a cut"
    :weapon-name-1 "Longsword" :weapon-attack-bonus-1 "+7" :weapon-damage-1 "1d8+1 slashing"
    :weapon-name-2 "Shortbow" :weapon-attack-bonus-2 "+7" :weapon-damage-2 "1d6+1 piercing"
    :weapon-name-3 "Eldritch Blast" :weapon-attack-bonus-3 "+9" :weapon-damage-3 "1d10 force"
    :gp "88" :sp "120" :cp "340" :ep "0" :pp "0"
    :personality-traits "Has never finished anything and is cheerful about it."
    :ideals "There is no wrong door. There are only doors you have not tried."
    :bonds "Eight oaths, orders and pacts, all technically still in force."
    :flaws "Cannot pass a training hall without enrolling."
    :backstory (str "Started as an apprentice bookbinder and kept meeting people who were "
                    "better at something. Signed with every one of them. Answers to eight "
                    "teachers, none of whom know about the other seven.")
    :allies "Everyone, briefly. Eight organisations each believe Wren is theirs."
    :other-profs "Common, Elvish, Dwarvish, Sylvan, Infernal, Druidic, Celestial. Bookbinder's tools."
    :features-and-traits-2 (str "Bardic Inspiration d6. Cutting Words. Channel Divinity. Preserve Life. "
                              "Wild Shape. Natural Recovery. Divine Smite. Aura of Protection. "
                              "Sacred Weapon. Favored Enemy. Natural Explorer. Colossus Slayer. "
                              "Font of Magic. Wild Magic Surge. Pact of the Blade. Arcane Ward.")
    :features-and-traits "Longsword, shortbow, shield, holy symbol, druidic focus, component pouch, spellbook, lute."
    :treasure "Cloak of Protection, Pearl of Power, a drawer of unfinished apprentice tokens"
    :attacks-and-spellcasting
    (str "Eight spellcasting classes; the sheet has room for six. Caster level 14 for shared "
         "slots, but no class above 4th, so nothing above 1st level can be prepared.")}})

(defn- build! [{:keys [file template grow-to casters fields]}]
  (let [all (merge fields (spellcasting-fields casters))
        out (io/file file)]
    (io/make-parents out)
    (with-open [doc (Loader/loadPDF (.readAllBytes
                                      (.openStream (io/resource template))))]
      ;; Mirrors the export path in routes.clj: grow the master to the shape the
      ;; character needs, pages for classes past that, then overflow, then write.
      (when grow-to (pdf/grow-spell-sections! doc grow-to :all))
      (pdf/add-missing-spell-pages! doc all)
      (let [all (pdf/spill-overflow! doc all)
            dropped (pdf/write-fields! doc all false {})]
        (with-open [o (FileOutputStream. out)] (.save doc o))
        (println (format "wrote %-28s %d fields, %d casters, %d KB%s"
                         (.getPath out) (count all) (count casters)
                         (quot (.length out) 1024)
                         (if (seq dropped)
                           (format "  [%d value(s) had no field]" (count dropped))
                           "")))))))

(defn -main [& _]
  (build! wizard-20)
  (build! multi-20)
  (build! multi-8))

(-main)
