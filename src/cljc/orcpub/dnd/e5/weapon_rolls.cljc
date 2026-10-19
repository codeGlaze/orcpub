(ns orcpub.dnd.e5.weapon-rolls
  "Rolling a weapon: the attack's d20 and the damage, part by part, for the result sheet and
  card. Every die comes from a function passed in, so a test can fix the rolls. Layout and
  rules: weapons-table-spec.md, handoff-weapons-table.md (stage 2)."
  (:require [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.weapon-damage :as weapon-damage]))

(defn roll-attack
  "Rolls a to-hit. Takes {:bonus :mode (:normal :adv :dis) :crit-min} and a die fn of sides.
  Returns {:dice [d20 ...] :kept :bonus :total :crit? :mode}: two d20s for advantage or
  disadvantage (the higher or lower kept), crit? when the kept die reaches crit-min."
  [{:keys [bonus mode crit-min] :or {mode :normal crit-min 20}} roll-die]
  (let [dice (if (= :normal mode) [(roll-die 20)] [(roll-die 20) (roll-die 20)])
        kept (case mode :adv (apply max dice) :dis (apply min dice) (first dice))]
    {:dice dice :kept kept :bonus bonus :total (+ kept bonus) :crit? (>= kept crit-min) :mode mode}))

(defn part-label
  "A damage part's name in a result: its type, then why it applies, such as \"fire · ablaze\"
  or \"radiant · vs undead\". Takes the entry and the toggle names (id to name)."
  [entry names]
  (let [{toggle ::mi/while against ::mi/against} entry
        type (some-> (::weapons/damage-type entry) common/safe-name)]
    (s/join " · " (remove nil? [(or type "damage")
                                     (when toggle (s/lower-case (get names toggle (common/safe-name toggle))))
                                     (when against (str "vs " (disp/creature-types-str against "and")))]))))

(defn roll-part
  "Rolls one damage entry. Takes the entry, the toggle names and a die fn of sides. Returns
  {:label :type :rolls :flat :detail :subtotal}, :detail as written in the result: \"1d8 [6] + 5\"."
  [entry names roll-die]
  (let [n (::weapons/damage-die-count entry 0)
        die (::weapons/damage-die entry)
        rolls (if die (vec (repeatedly n #(roll-die die))) [])
        flat (+ (or (::mi/damage-bonus entry) 0) (or (::weapon-damage/bonus entry) 0))]
    {:label (part-label entry names)
     :type (::weapons/damage-type entry)
     :rolls rolls
     :flat flat
     :detail (str (when die (str n "d" die " [" (s/join "," rolls) "]"))
                  (when-not (zero? flat) (str (when die " ") (if (neg? flat) "- " "+ ") (Math/abs flat))))
     :subtotal (+ (apply + rolls) flat)}))

(defn after-total
  "The lines listed after a damage total and never added to it: each save or effect that
  applies in the situation. Takes the weapon and a situation (as for weapon-damage/applies?).
  Returns [{:label :text}], e.g. {:label \"Target saves\" :text \"DC 15 Str or prone\"}."
  [weapon situation]
  (for [e (::mi/effects weapon)
        :when (weapon-damage/applies? e situation)
        :let [summary (disp/effect-summary e)]
        :when summary]
    {:label "Target saves" :text summary}))

(defn roll-damage
  "Rolls a weapon's damage in a situation. Takes the weapon, its damage modifier, two-handed?,
  the situation ({:toggles :against :crit? :roll}), whether a crit doubles the dice, and a die
  fn of sides. Returns {:parts [...] :total :after [...]}."
  [weapon damage-modifier two-handed? situation crit? roll-die]
  (let [names (disp/toggle-names weapon)
        entries (cond-> (weapon-damage/applying-damage weapon damage-modifier two-handed? situation)
                  crit? disp/crit-entries)
        parts (mapv #(roll-part % names roll-die) entries)]
    {:parts parts
     :total (apply + (map :subtotal parts))
     :after (vec (after-total weapon situation))}))
