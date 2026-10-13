(ns orcpub.dnd.e5.planned-text
  "The builder's words for held picks (picks/describe-held): the Planned marker and what brings each
   one back. docs/kb/ux-guide.md on agents/develop: a few words, the player's words."
  (:require [orcpub.common :as common]))

(defn- title [k] (common/kw-to-name k true))

(defn when-short
  "What brings a held pick back, in a few characters for the marker: \"L7\", \"if Hunter\"."
  [{:keys [class-level total-level first-class not-first-class class choice name]}]
  (cond
    (or class-level total-level) (str "L" (or class-level total-level))
    first-class (str "if " (title first-class) " first")
    not-first-class (str "if " (title not-first-class) " not first")
    class (str "if " (title class))
    choice (str "if " name)))

(defn when-long
  "What brings a held pick back, as the rest of \"Steel Will auto-fills …\"."
  [{:keys [class-level total-level first-class not-first-class class choice name]}]
  (cond
    (or class-level total-level) (str "at level " (or class-level total-level))
    first-class (str "if " (title first-class) " is your starting class")
    not-first-class (str "if " (title not-first-class) " is not your starting class")
    class (str "if you add " (title class) " again")
    choice (str "if you choose " name)
    :else "when it applies again"))

(defn marker
  "The Planned marker for `held` (picks/describe-held entries): one by name, several as a count."
  [held]
  (case (count held)
    0 nil
    1 (let [{:keys [name returns]} (first held)
            w (when-short returns)]
        (str "Planned: " name (when w (str " (" w ")"))))
    (str (count held) " planned")))

(defn auto-fills
  "\"Archery auto-fills at level 10.\""
  [{:keys [name returns]}]
  (str name " auto-fills " (when-long returns) "."))

(defn list-line
  "One held pick in a list: \"Archery · auto-fills at level 10\"."
  [{:keys [name returns]}]
  (str name " · auto-fills " (when-long returns)))
