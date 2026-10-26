(ns orcpub.dnd.e5.junk-pack-test
  "Imported homebrew is untrusted: any field of any entry can hold any shape. One entry of each
   content type (test/fixtures/junk-bases.orcbrew) gets junk in each field, and the pack goes in
   from storage, an import and a share link, then through startup, every subscription, the deep
   check, a character picking each entry, and the pages that draw it. An entry may be set aside
   (only that entry); nothing may throw and no built-in content may go missing. The everyday
   suite runs the base pack only; test/e2e/junk-pack-audit.js runs it all, in a few minutes: one
   pack per junk value holds every type, and a failure is traced to its entry within the run."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.fixtures :refer-macros [fixture-text]]
            [orcpub.entity :as entity]
            [orcpub.common :as common]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [orcpub.dnd.e5.library :as library]
            [orcpub.dnd.e5.options :as opt5e]
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
   them. Without it, none: the everyday suite checks only that the base pack works."
  []
  (let [q (str (.-search js/location))]
    (when-let [[_ named] (re-find #"junk-audit(?:=([a-z,]+))?" q)]
      (if named
        (filter (set (map #(keyword "orcpub.dnd.e5" %) (str/split named #",")))
                (keys @base-entries))
        (keys @base-entries)))))

(defn- my-part
  "This page's share of `jobs` (?part=1/4: the second of four pages run in parallel). Each job,
   heaviest first by `weight`, goes to the page with the least work so far, so the pages finish
   together; every page computes the same split."
  [jobs & [weight]]
  (let [[_ i n] (re-find #"part=(\d+)/(\d+)" (str (.-search js/location)))
        [i n] (if n [(js/parseInt i) (js/parseInt n)] [0 1])
        weight (or weight (constantly 1))
        loads (reduce (fn [loads [idx job]]
                        (let [p (apply min-key #(get-in loads [% :load]) (range n))]
                          (-> loads
                              (update-in [p :load] + (weight job))
                              (update-in [p :jobs] conj [idx job]))))
                      (vec (repeat n {:load 0 :jobs []}))
                      (sort-by (comp - weight second) (map-indexed vector jobs)))]
    (map second (sort-by first (get-in loads [i :jobs])))))

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

(defn- msg [e] (first (str/split-lines (or (ex-message e) (str e)))))

(def ^:private seen
  "What this pack has already realized, by identity. The class list, the class map, the template
   and the template's selections hold the same option objects, and a class's spell options recur
   at every level: walked once each, a homebrew class takes milliseconds instead of seconds."
  (atom (js/Set.)))

(defn- realize!
  "Realize every lazy part of `x`, skipping what this pack has already walked."
  [x]
  (let [s @seen]
    (letfn [(walk [x]
              (when (and (coll? x) (not (.has s x)))
                (.add s x)
                (if (map? x)
                  (doseq [[k v] x] (walk k) (walk v))
                  (doseq [v x] (walk v)))))]
      (walk x))
    x))

(defn- realize-each
  "Realize each item on its own, so one that throws names itself and does not hide the next.
   Returns the failures, each {:key :error}."
  [xs]
  (doall (keep (fn [x] (try (realize! x) nil
                            (catch :default e {:key (or (::t/key x) (:key x)) :error (msg e)})))
               xs)))

(defn- realize-homebrew
  "Realize the homebrew in a subscription's value and leave the built-in content alone: a template
   or a list of selections realizes the homebrew options in each selection, and those in the
   selections of each built-in option (a subrace sits under its race); a map or list realizes its
   homebrew items. Returns the failures, each {:key :error}."
  [v]
  (let [selections (cond (::t/selections v) (::t/selections v)
                         (and (sequential? v) (some ::t/options v)) v)
        items (fn [xs] (mapcat #(if (and (sequential? %) (not (map? %))) % [%]) xs))]
    (cond
      selections
      (doall (mapcat (fn [s]
                       (let [[own built-in] ((juxt filter remove) homebrew? (::t/options s))]
                         (concat (realize-each own)
                                 (realize-each (filter homebrew? (mapcat ::t/options
                                                                         (mapcat ::t/selections
                                                                                 built-in)))))))
                     selections))
      (map? v) (realize-each (filter homebrew? (items (vals v))))
      (sequential? v) (realize-each (filter homebrew? (items v)))
      :else (do (realize! v) nil))))

(def ^:private contained
  "The first error a render-guard caught while the current stage drew: shown on screen, but still
   a failure here, as junk should not reach a page at all (the shape table missed a field)."
  (atom nil))

(defn- attempt [stage f]
  (reset! contained nil)
  (try (f)
       (when-let [e @contained] {:stage stage :error (str "contained: " e)})
       (catch :default e {:stage stage :error (msg e)})))

(def ^:private storage-slots
  "What a pack can leave in browser storage."
  ["plugins" "plugins:rejected" "plugins:corrupt" "plugins:pre-fix" "plugins:pre-fix-at"
   "character"])

(def ^:private coverage
  "What the last pack exercised, by entry key: :picked by a character, :drawn in its builder, and
   the My Content lists :listed. A stage that ran on nothing must not read as a pass."
  (atom {}))

(defn- covered! [stage x] (swap! coverage update stage (fnil conj #{}) x))

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
   repeated so a pick that offers it again (a subrace under a race) is followed. `ks`: a set of
   keys."
  [template c ks]
  (loop [c c i 0]
    (let [[_ avail] (offered template c)
          ;; a single choice holds one option (Race: Human), a multiple one a list of them
          picked? (fn [s k] (let [o (entity/get-option template c (conj (entity/actual-path s) k))]
                              (if (sequential? o)
                                (some #(= k (::entity/key %)) o)
                                (= k (::entity/key o)))))
          [s k] (first (for [s avail
                             k (keep (comp ks ::t/key) (::t/options s))
                             :when (not (picked? s k))]
                         [s k]))]
      (if (or (nil? s) (> i 4))
        c
        (recur (entity/update-option template c (conj (entity/actual-path s) k)
                                     (fn [o] (if (::t/multiselect? s)
                                               (conj (or o []) {::entity/key k})
                                               {::entity/key k})))
               (inc i))))))

(defn- use-entry!
  "Pick homebrew entry `k` in `c`, build it, and draw what the builder draws under it: every
   selection whose path runs through `k`, fully realized. Throws when `k` is not in the character builder."
  [template k c & [entry]]
  (let [ks #{k}
        c (pick template c ks)
        [built avail] (offered template c)]
    (when (and k (not (some ks (tree-seq coll? seq c))))
      (throw (js/Error. "not in the character builder")))
    (when k (covered! :picked k))
    (realize! (char5e/ability-values built))
    (realize! (filter #(some ks (entity/actual-path %)) avail))))

(defn- expand
  "Draw `x` the way Reagent would: a vector headed by a function is a child component, so call it
   (twice for a component that returns its render function) and draw what it returns."
  [x]
  (cond (and (vector? x) (= views/guard-fallback (first x)))
        ;; the fallback a guard draws for what it caught, whichever guard
        (let [[_ data e] x]
          (swap! contained #(or % (msg e)))
          (expand (views/guard-fallback data e)))
        (and (vector? x) (= views/error-boundary (first x)))
        ;; as React does: the child, or the fallback when drawing it throws. What it contains is
        ;; drawn on screen, so it is not a failure
        (let [[_ fallback child] x]
          (try (expand child)
               (catch :default e
                 (swap! contained #(or % (msg e)))
                 (expand (fallback e nil nil)))))
        (and (vector? x) (fn? (first x)))
        (let [[f & args] x
              r (apply f args)]
          (expand (if (fn? r) (apply r args) r)))
        (map? x) x
        (coll? x) (doall (map expand x))
        :else x))

(def ^:private builders
  "Each content type's builder page, and the event that opens an entry in it. Each page is looked
   up when drawn, so a canary can stand in for it."
  {::e5/spells [::spells5e/set-spell #(views/spell-builder)]
   ::e5/monsters [::monsters5e/set-monster #(views/monster-builder)]
   ::e5/encounters [::encounters5e/set-encounter #(views/encounter-builder)]
   ::e5/backgrounds [::bg5e/set-background #(views/background-builder)]
   ::e5/languages [::langs5e/set-language #(views/language-builder)]
   ::e5/invocations [::classes5e/set-invocation #(views/invocation-builder)]
   ::e5/boons [::classes5e/set-boon #(views/boon-builder)]
   ::e5/selections [::selections5e/set-selection #(views/selection-builder)]
   ::e5/feats [::feats5e/set-feat #(views/feat-builder)]
   ::e5/races [::races5e/set-race #(views/race-builder)]
   ::e5/subraces [::races5e/set-subrace #(views/subrace-builder)]
   ::e5/subclasses [::classes5e/set-subclass #(views/subclass-builder)]
   ::e5/classes [::classes5e/set-class #(views/class-builder)]})

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

(def ^:private built
  "A result already found, by stage and what it ran on: an entry that arrives the same by storage,
   import and share link is built, drawn and deep-checked once."
  (atom {}))

(defn- once
  "`(attempt stage f)`, or what it gave the last time it ran on the same `id`."
  [id stage f]
  (if (contains? @built id)
    (get @built id)
    (let [r (attempt stage f)] (swap! built assoc id r) r)))

(defn- traced
  "`failure` from drawing everything, traced to the entries that fail when drawn alone by
   `draw-one` (each [key f]); the failure itself when none does."
  [failure draw-one]
  (when failure
    (or (seq (keep (fn [[k f]] (some-> (attempt (:stage failure) f) (assoc :key k))) draw-one))
        [failure])))

(defn- pages
  "Draw My Content (the page, and each content type's list for the pack), the monster list
   filtered by a name, and, with `builders?`, each entry of `entries` opened in its builder.
   Returns the failures."
  [entries builders?]
  (let [plugin (get-in @app-db [:plugins source])
        type-list (fn [{:keys [type-name type-key icon add-event edit-event delete-event plural]}
                       plugin]
                    #(expand [views/my-content-type source plugin type-name type-key icon add-event
                              edit-event delete-event plural "" true]))]
    (concat
     [(attempt [:page :my-content] #(expand [views/my-content]))]
     (mapcat (fn [{:keys [type-name type-key] :as t}]
               (let [es (get plugin type-key)]
                 (covered! :listed type-name)
                 (traced (attempt [:page :my-content type-name] (type-list t plugin))
                         (when (map? es)
                           (for [[k e] es] [k (type-list t (assoc plugin type-key {k e}))])))))
             views/my-content-types)
     (traced (attempt [:page :monster-list]
                      #(do (rf/dispatch-sync [::char5e/filter-monsters "junk"])
                           (expand [views/monster-list-items])))
             ;; the list is lazy: realize it inside the try, or it throws outside it
             (for [m (try (doall @(rf/subscribe [::monsters5e/filtered-monsters]))
                          (catch :default _ nil))
                   :when (homebrew? m)]
               ;; drawn as the list draws each one, inside its guard
               [(:key m) #(expand [views/render-guard m [views/monster-list-item m]])]))
     (for [[t es] (when builders? entries) :when (map? es)
           :let [[open builder] (builders t)]
           :when open
           [k e] es :when (map? e)]
       (do (covered! :drawn k)
           (some-> (once [:builder (hash e)] [:builder k]
                         #(do (rf/dispatch-sync [open e]) (expand (views/guarded-form e builder))))
                   (assoc :stage [:builder k] :key k)))))))

(defn- run-pack!
  "Start the app with `lib` arriving `via` :storage (the stored library; also what
   LOAD_HOMEBREW_URL writes) or :share (a share link's homebrew), and use it; with `light?`, skip
   the characters and builders. Returns the failures, each {:stage :error}, with :key when the
   failure is traced to an entry."
  [lib & [via {:keys [light?]}]]
  (doseq [k (drafts/draft-keys)] (drafts/remove-draft! k))
  ;; what an earlier pack queued (a write, a set-aside) must not land in this one
  (rf/purge-event-queue)
  (doseq [k storage-slots] (.removeItem js/localStorage k))
  (when (not= via :share) (.setItem js/localStorage "plugins" (pr-str lib)))
  (.setItem js/localStorage "character" (pr-str (char5e/to-strict stored-character)))
  (reset! app-db {})
  (rf/clear-subscription-cache!)
  (reset! seen (js/Set.))
  (reset! coverage {})
  (let [reports (atom [])]
    (guard/call-with-reporter
     #(swap! reports conj %)
     (fn []
       (let [boot (attempt :startup #(rf/dispatch-sync [:initialize-db]))
             shared (when (= via :share)
                      (attempt :share
                               #(rf/dispatch-sync [::e5/apply-shared-content {:plugins lib}])))
             entries (get (if (= via :share) (:shared-plugins @app-db) (:plugins @app-db)) source)
             ;; each stage runs now, in order: what is set aside depends on what ran before
             ;; light: only what a failure can hide in that is not traced to an item (a
             ;; subscription that throws as a whole); the items are traced in the full run
             sub-fails (doall (mapcat (fn [q]
                                        (if light?
                                          (keep #(attempt q (fn [] (dorun @(rf/subscribe %)))) [q])
                                          (let [each (atom nil)]
                                            (or (some-> (attempt q #(reset! each
                                                                            (realize-homebrew
                                                                             @(rf/subscribe q))))
                                                        vector)
                                                (map #(assoc % :stage q) @each)))))
                                      homebrew-subs))
             built-ins (attempt :built-in-content missing-built-ins)
             deep (when-not light?
                    (once [:deep-check (hash entries)] :deep-check
                          #(homebrew-check/deep-check nil)))
             template (try @(rf/subscribe [::char5e/template]) (catch :default _ nil))
             ;; an entry set aside (reported, or in the quarantine panel) is not offered, and is
             ;; allowed, as is one marked as a link to nothing (in My Content, or for a share
             ;; link in its banner); one silently not offered is lost
             set-aside (into (into (set (map :key @reports))
                                   (for [[_ section] (get (:quarantined-plugins @app-db) source)
                                         :when (map? section)
                                         k (keys section)]
                                     k))
                             (when template
                               ;; reads every option, so a throw in one is the stages' to report
                               ;; only a missing parent keeps an entry from being offered
                               (try (map last (library/orphans (library/dangling
                                                     (get @app-db (if (= via :share)
                                                                    :shared-plugins
                                                                    :plugins))
                                                     (library/offered-by-type template))))
                                    (catch :default _ nil))))
             entry (fn [k] (some #(when (map? %) (get % k)) (vals entries)))
             build (fn [[k c]]
                     (let [id [(hash (entry k)) (hash c)]
                           r (if (and k (contains? @built id))
                               (get @built id)
                               (attempt [:character k] #(use-entry! template k c (entry k))))]
                       (when k (swap! built assoc id r))
                       (some-> r (assoc :stage [:character k]) (cond-> k (assoc :key k)))))
             builds (when (and template (not light?))
                      (doall
                       (remove #(and (= "not in the character builder" (:error %)) (set-aside (second (:stage %))))
                               (keep build (cons [nil stored-character] (seeds entries))))))
             drawn (doall (pages entries (not light?)))]
         (doall (remove nil? (concat [boot shared] sub-fails [built-ins deep] builds drawn
                                     (wrong-blame @reports (get lib source))))))))))

(defn- run-via!
  "run-pack! for `lib` arriving `via` :storage, :share, or :import (the file importer's checks
   and clean-up first, then what it would store)."
  [lib via & [opts]]
  (if (= via :import)
    (let [result (atom nil)]
      (or (some-> (attempt :import
                           #(reset! result (orcbrew-val/validate-import
                                            (pr-str lib) {:strategy :progressive :auto-clean true
                                                          :existing-plugins {}
                                                          :import-source-name source})))
                  vector)
          (when-let [data (:data @result)]
            (run-pack! (if (get data source) data {source data}) :storage opts))
          []))
    (run-pack! lib via opts)))

(defn- cleanup! []
  (rf/purge-event-queue)
  (doseq [k storage-slots] (.removeItem js/localStorage k))
  (reset! app-db {})
  (rf/clear-subscription-cache!))

;; ---------------------------------------------------------------------------------------------

(defn- base-pack
  "One clean entry of each type, keyed :junk-<type>, so picking a race cannot stand in for the
   feat of the same key."
  []
  (into {} (for [[t m] @base-entries
                 :let [k (keyword (str "junk-" (name t)))]]
             [t {k (assoc (:junk-base m) :key k)}])))

(def ^:private pickable
  "The types a character picks: each needs a character that offers it."
  #{::e5/classes ::e5/subclasses ::e5/invocations ::e5/boons ::e5/subraces ::e5/races
    ::e5/backgrounds ::e5/feats})

(defn- know-built-ins!
  "What the app offers with no homebrew at all, read once."
  []
  (when-not @built-in-keys
    (run-pack! {})
    (reset! built-in-keys (offered-keys))))

(deftest the-base-pack-works
  (know-built-ins!)
  (let [pack (base-pack)
        failures (run-pack! (library pack))
        {:keys [picked drawn listed]} @coverage
        k #(keyword (str "junk-" (name %)))]
    (cleanup!)
    (is (empty? failures) (pr-str failures))
    (testing "every stage ran on every entry"
      (is (= (set (map k (filter pickable (keys pack)))) picked) "picked by a character")
      (is (= (set (map k (keys pack))) drawn) "opened in its builder")
      (is (= (set (map :type-name views/my-content-types)) listed) "listed in My Content"))))

(defn- canary-run
  "The base pack's failures, with nothing remembered from an earlier run."
  []
  (reset! built {})
  (let [fs (run-pack! (library (base-pack)))]
    (cleanup!)
    fs))

(defn- with-race-option
  "Run `f` with `opt5e/race-option` passing the junk race's option through `change`."
  [change f]
  (let [real opt5e/race-option]
    (with-redefs [opt5e/race-option (fn [& args]
                                      (let [o (apply real args)]
                                        (if (= :junk-races (::t/key o)) (change o) o)))]
      (f))))

(deftest each-check-catches-its-canary
  ;; a check that cannot fail is not a check: each stage is given a failure and must report it
  (know-built-ins!)
  (let [canary #(throw (js/Error. "canary"))
        caught? (fn [fs pred] (boolean (some pred fs)))]
    (testing "a race that throws once it is used, past the guard"
      (is (caught? (with-race-option #(assoc % ::t/selections (lazy-seq (canary))) canary-run)
                   #(and (= :junk-races (:key %)) (= "canary" (:error %))))))
    (testing "a race that silently goes missing"
      (is (caught? (with-race-option (constantly nil) canary-run)
                   #(= {:stage [:character :junk-races] :error "not in the character builder"}
                       (select-keys % [:stage :error])))))
    (testing "an entry set aside that is not the pack's own"
      (is (caught? (with-race-option #(do (guard/report! {:content-type ::e5/races :key :elf}) %)
                                     canary-run)
                   #(= :set-aside (:stage %)))))
    (testing "a builder that throws"
      (with-redefs [views/race-builder (fn [] (canary))]
        (is (caught? (canary-run) #(= [:builder :junk-races] (:stage %))))))
    (testing "a My Content list that throws"
      (with-redefs [views/my-content-type (fn [& _] (canary))]
        (is (caught? (canary-run) #(= [:page :my-content] (take 2 (:stage %)))))))
    (testing "startup that throws"
      (with-redefs [e5/mend-library (fn [_] (canary))]
        (is (caught? (canary-run) #(= :startup (:stage %))))))
    (testing "the deep check that throws"
      (with-redefs [homebrew-check/deep-check (fn [_] (canary))]
        (is (caught? (canary-run) #(= :deep-check (:stage %))))))
    (testing "built-in content that goes missing"
      (let [before @built-in-keys]
        (swap! built-in-keys update :classes conj :canary-class)
        (try (is (caught? (canary-run) #(= :built-in-content (:stage %))))
             (finally (reset! built-in-keys before)))))))

(defn- field-pack
  "Every field of every type's base entry holding each of `vs`: one entry per field and value,
   keyed :junk-<type>-<value>-<field>. Returns {:cases {key [type path value]}
   :entries {type {key entry}}}."
  [types vs]
  (let [cases (into {} (for [t types
                             [j v] (map-indexed vector vs)
                             [i p] (map-indexed vector (field-paths (get-in @base-entries
                                                                            [t :junk-base])))]
                         [(keyword (str "junk-" (name t) "-" j "-" i)) [t p v]]))]
    {:cases cases
     ;; the name is the key's own text; a junk :name stays junk
     :entries (reduce-kv (fn [m k [t p v]]
                           (assoc-in m [t k] (assoc-in (assoc (get-in @base-entries [t :junk-base])
                                                              :key k :name (name k))
                                                       p v)))
                         {} cases)}))

(defn- sub-pack [entries ks]
  (let [ks (set ks)]
    (into {} (for [[t es] entries] [t (into {} (filter (comp ks key)) es)]))))

(defn- untraced
  "The failures in `fs` not traced to an entry in `ks`."
  [fs ks]
  (remove #(contains? ks (:key %)) fs))

(defn- culprits
  "The entries among `ks` whose failure could not be traced in a run of all of them, found by
   halving: [[key failure]]. Two entries that fail only together are returned as one, keyed by
   their first."
  [entries ks via]
  (let [ks (vec ks)
        fs (untraced (run-via! (library (sub-pack entries ks)) via {:light? true}) (set ks))]
    (cond
      (empty? fs) []
      (= 1 (count ks)) [[(first ks) (first fs)]]
      :else (let [half (quot (count ks) 2)
                  found (concat (culprits entries (subvec ks 0 half) via)
                                (culprits entries (subvec ks half) via))]
              (if (seq found) found [[(first ks) (assoc (first fs) :together (count ks))]])))))

(defn- field-job
  "Run the pack of `types`' fields holding each of `vs`, arriving `via`, and return [key failure]
   for each entry that fails: traced within the run, or found by halving when a failure cannot be
   traced (startup, a whole subscription). Entries found that way are taken out and the pack run
   again, since their failure can hide others (a broken class list leaves no template)."
  [types vs via]
  (let [{:keys [cases entries]} (field-pack types vs)]
    (loop [ks (set (keys cases)) found {}]
      (let [fs (run-via! (library (sub-pack entries ks)) via)
            named (into {} (for [f (reverse fs) :when (contains? ks (:key f))] [(:key f) f]))
            found (merge named found)
            left (untraced fs ks)
            loose (when (seq left) (culprits entries ks via))]
        (cond
          (seq loose) (recur (apply disj ks (map first loose)) (into found loose))
          ;; it failed, and failed nowhere when run again: say so rather than drop it
          (seq left) {:cases cases
                      :found (assoc found :pack (assoc (first left) :not-repeated true))}
          :else {:cases cases :found found})))))

(deftest junk-in-any-field-never-throws
  ;; one type per pack, every value in it: entries of other types can stop one being offered,
  ;; and each pack pays for the built-in content once
  ;; a job is one type's values by all three ways in, on one page, so an entry that arrives the
  ;; same each way is drawn and built once; the heaviest types are split in four
  ;; seconds a job took on integration (classes ~240 a group): spreads the work across the pages
  (let [heavy #{::e5/classes ::e5/subclasses ::e5/monsters}
        groups #(if (heavy %) (partition-all 3 junk) [junk])
        weight {::e5/classes 240 ::e5/monsters 65 ::e5/subraces 70 ::e5/subclasses 15 ::e5/races 35
                ::e5/backgrounds 25 ::e5/feats 15}
        failures (doall
                  (for [[t vs] (my-part (for [t (audit-types) vs (groups t)] [t vs])
                                        (fn [[t]] (get weight t 3)))
                        via [:storage :import :share]
                        :let [t0 (js/performance.now)
                              {:keys [cases found]} (field-job [t] vs via)
                              _ (js/console.log "JUNKJOB" (name t) (name via) (count vs) "values"
                                                (int (/ (- (js/performance.now) t0) 1000)) "s")]
                        [k f] (sort-by (comp str key) found)
                        :let [[t path v] (cases k)]]
                    (report! [(if t (name t) "pack") path v via (dissoc f :key)])))]
    (cleanup!)
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))

(deftest a-junk-section-never-throws
  (let [failures (doall
                  (for [content-type (my-part (audit-types))
                        v [42 "x" true [] [42] #{42}]
                        via [:storage :import :share]
                        :let [fs (run-via! (library {content-type v}) via)]
                        :when (seq fs)]
                    (report! [(name content-type) v via (dissoc (first fs) :key)])))]
    (cleanup!)
    (is (empty? failures)
        (str (count failures) " failures\n" (str/join "\n" (map pr-str failures))))))
