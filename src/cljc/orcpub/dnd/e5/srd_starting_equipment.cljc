(ns orcpub.dnd.e5.srd-starting-equipment
  "SRD classes' starting equipment expressed as serializable data (the shorthand +
   :equipment-selections form from options.cljc), so the class builder can 'start from'
   an SRD class. The live class definitions in classes.cljc remain the ground truth;
   orcpub.starting-equipment-test asserts each entry here compiles (via class-option) to
   the same equipment the live class produces. Keys, once shipped, are frozen (a wrong
   key is fixed with a shim, never a rename) — see docs/kb/starting-equipment-override-ledger.md.")

(def srd-class-equipment
  {;; Pure-shorthand class — copied verbatim from wizard-option (classes.cljc).
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
