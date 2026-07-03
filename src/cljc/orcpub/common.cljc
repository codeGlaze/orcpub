(ns orcpub.common
  (:require [clojure.string :as s]
            #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])))

(def dot-char "•")

(defn- name-to-kw-aux [name ns]
  (when (string? name)
    (as-> name $
        (s/lower-case $)
        (s/replace $ #"'" "")
        (s/replace $ #"\W" "-")
        (s/replace $ #"\-+" "-")
        (keyword ns $))))

(def memoized-name-to-kw (memoize name-to-kw-aux))

(defn name-to-kw [name & [ns]]
  (memoized-name-to-kw name ns))

(defn kw-to-name [kw & [capitalize?]]
  (when (keyword? kw)
    (as-> kw $
      (name $)
      (s/split $ #"\-")
      (if capitalize? (map s/capitalize $) $)
      (s/join " " $))))

(defn map-by [by values]
  (zipmap (map by values) values))

(defn map-by-key [values]
  (map-by :key values))

(defn map-by-id [values]
  (map-by :db/id values))

;; dead — zero callers (only ref is in #_ discarded views.cljs block)
#_(defmacro ptime [message body]
  `(do (prn ~message)
       (time ~body)))

(defn bonus-str [val]
  (str (when (pos? val) "+") val))

(defn mod-str [val]
  (cond (pos? val) (str "+" val)
        (neg? val) (str "-" (int (Math/abs val)))
        :else (str "+" val)))

(defn map-vals [val-fn m]
  (reduce-kv
   (fn [m2 k v]
     (assoc m2 k (val-fn k v)))
   {}
   m))

(defn list-print [list & [preceding-last]]
  (let [preceding-last (or preceding-last "and")]
    (case (count list)
      0 ""
      1 (str (first list))
      2 (s/join (str " " preceding-last " ") list)
      (str
       (s/join ", " (butlast list))
       ", " preceding-last " "
       (last list)))))

(defn round-up [num]
  (int (Math/ceil (double num))))

(defn warn [message]
  #?(:cljs (js/console.warn message))
  #?(:clj (prn "WARNING: " message)))

(defn safe-name [kw]
  (if (keyword? kw)
    (name kw)
    (warn (str "non-keyword value passed to safe-name: " kw))))

(defn safe-capitalize [s]
  (when (string? s) (s/capitalize s)))

(defn safe-capitalize-kw [kw]
  (some-> kw
          name
          safe-capitalize))

(defn kw-base
  "Extract the base part of a keyword (before first dash).
   E.g., :artificer-kibbles-tasty -> \"artificer\""
  [kw]
  (when (keyword? kw)
    (first (s/split (name kw) #"-"))))

(defn traverse-nested
  "HOF for traversing nested option structures (vector/map/nil pattern).
   Calls (f item path) for each nested item, returns concatenated results."
  [f coll path]
  (mapcat
   (fn [[k v]]
     (cond
       (vector? v)
       (apply concat (map-indexed (fn [idx item] (f item (conj path k idx))) v))
       (map? v)
       (f v (conj path k))
       :else nil))
   coll))

(defn sentensize [desc]
  (when desc
    (str
     (s/upper-case (subs desc 0 1))
     (subs desc 1)
     (when (not (s/ends-with? desc "."))
       "."))))

(def add-keys-xform
  (map
   #(assoc % :key (name-to-kw (:name %)))))

(defn add-keys [vals]
  (into [] add-keys-xform vals))

(defn remove-first [f v]
  (concat
   (take-while (complement f) v)
   (rest (drop-while (complement f) v))))

(defn add-namespaces-to-keys [ns-str item]
  (into {}
        (map
         (fn [x]
           (let [[k v] x]
             [(if (simple-keyword? k)
                (keyword ns-str (name k))
                k)
              v]))
         item)))

(spec/fdef add-namespaces-to-keys
           :args (spec/cat :ns-str string? :item (spec/map-of keyword? any?))
           :ret (spec/map-of qualified-keyword? any?)
           :fn #(and (= (count (-> % :args :item))
                        (count (-> % :ret)))
                     (= (set (-> % :args :item keys))
                        (set (->> % :ret keys (map (fn [k] (keyword (name k)))))))))

(defn ordinal [i]
  (case i
    1 "1st"
    2 "2nd"
    3 "3rd"
    (str i "th")))

(defn starts-with-letter? [nm]
  (re-matches #"^[a-zA-Z].*" nm))

(defn keyword-starts-with-letter? [kw]
  (and (keyword? kw)
       (-> kw name starts-with-letter?)))

(defn remove-at-index [v index]
  (vec
   (keep-indexed
    (fn [i item]
      (when (not= i index)
        item))
    v)))

(def rounds-per-minute 10)
(def minutes-per-hour 60)
;; dead — redefined in views.cljs (also dead there), never referenced from common
#_(def hours-per-day 24)

(def rounds-per-hour (* minutes-per-hour rounds-per-minute))

;; dead — zero callers
#_(defn rounds-to-hours [rounds]
  (int (/ rounds rounds-per-hour)))

;; dead — zero callers
#_(defn rounds-to-minutes [rounds]
  (int (/ (rem rounds rounds-per-hour) rounds-per-minute)))

(def filter-true-xform
  (filter (fn [[k v]] v)))

(defn true-keys [m]
  (keys (sequence filter-true-xform m)))

(defn dissoc-in [m path]
  (update-in m
             (butlast path)
             (fn [x]
               (dissoc x (last path)))))

(defn print-bonus-map [m]
  (s/join ", "
          (map
           (fn [[k v]] (str (safe-capitalize-kw k) " " (bonus-str v)))
           m)))

;; Crash-safe case fold for sort/compare keys: coerces a nil/non-string to "" so
;; core s/lower-case can't crash on it. Never throws — it folds arbitrary keys
;; (e.g. :level), so a non-string isn't a bug here; that judgment is the caller's
;; (see feature-name, where the dev-throw lives).
(defn lower-case [x]
  (s/lower-case (str x)))

;; Case-insensitive `sort-by`, built on the safe fold above so a nil/non-string key
;; sorts as "" rather than throwing.
(defn aloof-sort-by [sorter coll]
  (sort-by (comp lower-case sorter) coll))

;; Display name with an obvious placeholder, not a blank: any unusable name ->
;; "[Unnamed feature]" (shown and sorted by), never a blank or a plausible-looking
;; coercion. Here a string IS expected, so a wrong-typed name is a real bug — dev
;; throws to surface it; prod also shows the placeholder rather than hiding it as
;; e.g. "42". (The generic lower-case fold can't tell, so it never throws.)
(defn feature-name [{:keys [name]}]
  (cond
    (and (string? name) (not (s/blank? name))) name
    (or (nil? name) (string? name)) "[Unnamed feature]"   ; nil or blank string
    :else #?(:cljs (if ^boolean goog/DEBUG
                     (throw (ex-info "feature :name is not a string" {:name name}))
                     "[Unnamed feature]")
             :clj "[Unnamed feature]")))

(defn ->kebab-case [s]
  (-> s
      ;; Insert hyphen before each capital letter, but not at the start.
      (s/replace #"([A-Z])" "-$1")
      .toLowerCase))
