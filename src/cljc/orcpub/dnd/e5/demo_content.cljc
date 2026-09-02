(ns orcpub.dnd.e5.demo-content
  "The demo/example content pack, as a declarative recipe built in code so it's
   proofed by the compiler and the build — never hand-typed EDN. The build-time
   emitter (orcpub.build.demo-emit) serializes this to the bundled .orcbrew the app
   loads at boot.

   Grow this pack as content features land: each addition doubles as a built-in
   test that the feature exports, imports, and builds. Every item must carry
   :option-pack (the load floor) and satisfy its content type's save spec. See
   docs/kb/demo-content-tier.md."
  (:require [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.spells :as spells]))

(def source-name
  "The source the demo pack's content is filed under in the content library."
  "Demo Content")

(def requires
  "The :orcbrew/requires compat tag for this pack — the features / minimum build it
   needs. Empty while the pack is still old-format compatible; add entries as it
   starts using features older builds can't read."
  [])

(def plugins
  "The demo pack as a plugin map {source {content-type {key item}}}, the same shape
   an export produces and an import reads."
  {source-name
   {::e5/feats
    {:demo-tough
     {:key :demo-tough
      :name "Demo: Tough"
      :option-pack source-name
      :description (str "You are hardier than most: increase your Constitution "
                        "by 1, and one ability score of your choice by 1.")
      ;; Exercises the ability-increase spread: [amount pool] pairs — a fixed +1
      ;; CON plus one floating +1 the player assigns. See ability-increase-spreads.
      :ability-increases [[1 :con] [1 :any]]}
     :demo-versatile
     {:key :demo-versatile
      :name "Demo: Versatile"
      :option-pack source-name
      :description "You adopt a fighting style of your choice."
      ;; Exercises the generic :grant primitive — a choice from a named pool. On
      ;; the app path a feat grants from the built-in fighting styles.
      :grant {:from :fighting-styles :choose 1}}}
    ::e5/backgrounds
    {:demo-traveler
     {:key :demo-traveler
      :name "Demo: Traveler"
      :option-pack source-name
      :description (str "A life on the road toughened you: increase your "
                        "Constitution by 1 and gain proficiency in Constitution "
                        "saving throws.")
      ;; Exercises the :save rider on an ability-increase increment (the +1 also
      ;; grants that ability's save), attributed to the background (:general) silo.
      :ability-increases [[1 :con :save]]}}
    ::e5/races
    {:demo-tideborn
     {:key :demo-tideborn
      :name "Demo: Tideborn"
      :option-pack source-name
      :description (str "Folk of the tidal deeps — quick and hardy, at home in "
                        "the water and warded against flame.")
      ;; Exercises the ASI spread on the RACE silo (race-column attribution),
      ;; standalone save proficiencies, and the :props mechanics vocabulary.
      :ability-increases [[2 :dex] [1 :any]]
      :save-proficiencies [[1 :con]]
      :props {:speed 30
              :swimming-speed 30
              :damage-resistance {:fire true}}}}
    ::e5/spells
    {:demo-spark
     {:key :demo-spark
      :name "Demo: Spark"
      :school spells/evocation
      :level 0
      :casting-time "1 action"
      :range "30 feet"
      :duration "Instantaneous"
      :components {:verbal true :somatic true}
      :description (str "You fling a mote of teal light at a creature or object "
                        "within range. Make a ranged spell attack against the "
                        "target. On a hit, it takes 1d8 radiant damage. The "
                        "damage increases by 1d8 at 5th level (2d8), 11th level "
                        "(3d8), and 17th level (4d8).")
      :spell-lists {:wizard true :sorcerer true}
      :option-pack source-name}}}})
