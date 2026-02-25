(ns orcpub.dnd.e5.classes
  "Class/subclass specs, class-level helper, and re-exported SRD class
   option builders. Class data lives in classes-data."
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]
            [orcpub.dnd.e5.classes-data :as classes-data]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::homebrew-class (spec/keys :req-un [::name ::key ::option-pack]))

(spec/def ::class (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::homebrew-subclass (spec/keys :req-un [::name ::key ::class ::option-pack]))

(spec/def ::homebrew-invocation (spec/keys :req-un [::name ::key ::option-pack]))

(spec/def ::homebrew-boon (spec/keys :req-un [::name ::key ::option-pack]))

;; Re-exported utility (used by subs/events)
(def class-level classes-data/class-level)

;; ============================================================================
;; Re-exported class option builders (from classes-data)
;; ============================================================================

(def barbarian-option classes-data/barbarian-option)
(def bard-option classes-data/bard-option)
(def cleric-option classes-data/cleric-option)
(def druid-option classes-data/druid-option)
(def fighter-option classes-data/fighter-option)
(def monk-option classes-data/monk-option)
(def paladin-option classes-data/paladin-option)
(def ranger-option classes-data/ranger-option)
(def rogue-option classes-data/rogue-option)
(def sorcerer-option classes-data/sorcerer-option)
(def wizard-option classes-data/wizard-option)
(def warlock-option classes-data/warlock-option)

;; Spellcasting schedule lookups (used by views/builders/classes.cljs)
(def third-caster-spells-known-schedule classes-data/third-caster-spells-known-schedule)
(def half-caster-spells-known-schedule classes-data/half-caster-spells-known-schedule)
(def full-caster-spells-known-schedule classes-data/full-caster-spells-known-schedule)
