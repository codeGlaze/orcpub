(ns orcpub.dnd.e5.ability-increase-grant-cljs-test
  "Layer 2 (cljs) of the floating-ASI vertical: the RACE/SUBRACE assembly wires a homebrew
   entry's :ability-increases data through opt5e/compile-ability-increases. Proven through the
   REAL ::races5e/races sub (the same harness pattern as draconic-ancestry-test): a homebrew race
   with a fixed + floating allotment yields a race option carrying the fixed modifiers AND a
   floating ASI selection restricted to the named subset. (That the compiled output lands on a
   built character is proven under JVM in ability-increase-grant-test — layer 1.)"
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.races :as races5e]
            ;; side effects: register the races sub + the builder events
            [orcpub.dnd.e5.spell-subs]
            [orcpub.dnd.e5.events]))

(defn reset-db! [] (reset! app-db {}) (rf/clear-subscription-cache!))
(use-fixtures :each {:before reset-db!})

(def S ::char5e/str)
(def D ::char5e/dex)
(def C ::char5e/con)
(def Ch ::char5e/cha)

;; a homebrew race that grants "+2 CHA (fixed) and +1 to any martial stat (floating)" as DATA
(def asi-race-pack
  {"ASI Pack"
   {::e5/races
    {:tide-touched
     {:name "Tide-Touched" :key :tide-touched :option-pack "ASI Pack"
      :ability-increases [{:ability Ch :amount 2}
                          {:select {:from :martial :num 1 :amount 1 :different? true}}]}}}})

(defn- homebrew-race []
  (rf/clear-subscription-cache!)
  (first (filter #(= :tide-touched (:key %)) @(rf/subscribe [::races5e/races]))))

(deftest race-ability-increases-data-wires-fixed-and-floating
  (reset! app-db {:plugins asi-race-pack})
  (let [race (homebrew-race)]
    (is (some? race) "the homebrew race appears in the races sub")
    (testing "FLOATING — a user-choice ASI selection, restricted to the martial subset"
      (let [sel (first (filter #(= :floating-asi-0 (::t/key %)) (:selections race)))]
        (is (some? sel) "the floating ASI selection was compiled and merged onto the race")
        (is (= #{S D C} (set (map ::t/key (::t/options sel))))
            "the choice offers only the martial stats — not all six")))
    (testing "FIXED — the +2 CHA contributes its modifiers to the race"
      ;; minimal race (no :props/spells), so the only modifiers are the fixed race-ability's two
      (is (= 2 (count (:modifiers race)))
          "compile-ability-increases' fixed CHA modifiers were merged into the race's :modifiers"))))

(deftest authoring-via-builder-events-produces-a-working-race
  (testing "driving the REAL race-builder events (what the form dispatches) authors a working floating-ASI race"
    (reset! app-db {})
    (rf/dispatch-sync [::races5e/set-race-prop :name "Tide-Touched"])
    (rf/dispatch-sync [::races5e/set-race-prop :key :tide-touched])
    (rf/dispatch-sync [::races5e/set-race-prop :option-pack "P"])
    (rf/dispatch-sync [::races5e/set-race-path-prop [:ability-increases]
                       [{:ability Ch :amount 2}
                        {:select {:from :martial :num 1 :amount 1 :different? true}}]])
    (let [item (::races5e/builder-item @app-db)]
      (is (= 2 (count (:ability-increases item))) "the builder events built the allotment list")
      (is (= :tide-touched (:key item)))
      ;; feed the authored race through the REAL assembly (as a loaded plugin) — end to end
      (reset! app-db {:plugins {"P" {::e5/races {:tide-touched item}}}})
      (let [race (first (filter #(= :tide-touched (:key %)) @(rf/subscribe [::races5e/races])))
            sel  (first (filter #(= :floating-asi-0 (::t/key %)) (:selections race)))]
        (is (some? sel) "the authored race carries the floating ASI selection")
        (is (= #{S D C} (set (map ::t/key (::t/options sel))))
            "restricted to the martial set, end to end from the builder events")))))

(deftest race-without-ability-increases-is-unaffected
  (testing "additive: a homebrew race with no :ability-increases gets no ASI selection"
    (reset! app-db {:plugins {"P" {::e5/races {:plain {:name "Plain" :key :plain :option-pack "P"}}}}})
    (let [race (first (filter #(= :plain (:key %)) @(rf/subscribe [::races5e/races])))]
      (is (some? race))
      (is (not-any? #(= :floating-asi-0 (::t/key %)) (:selections race))
          "no :ability-increases -> no floating selection (the hook is opt-in)"))))
