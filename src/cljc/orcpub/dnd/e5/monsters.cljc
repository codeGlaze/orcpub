(ns orcpub.dnd.e5.monsters
  "Monster specs, display helpers, and derived lookup maps.
   SRD stat blocks live in monsters-data."
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.dnd.e5.monsters-data :as monsters-data]
            [orcpub.common :as common :refer [name-to-kw]]
            [clojure.string :as s]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::die nat-int?)
(spec/def ::die-count nat-int?)
(spec/def ::modifier number?)
(spec/def ::hit-points (spec/keys :req-un [::die ::die-count]
                                  :opt-un [::modifier]))
(spec/def ::homebrew-monster (spec/keys :req-un [::name ::key ::option-pack ::hit-points]))

(defn monster-subheader
  ([size type subtypes alignment]
   (str (when size (common/safe-capitalize-kw size))
        " "
        (common/kw-to-name type)
        (when (seq subtypes)
          (str " (" (s/join ", " (map common/kw-to-name subtypes)) ")"))
        ", "
        alignment))
  ([{:keys [size type subtypes alignment]}]
   (monster-subheader size type subtypes alignment)))


(def monster-types
  [:aberration :beast :celestial :construct :dragon :elemental :fey :fiend :giant :humanoid :monstrosity :ooze :plant :swarm-of-tiny-beasts :undead])

(def monster-size-order
  [:tiny :small :medium :large :huge :gargantuan])

(def monster-sizes
  {:huge "Huge"
   :medium "Medium"
   :gargantuan "Gargantuan"
   :tiny "Tiny"
   :large "Large"
   :small "Small"})

(def challenge-ratings {0 10, (/ 1 8) 25, (/ 1 4) 50, (/ 1 2) 100, 1 200, 2 450, 3 700, 4 1100, 5 1800, 6 2300, 7 2900, 8 3900, 9 5000, 10 5900, 11 7200, 12 8400, 13 10000, 14 11500, 15 13000, 16 15000, 17 18000, 18 20000, 19 22000, 20 25000, 21 33000, 22 41000, 23 50000, 24 62000, 25 75000, 26 90000, 27 105000, 28 120000, 29 135000, 30 155000})


;; Derived lookups — keyed versions of the raw SRD data
(def monsters (map (fn [m] (assoc m :key (name-to-kw (:name m)))) monsters-data/monsters-raw))
(def monster-map (reduce (fn [mp m] (assoc mp (:key m) m))
                         {}
                         monsters))