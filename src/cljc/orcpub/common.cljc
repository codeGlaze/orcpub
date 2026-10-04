(ns orcpub.common
  (:require [clojure.string :as s]
            #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])))

(def dot-char "•")

(def ^:private bare-colon-re
  ;; Matches EITHER a full "string literal" OR a bare-colon token (the printed empty
  ;; keyword `:`). A string literal is consumed whole, so a `:` inside one never matches.
  ;; The lookahead requires a delimiter or end after the colon, so `:foo`, `::x`,
  ;; `:ns/foo` and `#:ns{...}` are left untouched.
  #"\"(?:[^\"\\]|\\.)*\"|:(?=[\s,{}\[\]()\";]|$)")

(defn sanitize-edn-colons
  "SELF-HEAL: return `edn-str` with every bare-colon token (unreadable empty keyword)
   replaced by a unique placeholder `:unnamed-N`, so a corrupt EDN blob (a saved character
   or localStorage plugins carrying a `:` key) reads instead of crashing with \"A single
   colon is not a valid keyword.\" Colons inside \"...\" and valid keywords are preserved.
   Returns {:text <sanitized> :count <replacements>}; :count 0 means the input was already
   clean (do not rewrite it). A non-string is returned as {:text edn-str :count 0}."
  [edn-str]
  (if (string? edn-str)
    (let [cnt (atom 0)
          text (s/replace edn-str bare-colon-re
                          (fn [m] (if (= \" (first m))
                                    m
                                    (str ":unnamed-" (swap! cnt inc)))))]
      {:text text :count @cnt})
    {:text edn-str :count 0}))

(defn ascii-lower-case
  "Lowercase without the machine's locale. On a Turkish or Azerbaijani JVM
   clojure.string/lower-case folds \"I\" to the dotless \"ı\", so the server derived different
   keys from the same content. Names, keys and search terms are machine tokens, not prose. JS
   toLowerCase is already locale-invariant. docs/kb/locale-safety.md."
  [x]
  (let [t (str x)]
    #?(:clj  (.toLowerCase ^String t java.util.Locale/ROOT)
       :cljs (.toLowerCase t))))

(defn ascii-upper-case
  "Uppercase without the machine's locale; the upper-case twin of ascii-lower-case. On a
   Turkish JVM clojure.string/upper-case turns \"i\" into the dotted \"İ\", which no [A-Z]
   pattern matches."
  [x]
  (let [t (str x)]
    #?(:clj  (.toUpperCase ^String t java.util.Locale/ROOT)
       :cljs (.toUpperCase t))))

(defn- name-to-kw-aux [name ns]
  (when (string? name)
    (as-> name $
        (ascii-lower-case $)
        (s/replace $ #"'" "")
        (s/replace $ #"\W" "-")
        (s/replace $ #"\-+" "-")
        ;; Drop a TRAILING separator ("Eladrin (Cha)" -> :eladrin-cha). Keep a LEADING one: a
        ;; non-letter lead is how keyword-starts-with-letter? catches junk names. An all-separator
        ;; name ("@@@" -> "-") is kept, or it would become :unnamed-<hash> and pass that check.
        ;; Stored trailing-dash keys resolve via canonical-key (homebrew-key-map.md).
        (if (re-matches #"-+" $) $ (s/replace $ #"-+$" ""))
        ;; Never emit the empty keyword `:`, an unreadable EDN token that crashes read-string.
        ;; A name that reduced to "" (blank, or apostrophe-only like "'") gets a placeholder
        ;; keyed off the ORIGINAL name, so "" vs "'" vs "''" stay distinct. Only the empty
        ;; case is touched.
        (if (s/blank? $) (str "unnamed-" (hash name)) $)
        (keyword ns $))))

(def memoized-name-to-kw (memoize name-to-kw-aux))

(defn name-to-kw [name & [ns]]
  (memoized-name-to-kw name ns))

(defn canonical-key
  "Keyword `k` with its trailing separator removed and nothing else, for matching a stored key
   against the form `name-to-kw` produces; nil for a non-keyword. Stripping more would bind
   characters to content they never named (`:fire-bolt` vs `:firebolt`).
   GOTCHA: a leading dash is kept, so a trapped key cannot match a legitimate one, and an
   all-separator key is returned unchanged rather than reduced to the empty keyword."
  [k]
  (when (keyword? k)
    (let [n (name k)
          trimmed (s/replace n #"-+$" "")]
      (if (s/blank? trimmed) k (keyword (namespace k) trimmed)))))

(def ^:private word-separator-re
  "Splits a source name into words. Anything not a letter or digit separates; Latin-1
   Supplement and Latin Extended-A/B are spelled out as word characters so accented names
   keep their shape.
   GOTCHA: the ranges are literal because \\p{L} and \\p{N} are Java-only; in a JS RegExp
   without the `u` flag \\p is an escaped `p`, which would split on the letter p."
  #"[^a-zA-Z0-9\u00C0-\u024F]+")

(def source-abbreviation-overrides
  "Sources whose real-world abbreviation is not what `source-abbreviation` derives (nobody
   writes Unearthed Arcana as UdAa). Keys are the source name reduced by
   `abbreviation-lookup-key` (lowercased, apostrophes dropped, other non-alphanumeric runs
   collapsed to one space), so \"unearthed-arcana\" and \"Unearthed Arcana:\" hit one entry.
   Only sources the derivation gets WRONG belong here (source-tagged-keys.md)."
  {;; The source an item lands in when its author never named one. The derivation reads it as
   ;; three words and produces DtOnSe; "dflt" is the tag people recognise. It matters more than
   ;; any other entry here: most first-time homebrew lands in this source.
   "default option source" "dflt"
   "unearthed arcana" "UA"
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
      (ascii-lower-case)
      (s/replace #"[^a-z0-9À-ɏ]+" " ")
      (s/trim)))

(def ^:private initialism-re
  "A word that is ALREADY an abbreviation: two to six characters, all caps or digits, starting
   with a letter.

   GOTCHA: the leading-letter requirement is what keeps a year out. \"Unearthed Arcana 2022:
   Heroes of Krynn\" must read UA2HoK, not UA2022HoK."
  #"[A-Z][A-Z0-9]{1,5}")

(defn source-abbreviation
  "A short tag for a content source, for disambiguating items that share a name; nil when the
   name has no letters or digits (callers must handle it). An override or a lone initialism
   passes through; initialisms among words stay whole with initials of the rest; up to three
   words give first+last letter each, `Xx`-cased (\"Kibbles Tasty\" -> \"KsTy\"); more words
   give initials in each word's own case (\"TCoE\"). Why two shapes: source-tagged-keys.md.
   GOTCHA: apostrophes are removed, not split on, so \"Tasha's\" stays one word."
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
        (cond
          (and (= 1 (count words)) (re-matches initialism-re (first words)))
          (first words)

          ;; An initialism with a description after it ("UA - Giant Options", "MM Extra
          ;; Monsters") keeps the initialism whole and takes initials of the rest; the
          ;; two-shape rule would mangle the part that carries the meaning.
          (some #(re-matches initialism-re %) words)
          (s/join (map (fn [w] (if (re-matches initialism-re w) w (subs w 0 1))) words))

          (<= (count words) 3)
          (s/join (map (fn [w]
                         (if (= 1 (count w))
                           (s/upper-case w)
                           (str (s/upper-case (subs w 0 1))
                                (s/lower-case (subs w (dec (count w)))))))
                       words))

          :else
          (s/join (map #(subs % 0 1) words)))))))

(defn- abbreviation-suffix-re
  "Matches a trailing \" (Abbr)\" or \" (Abbr 2)\" for one specific abbreviation, so
   re-applying the same tag replaces it instead of stacking another copy.
   GOTCHA: `abbr` is interpolated raw, safe only because source-abbreviation emits letters
   and digits alone; a JS RegExp has no \\Q...\\E quoting to fall back on."
  [abbr]
  (re-pattern (str "\\s*\\(" abbr "(?:\\s+\\d+)?\\)\\s*$")))

(defn disambiguated
  "{:name :key} for `item-name` tagged with `source-name`'s abbreviation: \"Artificer\" +
   \"Kibbles Tasty\" -> \"Artificer (KsTy)\" / :artificer-ksty. The key is DERIVED from the
   tagged name, so the editor re-deriving it on save is a no-op. `taken?` (optional key
   predicate) puts a counter inside the parens, \"Artificer (KsTy 2)\", up to 99. An existing
   tag is replaced, not stacked. With no abbreviation, returns the trimmed name and its key.
   (source-tagged-keys.md)"
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

(defn normalize-abbreviation
  "A source's own abbreviation, reduced to the shape the derivation would have produced: letters and
   digits only, upper-cased, at most six, and a letter first. nil when nothing usable is left.

   GOTCHA: upper-casing is not cosmetic. It is what makes the value pass `source-abbreviation`'s
   already-an-abbreviation branch, so an author-set tag and a derived one travel the same path. The
   key lower-cases either way, so case only shows in a name tagged by an import conflict."
  [abbr]
  (let [cleaned (-> (str abbr)
                    (s/replace #"[^A-Za-z0-9]" "")
                    (ascii-upper-case))]
    (when (re-matches #"[A-Z][A-Z0-9]{1,5}" (subs cleaned 0 (min 6 (count cleaned))))
      (subs cleaned 0 (min 6 (count cleaned))))))

(defn source-tagged-key
  "The key an item mints in `source-name`: its name's keyword with the source's abbreviation
   appended — `(\"Stone Elf\" \"Tidewater Curios\")` => `:stone-elf-trcs`. A usable `abbr` replaces
   the derived abbreviation; a source with none mints the plain key. The NAME is not tagged.
   GOTCHA: routed through `disambiguated`, so minted and import-tagged keys cannot drift. No
   `taken?`: one name twice in one source is reported, not uniquified (source-tagged-keys.md)."
  ([item-name source-name] (source-tagged-key item-name source-name nil))
  ([item-name source-name abbr]
   ;; An author-set abbreviation is handed in AS the source name: normalized, it is already in the
   ;; shape `source-abbreviation` passes through, so the explicit and derived paths stay one path.
   (:key (disambiguated item-name (or (normalize-abbreviation abbr) source-name)))))

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

;; ── Number-word translation, for repairing keyword-trap names (keyword-trap-name-repair.md) ──
;; A name's derived key must start with a letter, so a leading number ("9 Lives", "2nd Wind")
;; is translated to words ("Nine Lives", "Second Wind"). Bounded by max-number-word: above it
;; a number reads as data ("2020 Vision"), the translator declines and the caller falls back.

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
  "Best-effort coerce `name` to a letter-leading name by translating a leading NUMBER to
   words (\"9 Lives\" -> \"Nine Lives\"). Returns the trimmed name if it already leads with a
   letter, else the repaired name, else nil (symbol-led junk like \"@@@\" or \"1@-asdml;\", or
   out of range) — the caller then uses a placeholder like \"Unnamed <Type>\". A SUGGESTION
   only: the caller still checks the derived key for collisions."
  [name]
  (when (string? name)
    (let [t (s/trim name)]
      (if (starts-with-letter? t) t (lead-number->words t)))))

(defn toggle-flag
  "Flip a boolean flag, but return a collection untouched. Use in place of bare `not` for
   builder toggles whose path could land on a MAP: `(not {…})` is `false`, which destroys the
   map so every child read returns nil. Only an actual `true` reads as ON: nil, absent and
   non-boolean garbage (a string \"false\") read as OFF, so the first click turns them ON.
   Both halves are needed; see the convergence note in builder_fields.cljc."
  [v]
  (if (coll? v) v (not (true? v))))

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

;; Crash-safe, locale-pinned case fold for sort/compare keys (ascii-lower-case): coerces a
;; nil/non-string to "" so it can't crash on it. Never throws — it folds arbitrary keys
;; (e.g. :level), so a non-string isn't a bug here; that judgment is the caller's
;; (see feature-name, where the dev-throw lives).
(defn lower-case [x]
  (ascii-lower-case x))

;; Case-insensitive `sort-by`, built on the safe fold above so a nil/non-string key
;; sorts as "" rather than throwing.
(defn aloof-sort-by [sorter coll]
  (sort-by (comp lower-case sorter) coll))

;; Display name with an obvious placeholder: any unusable name -> "[Unnamed feature]"
;; (shown and sorted by), never a blank or a plausible coercion like "42". A string IS
;; expected here, so a wrong-typed name throws in cljs dev builds; prod shows the placeholder.
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
