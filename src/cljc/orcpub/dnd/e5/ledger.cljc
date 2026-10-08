(ns orcpub.dnd.e5.ledger
  "The character data page (the ledger): a saved or draft character's stored picks as plain rows,
   read without the template, the app or any homebrew. Pure data, shared by the page script and
   the JVM tests. character-rescue.md"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])
            [clojure.string :as s]
            [clojure.walk :as walk]
            [orcpub.common :as common]
            [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.dnd.e5.picks :as picks]))

(defn label
  "Readable name for a stored key: `:wizard-spells-known` is \"Wizard Spells Known\"."
  [k]
  (if (keyword? k) (common/kw-to-name k true) (str k)))

(defn- below
  "How many of `addresses` sit under `address`, at any depth."
  [addresses address]
  (let [n (count address)]
    (count (filter #(and (> (count %) n) (= address (subvec % 0 n))) addresses))))

(defn- value-text
  "The stored value of `entry` as EDN text with full keyword names, or nil when it has none."
  [entry]
  (when (contains? entry ::entity/value)
    (binding [*print-namespace-maps* false] (pr-str (::entity/value entry)))))

(defn rows
  "`character` (`entity/from-strict` shape) as one row per stored pick, parents first:
   {:address :depth :section :choice :key :value :below :line}. `:line` names the whole path,
   e.g. \"Class › Wizard › Wizard Spells Known › Brine Lash\"; `:below` counts the picks under it."
  [character]
  (let [listed (picks/addresses character)
        addresses (mapv first listed)]
    (mapv (fn [[address entry]]
            (let [pairs (partition 2 address)
                  [sel k] (last pairs)]
              {:address address
               :depth (dec (count pairs))
               :section (label sel)
               :choice (label k)
               :key k
               :value (value-text entry)
               :below (below addresses address)
               :line (s/join " › " (map label address))}))
          listed)))

(defn- read-edn
  "`text` read as EDN after repairing bare-colon keys, as the app does
   (`common/sanitize-edn-colons`). Throws when it still cannot be read."
  [text]
  (let [healed (:text (common/sanitize-edn-colons text))]
    #?(:clj (edn/read-string healed) :cljs (reader/read-string healed))))

(defn read-character
  "Text from storage (a saved character's EDN, or the browser draft) as {:character} in the
   `entity/from-strict` shape, or {:error message} when it cannot be read as a character."
  [text]
  (try
    (let [data (read-edn text)]
      (if (and (map? data) (some #(contains? data %) [::se/selections ::se/values ::se/owner]))
        {:character (entity/from-strict data)}
        {:error "This is not a character."}))
    (catch #?(:clj Exception :cljs :default) e
      {:error (str "This character's data cannot be read: " #?(:clj (.getMessage e) :cljs (.-message e)))})))

(defn read-stored
  "Text from storage as the stored map itself (the strict shape, as saved), or nil when it
   cannot be read. The base `save-data` works from, so whatever the page does not change is
   written back exactly as it was."
  [text]
  (try
    (let [data (read-edn text)] (when (map? data) data))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn save-data
  "`stored` (from `read-stored`) with its selections replaced by those of `character` (the
   `entity/from-strict` shape, after `picks/remove-at` or `put-at`): what the save route or the
   browser draft takes. Everything else, the summary and values included, is kept as stored."
  [stored character]
  (assoc stored ::se/selections (vec (::se/selections (entity/to-strict character)))))

(defn same-stored?
  "True when stored maps `a` and `b` (from `read-stored`) hold the same data, whatever order
   their lists come back in."
  [a b]
  (let [unordered (fn [x] (walk/postwalk #(if (sequential? %) (frequencies %) %) x))]
    (= (unordered a) (unordered b))))

(defn reapply
  "`pending` (`picks/remove-at` records) applied again to `character`, a fresh copy, as
   {:character :pending :lost}: `:pending` the records that applied, `:lost` the `:line` of each
   one whose pick is no longer stored there."
  [character pending]
  (reduce (fn [acc rec]
            (let [{c :character removed :removed} (picks/remove-at (:character acc) (:address rec))]
              (if removed
                (-> acc (assoc :character c) (update :pending conj (merge rec removed)))
                (update acc :lost conj (:line rec)))))
          {:character character :pending [] :lost []}
          pending))

(defn warning
  "What to tell the player before `row` (from `rows`) is removed, or nil when nothing needs
   saying: removing the race, a class or the ability scores leaves the builder asking again."
  [{:keys [address choice] under :below}]
  (let [with (when (pos? under) (str " and the " under " choices under it"))]
    (when (= 2 (count address))
      (case (first address)
        :class (str "Remove " choice with "? The builder will ask for a class again.")
        :race (str "Remove " choice with "? The builder will ask for a race again.")
        :ability-scores "Remove the ability scores? The app will not save this character until you choose them again in the builder."
        nil))))

(defn owner?
  "True when `user` (the app's stored login: {:user-data {:username :email}}) owns `character`,
   whose owner is stored as a username or an email, as the server's own check allows."
  [character {{:keys [username email]} :user-data}]
  (let [owner (::entity/owner character)]
    (boolean (and owner (some #(= owner %) (remove nil? [username email]))))))

(defn character-name
  "The name stored on `character` (`entity/from-strict` shape), or nil."
  [character]
  (get-in character [::entity/values :orcpub.dnd.e5.character/character-name]))

(defn support-text
  "`ledger-rows` (from `rows`) as plain text for a support message: `heading`, which names the
   character, then one `:line` per row."
  [heading ledger-rows]
  (s/join "\n" (cons heading (map :line ledger-rows))))
