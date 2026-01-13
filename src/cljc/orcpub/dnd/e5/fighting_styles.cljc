(ns orcpub.dnd.e5.fighting-styles
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [orcpub.dnd.e5.feats :as feats]))

;; Fighting styles share common specs with feats (name, key, option-pack)
;; No need to redefine - just use feats/name, feats/key, feats/option-pack

;; Fighting-style-specific specs
(spec/def ::ability-type #{:trait :reaction :bonus-action})
(spec/def ::page pos-int?)
(spec/def ::source keyword?)
(spec/def ::description string?)

;; Props map - all possible mechanical properties
;; These correspond to cases in options/make-feat-modifiers
(spec/def ::props map?)  ; Flexible for now - specific props validated by make-feat-modifiers

;; Complete homebrew fighting style definition
(spec/def ::homebrew-fighting-style
  (spec/keys :req-un [::feats/name ::feats/key ::feats/option-pack]
             :opt-un [::description
                      ::page
                      ::source
                      ::ability-type
                      ::props]))
