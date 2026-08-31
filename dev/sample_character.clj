(ns sample-character
  "Builds a fully-populated level 20 wizard and writes it through the real
   export path, for eyeballing template changes against a sheet with data on it.

   An empty form hides most problems: missing fields, truncated values, fields
   that exist but sit off-page. Every spell level is filled here so the spell
   page can be judged at its worst case rather than blank.

     lein with-profile init-db run -m clojure.main dev/sample_character.clj

   Writes target/sample-wizard.pdf. Dev tooling; nothing in src depends on it."
  (:require [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.spell-lists :as sl]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import (org.apache.pdfbox Loader)
           (java.io FileOutputStream)))

(def ^:private prof-bonus 6)
(def ^:private int-mod 5)

;; Slots per level for a level 20 wizard, and how many name fields the style 1
;; spell page actually provides at each level. The template is the smaller of
;; the two -- level 3 offers 13 boxes but skips spells-3-11, see the notes in
;; docs/issues/pdf-export-size.md.
(def ^:private slots {1 4, 2 3, 3 3, 4 3, 5 3, 6 2, 7 2, 8 1, 9 1})
(def ^:private rows {0 8, 1 12, 2 13, 3 13, 4 13, 5 9, 6 9, 7 9, 8 7, 9 7})

(defn- spell-name [k]
  (or (:name (get spells/spell-map k)) (name k)))

(defn- spell-fields
  "Fill every spell row the page offers, drawing on the real wizard list."
  []
  (let [wizard (:wizard sl/spell-lists)]
    (into {}
          (for [level (range 0 10)
                :let [available (sort (map spell-name (get wizard level)))]
                [idx nm] (map-indexed vector (take (get rows level) available))]
            [(keyword (str "spells-" level "-" (inc idx) "-1")) nm]))))

(defn- slot-fields []
  (into {} (for [[level n] slots]
             [(keyword (str "spell-slots-" level "-1")) (str n)])))

(def ^:private ability-fields
  {:str "8"  :str-mod "-1" :dex "14" :dex-mod "+2" :con "16" :con-mod "+3"
   :int "20" :int-mod "+5" :wis "13" :wis-mod "+1" :cha "10" :cha-mod "+0"
   :str-save "-1" :dex-save "+2" :con-save "+3"
   :int-save "+11" :wis-save "+7" :cha-save "+0"
   :int-save-check true :wis-save-check true})

(def ^:private skill-fields
  {:arcana "+11" :arcana-check true
   :history "+11" :history-check true
   :investigation "+11" :investigation-check true
   :insight "+7" :insight-check true
   :perception "+1" :medicine "+1" :nature "+5" :religion "+5"
   :acrobatics "+2" :athletics "-1" :stealth "+2" :deception "+0"
   :intimidation "+0" :performance "+0" :persuasion "+0" :survival "+1"
   :animal-handling "+1" :sleight-of-hand "+2"})

(def ^:private detail-fields
  {:character-name "Ysolde Vantreaux"
   :character-name-2 "Ysolde Vantreaux"
   :class-level "Wizard 20 (School of Evocation)"
   :background "Sage" :player-name "fixture" :race "High Elf"
   :alignment "Neutral Good" :xp "355,000"
   :ac "15" :initiative "+2" :speed "30 ft."
   :hp-max "122" :hp-current "122" :hp-temp "0"
   :hd "20d6" :prof-bonus "+6" :passive "11"
   :age "241" :height "5'11\"" :weight "134 lb."
   :eyes "Pale grey" :skin "Fair" :hair "Silver, braided"
   :spellcasting-class-1 "Wizard"
   :spellcasting-ability-1 "Intelligence"
   :spell-save-dc-1 (str (+ 8 prof-bonus int-mod))
   :spell-attack-bonus-1 (str "+" (+ prof-bonus int-mod))
   :weapon-name-1 "Quarterstaff" :weapon-attack-bonus-1 "+5" :weapon-damage-1 "1d6+3 bludgeoning"
   :weapon-name-2 "Dagger" :weapon-attack-bonus-2 "+8" :weapon-damage-2 "1d4+2 piercing"
   :weapon-name-3 "Fire Bolt (cantrip)" :weapon-attack-bonus-3 "+11" :weapon-damage-3 "4d10 fire"
   :cp "0" :sp "0" :ep "0" :gp "1,240" :pp "60"
   :personality-traits "Speaks to books as though they can hear her. They occasionally answer."
   :ideals "Knowledge withheld is knowledge wasted."
   :bonds "The Vantreaux archive burned. She is rewriting it from memory, one volume a year."
   :flaws "Assumes she is the smartest person in the room, and is usually right, which makes it worse."
   :backstory (str "Apprenticed at eleven to a conjurer who did not survive his own summoning. "
                   "Finished her training alone, on stolen notes, and has been suspicious of "
                   "bound things ever since.")
   :allies "The Candlewrights; Archivist Bell; a copper dragon who owes her a favour and resents it."
   :other-profs "Common, Elvish, Draconic, Celestial, Deep Speech. Calligrapher's supplies."
   :features-and-traits (str "Arcane Recovery. Evocation Savant. Sculpt Spells. "
                             "Potent Cantrip. Empowered Evocation. Overchannel. Spell Mastery. "
                             "Signature Spells. Fey Ancestry. Trance.")
   :equipment (str "Quarterstaff, dagger, component pouch, scholar's pack, spellbook (bound in "
                   "grey wyvernhide), bedroll, ink and pens, 12 sheets of parchment.")
   :treasure "Ring of Spell Storing, Wand of the War Mage +2, Robe of the Archmagi, Ioun Stone (Mastery)"
   :attacks-and-spellcasting "Evocation save DC 19. Overchannel: 1/long rest, max damage on a spell of 5th level or lower."
   :inspiration "1"})

(defn -main [& _]
  (let [fields (merge detail-fields ability-fields skill-fields
                      (slot-fields) (spell-fields))
        out (io/file "target/sample-wizard.pdf")]
    (io/make-parents out)
    (with-open [doc (Loader/loadPDF (.readAllBytes
                                      (.openStream (io/resource "fillable-char-sheetstyle-1-1-spells.pdf"))))]
      (pdf/write-fields! doc fields false {})
      (with-open [o (FileOutputStream. out)] (.save doc o)))
    (println (format "wrote %s -- %d fields, %d KB"
                     (.getPath out) (count fields) (quot (.length out) 1024)))))

(-main)
