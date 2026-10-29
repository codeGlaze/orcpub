(ns orcpub.dnd.e5.homebrew-interceptor-test
  "The global interceptor: whatever an event does to the homebrew library, and however, what it
   leaves is made to fit the shape table. See homebrew-guard.md."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.homebrew-check :as homebrew-check]
            [orcpub.dnd.e5.homebrew-guard :as guard]
            [orcpub.dnd.e5.events :as events]))

(def ^:private race {:name "Hill" :key :hill :option-pack "P" :speed "25"})
(def ^:private lib {"P" {::e5/races {:hill race}}})

(defn- fx-of
  "The values of the `kind` effects in `effects`' :fx list, as a lazy seq."
  [effects kind]
  (for [[k v] (:fx effects) :when (= k kind)] v))

(deftest an-event-that-leaves-the-library-alone-is-left-alone
  (let [db {:plugins lib}
        effects {:db (assoc db :other 1)}]
    (is (identical? effects (homebrew-check/homebrew-shapes-checked db effects)))))

(deftest a-fix-is-used-and-stored
  (let [effects (homebrew-check/homebrew-shapes-checked {:plugins {}} {:db {:plugins lib}})
        cleaned (get-in effects [:db :plugins])]
    (is (= 25 (get-in cleaned ["P" ::e5/races :hill :speed])) "the app reads 25 at once")
    (is (= [lib] (fx-of effects ::homebrew-check/keep-pre-fix-copy)) "the library as it was is kept first")
    (is (= [[::e5/set-plugins cleaned]] (fx-of effects :dispatch)) "and the fix is stored")))

(deftest an-unreadable-entry-is-set-aside-as-written
  (let [bad (assoc race :abilities 42)
        written {"P" {::e5/races {:hill bad}}}
        effects (homebrew-check/homebrew-shapes-checked {:plugins {}} {:db {:plugins written}})
        broke (fx-of effects ::homebrew-check/report)]
    (is (= 1 (count broke)))
    (is (= {:content-type ::e5/races :key :hill :source "P" :original bad}
           (select-keys (first broke) [:content-type :key :source :original])))
    (is (not (contains? (get-in effects [:db :plugins "P" ::e5/races :hill]) :abilities))
        "the app reads past the field until the entry is set aside")))

(deftest a-changed-entry-is-converted
  (let [before {"P" {::e5/races {:hill (assoc race :speed 25)}} "Q" {}}
        after (assoc-in before ["P" ::e5/races :dale] {:name "Dale" :key :dale :option-pack "P"})]
    (is (= [#{["P" ::e5/races :dale]}]
           (fx-of (homebrew-check/homebrew-shapes-checked {:plugins before} {:db {:plugins after}})
                  ::homebrew-check/check-entries))
        "only the entry that changed")
    (is (empty? (fx-of (homebrew-check/homebrew-shapes-checked {} {:db {:plugins after}})
                       ::homebrew-check/check-entries))
        "not the whole library on load")))

(deftest shared-content-is-used-not-stored
  (let [effects (homebrew-check/homebrew-shapes-checked {} {:db {:shared-plugins lib}})]
    (is (= 25 (get-in effects [:db :shared-plugins "P" ::e5/races :hill :speed])))
    (is (empty? (:fx effects)) "never stored"))
  (testing "an entry that cannot be read is left out, and named"
    (let [bad {"P" {::e5/races {:hill (assoc race :abilities 42) :dale (assoc race :key :dale)}}}
          effects (homebrew-check/homebrew-shapes-checked {} {:db {:shared-plugins bad}})]
      (is (= #{:dale} (set (keys (get-in effects [:db :shared-plugins "P" ::e5/races])))))
      (is (= [:hill] (map :key (fx-of effects ::homebrew-check/report)))))))

;; an event that writes the library without the gate, as a bug or a script could
(rf/reg-event-db ::write-around-the-gate
  (fn [db [_ plugins]] (assoc db :plugins plugins))) ;; not app-db

(deftest a-write-that-goes-around-the-gate-is-still-checked
  (let [was @app-db]
    (try
      (reset! app-db {:plugins {}})
      (rf/dispatch-sync [::write-around-the-gate lib])
      (is (= 25 (get-in @app-db [:plugins "P" ::e5/races :hill :speed])))
      (finally
        (rf/purge-event-queue)
        (.removeItem js/localStorage "plugins:pre-fix")
        (.removeItem js/localStorage "plugins:pre-fix-at")
        (reset! app-db was)))))

(deftest a-set-aside-entry-keeps-what-was-written
  (let [bad (assoc race :abilities 42)
        {:keys [rejected]} (homebrew-check/set-aside-broken-entries
                            {"P" {::e5/races {:hill (dissoc bad :abilities)}}} {}
                            [{:content-type ::e5/races :key :hill :source "P" :original bad}])]
    (is (= bad (get-in rejected ["P" ::e5/races :hill])))))

;; ---------------------------------------------------------------------------
;; The read check: what a script or a bug writes to app-db outside any event
;; ---------------------------------------------------------------------------

(def ^:private read-by-handler (atom nil))
(rf/reg-event-db ::read-the-library (fn [db _] (reset! read-by-handler (:plugins db)) db))

(deftest a-library-written-outside-any-event-is-read-checked
  (let [was @app-db
        reports (atom [])
        bad (assoc race :key :bad :abilities 42)
        written {"P" {::e5/races {:hill race :bad bad}}}]
    (try
      (guard/call-with-reporter
       #(swap! reports conj %)
       (fn []
         (reset! app-db {:plugins written})
         (testing "a view reads the fix, and not the entry that cannot be read"
           (let [races (get-in @(rf/subscribe [::e5/plugins]) ["P" ::e5/races])]
             (is (= 25 (get-in races [:hill :speed])))
             (is (not (contains? races :bad)))))
         (testing "so does an event handler"
           (rf/dispatch-sync [::read-the-library])
           (is (= 25 (get-in @read-by-handler ["P" ::e5/races :hill :speed])))
           (is (not (contains? (get-in @read-by-handler ["P" ::e5/races]) :bad))))
         (testing "the entry is reported once, as written, however often it is read"
           @(rf/subscribe [::e5/plugins])
           (rf/dispatch-sync [::read-the-library])
           (is (= [[:bad bad]] (map (juxt :key :original) @reports))))))
      (finally
        (rf/purge-event-queue)
        (reset! app-db was)))))

(deftest a-checked-library-costs-one-comparison
  (let [lib' {"P" {::e5/races {:hill (assoc race :speed 25)}}}]
    (is (identical? lib' (homebrew-check/checked ::test lib')) "a sound library is the same object")
    (is (identical? lib' (homebrew-check/checked ::test lib')))))
