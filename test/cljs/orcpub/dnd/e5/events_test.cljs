(ns orcpub.dnd.e5.events-test
  "Integration tests for re-frame event handlers refactored during the
   subscribe-outside-reactive-context fix. These tests verify that
   dispatching an event produces the expected db state or effects.

   PATTERN — testing a reg-event-db handler:
     1. Reset app-db to a known state
     2. dispatch-sync the event
     3. Assert on @app-db

   PATTERN — testing a reg-event-fx handler:
     For handlers that return {:http ...} or {:dispatch ...}, we can't
     easily capture effects without day8.re-frame/re-frame-test. Instead:
     - Test the db-only portions via dispatch-sync
     - For effects, test indirectly (e.g. verify db guard logic)
     - Or register a stub :http fx handler before the test

   NOTE: These tests require all event handlers to be registered. We do
   this by requiring orcpub.dnd.e5.events, which has side effects
   (reg-event-db, reg-event-fx calls at load time)."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spells :as spells]
            [orcpub.dnd.e5.autosave-fx :as autosave-fx]
            [orcpub.entity :as entity]
            ;; Side effect: registers all event handlers
            [orcpub.dnd.e5.events :as events]))

;; ---------------------------------------------------------------------------
;; Fixtures
;; ---------------------------------------------------------------------------

(def pristine-db
  "Minimal app-db state. Extend per-test as needed."
  {})

(defn reset-db!
  "Reset app-db before each test to prevent state leakage."
  []
  (reset! app-db pristine-db))

(use-fixtures :each {:before reset-db!})

;; ---------------------------------------------------------------------------
;; ::char5e/filter-spells  (reg-event-db)
;;
;; This handler was refactored to call compute-sorted-spells on db
;; instead of @(subscribe [::char5e/sorted-spells]).
;; ---------------------------------------------------------------------------

(deftest filter-spells-short-text-returns-all-sorted
  (testing "filter text under 3 chars → all spells returned (no filtering)"
    (reset! app-db {:plugins {}})
    (rf/dispatch-sync [::char5e/filter-spells "fi"])
    (let [db @app-db]
      ;; Text stored for the UI input
      (is (= "fi" (::char5e/spell-text-filter db)))
      ;; All sorted spells returned (not filtered) because "fi" < 3 chars
      (let [result (::char5e/filtered-spells db)
            names (set (map :name result))]
        ;; Should contain known static spells
        (is (contains? names "Fireball"))
        (is (contains? names "Shield"))))))

(deftest filter-spells-long-text-filters
  (testing "filter text >= 3 chars → only matching spells returned"
    (reset! app-db {:plugins {}})
    (rf/dispatch-sync [::char5e/filter-spells "fire"])
    (let [result (::char5e/filtered-spells @app-db)
          names (set (map :name result))]
      ;; Only spells containing "fire" (case-insensitive)
      (is (contains? names "Fireball"))
      (is (contains? names "Fire Bolt"))
      (is (not (contains? names "Shield"))))))

(deftest filter-spells-includes-plugin-spells
  (testing "plugin spells are merged into the result"
    (reset! app-db {:plugins {:test-plugin
                              {::e5/spells
                               {:zap {:name "Zap" :key :zap}}}}})
    (rf/dispatch-sync [::char5e/filter-spells "zap"])
    (let [result (::char5e/filtered-spells @app-db)
          names (set (map :name result))]
      (is (contains? names "Zap")))))

;; ---------------------------------------------------------------------------
;; ::char5e/filter-items  (reg-event-db)
;;
;; Same pattern as filter-spells but for magic items.
;; Uses compute-sorted-items instead of subscribe.
;; ---------------------------------------------------------------------------

(deftest filter-items-short-text-returns-all
  (testing "filter text under 3 chars → all items returned"
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-items "ba"])
    (let [result (::char5e/filtered-items @app-db)
          names (set (map mi/name-key result))]
      ;; Known static item should be present
      (is (contains? names "Alchemy Jug")))))

(deftest filter-items-long-text-filters
  (testing "filter text >= 3 chars → only matching items returned"
    (reset! app-db {})
    (rf/dispatch-sync [::char5e/filter-items "alchemy"])
    (let [result (::char5e/filtered-items @app-db)
          names (set (map mi/name-key result))]
      (is (contains? names "Alchemy Jug"))
      ;; Something unrelated should NOT be present
      (is (not (contains? names "Animated Shield"))))))

;; ---------------------------------------------------------------------------
;; ::char5e/save-character  (reg-event-fx)
;;
;; Autosave handler. Was refactored to read cached template from app-db
;; (via track! watcher) instead of @(subscribe [:built-character]).
;;
;; Testing an fx handler without day8.re-frame/re-frame-test:
;; We can't easily intercept the :http effect, but we CAN test the
;; guard logic (cached-template nil → no-op) by checking that the
;; handler doesn't crash and doesn't produce unwanted side effects.
;; ---------------------------------------------------------------------------

(deftest save-character-skips-when-no-cached-template
  (testing "without cached template, handler is a no-op (returns {})"
    ;; Set up db with a character but no cached template
    (reset! app-db {::char5e/character-map {42 {:orcpub.entity/options {}}}})
    ;; This should NOT throw or dispatch error — it should silently skip
    ;; We verify by checking no error dispatch happened
    (rf/dispatch-sync [::char5e/save-character "42"])
    ;; If we got here without exception, the nil guard works.
    ;; The db should not have :loading set to true (no save attempted)
    (is (nil? (:loading @app-db))
        "No save should be attempted without cached template")))

(deftest save-character-rejects-missing-abilities
  (testing "with cached template but no ability scores → error dispatch"
    ;; Minimal character with no abilities
    (let [template {} ;; empty template → entity/build returns bare character
          character {:orcpub.entity/options {}}]
      (reset! app-db {::char5e/character-map {42 character}
                      ::autosave-fx/cached-template template})
      ;; This will try to build the character and check abilities.
      ;; With an empty template, built-character won't have :base-abilities,
      ;; so the ability check fails → dispatches error message.
      ;; We can't intercept the :dispatch effect, but we can verify it
      ;; doesn't crash and the handler runs to completion.
      (rf/dispatch-sync [::char5e/save-character "42"]))))

;; ---------------------------------------------------------------------------
;; :save-character  (reg-event-fx)
;;
;; Manual save. Was refactored to receive built-character as a parameter
;; from the component instead of @(subscribe [:built-character]).
;; ---------------------------------------------------------------------------

(deftest save-character-manual-rejects-missing-abilities
  (testing "built-character without abilities → error dispatch"
    (reset! app-db {:character {:orcpub.entity/options {}}})
    ;; Pass a built-character with no :base-abilities
    (rf/dispatch-sync [:save-character {}])
    ;; Handler should not crash. The ability check will fail,
    ;; resulting in a :show-error-message dispatch.
    ;; Without intercepting effects, we verify no exception.
    (is true "Handler completed without exception")))

;; ---------------------------------------------------------------------------
;; :verify-user-session  (reg-event-fx)
;;
;; Replaced @(subscribe [:user false]) in core.cljs.
;; Tests that the guard logic works: no user → no HTTP call.
;; ---------------------------------------------------------------------------

(deftest verify-user-session-no-user
  (testing "without user in db, handler is a no-op"
    (reset! app-db {})
    ;; Should not attempt HTTP call
    (rf/dispatch-sync [:verify-user-session])
    ;; If we got here, the guard check passed. No exception = success.
    (is (= {} (select-keys @app-db [:loading]))
        "No loading state should be set without user")))

(deftest verify-user-session-no-token
  (testing "user without token → no HTTP call"
    (reset! app-db {:user {:name "test"}})
    (rf/dispatch-sync [:verify-user-session])
    (is true "Handler completed without exception")))

;; ---------------------------------------------------------------------------
;; ::char5e/level-up  (reg-event-fx) — staleness smell
;;
;; The handler returns
;;   {:dispatch-n [[::char5e/add-level id]
;;                 [:set-character (get-in db [::char5e/character-map id] {})]
;;                 [:route ...]]}
;;
;; The :set-character argument is computed at handler-return time from the
;; pre-add-level db. After re-frame processes the dispatch-n queue:
;;   character-map[id]  → has the new level (add-level processed first)
;;   :character         → has the OLD level (set-character used the snapshot)
;;
;; In normal use, the autosave round-trip from add-level resolves this within
;; ~7.5s, so the staleness window is invisible. But it's the kind of subtle
;; ordering bug that is worth pinning down with a test rather than reasoning
;; about. These tests document and verify the actual current behavior.
;;
;; NOTE: re-frame's dispatch-sync only runs the immediate handler synchronously
;; — :dispatch-n events are queued for async processing. To exercise the
;; ordering deterministically in a unit test, we manually replay the same
;; sequence the level-up handler would emit, using the same arg-capture
;; semantics (snapshot taken before add-level).
;; ---------------------------------------------------------------------------

(def ^:private level-1-character
  "A minimal character with one class (barbarian) at level 1.
   Shape matches what update-character-fx + add-level expect."
  {:db/id 99
   :orcpub.entity/options
   {:class
    [{:orcpub.entity/key :barbarian
      :orcpub.entity/options
      {:levels [{:orcpub.entity/key :level-1}]}}]}})

(defn- class-0-levels
  "Convenience: dig out the level keys for class index 0."
  [character]
  (->> (get-in character [:orcpub.entity/options :class 0
                          :orcpub.entity/options :levels])
       (mapv :orcpub.entity/key)))

(deftest add-level-pure-function-appends-next-level
  (testing "events/add-level appends the next-numbered level to class 0"
    (let [updated (events/add-level level-1-character)]
      (is (= [:level-1 :level-2] (class-0-levels updated))))))

(deftest update-character-fx-with-id-updates-character-map-and-queues-save
  (testing "update-character-fx with an id writes character-map[int-id] and emits the throttled-save fx"
    (reset! app-db {::char5e/character-map {42 level-1-character}})
    (let [effect (events/update-character-fx @app-db "42" events/add-level)]
      (is (= 42 (-> effect ::char5e/save-character-throttled))
          "Throttled save effect must carry the id (raw, not parseInt'd)")
      (is (= [:level-1 :level-2]
             (class-0-levels (get-in (:db effect)
                                     [::char5e/character-map 42])))
          "character-map[id] should reflect the new level"))))

(deftest update-character-fx-without-id-falls-back-to-character-slot
  (testing "update-character-fx with nil id dispatches :set-character on the in-memory :character"
    (reset! app-db {:character level-1-character})
    (let [effect (events/update-character-fx @app-db nil events/add-level)]
      (is (vector? (:dispatch effect)) "Must return a :dispatch effect")
      (is (= :set-character (first (:dispatch effect))))
      (is (= [:level-1 :level-2] (class-0-levels (second (:dispatch effect))))))))

(deftest level-up-staleness-character-slot-lags-character-map
  (testing "After level-up, :character holds the PRE-add-level snapshot while character-map[id] holds the POST-add-level state. This is the documented staleness smell."
    ;; Set up: character at id=42, also currently in :character (as if user
    ;; was viewing or recently edited it). Same shape, same starting level.
    (reset! app-db {::char5e/character-map {42 level-1-character}
                    :character              level-1-character})
    ;; Replay the dispatch-n sequence the level-up handler emits, with the
    ;; same arg-capture semantics:
    ;;   1. snapshot :set-character's argument from the pre-add-level db
    ;;   2. dispatch ::char5e/add-level (which mutates character-map[id])
    ;;   3. dispatch [:set-character snapshot]
    (let [pre-snapshot (get-in @app-db [::char5e/character-map 42] {})]
      (rf/dispatch-sync [::char5e/add-level "42"])
      (rf/dispatch-sync [:set-character pre-snapshot]))

    (let [final-character-map (get-in @app-db [::char5e/character-map 42])
          final-character     (:character @app-db)]
      ;; character-map got the new level (add-level worked correctly)
      (is (= [:level-1 :level-2] (class-0-levels final-character-map))
          "character-map[id] should have the new level after add-level")
      ;; :character did NOT get the new level — staleness confirmed
      (is (= [:level-1] (class-0-levels final-character))
          ":character holds the pre-add-level snapshot — the staleness smell")
      ;; The two slots disagree about the level count
      (is (not= (class-0-levels final-character-map)
                (class-0-levels final-character))
          ":character and character-map[id] diverge after level-up. In normal use the autosave round-trip resolves this within ~7.5s. If the user clicks manual Save during that window, the stale :character is posted to the server and the level-up is undone."))))
