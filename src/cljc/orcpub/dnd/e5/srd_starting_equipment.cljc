(ns orcpub.dnd.e5.srd-starting-equipment
  "SRD classes' starting equipment expressed as serializable data (the shorthand +
   :equipment-selections form from options.cljc), so the class builder can 'start from'
   an SRD class. The live class definitions in classes.cljc remain the ground truth;
   orcpub.starting-equipment-test asserts each entry here compiles (via class-option) to
   the same equipment the live class produces. Keys, once shipped, are frozen (a wrong
   key is fixed with a shim, never a rename) — see docs/kb/starting-equipment-override-ledger.md."
  (:require [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.armor :as armor]
            [orcpub.dnd.e5.equipment :as equipment]))

(def srd-class-equipment
  {;; Pure-shorthand classes — copied verbatim from their -option fns (classes.cljc).
   :barbarian
   {:weapons {:javelin 4}
    :equipment {:explorers-pack 1}
    :weapon-choices [{:name "Martial Weapon" :options {:greataxe 1 :martial 1}}
                     {:name "Simple Weapon"  :options {:handaxe 2 :simple 1}}]}

   :monk
   {:weapons {:dart 10}
    :equipment-choices [{:name "Equipment Pack" :options {:dungeoneers-pack 1 :explorers-pack 1}}]
    :weapon-choices    [{:name "Weapon"         :options {:shortsword 1 :simple 1}}]}

   ;; Shorthand + one :selections group.
   :rogue
   {:armor {:leather 1}
    :weapons {:dagger 2}
    :equipment {:thieves-tools 1}
    :weapon-choices    [{:name "Melee Weapon"   :options {:rapier 1 :shortsword 1}}]
    :equipment-choices [{:name "Equipment Pack" :options {:burglars-pack 1 :dungeoneers-pack 1 :explorers-pack 1}}]
    :equipment-selections
    [{:name "Additional Weapon"
      :options [{:name "Shortbow, Quiver, 20 Arrows"
                 :grants [{:kind :weapon :key :shortbow :qty 5}    ; :shortbow 5 verbatim from live rogue-option
                          {:kind :equipment :key :quiver :qty 1}
                          {:kind :equipment :key :arrow :qty 20}]}
                {:name "Shortsword" :grants [{:kind :weapon :key :shortsword :qty 1}]}]}]}

   :wizard
   {:equipment {:spellbook 1}
    :equipment-choices [{:name "Equipment Pack"        :options {:scholars-pack 1 :explorers-pack 1}}
                        {:name "Spellcasting Equipment" :options {:component-pouch 1 :arcane-focus 1}}]
    :weapon-choices    [{:name "Melee Weapon"          :options {:quarterstaff 1 :dagger 1}}]}

   ;; :selections class — transcribed from fighter-option (classes.cljc:1106-1151) into the
   ;; serializable form: bundle options (:grants) and nested weapon picks (:choose).
   :fighter
   {:equipment-selections
    [{:name "Armor"
      :options [{:name "Chain Mail" :grants [{:kind :armor :key :chain-mail :qty 1}]}
                {:name "Leather Armor, Longbow, 20 Arrows"
                 :grants [{:kind :armor :key :leather :qty 1}
                          {:kind :weapon :key :longbow :qty 1}
                          {:kind :equipment :key :arrow :qty 20}]}]}
     {:name "Weapons"
      :options [{:name "Martial Weapon and Shield"
                 :grants [{:kind :armor :key :shield :qty 1}]
                 :choose [{:from :martial}]}
                {:name "Two Martial Weapons"
                 :choose [{:name "Martial Weapon 1" :from :martial}
                          {:name "Martial Weapon 2" :from :martial}]}]}
     {:name "Additional Weapons"
      :options [{:name "Light Crossbow and 20 Bolts"
                 :grants [{:kind :weapon :key :crossbow-light :qty 1}
                          {:kind :equipment :key :crossbow-bolt :qty 20}]}
                {:name "Two Handaxes"
                 :grants [{:kind :weapon :key :handaxe :qty 2}]}]}
     ;; Fighter also has a shorthand :equipment-choices pack — folded in as a group.
     {:name "Equipment Pack"
      :options [{:name "Dungeoneer's Pack" :grants [{:kind :equipment :key :dungeoneers-pack :qty 1}]}
                {:name "Explorer's Pack"   :grants [{:kind :equipment :key :explorers-pack :qty 1}]}]}]}})

;; --- Builder-ready form -----------------------------------------------------
;; The fill-in ("start from a class") hands the builder ONE editable form: fixed
;; grants as-is, and every choice — shorthand :*-choices and any :equipment-selections
;; — unified into :equipment-selections. A menu option keyed by a "chooser" (a weapon
;; class or a grouped-equipment key) becomes a :choose sub-choice, not a fixed grant, so
;; it expands to a pick the same way the shorthand did. Verified against the live class.

(def ^:private choice-key->kind
  {:weapon-choices :weapon :armor-choices :armor :equipment-choices :equipment})

;; item-keys that mean "pick one of a pool" rather than "grant this item"
(def ^:private chooser-labels
  {:simple "Any Simple Weapon" :martial "Any Martial Weapon"
   :holy-symbol "A Holy Symbol" :arcane-focus "An Arcane Focus" :druidic-focus "A Druidic Focus"
   :musical-instrument "A Musical Instrument" :pack "An Equipment Pack"})

(defn- item-name [k]
  (or (get-in weapons/weapons-map [k :name])
      (get-in armor/armor-map [k :name])
      (get-in equipment/equipment-map [k :name])
      (name k)))

(defn- shorthand-choice->group [kind {:keys [name options]}]
  {:name (or name "")
   :options (vec (for [[k q] options]
                   (if-let [label (chooser-labels k)]
                     {:name label :choose [{:from k}]}
                     {:name (item-name k) :grants [{:kind kind :key k :qty q}]})))})

(defn builder-equipment
  "The class's starting equipment in the builder's editable form (fixed grants +
   unified :equipment-selections). nil if the class isn't in the table."
  [class-kw]
  (when-let [{:keys [weapons armor equipment equipment-selections] :as e}
             (srd-class-equipment class-kw)]
    (let [from-shorthand (vec (for [[ck kind] choice-key->kind
                                    grp (get e ck) :when (seq (:options grp))]
                                (shorthand-choice->group kind grp)))
          groups (into (vec equipment-selections) from-shorthand)]
      (cond-> {}
        weapons        (assoc :weapons weapons)
        armor          (assoc :armor armor)
        equipment      (assoc :equipment equipment)
        (seq groups)   (assoc :equipment-selections groups)))))
