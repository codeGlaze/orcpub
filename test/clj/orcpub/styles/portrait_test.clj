(ns orcpub.styles.portrait-test
  "The portrait compositor's CSS is Garden (orcpub.styles.portrait), compiled into the app's
   stylesheet: portrait.cljs injects no stylesheet, and its inline styles only set CSS variables."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [garden.core :as garden]
            [rewrite-clj.parser :as p]
            [rewrite-clj.node :as n]
            [orcpub.styles.core :as core]))

(def source "src/cljs/orcpub/dnd/e5/portrait.cljs")

(defn- nodes
  "Every node of the parsed file, depth first."
  [root]
  (tree-seq n/inner? n/children root))

(defn- token=
  "Whether `node` is the token written `text`. Compared as text: ::alias/kw has no sexpr here."
  [node text]
  (and (= :token (n/tag node)) (= text (n/string node))))

(defn- style-values
  "The value of every :style key in a map literal in `root`, with its line."
  [root]
  (for [m (nodes root) :when (= :map (n/tag m))
        [k v] (partition 2 (remove n/whitespace-or-comment? (n/children m)))
        :when (token= k ":style")]
    [(:row (meta v)) v]))

(defn- variables-only?
  "Whether a :style value sets nothing but CSS variables: it holds a map literal, and every map
   literal in it has only \"--name\" keys."
  [v]
  (let [maps (filter #(= :map (n/tag %)) (nodes v))]
    (and (seq maps)
         (every? (fn [m] (every? #(s/starts-with? (n/string %) "\"--")
                                 (take-nth 2 (remove n/whitespace-or-comment? (n/children m)))))
                 maps))))

(deftest portrait-views-carry-no-css
  (let [root (p/parse-string-all (slurp source))]
    (testing "no [:style ...] element"
      (is (empty? (for [v (nodes root)
                        :when (= :vector (n/tag v))
                        :let [[head] (remove n/whitespace-or-comment? (n/children v))]
                        :when (and head (token= head ":style"))]
                    (:row (meta v))))))
    (testing "inline styles set only CSS variables"
      (is (empty? (for [[row v] (style-values root) :when (not (variables-only? v))]
                    [row (n/string v)]))))
    (testing "no style written from code"
      (is (not (re-find #"-style\s+-" (slurp source)))))))

(deftest the-app-stylesheet-carries-the-portrait-rules
  (let [css (garden/css {:pretty-print? false} core/app)]
    (doseq [rule [".pl-drawer{" ".pl-root.light-theme .pl-drawer{" ".lk-name{" ".portrait-layer{"
                  ".portrait-layer-tinted{" ".pl-fill{" "@keyframes lk-prism" "@font-face{"
                  "var(--pl-logo)" "@media(prefers-reduced-motion:reduce)"]]
      (is (s/includes? css rule) rule))))
