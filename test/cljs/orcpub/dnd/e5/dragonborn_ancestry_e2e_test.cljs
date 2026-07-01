(ns orcpub.dnd.e5.dragonborn-ancestry-e2e-test
  "Honest end-to-end test of the ONE cross-bucket grant in the codebase — and the
   one I (Claude) built, so it gets the LEAST benefit of the doubt. The existing
   draconic tests stop at option construction; NONE build a dragonborn character
   and check the breath weapon + resistance on the derived sheet. This does that:
   set :plugins with a homebrew ancestry, build a real dragonborn that chose it,
   and read the mechanics off the BUILT character (entity/build via the real
   ::char5e/template sub). Falsifiable: if the grant doesn't actually flow through
   to a character, these go red."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.character :as char5e]
            ;; side effects: register all the subs the full template depends on
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.spell-subs]
            [orcpub.dnd.e5.equipment-subs]
            [orcpub.dnd.e5.subs]))

(defn reset-db! [] (reset! app-db {}) (rf/clear-subscription-cache!))
(use-fixtures :each {:before reset-db!})

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
                                :save :orcpub.dnd.e5.character/dex}}}}})

(def dragonborn-entity
  {:orcpub.entity/options
   {:race {:orcpub.entity/key :dragonborn
           :orcpub.entity/options
           {:draconic-ancestry {:orcpub.entity/key :amethyst}}}
    :ability-scores
    {:orcpub.entity/key :standard-roll
     :orcpub.entity/value {:orcpub.dnd.e5.character/str 14
                           :orcpub.dnd.e5.character/dex 10
                           :orcpub.dnd.e5.character/con 14
                           :orcpub.dnd.e5.character/int 10
                           :orcpub.dnd.e5.character/wis 10
                           :orcpub.dnd.e5.character/cha 12}}}})

(defn build []
  (reset! app-db {:plugins homebrew-pack})
  (rf/clear-subscription-cache!)
  (let [template @(rf/subscribe [:orcpub.dnd.e5.character/template])]
    (entity/build dragonborn-entity template)))

(deftest homebrew-ancestry-mechanics-land-on-the-built-character
  (testing "a dragonborn who chose a HOMEBREW ancestry actually gets resistance + breath weapon on the derived sheet"
    (let [built (build)]
      (is (some? built) "build must not throw")
      ;; damage-resistances is a set of {:value … :qualifier …} maps (resistance-cfg), not bare kws
      (is (some #(= :force (:value %)) (char5e/damage-resistances built))
          "damage resistance to the homebrew breath type (:force) lands on the character")
      (is (= :force (:damage-type (char5e/get-prop built :draconic-ancestry-breath-weapon)))
          "the breath-weapon prop the race's attack reads is set from the homebrew ancestry")
      (let [breath (first (filter #(= "Breath Weapon" (:name %)) (char5e/attacks built)))]
        (is (some? breath)
            "the Breath Weapon attack appears on the built character")
        (is (= :force (:damage-type breath))
            "and it uses the homebrew ancestry's damage type")))))
