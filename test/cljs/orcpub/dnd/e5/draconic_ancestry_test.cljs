(ns orcpub.dnd.e5.draconic-ancestry-test
  "First end-to-end POOL/GRANT slice. Dragonborn's Draconic Ancestry choice now GRANTS from
   an open pool (built-in colours ++ homebrew ancestries an orcbrew pack adds). These tests
   are falsifiable on the two things that matter:
     1. built-in behaviour is unchanged (the 10 colours still appear),
     2. a homebrew ancestry inherits the FULL mechanics (resistance + breath weapon), not a
        text stub, and uses its own stored key.
   If the wiring regresses to the old fixed list, test 2 goes red (homebrew vanishes)."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.template :as t]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.races :as races5e]
            ;; Side effect: registers ::races5e/races and ::races5e/draconic-ancestry-pool
            [orcpub.dnd.e5.spell-subs]))

(defn reset-db! []
  (reset! app-db {})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

(defn ancestry-options
  "The options offered by dragonborn's Draconic Ancestry selection in the current db state."
  []
  (rf/clear-subscription-cache!)
  (let [races      @(rf/subscribe [::races5e/races])
        dragonborn (first (filter #(= :dragonborn (:key %)) races))
        selection  (first (filter #(= "Draconic Ancestry" (::t/name %))
                                  (:selections dragonborn)))]
    (::t/options selection)))

(def homebrew-pack
  {"Test Pack"
   {::e5/draconic-ancestries
    {:amethyst {:name "Amethyst"
                :key :amethyst
                :option-pack "Test Pack"
                :breath-weapon {:damage-type :force
                                :area-type :line
                                :line-width 5
                                :line-length 30
                                :save ::char5e/dex}}}}})

(deftest built-in-ancestries-unchanged
  (testing "with no plugins loaded, the 10 built-in colours are still the options"
    (reset! app-db {})
    (let [opts  (ancestry-options)
          names (set (map ::t/name opts))]
      (is (= 10 (count opts)))
      (is (contains? names "Red"))
      (is (contains? names "Silver")))))

(deftest homebrew-ancestry-appears-with-full-mechanics
  (testing "a homebrew ancestry joins dragonborn AND carries resistance + breath weapon"
    (reset! app-db {:plugins homebrew-pack})
    (let [opts     (ancestry-options)
          amethyst (first (filter #(= "Amethyst" (::t/name %)) opts))]
      (is (= 11 (count opts)) "homebrew ancestry joins the built-in 10")
      (is (some? amethyst) "homebrew ancestry is grantable under dragonborn")
      (is (= 2 (count (::t/modifiers amethyst)))
          "same mechanical heft as a built-in: damage resistance + breath-weapon modifier")
      (is (= :amethyst (::t/key amethyst))
          "uses its stored key (stable id), not a name-derived one (D10)"))))

(def gem-style-pack
  ;; A Fizban-style "gem" ancestry: resistance + breath PLUS extra mechanics declared as a
  ;; :props map (the same vocabulary homebrew races/feats use). Here, a flying speed.
  {"Gem Pack"
   {::e5/draconic-ancestries
    {:sapphire {:name "Sapphire"
                :key :sapphire
                :option-pack "Gem Pack"
                :breath-weapon {:damage-type :thunder
                                :area-type :line
                                :line-width 5
                                :line-length 30
                                :save ::char5e/dex}
                :props {:flying-speed-equals-walking-speed true}}}}})

(deftest richer-ancestry-carries-extra-mechanics-via-props
  (testing "an ancestry can grant more than resistance+breath — extra :props compile to modifiers"
    (reset! app-db {:plugins gem-style-pack})
    (let [opts     (ancestry-options)
          sapphire (first (filter #(= "Sapphire" (::t/name %)) opts))]
      (is (some? sapphire))
      (is (= 3 (count (::t/modifiers sapphire)))
          "resistance + breath weapon + the :props-declared flying-speed modifier"))))
