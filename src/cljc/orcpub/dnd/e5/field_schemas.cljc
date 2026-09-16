(ns orcpub.dnd.e5.field-schemas
  "Registry: plugin content-key -> the type's declarative field schema. The single place that
   knows which content types have a field schema, so import/export verification can stay synced
   with them (import_validation reads this). Add a new schema-based type here.

   NOT a leaf (requires the schema-holding domain nss) — that's fine; its consumers
   (import_validation, etc.) are not leaves either."
  (:require [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.races :as races]
            [orcpub.dnd.e5.classes :as classes]))

(def by-plugin-key
  {:orcpub.dnd.e5/draconic-ancestries races/draconic-ancestry-fields
   :orcpub.dnd.e5/fighting-styles      classes/fighting-style-fields})
