(ns orcpub.dnd.e5.character.equipment
  (:require [clojure.spec.alpha :as spec]
            [orcpub.common :as common]))

(spec/def ::name string?)
(spec/def ::quantity nat-int?)
(spec/def ::equipped? boolean?)
(spec/def ::background-starting-equipment? boolean?)
(spec/def ::class-starting-equipment? boolean?)
;; Per item in a stack, one record each, found by ::charge-index (its position in the stack): charges
;; spent, and a maximum rolled when added (an item whose charges start as dice). plan-charges-and-casting.md
(spec/def ::charge-index nat-int?)
(spec/def ::charges-used nat-int?)
(spec/def ::charges-max nat-int?)
(spec/def ::charges (spec/coll-of (spec/keys :req [::charge-index] :opt [::charges-used ::charges-max]) :kind vector?))

(def equipment-item-req [::name ::quantity ::equipped?])
(def equipment-item-opt [::background-starting-equipment?
                         ::class-starting-equipment?])

(spec/def ::equipment-item (spec/keys :req [::name ::quantity ::equipped?]
                                      :opt [::background-starting-equipment?
                                            ::class-starting-equipment?]))

(spec/def ::equipment-items (spec/coll-of ::equipment-item))

(spec/def ::equipment-item-un (spec/keys :req-un [::name ::quantity ::equipped?]
                                         :opt-un [::background-starting-equipment?
                                                  ::class-starting-equipment?]))

(spec/def ::equipment-items-un (spec/coll-of ::equipment-item-un))

(defn to-namespaced [item]
  (common/add-namespaces-to-keys "orcpub.dnd.e5.character.equipment" item))

(spec/fdef to-namespaced
           :args ::equipment-item-un
           :ret ::equipment-item)
