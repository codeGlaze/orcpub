(ns orcpub.dnd.e5.ledger
  "The character data page (the ledger): a saved or draft character's stored picks as plain rows,
   read without the template, the app or any homebrew. Pure data, shared by the page script and
   the JVM tests. character-rescue.md"
  (:require #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])
            [clojure.string :as s]
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

(defn read-character
  "Text from storage (a saved character's EDN, or the browser draft) as {:character} in the
   `entity/from-strict` shape, or {:error message} when it cannot be read as a character.
   Bare-colon keys are repaired first, as the app does (`common/sanitize-edn-colons`)."
  [text]
  (try
    (let [data #?(:clj (edn/read-string (:text (common/sanitize-edn-colons text)))
                  :cljs (reader/read-string (:text (common/sanitize-edn-colons text))))]
      (if (and (map? data) (some #(contains? data %) [::se/selections ::se/values ::se/owner]))
        {:character (entity/from-strict data)}
        {:error "This is not a character."}))
    (catch #?(:clj Exception :cljs :default) e
      {:error (str "This character's data cannot be read: " #?(:clj (.getMessage e) :cljs (.-message e)))})))

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
  "The rows as plain text for a support message: a heading naming the character, then one
   `:line` per row."
  [heading rows]
  (s/join "\n" (cons heading (map :line rows))))
