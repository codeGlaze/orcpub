(ns orcpub.dnd.e5.content-types
  "Single source of truth describing each plugin-based homebrew content type.

   The wiring loops that CONSUME this registry (see docs/kb/content-extensibility-framework.md):
     - subs   (spell_subs.cljs)  — builder-item passthrough subscriptions   [generated]
     - events (events.cljs)      — register-homebrew-content! for :homebrew-builder? entries [generated]
     - db     (db.cljs)          — default-value builder-item draft slots    [generated]
     - routes (route_map/routes.clj), core page-map                          [still hand-wired]
   So adding a homebrew type is (increasingly) ONE entry here instead of edits in ~9 files.

   SCOPE: the homebrew content types that flow through the `reg-save-homebrew` / plugins-map
   pipeline. Magic items and the combat tracker are intentionally NOT here — they don't use the
   plugins map or the shared homebrew factories (server-backed / transient), so folding them in
   would be wrong (decision D14).

   PURE-DATA LEAF: requires nothing. Spec, builder-item, plugin, and route keys are written as
   keyword literals so this ns pulls in no domain/routing/events/subs/views code and cannot
   create circular deps (D7/D8). This is what lets route_map / routes.clj read it to GENERATE
   the bidi tree + allowlist (the routes pass) without a cycle. :route-kw literals are guarded
   against drift from route_map's vars by a test (route_map-test / content_types-test).

   Keys per descriptor:
     :id                content type id (keyword)
     :type-name         human label used in builders / messages
     :builder-item      app-db key holding the in-progress item; MUST be
                        ::<ns>/<base>-builder-item (the event-keyword convention derives <base>)
     :spec              spec the saved item is validated against
     :plugin-key        ::e5/* content key under which items are stored in :plugins
     :route-kw          builder page route keyword
     :route-seg         builder page URL path segment
     :local-storage-key localStorage draft key
     :homebrew-builder? opt this type into the events + db generative loops (true)
     :default           the empty-draft value (usually {}); required with :homebrew-builder?")

(def content-types
  [{:id :spell
    :type-name "Spell"
    :builder-item :orcpub.dnd.e5.spells/builder-item
    :spec :orcpub.dnd.e5.spells/homebrew-spell
    :plugin-key :orcpub.dnd.e5/spells
    :route-kw :spell-builder-5e-page
    :route-seg "spell-builder"
    :local-storage-key "spell"}
   {:id :monster
    :type-name "Monster"
    :builder-item :orcpub.dnd.e5.monsters/builder-item
    :spec :orcpub.dnd.e5.monsters/homebrew-monster
    :plugin-key :orcpub.dnd.e5/monsters
    :route-kw :monster-builder-5e-page
    :route-seg "monster-builder"
    :local-storage-key "monster"}
   {:id :encounter
    :type-name "Encounter"
    :builder-item :orcpub.dnd.e5.encounters/builder-item
    ;; note: encounter validates against ::encounters/encounter (no homebrew-* alias)
    :spec :orcpub.dnd.e5.encounters/encounter
    :plugin-key :orcpub.dnd.e5/encounters
    :route-kw :encounter-builder-5e-page
    :route-seg "encounter-builder"
    :local-storage-key "encounter"}
   {:id :background
    :type-name "Background"
    :builder-item :orcpub.dnd.e5.backgrounds/builder-item
    :spec :orcpub.dnd.e5.backgrounds/homebrew-background
    :plugin-key :orcpub.dnd.e5/backgrounds
    :route-kw :background-builder-5e-page
    :route-seg "background-builder"
    :local-storage-key "background"}
   {:id :language
    :type-name "Language"
    :builder-item :orcpub.dnd.e5.languages/builder-item
    :spec :orcpub.dnd.e5.languages/homebrew-language
    :plugin-key :orcpub.dnd.e5/languages
    :route-kw :language-builder-5e-page
    :route-seg "language-builder"
    :local-storage-key "language"}
   {:id :invocation
    :type-name "Eldritch Invocation"
    :builder-item :orcpub.dnd.e5.classes/invocation-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-invocation
    :plugin-key :orcpub.dnd.e5/invocations
    :route-kw :invocation-builder-5e-page
    :route-seg "invocation-builder"
    :local-storage-key "invocation"}
   {:id :boon
    :type-name "Pact Boon"
    :builder-item :orcpub.dnd.e5.classes/boon-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-boon
    :plugin-key :orcpub.dnd.e5/boons
    :route-kw :boon-builder-5e-page
    :route-seg "boon-builder"
    :local-storage-key "boon"
    ;; :homebrew-builder? — wired entirely by the events.cljs loop (no per-type code).
    :homebrew-builder? true
    :default {}}
   {:id :draconic-ancestry
    :type-name "Draconic Ancestry"
    :builder-item :orcpub.dnd.e5.races/draconic-ancestry-builder-item
    :spec :orcpub.dnd.e5.races/homebrew-draconic-ancestry
    :plugin-key :orcpub.dnd.e5/draconic-ancestries
    :route-kw :draconic-ancestry-builder-5e-page
    :route-seg "draconic-ancestry-builder"
    :local-storage-key "draconic-ancestry"
    :homebrew-builder? true
    :default {}}
   {:id :selection
    :type-name "Selection"
    :builder-item :orcpub.dnd.e5.selections/builder-item
    :spec :orcpub.dnd.e5.selections/homebrew-selection
    :plugin-key :orcpub.dnd.e5/selections
    :route-kw :selection-builder-5e-page
    :route-seg "selection-builder"
    :local-storage-key "selection"}
   {:id :feat
    :type-name "Feat"
    :builder-item :orcpub.dnd.e5.feats/builder-item
    :spec :orcpub.dnd.e5.feats/homebrew-feat
    :plugin-key :orcpub.dnd.e5/feats
    :route-kw :feat-builder-5e-page
    :route-seg "feat-builder"
    :local-storage-key "feat"}
   {:id :race
    :type-name "Race"
    :builder-item :orcpub.dnd.e5.races/builder-item
    :spec :orcpub.dnd.e5.races/homebrew-race
    :plugin-key :orcpub.dnd.e5/races
    :route-kw :race-builder-5e-page
    :route-seg "race-builder"
    :local-storage-key "race"}
   {:id :subrace
    :type-name "Subrace"
    :builder-item :orcpub.dnd.e5.races/subrace-builder-item
    :spec :orcpub.dnd.e5.races/homebrew-subrace
    :plugin-key :orcpub.dnd.e5/subraces
    :route-kw :subrace-builder-5e-page
    :route-seg "subrace-builder"
    :local-storage-key "subrace"}
   {:id :subclass
    :type-name "Subclass"
    :builder-item :orcpub.dnd.e5.classes/subclass-builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-subclass
    :plugin-key :orcpub.dnd.e5/subclasses
    :route-kw :subclass-builder-5e-page
    :route-seg "subclass-builder"
    :local-storage-key "subclass"}
   {:id :class
    :type-name "Class"
    :builder-item :orcpub.dnd.e5.classes/builder-item
    :spec :orcpub.dnd.e5.classes/homebrew-class
    :plugin-key :orcpub.dnd.e5/classes
    :route-kw :class-builder-5e-page
    :route-seg "class-builder"
    :local-storage-key "class"}])

(def by-id
  "Registry indexed by :id."
  (into {} (map (juxt :id identity)) content-types))
