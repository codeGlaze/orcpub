(ns orcpub.dnd.e5.fighting-styles
  "Fighting Style specifications and data structures for plugin support"
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.common :as common]))

;; Basic fighting style properties
(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::description string?)
(spec/def ::page pos-int?)
(spec/def ::source keyword?)
(spec/def ::option-pack string?)

;; Ability type - trait (default), reaction, or bonus-action
(spec/def ::ability-type #{:trait :reaction :bonus-action})

;; Props - mechanical effects
(spec/def ::ranged-attack-bonus int?)
(spec/def ::melee-attack-bonus int?)
(spec/def ::armored-ac-bonus int?)
(spec/def ::unarmored-ac-bonus int?)
(spec/def ::critical-range int?)
(spec/def ::blindsight int?)
(spec/def ::initiative int?)
(spec/def ::speed int?)

;; Complex conditional props (for things like Dueling)
(spec/def ::weapon-conditions
  (spec/keys :opt-un [::one-handed ::melee ::two-handed ::ranged]))

(spec/def ::off-hand-conditions
  (spec/keys :opt-un [::no-weapon]))

(spec/def ::conditional-damage-bonus
  (spec/keys :req-un [::value]
             :opt-un [::weapon-conditions ::off-hand-conditions]))

;; Props map - all possible mechanical properties
(spec/def ::props
  (spec/keys :opt-un [::ranged-attack-bonus
                      ::melee-attack-bonus
                      ::armored-ac-bonus
                      ::unarmored-ac-bonus
                      ::critical-range
                      ::blindsight
                      ::initiative
                      ::speed
                      ::conditional-damage-bonus]))

;; Complete fighting style definition
(spec/def ::fighting-style
  (spec/keys :req-un [::name ::key]
             :opt-un [::description
                      ::page
                      ::source
                      ::option-pack
                      ::ability-type
                      ::props]))

;; For plugin imports
(spec/def ::homebrew-fighting-style ::fighting-style)
