(ns orcpub.dnd.e5.srd-conditions
  "Lookups over the SRD conditions file, `resources/public/srd/2014/conditions.edn`, as parsed
   from EDN. Shared by the conditions pages, the Orcacle and the monster stat block. The file is
   generated: see `resources/srd/README.md`."
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(def data-path
  "URL of the 2014 conditions file, served from `resources/public`."
  "/srd/2014/conditions.edn")

(def condition-keys
  "The 15 SRD conditions. The same set in SRD 5.1 and 5.2.1, so the stat block can link a
   condition before the file has loaded."
  #{:blinded :charmed :deafened :exhaustion :frightened :grappled :incapacitated :invisible
    :paralyzed :petrified :poisoned :prone :restrained :stunned :unconscious})

(defn by-key
  "Indexes the conditions file by condition key.

   Args: `data`, the parsed file (a map with `:conditions`), or nil.
   Returns: a map of keyword to condition map; empty when `data` is nil."
  [data]
  (into {} (map (juxt :key identity)) (:conditions data)))

(defn find-condition
  "The condition named exactly `text`, ignoring case and surrounding space.

   Args: `data`, the parsed file or nil; `text`, a search string or nil.
   Returns: the condition map, or nil."
  [data text]
  (when-not (s/blank? text)
    (get (by-key data) (keyword (common/ascii-lower-case (s/trim text))))))

(defn matching
  "Conditions whose names contain `text`, ignoring case, in file order.

   Args: `data`, the parsed file or nil; `text`, a search string.
   Returns: a seq of condition maps; empty for text under three characters, as the Orcacle's
   other lists are."
  [data text]
  (let [t (common/ascii-lower-case (s/trim (or text "")))]
    (when (>= (count t) 3)
      (filter #(s/includes? (common/ascii-lower-case (:name %)) t) (:conditions data)))))

(defn immunity-parts
  "Splits a monster's condition-immunities text into its named conditions.

   Args: `text`, e.g. \"charmed, exhaustion, poisoned\" (free text in homebrew); `known`, a set
   of condition keys, usually `condition-keys`.
   Returns: a vector of {:text \"charmed\" :key :charmed}, `:key` nil where the part names no
   known condition, so it shows as plain text."
  [text known]
  (into []
        (comp (map s/trim)
              (remove s/blank?)
              (map (fn [part]
                     (let [k (keyword (common/ascii-lower-case part))]
                       {:text part :key (when (known k) k)}))))
        (s/split (or text "") #",")))
