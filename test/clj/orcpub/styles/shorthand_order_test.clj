(ns orcpub.styles.shorthand-order-test
  "No rule in the compiled stylesheet sets a longhand (`mask-composite`) before its own shorthand
   (`mask`): the shorthand resets the longhand, so the longhand is lost. Garden writes a declaration
   map of more than 8 keys in hash order, so this can happen to any big style map.
   docs/kb/garden-declaration-order.md"
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as s]
            [garden.core :as garden]
            [orcpub.styles.core :as styles]))

(def ^:private css (delay (garden/css {:pretty-print? false} styles/app)))

(def ^:private shorthands
  "Each shorthand and what it resets beyond its own `name-*` longhands."
  {"margin" #{} "padding" #{} "background" #{} "mask" #{} "-webkit-mask" #{} "flex" #{}
   "border" #{} "border-top" #{} "border-right" #{} "border-bottom" #{} "border-left" #{}
   "border-color" #{} "border-width" #{} "border-style" #{} "outline" #{} "overflow" #{}
   "transition" #{} "animation" #{} "text-decoration" #{} "list-style" #{} "columns" #{}
   "font" #{"line-height"} "inset" #{"top" "right" "bottom" "left"}})

(def ^:private not-reset
  "`name-*` properties a shorthand leaves alone, so they may come before it."
  {"border" #{"border-collapse" "border-spacing"}
   "font" #{"font-feature-settings" "font-variation-settings" "font-display"}
   "flex" #{"flex-direction" "flex-wrap" "flex-flow"}
   "outline" #{"outline-offset"}
   "overflow" #{"overflow-wrap" "overflow-anchor" "overflow-clip-margin"}})

(defn- resets?
  "Whether shorthand `sh` resets property `prop`. No border shorthand resets a corner radius."
  [sh prop]
  (and (not= sh prop)
       (not (contains? (not-reset sh) prop))
       (not (and (s/starts-with? sh "border") (s/includes? prop "radius")))
       (or (s/starts-with? prop (str sh "-"))
           (contains? (shorthands sh) prop))))

(defn- props
  "The property names in a rule body, in the order written."
  [body]
  (map second (re-seq #"(?:^|;)\s*(-?[a-z][a-z-]*)\s*:" body)))

(defn order-faults
  "Each rule in `css-text` that sets a property before a shorthand that resets it, as
   \"selector: prop before shorthand\". Returns a seq of strings, empty when none."
  [css-text]
  (for [[_ sel body] (re-seq #"(?:^|[{}])([^{}]+)\{([^{}]*)(?=\})" css-text)
        :let [ps (vec (props body))]
        [i p] (map-indexed vector ps)
        sh (subvec ps (inc i))
        :when (and (contains? shorthands sh) (resets? sh p))]
    (str (s/trim sel) ": " p " before " sh)))

(deftest the-check-sees-a-longhand-before-its-shorthand
  (is (= [".x: mask-composite before mask"]
         (order-faults ".x{mask-composite:exclude;mask:linear-gradient(#000 0 0)}")))
  (is (empty? (order-faults ".x{mask:none;mask-composite:exclude;border-radius:4px;border:0}"))
      "shorthand first, or a property the shorthand leaves alone, passes")
  (is (empty? (order-faults ".x{border-top-left-radius:4px;border-top:0;outline-offset:2px;outline:0}"))
      "a side's border leaves its corner radius alone, and outline its offset")
  (is (= [".x: border-top-width before border-top"] (order-faults ".x{border-top-width:2px;border-top:0}")))
  (is (= [".x: mask-composite before mask"] (order-faults "@media screen{.x{mask-composite:exclude;mask:none}}"))
      "the first rule inside a media block is checked too"))

(deftest no-longhand-is-reset-by-a-later-shorthand
  (is (= [] (vec (order-faults @css)))))
