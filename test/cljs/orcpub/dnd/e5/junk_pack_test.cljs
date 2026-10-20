(ns orcpub.dnd.e5.junk-pack-test
  "Imported homebrew is untrusted: any field of any entry can hold any shape. One entry of each
   content type (test/fixtures/junk-bases.orcbrew) gets junk in each field, and the pack goes in
   from storage, an import and a share link, then through startup, every subscription, the deep
   check, a character picking each entry, and the pages that draw it. An entry may be set aside
   (only that entry); nothing may throw and no built-in content may go missing. The everyday
   suite runs the base pack only; test/e2e/junk-pack-audit.js runs it all."
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
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.spell-subs]))

(def ^:private source "Junk Pak")

(def ^:private base-entries
  (delay (get (:data (orcbrew-val/parse-edn (fixture-text "test/fixtures/junk-bases.orcbrew")))
              source)))

(def ^:private junk [nil 42 "x" true :kw [] [42] ["x"] [{}] {} {:a 1} #{42}])

(defn- audit-types
  "The content types the audit covers: ?junk-audit=races,feats names some, ?junk-audit all of
   them (test/e2e/junk-pack-audit.js runs one type per page). Without it, none: the grid takes
   minutes, so the everyday suite checks only that the base pack works."
  []
  (let [q (str (.-search js/location))]
    (when-let [[_ named] (re-find #"junk-audit(?:=([a-z,]+))?" q)]
      (if named
        (filter (set (map #(keyword "orcpub.dnd.e5" %) (str/split named #",")))
                (keys @base-entries))
        (keys @base-entries)))))

(defn- report!
  "Log a failure as it is found, so an audit cut short still has what it found."
  [f]
  (js/console.log "JUNKFAIL" (pr-str f))
  f)

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
     (for [t [::e5/invocations ::e5/boons] [k _] (section t)] [k (leveled :warlock)])
     ;; a subrace is offered under its race, so start from that race
     (for [[k {race :race}] (section ::e5/subraces)]
       [k (if (keyword? race)
            (assoc-in stored-character [::entity/options :race] {::entity/key race})
            stored-character)])
     (for [t [::e5/races ::e5/backgrounds ::e5/feats]
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
          ;; a single choice holds one option (Race: Human), a multiple one a list of them
          picked? (fn [s] (let [o (entity/get-option template c (conj (entity/actual-path s) k))]
                            (if (sequential? o)
                              (some #(= k (::entity/key %)) o)
                              (= k (::entity/key o)))))
          s (first (filter (fn [s] (and (some #(= k (::t/key %)) (::t/options s))
                                        (not (picked? s))))
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
   selection whose path runs through `k`, fully realized. Throws when `k` is never offered."
  [template k c]
  (let [c (pick template c k)
        [built avail] (offered template c)]
    (when (and k (not (some #{k} (tree-seq coll? seq c))))
      (throw (js/Error. "never offered")))
    (guard/realize (char5e/ability-values built))
    (guard/realize (filter #(some #{k} (entity/actual-path %)) avail))))

(defn- expand
  "Draw `x` the way Reagent would: a vector headed by a function is a child component, so call it
   (twice for a component that returns its render function) and draw what it returns."
  [x]
  (cond (and (vector? x) (fn? (first x)))
        (let [[f & args] x
              r (apply f args)]
          (expand (if (fn? r) (apply r args) r)))
        (map? x) x
        (coll? x) (doall (map expand x))
        :else x))

(def ^:private builders
  "Each content type's builder page, and the event that opens an entry in it."
  {::e5/spells [::spells5e/set-spell views/spell-builder]
   ::e5/monsters [::monsters5e/set-monster views/monster-builder]
   ::e5/encounters [::encounters5e/set-encounter views/encounter-builder]
   ::e5/backgrounds [::bg5e/set-background views/background-builder]
   ::e5/languages [::langs5e/set-language views/language-builder]
   ::e5/invocations [::classes5e/set-invocation views/invocation-builder]
   ::e5/boons [::classes5e/set-boon views/boon-builder]
   ::e5/selections [::selections5e/set-selection views/selection-builder]
   ::e5/feats [::feats5e/set-feat views/feat-builder]
   ::e5/races [::races5e/set-race views/race-builder]
   ::e5/subraces [::races5e/set-subrace views/subrace-builder]
   ::e5/subclasses [::classes5e/set-subclass views/subclass-builder]
   ::e5/classes [::classes5e/set-class views/class-builder]})

(defn- pages
  "Draw My Content (the page, and each content type's list for the pack), the monster list
   filtered by a name, and each entry of `entries` opened in its builder. Returns the failures."
  [entries]
  (let [plugin (get-in @app-db [:plugins source])]
    (concat
     [(attempt [:page :my-content] #(expand [views/my-content]))]
     (for [{:keys [type-name type-key icon add-event edit-event delete-event plural]}
           views/my-content-types]
       (attempt [:page :my-content type-name]
                #(expand [views/my-content-type source plugin type-name type-key icon add-event
                          edit-event delete-event plural "" true])))
     [(attempt [:page :monster-list]
               #(do (rf/dispatch-sync [::char5e/filter-monsters "junk"])
                    (expand [views/monster-list-items])))]
     (for [[t es] entries :when (map? es)
           :let [[open builder] (builders t)]
           :when open
           [k e] es :when (map? e)]
       (attempt [:builder k] #(do (rf/dispatch-sync [open e]) (expand [builder])))))))

(defn- offered-keys
  "The keys of the classes, races and backgrounds the app offers now."
  []
  (let [ks #(set (keep (fn [x] (or (:key x) (::t/key x))) @(rf/subscribe [%])))]
    {:classes (ks ::classes5e/classes)
     :races (ks ::races5e/races)
     :backgrounds (ks ::bg5e/backgrounds)}))

(def ^:private built-in-keys
  "What the app offers with no homebrew at all, read once by the base-pack test."
  (atom nil))

(defn- missing-built-ins []
  (when-let [before @built-in-keys]
    (let [now (offered-keys)
          gone (into {} (for [[k ks] before
                              :let [m (remove (now k) ks)]
                              :when (seq m)]
                          [k (vec m)]))]
      (when (seq gone) (throw (js/Error. (str "built-in content went missing: " (pr-str gone))))))))

(defn- wrong-blame
  "Reports naming an entry that is not one of the pack's own: something else was set aside."
  [reports entries]
  (for [{:keys [content-type key]} reports
        :when (not (contains? (get entries content-type) key))]
    {:stage :set-aside :error (str "set aside " (pr-str content-type) " " (pr-str key)
                                   ", which is not in the pack")}))

(defn- run-pack!
  "Start the app with `lib` arriving `via` :storage (the stored library; also what
   LOAD_HOMEBREW_URL writes) or :share (a share link's homebrew), and use it. Returns the
   failures, each {:stage :error}."
  [lib & [via]]
  (doseq [k (drafts/draft-keys)] (drafts/remove-draft! k))
  (doseq [k ["plugins" "plugins:rejected" "plugins:corrupt" "character"]]
    (.removeItem js/localStorage k))
  (when (not= via :share) (.setItem js/localStorage "plugins" (pr-str lib)))
  (.setItem js/localStorage "character" (pr-str (char5e/to-strict stored-character)))
  (reset! app-db {})
  (rf/clear-subscription-cache!)
  (let [reports (atom [])]
    (guard/call-with-reporter
     #(swap! reports conj %)
     (fn []
       (let [boot (attempt :startup #(rf/dispatch-sync [:initialize-db]))
             shared (when (= via :share)
                      (attempt :share
                               #(rf/dispatch-sync [::e5/apply-shared-content {:plugins lib}])))
             entries (get (if (= via :share) (:shared-plugins @app-db) (:plugins @app-db)) source)
             sub-fails (keep (fn [q] (attempt q #(realize-homebrew @(rf/subscribe q))))
                             homebrew-subs)
             built-ins (attempt :built-in-content missing-built-ins)
             deep (attempt :deep-check #(homebrew-check/deep-check nil))
             template (try @(rf/subscribe [::char5e/template]) (catch :default _ nil))
             set-aside (set (map :key @reports))
             ;; an entry set aside is not offered, and is allowed; one silently not offered is lost
             builds (when template
                      (remove #(and (= "never offered" (:error %)) (set-aside (second (:stage %))))
                              (keep (fn [[k c]] (attempt [:character k] #(use-entry! template k c)))
                                    (cons [nil stored-character] (seeds entries)))))
             drawn (pages entries)]
         (doall (remove nil? (concat [boot shared] sub-fails [built-ins deep] builds drawn
                                     (wrong-blame @reports (get lib source))))))))))

(defn- run-via!
  "run-pack! for `lib` arriving `via` :storage, :share, or :import (the file importer's checks
   and clean-up first, then what it would store)."
  [lib via]
  (if (= via :import)
    (let [result (atom nil)]
      (or (some-> (attempt :import
                           #(reset! result (orcbrew-val/validate-import
                                            (pr-str lib) {:strategy :progressive :auto-clean true
                                                          :existing-plugins {}
                                                          :import-source-name source})))
                  vector)
          (when-let [data (:data @result)] (run-pack! (if (get data source) data {source data})))
          []))
    (run-pack! lib via)))

(defn- cleanup! []
  (doseq [k ["plugins" "plugins:rejected" "plugins:corrupt" "character"]]
    (.removeItem js/localStorage k))
  (reset! app-db {})
  (rf/clear-subscription-cache!))

;; ---------------------------------------------------------------------------------------------

(deftest the-base-pack-works
  (when-not @built-in-keys
    (run-pack! {})
    (reset! built-in-keys (offered-keys)))
  ;; one key per type, so picking a race cannot stand in for the feat of the same key
  (let [keyed (into {} (for [[t m] @base-entries
                             :let [k (keyword (str "junk-" (name t)))]]
                         [t {k (assoc (:junk-base m) :key k)}]))
        failures (run-pack! (library keyed))]
    (cleanup!)
    (is (empty? failures) (pr-str failures))))

(deftest junk-in-any-field-never-throws
  (let [failures
        (doall
         (for [content-type (audit-types)
               v junk
               via [:storage :import :share]
               :let [{:keys [cases entries]} (batch content-type v)
                     fs (run-via! (library entries) via)]
               :when (seq fs)
               ;; one pack per value holds every field: name the field by running each alone
               [k path] cases
               :let [single (run-via! (library {content-type
                                               {k (get-in entries [content-type k])}}) via)]
               :when (seq single)]
           (report! [(name content-type) path v via (first single)])))]
    (cleanup!)
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))

(deftest a-junk-section-never-throws
  (let [failures (doall
                  (for [content-type (audit-types)
                        v [42 "x" true [] [42] #{42}]
                        via [:storage :import :share]
                        :let [fs (run-via! (library {content-type v}) via)]
                        :when (seq fs)]
                    (report! [(name content-type) v via (first fs)])))]
    (cleanup!)
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))
