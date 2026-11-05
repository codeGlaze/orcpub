(ns orcpub.dnd.e5.srd-conditions
  "Lookups over the SRD conditions file, `resources/public/srd/2014/conditions.edn`, as parsed
   from EDN. Shared by the conditions pages, the Orcacle and the monster stat block. The file is
   generated: see `resources/srd/README.md`."
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(def data-path
  "URL of the 2014 conditions file, served from `resources/public`."
  "/srd/2014/conditions.edn")

(def condition-keys
  "The 15 SRD conditions. The same set in SRD 5.1 and 5.2.1, so the stat block can link a
   condition before the file has loaded."
  #{:blinded :charmed :deafened :exhaustion :frightened :grappled :incapacitated :invisible
    :paralyzed :petrified :poisoned :prone :restrained :stunned :unconscious})

(defn by-key
  "Indexes the conditions file by condition key.

   Args: `data`, the parsed file (a map with `:conditions`), or nil.
   Returns: a map of keyword to condition map; empty when `data` is nil."
  [data]
  (into {} (map (juxt :key identity)) (:conditions data)))

(defn find-condition
  "The condition named exactly `text`, ignoring case and surrounding space.

   Args: `data`, the parsed file or nil; `text`, a search string or nil.
   Returns: the condition map, or nil."
  [data text]
  (when-not (s/blank? text)
    (get (by-key data) (keyword (common/ascii-lower-case (s/trim text))))))

(defn matching
  "Conditions whose names contain `text`, ignoring case, in file order.

   Args: `data`, the parsed file or nil; `text`, a search string.
   Returns: a seq of condition maps; empty for text under three characters, as the Orcacle's
   other lists are."
  [data text]
  (let [t (common/ascii-lower-case (s/trim (or text "")))]
    (when (>= (count t) 3)
      (filter #(s/includes? (common/ascii-lower-case (:name %)) t) (:conditions data)))))

(defn immunity-parts
  "Splits a monster's condition-immunities text into its named conditions.

   Args: `text`, e.g. \"charmed, exhaustion, poisoned\" (free text in homebrew); `known`, a set
   of condition keys, usually `condition-keys`.
   Returns: a vector of {:text \"charmed\" :key :charmed}, `:key` nil where the part names no
   known condition, so it shows as plain text."
  [text known]
  (into []
        (comp (map s/trim)
              (remove s/blank?)
              (map (fn [part]
                     (let [k (keyword (common/ascii-lower-case part))]
                       {:text part :key (when (known k) k)}))))
        (s/split (or text "") #",")))

(def ^:private condition-alternation
  "blinded|charmed|deafened|exhaustion|frightened|grappled|incapacitated|invisible|paralyzed|petrified|poisoned|prone|restrained|stunned|unconscious")

(def ^:private condition-pattern
  "Where text names a condition rather than using the word plainly: \"the poisoned condition\",
   \"a frightened creature\", \"is blinded\", \"knocked prone\", \"one level of exhaustion\". The
   same patterns scripts/srd/srd_format.py uses when it writes the SRD files. Matched against
   lower-case text."
  (str "\\b(?:(" condition-alternation ") (?:condition|creature|target|character|wielder)"
       "|(?:is|are|be|been|becomes|become|being|remains|remain|knocked|falls|fall|fell|lands|land|drop|while"
       "|isn't|aren't|is not|are not|wasn't)(?: also)? (" condition-alternation ")"
       "|of (exhaustion)|(exhaustion) level)\\b"))

(def ^:private follow-on-pattern
  "A further condition continuing a named one: \"is blinded and deafened\", \"charmed or frightened\"."
  (re-pattern (str "^(,? (?:and|or) |, )(" condition-alternation ")\\b")))

(defn- with-follow-ons
  "`matches` plus every condition that continues one of them in `lower`, in order."
  [lower matches]
  (vec (mapcat (fn [[_ at word :as m]]
                 (loop [end (+ at (count word)) out [m]]
                   (if-let [[_ sep w] (re-find follow-on-pattern (subs lower end))]
                     (let [w-at (+ end (count sep))]
                       (recur (+ w-at (count w)) (conj out [end w-at w])))
                     out)))
               matches)))

(defn- condition-matches
  "Each place `lower` names a condition, as [start word-start word] triples in order."
  [lower]
  #?(:clj (let [m (re-matcher (re-pattern condition-pattern) lower)]
            (loop [out []]
              (if (.find m)
                (let [g (first (filter #(.group m (int %)) [1 2 3 4]))]
                  (recur (conj out [(.start m) (.start m (int g)) (.group m (int g))])))
                out)))
     :cljs (let [re (js/RegExp. condition-pattern "g")]
             (loop [out []]
               (if-let [m (.exec re lower)]
                 (let [g (first (filter #(aget m %) [1 2 3 4]))
                       word (aget m g)
                       offset (.lastIndexOf (aget m 0) word)]
                   (recur (conj out [(.-index m) (+ (.-index m) offset) word])))
                 out)))))

(defn link-conditions
  "Splits `text` into SRD inlines: plain strings, and condition links where the text names a
   condition (see condition-pattern), in the form the SRD files use.

   Args: `text`, a string, or nil.
   Returns: a vector of strings and [:a {:kind :condition :key k} word] links; [text] when it
   names none."
  [text]
  (let [text (or text "")
        lower (common/ascii-lower-case text)]
    (loop [[[_ at word] & more] (with-follow-ons lower (condition-matches lower))
           pos 0
           out []]
      (cond
        (not at) (cond-> out (< pos (count text)) (conj (subs text pos)))
        ;; already linked: matched both on its own and as a follow-on ("blinded, poisoned creature")
        (< at pos) (recur more pos out)
        :else
        (let [end (+ at (count word))]
          (recur more end
                 (cond-> out
                   (< pos at) (conj (subs text pos at))
                   true (conj [:a {:kind :condition :key (keyword word)} (subs text at end)]))))))))

