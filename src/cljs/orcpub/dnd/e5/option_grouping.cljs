(ns orcpub.dnd.e5.option-grouping
  "Pure helpers for the growable multi-select menus: collapse repeated
   boilerplate wording (e.g. every Saving-Throw-Advantage option begins
   \"You have advantage on saving throws against being …\") and group options A–Z.

   No re-frame / Reagent / DOM dependencies — these are plain functions over the
   option *labels* (strings), so they are trivially unit-testable and can be
   called from `default-selection-section-body` in character_builder.cljs.

   Everything here is sort-agnostic and length-agnostic: nothing assumes a fixed
   or known set of options, which is the whole point — the menus grow over time."
  (:require [clojure.string :as str]))

(defn dominant-prefix
  "The leading wording shared by a MAJORITY of `labels`, returned as a string
   INCLUDING its trailing space, or nil when no boilerplate dominates.

   Unlike a strict longest-common-prefix, this tolerates off-pattern additions:
   a single odd label neither triggers nor blocks a collapse. `min-words`
   (default 4) keeps short coincidental overlaps from collapsing; `ratio`
   (default 0.5) is the fraction of options that must share the prefix."
  ([labels] (dominant-prefix labels nil))
  ([labels {:keys [min-words ratio] :or {min-words 4 ratio 0.5}}]
   (let [n (count labels)]
     (when (>= n 3)
       (let [tokens    (mapv #(str/split % #" ") labels)
             max-w     (apply max (map count tokens))
             threshold (max 2 (int (js/Math.ceil (* n ratio))))]
         (loop [w min-words, best nil]
           (if (> w max-w)
             best
             (let [counts (reduce
                           (fn [m t]
                             ;; require at least one word AFTER the prefix (the keyword)
                             (if (>= (count t) (inc w))
                               (update m (str/join " " (take w t)) (fnil inc 0))
                               m))
                           {} tokens)
                   [best-key best-count]
                   (reduce-kv (fn [[bk bc] k c] (if (> c bc) [k c] [bk bc]))
                              [nil 0] counts)]
               (if (and best-key (>= best-count threshold))
                 (recur (inc w) (str best-key " "))   ; try to extend the prefix
                 best)))))))))                          ; longer can only shrink count

(defn classify
  "Annotate each label relative to `prefix` (nil = no collapse). Returns a vector
   of maps:
     :label          the original full label
     :display        keyword-only text when it conforms, else the full label
     :conform?       starts with the dominant prefix
     :non-standard?  a prefix exists but THIS label doesn't follow it
     :diverge-at     char index where a non-standard label stops matching the
                     prefix — render [0,diverge-at) muted and the tail highlighted."
  [labels prefix]
  (mapv
   (fn [label]
     (let [conform?      (boolean (and prefix (str/starts-with? label prefix)))
           non-standard? (boolean (and prefix (not conform?)))]
       {:label         label
        :display       (if conform? (subs label (count prefix)) label)
        :conform?      conform?
        :non-standard? non-standard?
        :diverge-at    (if non-standard?
                         (let [pl (count prefix) ll (count label)]
                           (loop [i 0]
                             (if (and (< i pl) (< i ll)
                                      (= (.charAt label i) (.charAt prefix i)))
                               (recur (inc i))
                               i)))
                         0)}))
   labels))

(defn- first-letter [display]
  (let [ch (str/upper-case (str (first display)))]
    (if (re-matches #"[A-Z]" ch) ch "#")))

(defn group-by-letter
  "Bucket annotated items by the first letter of their :display text (so collapsed
   keywords group by their own initial, not the shared prefix). Non-alphabetic
   leads fall into '#'. Returns [{:letter :items}] in alphabetical order."
  [items]
  (->> items
       (group-by (comp first-letter :display))
       (sort-by key)
       (mapv (fn [[letter its]] {:letter letter :items its}))))

(defn present-letters
  "Distinct first-letters present across `items`, alphabetically — for the A–Z
   jump bar. Always feed it the UNFILTERED set so the bar is stable."
  [items]
  (->> items (map (comp first-letter :display)) distinct sort vec))
