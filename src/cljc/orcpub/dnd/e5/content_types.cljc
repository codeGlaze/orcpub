(ns orcpub.dnd.e5.content-types
  "Single source of truth describing each plugin-based homebrew content type.

   Part of the content-extensibility work (docs/kb/content-extensibility.md,
   Phase 4). Today the per-type wiring (route, db default, localStorage, events,
   subs, page-map entry) is duplicated across many files. This registry holds the
   per-type facts once; the wiring loops (added in later sub-phases) consume it so a
   new type is one entry instead of edits in ~8 files.

   SCOPE: the homebrew content types that flow through the `reg-save-homebrew` /
   plugins-map pipeline. Magic items and the combat tracker are intentionally NOT
   here — they do not use the plugins map or the shared homebrew factories (they
   have bespoke save/storage), so folding them in would be wrong (see the inventory
   in the content-extensibility KB docs).

   LEAF NAMESPACE: requires only `route-map` (for the route keyword vars). Spec,
   builder-item, and plugin keys are written as fully-qualified keyword literals so
   this namespace pulls in no domain/events/subs/views code and cannot create the
   circular deps the app already works around (decisions D7/D8).

   Keys per descriptor:
     :id                content type id (keyword)
     :type-name         human label used in builders / messages
     :builder-item      app-db key holding the in-progress item
     :spec              spec the saved item is validated against
     :plugin-key        ::e5/* content key under which items are stored in :plugins
     :route-kw          builder page route keyword
     :route-seg         builder page URL path segment
     :local-storage-key localStorage draft key"
  (:require [orcpub.route-map :as route-map]))

(def content-types
  [{:id :spell
    :type-name "Spell"
    :builder-item :orcpub.dnd.e5.spells/builder-item
    :spec :orcpub.dnd.e5.spells/homebrew-spell
    :plugin-key :orcpub.dnd.e5/spells
    :route-kw route-map/dnd-e5-spell-builder-page-route
    :route-seg "spell-builder"
    :local-storage-key "spell"}
   {:id :monster
    :type-name "Monster"
    :builder-item :orcpub.dnd.e5.monsters/builder-item
    :spec :orcpub.dnd.e5.monsters/homebrew-monster
    :plugin-key :orcpub.dnd.e5/monsters
    :route-kw route-map/dnd-e5-monster-builder-page-route
    :route-seg "monster-builder"
    :local-storage-key "monster"}
   {:id :encounter
    :type-name "Encounter"
    :builder-item :orcpub.dnd.e5.encounters/builder-item
    ;; note: encounter validates against ::encounters/encounter (no homebrew-* alias)
    :spec :orcpub.dnd.e5.encounters/encounter
    :plugin-key :orcpub.dnd.e5/encounters
    :route-kw route-map/dnd-e5-encounter-builder-page-route
    :route-seg "encounter-builder"
    :local-storage-key "encounter"}
   {:id :background
    :type-name "Background"
    :builder-item :orcpub.dnd.e5.backgrounds/builder-item
    :spec :orcpub.dnd.e5.backgrounds/homebrew-background
    :plugin-key :orcpub.dnd.e5/backgrounds
    :route-kw route-map/dnd-e5-background-builder-page-route
    :route-seg "background-builder"
    :local-storage-key "background"}
   {:id :language
    :type-name "Language"
    :builder-item :orcpub.dnd.e5.languages/builder-item
    :spec :orcpub.dnd.e5.languages/homebrew-language
    :plugin-key :orcpub.dnd.e5/languages
    :route-kw route-map/dnd-e5-language-builder-page-route
    :route-seg "language-builder"
    :local-storage-key "language"}
   {:id :invocation
    :type-name "Eldritch Invocation"
    :builder-item :orcpub.dnd.e5.classes/invocation-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-invocation
    :plugin-key :orcpub.dnd.e5/invocations
    :route-kw route-map/dnd-e5-invocation-builder-page-route
    :route-seg "invocation-builder"
    :local-storage-key "invocation"}
   {:id :boon
    :type-name "Pact Boon"
    :builder-item :orcpub.dnd.e5.classes/boon-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-boon
    :plugin-key :orcpub.dnd.e5/boons
    :route-kw route-map/dnd-e5-boon-builder-page-route
    :route-seg "boon-builder"
    :local-storage-key "boon"}
   {:id :selection
    :type-name "Selection"
    :builder-item :orcpub.dnd.e5.selections/builder-item
    :spec :orcpub.dnd.e5.selections/homebrew-selection
    :plugin-key :orcpub.dnd.e5/selections
    :route-kw route-map/dnd-e5-selection-builder-page-route
    :route-seg "selection-builder"
    :local-storage-key "selection"}
   {:id :feat
    :type-name "Feat"
    :builder-item :orcpub.dnd.e5.feats/builder-item
    :spec :orcpub.dnd.e5.feats/homebrew-feat
    :plugin-key :orcpub.dnd.e5/feats
    :route-kw route-map/dnd-e5-feat-builder-page-route
    :route-seg "feat-builder"
    :local-storage-key "feat"}
   {:id :race
    :type-name "Race"
    :builder-item :orcpub.dnd.e5.races/builder-item
    :spec :orcpub.dnd.e5.races/homebrew-race
    :plugin-key :orcpub.dnd.e5/races
    :route-kw route-map/dnd-e5-race-builder-page-route
    :route-seg "race-builder"
    :local-storage-key "race"}
   {:id :subrace
    :type-name "Subrace"
    :builder-item :orcpub.dnd.e5.races/subrace-builder-item
    :spec :orcpub.dnd.e5.races/homebrew-subrace
    :plugin-key :orcpub.dnd.e5/subraces
    :route-kw route-map/dnd-e5-subrace-builder-page-route
    :route-seg "subrace-builder"
    :local-storage-key "subrace"}
   {:id :subclass
    :type-name "Subclass"
    :builder-item :orcpub.dnd.e5.classes/subclass-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-subclass
    :plugin-key :orcpub.dnd.e5/subclasses
    :route-kw route-map/dnd-e5-subclass-builder-page-route
    :route-seg "subclass-builder"
    :local-storage-key "subclass"}
   {:id :class
    :type-name "Class"
    :builder-item :orcpub.dnd.e5.classes/builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-class
    :plugin-key :orcpub.dnd.e5/classes
    :route-kw route-map/dnd-e5-class-builder-page-route
    :route-seg "class-builder"
    :local-storage-key "class"}])

(def by-id
  "Registry indexed by :id."
  (into {} (map (juxt :id identity)) content-types))
