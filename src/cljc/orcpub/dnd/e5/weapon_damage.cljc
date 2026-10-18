(ns orcpub.dnd.e5.weapon-damage
  "A weapon's damage as entries: the weapon's own dice first, then each extra a
  magic item adds, with the conditions under which each applies. The fields and
  their meaning: weapon-damage-model.md."
  (:require [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.weapons :as weapons]))

(defn weapon-type
  "The damage type a weapon deals: an item's replacement type (Sun Blade's
  radiant) over its base weapon's. Takes a weapon map; returns a damage-type
  keyword, or nil for a weapon that deals no damage (the Net)."
  [weapon]
  (or (::mi/magical-damage-type weapon)
      (::weapons/damage-type weapon)))

(defn base-damage
  "The weapon's own damage as an entry. Takes the weapon map, the total damage
  modifier and whether it is wielded in two hands (only a Versatile weapon's
  dice change). Returns the entry, with the modifier under ::bonus, or nil when
  the weapon has no damage dice."
  [weapon damage-modifier two-handed?]
  (let [{:keys [::weapons/damage-die ::weapons/damage-die-count ::weapons/versatile]} weapon
        dice (if (and two-handed? versatile) versatile weapon)]
    (when damage-die
      {::weapons/damage-die (::weapons/damage-die dice)
       ::weapons/damage-die-count (::weapons/damage-die-count dice damage-die-count)
       ::weapons/damage-type (weapon-type weapon)
       ::bonus damage-modifier})))

(defn resolve-extra
  "An item's extra damage entry with its type filled in: an entry that names no
  type deals the weapon's. Takes the weapon map and the entry; returns the entry.
  GOTCHA: on a weapon with no damage type (a Vicious Net) the type stays nil."
  [weapon extra]
  (cond-> extra
    (nil? (::weapons/damage-type extra)) (assoc ::weapons/damage-type (weapon-type weapon))))

(defn weapon-damage
  "Every damage entry of a weapon: its own first, then the item's extras with
  types resolved. Takes the weapon map, the total damage modifier and whether it
  is wielded in two hands. Returns a vector of entries."
  [weapon damage-modifier two-handed?]
  (let [base (base-damage weapon damage-modifier two-handed?)]
    (into (if base [base] [])
          (map (partial resolve-extra weapon))
          (::mi/extra-damage weapon))))

(defn crit-min
  "The lowest d20 roll that is a critical hit with this weapon. Takes the
  character's critical-hit values (a set such as #{19 20}, or nil) and the
  weapon map; returns an int, the lower of the character's and the item's
  ::mi/crit-from."
  [crit-values weapon]
  (min (if (seq crit-values) (apply min crit-values) 20)
       (::mi/crit-from weapon 20)))

(defn applies?
  "Whether an entry or effect is part of an ordinary roll in a situation; every
  condition it names must hold. Takes the entry and a map of :toggles (set of
  toggle ids that are on), :against (the target's creature type), :roll (the
  d20 as rolled), :crit? and :miss?. Returns a boolean. Entries with a charge
  cost or a recharge are used from their own button, so never apply."
  [{:keys [::mi/against ::mi/on ::mi/min-roll ::mi/charge-cost ::mi/recharge] toggle ::mi/while}
   {:keys [toggles roll crit? miss?] target :against}]
  (boolean
   (and (nil? charge-cost)
        (nil? recharge)
        (case (or on :hit)
          :hit (not miss?)
          :miss (boolean miss?)
          :crit (and (not miss?) (boolean crit?)))
        (or (nil? min-roll) (and roll (>= roll min-roll)))
        (or (nil? toggle) (contains? (set toggles) toggle))
        (or (nil? against) (contains? against target)))))

(defn applying-damage
  "The damage entries a roll adds up in a situation. Takes the weapon map, the
  total damage modifier, whether it is wielded in two hands and a situation map
  as for `applies?`. Returns a vector of entries."
  [weapon damage-modifier two-handed? situation]
  (filterv #(applies? % situation)
           (weapon-damage weapon damage-modifier two-handed?)))
