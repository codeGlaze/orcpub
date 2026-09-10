(ns orcpub.common
  (:require [clojure.string :as s]
            #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])))

(def dot-char "•")

(def ^:private bare-colon-re
  ;; Matches EITHER a full "string literal" OR a bare-colon token (the printed
  ;; form of the empty keyword `:`). Scanning left-to-right, a string literal is
  ;; consumed whole, so a `:` inside a string is never seen as a bare colon.
  ;; The lookahead requires the colon to be followed by a delimiter or end, so
  ;; `:foo`, `::x`, `:ns/foo`, and `#:ns{...}` are all left untouched.
  #"\"(?:[^\"\\]|\\.)*\"|:(?=[\s,{}\[\]()\";]|$)")

(defn sanitize-edn-colons
  "SELF-HEAL: return `edn-str` with every bare-colon token (unreadable empty
   keyword) replaced by a unique placeholder `:unnamed-N`, so an already-corrupt
   EDN blob (a saved character or localStorage plugins carrying a `:` key) can
   be read instead of crashing the load with \"A single colon is not a valid
   keyword.\" String-aware: colons inside \"...\" and all valid keywords are
   preserved. Returns {:text <sanitized> :count <replacements>}; :count 0 means
   the input was already clean (do not rewrite it)."
  [edn-str]
  (if (string? edn-str)
    (let [cnt (atom 0)
          text (s/replace edn-str bare-colon-re
                          (fn [m] (if (= \" (first m))
                                    m
                                    (str ":unnamed-" (swap! cnt inc)))))]
      {:text text :count @cnt})
    {:text edn-str :count 0}))

(defn- name-to-kw-aux [name ns]
  (when (string? name)
    (as-> name $
        (s/lower-case $)
        (s/replace $ #"'" "")
        (s/replace $ #"\W" "-")
        (s/replace $ #"\-+" "-")
        ;; Drop a TRAILING separator. Edge whitespace and terminal punctuation both
        ;; became dashes above, and collapsing runs cannot remove a run of one at
        ;; the end, so "Eladrin (Cha)" derived :eladrin-cha- -- 247 of 661 keys in
        ;; one shipped pak carried that dangling dash and it means nothing.
        ;;
        ;; The LEADING dash stays. It is not noise: a name starting with a
        ;; non-letter derives a keyword that fails keyword-starts-with-letter?,
        ;; which is how the keyword-trap machinery catches junk. Sanitising it here
        ;; would silently admit "  Baz", "++Plus" and "Uber" as valid content.
        ;;
        ;; A name that is ALL separators is left alone. "@@@" reduces to "-", and
        ;; stripping that leaves "", which trips the blank-name placeholder below
        ;; and yields :unnamed-<hash> -- which starts with a letter and so PASSES
        ;; the trap check. The guard keeps junk detectable.
        ;;
        ;; Keys stored before this change keep their trailing dash. They still
        ;; resolve, via entity/index-matching-key and common/canonical-key.
        (if (re-matches #"-+" $) $ (s/replace $ #"-+$" ""))
        ;; Never emit the empty keyword `:` — a name that reduced to "" (blank,
        ;; or apostrophe-only like "'") would build (keyword "") = a bare `:`,
        ;; an unreadable EDN token that crashes read-string on load. Substitute a
        ;; stable placeholder keyed off the ORIGINAL name so distinct empties
        ;; ("" vs "'" vs "''") stay distinct. Only the empty case is touched, so
        ;; no existing (non-blank) key changes.
        (if (s/blank? $) (str "unnamed-" (hash name)) $)
        (keyword ns $))))

(def memoized-name-to-kw (memoize name-to-kw-aux))

(defn name-to-kw [name & [ns]]
  (memoized-name-to-kw name ns))

(defn canonical-key
  "A key reduced to the form `name-to-kw` produces TODAY, for matching one that was
   stored before the derivation changed.

   It removes exactly what the derivation stopped emitting -- a trailing separator
   -- and nothing else. Removing more would be worse than useless: strip every dash
   and `:fire-bolt` matches `:firebolt`, binding a character to content it never
   referenced.

   The leading dash is deliberately preserved. It is how `keyword-starts-with-letter?`
   catches junk names, and canonicalising it away here would let a trapped key
   match a legitimate one.

   A key that is nothing but separators is returned unchanged: reducing it to the
   empty keyword would make every such key equal to every other."
  [k]
  (when (keyword? k)
    (let [n (name k)
          trimmed (s/replace n #"-+$" "")]
      (if (s/blank? trimmed) k (keyword (namespace k) trimmed)))))

(def ^:private word-separator-re
  "Splits a source name into words. Anything that is not a letter or digit
   separates, with Latin-1 Supplement and Latin Extended-A/B spelled out as
   word characters so accented names keep their shape.

   The ranges are literal because the portable alternative does not exist:
   \\p{L} and \\p{N} are Java-only, and in a JS RegExp without the `u` flag \\p is
   just an escaped `p`, which would silently split on the letter p instead."
  #"[^a-zA-Z0-9\u00C0-\u024F]+")

(def source-abbreviation-overrides
  "Sources whose real-world abbreviation is not what the derivation would produce.

   The two-shape rule below earns its keep on invented source names, where there
   is no established form to get wrong. It is wrong by definition on sources that
   already HAVE one: nobody writes Unearthed Arcana as UdAa. Where a name is
   already an abbreviation in the world, the world wins.

   Keys are the source name reduced by `abbreviation-lookup-key` -- lowercased,
   apostrophes dropped, every other non-alphanumeric run collapsed to one space --
   so \"Unearthed Arcana\", \"unearthed-arcana\" and \"Unearthed Arcana:\" all hit the
   same entry.

   Only sources the derivation gets WRONG belong here. \"Tasha's Cauldron of
   Everything\" already derives TCoE and \"Volo's Guide to Monsters\" already derives
   VGtM, so listing them would just be a second place to keep them correct."
  {"unearthed arcana" "UA"
   ;; The initialisms themselves, so someone who types the short form lowercase
   ;; gets the same tag as someone who spells the source out. The all-caps
   ;; passthrough below only catches them when they are already capitalised.
   "ua" "UA"
   "srd" "SRD"
   "phb" "PHB"
   "dmg" "DMG"
   "mm" "MM"
   "monster manual" "MM"
   "players handbook" "PHB"
   "dungeon masters guide" "DMG"
   "eberron" "EB"
   "eberron rising from the last war" "ERLW"
   "mordenkainen presents monsters of the multiverse" "MPMM"})

(defn- abbreviation-lookup-key
  "A source name reduced to its comparable form for the override table."
  [source-name]
  (-> (str source-name)
      (s/replace #"['’]" "")
      (s/lower-case)
      (s/replace #"[^a-z0-9À-ɏ]+" " ")
      (s/trim)))

(defn source-abbreviation
  "A short tag for a content source, for disambiguating two items that share a
   name -- \"Kibbles Tasty\" -> \"KsTy\", \"Tasha's Cauldron of Everything\" -> \"TCoE\".

   Two shapes, because one rule cannot serve both lengths. A short source has too
   few words for initials to say anything (\"Kibbles Tasty\" -> \"KT\" is noise), so
   each word contributes its first and last letter. A long source is one people
   already abbreviate by initials, and reading the real-world form back is the
   whole point of showing it.

   Case follows from that. The short form is normalised (`Xx` per word) because it
   is a coinage and consistency is all it has. The long form preserves each word's
   own case, which is what turns \"of\" into the lowercase `o` in `TCoE` rather than
   an `O` nobody writes.

   Apostrophes are removed before splitting rather than treated as separators, so
   \"Tasha's\" stays one word; splitting there would yield a stray \"s\" word and push
   a 3-word source into the 4-word branch.

   Returns nil when there is nothing to abbreviate. Callers must handle that --
   it means the source name carried no letters or digits at all, and inventing a
   tag for it would be worse than leaving the name alone."
  [source-name]
  (let [words (->> (-> (str source-name)
                       (s/replace #"['’]" "")
                       (s/split word-separator-re))
                   (remove s/blank?))]
    (when (seq words)
      (if-let [override (get source-abbreviation-overrides
                             (abbreviation-lookup-key source-name))]
        override
        ;; A source that is ALREADY an abbreviation is passed through rather than
        ;; abbreviated again: "UA" would otherwise come back "Ua", which is the
        ;; same name with its meaning filed off. One all-caps word, short enough
        ;; to read as a tag.
        (if (and (= 1 (count words)) (re-matches #"[A-Z0-9]{2,6}" (first words)))
          (first words)
          (if (<= (count words) 3)
            (s/join (map (fn [w]
                           (if (= 1 (count w))
                             (s/upper-case w)
                             (str (s/upper-case (subs w 0 1))
                                  (s/lower-case (subs w (dec (count w)))))))
                         words))
            (s/join (map #(subs % 0 1) words))))))))

(defn- abbreviation-suffix-re
  "Matches a trailing \" (Abbr)\" or \" (Abbr 2)\" for one specific abbreviation, so
   re-applying the same tag replaces it instead of stacking another copy.

   `abbr` is interpolated raw, which is safe only because source-abbreviation
   emits letters and digits and nothing else. Java's \\Q...\\E quoting would be the
   general answer and is not available here -- this is .cljc, and a JS RegExp has
   no such construct."
  [abbr]
  (re-pattern (str "\\s*\\(" abbr "(?:\\s+\\d+)?\\)\\s*$")))

(defn disambiguated
  "The name and key for `item-name` tagged with `source-name`'s abbreviation, as
   one map, so the two cannot drift:

     (disambiguated \"Artificer\" \"Kibbles Tasty\") ;=> {:name \"Artificer (KsTy)\"
                                                       :key  :artificer-ksty}

   The key is DERIVED from the tagged name rather than minted alongside it. That
   is the entire point. The editor's save path re-derives a key from the name, so
   a key built any other way reverts on the next save and the duplicate it was
   resolving comes back. Derived, re-derivation is a no-op.

   `taken?` is an optional predicate on a candidate key; when it says the key is
   already in use, a counter goes INSIDE the parentheses -- \"Artificer (KsTy 2)\"
   -- so the tie-break rides in the name too and survives the same round trip.

   Idempotent for a given source: re-tagging an already-tagged name replaces the
   suffix rather than appending a second one, so importing the same file twice
   does not yield \"Artificer (KsTy) (KsTy)\".

   Returns the name unchanged (with its derived key) when the source yields no
   abbreviation, so a nameless source degrades to today's behaviour instead of
   producing \"Artificer ()\"."
  ([item-name source-name] (disambiguated item-name source-name (constantly false)))
  ([item-name source-name taken?]
   (let [base (s/trim (str item-name))
         abbr (source-abbreviation source-name)]
     (if-not abbr
       {:name base :key (name-to-kw base)}
       (let [stem (s/replace base (abbreviation-suffix-re abbr) "")
             stem (if (s/blank? stem) base stem)
             candidate (fn [n] (let [nm (if n
                                          (str stem " (" abbr " " n ")")
                                          (str stem " (" abbr ")"))]
                                 {:name nm :key (name-to-kw nm)}))]
         (loop [c (candidate nil) n 2]
           (if (or (not (taken? (:key c))) (> n 99))
             c
             (recur (candidate n) (inc n)))))))))

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

;; ── Number-word translation, for repairing keyword-trap names ────────────────
;; A homebrew NAME derives its KEY, and a key must start with a letter
;; (keyword-starts-with-letter?). Names that LEAD with a number ("9 Lives",
;; "2nd Wind") are the most common trap. Instead of discarding the name, we
;; translate the leading number to its word form so the user's intent survives:
;;   "9 Lives"  -> "Nine Lives"      "2nd Wind" -> "Second Wind"
;;   "20 Sided" -> "Twenty Sided"    "13th Warrior" -> "Thirteenth Warrior"
;; This is BOUNDED on purpose (see max-number-word): above the cap a leading
;; number reads as data — a year/stat/code ("2020 Vision") — not a word, so the
;; translator declines and the caller falls back (strip symbols, else placeholder).
;; Depth is cheap to extend; the cap is a quality knob, not an effort limit.

(def ^:const max-number-word
  "Inclusive cap for number->word name repair. 0..this translate to words; a
   larger leading number reads as data, not a name, so translation declines.
   999 covers every realistic name ('100 Hands', '300'); bump to 9999 for
   '1000 Cuts'. It's a constant precisely so moving the ceiling stays one edit."
  999)

(def ^:private cardinal-ones
  ["zero" "one" "two" "three" "four" "five" "six" "seven" "eight" "nine" "ten"
   "eleven" "twelve" "thirteen" "fourteen" "fifteen" "sixteen" "seventeen"
   "eighteen" "nineteen"])

(def ^:private cardinal-tens
  ["" "" "twenty" "thirty" "forty" "fifty" "sixty" "seventy" "eighty" "ninety"])

(defn cardinal->words
  "Cardinal words for 0 <= n <= 999 (121 -> \"one hundred twenty-one\"), else nil."
  [n]
  (cond
    (or (not (integer? n)) (neg? n) (> n 999)) nil
    (< n 20)  (nth cardinal-ones n)
    (< n 100) (let [t (nth cardinal-tens (quot n 10)) o (rem n 10)]
                (if (zero? o) t (str t "-" (nth cardinal-ones o))))
    :else     (let [h (nth cardinal-ones (quot n 100)) r (rem n 100)]
                (if (zero? r) (str h " hundred")
                    (str h " hundred " (cardinal->words r))))))

(def ^:private cardinal->ordinal-word
  ;; The irregular ordinal stems; every other word just takes a "th" suffix
  ;; (fourth, sixth, seventh, tenth, thirteenth, …).
  {"zero" "zeroth"  "one" "first"   "two" "second"  "three" "third"
   "five" "fifth"   "eight" "eighth" "nine" "ninth" "twelve" "twelfth"
   "twenty" "twentieth"  "thirty" "thirtieth"  "forty" "fortieth"
   "fifty" "fiftieth"    "sixty" "sixtieth"    "seventy" "seventieth"
   "eighty" "eightieth"  "ninety" "ninetieth"  "hundred" "hundredth"})

(defn- ordinalize-word [w]
  (or (cardinal->ordinal-word w) (str w "th")))

(defn ordinal->words
  "Ordinal words for 0 <= n <= 999 (21 -> \"twenty-first\", 13 -> \"thirteenth\",
   100 -> \"one hundredth\"), else nil. Only the FINAL atom is ordinalized."
  [n]
  (when-let [c (cardinal->words n)]
    (let [sp   (s/last-index-of c " ")
          head (if sp (subs c 0 (inc sp)) "")
          tail (if sp (subs c (inc sp)) c)
          hy   (s/last-index-of tail "-")]
      (if hy
        (str head (subs tail 0 (inc hy)) (ordinalize-word (subs tail (inc hy))))
        (str head (ordinalize-word tail))))))

(defn- parse-uint [digits]
  #?(:clj  (try (Long/parseLong digits) (catch Exception _ nil))
     :cljs (let [n (js/parseInt digits 10)] (when-not (js/isNaN n) n))))

(defn- title-number-phrase
  "Title-case a number phrase for a name lead: \"twenty-one\" -> \"Twenty-one\",
   \"one hundred\" -> \"One Hundred\" (capitalize the letter after start/space)."
  [phrase]
  (s/replace phrase #"(^|\s)([a-z])"
             (fn [[_ pre ch]] (str pre (s/upper-case ch)))))

(defn lead-number->words
  "If `name` starts with a number — cardinal (\"9 Lives\") or ordinal
   (\"2nd Wind\") — return it with that leading number replaced by its
   Title-Cased word form (\"Nine Lives\", \"Second Wind\"). Returns nil when the
   name doesn't start with a translatable, in-range (<= max-number-word) number,
   or when the digits are glued to a non-ordinal letter (dice/version tokens like
   \"3d6\", \"5e\" are deliberately left alone rather than mangled)."
  [name]
  (when (string? name)
    (let [t (s/triml name)]
      (when-let [[_ digits tail] (re-matches #"(\d+)([\s\S]*)" t)]
        (when (<= (count digits) 4)                 ; length guard before parse
          (when-let [n (parse-uint digits)]
            (when (<= n max-number-word)
              (let [ord (re-find #"(?i)^(st|nd|rd|th)($|\s[\s\S]*|[^a-zA-Z][\s\S]*)" tail)]
                (cond
                  ;; ordinal: "2nd Wind" -> "Second Wind"
                  ord
                  (when-let [w (ordinal->words n)]
                    (str (title-number-phrase w) (nth ord 2)))
                  ;; cardinal: only when the digits are a standalone token —
                  ;; followed by whitespace or the end of the string. Anything
                  ;; else glued on ("3d6", "5e", or junk like "1@-asdml;") is NOT
                  ;; a "N word" name, so we decline and let it become "Unnamed".
                  (re-find #"^(\s|$)" tail)
                  (when-let [w (cardinal->words n)]
                    (str (title-number-phrase w) tail))
                  :else nil)))))))))

(defn repair-name-lead
  "Best-effort coerce `name` to a valid, letter-leading name by translating a
   leading NUMBER to its word form (\"9 Lives\" -> \"Nine Lives\"). Returns the
   repaired name, an already-valid name unchanged, or nil when it can't be
   salvaged this way (symbol-leading or out-of-range) — the caller then falls
   back to a placeholder like \"Unnamed <Type>\". Deliberately conservative:
   symbol-led junk (\"@@@\", \"1@-asdml;\") is left for the placeholder rather
   than salvaged into more junk. Purely a SUGGESTION; the caller still checks the
   derived key for collisions."
  [name]
  (when (string? name)
    (let [t (s/trim name)]
      (if (starts-with-letter? t) t (lead-number->words t)))))

(defn toggle-flag
  "Flip a boolean flag, but leave a collection untouched instead of collapsing it.
   Use in place of bare `not` for builder toggles whose path could land on a MAP:
   `(not {…})` is `false`, which DESTROYS the map so every child read returns nil
   (the 'true/false/nil from clicking a lot' corruption)."
  [v]
  (if (coll? v) v (not v)))

(defn toggle-in
  "Toggle a boolean flag at path `ks` in `m` (like `update-in` with `not`), with
   two safeguards: the LEAF uses `toggle-flag` so it never collapses a map; a
   non-associative INTERMEDIATE (a stray `false` from the old collapse bug, or an
   absent slot) is healed to a fresh map instead of crashing on `(assoc false …)`,
   so a click on a previously-corrupted spot self-heals."
  [m ks]
  (let [[k & more] ks]
    (if (seq more)
      (let [child (get m k)
            child (if (associative? child) child {})]   ; heal a collapsed node
        (assoc m k (toggle-in child more)))
      (assoc m k (toggle-flag (get m k))))))

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
