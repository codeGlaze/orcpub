(ns orcpub.dnd.e5.spells
  "Spell specs and re-exported SRD data.
   Raw spell data lives in spells-data."
  (:require [orcpub.common :as common]
            #?(:cljs [cljs.spec.alpha :as spec])
            #?(:clj [clojure.spec.alpha :as spec])
            [orcpub.dnd.e5.spells-data :as spells-data]
            [clojure.string :as s]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::school string?)
(spec/def ::level (spec/int-in 0 10))
(spec/def ::casting-time string?)
(spec/def ::duration string?)
(spec/def ::range string?)
(spec/def ::source keyword?)
(spec/def ::page nat-int?)
(spec/def ::summary string?)
(spec/def ::description string?)

(spec/def ::verbal boolean?)
(spec/def ::somatic boolean?)
(spec/def ::material boolean?)
(spec/def ::material-component string?)

(spec/def ::components (spec/keys :opt-un [::verbal ::somatic ::material ::material-component]))

(spec/def ::spell (spec/keys :req-un [::name ::key ::school ::level]
                             :opt-un [::casting-time
                                      ::duration
                                      ::range
                                      ::source
                                      ::page
                                      ::summary
                                      ::components
                                      ::description]))

(spec/def ::option-pack string?)
(spec/def ::homebrew (spec/keys :req-un [::option-pack]))
(spec/def ::spell-lists (fn [lists]
                          (let [s (into #{} (vals lists))]
                            (and (s true)
                                 (every? keyword? (keys lists))))))

(spec/def ::has-spell-lists (spec/keys :req-un [::spell-lists]))

(spec/def ::homebrew-spell (spec/and ::spell
                                     ::homebrew
                                     ::has-spell-lists))

;; ============================================================================
;; Re-exported data (from spells-data)
;; ============================================================================

(def schools spells-data/schools)
(def spells spells-data/spells)
(def spell-map spells-data/spell-map)
