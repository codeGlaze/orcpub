(ns orcpub.dnd.e5.junk-pack-test
  "Imported homebrew is untrusted: any field of any entry can hold any shape. One entry of each
   content type (test/fixtures/junk-bases.orcbrew) gets junk in each field, and the pack goes
   through what the app does with homebrew: startup, the homebrew in every subscription realized,
   the deep check, and a character that picks each entry. An entry may be set aside; nothing may
   throw. The everyday suite runs the base pack only; test/e2e/junk-pack-audit.js runs it all."
  (:require [cljs.test :refer-macros [deftest is]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.fixtures :refer-macros [fixture-text]]
            [orcpub.entity :as entity]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [orcpub.dnd.e5.homebrew-check :as homebrew-check]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.monsters :as monsters5e]
            [orcpub.dnd.e5.encounters :as encounters5e]
            [orcpub.dnd.e5.character-drafts :as drafts]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.spell-subs]))

(def ^:private source "Junk Pak")

(def ^:private base-entries
  (delay (get (:data (orcbrew-val/parse-edn (fixture-text "test/fixtures/junk-bases.orcbrew")))
              source)))

(def ^:private junk [nil 42 "x" true :kw [] [42] ["x"] [{}] {} {:a 1} #{42}])

(defn- audit?
  "Is this the full audit (test/e2e/junk-pack-audit.js)? It takes minutes, so the everyday suite
   checks only that the base pack works."
  []
  (boolean (re-find #"junk-audit" (str (.-search js/location)))))

;; ---------------------------------------------------------------------------------------------
;; Cases: [content-type path value]. A path is [field] or [field index-or-key field].

(defn- field-paths [entry]
  (let [skip #{:key :option-pack}]
    (concat
     (for [k (keys entry) :when (not (skip k))] [k])
     (for [[k v] entry :when (not (skip k))
           p (cond (map? v) (for [k2 (keys v)] [k k2])
                   (and (sequential? v) (map? (first v))) (for [k2 (keys (first v))] [k 0 k2])
                   :else nil)]
       p))))

(defn- library
  "The pack with `entries` ({content-type {key entry}}) as its only content."
  [entries]
  {source entries})

(defn- batch
  "Every field of every base entry of `content-type` holding `v`, one entry per field."
  [content-type v]
  (let [base (get-in @base-entries [content-type :junk-base])
        paths (field-paths base)]
    {:cases (into {} (map-indexed (fn [i p] [(keyword (str "junk-" i)) p]) paths))
     :entries {content-type
               (into {} (map-indexed
                         (fn [i p]
                           (let [k (keyword (str "junk-" i))]
                             [k (assoc (assoc-in base p v) :key k :name (str "Junk " i))]))
                         paths))}}))

;; ---------------------------------------------------------------------------------------------
;; What the app does with a pack

(def ^:private homebrew-subs
  [[::e5/plugin-vals] [::e5/plugins-with-sources]
   [::bg5e/plugin-backgrounds] [::bg5e/backgrounds]
   [::langs5e/plugin-languages] [::langs5e/languages] [::langs5e/language-map]
   [::selections5e/plugin-selections] [::selections5e/selection-map]
   [::races5e/plugin-races] [::races5e/plugin-subraces] [::races5e/plugin-subraces-map]
   [::races5e/races] [::races5e/race-map]
   [::classes5e/plugin-subclasses] [::classes5e/plugin-classes] [::classes5e/plugin-subclasses-map]
   [::classes5e/classes] [::classes5e/class-map]
   [::classes5e/plugin-invocations] [::classes5e/invocations]
   [::classes5e/plugin-boons] [::classes5e/boons]
   [::feats5e/plugin-feats] [::feats5e/feats]
   [::spells5e/plugin-spells] [::spells5e/plugin-spells-map] [::spells5e/spells]
   [::spells5e/spells-map] [::spells5e/plugin-spell-lists] [::spells5e/spell-lists]
   [::monsters5e/plugin-monsters] [::monsters5e/monsters] [::monsters5e/monster-map]
   [::monsters5e/sorted-monsters]
   [::encounters5e/plugin-encounters] [::encounters5e/encounters] [::encounters5e/encounter-map]
   [::char5e/template-selections] [::char5e/template]])

(defn- homebrew?
  "Is `x` one of the pack's entries, or an option made from one?"
  [x]
  (and (map? x)
       (or (= source (:option-pack x)) (= source (::t/plugin-source x))
           (let [k (or (::t/key x) (:key x))]
             (and (keyword? k) (str/starts-with? (name k) "junk-"))))))

(defn- realize-homebrew
  "Realize the homebrew in a subscription's value and leave the built-in content alone: a template
   or a list of selections realizes the homebrew options in each selection, and in the selections
   of each option (a subrace sits under its race); a map or list realizes its homebrew items."
  [v]
  (let [selections (cond (::t/selections v) (::t/selections v)
                         (and (sequential? v) (some ::t/options v)) v)
        items (fn [xs] (mapcat #(if (and (sequential? %) (not (map? %))) % [%]) xs))]
    (cond
      selections
      (doseq [s selections
              o (::t/options s)
              :when (or (homebrew? o) (some homebrew? (mapcat ::t/options (::t/selections o))))]
        (guard/realize o))
      (map? v) (guard/realize (filter homebrew? (items (vals v))))
      (sequential? v) (guard/realize (filter homebrew? (items v)))
      :else (guard/realize v))))

(defn- msg [e] (first (str/split-lines (or (ex-message e) (str e)))))

(defn- attempt [stage f]
  (try (f) nil (catch :default e {:stage stage :error (msg e)})))

(def ^:private stored-character
  {::entity/options {:race {::entity/key :human}
                     :class [{::entity/key :fighter
                              ::entity/options {:levels [{::entity/key :level-1}]}}]}})

(defn- levels [n] (vec (for [i (range 1 (inc n))] {::entity/key (keyword (str "level-" i))})))

(defn- seeds
  "Characters to start from for each homebrew entry: a class or subclass needs levels to offer its
   choices; anything else starts from the stored character."
  [entries]
  (let [section #(let [v (get entries %)] (when (map? v) (filter (comp map? val) v)))
        leveled (fn [cls] {::entity/options {:race {::entity/key :human}
                                             :class [{::entity/key cls
                                                      ::entity/options {:levels (levels 20)}}]}})]
    (concat
     (for [[k _] (section ::e5/classes)] [k (leveled k)])
     (for [[k {cls :class}] (section ::e5/subclasses) :when (keyword? cls)] [k (leveled cls)])
     (for [t [::e5/races ::e5/subraces ::e5/backgrounds ::e5/feats]
           [k _] (section t)]
       [k stored-character]))))

(defn- offered
  "The selections `c` is offered, and `c` built."
  [template c]
  (let [built (entity/build c template)]
    [built (entity/combine-selections (entity/available-selections c built template))]))

(defn- pick
  "`c` with option `k` picked wherever it is offered and not yet picked, as the builder picks it,
   repeated so a pick that offers it again (a subrace under a race) is followed."
  [template c k]
  (loop [c c i 0]
    (let [[_ avail] (offered template c)
          s (first (filter (fn [s] (and (some #(= k (::t/key %)) (::t/options s))
                                        (nil? (entity/get-option template c
                                                                 (conj (entity/actual-path s) k)))))
                           avail))]
      (if (or (nil? s) (> i 4))
        c
        (recur (entity/update-option template c (conj (entity/actual-path s) k)
                                     (fn [o] (if (::t/multiselect? s)
                                               (conj (or o []) {::entity/key k})
                                               {::entity/key k})))
               (inc i))))))

(defn- use-entry!
  "Pick homebrew entry `k` in `c`, build it, and draw what the builder draws under it: every
   selection whose path runs through `k`, fully realized."
  [template k c]
  (let [c (pick template c k)
        [built avail] (offered template c)]
    (guard/realize (char5e/ability-values built))
    (guard/realize (filter #(some #{k} (entity/actual-path %)) avail))))

(defn- run-pack!
  "Put `lib` in storage, start the app on it, and use it. Returns the failures, each
   {:stage :error}; an entry set aside is not a failure."
  [lib]
  (doseq [k (drafts/draft-keys)] (drafts/remove-draft! k))
  (doseq [k ["plugins" "plugins:rejected" "plugins:corrupt" "character"]]
    (.removeItem js/localStorage k))
  (.setItem js/localStorage "plugins" (pr-str lib))
  (.setItem js/localStorage "character" (pr-str (char5e/to-strict stored-character)))
  (reset! app-db {})
  (rf/clear-subscription-cache!)
  ;; a report means the entry is set aside, which is allowed; drop it rather than queue the event
  (guard/call-with-reporter
   (constantly nil)
   (fn []
     (let [boot (attempt :startup #(rf/dispatch-sync [:initialize-db]))
           sub-fails (keep (fn [q] (attempt q #(realize-homebrew @(rf/subscribe q))))
                           homebrew-subs)
           deep (attempt :deep-check #(homebrew-check/deep-check nil))
           template (try @(rf/subscribe [::char5e/template]) (catch :default _ nil))
           builds (when template
                    (keep (fn [[k c]] (attempt [:character k] #(use-entry! template k c)))
                          (cons [nil stored-character]
                                (seeds (get (get-in @app-db [:plugins]) source)))))]
       (doall (remove nil? (concat [boot] sub-fails [deep] builds)))))))

(defn- cleanup! []
  (doseq [k ["plugins" "plugins:rejected" "plugins:corrupt" "character"]]
    (.removeItem js/localStorage k))
  (reset! app-db {})
  (rf/clear-subscription-cache!))

;; ---------------------------------------------------------------------------------------------

(deftest the-base-pack-works
  (let [failures (run-pack! (library @base-entries))]
    (cleanup!)
    (is (empty? failures) (pr-str failures))))

(deftest junk-in-any-field-never-throws
  (let [failures
        (doall
         (for [content-type (when (audit?) (keys @base-entries))
               v junk
               :let [{:keys [cases entries]} (batch content-type v)
                     fs (run-pack! (library entries))]
               :when (seq fs)
               ;; one pack per value holds every field: name the field by running each alone
               [k path] cases
               :let [single (run-pack! (library {content-type
                                                {k (get-in entries [content-type k])}}))]
               :when (seq single)]
           [(name content-type) path v (first single)]))]
    (cleanup!)
    (doseq [f failures] (js/console.log "JUNKFAIL" (pr-str f)))
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))

(deftest a-junk-section-never-throws
  (let [failures (doall
                  (for [content-type (when (audit?) (keys @base-entries))
                        v [42 "x" true [] [42] #{42}]
                        :let [fs (run-pack! (library {content-type v}))]
                        :when (seq fs)]
                    [(name content-type) v (first fs)]))]
    (cleanup!)
    (doseq [f failures] (js/console.log "JUNKFAIL" (pr-str f)))
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))
