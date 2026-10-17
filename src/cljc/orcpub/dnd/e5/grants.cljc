(ns orcpub.dnd.e5.grants
  "Spec for `:grants`, untrusted input from traded `.orcbrew` files. Shapes (options.cljc):
   {:pool p :key k} FIXED; {:pool p :count n :filter #{k …} :min m :order o} CHOICE, :count
   defaulting to 1, the rest optional. `:key` with `:count` is rejected (compilers prefer `:key`).
   GOTCHA: an unregistered `:pool` is valid here: unknown pools drop out at compile time so a pack
   from a newer build still loads. Validates SHAPE, never membership. See pool-grant-map.md."
  (:require #?(:clj [clojure.spec.alpha :as spec]
               :cljs [cljs.spec.alpha :as spec])))

(spec/def ::pool keyword?)
(spec/def ::key keyword?)
(spec/def ::count pos-int?)
(spec/def ::filter (spec/coll-of keyword? :kind set? :min-count 1))
(spec/def ::min nat-int?)
(spec/def ::order int?)

(spec/def ::fixed-grant
  (spec/and (spec/keys :req-un [::pool ::key])
            #(not (contains? % :count))))

(spec/def ::choice-grant
  (spec/and (spec/keys :req-un [::pool] :opt-un [::count ::filter ::min ::order])
            #(not (contains? % :key))
            ;; :min is the fewest of :count: "choose up to 2" is {:count 2 :min 0}.
            #(<= (get % :min 0) (get % :count 1))))

(spec/def ::grant
  ;; :selection-key and :prereq-fn are for built-in callers only, so a file may not carry them.
  (spec/and (spec/or :fixed ::fixed-grant :choice ::choice-grant)
            (fn [[_ g]] (not-any? #(contains? g %) [:selection-key :prereq-fn]))))

(spec/def ::grants
  (spec/coll-of ::grant :kind vector?))
