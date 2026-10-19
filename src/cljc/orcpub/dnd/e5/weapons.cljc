(ns orcpub.dnd.e5.weapons
  (:require [orcpub.common :as common]
            [orcpub.dnd.e5.srd-equipment :as srd]))

(def app-fields
  "The app's own fields on SRD weapons, merged onto the generated entries by :key
   (equipment-parity.md)."
  {:greataxe {::subtype :axe}
   :greatsword {::subtype :sword}
   :longsword {::subtype :sword ::finesse? false}
   :quarterstaff {::subtype :staff}
   :rapier {::subtype :sword}
   :scimitar {::subtype :sword}
   :shortsword {::subtype :sword}
   :net {:summary "restrains"
         ::description "A Large or smaller creature hit by a net is restrained until it is freed. A net has no effect on creatures that are formless, or creatures that are Huge or larger. A creature can use its action to make a DC 10 Strength check, freeing itself or another creature within its reach on a success. Dealing 5 slashing damage to the net (AC 10) also frees the creature without harming it, ending the effect and destroying the net. When you use an action, bonus action, or reaction to attack with a net, you can make only one attack regardless of the number of attacks you can normally make."}})

(def community-weapons
  "Weapons outside the SRD, kept by hand: the DMV firearms."
  [{:name "Firearm, Hand (DMV)",
    ::damage-type :piercing,
    ::damage-die 10,
    ::type :martial,
    ::damage-die-count 1,
    ::ranged? true,
    ::heavy? true,
    ::range {::min 30, ::max 90},
    :key :firearm-hand
    ::loading? true
    ::two-handed? false}
   {:name "Firearm, Burst (DMV)",
    ::damage-type :piercing,
    ::damage-die 6,
    ::type :martial,
    ::damage-die-count 2,
    ::ranged? true,
    ::heavy? true,
    ::range {::min 10, ::max 30},
    :key :firearm-burst
    ::two-handed? true}
   {:name "Firearm, Long (DMV)",
    ::damage-type :piercing,
    ::damage-die 12,
    ::type :martial,
    ::damage-die-count 1,
    ::ranged? true,
    ::heavy? true,
    ::range {::min 60, ::max 120},
    :key :firearm-long
    ::loading? true
    ::two-handed? true}])

(def weapon-order
  "Every weapon's :key, in the order lists show them."
  [:crossbow-light :dart :shortbow :sling :club :dagger :greatclub :handaxe :javelin :light-hammer :mace :quarterstaff :sickle :spear :battleaxe :flail :glaive :greataxe :greatsword :halberd :lance :longsword :maul :morningstar :pike :rapier :scimitar :shortsword :trident :war-pick :warhammer :whip :blowgun :crossbow-hand :crossbow-heavy :longbow :net :firearm-hand :firearm-burst :firearm-long])

(def weapons
  "Every weapon: the generated SRD entries with app-fields merged in, then community-weapons,
   in weapon-order."
  (let [by-key (into {} (map (juxt :key identity))
                     (concat (map #(merge % (app-fields (:key %))) srd/weapons) community-weapons))]
    (mapv by-key weapon-order)))

(def ammunition
  (common/add-keys
   [{:name "Arrow" ::type :ammunition :sell-qty 20 :cost {:num 1 :type :gp} :weight "1 lb."}
    {:name "Blowgun needle" ::type :ammunition :sell-qty 50 :cost {:num 1 :type :gp} :weight "1 lb."}
    {:name "Crossbow bolt" ::type :ammunition :sell-qty 20 :cost {:num 1 :type :gp} :weight "1½ lb."}
    {:name "Firearm bullet" ::type :ammunition :sell-qty 20 :cost {:num 2 :type :gp} :weight "1½ lb."}
    {:name "Sling bullet" ::type :ammunition :sell-qty 20 :cost {:num 4 :type :cp} :weight "1½ lb."}]))

(def weapons-map
  (zipmap (map :key weapons) weapons))

(defn weapons-of-type [weapons type]
  (filter #(= type (::type %)) weapons))

(defn martial-weapons [weapons]
  (weapons-of-type weapons :martial))

(defn simple-weapons [weapons]
  (weapons-of-type weapons :simple))

(defn light-melee-weapon? [{:keys [::light? ::melee?]}]
  (and melee? light?))

(defn one-handed-weapon? [{:keys [::two-handed?]}]
  (not two-handed?))
