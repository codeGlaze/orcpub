(ns orcpub.dnd.e5.events
  (:require [orcpub.entity :as entity]
            [orcpub.entity.strict :as se]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dice :as dice]
            [orcpub.modifiers :as mod]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.common :as common5e]
            [orcpub.dnd.e5.import-validation :as import-val]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.char-decision-tree :as char-dec5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.languages :as langs5e]
            [orcpub.dnd.e5.selections :as selections5e]
            [orcpub.dnd.e5.feats :as feats5e]
            [orcpub.dnd.e5.races :as race5e]
            [orcpub.dnd.e5.classes :as class5e]
            [orcpub.dnd.e5.units :as units5e]
            [orcpub.dnd.e5.party :as party5e]
            [orcpub.dnd.e5.folder :as folder5e]
            [orcpub.dnd.e5.character.random :as char-rand5e]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.encounters :as encounters]
            [orcpub.dnd.e5.combat :as combat]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.event-handlers :as event-handlers]
            [orcpub.dnd.e5.character.equipment :as char-equip5e]
            [orcpub.dnd.e5.db :refer [default-value
                                      character->local-store
                                      user->local-store
                                      magic-item->local-store
                                      spell->local-store
                                      monster->local-store
                                      encounter->local-store
                                      combat->local-store
                                      background->local-store
                                      language->local-store
                                      invocation->local-store
                                      boon->local-store
                                      selection->local-store
                                      feat->local-store
                                      race->local-store
                                      subrace->local-store
                                      subclass->local-store
                                      class->local-store
                                      plugins->local-store
                                      default-character
                                      default-spell
                                      default-monster
                                      default-encounter
                                      default-combat
                                      default-background
                                      default-language
                                      default-invocation
                                      default-boon
                                      default-selection
                                      default-feat
                                      default-race
                                      default-subrace
                                      default-class
                                      default-subclass]]
            [orcpub.dnd.e5.autosave-fx :as autosave-fx]
            [orcpub.dnd.e5.event-utils :as event-utils]
            [orcpub.dnd.e5.compute :as compute]
            [re-frame.core :refer [reg-event-db reg-event-fx reg-fx inject-cofx path
                                   after dispatch ->interceptor]]
            [cljs.spec.alpha :as spec]
            [cljs-http.client :as http]
            [cljs.core.async :refer [<! timeout]]
            [cljs-time.core :as time]
            [cljs.reader :as reader]
            [clojure.string :as s]
            [bidi.bidi :as bidi]
            [orcpub.route-map :as routes]
            [orcpub.errors :as errors]
            [clojure.set :as sets]
            [cljsjs.filesaverjs]
            [clojure.pprint :as pprint])
  (:require-macros [cljs.core.async.macros :refer [go]]))

;; =============================================================================
;; Version: 1.06 - Add export warning modal events, required field validation
;; =============================================================================

;; Forward declaration — defined below :update-value-field (line ~1226).
;; Used in :save-character to auto-generate names for unnamed characters.
(declare generate-random-name)

(defn check-and-throw
  "throw an exception if db doesn't match the spec"
  [a-spec db]
  (when-not (spec/valid? a-spec db)
    (throw (ex-info (str "spec check failed: " (spec/explain-str a-spec db)) {}))))

(def check-spec-interceptor (after (partial check-and-throw ::entity/raw-entity)))

(def ->local-store (after character->local-store))

(def db-char->local-store (after (fn [db] (character->local-store (:character db)))))

(def user->local-store-interceptor (after (fn [db] (user->local-store (:user-data db)))))

(def magic-item->local-store-interceptor (after magic-item->local-store))

(def spell->local-store-interceptor (after spell->local-store))

(def monster->local-store-interceptor (after monster->local-store))

(def encounter->local-store-interceptor (after encounter->local-store))

(def combat->local-store-interceptor (after combat->local-store))

(def background->local-store-interceptor (after background->local-store))

(def language->local-store-interceptor (after language->local-store))

(def invocation->local-store-interceptor (after invocation->local-store))

(def boon->local-store-interceptor (after boon->local-store))

(def selection->local-store-interceptor (after selection->local-store))

(def feat->local-store-interceptor (after feat->local-store))

(def race->local-store-interceptor (after race->local-store))

(def subrace->local-store-interceptor (after subrace->local-store))

(def subclass->local-store-interceptor (after subclass->local-store))

(def class->local-store-interceptor (after class->local-store))

(def plugins->local-store-interceptor (after plugins->local-store))

(def set-changed (->interceptor
                  :id :set-changed
                  :before (fn [context]
                            (assoc-in context [:coeffects :db :character :changed] true))))

(def character-interceptors [check-spec-interceptor
                             set-changed
                             (path :character)
                             ->local-store])


(def item-interceptors [(path ::mi/builder-item)
                        magic-item->local-store-interceptor])

(def spell-interceptors [(path ::spells/builder-item)
                         spell->local-store-interceptor])

(def monster-interceptors [(path ::monsters/builder-item)
                           monster->local-store-interceptor])

(def encounter-interceptors [(path ::encounters/builder-item)
                             encounter->local-store-interceptor])

(def combat-interceptors [(path ::combat/tracker-item)
                         combat->local-store-interceptor])

(def background-interceptors [(path ::bg5e/builder-item)
                              background->local-store-interceptor])

(def language-interceptors [(path ::langs5e/builder-item)
                            language->local-store-interceptor])

(def invocation-interceptors [(path ::class5e/invocation-builder-item)
                              invocation->local-store-interceptor])

(def boon-interceptors [(path ::class5e/boon-builder-item)
                              boon->local-store-interceptor])

(def selection-interceptors [(path ::selections5e/builder-item)
                            selection->local-store-interceptor])

(def feat-interceptors [(path ::feats5e/builder-item)
                         feat->local-store-interceptor])

(def race-interceptors [(path ::race5e/builder-item)
                        race->local-store-interceptor])

(def subrace-interceptors [(path ::race5e/subrace-builder-item)
                           subrace->local-store-interceptor])

(def class-interceptors [(path ::class5e/builder-item)
                         class->local-store-interceptor])

(def subclass-interceptors [(path ::class5e/subclass-builder-item)
                          subclass->local-store-interceptor])

(def plugins-interceptors [(path :plugins)
                          plugins->local-store-interceptor])


;; -- Event Handlers --------------------------------------------------

;; Delegated to event-utils to break circular dep with subs files.
(def backend-url event-utils/backend-url)

(reg-event-fx
 :initialize-db
 [(inject-cofx :local-store-character)
  (inject-cofx :local-store-user)
  (inject-cofx :local-store-magic-item)
  (inject-cofx ::e5/plugins)
  (inject-cofx ::combat/tracker-item)
  check-spec-interceptor]
 (fn [{:keys [db
              local-store-character
              local-store-user
              local-store-magic-item
              ::e5/plugins
              ::combat/tracker-item]} _]
   {:db (if (seq db)
          db
          (cond-> default-value
            plugins (assoc :plugins plugins)
            local-store-character (assoc :character local-store-character)
            local-store-user (update :user-data merge local-store-user)
            local-store-magic-item (assoc ::mi/builder-item local-store-magic-item)
            tracker-item (assoc ::combat/tracker-item tracker-item)))}))

(defn reset-character [_ _]
  (char5e/set-class t5e/character :barbarian 0 (class5e/barbarian-option [] {} {} {} {})))

(reg-event-db
 :reset-character
 character-interceptors
 reset-character)

(reg-event-fx
 ::char5e/clone-character
 (fn [{:keys [db]} _]
   {:dispatch [:set-character (-> :character
                                  db
                                  char5e/to-strict
                                  entity/remove-ids
                                  char5e/from-strict
                                  (update-in
                                   [::entity/values ::char5e/character-name]
                                   (fn [nm]
                                     (str nm " (clone)"))))]}))

(defn random-sequential-selection [built-template character {:keys [::t/min ::t/options ::entity/path] :as selection}]
  (let [num (inc (rand-int (count options)))
        actual-path (entity/actual-path selection)]
    (entity/update-option
     built-template
     character
     actual-path
     (fn [_]
       (mapv
        (fn [{:keys [::t/key]}]
          {::entity/key key})
        (take num options))))))

(defn random-selection [built-template character {:keys [::t/key ::t/min ::t/options ::t/multiselect? ::entity/path] :as selection}]
  (let [built-char (entity/build character built-template)
        new-options (take (entity/count-remaining built-template character selection)
                          (shuffle (filter
                                    (fn [o]
                                      (and (entity/meets-prereqs? o built-char)
                                           (not (#{:none :custom} (::t/key o)))))
                                    options)))]
    (reduce
     (fn [new-character {:keys [::t/key]}]
       (let [new-option {::entity/key key}]
         (entity/update-option
          built-template
          new-character
          (conj (entity/actual-path selection) key)
          (fn [options] (if multiselect? (conj (or options []) new-option) new-option)))))
     character
     (if (and (= :class key) (empty? new-options))
       [{::t/key :fighter}]
       new-options))))

(defn random-hit-points-option [levels class-kw]
  {::entity/key :roll
   ::entity/value (dice/die-roll (-> levels class-kw :hit-die))})

(def selection-randomizers
  {:ability-scores (fn [s _]
                     (fn [_] {::entity/key :standard-roll
                             ::entity/value (char5e/standard-ability-rolls)}))
   :hit-points (fn [{[_ class-kw] ::entity/path} built-char]
                 (fn [_]
                   (random-hit-points-option (char5e/levels built-char) class-kw)))})

#_ ;; unreferenced — random-character loop hardcodes 10
(def max-iterations 100)

(defn keep-options [built-template entity option-paths]
  (reduce
   (fn [new-entity option-path]
     (entity/update-option
      built-template
      new-entity
      option-path
      (fn [_] (entity/get-option built-template entity option-path))))
   {}
   option-paths))


(defn random-character [current-character built-template locked-components]
  (reduce
   (fn [character i]
     (if (< i 10)
       (let [built-char (entity/build character built-template)
             available-selections (entity/available-selections character built-char built-template)
             combined-selections (entity/combine-selections available-selections)
             pending-selections (filter
                                 (fn [{:keys [::entity/path ::t/ref] :as selection}]
                                   (let [remaining (entity/count-remaining built-template character selection)]
                                     (and (pos? remaining)
                                          (not (locked-components path)))))
                                 combined-selections)]
         (if (empty? pending-selections)
           (reduced character)
           (reduce
            (fn [new-character {:keys [::t/key ::t/sequential?] :as selection}]
              (let [selection-randomizer (selection-randomizers key)]
                (if selection-randomizer
                  (let [random-value (selection-randomizer selection)]
                    (entity/update-option
                     built-template
                     new-character
                     (entity/actual-path selection)
                     (selection-randomizer selection built-char)))
                  (if sequential?
                    (random-sequential-selection built-template new-character selection)
                    (random-selection built-template new-character selection)))))
            character
            pending-selections)))
       (reduced character)))
   (let [starting-character (keep-options built-template current-character (conj (vec locked-components) [:optional-content]))]
     starting-character)
   (range)))

(reg-event-fx
 :set-random-character
 (fn [{:keys [db]} [_ character built-template locked-components]]
   {:dispatch [:set-character (random-character character built-template locked-components)]}))

(reg-event-fx
 :random-character
 (fn [_ [_ character built-template locked-components]]
   {:dispatch [:set-random-character character built-template locked-components]}))

#_ ;; unreferenced — character path is constructed inline
(def dnd-5e-characters-path [:dnd :e5 :characters])

(reg-event-fx
 :character-save-success
 (fn [{:keys [db]} [_ response]]
   (let [strict-character (:body response)
         character (char5e/from-strict strict-character)
         id (:db/id character)]
     {:dispatch-n [[:show-message "Your character has been saved."]
                   [:set-character character]
                   [::char5e/set-character id character]]})))

(defn descriptive-character-label
  "Build a descriptive label like 'High Elf Ranger 3' from character properties.
   Used as the summary name when the user hasn't set a character name."
  [race subrace classes levels]
  (let [race-part (or subrace race)
        class-parts (when (seq classes)
                      (s/join "/"
                              (map (fn [cls]
                                     (let [{:keys [class-name class-level]} (levels cls)]
                                       (str class-name " " class-level)))
                                   classes)))]
    (cond
      (and race-part class-parts) (str race-part " " class-parts)
      race-part race-part
      class-parts class-parts
      :else "Adventurer")))

(defn make-summary [built-char]
  (let [classes (char5e/classes built-char)
        levels (char5e/levels built-char)
        race (char5e/race built-char)
        subrace (char5e/subrace built-char)
        character-name (char5e/character-name built-char)
        image-url (char5e/image-url built-char)
        faction-image-url (char5e/faction-image-url built-char)
        age (char5e/age built-char)
        sex (char5e/sex built-char)
        height (char5e/height built-char)
        weight (char5e/weight built-char)
        hair (char5e/hair built-char)
        eyes (char5e/eyes built-char)
        skin (char5e/skin built-char)
        ;; When user hasn't set a name, auto-generate a descriptive label
        ;; for the summary (what lists/parties display).
        display-name (if (s/blank? character-name)
                       (descriptive-character-label race subrace classes levels)
                       character-name)]
    (cond-> {::char5e/character-name display-name}
      image-url (assoc ::char5e/image-url image-url)
      faction-image-url (assoc ::char5e/faction-image-url faction-image-url)
      race (assoc ::char5e/race-name race)
      subrace (assoc ::char5e/subrace-name subrace)
      age (assoc ::char5e/age age)
      sex (assoc ::char5e/sex sex)
      height (assoc ::char5e/height height)
      weight (assoc ::char5e/weight weight)
      hair (assoc ::char5e/hair hair)
      eyes (assoc ::char5e/eyes eyes)
      skin (assoc ::char5e/skin skin)
      ;alignment (assoc ::char5e/alignment alignment) ;This is not available? 
      ;background (assoc ::char5e/background background) ;This is not available? 
      (seq classes) (assoc ::char5e/classes (map
                                             (fn [cls-nm]
                                               (let [{:keys [class-name subclass-name class-level]}
                                                     (levels cls-nm)]
                                                 (cond-> {}
                                                   class-name (assoc ::char5e/class-name class-name)
                                                   subclass-name (assoc ::char5e/subclass-name subclass-name)
                                                   class-level (assoc ::char5e/level class-level))))
                                             classes)))))

(def authorization-headers event-utils/auth-headers)
(def url-for-route event-utils/url-for-route)

;; Autosave handler — dispatched from autosave_fx.cljs throttle timer.
;; Posts character + summary to server.
(reg-event-fx
 ::char5e/save-character
 (fn [{:keys [db]} [_ id]]
   (let [character (get-in db [::char5e/character-map (js/parseInt id)] {})
         ;; Template is cached in app-db by autosave_fx's track! watcher.
         ;; Since built-template is a no-op (plugin merging commented out),
         ;; we use the cached template directly with entity/build.
         cached-template (get db ::autosave-fx/cached-template)]
     (if-not cached-template
       {} ;; template not cached yet — skip this cycle, next autosave will retry
       (let [{:keys [:db/id] :as strict} (char5e/to-strict character)
             built-character (entity/build character cached-template)
             summary (make-summary built-character)]
         (if (every?
              (fn [ability-kw]
                (nat-int? (get-in built-character [:base-abilities ability-kw])))
              char5e/ability-keys)
           {:dispatch [:set-loading true]
            :http {:method :post
                   :headers (authorization-headers db)
                   :url (url-for-route routes/dnd-e5-char-list-route)
                   :transit-params (assoc strict :orcpub.entity.strict/summary summary)
                   :on-success [:character-save-success]}}
           {:dispatch [:show-error-message "You must provide values for all ability scores"]}))))))

;; Manual save — dispatched from character builder UI with built-char in scope.
;; If the user hasn't set a name, generates a random one and persists it.
(reg-event-fx
 :save-character
 (fn [{:keys [db]} [_ built-character]]
   (let [character-name (char5e/character-name built-character)
         needs-name? (s/blank? character-name)
         ;; Generate a random name for unnamed characters on manual save
         rand-name (when needs-name? (generate-random-name built-character))
         ;; Update entity in db so the name persists across future edits
         db' (if needs-name?
               (assoc-in db [:character ::entity/values ::char5e/character-name] rand-name)
               db)
         {:keys [:db/id] :as strict} (char5e/to-strict (:character db'))
         summary (cond-> (make-summary built-character)
                   ;; Override summary name with the generated name
                   ;; (make-summary produced a descriptive label since entity was blank)
                   needs-name? (assoc ::char5e/character-name rand-name))]
     (if (every?
          (fn [ability-kw]
            (nat-int? (get-in built-character [:base-abilities ability-kw])))
          char5e/ability-keys)
       {:db db'
        :dispatch [:set-loading true]
        :http {:method :post
               :headers (authorization-headers db')
               :url (url-for-route routes/dnd-e5-char-list-route)
               :transit-params (assoc strict :orcpub.entity.strict/summary summary)
               :on-success [:character-save-success]}}
       {:dispatch [:show-error-message "You must provide values for all ability scores"]}))))

(reg-event-fx
 :item-save-success
 (fn [{:keys [db]} [_ response]]
   (let [strict-item (:body response)
         item (mi/to-internal-item strict-item)
         item-id (:db/id strict-item)
         existing-items (::mi/custom-items db)
         updated-items (if (some #(= item-id (:db/id %)) existing-items)
                         (mapv #(if (= item-id (:db/id %)) strict-item %) existing-items)
                         (conj (vec existing-items) strict-item))]
     {:db (assoc db ::mi/custom-items updated-items)
      :dispatch-n [[:show-message "Your item has been saved."]
                   [::mi/set-item item]]})))

(reg-event-fx
 ::mi/save-item
 (fn [{:keys [db]} _]
   (let [strict-item (mi/from-internal-item (::mi/builder-item db))]
     {:dispatch [:set-loading true]
      :http {:method :post
             :headers (authorization-headers db)
             :url (url-for-route routes/dnd-e5-items-route)
             :transit-params strict-item
             :on-success [:item-save-success]}})))

(defn spec-error-message
  "Extract specific field names from a spec explanation to produce a targeted
   error message. Falls back to the static message if parsing fails."
  [type-name explanation fallback-message]
  (if-let [problems (::spec/problems explanation)]
    (let [contains-syms #{'cljs.core/contains? 'clojure.core/contains?}
          missing-fields (->> problems
                              (keep (fn [{:keys [pred]}]
                                      (when (and (sequential? pred)
                                                 (contains-syms (first pred)))
                                        (name (last pred)))))
                              distinct)]
      (if (seq missing-fields)
        (str type-name " is missing required fields: " (s/join ", " missing-fields))
        fallback-message))
    fallback-message))

(defn reg-save-homebrew [type-name
                         event-key
                         item-key
                         spec-key
                         plugin-key
                         error-message]
  (reg-event-fx
   event-key
   (fn [{:keys [db]} _]
     (let [{:keys [name option-pack] :as item} (item-key db)
           key (common/name-to-kw name)
           ;; Normalize text then auto-fill missing required fields
           normalized-item (import-val/normalize-text-in-data item)
           {filled-item :item} (import-val/fill-all-missing-fields normalized-item plugin-key)
           item-with-key (assoc filled-item :key key)
           plugins (:plugins db)
           explanation (spec/explain-data spec-key item-with-key)]
       (if (nil? explanation)
         (let [new-plugins (assoc-in plugins
                                     [option-pack plugin-key key]
                                     item-with-key)]
           {:dispatch-n [[::e5/set-plugins new-plugins]
                         [:show-warning-message
                          [:div [:span.f-w-b.f-s-18.red "IMPORTANT!: "]
                           [:span.text-shadow
                            (str type-name " saved to your browser which could be lost if you clear your browser history or your browser storage fill up, you MUST export and save the content source by clicking ")]
                           [:span.pointer.underline.black
                            {:on-click #(dispatch [::e5/export-plugin option-pack (str (new-plugins option-pack))])}
                            "here"]]
                          60000]]})
         {:dispatch [:show-error-message (spec-error-message type-name explanation error-message)]})))))

(reg-save-homebrew
 "Spell"
 ::spells/save-spell
 ::spells/builder-item
 ::spells/homebrew-spell
 ::e5/spells
 "You must specify 'Name', 'Option Source Name', and at select at least one class in 'Class Spell Lists'")

(reg-save-homebrew
 "Monster"
 ::monsters/save-monster
 ::monsters/builder-item
 ::monsters/homebrew-monster
 ::e5/monsters
 "You must specify 'Name', 'Option Source Name', 'Hit Points Die Count', and 'Hit Points Die'")

(reg-save-homebrew
 "Encounter"
 ::encounters/save-encounter
 ::encounters/builder-item
 ::encounters/encounter
 ::e5/encounters
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Background"
 ::bg5e/save-background
 ::bg5e/builder-item
 ::bg5e/homebrew-background
 ::e5/backgrounds
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Language"
 ::langs5e/save-language
 ::langs5e/builder-item
 ::langs5e/homebrew-language
 ::e5/languages
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Invocation"
 ::class5e/save-invocation
 ::class5e/invocation-builder-item
 ::class5e/homebrew-invocation
 ::e5/invocations
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Boon"
 ::class5e/save-boon
 ::class5e/boon-builder-item
 ::class5e/homebrew-boon
 ::e5/boons
 "You must specify 'Name', 'Option Source Name'")

;; Selection save handler — standalone instead of reg-save-homebrew to add
;; duplicate option name validation. Mirrors reg-save-homebrew logic plus
;; checks for empty names and duplicate option names within :options.
(reg-event-fx
 ::selections5e/save-selection
 (fn [{:keys [db]} _]
   (let [{:keys [name option-pack] :as item} (::selections5e/builder-item db)
         key (common/name-to-kw name)
         normalized-item (import-val/normalize-text-in-data item)
         {filled-item :item} (import-val/fill-all-missing-fields normalized-item ::e5/selections)
         item-with-key (assoc filled-item :key key)
         plugins (:plugins db)
         explanation (spec/explain-data ::selections5e/homebrew-selection item-with-key)
         ;; Check for empty option names
         option-names (map :name (:options item))
         empty-names? (some s/blank? option-names)
         ;; Check for duplicate option names (case-insensitive via key derivation)
         option-keys (map #(when-not (s/blank? %) (common/name-to-kw %))
                          option-names)
         key-freqs (frequencies (remove nil? option-keys))
         dupe-keys (set (map first (filter #(> (val %) 1) key-freqs)))
         dupe-names (when (seq dupe-keys)
                      (->> option-names
                           (filter #(and (not (s/blank? %))
                                         (contains? dupe-keys (common/name-to-kw %))))
                           distinct
                           sort))]
     (cond
       ;; Reject empty option names
       empty-names?
       {:dispatch [:show-error-message
                   "Cannot save: all options must have names"]}
       ;; Reject duplicate option names
       (seq dupe-names)
       {:dispatch [:show-error-message
                   (str "Cannot save: duplicate option names: "
                        (s/join ", " dupe-names)
                        ". Each option must have a unique name.")]}
       ;; Spec validation
       (some? explanation)
       {:dispatch [:show-error-message
                   (spec-error-message "Selection" explanation
                                       "You must specify 'Name', 'Option Source Name'")]}
       ;; All good — save
       :else
       (let [new-plugins (assoc-in plugins
                                   [option-pack ::e5/selections key]
                                   item-with-key)]
         {:dispatch-n [[::e5/set-plugins new-plugins]
                       [:show-warning-message
                        [:div [:span.f-w-b.f-s-18.red "IMPORTANT!: "]
                         [:span.text-shadow
                          "Selection saved to your browser which could be lost if you clear your browser history or your browser storage fill up, you MUST export and save the content source by clicking "]
                         [:span.pointer.underline.black
                          {:on-click #(dispatch [::e5/export-plugin option-pack (str (new-plugins option-pack))])}
                          "here"]]
                        60000]]})))))

(reg-save-homebrew
 "Feat"
 ::feats5e/save-feat
 ::feats5e/builder-item
 ::feats5e/homebrew-feat
 ::e5/feats
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Race"
 ::race5e/save-race
 ::race5e/builder-item
 ::race5e/homebrew-race
 ::e5/races
 "You must specify 'Name', 'Option Source Name'")

(reg-save-homebrew
 "Subrace"
 ::race5e/save-subrace
 ::race5e/subrace-builder-item
 ::race5e/homebrew-subrace
 ::e5/subraces
 "You must specify 'Name', 'Option Source Name', and 'Race'")

(reg-save-homebrew
 "Subclass"
 ::class5e/save-subclass
 ::class5e/subclass-builder-item
 ::class5e/homebrew-subclass
 ::e5/subclasses
 "You must specify 'Name', 'Option Source Name', and 'Class'")

(reg-save-homebrew
 "Class"
 ::class5e/save-class
 ::class5e/builder-item
 ::class5e/homebrew-class
 ::e5/classes
 "You must specify 'Name', 'Option Source Name'")

(defn reg-delete-homebrew [event-key plugin-key]
  (reg-event-fx
   event-key
   (fn [{:keys [db]} [_ {:keys [key option-pack]}]]
     {:dispatch [::e5/set-plugins (update-in (:plugins db) [option-pack plugin-key] dissoc key)]})))

(reg-delete-homebrew
 ::spells/delete-spell
 ::e5/spells)

(reg-delete-homebrew
 ::monsters/delete-monster
 ::e5/monsters)

(reg-delete-homebrew
 ::encounters/delete-encounter
 ::e5/encounters)

(reg-delete-homebrew
 ::bg5e/delete-background
 ::e5/backgrounds)

(reg-delete-homebrew
 ::langs5e/delete-language
 ::e5/languages)

(reg-delete-homebrew
 ::class5e/delete-invocation
 ::e5/invocations)

(reg-delete-homebrew
 ::class5e/delete-boon
 ::e5/boons)

(reg-delete-homebrew
 ::selections5e/delete-selection
 ::e5/selections)

(reg-delete-homebrew
 ::feats5e/delete-feat
 ::e5/feats)

(reg-delete-homebrew
 ::race5e/delete-race
 ::e5/races)

(reg-delete-homebrew
 ::race5e/delete-subrace
 ::e5/subraces)

(reg-delete-homebrew
 ::class5e/delete-subclass
 ::e5/subclasses)

(reg-delete-homebrew
 ::class5e/delete-class
 ::e5/classes)

(reg-event-fx
 ::party5e/make-party-success
 (fn []
   {:dispatch [:show-message [:div
                              "Your party has been created. View it on the "
                              [:span.underline.pointer.orange
                               {:on-click #(dispatch [:route routes/dnd-e5-char-parties-page-route])}
                               "Parties Page"]]]}))

(reg-event-fx
 ::party5e/make-party
 (fn [{:keys [db]} [_ character-ids]]
   {:dispatch [:set-loading true]
    :http {:method :post
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-parties-route)
           :transit-params {::party5e/name "A New Party"
                            ::party5e/character-ids character-ids}
           :on-success [::party5e/make-party-success]}}))

(reg-event-fx
 ::party5e/make-empty-party-success
 (fn [{:keys [db]} [_ response]]
   (let [new-party (:body response)
         parties (conj (vec (::char5e/parties db)) new-party)]
     {:db (assoc db
                 ::char5e/parties parties
                 ::char5e/parties-map (common/map-by-id parties))})))

(reg-event-fx
  ::party5e/make-empty-party
  (fn [{:keys [db]} [_]]
    {:dispatch [:set-loading true]
     :http {:method :post
            :headers (authorization-headers db)
            :url (url-for-route routes/dnd-e5-char-parties-route)
            :transit-params {::party5e/name "A New Party"}
            :on-success [::party5e/make-empty-party-success]}}))

(reg-event-fx
 ::party5e/rename-party
 (fn [{:keys [db]} [_ id new-name]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (assoc party ::party5e/name new-name)
                party))
            parties)))
    :http {:method :put
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-name-route :id id)
           :transit-params new-name}}))

(reg-event-fx
 ::party5e/delete-party
 (fn [{:keys [db]} [_ id new-name]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (remove
            (fn [party]
              (= id (:db/id party)))
            parties)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-route :id id)}}))

(reg-event-fx
 ::mi/delete-custom-item-success
 (fn [_ _]
   {:dispatch [:route routes/dnd-e5-item-list-page-route]}))

(reg-event-fx
 ::mi/delete-custom-item
 (fn [{:keys [db]} [_ id]]
   {:db (update
         db
         ::mi/custom-items
         (fn [custom-items]
           (remove
            (fn [item]
              (= id (:db/id item)))
            custom-items)))
    :dispatch [::mi/delete-custom-item-success]
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-item-route :id id)}}))

(reg-event-fx
 ::party5e/remove-character
 (fn [{:keys [db]} [_ id character-id]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (update
                 party
                 ::party5e/character-ids
                 (fn [character-ids]
                   (remove
                    (fn [{:keys [:db/id]}]
                      (= character-id id))
                    character-ids)))
                party))
            parties)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-party-character-route :id id :character-id character-id)}}))

(reg-event-fx
 ::party5e/add-character-remote-success
 (fn [_ [_ show-confirmation?]]
   (when show-confirmation?
     {:dispatch [:show-message [:div
                                "Character has been added to the party. View it on the "
                                [:span.underline.pointer.orange
                                 {:on-click #(dispatch [:route routes/dnd-e5-char-parties-page-route])}
                                 "Parties Page"]]]})))

(reg-event-fx
 ::party5e/add-character-remote
 (fn [{:keys [db]} [_ id character-id show-confirmation?]]
   {:http {:method :post
           :headers (authorization-headers db)
           :transit-params character-id
           :url (url-for-route routes/dnd-e5-char-party-characters-route :id id)
           :on-success [::party5e/add-character-remote-success show-confirmation?]}}))

(reg-event-fx
 ::party5e/add-character
 (fn [{:keys [db]} [_ id character-id show-confirmation?]]
   {:db (update
         db
         ::char5e/parties
         (fn [parties]
           (map
            (fn [party]
              (if (= id (:db/id party))
                (update
                 party
                 ::party5e/character-ids
                 conj
                 (get-in db [::char5e/summary-map character-id]))
                party))
            parties)))
    :dispatch [::party5e/add-character-remote id character-id show-confirmation?]}))

;; ---- Folder Events -------------------------------------------------------

(reg-event-db
 ::folder5e/set-folders
 (fn [db [_ folders]]
   (assoc db ::folder5e/folders folders)))

(reg-event-fx
 ::folder5e/on-folder-failure
 ;; Re-fetches folders from server to reconcile optimistic UI on HTTP failure.
 (fn [{:keys [db]} [_ _response]]
   {:http {:method :get
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folders-route)
           :on-success [::folder5e/set-folders-from-response]}
    :dispatch (event-utils/show-generic-error)}))

(reg-event-db
 ::folder5e/set-folders-from-response
 (fn [db [_ response]]
   (assoc db ::folder5e/folders (:body response))))

(reg-event-fx
 ::folder5e/create-folder
 (fn [{:keys [db]} [_]]
   {:http {:method :post
           :headers (authorization-headers db)
           :transit-params {::folder5e/name "New Folder"}
           :url (url-for-route routes/dnd-e5-char-folders-route)
           :on-success [::folder5e/create-folder-success]
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/create-folder-success
 (fn [{:keys [db]} [_ response]]
   (let [folder (:body response)]
     {:db (update db ::folder5e/folders conj folder)
      :dispatch [::folder5e/toggle-renaming (:db/id folder)]})))

(reg-event-fx
 ::folder5e/rename-folder
 (fn [{:keys [db]} [_ id new-name]]
   (let [trimmed (clojure.string/trim (str new-name))]
     (when-not (clojure.string/blank? trimmed)
       {:db (update db ::folder5e/folders
                    (fn [folders]
                      (map (fn [f]
                             (if (= id (:db/id f))
                               (assoc f ::folder5e/name trimmed)
                               f))
                           folders)))
        :http {:method :put
               :headers (authorization-headers db)
               :transit-params trimmed
               :url (url-for-route routes/dnd-e5-char-folder-name-route :id id)
               :on-failure [::folder5e/on-folder-failure]}}))))

(reg-event-fx
 ::folder5e/delete-folder
 (fn [{:keys [db]} [_ id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (remove #(= id (:db/id %)) folders)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folder-route :id id)
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/add-character
 (fn [{:keys [db]} [_ folder-id character-id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (map (fn [f]
                         (if (= folder-id (:db/id f))
                           (update f ::folder5e/character-ids
                                   conj
                                   (get-in db [::char5e/summary-map character-id]))
                           ;; remove from any other folder (at-most-one constraint)
                           (update f ::folder5e/character-ids
                                   (fn [chars]
                                     (remove #(= character-id (:db/id %)) chars)))))
                       folders)))
    :http {:method :post
           :headers (authorization-headers db)
           :transit-params character-id
           :url (url-for-route routes/dnd-e5-char-folder-characters-route :id folder-id)
           :on-failure [::folder5e/on-folder-failure]}}))

(reg-event-fx
 ::folder5e/remove-character
 (fn [{:keys [db]} [_ folder-id character-id]]
   {:db (update db ::folder5e/folders
                (fn [folders]
                  (map (fn [f]
                         (if (= folder-id (:db/id f))
                           (update f ::folder5e/character-ids
                                   (fn [chars]
                                     (remove #(= character-id (:db/id %)) chars)))
                           f))
                       folders)))
    :http {:method :delete
           :headers (authorization-headers db)
           :url (url-for-route routes/dnd-e5-char-folder-character-route
                               :id folder-id
                               :character-id character-id)
           :on-failure [::folder5e/on-folder-failure]}}))

;; When expanding a folder, collapse its characters so they start closed
(reg-event-db
 ::folder5e/toggle-expanded
 (fn [db [_ folder-id char-ids]]
   (let [opening? (not (get-in db [::folder5e/expanded folder-id]))]
     (cond-> (update-in db [::folder5e/expanded folder-id] not)
       (and opening? (seq char-ids))
       (update :expanded-characters
               (fn [ec] (apply dissoc (or ec {}) char-ids)))))))

(reg-event-db
 ::folder5e/toggle-renaming
 (fn [db [_ folder-id]]
   (update-in db [::folder5e/renaming folder-id] not)))

;; ---- End Folder Events ----------------------------------------------------

;; ---- Character Filter Events -----------------------------------------------

(reg-event-db
 ::char5e/set-char-name-filter
 (fn [db [_ v]]
   (assoc db ::char5e/char-name-filter v)))

(reg-event-db
 ::char5e/toggle-char-level-filter
 (fn [db [_ level]]
   (let [filters (or (get db ::char5e/char-level-filters) #{})]
     (assoc db ::char5e/char-level-filters
            ((if (filters level) disj conj) filters level)))))

(reg-event-db
 ::char5e/toggle-char-class-filter
 (fn [db [_ cls]]
   (let [filters (or (get db ::char5e/char-class-filters) #{})]
     (assoc db ::char5e/char-class-filters
            ((if (filters cls) disj conj) filters cls)))))

(reg-event-db
 ::char5e/toggle-char-has-portrait
 (fn [db _]
   (update db ::char5e/char-has-portrait?
           #(case % nil true, true false, false nil))))

(reg-event-db
 ::char5e/toggle-char-has-faction-pic
 (fn [db _]
   (update db ::char5e/char-has-faction-pic?
           #(case % nil true, true false, false nil))))

(reg-event-db
 ::char5e/clear-char-filters
 (fn [db _]
   (dissoc db
           ::char5e/char-name-filter
           ::char5e/char-level-filters
           ::char5e/char-class-filters
           ::char5e/char-has-portrait?
           ::char5e/char-has-faction-pic?)))

;; ---- End Character Filter Events -------------------------------------------

(reg-event-fx
 :follow-user-success
 (fn []))

(reg-event-fx
 :follow-user
 (fn [{:keys [db]} [_ username]]
   (let [path (routes/path-for routes/follow-user-route :user username)]
     {:dispatch [:set-user (update (:user db) :following conj username)]
      :http {:method :post
             :headers (authorization-headers db)
             :url (backend-url path)
             :on-success [:follow-user-success]}})))

(reg-event-fx
 :unfollow-user-success
 (fn []))

(reg-event-fx
 :delete-account
 (fn [{:keys [db]} _]
   (let [path (routes/path-for routes/user-route)]
     {:dispatch-n [[:logout]
                   [:new-character]
                   [:route routes/dnd-e5-char-builder-route]]
      :http {:method :delete
             :headers (authorization-headers db)
             :url (backend-url path)}})))

(reg-event-fx
 :change-email
 (fn [{:keys [db]} [_ new-email]]
   {:db (dissoc db :email-change-sent? :email-change-error)
    :http {:method :put
           :headers (authorization-headers db)
           :url (backend-url (routes/path-for routes/user-email-route))
           :transit-params {:new-email new-email}
           :on-success [:change-email-success]
           :on-failure [:change-email-failure]}}))

(reg-event-db
 :change-email-success
 (fn [db [_ response]]
   (-> db
       (assoc :email-change-sent? true)
       ;; Use server-canonical (lowercased/trimmed) email for display
       (assoc-in [:user-data :user-data :pending-email]
                 (-> response :body :pending-email)))))

(reg-event-db
 :change-email-failure
 (fn [db [_ response]]
   (let [body (:body response)
         error (:error body)]
     (assoc db :email-change-error
            (case error
              :email-taken "That email address is already in use by another account."
              :invalid-email "Please enter a valid email address."
              :same-as-current "That is already your current email address."
              :too-many-requests
              (let [secs (:retry-after-secs body)]
                (if (and secs (pos? secs))
                  (if (<= secs 60)
                    ;; 0–1 min zone: email is in transit, show short countdown
                    (str "Your email is on its way. You can resend in " secs " second" (when (> secs 1) "s") ".")
                    ;; 1–5 min zone for a different email: show minutes
                    (let [mins (.ceil js/Math (/ secs 60))]
                      (str "Please wait " mins " minute" (when (> mins 1) "s") " before requesting another change.")))
                  "Please wait a few minutes before requesting another email change."))
              :email-send-failed "Verification email could not be sent. Please try again later."
              "There was an error updating your email. Please try again.")))))

(reg-event-db
 :change-email-clear
 (fn [db _]
   (dissoc db :email-change-sent? :email-change-error)))

(reg-event-fx
 :unfollow-user
 (fn [{:keys [db]} [_ username]]
   (let [path (routes/path-for routes/follow-user-route :user username)]
     {:dispatch-n [[:set-user (update (:user db) :following #(remove (partial = username) %))]
                   [::char5e/remove-user-characters username]]
      :http {:method :delete
             :headers (authorization-headers db)
             :url (backend-url path)
             :on-success [:unfollow-user-success]}})))

(defn set-character [db [_ character]]
  (assoc db :character character :loading false))

(reg-event-db
 :toggle-character-expanded
 (fn [db [_ character-id]]
   (update-in db [:expanded-characters character-id] not)))

(reg-event-db
 :toggle-monster-expanded
 (fn [db [_ monster-name]]
   (update-in db [:expanded-monsters monster-name] not)))

(reg-event-db
 :toggle-spell-expanded
 (fn [db [_ spell-name]]
   (update-in db [:expanded-spells spell-name] not)))

(reg-event-db
 :toggle-item-expanded
 (fn [db [_ item-name]]
   (update-in db [:expanded-items item-name] not)))

(reg-event-db
 :set-character
 [db-char->local-store]
 set-character)

(def character-values-path
  [::entity/values])

(defn character-value-path [prop-name]
  (conj character-values-path prop-name))

(defn update-value-field [character [_ prop-name value]]
  (assoc-in character (character-value-path prop-name) value))

(reg-event-db
 :update-value-field
 character-interceptors
 update-value-field)

(defn generate-random-name
  "Generate a random name from the built character's race/subrace/sex.
   Falls back to a random human name for unsupported or custom races."
  [built-char]
  (let [race-kw (common/name-to-kw (char5e/race built-char) "orcpub.dnd.e5.character.random")
        subrace-kw (common/name-to-kw (char5e/subrace built-char) "orcpub.dnd.e5.character.random")
        sex-kw (common/name-to-kw (char5e/sex built-char) "orcpub.dnd.e5.character.random")]
    (:name (char-rand5e/random-name-result
            {:race race-kw
             :subrace (when (= ::char-rand5e/human race-kw) subrace-kw)
             :sex sex-kw}))))

;; Generate a random name based on character's race/subrace/sex.
;; built-char passed from component (description-fields).
(reg-event-fx
 ::char5e/set-random-name
 (fn [_ [_ built-char]]
   {:dispatch [:update-value-field ::char5e/character-name
               (generate-random-name built-char)]}))
(reg-event-db
 :select-option
 character-interceptors
 event-handlers/select-option)

(defn add-class [character [_ first-unselected]]
  (update-in
   character
   [::entity/options :class]
   conj
   {::entity/key first-unselected ::entity/options {:levels [{::entity/key :level-1}]}}))

(reg-event-db
 :add-class
 character-interceptors
 add-class)

(reg-event-db
 :set-image-url
 character-interceptors
 (fn [character [_ image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/image-url
           image-url
           ::char5e/image-url-failed
           nil)))

#_ ;; never dispatched from UI
(reg-event-db
 :toggle-public
 character-interceptors
 (fn [character _]
   (update character
           ::entity/values
           update
           ::char5e/share?
           not)))

(reg-event-db
 :set-faction-image-url
 character-interceptors
 (fn [character [_ faction-image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/faction-image-url
           faction-image-url
           ::char5e/faction-image-url-failed
           nil)))

(reg-event-db
 :add-background-starting-equipment
 character-interceptors
 event-handlers/add-background-starting-equipment)

(reg-event-db
 :set-class
 character-interceptors
 event-handlers/set-class)

(reg-event-db
 :set-class-level
 character-interceptors
 event-handlers/set-class-level)

(defn delete-class [character [_ class-key i options-map]]
  (let [updated (update-in
                 character
                 [::entity/options :class]
                 (fn [classes] (vec (remove #(= class-key (::entity/key %)) classes))))
        new-first-class-key (get-in updated [::entity/options :class 0 ::entity/key])
        new-first-class-option (when new-first-class-key (options-map new-first-class-key))]
    (if (and (zero? i)
             new-first-class-option)
      (char5e/set-class updated new-first-class-key 0 new-first-class-option)
      updated)))

(reg-event-db
 :delete-class
 character-interceptors
 delete-class)

(reg-event-db
 :add-inventory-item
 character-interceptors
 (fn [character [_ selection-key item-key]]
   (event-handlers/add-inventory-item character selection-key item-key)))

(defn toggle-inventory-item-equipped [character [_ selection-key item-index]]
  (update-in
   character
   [::entity/options selection-key item-index ::entity/value ::char-equip5e/equipped?]
   not))

(reg-event-db
 :toggle-inventory-item-equipped
 character-interceptors
 toggle-inventory-item-equipped)

(defn toggle-custom-inventory-item-equipped [character [_ custom-equipment-key item-index]]
  (update-in
   character
   [::entity/values custom-equipment-key item-index ::char-equip5e/equipped?]
   not))

(reg-event-db
 :toggle-custom-inventory-item-equipped
 character-interceptors
 toggle-custom-inventory-item-equipped)

(defn change-inventory-item-quantity [character [_ selection-key item-index quantity]]
  (update-in
   character
   [::entity/options selection-key item-index ::entity/value]
   (fn [item-cfg]
     ;; the select keys here is to keep :equipped while wiping out the starting-equipment indicators
     (assoc (select-keys item-cfg [::char-equip5e/equipped?]) ::char-equip5e/quantity quantity))))

(reg-event-db
 :change-inventory-item-quantity
 character-interceptors
 change-inventory-item-quantity)

(defn change-custom-inventory-item-quantity [character [_ custom-equipment-key item-index quantity]]
  (update-in
   character
   [::entity/values custom-equipment-key item-index]
   (fn [item-cfg]
     ;; the select keys here is to keep :equipped and :name while wiping out the starting-equipment indicators
     (assoc
      (select-keys item-cfg [::char-equip5e/name ::char-equip5e/equipped?])
      ::char-equip5e/quantity
      quantity))))

(reg-event-db
 :change-custom-inventory-item-quantity
 character-interceptors
 change-custom-inventory-item-quantity)

(reg-event-db
 :remove-inventory-item
 character-interceptors
 event-handlers/remove-inventory-item)

(defn remove-custom-inventory-item [character [_ custom-equipment-key name]]
  (update-in
   character
   [::entity/values custom-equipment-key]
   (fn [items]
     (vec (remove #(= name (::char-equip5e/name %)) items)))))

(reg-event-db
 :remove-custom-inventory-item
 character-interceptors
 remove-custom-inventory-item)

(defn set-abilities [character [_ abilities]]
  (assoc-in character [::entity/options :ability-scores ::entity/value] abilities))

(reg-event-db
 :set-abilities
 character-interceptors
 set-abilities)

(defn swap-ability-values [character [_ i other-i k v]]
  (update-in
   character
   [::entity/options :ability-scores ::entity/value]
   (fn [a]
     (let [a-vec (vec (map (fn [k] [k (k a)]) char5e/ability-keys))
           other-index (mod other-i (count a-vec))
           [other-k other-v] (a-vec other-index)]
       (assoc a k other-v other-k v)))))

(reg-event-db
 :swap-ability-values
 character-interceptors
 swap-ability-values)

(defn decrease-ability-value [character [_ full-path k]]
  (update-in
   character
   full-path
   (fn [incs]
     (common/remove-first
      (fn [{inc-key ::entity/key}]
        (= inc-key k))
      incs))))

(reg-event-db
 :decrease-ability-value
 character-interceptors
 decrease-ability-value)

(defn increase-ability-value [character [_ full-path k]]
  (update-in
   character
   full-path
   conj
   {::entity/key k}))

(reg-event-db
 :increase-ability-value
 character-interceptors
 increase-ability-value)

(defn set-ability-score [character [_ ability-kw v]]
  (assoc-in character [::entity/options :ability-scores ::entity/value ability-kw] v))

(reg-event-db
 :set-ability-score
 character-interceptors
 set-ability-score)

(defn set-ability-score-variant [character [_ variant-key]]
  (assoc-in character [::entity/options :ability-scores ::entity/key] variant-key))

(reg-event-db
 :set-ability-score-variant
 character-interceptors
 set-ability-score-variant)

(defn select-skill [character [_ path selected? skill-key]]
  (update-in
   character
   path
   (fn [skills]
     (if selected?                                             
       (vec (remove (fn [s] (= skill-key (::entity/key s))) skills))
       (vec (conj skills {::entity/key skill-key}))))))

(reg-event-db
 :select-skill
 character-interceptors
 select-skill)

(defn set-total-hps [character [_ full-path first-selection selection average-value remainder]]
  (assoc-in
   character
   full-path
   {::entity/key :manual-entry
    ::entity/value (if (= first-selection selection)
                     (+ average-value remainder)
                     average-value)}))

(reg-event-db
 :set-total-hps
 character-interceptors
 set-total-hps)

(defn randomize-hit-points [character [_ built-template path levels class-kw]]
  (assoc-in
   character
   (entity/get-entity-path built-template character path)
   (random-hit-points-option levels class-kw)))

(reg-event-db
 :randomize-hit-points
 character-interceptors
 randomize-hit-points)

(defn set-hit-points-to-average [character [_ built-template path levels class-kw]]
  (assoc-in
   character
   (entity/get-entity-path built-template character path)
   {::entity/key :average
    ::entity/value (dice/die-mean-round-up (-> levels class-kw :hit-die))}))

(reg-event-db
 :set-hit-points-to-average
 character-interceptors
 set-hit-points-to-average)

#_:clj-kondo/ignore
(defn set-level-hit-points [character [_ built-template character level-value value]]
  (assoc-in
   character
   (entity/get-entity-path built-template character (:path level-value))
   {::entity/key :manual-entry
    ::entity/value (when (not (js/isNaN value)) value)}))

(reg-event-db
 :set-level-hit-points
 character-interceptors
 set-level-hit-points)

(defn set-page [db [_ page-index]]
  (assoc db :page page-index))

(reg-event-db
 :set-page
 set-page)

(defn make-url [protocol hostname path & [port]]
  (str protocol "://" hostname (when port (str ":" port)) path))

(reg-event-fx
 :route
 (fn [{:keys [db]} [_ {:keys [handler route-params] :as new-route} {:keys [no-return? skip-path? event secure?] :as options}]]
   (let [{:keys [route route-history]} db
         seq-params (seq route-params)
         flat-params (flatten seq-params)
         path (apply routes/path-for (or handler new-route) flat-params)]
     (when (and js/window.location
              secure?
              (not= "localhost" js/window.location.hostname))
       (set! js/window.location.href (make-url "https"
                                               js/window.location.hostname
                                               path
                                               js/window.location.port)))
     (cond-> {:db (assoc db :route new-route)
              :dispatch-n [[:hide-message]
                           [:close-orcacle]]}
       (not no-return?) (assoc-in [:db :return-route] new-route)
       (not skip-path?) (assoc :path path)
       event (update :dispatch-n conj event)))))

(reg-event-db
 :set-user-data
 [user->local-store-interceptor]
 (fn [db [_ user-data]]
   (update db :user-data merge user-data)))

(reg-event-db
 :clear-login
 [user->local-store-interceptor]
 (fn [db [_ user-data]]
   (update db :user-data dissoc :user-data :token)))

;; Replaces top-level @(subscribe [:user false]) in core.cljs — that was a
;; Replaces top-level @(subscribe [:user false]) in core.cljs — that was a
;; side-effect-only subscribe used to trigger an HTTP auth check on app startup.
(reg-event-fx
 :verify-user-session
 (fn [{:keys [db]} _]
   (if (and (:user db) (:token (:user db)))
     (do (go (let [response (<! (http/get (url-for-route routes/user-route)
                                           {:headers (authorization-headers db)}))]
               (case (:status response)
                 200 nil
                 401 (dispatch [:clear-login])
                 nil)))
         {})
     {})))

(reg-event-db
 :set-user
 (fn [db [_ user-data]]
   (assoc db :user user-data)))

#_ ;; never dispatched from UI
(defn set-active-tabs [db [_ active-tabs]]
  (assoc-in db tab-path active-tabs))

#_ ;; never dispatched from UI
(reg-event-db
 :set-active-tabs
 set-active-tabs)

(defn set-loading [db [_ v]]
  (assoc db :loading v))

(reg-event-db
 :set-loading
 set-loading)

(reg-event-db
 :toggle-locked
 (fn [db [_ path]]
   (update db :locked-components (fn [comps]
                                   (if (comps path)
                                     (disj comps path)
                                     (conj comps path))))))

(reg-event-db
 :toggle-homebrew
 character-interceptors
 (fn [character [_ path]]
   (update-in character
              [::entity/homebrew-paths path]
              not)))

(reg-event-db
 :failed-loading-image
 character-interceptors
 (fn [character [_ image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/image-url-failed
           image-url)))

(reg-event-db
 :failed-loading-faction-image
 character-interceptors
 (fn [character [_ faction-image-url]]
   (update character
           ::entity/values
           assoc
           ::char5e/faction-image-url-failed
           faction-image-url)))

(reg-event-db
 :loaded-image
 character-interceptors
 (fn [character []]
   (update character
           ::entity/values
           dissoc
           ::char5e/image-url-failed)))

(reg-event-db
 :loaded-faction-image
 character-interceptors
 (fn [character []]
   (update character
           ::entity/values
           dissoc
           ::char5e/faction-image-url-failed)))

(reg-event-db
 :set-custom-race
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :race
              ::entity/value]
             name)))

(reg-event-db
 :set-custom-subrace
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :race
              ::entity/options
              :subrace
              ::entity/value]
             name)))

;; Set homebrew subclass name. built-template passed from custom-option-builder.
(reg-event-db
 :set-custom-subclass
 character-interceptors
 (fn [character [_ path built-template name]]
   (let [entity-path (entity/get-option-value-path
                      built-template
                      character
                      path)]
     (assoc-in character
               entity-path
               name))))

;; Set homebrew feat name. built-template passed from custom-option-builder.
(reg-event-db
 :set-custom-feat-name
 character-interceptors
 (fn [character [_ path built-template name]]
   (let [entity-path (entity/get-option-value-path
                      built-template
                      character
                      path)]
     (assoc-in character
               entity-path
               name))))

(reg-event-db
 :set-custom-background
 character-interceptors
 (fn [character [_ name]]
   (assoc-in character
             [::entity/options
              :background
              ::entity/value]
             name)))

(defn cookies []
  (let [cookie js/document.cookie]
    (into {}
          (map #(s/split % "="))
          (s/split cookie "; "))))

(def show-generic-error event-utils/show-generic-error)

(reg-fx
 :http
 (fn [{:keys [on-success on-failure on-unauthorized auth-token] :as cfg}]
   (let [final-cfg (if auth-token
                     (assoc-in cfg [:headers "Authorization"] (str "Token " auth-token))
                     cfg)]
     (go (let [response (<! (http/request final-cfg))]
           (dispatch [:set-loading false])
           (if (<= 200 (:status response) 299)
             (when on-success (dispatch (conj on-success response)))
             (if (= 401 (:status response))
               (if on-unauthorized
                 (dispatch (conj on-unauthorized response))
                 (dispatch [:route-to-login]))
               (if on-failure
                 (dispatch (conj on-failure response))
                 (dispatch (show-generic-error))))))))))

(reg-fx
 :path
 (fn [path]
   (.pushState js/window.history {} nil path)))

(def login-url (backend-url "/login"))

(reg-event-fx
 :login-success
 [user->local-store-interceptor]
 (fn [{:keys [db]} [_ backtrack? response]]
   {:db (update db :user-data merge (-> response :body))
    :dispatch [:route (or
                       (:return-route db)
                        routes/dnd-e5-char-builder-route)]}))

(defn show-old-account-message []
  [:show-login-message [:div  "There is no account for the email or username, please double-check it. Usernames and passwords are case sensitive, email addresses are not. You can also try to " [:a {:href (routes/path-for routes/register-page-route)} "register"] "." ]])

(defn dispatch-login-failure [message]
  {:dispatch-n [[:clear-login]
                [:show-login-message message]]})

(reg-event-fx
 :login-failure
 (fn [{:keys [db]} [_ response]]
   (let [error-code (-> response :body :error)]
     (cond
       (= error-code errors/username-required) (dispatch-login-failure "Username is required.")
       (= error-code errors/too-many-attempts) (dispatch-login-failure "You have made too many login attempts, you account is locked for 15 minutes. Please do not try to login again until 15 minutes have passed.")
       (= error-code errors/password-required) (dispatch-login-failure "Password is required.")
       (= error-code errors/bad-credentials) (dispatch-login-failure "Password is incorrect.") 
       (= error-code errors/no-account) {:dispatch-n [[:clear-login]
                                                      (show-old-account-message)]}
       (= error-code errors/unverified) {:db (assoc db :temp-email (-> response :body :email))
                                         :dispatch [:route routes/verify-sent-route]}
       (= error-code errors/unverified-expired) {:dispatch [:route routes/verify-failed-route]}
       :else (dispatch-login-failure [:div "A login error occurred."])))))

(reg-event-fx
 :logout
 (fn [cofx [_ response]]
   {:dispatch-n [[:clear-login]]}))

(def login-routes
  #{routes/login-page-route
    routes/register-page-route
    routes/verify-sent-route
    routes/reset-password-page-route
    routes/verify-failed-route
    routes/verify-success-route
    routes/send-password-reset-page-route
    routes/password-reset-success-route
    routes/password-reset-expired-route
    routes/password-reset-used-route})

(reg-event-fx
 :login
 (fn [{:keys [db]} [_ params backtrack?]]
   {:http {:method :post
           :url login-url
           :json-params params
           :on-success [:login-success backtrack?]
           :on-unauthorized [:login-failure]}}))

(reg-event-db
 :register-success
 (fn [db [_ backtrack? response]]
   (-> db
       (update :user-data merge (:body response))
       (assoc :route :verify-sent))))

(reg-event-fx
 :register-failure
 (fn [cofx [_ response]]
   {:dispatch [:clear-login]}))

#_ ;; dead stub — real impl is orcpub.registration/validate-registration
(defn validate-registration [])

(reg-event-db
 :email-taken
 (fn [db [_ response]]
   (assoc db :email-taken? (-> response :body (= "true")))))

(reg-event-db
 :username-taken
 (fn [db [_ response]]
   (assoc db :username-taken? (-> response :body (= "true")))))

#_ ;; never dispatched — registration form uses :register-first-and-last-name
(reg-event-db
 :registration-first-and-last-name
 (fn [db [_ first-and-last-name]]
   (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-fx
 :registration-email
 (fn [{:keys [db]} [_ email]]
   {:db (assoc-in db [:registration-form :email] email)
    :dispatch [:check-email email]}))

(reg-event-fx
 :registration-verify-email
 (fn [{:keys [db]} [_ email]]
   {:db (assoc-in db [:registration-form :verify-email] email)}))

(reg-event-fx
 :registration-username
 (fn [{:keys [db]} [_ username]]
   {:db (assoc-in db [:registration-form :username] username)
    :dispatch [:check-username username]}))

(reg-event-db
 :registration-password
 (fn [db [_ password]]
   (assoc-in db [:registration-form :password] password)))

(reg-event-db
 :registration-send-updates?
 (fn [db [_ send-updates?]]
   (assoc-in db [:registration-form :send-updates?] send-updates?)))

#_ ;; never dispatched from UI
(reg-event-db
 :register-first-and-last-name
 (fn [db [_ first-and-last-name]]
   (assoc-in db [:registration-form :first-and-last-name] first-and-last-name)))

(reg-event-fx
 :check-email
 (fn [{:keys [db]} [_ email]]
   {:http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/check-email-route))
           :query-params {:email email}
           :on-success [:email-taken]}}))

(reg-event-fx
 :check-username
 (fn [{:keys [db]} [_ username]]
   {:http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/check-username-route))
           :query-params {:username username}
           :on-success [:username-taken]}}))

(reg-event-fx
 :register
 (fn [{:keys [db]} [_ params backtrack?]]
   (let [registration-form (:registration-form db)]
     {:db (assoc db :temp-email (:email registration-form))
      :http {:method :post
             :url (backend-url (bidi/path-for routes/routes routes/register-route))
             :json-params registration-form
             :on-success [:register-success backtrack?]
             :on-failure [:register-failure]}})))

(reg-event-db
 :re-verify-success
 (fn [db []]
   (assoc db :route routes/verify-sent-route)))

(reg-event-fx
 :re-verify
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/re-verify-route))
           :query-params params
           :on-success [:re-verify-success]}}))

(reg-event-db
 :send-password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-sent-route)))

(reg-event-fx
 :send-password-reset-failure
 (fn [_ [_ response]]
   (let [error (-> response :body :error (= :no-account))]
     (if error
       (dispatch (show-old-account-message))
       (show-generic-error)))))

(reg-event-fx
 :send-password-reset
 (fn [{:keys [db]} [_ params]]
   {:db (assoc db :temp-email (:email params))
    :http {:method :get
           :url (backend-url (bidi/path-for routes/routes routes/send-password-reset-route))
           :query-params params
           :on-success [:send-password-reset-success]
           :on-failure [:send-password-reset-failure]}}))

;; never dispatched — character loading uses :load-user-data flow
#_(reg-event-db
 :load-characters-success
 (fn [db [_ response]]
   (assoc-in db [:dnd :e5 :characters] (:body response))))

(defn get-auth-token [db]
  (-> db :user-data :token))

#_ ;; never dispatched — character loading uses :load-user-data flow
(reg-event-fx
 :load-characters
 (fn [{:keys [db]} [_ params]]
   {:http {:method :get
           :auth-token (get-auth-token db)
           :url (backend-url (routes/path-for routes/dnd-e5-char-list-route))
           :on-success [:load-characters-success]}}))

(reg-event-db
 :password-reset-success
 (fn [db []]
   (assoc db :route routes/password-reset-success-route)))

(reg-event-fx
 :password-reset-failure
 (fn [_ _]
   (dispatch-login-failure "There was an error resetting your password.")))

(reg-event-fx
 :password-reset
 (fn [{:keys [db]} [_ params]]
   (let [c (cookies)
         token (c "token")]
     {:db (assoc db :temp-email (:email params))
      :http {:method :post
             :auth-token token
             :url (backend-url (bidi/path-for routes/routes routes/reset-password-route))
             :json-params params
             :on-success [:password-reset-success]
             :on-unauthorized [:password-reset-failure]
             :on-failure [:password-reset-failure]}})))

(reg-event-db
 ::char5e/set-characters
 (fn [db [_ characters]]
   (assoc db
          ::char5e/characters characters
          ::char5e/summary-map (common/map-by-id characters))))

(reg-event-db
 ::mi/set-custom-items
 (fn [db [_ items]]
   (assoc db ::mi/custom-items items)))

(reg-event-db
 ::party5e/set-parties
 (fn [db [_ parties]]
   (assoc db
          ::char5e/parties parties
          ::char5e/parties-map (common/map-by-id parties))))

(reg-event-db
 ::char5e/remove-user-characters
 (fn [db [_ user]]
   (update db ::char5e/characters (fn [characters]
                                    (remove
                                     (fn [{:keys [:orcpub.entity.strict/owner]}]
                                       (= owner user))
                                     characters)))))

(reg-event-db
 ::char5e/set-character
 (fn [db [_ id character]]
   (let [int-id (js/parseInt id)
         updated (assoc-in db
                           [::char5e/character-map int-id]
                           character)]
     (if (and (= int-id (get-in db [:character :db/id]))
              (not (get-in db [:character :changed])))
       (assoc updated :character character)
       updated))))

(reg-event-fx
 :edit-character
 (fn [{:keys [db]} [_ character]]
   {:dispatch-n [[:set-character character]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 ::mi/edit-custom-item
 (fn [{:keys [db]} [_ item]]
   {:dispatch-n [[::mi/set-item (mi/to-internal-item item)]
                 [:route routes/dnd-e5-item-builder-page-route]]}))

(defn reg-edit-homebrew [event set-event route]
  (reg-event-fx
   event
   (fn [{:keys [db]} [_ item]]
     {:dispatch-n [[set-event item]
                   [:route route]]})))

(reg-edit-homebrew
 ::spells/edit-spell
 ::spells/set-spell
 routes/dnd-e5-spell-builder-page-route)

(reg-edit-homebrew
 ::monsters/edit-monster
 ::monsters/set-monster
 routes/dnd-e5-monster-builder-page-route)

(reg-edit-homebrew
 ::encounters/edit-encounter
 ::encounters/set-encounter
 routes/dnd-e5-encounter-builder-page-route)

(reg-edit-homebrew
 ::bg5e/edit-background
 ::bg5e/set-background
 routes/dnd-e5-background-builder-page-route)

(reg-edit-homebrew
 ::langs5e/edit-language
 ::langs5e/set-language
 routes/dnd-e5-language-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-invocation
 ::class5e/set-invocation
 routes/dnd-e5-invocation-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-boon
 ::class5e/set-boon
 routes/dnd-e5-boon-builder-page-route)

(reg-edit-homebrew
 ::selections5e/edit-selection
 ::selections5e/set-selection
 routes/dnd-e5-selection-builder-page-route)

(reg-edit-homebrew
 ::feats5e/edit-feat
 ::feats5e/set-feat
 routes/dnd-e5-feat-builder-page-route)

(reg-edit-homebrew
 ::race5e/edit-race
 ::race5e/set-race
 routes/dnd-e5-race-builder-page-route)

(reg-edit-homebrew
 ::race5e/edit-subrace
 ::race5e/set-subrace
 routes/dnd-e5-subrace-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-subclass
 ::class5e/set-subclass
 routes/dnd-e5-subclass-builder-page-route)

(reg-edit-homebrew
 ::class5e/edit-class
 ::class5e/set-class
 routes/dnd-e5-class-builder-page-route)

(reg-event-fx
 :delete-character-success
 (fn [_ _]
   {:dispatch [:show-message "Character successfully deleted"]}))


(reg-event-fx
 :delete-character
 (fn [{:keys [db]} [_ id]]
   {:db (update db
                ::char5e/characters
                (fn [chars]
                  (remove #(-> % :db/id (= id)) chars)))
    :http {:method :delete
           :auth-token (get-auth-token db)
           :url (backend-url (routes/path-for routes/dnd-e5-char-route :id id))
           :on-success [:delete-character-success]}}))

(reg-event-fx
 :new-character
 (fn [{:keys [db]} _]
   {:db (assoc db :character default-character)
    :dispatch [:route routes/dnd-e5-char-builder-route]}))

(reg-event-db
 :hide-message
 (fn [db _]
   (assoc db :message-shown? false)))

(reg-event-db
 :hide-login-message
 (fn [db _]
   (assoc db :login-message-shown? false)))

(reg-event-db
 :show-message
 (fn [db [_ message ttl]]
   (go (<! (timeout (or ttl 5000)))
       (dispatch [:hide-message]))
   (assoc db
          :message-shown? true
          :message message
          :message-type :success)))

(reg-event-db
 :show-message-2
; Display msg with out auto closing the msg.
 (fn [db [_ message]]
   (prn message)
   (assoc db
          :message-shown? true
          :message message
          :message-type :success)))

(reg-event-db
 :show-warning-message
 (fn [db [_ message ttl]]
   (go (<! (timeout (or ttl 5000)))
       (dispatch [:hide-message]))
   (assoc db
          :message-shown? true
          :message message
          :message-type :warning)))

(reg-event-db
 :show-error-message
 (fn [db [_ message ttl]]
   (go (<! (timeout (or ttl 5000)))
       (dispatch [:hide-message]))
   (assoc db
          :message-shown? true
          :message message
          :message-type :error)))

(reg-event-db
 :show-login-message
 (fn [db [_ message]]
   (go (<! (timeout 15000))
       (dispatch [:hide-login-message]))
   (assoc db
          :login-message-shown? true
          :login-message message)))

#_ ;; never dispatched from UI
(reg-event-db
 :hide-warning
 (fn [db _]
   (assoc db :warning-hidden true)))

(reg-event-db
 :hide-confirmation
 (fn [db _]
   (assoc db :confirmation-shown? false)))

(reg-event-fx
 :confirm
 (fn [_ [_ event]]
   {:dispatch-n [[:hide-confirmation]
                 event]}))

(reg-event-db
 :show-confirmation
 (fn [db [_ cfg]]
   (assoc db
          :confirmation-shown? true
          :confirmation-cfg cfg)))

(defn name-result [search-text]
  (let [[sex race subrace :as result] (event-handlers/parse-name-query search-text)]
    (when result
      {:type :name
       :result (char-rand5e/random-name-result
                {:race race
                 :subrace subrace
                 :sex sex})})))

#_ ;; unreferenced
(defn remove-subtypes [subtypes hidden-subtypes]
  (let [result (sets/difference subtypes hidden-subtypes)]
    result))

#_ ;; orphaned re-export alias — callers use compute/compute-plugin-vals directly
(def compute-plugin-vals compute/compute-plugin-vals)
(def compute-sorted-spells compute/compute-sorted-spells)
(def compute-sorted-items compute/compute-sorted-items)
(def filter-by-name-xform compute/filter-by-name-xform)
(def filter-spells compute/filter-spells)
(def filter-items compute/filter-items)

(defn search-results [text]
  (let [search-text (s/lower-case text)
        dice-result (dice/dice-roll-text search-text)
        kw (when search-text (common/name-to-kw search-text))
        name-result (name-result search-text)
        top-result (cond
                     dice-result {:type :dice-roll
                                  :result dice-result}
                     (spells/spell-map kw) {:type :spell
                                            :result (spells/spell-map kw)}
                     (monsters/monster-map kw) {:type :monster
                                                :result (monsters/monster-map kw)}
                     (mi/magic-item-map kw) {:type :magic-item
                                             :result (mi/magic-item-map kw)}
                     (= "tavern name" search-text) {:type :tavern-name
                                                    :result (char-rand5e/random-tavern-name)}
                     name-result name-result
                     :else nil)
        filter-xform (filter-by-name-xform search-text :name)
        top-spells (when (>= (count text) 3)
                     (sequence
                      filter-xform
                      spells/spells))
        top-monsters (when (>= (count text) 3)
                       (sequence
                        filter-xform
                        monsters/monsters))]
    (cond-> {}
      top-result (assoc :top-result top-result)
      (seq top-spells) (update :results conj {:type :spell
                                              :results top-spells})
      (seq top-monsters) (update :results conj {:type :monster
                                                :results top-monsters}))))


(reg-event-db
 :set-search-text
 (fn [db [_ search-text]]
   (cond-> db
     true (assoc :search-text search-text
                 :search-results (search-results search-text))
     (s/blank? search-text) (assoc :orcacle-clicked? false))))

(reg-event-db
 :close-orcacle
 (fn [db _]
   (-> db
       (assoc :orcacle-clicked? false)
       (dissoc :search-text))))

#_ ;; never dispatched from UI (note: "orcacle" typo)
(reg-event-fx
 :open-orcacle-over-character-builder
 (fn []
   {:dispatch-n [[:route routes/dnd-e5-char-builder-route]
                 [:open-orcacle]]}))

(reg-event-db
 :open-orcacle
 (fn [db _]
   (-> db
       (assoc :orcacle-clicked? true)
       (dissoc :search-text))))

(reg-event-db
 ::char5e/set-selected-display-tab
 (fn [db [_ tab]]
   (assoc db ::char5e/selected-display-tab tab)))

(reg-event-db
 ::char5e/set-builder-tab
 (fn [db [_ tab]]
   (assoc db ::char5e/builder-tab tab)))

(reg-event-db
  ::char5e/sort-monsters
  (fn [db [_ sort-criteria sort-direction]]
    (assoc db ::char5e/monster-sort-criteria sort-criteria
              ::char5e/monster-sort-direction sort-direction)))

(reg-event-db
 ::char5e/filter-monsters
 (fn [db [_ filter-text]]
   (assoc db ::char5e/monster-text-filter filter-text)))

;; Filter spell list by name. Computes sorted spells from db directly
;; (avoids subscribe outside reactive context).
(reg-event-db
 ::char5e/filter-spells
 (fn [db [_ filter-text]]
   (let [sorted (compute-sorted-spells db)]
     (assoc db
            ::char5e/spell-text-filter filter-text
            ::char5e/filtered-spells (if (>= (count filter-text) 3)
                                       (filter-spells filter-text sorted)
                                       sorted)))))

;; Filter magic item list by name. Computes sorted items from db directly.
(reg-event-db
 ::char5e/filter-items
 (fn [db [_ filter-text]]
   (let [sorted (compute-sorted-items db)]
     (assoc db
            ::char5e/item-text-filter filter-text
            ::char5e/filtered-items (if (>= (count filter-text) 3)
                                       (filter-items filter-text sorted)
                                       sorted)))))

(reg-event-db
 ::char5e/toggle-selected
 (fn [db [_ id]]
   (update db
           ::char5e/selected
           (fn [s]
             (if (get s id)
               (disj s id)
               (conj (or s #{}) id))))))

(reg-event-db
 ::char5e/toggle-monster-filter-hidden
 (fn [db [_ filter value]]
   (update-in db [::char5e/monster-filter-hidden? filter value] not)))

(defn toggle-set [key set]
  (if (get set key)
    (disj set key)
    (conj (or set #{}) key)))

(defn toggle-character-spell-prepared [class spell-key character]
  (update-in
   character
   [::entity/values
    ::char5e/prepared-spells-by-class
    class]
   (partial toggle-set spell-key)))

(defn toggle-spell-slot-used [level i character]
  (update-in
   character
   [::entity/values
    ::spells/slots-used
    (common5e/slot-level-key level)]
   (partial toggle-set i)))

(defn update-character-fx [db id update-fn]
  (if id
    {:db (update-in
          db
          [::char5e/character-map (js/parseInt id)]
          update-fn)
     ::char5e/save-character-throttled id}
    {:dispatch [:set-character (update-fn (:character db))]}))

(reg-event-fx
 ::char5e/toggle-spell-prepared
 (fn [{:keys [db]} [_ id class spell-key]]
   (let [update-fn (partial toggle-character-spell-prepared class spell-key)]
     (update-character-fx db id update-fn))))

(defn use-spell-slot [lvl character]
  (update-in
   character
   [::entity/values
    ::spells/slots-used
    (common5e/slot-level-key lvl)]
   (fn [level-slots-used]
     (let [first-empty-slot (some
                             (fn [v]
                               (when (not (get level-slots-used v))
                                 v))
                             (range))]
       (conj (or level-slots-used #{})
             first-empty-slot)))))

(reg-event-fx
 ::char5e/use-spell-slot
 (fn [{:keys [db]} [_ id lvl]]
   (let [update-fn (partial use-spell-slot lvl)]
     (update-character-fx db id update-fn))))

(reg-event-fx
 ::char5e/toggle-spell-slot-used
 (fn [{:keys [db]} [_ id level i]]
   (let [update-fn (partial toggle-spell-slot-used level i)]
     (update-character-fx db id update-fn))))

(defn set-current-hit-points [character current-hit-points]
  (assoc-in
   character
   [::entity/values
    ::char5e/current-hit-points]
   current-hit-points))

(defn set-current-xps [character xps]
  (assoc-in
   character
   [::entity/values
    ::char5e/xps]
   (when (not (js/isNaN xps))
     xps)))

(defn set-notes [character notes]
  (assoc-in
   character
   [::entity/values
    ::char5e/notes]
   notes))

(defn add-level [character]
  (let [path [::entity/options
              :class
              0
              ::entity/options
              :levels]
        levels (get-in character path)
        updated (if levels
                  (update-in
                   character
                   path
                   (fn [levels]
                     (conj
                      levels
                      (event-handlers/empty-level (count levels)))))
                  character)
        updated-levels (get-in updated path)]
    updated))

(reg-event-fx
 ::char5e/set-current-hit-points
 (fn [{:keys [db]} [_ id current-hit-points]]
   (update-character-fx db id #(set-current-hit-points % current-hit-points))))

(reg-event-fx
 ::char5e/set-current-xps
 (fn [{:keys [db]} [_ id current-xps]]
   (update-character-fx db id #(set-current-xps % current-xps))))

(reg-event-fx
 ::char5e/add-level
 (fn [{:keys [db]} [_ id]]
   (update-character-fx db id add-level)))

;; Level up a character by id — adds a level and opens the builder.
(reg-event-fx
 ::char5e/level-up
 (fn [{:keys [db]} [_ character-id]]
   {:dispatch-n [[::char5e/add-level character-id]
                 [:set-character (get-in db [::char5e/character-map (js/parseInt character-id)] {})]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 ::char5e/set-notes
 (fn [{:keys [db]} [_ id notes]]
   (update-character-fx db id #(set-notes % notes))))

(defn toggle-feature-used [character units nm]
  (-> character
   (update-in    
    [::entity/values
     ::char5e/features-used
     units]
    (partial toggle-set nm))
   (dissoc
    [::entity/values
     ::char5e/features-used
     :db/id])))

(reg-event-fx
 ::char5e/toggle-feature-used
 (fn [{:keys [db]} [_ id units nm]]
   (update-character-fx db id #(toggle-feature-used % units nm))))

(defn clear-period [db id update-fn & units]
  (update-character-fx db id #(cond-> %
                                true (update-in
                                      [::entity/values ::char5e/features-used]
                                      (fn [features-used]
                                        (apply dissoc features-used units)))
                                update-fn update-fn)))

(reg-event-fx
 ::char5e/finish-long-rest
 (fn [{:keys [db]} [_ id]]
   (clear-period db
                 id
                 (fn [character]
                   (update
                    character
                    ::entity/values
                    dissoc
                    ::spells/slots-used
                    character
                    ::char5e/current-hit-points ::char5e/max-hit-points))
                 ::units5e/long-rest
                 ::units5e/rest)))

(reg-event-fx
  ::char5e/finish-short-rest-warlock
  (fn [{:keys [db]} [_ id]]
    (clear-period db
                  id
                  (fn [character]
                    (update
                      character
                      ::entity/values
                      dissoc
                      ::spells/slots-used))
                  ::units5e/rest)))

(reg-event-fx
 ::char5e/finish-short-rest
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/short-rest ::units5e/rest)))

(reg-event-fx
 ::char5e/new-round
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/round)))

(reg-event-fx
 ::char5e/new-turn
 (fn [{:keys [db]} [_ id]]
   (clear-period db id nil ::units5e/turn)))

(defn vec-conj [v item]
  (conj (or v []) item))

(reg-event-db
 ::char5e/new-custom-item
 (fn [db [_ items-key]]
   (update-in
    db
    [:character
     ::entity/values
     items-key]
    vec-conj
    {::char-equip5e/name "New Custom Item"
     ::char-equip5e/quantity 1
     ::char-equip5e/equipped? true})))

(reg-event-db
 ::char5e/set-custom-item-name
 (fn [db [_ items-key i value]]
   (assoc-in
    db
    [:character
     ::entity/values
     items-key
     i
     ::char-equip5e/name]
    value)))

(reg-event-db
 :toggle-theme
 [user->local-store-interceptor]
 (fn [db _]
   (update-in db [:user-data :theme]
              (fn [theme]
                (if (= theme "light-theme")
                  "dark-theme"
                  "light-theme")))))

#_ ;; never dispatched from UI
(reg-event-db
 ::mi/set-builder-item
 [magic-item->local-store-interceptor]
 (fn [db [_ magic-item]]
   (assoc db ::mi/builder-item magic-item)))

(reg-event-db
 ::mi/toggle-attunement
 item-interceptors
 (fn [item _]
   (if (::mi/attunement item)
     (dissoc item ::mi/attunement)
     (assoc item ::mi/attunement #{:any}))))

(defn set-any-attunement [attunement]
  (if (empty? attunement)
    (conj attunement :any)
    (disj attunement :any)))

(reg-event-db
 ::mi/toggle-attunement-value
 item-interceptors
 (fn [item [_ value]]
   (update item
           ::mi/attunement
           #(->> %
                 (toggle-set value)
                 set-any-attunement))))

(reg-event-db
 ::mi/add-remote-item
 (fn [db [_ item]]
   (assoc-in db [::mi/remote-items (:db/id item)] item)))

(reg-event-db
 ::mi/set-item-name
 item-interceptors
 (fn [item [_ item-name]]
   (assoc item ::mi/name item-name)))

(reg-event-db
 ::spells/set-spell-prop
 spell-interceptors
 (fn [spell [_ prop-key prop-value]]
   (assoc spell prop-key prop-value)))

(reg-event-db
 ::spells/toggle-spell-prop
 spell-interceptors
 (fn [spell [_ prop-key]]
   (update spell prop-key not)))

(reg-event-db
 ::monsters/set-monster-prop
 monster-interceptors
 (fn [monster [_ prop-key prop-value]]
   (assoc monster prop-key prop-value)))

(reg-event-db
 ::encounters/set-encounter-prop
 encounter-interceptors
 (fn [encounter [_ prop-key prop-value]]
   (assoc encounter prop-key prop-value)))

(reg-event-db
 ::monsters/set-monster-path-prop
 monster-interceptors
 (fn [monster [_ prop-path prop-value]]
   (assoc-in monster prop-path prop-value)))

(reg-event-db
 ::combat/set-monster-hit-points
 combat-interceptors
 (fn [combat [_ {:keys [num monster]} individual-index value]]
   (let [{:keys [die die-count modifier]} (:hit-points monster)]
     (assoc-in combat
               [:monster-data (:key monster) individual-index :hit-points]
               value))))

(reg-event-db
 ::combat/delete-monster-condition
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              common/remove-at-index
              condition-index)))

(reg-event-db
 ::combat/set-monster-condition-type
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index type]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              (fn [conditions]
                (let [conditions (vec conditions)]
                  (assoc-in conditions [condition-index :type] type))))))

(reg-event-db
 ::combat/set-monster-condition-duration
 combat-interceptors
 (fn [combat [_ monster-key individual-index condition-index duration-type hours]]
   (update-in combat
              [:monster-data monster-key individual-index :conditions]
              (fn [conditions]
                (let [conditions (vec conditions)]
                  (assoc-in conditions
                            [condition-index :duration duration-type]
                            hours))))))

(reg-event-db
 ::combat/randomize-monster-hit-points
 combat-interceptors
 (fn [combat [_ {:keys [num monster]} monster-map]]
   (let [{:keys [die die-count modifier]} (:hit-points monster)]
     (update-in combat
                [:monster-data (:key monster)]
                (fn [monster-data]
                  (reduce
                   (fn [m x]
                     (assoc-in m
                               [x :hit-points]
                               (dice/dice-roll {:num die-count
                                                :sides die
                                                :modifier modifier})))
                   monster-data
                   (range num)))))))

(reg-event-db
 ::combat/set-combat-prop
 combat-interceptors
 (fn [combat [_ prop-key prop-value]]
   (assoc combat prop-key prop-value)))

(defn nil-or-zero? [v]
  (or (nil? v) (zero? v)))

(defn zero-duration? [{{:keys [hours minutes rounds]} :duration}]
  (and (nil-or-zero? hours)
       (nil-or-zero? minutes)
       (nil-or-zero? rounds)))

(defn decrement-duration [condition]
  (update
   condition
   :duration
   (fn [{:keys [hours minutes rounds] :as duration}]
     (when (not (zero-duration? condition))
       (let [total-rounds (+ rounds
                             (* common/rounds-per-minute minutes)
                             (* common/rounds-per-hour hours))
             next-total-rounds (dec total-rounds)
             next-hours (int (/ next-total-rounds common/rounds-per-hour))
             remaining (rem next-total-rounds common/rounds-per-hour)
             next-minutes (int (/ remaining common/rounds-per-minute))
             next-rounds (rem remaining common/rounds-per-minute)]
         {:hours next-hours
          :minutes next-minutes
          :rounds next-rounds})))))

(defn update-individual-monster [data monster-index individual-data]
  (let [current-conditions (:conditions individual-data)
        decremented-conditions (map decrement-duration current-conditions)
        {new-conditions false removed-conditions true}
        (group-by zero-duration? decremented-conditions)]
    (assoc
     data
     monster-index
     (assoc
      individual-data
      :conditions
      new-conditions
      :removed-conditions
      removed-conditions))))

(defn update-monster-data-item [monster-data monster-kw data]
  (assoc
   monster-data
   monster-kw
   (reduce-kv
    update-individual-monster
    data
    data)))

(defn update-monster-data [monster-data]
  (reduce-kv
   update-monster-data-item
   monster-data
   monster-data))

(defn update-conditions [combat]
  (update combat
          :monster-data
          update-monster-data))

(reg-event-fx
 ::combat/next-initiative
 (fn [{:keys [db]} [_ monster-map]]
   (let [combat (::combat/tracker-item db)
         initiatives (->> combat
                          :initiative
                          vals
                          (mapcat vals)
                          (sort >))
         current-initiative (:current-initiative combat)
         next-initiative (if current-initiative
                           (or (first (drop-while #(>= % current-initiative) initiatives))
                               (first initiatives))
                           (second initiatives))
         round (get combat :round 1)
         next-round? (and current-initiative
                          (> next-initiative current-initiative))
         updated (cond-> combat
                   true (assoc :current-initiative next-initiative)
                   next-round? (assoc :round (inc round))
                   next-round? update-conditions)
         removed-conditions (when next-round?
                              (filter
                               (comp seq :removed-conditions)
                               (flatten
                                (map
                                 (fn [[monster-kw individuals]]
                                   (map
                                    (fn [[individual-index {:keys [removed-conditions]}]]
                                      {:type :monster
                                       :index individual-index
                                       :name (get-in monster-map [monster-kw :name])
                                       :removed-conditions (map :type removed-conditions)})
                                    individuals))
                                 (:monster-data updated)))))]
       {:dispatch-n (cond-> [[::combat/set-combat updated]]
                      (seq removed-conditions)
                      (conj [:show-message
                             [:div.m-t-5.f-w-b.f-s-18
                              (doall
                               (map-indexed
                                (fn [i {:keys [name index removed-conditions]}]
                                  ^{:key i}
                                  [:div.m-b-5 (str name " #" (inc index) " is no longer " (common/list-print (map common/kw-to-name removed-conditions) "or") ".")])
                                removed-conditions))]]))})))

(reg-event-db
 ::encounters/set-encounter-path-prop
 encounter-interceptors
 (fn [encounter [_ prop-path prop-value]]
   (assoc-in encounter prop-path prop-value)))

(reg-event-db
 ::combat/delete-party
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :parties common/remove-at-index index)))

(reg-event-db
 ::combat/delete-encounter
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :encounters common/remove-at-index index)))

(reg-event-db
 ::combat/delete-character
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :characters common/remove-at-index index)))

(reg-event-db
 ::combat/delete-monster
 combat-interceptors
 (fn [combat [_ index]]
   (update combat :monsters common/remove-at-index index)))

(reg-event-db
 ::combat/set-combat-path-prop
 combat-interceptors
 (fn [combat [_ path-prop prop-value]]
   ;; Guard: ensure combat is a valid map with vector collections.
   ;; If the path interceptor extracts nil (key missing from db), bare
   ;; assoc-in creates maps for integer keys instead of vectors,
   ;; corrupting localStorage and failing the spec on next load.
   (assoc-in (or combat default-combat) path-prop prop-value)))

(reg-event-db
 ::encounters/delete-creature
 encounter-interceptors
 (fn [encounter [_ index]]
   (update encounter :creatures common/remove-at-index index)))

(reg-event-db
 ::class5e/set-class-path-prop
 class-interceptors
 (fn [class [_ prop-path prop-value prop-path-2 prop-value-2]]
   ;; Only apply second assoc-in if prop-path-2 is provided
   ;; (prevents {nil nil} corruption when called with 2 args)
   (cond-> class
     true (assoc-in prop-path prop-value)
     prop-path-2 (assoc-in prop-path-2 prop-value-2))))

(reg-event-db
 ::selections5e/set-selection-path-prop
 selection-interceptors
 (fn [selection [_ prop-path prop-value]]
   (assoc-in selection prop-path prop-value)))

(reg-event-db
 ::selections5e/delete-option
 selection-interceptors
 (fn [selection [_ index]]
   (update selection :options common/remove-at-index index)))

;; Append a new option to the selection with a unique default name ("Option N").
;; Starts from count+1, increments if that name already exists (e.g., after deletions).
(reg-event-db
 ::selections5e/add-option
 selection-interceptors
 (fn [selection]
   (let [existing (set (map :name (:options selection)))
         idx (inc (count (:options selection)))
         idx (if (contains? existing (str "Option " idx))
               (loop [n idx] (if (contains? existing (str "Option " n)) (recur (inc n)) n))
               idx)]
     (update selection :options conj {:name (str "Option " idx)}))))

(reg-event-db
 ::class5e/set-subclass-path-prop
 subclass-interceptors
 (fn [subclass [_ prop-path prop-value]]
   (assoc-in subclass prop-path prop-value)))

(reg-event-db
 ::race5e/set-race-path-prop
 race-interceptors
 (fn [race [_ prop-path prop-value]]
   (assoc-in race prop-path prop-value)))

(reg-event-db
 ::race5e/set-subrace-path-prop
 subrace-interceptors
 (fn [subrace [_ prop-path prop-value]]
   (assoc-in subrace prop-path prop-value)))

(reg-event-db
 ::bg5e/set-background-prop
 background-interceptors
 (fn [background [_ prop-key prop-value]]
   (assoc background prop-key prop-value)))

(reg-event-db
 ::langs5e/set-language-prop
 language-interceptors
 (fn [language [_ prop-key prop-value]]
   (assoc language prop-key prop-value)))

(reg-event-db
 ::class5e/set-invocation-prop
 invocation-interceptors
 (fn [invocation [_ prop-key prop-value]]
   (assoc invocation prop-key prop-value)))

(reg-event-db
 ::class5e/set-boon-prop
 boon-interceptors
 (fn [boon [_ prop-key prop-value]]
   (assoc boon prop-key prop-value)))

(reg-event-db
 ::selections5e/set-selection-prop
 selection-interceptors
 (fn [selection [_ prop-key prop-value]]
   (assoc selection prop-key prop-value)))

(reg-event-db
 ::race5e/set-race-prop
 race-interceptors
 (fn [race [_ prop-key prop-value]]
   (assoc race prop-key prop-value)))

(reg-event-db
 ::race5e/set-subrace-prop
 subrace-interceptors
 (fn [subrace [_ prop-key prop-value]]
   (assoc subrace prop-key prop-value)))

(reg-event-db
 ::class5e/set-subclass-prop
 subclass-interceptors
 (fn [subclass [_ prop-key prop-value]]
   (assoc subclass prop-key prop-value)))

(reg-event-db
 ::class5e/toggle-save-prof
 class-interceptors
 (fn [class [_ key]]
   (update-in class
              [:profs :save]
              (fn [saves]
                (if (key saves)
                  (dissoc saves key)
                  (assoc saves key true))))))

(reg-event-db
 ::class5e/toggle-ability-increase-level
 class-interceptors
 (fn [class [_ level]]
   (update class
           :ability-increase-levels
           (fn [levels]
             (let [levels-set (into (sorted-set) levels)]
               (vec
                (if (levels-set level)
                  (disj levels-set level)
                  (conj levels-set level))))))))

(reg-event-db
 ::class5e/set-class-prop
 class-interceptors
 (fn [class [_ prop-key prop-value]]
   (assoc class prop-key prop-value)))

(reg-event-db
 ::class5e/toggle-class-spell-list
 class-interceptors
 (fn [class [_ level spell-kw]]
   (update-in class
              [:spellcasting :spell-list level]
              (fn [spells]
                (let [spells (or spells #{})]
                  (if (spells spell-kw)
                    (disj spells spell-kw)
                    (conj spells spell-kw)))))))

(reg-event-db
 ::class5e/toggle-subclass-spellcasting
 subclass-interceptors
 (fn [subclass]
   (if (:spellcasting subclass)
     (dissoc subclass :spellcasting)
     (assoc subclass :spellcasting {:level-factor 3}))))

(reg-event-db
 ::class5e/set-class-spell
 subclass-interceptors
 (fn [subclass [_ class-spells-key level index spell-kw]]
   (assoc-in subclass [class-spells-key level index] spell-kw)))

#_ ;; never dispatched from UI
(reg-event-db
 ::class5e/set-spell-list
 subclass-interceptors
 (fn [subclass [_ class-kw]]
   (assoc-in subclass [:spellcasting :spell-list] class-kw)))

(reg-event-db
 ::feats5e/set-feat-prop
 feat-interceptors
 (fn [feat [_ prop-key prop-value]]
   (assoc feat prop-key prop-value)))

#_ ;; never dispatched from UI
(reg-event-db
 ::bg5e/set-feature-prop
 background-interceptors
 (fn [background [_ prop-key prop-value]]
   (assoc-in background [:traits 0 prop-key] prop-value)))

(reg-event-db
 ::feats5e/toggle-feat-prop
 feat-interceptors
 (fn [feat [_ key]]
   (update-in feat [:props key] not)))

#_ ;; never dispatched from UI — feat builder uses toggle-feat-prop instead
(reg-event-db
 ::feats5e/toggle-feat-selection
 feat-interceptors
 (fn [feat [_ key]]
   (update-in feat [:selections key] not)))

(reg-event-db
 ::feats5e/toggle-feat-value-prop
 feat-interceptors
 (fn [feat [_ key num]]
   (update feat :props (fn [m]
                         (if (= (get m key) num)
                           (dissoc m key)
                           (assoc m key num))))))

(reg-event-db
 ::race5e/toggle-race-prop
 race-interceptors
 (fn [race [_ key]]
   (update-in race [:props key] not)))

(reg-event-db
 ::race5e/toggle-subrace-value-prop
 subrace-interceptors
 (fn [subrace [_ key num]]
   (update subrace :props (fn [m]
                         (if (= (get m key) num)
                           (dissoc m key)
                           (assoc m key num))))))

#_ ;; never dispatched — class/subclass builder UI not wired for value-prop toggles
(reg-event-db
 ::class5e/toggle-subclass-value-prop
 subclass-interceptors
 (fn [subclass [_ key num]]
   (update subclass :props (fn [m]
                         (if (= (get m key) num)
                           (dissoc m key)
                           (assoc m key num))))))

#_ ;; never dispatched — class builder UI not wired for value-prop toggles
(reg-event-db
 ::class5e/toggle-class-value-prop
 class-interceptors
 (fn [class [_ key num]]
   (update class :props (fn [m]
                         (if (= (get m key) num)
                           (dissoc m key)
                           (assoc m key num))))))

(reg-event-db
 ::feats5e/toggle-feat-map-prop
 feat-interceptors
 (fn [feat [_ key value]]
   (update-in feat [:props key value] not)))

(reg-event-db
 ::race5e/toggle-subrace-map-prop
 subrace-interceptors
 (fn [subrace [_ key value]]
   (update-in subrace [:props key value] not)))

(reg-event-db
 ::monsters/toggle-monster-map-prop
 monster-interceptors
 (fn [monster [_ key value]]
   (update-in monster [:props key value] not)))

(reg-event-db
 ::class5e/toggle-class-path-prop
 class-interceptors
 (fn [class [_ prop-path prop-value]]
   (update-in class prop-path not)))

#_ ;; never dispatched — class builder UI not wired for prof toggles
(reg-event-db
 ::class5e/toggle-class-prof
 class-interceptors
 (fn [class [_ prop-path]]
   (let [v (get-in class prop-path)]
     ;; for classes, the value for a prof signals whether
     ;; it only applies to the first class a character takes
     (if (= v false)
       (common/dissoc-in class prop-path)
       (assoc-in class prop-path false)))))

(reg-event-db
 ::class5e/toggle-subclass-path-prop
 subclass-interceptors
 (fn [subclass [_ prop-path prop-value]]
   (update-in subclass prop-path not)))

(reg-event-db
 ::race5e/toggle-race-path-prop
 race-interceptors
 (fn [race [_ prop-path prop-value]]
   (update-in race prop-path not)))

(reg-event-db
 ::race5e/toggle-subrace-path-prop
 subrace-interceptors
 (fn [subrace [_ prop-path prop-value]]
   (update-in subrace prop-path not)))

(reg-event-db
 ::race5e/toggle-race-map-prop
 race-interceptors
 (fn [race [_ key value]]
   (update-in race [:props key value] not)))

#_ ;; never dispatched — class builder UI not wired for subclass map-prop toggles
(reg-event-db
 ::class5e/toggle-subclass-map-prop
 subclass-interceptors
 (fn [subclass [_ key value]]
   (update-in subclass [:props key value] not)))

#_ ;; never dispatched — class builder UI not wired for class map-prop toggles
(reg-event-db
 ::class5e/toggle-class-map-prop
 class-interceptors
 (fn [class [_ key value]]
   (update-in class [:props key value] not)))

#_ ;; never dispatched — background builder UI not wired for map-prop toggles
(reg-event-db
 ::bg5e/toggle-background-map-prop
 background-interceptors
 (fn [background [_ key value]]
   (update-in background [:props key value] not)))

(reg-event-db
 ::feats5e/toggle-feat-ability-increase
 feat-interceptors
 (fn [feat [_ ability-key]]
   (update feat :ability-increases (fn [s]
                                     (if (s ability-key)
                                       (disj s ability-key)
                                       (conj s ability-key))))))

(reg-event-db
 ::feats5e/toggle-ability-prereq
 feat-interceptors
 (fn [feat [_ ability-key]]
   (update feat :prereqs (fn [s]
                           (if (s ability-key)
                             (disj s ability-key)
                             (conj s ability-key))))))

(reg-event-db
 ::feats5e/toggle-path-prereq
 feat-interceptors
 (fn [feat [_ path]]
   (update-in feat (cons :path-prereqs path) not)))

(reg-event-db
 ::feats5e/toggle-spellcasting-prereq
 feat-interceptors
 (fn [feat]
   (update feat :prereqs (fn [s]
                           (if (s :spellcasting)
                             (disj s :spellcasting)
                             (conj s :spellcasting))))))


(reg-event-db
 ::bg5e/set-background-gold
 background-interceptors
 (fn [background [_ amount]]
   (assoc-in background [:treasure :gp] (js/parseInt amount))))

(reg-event-db
 ::race5e/set-race-speed
 race-interceptors
 (fn [race [_ v]]
   (assoc race :speed (js/parseInt v))))

(reg-event-db
 ::race5e/set-race-value-prop
 race-interceptors
 (fn [race [_ k v]]
   (assoc-in race [:props k] v)))

(reg-event-db
 ::race5e/set-subrace-speed
 subrace-interceptors
 (fn [subrace [_ v]]
   (assoc subrace :speed (js/parseInt v))))

(reg-event-db
 ::race5e/set-race-ability-increase
 race-interceptors
 (fn [race [_ ability-kw bonus]]
   (assoc-in race [:abilities ability-kw] (js/parseInt bonus))))

(reg-event-db
 ::race5e/set-subrace-ability-increase
 subrace-interceptors
 (fn [subrace [_ ability-kw bonus]]
   (assoc-in subrace [:abilities ability-kw] (js/parseInt bonus))))

(reg-event-db
 ::spells/set-spell-level
 spell-interceptors
 (fn [spell [_ level]]
   (assoc spell :level (js/parseInt level))))

(reg-event-db
 ::spells/toggle-component
 spell-interceptors
 (fn [spell [_ component]]
   (update-in spell [:components component] not)))

(reg-event-db
 ::bg5e/toggle-skill-prof
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:profs :skill key])
     (update-in background [:profs :skill] dissoc key)
     (assoc-in background [:profs :skill key] true))))

(reg-event-db
 ::race5e/toggle-language
 race-interceptors
 (fn [race [_ nm]]
   (if (get-in race [:languages nm])
     (update race :languages disj nm)
     (update race :languages conj nm))))

(reg-event-db
 ::bg5e/toggle-tool-prof
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:profs :tool key])
     (update-in background [:profs :tool] dissoc key)
     (assoc-in background [:profs :tool key] true))))

(reg-event-db
 ::bg5e/toggle-starting-equipment
 background-interceptors
 (fn [background [_ key]]
   (if (get-in background [:equipment key])
     (update-in background [:equipment] dissoc key)
     (assoc-in background [:equipment key] 1))))

(reg-event-db
 ::bg5e/toggle-starting-equipment-choice
 background-interceptors
 (fn [background [_ equipment equipment-name]]
   (letfn [(find-equipment [{:keys [name]}]
             (= name equipment-name))]
     (if (some
          find-equipment
          (:equipment-choices background))
       (update background :equipment-choices #(remove find-equipment %))
       (update background :equipment-choices conj {:name equipment-name
                                                   :options (zipmap
                                                             (map
                                                              :key
                                                              equipment)
                                                             (repeat 1))})))))

(reg-event-db
 ::bg5e/toggle-choice-tool-prof
 background-interceptors
 (fn [background [_ key num]]
   (if (= num (get-in background [:profs :tool-options key]))
     (update-in background [:profs :tool-options] dissoc key)
     (assoc-in background [:profs :tool-options key] num))))

(reg-event-db
 ::bg5e/toggle-choice-language-prof
 background-interceptors
 (fn [background [_ num]]
   (if (= num (get-in background [:profs :language-options :choose]))
     (update background :profs dissoc  :language-options)
     (assoc-in background [:profs :language-options] {:choose num :options {:any true}}))))

(reg-event-db
 ::spells/toggle-spell-list
 spell-interceptors
 (fn [spell [_ class-key]]
   (update-in spell [:spell-lists class-key] not)))

(reg-event-db
 ::spells/set-material-component
 spell-interceptors
 (fn [spell [_ material-component]]
   (assoc-in spell [:components :material-component] material-component)))

;;;; Item Builder

(reg-event-db
 ::mi/set-item-description
 item-interceptors
 (fn [item [_ item-description]]
   (assoc item ::mi/description item-description)))

(reg-event-db
 ::mi/set-item-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (-> item
       (assoc ::mi/type (keyword item-type-str))
       (dissoc ::mi/subtypes))))

(reg-event-db
 ::mi/set-item-weapon-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::weapons/type (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-damage-type
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::weapons/damage-type (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-melee-ranged
 item-interceptors
 (fn [item [_ item-type-str]]
   (let [kw (keyword item-type-str)]
     (assoc item
            ::weapons/melee? (= kw :melee)
            ::weapons/ranged? (= kw :ranged)))))

(reg-event-db
 ::mi/set-item-range-min
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/range ::weapons/min] v)))

(reg-event-db
 ::mi/set-item-range-max
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/range ::weapons/max] v)))

(reg-event-db
 ::mi/set-item-damage-die-count
 item-interceptors
 (fn [item [_ v]]
   (assoc item ::weapons/damage-die-count v)))

(reg-event-db
 ::mi/set-item-damage-die
 item-interceptors
 (fn [item [_ v]]
   (assoc item ::weapons/damage-die v)))

(reg-event-db
 ::mi/set-item-versatile-damage-die-count
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/versatile ::weapons/damage-die-count] v)))

(reg-event-db
 ::mi/set-item-versatile-damage-die
 item-interceptors
 (fn [item [_ v]]
   (assoc-in item [::weapons/versatile ::weapons/damage-die] v)))

(reg-event-db
 ::mi/toggle-item-finesse?
 item-interceptors
 (fn [item _]
   (update item ::weapons/finesse? not)))

(reg-event-db
 ::mi/toggle-item-reach?
 item-interceptors
 (fn [item _]
   (update item ::weapons/reach? not)))

(reg-event-db
 ::mi/toggle-item-two-handed?
 item-interceptors
 (fn [item _]
   (update item ::weapons/two-handed? not)))

(reg-event-db
 ::mi/toggle-item-heavy?
 item-interceptors
 (fn [item _]
   (update item ::weapons/heavy? not)))

(reg-event-db
 ::mi/toggle-item-light?
 item-interceptors
 (fn [item _]
   (update item ::weapons/light? not)))

(reg-event-db
 ::mi/toggle-item-thrown?
 item-interceptors
 (fn [item _]
   (update item ::weapons/thrown? not)))

(reg-event-db
 ::mi/toggle-item-ammunition?
 item-interceptors
 (fn [item _]
   (update item ::weapons/ammunition? not)))

(reg-event-db
 ::mi/toggle-item-special?
 item-interceptors
 (fn [item _]
   (update item ::weapons/special? not)))

(reg-event-db
 ::mi/toggle-item-loading?
 item-interceptors
 (fn [item _]
   (update item ::weapons/loading? not)))

(reg-event-db
 ::mi/toggle-item-versatile?
 item-interceptors
 (fn [item _]
   (if (::weapons/versatile item)
     (dissoc item ::weapons/versatile)
     (assoc item ::weapons/versatile {}))))

(defn set-value [item kw value]
  (if value
    (assoc item kw value)
    (dissoc item kw)))

(reg-event-db
 ::mi/set-item-rarity
 item-interceptors
 (fn [item [_ item-type-str]]
   (assoc item ::mi/rarity (keyword item-type-str))))

(reg-event-db
 ::mi/set-item-damage-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-damage-bonus bonus)))

(reg-event-db
 ::mi/set-item-attack-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-attack-bonus bonus)))

(reg-event-db
 ::mi/set-item-ac-bonus
 item-interceptors
 (fn [item [_ bonus]]
   (set-value item ::mi/magical-ac-bonus bonus)))

#_ ;; orphaned re-export aliases — all callers use event-utils/ directly now
(def mod-cfg event-utils/mod-cfg)
#_(def mod-key event-utils/mod-key)
#_(def compare-mod-keys event-utils/compare-mod-keys)
#_(def default-mod-set event-utils/default-mod-set)

(doseq [toggle-mod [:damage-resistance :damage-vulnerability :damage-immunity :condition-immunity]]
  (reg-event-db
   (keyword "orcpub.dnd.e5.magic-items" (str "toggle-" (name toggle-mod)))
   item-interceptors
   (fn [item [_ type]]
     (update-in item
                [::mi/internal-modifiers
                 toggle-mod
                 type]
                not))))

(reg-event-db
 ::mi/set-item
 item-interceptors
 (fn [_ [_ item]]
   item))

(reg-event-db
 ::e5/set-plugins
 plugins-interceptors
 (fn [_ [_ plugins]]
   plugins))

(reg-event-fx
 ::e5/export-plugin
 (fn [_ [_ name plugin]]
   ;; Validate before export to catch bugs early
   (let [validation (import-val/validate-before-export plugin)]
     (cond
       ;; Has missing required fields - show modal for user decision
       (:has-missing-required-fields validation)
       (do
         (js/console.warn "Export validation found missing required fields for" name ":")
         (js/console.warn (clj->js (:missing-fields-issues validation)))
         {:dispatch [:show-export-warning-modal
                     {:name name
                      :plugin plugin
                      :issues (:missing-fields-issues validation)
                      :warnings (:warnings validation)}]})

       ;; Valid - proceed with export
       (:valid validation)
       (do
         ;; Log warnings if any
         (when (seq (:warnings validation))
           (js/console.warn "Export warnings for" name ":")
           (doseq [warning (:warnings validation)]
             (js/console.warn " " warning)))

         ;; Proceed with export
         (let [blob (js/Blob.
                     (clj->js [(str plugin)])
                     (clj->js {:type "text/plain;charset=utf-8"}))]
           (js/saveAs blob (str name ".orcbrew"))
           (if (seq (:warnings validation))
             {:dispatch [:show-warning-message
                        (str "Plugin '" name "' exported with warnings. Check console for details.")]}
             {})))

       ;; Other validation failure - don't export
       :else
       (do
         (js/console.error "Export validation failed for" name ":")
         (js/console.error (:errors validation))
         {:dispatch [:show-error-message
                    (str "Cannot export '" name "' - contains invalid data. Check console for details.")]})))))

;; Export warning modal events
(reg-event-db
 :show-export-warning-modal
 (fn [db [_ {:keys [name plugin issues warnings]}]]
   (assoc db :export-warning
          {:active? true
           :name name
           :plugin plugin
           :issues issues
           :warnings warnings})))

(reg-event-db
 :cancel-export
 (fn [db _]
   (assoc db :export-warning {:active? false})))

(reg-event-fx
 :export-anyway
 (fn [{:keys [db]} _]
   (let [{:keys [name plugin]} (:export-warning db)
         ;; Fill missing fields with dummy data
         filled-plugin (import-val/fill-missing-for-export plugin)
         blob (js/Blob.
               (clj->js [(str filled-plugin)])
               (clj->js {:type "text/plain;charset=utf-8"}))]
     (js/saveAs blob (str name ".orcbrew"))
     {:db (assoc db :export-warning {:active? false})
      :dispatch [:show-warning-message
                 (str "Plugin '" name "' exported with placeholder data for missing fields.")]})))

;; Export all homebrew plugins as .orcbrew file.
(reg-event-fx
 ::e5/export-all-plugins
 (fn [{:keys [db]} _]
   (let [all-plugins (:plugins db)
         ;; Validate each plugin
         validations (into {}
                          (map (fn [[name plugin]]
                                 [name (import-val/validate-before-export plugin)])
                               all-plugins))
         has-errors (some (fn [[_ v]] (not (:valid v))) validations)
         has-warnings (some (fn [[_ v]] (seq (:warnings v))) validations)]

     (when (or has-errors has-warnings)
       (js/console.warn "Export validation results:")
       (doseq [[name validation] validations]
         (when-not (:valid validation)
           (js/console.error "Plugin" name "has errors:" (:errors validation)))
         (when (seq (:warnings validation))
           (js/console.warn "Plugin" name "has warnings:" (:warnings validation)))))

     (if has-errors
       {:dispatch [:show-error-message
                  "Cannot export all plugins - some contain invalid data. Check console for details."]}

       (let [blob (js/Blob.
                   (clj->js [(str all-plugins)])
                   (clj->js {:type "text/plain;charset=utf-8"}))]
         (js/saveAs blob "all-content.orcbrew")
         (if has-warnings
           {:dispatch [:show-warning-message
                      "All plugins exported with some warnings. Check console for details."]}
           {}))))))

(reg-event-fx
  ::e5/export-plugin-pretty-print
  (fn [_ [_ name plugin]]
    (let [blob (js/Blob.
                 (clj->js [(with-out-str (pprint/pprint plugin))])
                 (clj->js {:type "text/plain;charset=utf-8"}))]
      (js/saveAs blob (str name ".orcbrew"))
      {})))
;; Export all homebrew plugins as pretty-printed .orcbrew file.
(reg-event-fx
  ::e5/export-all-plugins-pretty-print
  (fn [{:keys [db]} _]
    (let [blob (js/Blob.
                 (clj->js [(with-out-str (pprint/pprint (:plugins db)))])
                 (clj->js {:type "text/plain;charset=utf-8"}))]
      (js/saveAs blob "all-content.orcbrew")
      {})))

(reg-event-fx
 ::e5/delete-plugin
 (fn [{:keys [db]} [_ name]]
   {:dispatch [::e5/set-plugins (-> db :plugins (dissoc name))]}))

(reg-event-fx
 ::e5/toggle-plugin
 (fn [{:keys [db]} [_ name]]
   {:dispatch [::e5/set-plugins (-> db :plugins (update-in [name :disabled?] not))]}))

(reg-event-fx
 ::e5/toggle-plugin-item
 (fn [{:keys [db]} [_ plugin-name type-key key]]
   {:dispatch [::e5/set-plugins (-> db :plugins (update-in [plugin-name type-key key :disabled?] not))]}))

(defn clean-plugin-errors
  "DEPRECATED: Use import-validation/validate-import instead.
   Kept for backward compatibility only."
  [plugin-text]
  (-> plugin-text
      (clojure.string/replace #"disabled\?\s+nil" "disabled? false") ; disabled? nil - replace w/disabled? false
      (clojure.string/replace #"(?m)nil nil, " "") ; nil nil,  - find+remove
      (clojure.string/replace #":\w+\snil" "") ; :[a-0] nil - find+remove
      (clojure.string/replace #"\{\"\"\s*\{:orcpub\.dnd\.e5" "{\"Default Option Source\" {:orcpub.dnd.e5")
      (clojure.string/replace #":option-pack\s*\"\s*\"\s*," ":option-pack \"Default Option Source\",") ;:option-pack "",
      ))

;; ============================================================================
;; Import Log Events
;; ============================================================================

(reg-event-db
 :set-import-log
 (fn [db [_ {:keys [name changes errors skipped-items]}]]
   (assoc db :import-log
          {:panel-shown? (or (seq changes) (seq errors) (seq skipped-items))
           :changes (or changes [])
           :errors (or errors [])
           :skipped-items (or skipped-items [])
           :import-name name
           :timestamp (js/Date.)})))

(reg-event-db
 :toggle-import-log-panel
 (fn [db _]
   (update-in db [:import-log :panel-shown?] not)))

(reg-event-db
 :close-import-log-panel
 (fn [db _]
   (assoc-in db [:import-log :panel-shown?] false)))

(reg-event-db
 :clear-import-log
 (fn [db _]
   (assoc db :import-log {:panel-shown? false
                          :changes []
                          :errors []
                          :skipped-items []
                          :import-name nil
                          :timestamp nil})))

;; ============================================================================
;; Import Plugin Events
;; ============================================================================

(reg-event-fx
 ::e5/import-plugin
 (fn [{:keys [db]} [_ plugin-name plugin-text]]
   ;; Use comprehensive validation with progressive import strategy
   ;; Pass existing plugins for duplicate key detection
   (let [result (import-val/validate-import plugin-text {:strategy :progressive
                                                         :auto-clean true
                                                         :existing-plugins (:plugins db)
                                                         :import-source-name plugin-name})
         user-message (import-val/format-import-result result)
         has-conflicts? (or (seq (get-in result [:key-conflicts :internal-conflicts]))
                           (seq (get-in result [:key-conflicts :external-conflicts])))]

     ;; Log detailed results to console for debugging
     (js/console.log "Import validation result:" (clj->js result))
     (js/console.log "Key conflicts:" (clj->js (:key-conflicts result)))
     (js/console.log "Has conflicts?:" has-conflicts?)

     (cond
       ;; Parse error - cannot recover
       (:parse-error result)
       (do
         (js/console.error "Parse error:" (:error result))
         {:dispatch-n [[:show-error-message user-message]
                       [:set-import-log {:name plugin-name
                                         :changes (:changes result)
                                         :errors [(:error result)]
                                         :skipped-items []}]]})

       ;; Validation failed completely
       (and (not (:success result)) (:errors result))
       (do
         (js/console.error "Validation errors:" (clj->js (:errors result)))
         {:dispatch-n [[:show-error-message user-message]
                       [:set-import-log {:name plugin-name
                                         :changes (:changes result)
                                         :errors (:errors result)
                                         :skipped-items []}]]})

       ;; Key conflicts detected - show resolution modal
       (and (:success result) has-conflicts?)
       (do
         (js/console.log "Key conflicts detected, showing resolution modal")
         {:dispatch [:start-conflict-resolution
                     {:import-name plugin-name
                      :import-data (:data result)
                      :conflicts (:key-conflicts result)
                      :validation-result result}]})

       ;; Progressive import succeeded (may have skipped some items)
       (:success result)
       (let [plugin (:data result)
             is-multi-plugin (and (spec/valid? ::e5/plugins plugin)
                                 (not (spec/valid? ::e5/plugin plugin)))]

         ;; Log skipped items if any
         (when (:had-errors result)
           (js/console.warn "Skipped invalid items:")
           (doseq [item (:skipped-items result)]
             (js/console.warn "  " (:key item))
             (js/console.warn "    Errors:" (:errors item))))

         {:dispatch-n (cond-> []
                        ;; Set the plugins
                        true
                        (conj (if is-multi-plugin
                                [::e5/set-plugins (e5/merge-all-plugins (:plugins db) plugin)]
                                [::e5/set-plugins (assoc (:plugins db) plugin-name plugin)]))

                        ;; Show appropriate message
                        true
                        (conj [:show-warning-message user-message])

                        ;; Store import log for UI panel
                        true
                        (conj [:set-import-log {:name plugin-name
                                                :changes (:changes result)
                                                :errors []
                                                :skipped-items (:skipped-items result)
                                                :key-conflicts (:key-conflicts result)
                                                :key-warnings (:key-warnings result)}]))})

       ;; Unknown state
       :else
       {:dispatch [:show-error-message "Unknown import error. Check console for details."]}))))

;; Add a strict import option for users who want all-or-nothing behavior
(reg-event-fx
 ::e5/import-plugin-strict
 (fn [{:keys [db]} [_ plugin-name plugin-text]]
   (let [result (import-val/validate-import plugin-text {:strategy :strict
                                                         :auto-clean true
                                                         :existing-plugins (:plugins db)
                                                         :import-source-name plugin-name})
         user-message (import-val/format-import-result result)]

     (js/console.log "Strict import validation result:" (clj->js result))

     (if (:success result)
       (let [plugin (:data result)]
         {:dispatch-n [[::e5/set-plugins (if (= :multi-plugin (:strategy result))
                                           (e5/merge-all-plugins (:plugins db) plugin)
                                           (assoc (:plugins db) plugin-name plugin))]
                      [:show-warning-message user-message]]})

       {:dispatch [:show-error-message user-message]}))))

;; ============================================================================
;; Conflict Resolution Events
;; ============================================================================

(defn build-conflict-list
  "Build a list of conflicts with unique IDs for UI tracking.
   Combines internal and external conflicts with suggested renames."
  [{:keys [internal-conflicts external-conflicts]} import-name]
  (let [;; Internal conflicts: same key appears in multiple sources within the import
        internal (map-indexed
                  (fn [idx {:keys [key content-type content-type-name sources]}]
                    {:id (str "internal-" idx)
                     :type :internal
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :sources sources
                     ;; For internal, user picks which source to rename
                     :suggested-renames (mapv (fn [{:keys [source name]}]
                                                {:source source
                                                 :new-key (import-val/generate-new-key key source)})
                                              sources)})
                  internal-conflicts)

        ;; External conflicts: imported key conflicts with existing key
        external (map-indexed
                  (fn [idx {:keys [key content-type content-type-name
                                   import-source import-name
                                   existing-source existing-name]}]
                    {:id (str "external-" idx)
                     :type :external
                     :key key
                     :content-type content-type
                     :content-type-name content-type-name
                     :import-source import-source
                     :import-name import-name
                     :existing-source existing-source
                     :existing-name existing-name
                     ;; Suggested rename for the import
                     :suggested-new-key (import-val/generate-new-key key import-source)})
                  external-conflicts)]
    (vec (concat internal external))))

(reg-event-db
 :start-conflict-resolution
 (fn [db [_ {:keys [import-name import-data conflicts validation-result]}]]
   (let [conflict-list (build-conflict-list conflicts import-name)]
     (assoc db :conflict-resolution
            {:active? true
             :import-name import-name
             :import-data import-data
             :conflicts conflict-list
             :decisions {}
             :validation-result validation-result}))))

(reg-event-db
 :set-conflict-decision
 (fn [db [_ conflict-id decision]]
   ;; decision is {:action :rename-import | :skip | :keep-both, :new-key :foo, :source "..."}
   (assoc-in db [:conflict-resolution :decisions conflict-id] decision)))

(reg-event-db
 :rename-all-conflicts
 (fn [db _]
   (let [conflicts (get-in db [:conflict-resolution :conflicts])
         decisions (into {}
                         (map (fn [{:keys [id suggested-new-key suggested-renames
                                           import-source sources]}]
                                [id {:action :rename-import
                                     :source (or import-source (-> sources first :source))
                                     :new-key (or suggested-new-key
                                                  (-> suggested-renames first :new-key))}])
                              conflicts))]
     (assoc-in db [:conflict-resolution :decisions] decisions))))

(reg-event-db
 :cancel-conflict-resolution
 (fn [db _]
   (assoc db :conflict-resolution
          {:active? false
           :import-name nil
           :import-data nil
           :conflicts []
           :decisions {}
           :validation-result nil})))

(reg-event-fx
 :apply-conflict-resolutions
 (fn [{:keys [db]} _]
   (let [{:keys [import-name import-data conflicts decisions validation-result]}
         (:conflict-resolution db)

         ;; Build list of renames from decisions
         renames (reduce
                  (fn [acc {:keys [id type key content-type] :as conflict}]
                    (let [decision (get decisions id)]
                      (cond
                        ;; User chose to rename the import
                        (= :rename-import (:action decision))
                        (conj acc {:source (:source decision)
                                   :content-type content-type
                                   :from key
                                   :to (:new-key decision)})

                        ;; Skip this item (don't import it)
                        (= :skip (:action decision))
                        acc  ; Will handle removal separately

                        ;; Keep both (no rename - allows override)
                        :else
                        acc)))
                  []
                  conflicts)

         ;; Apply renames to import data
         renamed-data (if (seq renames)
                        (import-val/apply-key-renames import-data renames)
                        import-data)

         ;; Check if this is a multi-plugin
         is-multi-plugin (and (spec/valid? ::e5/plugins renamed-data)
                              (not (spec/valid? ::e5/plugin renamed-data)))]

     (js/console.log "Applying conflict resolutions:" (clj->js {:renames renames}))

     {:db (assoc db :conflict-resolution
                 {:active? false
                  :import-name nil
                  :import-data nil
                  :conflicts []
                  :decisions {}
                  :validation-result nil})
      :dispatch-n [;; Set the plugins with renamed data
                   (if is-multi-plugin
                     [::e5/set-plugins (e5/merge-all-plugins (:plugins db) renamed-data)]
                     [::e5/set-plugins (assoc (:plugins db) import-name renamed-data)])

                   ;; Show success message
                   [:show-warning-message
                    (str "✅ Import successful"
                         (when (seq renames)
                           (str "\n\nRenamed " (count renames) " key(s) to resolve conflicts.")))]

                   ;; Store import log
                   [:set-import-log {:name import-name
                                     :changes (concat (:changes validation-result)
                                                      (mapv #(assoc % :type :key-renamed) renames))
                                     :errors []
                                     :skipped-items (:skipped-items validation-result)}]]})))

(reg-event-db
 ::spells/set-spell
 spell-interceptors
 (fn [_ [_ spell]]
   spell))

(reg-event-db
 ::monsters/set-monster
 monster-interceptors
 (fn [_ [_ monster]]
   monster))

(reg-event-db
 ::encounters/set-encounter
 encounter-interceptors
 (fn [_ [_ encounter]]
   encounter))

(reg-event-db
 ::combat/set-combat
 combat-interceptors
 (fn [_ [_ combat]]
   combat))

(reg-event-db
 ::bg5e/set-background
 background-interceptors
 (fn [_ [_ background]]
   background))

(reg-event-db
 ::langs5e/set-language
 language-interceptors
 (fn [_ [_ language]]
   language))

(reg-event-db
 ::class5e/set-invocation
 invocation-interceptors
 (fn [_ [_ invocation]]
   invocation))

(reg-event-db
 ::class5e/set-boon
 boon-interceptors
 (fn [_ [_ boon]]
   boon))

(reg-event-db
 ::selections5e/set-selection
 selection-interceptors
 (fn [_ [_ selection]]
   selection))

(reg-event-db
 ::feats5e/set-feat
 feat-interceptors
 (fn [_ [_ feat]]
   feat))

(reg-event-db
 ::race5e/set-race
 race-interceptors
 (fn [_ [_ race]]
   race))

(reg-event-db
 ::race5e/set-subrace
 subrace-interceptors
 (fn [_ [_ subrace]]
   subrace))

(reg-event-db
 ::class5e/set-subclass
 subclass-interceptors
 (fn [_ [_ subclass]]
   subclass))

(reg-event-db
 ::class5e/set-class
 class-interceptors
 (fn [_ [_ class]]
   class))

(reg-event-fx
 ::mi/reset-item
 (fn [_ _]
   {:dispatch [::mi/set-item
               {::mi/type :wondrous-item
                ::mi/rarity :common}]}))

(reg-event-fx
 ::spells/reset-spell
 (fn [_ _]
   {:dispatch [::spells/set-spell
               default-spell]}))

(reg-event-fx
 ::monsters/reset-monster
 (fn [_ _]
   {:dispatch [::monsters/set-monster
               default-monster]}))

(reg-event-fx
 ::encounters/reset-encounter
 (fn [_ _]
   {:dispatch [::encounters/set-encounter
               default-encounter]}))

(reg-event-fx
 ::combat/reset-combat
 (fn [_ _]
   {:dispatch [::combat/set-combat
               default-combat]}))

(reg-event-fx
 ::bg5e/reset-background
 (fn [_ _]
   {:dispatch [::bg5e/set-background
               default-background]}))

(reg-event-fx
 ::langs5e/reset-language
 (fn [_ _]
   {:dispatch [::langs5e/set-language
               default-language]}))

(reg-event-fx
 ::class5e/reset-invocation
 (fn [_ _]
   {:dispatch [::class5e/set-invocation
               default-invocation]}))

(reg-event-fx
 ::class5e/reset-boon
 (fn [_ _]
   {:dispatch [::class5e/set-boon
               default-boon]}))

(reg-event-fx
 ::selections5e/reset-selection
 (fn [_ _]
   {:dispatch [::selections5e/set-selection
               default-selection]}))

(reg-event-fx
 ::feats5e/reset-feat
 (fn [_ _]
   {:dispatch [::feats5e/set-feat
               default-feat]}))

(reg-event-fx
 ::race5e/reset-race
 (fn [_ _]
   {:dispatch [::race5e/set-race
               default-race]}))

(reg-event-fx
 ::race5e/reset-subrace
 (fn [_ _]
   {:dispatch [::race5e/set-subrace
               default-subrace]}))

(reg-event-fx
 ::class5e/reset-subclass
 (fn [_ _]
   {:dispatch [::class5e/set-subclass
               default-subclass]}))

(reg-event-fx
 ::class5e/reset-class
 (fn [_ _]
   {:dispatch [::class5e/set-class
               default-class]}))

(defn reg-new-homebrew [event set-event default-val route]
  (reg-event-fx
   event
   (fn [_ [_ option-pack option]]
     {:dispatch-n [[set-event (-> default-val
                                  (assoc :option-pack option-pack)
                                  (merge option))]
                   [:route route]]})))

(defn reg-option-selections [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-selection"))
   interceptors
   (fn [option]
     (update option :level-selections (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-type"))
   interceptors
   (fn [option [_ index type]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :type] type))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-level"))
   interceptors
   (fn [option [_ index level]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :level] level))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-selection-num"))
   interceptors
   (fn [option [_ index num]]
     (cond-> option
       (nil? (:level-selections option)) (assoc :level-selections [])
       true (assoc-in [:level-selections index :num] num))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-selection"))
   interceptors
   (fn [option [_ index]]
     (update option :level-selections common/remove-at-index index))))

(reg-option-selections "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-selections "class" ::class5e/builder-item class-interceptors)

(defn reg-option-modifiers [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-modifier"))
   interceptors
   (fn [option]
     (update option :level-modifiers (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-type"))
   interceptors
   (fn [option [_ index type]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :type] type))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-level"))
   interceptors
   (fn [option [_ index level]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :level] level))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-modifier-value"))
   interceptors
   (fn [option [_ index value]]
     (cond-> option
       (nil? (:level-modifiers option)) (assoc :level-modifiers [])
       true (assoc-in [:level-modifiers index :value] value))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-modifier"))
   interceptors
   (fn [option [_ index]]
     (update option :level-modifiers common/remove-at-index index))))

(reg-option-modifiers "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-modifiers "class" ::class5e/builder-item class-interceptors)

(reg-event-db
 ::race5e/set-subrace-spell-level
 subrace-interceptors
 (fn [subrace [_ index level]]
   (cond-> subrace
     (nil? (:spells subrace)) (assoc :spells [])
     true (assoc-in [:spells index :level] level))))

(reg-event-db
 ::race5e/set-subrace-spell-value
 subrace-interceptors
 (fn [subrace [_ index value]]
   (cond-> subrace
     (nil? (:spells subrace)) (assoc :spells [])
     true (assoc-in [:spells index :value] value))))

(reg-event-db
 ::race5e/delete-subrace-spell
 subrace-interceptors
 (fn [subrace [_ index]]
   (update subrace :spells common/remove-at-index index)))

(reg-event-db
 ::race5e/set-race-spell-level
 race-interceptors
 (fn [race [_ index level]]
   (cond-> race
     (nil? (:spells race)) (assoc :spells [])
     true (assoc-in [:spells index :level] level))))

(reg-event-db
 ::race5e/set-race-spell-value
 race-interceptors
 (fn [race [_ index value]]
   (cond-> race
     (nil? (:spells race)) (assoc :spells [])
     true (assoc-in [:spells index :value] value))))

(reg-event-db
 ::race5e/delete-race-spell
 race-interceptors
 (fn [race [_ index]]
   (update race :spells common/remove-at-index index)))

(defn reg-option-traits [option-name option-key interceptors]
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "add-" option-name "-trait"))
   interceptors
   (fn [option]
     (update option :traits (fn [t] (if (vector? t) (conj t {}) [{}])))))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-name"))
   interceptors
   (fn [option [_ index name]]
     (assoc-in option [:traits index :name] name)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-type"))
   interceptors
   (fn [option [_ index type]]
     (assoc-in option [:traits index :type] type)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-level"))
   interceptors
   (fn [option [_ index level]]
     (assoc-in option [:traits index :level] level)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "edit-" option-name "-trait-description"))
   interceptors
   (fn [option [_ index description]]
     (assoc-in option [:traits index :description] description)))
  (reg-event-db
   (keyword "orcpub.dnd.e5"
            (str "delete-" option-name "-trait"))
   interceptors
   (fn [option [_ index]]
     (update option :traits common/remove-at-index index))))

(reg-option-traits "monster" ::monsters/builder-item monster-interceptors)
(reg-option-traits "subrace" ::race5e/subrace-builder-item subrace-interceptors)
(reg-option-traits "subclass" ::class5e/subclass-builder-item subclass-interceptors)
(reg-option-traits "class" ::class5e/builder-item class-interceptors)
(reg-option-traits "race" ::race5e/builder-item race-interceptors)
(reg-option-traits "background" ::bg5e/builder-item background-interceptors)

(reg-new-homebrew
 ::spells/new-spell
 ::spells/set-spell
 default-spell
 routes/dnd-e5-spell-builder-page-route)

(reg-new-homebrew
 ::monsters/new-monster
 ::monsters/set-monster
 default-monster
 routes/dnd-e5-monster-builder-page-route)

(reg-new-homebrew
 ::encounters/new-encounter
 ::encounters/set-encounter
 default-encounter
 routes/dnd-e5-encounter-builder-page-route)

(reg-new-homebrew
 ::bg5e/new-background
 ::bg5e/set-background
 default-background
 routes/dnd-e5-background-builder-page-route)

(reg-new-homebrew
 ::langs5e/new-language
 ::langs5e/set-language
 default-language
 routes/dnd-e5-language-builder-page-route)

(reg-new-homebrew
 ::class5e/new-invocation
 ::class5e/set-invocation
 default-invocation
 routes/dnd-e5-invocation-builder-page-route)

(reg-new-homebrew
 ::selections5e/new-selection
 ::selections5e/set-selection
 default-selection
 routes/dnd-e5-selection-builder-page-route)

(reg-new-homebrew
 ::class5e/new-boon
 ::class5e/set-boon
 default-boon
 routes/dnd-e5-boon-builder-page-route)

(reg-new-homebrew
 ::feats5e/new-feat
 ::feats5e/set-feat
 default-feat
 routes/dnd-e5-feat-builder-page-route)

(reg-new-homebrew
 ::race5e/new-race
 ::race5e/set-race
 default-race
 routes/dnd-e5-race-builder-page-route)

(reg-new-homebrew
 ::race5e/new-subrace
 ::race5e/set-subrace
 default-subrace
 routes/dnd-e5-subrace-builder-page-route)

(reg-new-homebrew
 ::class5e/new-subclass
 ::class5e/set-subclass
 default-subclass
 routes/dnd-e5-subclass-builder-page-route)

(reg-new-homebrew
 ::class5e/new-class
 ::class5e/set-class
 default-class
 routes/dnd-e5-class-builder-page-route)

(reg-event-fx
 ::mi/new-item
 (fn [_ _]
   {:dispatch-n [[::mi/reset-item]
                 [:route routes/dnd-e5-item-builder-page-route]]}))

(reg-event-db
 ::mi/set-ability-mod-type
 item-interceptors
 (fn [item [_ ability-kw type]]
   (assoc-in item
             [::mi/internal-modifiers
              :ability
              ability-kw
              :type]
             (keyword type))))

(defn set-mod-value [item mods-path mod-key value]
  (if value
    (assoc-in item
              (conj mods-path
                    mod-key
                    :value)
              value)
    (update-in item
               mods-path
               dissoc
               mod-key)))

(reg-event-db
 ::mi/set-ability-mod-value
 item-interceptors
 (fn [item [_ ability-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers
                   :ability]
                  ability-kw
                  value)))

(reg-event-db
 ::mi/set-speed-mod-type
 item-interceptors
 (fn [item [_ speed-type-kw mod-type]]
   (assoc-in item
             [::mi/internal-modifiers
              speed-type-kw
              :type]
             (keyword mod-type))))

(reg-event-db
 ::mi/set-speed-mod-value
 item-interceptors
 (fn [item [_ speed-type-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers]
                  speed-type-kw
                  value)))

(reg-event-db
 ::mi/set-save-mod-value
 item-interceptors
 (fn [item [_ ability-kw value]]
   (set-mod-value item
                  [::mi/internal-modifiers
                   :save]
                  ability-kw
                  value)))

(reg-event-db
 ::mi/toggle-subtype
 item-interceptors
 (fn [item [_ type]]
   (mi/apply-subtype-toggle item type)))

(reg-event-fx
 ::char5e/open-character
 (fn [_ [_ character]]
   {:dispatch-n [[:set-character character]
                 [:route routes/dnd-e5-char-builder-route]]}))

(reg-event-fx
 :route-to-login
 (fn [_ _]
   {:dispatch [:route routes/login-page-route {:secure? true :no-return? true}]}))

(reg-event-db
 ::char5e/show-options
 (fn [db [_ component]]
   (assoc db
          ::char5e/options-shown? true
          ::char5e/options-component component)))

(reg-event-db
 ::char5e/hide-options
 (fn [db _]
   (assoc db ::char5e/options-shown? false)))

#_ ;; never dispatched — print UI not wired
(reg-event-db
 ::char5e/toggle-character-sheet-print
 (fn [db _]
   (update db ::char5e/exclude-character-sheet-print? not)))

(reg-event-db
 ::char5e/toggle-spell-cards-print
 (fn [db _]
   (update db ::char5e/exclude-spell-cards-print? not)))

#_ ;; never dispatched — print UI not wired
(reg-event-db
 ::char5e/toggle-spell-cards-by-level
 (fn [db _]
   (update db ::char5e/exclude-spell-cards-by-level? not)))

(reg-event-db
 ::char5e/toggle-spell-cards-by-dc-mod
 (fn [db _]
   (update db ::char5e/exclude-spell-cards-by-dc-mod? not)))

(reg-event-db
 ::char5e/toggle-large-abilities-print
 (fn [db _]
   (update db ::char5e/print-large-abilities? not)))

(reg-event-db
 ::char5e/set-print-character-sheet-style?
 (fn [db [_ id]]
   (assoc-in db [::char5e/print-character-sheet-style?] id)))

(reg-event-db
 ::char5e/toggle-known-spells-print
 (fn [db _]
   (update db ::char5e/print-prepared-spells? not)))

(reg-event-db
 ::char5e/show-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::char5e/delete-confirmation-shown? id] true)))

(reg-event-db
 ::char5e/hide-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::char5e/delete-confirmation-shown? id] false)))

(reg-event-db
 ::mi/show-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::mi/delete-confirmation-shown? id] true)))

(reg-event-db
 ::mi/hide-delete-confirmation
 (fn [db [_ id]]
   (assoc-in db [::mi/delete-confirmation-shown? id] false)))

(reg-event-db
 ::char5e/show-delete-plugin-confirmation
 (fn [db _]
   (assoc-in db [::char5e/delete-plugin-confirmation-shown?] true)))

(reg-event-db
 ::char5e/hide-delete-plugin-confirmation
 (fn [db _]
   (assoc-in db [::char5e/delete-plugin-confirmation-shown?] false)))

;; Base class keys that are always available (not from plugins)
(def base-class-keys
  #{:barbarian :bard :cleric :druid :fighter :monk :paladin :ranger :rogue :sorcerer :warlock :wizard})

(defn remove-plugin-classes
  "Removes classes from character that aren't base classes.
   If no classes remain, sets to Barbarian. Preserves all other character data."
  [character]
  (let [current-classes (get-in character [::entity/options :class])
        valid-classes (vec (filter #(base-class-keys (::entity/key %)) current-classes))]
    (if (seq valid-classes)
      ;; Keep only valid base classes
      (assoc-in character [::entity/options :class] valid-classes)
      ;; No valid classes - set to Barbarian
      (char5e/set-class character :barbarian 0 (class5e/barbarian-option [] {} {} {} {})))))

(reg-event-db
 ::char5e/remove-plugin-classes
 character-interceptors
 (fn [character _]
   (remove-plugin-classes character)))

(reg-event-fx
 ::char5e/delete-all-plugins
 (fn [{:keys [db]} _]
   ;; Reset to default empty plugins state.
   ;; DO NOT call remove-plugin-classes - let the character keep its
   ;; references so the Missing Content Warning can properly show what's missing.
   ;; The warning system will help users understand which homebrew they need
   ;; to re-import to restore their character.
   {:dispatch-n [[::e5/set-plugins {"Default Option Source" {}}]
                 [::char5e/hide-delete-plugin-confirmation]]}))

(reg-event-fx
 ::char5e/don-armor
 (fn [{:keys [db]} [_ id armor-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/worn-armor]
                                armor-kw))))

(reg-event-fx
 ::char5e/wield-shield
 (fn [{:keys [db]} [_ id shield-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/wielded-shield]
                                shield-kw))))

(reg-event-fx
 ::char5e/wield-main-hand-weapon
 (fn [{:keys [db]} [_ id weapon-kw]]
   (update-character-fx db id #(update
                                %
                                ::entity/values
                                assoc
                                ::char5e/main-hand-weapon
                                weapon-kw
                                ::char5e/off-hand-weapon
                                :none))))

(reg-event-fx
 ::char5e/wield-off-hand-weapon
 (fn [{:keys [db]} [_ id weapon-kw]]
   (update-character-fx db id #(assoc-in
                                %
                                [::entity/values
                                 ::char5e/off-hand-weapon]
                                weapon-kw))))

#_ ;; never dispatched — attunement UI not wired
(reg-event-fx
 ::char5e/attune-magic-item
 (fn [{:keys [db]} [_ id i weapon-kw]]
   (update-character-fx db id #(update-in
                                %
                                [::entity/values
                                 ::char5e/attuned-magic-items]
                                (fn [items]
                                  (assoc
                                   (or items [:none :none :none])
                                   i
                                   weapon-kw))))))

(reg-event-db
 :close-srd-message
 (fn [db [_]]
   (assoc db :srd-message-closed? true)))

(reg-event-db
 ::char5e/add-answer
 (fn [db [_ question answer]]
   (update db
           ::char5e/newb-char-data
           char-dec5e/add-answer
           question
           answer)))

(reg-event-db
 ::char5e/next-question
 (fn [db _]
   (-> db
       (assoc ::char5e/current-question
              (char-dec5e/next-question (::char5e/newb-char-data db)))
       (update ::char5e/question-history
               (fn [{:keys [questions newb-char-data]}]
                 {:questions (conj questions (get db ::char5e/current-question (char-dec5e/next-question {})))
                  :newb-char-data (conj newb-char-data (::char5e/newb-char-data db))})))))

(reg-event-db
 ::char5e/previous-question
 (fn [db _]
   (let [{:keys [questions newb-char-data] :as hist} (::char5e/question-history db)]
     (assoc
      db
      ::char5e/current-question (peek questions)
      ::char5e/newb-char-data (peek newb-char-data)
      ::char5e/question-history {:questions (pop questions)
                                 :newb-char-data (pop newb-char-data)}))))
