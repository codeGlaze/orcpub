(ns orcpub.dnd.e5.spell-print
  "Shortening a spell's fields to fit a printed card or a spellbook row, and the subtitle
   line naming its class, level and school. Deliberately lossy -- a card field is about
   sixteen characters -- so each docstring says what it drops.
   Pure string work, no PDFBox. These were in pdf.clj, so spellbook.clj depended on a
   3,200-line clj file for six functions that never touch a PDF, beside spell-annotations,
   which was lifted out of the print path on the same grounds."
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(defn spell-school-level
  "The subtitle line of a spell card: \"Wizard Cantrip Evocation\" at level 0,
   \"Wizard Level 3 Evocation\" otherwise. A missing school reads \"Unknown\" and a
   missing level \"?\" rather than throwing, since homebrew spells reach here
   unvalidated."
  [{:keys [level school]} class-nm]
  (let [school-str (if school (common/ascii-capitalize school) "Unknown")]
    (if (and level (zero? level))
      (str class-nm " Cantrip " school-str)
      (str class-nm " Level " (or level "?") " " school-str))))

(defn abbreviate-times
  "\"minute\" -> \"min\", \"hour\" -> \"hr\", anywhere in the string. Case-sensitive, so
   a capitalized \"Hour\" passes through untouched. Throws on nil."
  [time]
  (-> time
      (s/replace #"minute" "min")
      (s/replace #"hour" "hr")))

(defn max-len
  "`s` truncated to at most `len` characters, with no ellipsis. nil returns nil."
  [s len]
  (if (<= (count s) len)
    s
    (subs s 0 len)))

(defn abbreviate-duration
  "Spell duration shortened to fit a card field: \"Concentration, up to 1 hour\"
   -> \"Conc, 1 hr\", \"Instantaneous\" -> \"Inst\", \"round\" -> \"Rnd\", then hard
   truncated at 16 characters. nil passes through."
  [duration]
  (when duration
    (-> duration
        (s/replace #"Concentration,? up to " "Conc, ")
        abbreviate-times
        (s/replace #"Instantaneous.*" "Inst")
        (s/replace #"round" "Rnd")
        (max-len 16))))

(defn abbreviate-casting-time
  "\"1 bonus action\" -> \"1 B.A.\", \"1 reaction\" -> \"1 React.\". Reaction goes before
   action, which it contains."
  [casting-time]
  (-> casting-time
      abbreviate-times
      (s/replace #"bonus action" "B.A.")
      (s/replace #"reaction" "React.")
      (s/replace #"\baction" "Act.")))

(defn abbreviate-range
  "Spell range shortened to fit a card field: anything starting \"Self\" collapses
   to \"Self\" (dropping the shape, so \"Self (15-foot cone)\" loses the cone), and
   \"feet\" -> \"ft\". Throws on nil."
  [range]
  (-> range
      (s/replace #"Self.*" "Self")
      (s/replace #"feet" "ft")))
