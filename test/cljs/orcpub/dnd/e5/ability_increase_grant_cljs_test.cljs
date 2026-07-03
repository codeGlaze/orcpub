(ns orcpub.dnd.e5.ability-increase-grant-cljs-test
  "Layer 2 (cljs) of the floating-ASI vertical: the RACE/SUBRACE assembly wires a homebrew entry's
   :ability-increases SPREAD (terse [amount pool] pairs) through opt5e/compile-ability-increases.
   Proven through the REAL ::races5e/races sub: a homebrew race with a fixed + floating increment
   yields a race option carrying the fixed modifiers AND a floating ASI selection (keyed :asi,
   carrying ::t/spread) restricted to the named pool. (That the compiled output lands on a built
   character is proven under JVM in ability-increase-grant-test — layer 1.)
   See docs/kb/ability-increase-spreads.md."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.backgrounds :as bg5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.import-validation :as import-val]
            ;; side effects: register the races/backgrounds subs + the builder events
            [orcpub.dnd.e5.spell-subs]
            [orcpub.dnd.e5.events]))

(defn reset-db! [] (reset! app-db {}) (rf/clear-subscription-cache!))
(use-fixtures :each {:before reset-db!})

(def S ::char5e/str)
(def D ::char5e/dex)
(def C ::char5e/con)
;; the floating "+1 martial" increment is at spread index 1 (CHA is index 0), so its slot options
;; are keyed asi-1-<ability>
(def martial-slot-1 #{:asi-1-str :asi-1-dex :asi-1-con})

;; "+2 CHA (fixed), +1 to any martial stat (floating)" as terse spread data
(def asi-race-pack
  {"ASI Pack"
   {::e5/races
    {:tide-touched
     {:name "Tide-Touched" :key :tide-touched :option-pack "ASI Pack"
      :ability-increases [[2 :cha] [1 :martial]]}}}})

(defn- floating-sel [race]
  (first (filter #(= :asi (::t/key %)) (:selections race))))

(deftest race-ability-increases-data-wires-fixed-and-floating
  (reset! app-db {:plugins asi-race-pack})
  (let [race (first (filter #(= :tide-touched (:key %)) @(rf/subscribe [::races5e/races])))]
    (is (some? race) "the homebrew race appears in the races sub")
    (testing "FLOATING — a user-choice ASI slot, restricted to the martial pool"
      (let [sel (floating-sel race)]
        (is (some? sel) "the floating ASI selection was compiled and merged onto the race")
        (is (= 2 (count (::t/spread sel))) "the full spread (fixed + floating) rides on ::t/spread")
        (is (= martial-slot-1 (set (map ::t/key (::t/options sel))))
            "the floating slot offers only the martial stats — not all six")))
    (testing "FIXED — the +2 CHA contributes its modifiers to the race"
      (is (= 2 (count (:modifiers race)))
          "compile-ability-increases' fixed CHA modifiers were merged into the race's :modifiers"))))

(deftest authoring-via-builder-events-produces-a-working-race
  (testing "driving the REAL race-builder events (what the form dispatches) authors a working spread"
    (reset! app-db {})
    (rf/dispatch-sync [::races5e/set-race-prop :name "Tide-Touched"])
    (rf/dispatch-sync [::races5e/set-race-prop :key :tide-touched])
    (rf/dispatch-sync [::races5e/set-race-prop :option-pack "P"])
    (rf/dispatch-sync [::races5e/set-race-path-prop [:ability-increases] [[2 :cha] [1 :martial]]])
    (let [item (::races5e/builder-item @app-db)]
      (is (= 2 (count (:ability-increases item))) "the builder events built the spread")
      (reset! app-db {:plugins {"P" {::e5/races {:tide-touched item}}}})
      (let [sel (floating-sel (first (filter #(= :tide-touched (:key %)) @(rf/subscribe [::races5e/races]))))]
        (is (= martial-slot-1 (set (map ::t/key (::t/options sel))))
            "restricted to the martial pool, end to end from the builder events")))))

(deftest background-ability-increases-wires-through-the-sub
  (testing "a homebrew BACKGROUND's spread flows through ::bg5e/backgrounds (2024 ASI-via-origin)"
    (reset! app-db {:plugins {"BG Pack" {::e5/backgrounds
                                         {:tide-born {:name "Tide-Born" :key :tide-born :option-pack "BG Pack"
                                                      :ability-increases [[2 :cha] [1 :martial]]}}}}})
    (rf/clear-subscription-cache!)
    (let [bg  (first (filter #(= :tide-born (:key %)) @(rf/subscribe [::bg5e/backgrounds])))
          sel (floating-sel bg)]
      (is (some? bg) "the homebrew background appears in the backgrounds sub")
      (is (= 1 (count (:modifiers bg)))
          "fixed +2 CHA merged as ONE neutral ability modifier (:general attribution — NOT racial; a
           race would emit 2, the extra being the race-column write)")
      (is (= martial-slot-1 (set (map ::t/key (::t/options sel))))
          "the floating slot is wired onto the background, restricted to martial"))))

(deftest subclass-ability-increases-wires-through-the-sub
  (testing "an OPT-IN subclass spread flows through ::classes5e/plugin-subclasses (non-standard 5e)"
    (reset! app-db {:plugins {"SC Pack" {::e5/subclasses
                                         {:tide-knight {:name "Tide Knight" :key :tide-knight :class :fighter
                                                        :option-pack "SC Pack"
                                                        :ability-increases [[2 :cha] [1 :martial]]}}}}})
    (rf/clear-subscription-cache!)
    (let [sc  (first (filter #(= :tide-knight (:key %)) @(rf/subscribe [::classes5e/plugin-subclasses])))
          sel (floating-sel sc)]
      (is (some? sc) "the homebrew subclass appears in the sub")
      (is (= 1 (count (:modifiers sc)))
          "fixed +2 CHA merged as ONE neutral ability modifier (:general attribution — subclass ASI is
           not racial)")
      (is (= martial-slot-1 (set (map ::t/key (::t/options sel))))
          "the floating slot is wired onto the subclass, restricted to martial"))))

(deftest race-without-ability-increases-is-unaffected
  (testing "additive: a homebrew race with no :ability-increases gets no ASI selection"
    (reset! app-db {:plugins {"P" {::e5/races {:plain {:name "Plain" :key :plain :option-pack "P"}}}}})
    (let [race (first (filter #(= :plain (:key %)) @(rf/subscribe [::races5e/races])))]
      (is (some? race))
      (is (nil? (floating-sel race)) "no :ability-increases -> no floating selection (opt-in)"))))

;; Save proficiencies wire through the SAME merged hook (compile-ability-grants) as the ASI spread.
(defn- save-prof-sel [content]
  (first (filter #(= :save-prof-0 (::t/key %)) (:selections content))))

(deftest standalone-save-proficiencies-wire-through-the-race-sub
  (testing "a homebrew race's :save-proficiencies [[1 :mental] [1 :con]] -> a 'choose 1 mental save'
            selection (idx 0) AND a fixed CON save modifier (idx 1), merged by compile-ability-grants"
    (reset! app-db {:plugins {"P" {::e5/races
                                   {:warded {:name "Warded" :key :warded :option-pack "P"
                                             :size :medium :speed 30 :languages #{} :traits []
                                             :save-proficiencies [[1 :mental] [1 :con]]}}}}})
    (rf/clear-subscription-cache!)
    (let [race (first (filter #(= :warded (:key %)) @(rf/subscribe [::races5e/races])))
          sel  (save-prof-sel race)]
      (is (some? race) "the homebrew race appears in the races sub")
      (is (pos? (count (:modifiers race))) "the fixed CON save contributed a modifier")
      (is (some? sel) "the floating 'choose a mental save' selection was wired onto the race")
      (is (= #{:save-0-int :save-0-wis :save-0-cha} (set (map ::t/key (::t/options sel))))
          "the save choice offers only the mental pool, keyed save-<idx>-<ability>"))))

(deftest save-rider-rides-the-spread-through-the-sub
  (testing "a race with :ability-increases [[1 :martial :save]] keeps ONE :asi selection whose options
            each carry a save (the rider compiles inside the spread, not as a separate selection)"
    (reset! app-db {:plugins {"P" {::e5/races
                                   {:bulwark {:name "Bulwark" :key :bulwark :option-pack "P"
                                              :size :medium :speed 30 :languages #{} :traits []
                                              :ability-increases [[1 :martial :save]]}}}}})
    (rf/clear-subscription-cache!)
    (let [race (first (filter #(= :bulwark (:key %)) @(rf/subscribe [::races5e/races])))
          sel  (floating-sel race)]
      (is (some? sel) "the rider still produces the single :asi selection")
      (is (nil? (save-prof-sel race)) "the rider does NOT add a separate save-prof selection")
      (is (= #{:asi-0-str :asi-0-dex :asi-0-con} (set (map ::t/key (::t/options sel))))
          "martial pool, keyed asi-0-<ability> (the save rides each option's modifiers)"))))

;; Layer 5 (homebrew half): :ability-increases survives a real orcbrew export -> import.
;; Export is `(str plugin)`; import is validate-import (parse/normalize/clean/fill/dedup/validate).
;; Round-trip the SINGLE-plugin shape (the strict path that runs remove-invalid-items) and assert the
;; spread is preserved verbatim AND still drives the live sub.
(def asi-race
  {:name "Tide-Touched" :key :tide-touched :option-pack "ASI Pack"
   :ability-increases [[2 :cha] [1 :martial]]})

(deftest ability-increases-survives-orcbrew-export-import
  (let [plugin   {::e5/races {:tide-touched asi-race}}
        result   (import-val/validate-import (str plugin) {})
        imported (get-in (:data result) [::e5/races :tide-touched])]
    (testing "the import succeeds and keeps the (valid) race"
      (is (:success result))
      (is (zero? (:skipped-count result)) "a valid race is not dropped by progressive import"))
    (testing "the spread survives the export->import pipeline verbatim"
      (is (= [[2 :cha] [1 :martial]] (:ability-increases imported))
          "the terse [amount pool] pairs round-trip through (str)->validate-import"))
    (testing "the imported data still drives the live races sub (floating slot restricted to martial)"
      (reset! app-db {:plugins {"ASI Pack" (:data result)}})
      (rf/clear-subscription-cache!)
      (let [sel (floating-sel (first (filter #(= :tide-touched (:key %)) @(rf/subscribe [::races5e/races]))))]
        (is (= martial-slot-1 (set (map ::t/key (::t/options sel))))
            "still restricted to the martial pool after export/import")))))
