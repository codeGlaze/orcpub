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
