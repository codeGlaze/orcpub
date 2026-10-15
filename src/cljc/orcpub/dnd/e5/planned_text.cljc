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

(defn returns-line
  "When a held pick comes back, beside its name in a list: \"auto-fills at level 10\"."
  [{:keys [returns]}]
  (str "auto-fills " (when-long returns)))

(defn panel-rows
  "The rows behind a Planned line, leaving out what the line says: one pick is only when it returns;
   picks at levels are a name and \"level N\"; picks sharing a reason the line states
   (`says-reason?`) are names only; others, a name and when. Each row: {:held :label :aside}."
  [held says-reason?]
  (let [level (fn [{{:keys [class-level total-level]} :returns}] (or class-level total-level))
        row (fn [h label aside] {:held h :label label :aside aside})]
    (cond
      (= 1 (count held)) [(row (first held) nil (returns-line (first held)))]
      (every? level held) (map #(row % (:name %) (str "level " (level %))) held)
      (and says-reason? (apply = (map :returns held))) (map #(row % (:name %) nil) held)
      :else (map #(row % (:name %) (returns-line %)) held))))

(defn class-line
  "The line under a class row for its held picks: one by name, otherwise a count that says why
   when they share a reason (\"3 planned at higher levels\")."
  [held]
  (let [rs (map :returns held)
        n (count held)]
    (cond
      (<= n 1) (marker held)
      (every? #(or (:class-level %) (:total-level %)) rs) (str n " planned at higher levels")
      (and (every? :first-class rs) (apply = (map :first-class rs)))
      (str n " planned if " (title (:first-class (first rs))) " is your starting class")
      :else (str n " planned"))))

(defn restored
  "The notice for picks that came back on their own: one by name, several from one class as
   \"Rogue picks restored (4)\", otherwise a count. `picks`: {:name :class} maps."
  [picks]
  (let [n (count picks)
        classes (set (map :class picks))]
    (cond
      (= 1 n) (str (:name (first picks)) " restored")
      (and (= 1 (count classes)) (first classes))
      (str (title (first classes)) " picks restored (" n ")")
      :else (str n " picks restored"))))

(defn opened
  "The notice on opening a character whose stored choices no longer apply: \"3 choices don't
   apply right now\"."
  [n]
  (if (= 1 n) "1 choice doesn't apply right now" (str n " choices don't apply right now")))
