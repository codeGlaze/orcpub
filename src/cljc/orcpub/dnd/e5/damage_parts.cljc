(ns orcpub.dnd.e5.damage-parts
  "A weapon's damage as a list of parts: the weapon's own dice first, then each
  extra a magic item adds, each with the condition under which it applies.
  Layout and rules: weapons-table-spec.md and handoff-weapons-table.md."
  (:require [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5.damage-types :as damage-types]
            [orcpub.dnd.e5.weapons :as weapons]))

(spec/def ::die pos-int?)
(spec/def ::die-count pos-int?)
(spec/def ::bonus int?)
(spec/def ::type (set (conj damage-types/damage-types :weapon)))

(spec/def ::switch keyword?)
(spec/def ::vs (spec/coll-of keyword? :kind set? :min-count 1))
(spec/def ::charge pos-int?)
(spec/def ::per-rest #{:short :long})
(spec/def ::when
  (spec/or :always #{:always}
           :miss #{:miss}
           :crit #{:crit}
           :switch (spec/keys :req [::switch])
           :vs (spec/keys :req [::vs])
           :charge (spec/keys :req [::charge])
           :per-rest (spec/keys :req [::per-rest])))

(spec/def ::part
  (spec/keys :req [::die ::die-count ::type ::when]
             :opt [::bonus]))
(spec/def ::parts (spec/coll-of ::part :kind vector?))

(spec/def ::id keyword?)
(spec/def ::label string?)
(spec/def ::switch-def (spec/keys :req [::id ::label]))
(spec/def ::switches (spec/coll-of ::switch-def :kind vector?))
(spec/def ::charges pos-int?)

(spec/def ::kind #{:save :heal :condition :mastery})
(spec/def ::dc pos-int?)
(spec/def ::ability keyword?)
(spec/def ::on-fail keyword?)
(spec/def ::text string?)
(spec/def ::effect
  (spec/keys :req [::kind ::text ::when]
             :opt [::dc ::ability ::on-fail]))
(spec/def ::effects (spec/coll-of ::effect :kind vector?))

(defn weapon-type
  "The damage type a weapon deals: an item's replacement type (Sun Blade's
  radiant) over its base weapon's. Takes a weapon map; returns a damage-type
  keyword, or nil for a weapon that deals no damage (the Net)."
  [weapon]
  (or (:orcpub.dnd.e5.magic-items/magical-damage-type weapon)
      (::weapons/damage-type weapon)))

(defn base-part
  "The weapon's own damage as a part that applies on every hit. Takes the
  weapon map, the total damage modifier and whether it is wielded in two hands
  (only a Versatile weapon's dice change). Returns a part, or nil when the
  weapon has no damage dice."
  [weapon damage-modifier two-handed?]
  (let [{:keys [::weapons/damage-die ::weapons/damage-die-count ::weapons/versatile]} weapon
        dice (if (and two-handed? versatile) versatile weapon)]
    (when damage-die
      {::die (::weapons/damage-die dice)
       ::die-count (::weapons/damage-die-count dice damage-die-count)
       ::bonus damage-modifier
       ::type (weapon-type weapon)
       ::when :always})))

(defn resolve-part
  "Replaces a part's :weapon type with the weapon's own damage type. Takes the
  weapon map and a part; returns the part. GOTCHA: on a weapon with no damage
  type (a Vicious Net) the type becomes nil."
  [weapon part]
  (cond-> part
    (= :weapon (::type part)) (assoc ::type (weapon-type weapon))))

(defn weapon-parts
  "Every damage part of a weapon: the base part first, then the item's extras
  with :weapon types resolved. Takes the weapon map, the total damage modifier
  and whether it is wielded in two hands. Returns a vector of parts."
  [weapon damage-modifier two-handed?]
  (let [base (base-part weapon damage-modifier two-handed?)
        extras (map (partial resolve-part weapon)
                    (:orcpub.dnd.e5.magic-items/damage-parts weapon))]
    (into (if base [base] []) extras)))

(defn applies?
  "Whether a part or effect is added to an ordinary damage roll in a situation.
  Takes the part and a map of :switches (the set of switch ids that are on),
  :vs (the target's creature type) and :crit? (a natural 20). Returns a boolean.
  Miss, charge and per-rest parts are rolled from their own button, so never apply."
  [{when-applies ::when} {:keys [switches vs crit?]}]
  (cond
    (= :always when-applies) true
    (= :crit when-applies) (boolean crit?)
    (::switch when-applies) (contains? (set switches) (::switch when-applies))
    (::vs when-applies) (contains? (::vs when-applies) vs)
    :else false))

(defn applying-parts
  "The parts of a weapon that a damage roll adds up in a situation. Takes the
  weapon map, the total damage modifier, whether it is wielded in two hands and
  a situation map as for `applies?`. Returns a vector of parts."
  [weapon damage-modifier two-handed? situation]
  (filterv #(applies? % situation)
           (weapon-parts weapon damage-modifier two-handed?)))
