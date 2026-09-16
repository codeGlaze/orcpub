(ns orcpub.dnd.e5.grants
  "The spec for `:grants` — the one authored key that had none.

   `:grants` arrives in `.orcbrew` files users trade with each other, so it is untrusted input at
   a trust boundary. Every other authored shape is specced (`content_specs.cljc` maps each content
   type to one); this was the exception, so a malformed grant was discovered as a nil somewhere
   downstream instead of at import.

   Pure leaf — spec only, no code, no requires beyond spec itself. Mirrors `requirements.cljc`.

   THE SHAPE, as the two compilers actually read it (`options.cljc`, `grant-selection` and
   `compile-grants`):

     {:pool :skills :key :athletics}              FIXED  — the entry's modifiers, no pick
     {:pool :skills :count 2}                     CHOICE — pick 2 from the whole pool
     {:pool :skills :count 2 :filter #{:athletics :stealth}}   CHOICE, narrowed
     {:pool :skills}                              CHOICE of 1 — :count defaults to 1

   `:key` wins: `grant-selection` returns nil when `:key` is present, and `compile-grants` takes
   the fixed branch. So `:key` together with `:count` is contradictory and this spec rejects it,
   even though today's compiler would quietly prefer `:key`.

   GOTCHA: an unregistered `:pool` is NOT a spec error. Unknown pools drop out at compile time on
   purpose — that is the forward-compatibility seam that lets a pack from a newer build load with
   its unknown grants ignored rather than rejected. This spec validates SHAPE, never membership.

   Reference: docs/kb/pool-grant-map.md, docs/kb/plan-hidden-pick-fix-and-grant-fields.md."
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
