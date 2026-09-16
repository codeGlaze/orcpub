(ns orcpub.dnd.e5.languages
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::homebrew-language (spec/keys :req-un [::name ::key ::option-pack]))

;; The 16 built-in languages. Lived in spell_subs.cljs (a cljs subs namespace) until the grant-pool
;; registry needed to read them from cljc. Data only — the pool that wraps them, and the option
;; constructor, live in grant_pools.cljc / options.cljc.
(def languages
[{:name "Common"
    :key :common}
   {:name "Dwarvish"
    :key :dwarvish}
   {:name "Elvish"
    :key :elvish}
   {:name "Giant"
    :key :giant}
   {:name "Gnomish"
    :key :gnomish}
   {:name "Goblin"
    :key :goblin}
   {:name "Halfling"
    :key :halfling}
   {:name "Orc"
    :key :orc}
   {:name "Abyssal"
    :key :abyssal}
   {:name "Celestial"
    :key :celestial}
   {:name "Draconic"
    :key :draconic}
   {:name "Deep Speech"
    :key :deep-speech}
   {:name "Infernal"
    :key :infernal}
   {:name "Primordial"
    :key :primordial}
   {:name "Sylvan"
    :key :sylvan}
   {:name "Undercommon"
    :key :undercommon}])
