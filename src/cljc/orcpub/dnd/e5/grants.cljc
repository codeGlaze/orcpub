(ns orcpub.dnd.e5.grants
  "Spec for `:grants`, untrusted input from traded `.orcbrew` files. Shapes (options.cljc):
   {:pool p :key k} FIXED; {:pool p :count n :filter #{k …}} CHOICE, :count defaulting to 1 and
   :filter optional. `:key` with `:count` is rejected (the compilers silently prefer `:key`).
   GOTCHA: an unregistered `:pool` is valid here: unknown pools drop out at compile time so a pack
   from a newer build still loads. Validates SHAPE, never membership. See pool-grant-map.md."
  (:require #?(:clj [clojure.spec.alpha :as spec]
               :cljs [cljs.spec.alpha :as spec])))

(spec/def ::pool keyword?)
(spec/def ::key keyword?)
(spec/def ::count pos-int?)
(spec/def ::filter (spec/coll-of keyword? :kind set? :min-count 1))

(spec/def ::fixed-grant
  (spec/and (spec/keys :req-un [::pool ::key])
            #(not (contains? % :count))))

(spec/def ::choice-grant
  (spec/and (spec/keys :req-un [::pool] :opt-un [::count ::filter])
            #(not (contains? % :key))))

(spec/def ::grant
  (spec/or :fixed ::fixed-grant :choice ::choice-grant))

(spec/def ::grants
  (spec/coll-of ::grant :kind vector?))
