(ns orcpub.dnd.e5.reference-web-test
  "Every link between homebrew items, probed two ways: that the link does something (the real
   consumer reads it), and that it survives a key rename -- the rename import conflict resolution
   and relocation both go through (`rename-key-in-plugin`).

   The first run asserted the correct outcome for every row; the eleven that failed are the ones
   marked :gap. The map these probe is agents/develop docs/kb/homebrew-reference-web.md. Rows marked :gap are
   links a rename is known to strand today; their assertion pins the stranding, so a fix that makes
   one follow turns it red on purpose -- flip :gap off in the same commit."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.races :as races5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.orcbrew-validation :as orcbrew-val]
            ;; Side effect: registers the subscriptions the link probes read.
            [orcpub.dnd.e5.spell-subs]))

(def ^:private P "Web Pak")

(defn- item [k & {:as more}]
  (merge {:key k :name (name k) :option-pack P} more))

;; ── Does a rename carry each link along? ────────────────────────────────────────────────────
;; Each row: a plugin holding the link, the rename of the thing linked TO, and what the holder
;; should read afterwards if the link followed.

(def ^:private rename-probes
  [{:link "subclass :class -> class"
    :plugin {::e5/classes    {:caster (item :caster)}
             ::e5/subclasses {:sub (item :sub :class :caster)}}
    :rename [::e5/classes :caster :caster-x "caster (X)"]
    :follows? #(= :caster-x (get-in % [::e5/subclasses :sub :class]))}

   {:link "subrace :race -> race"
    :plugin {::e5/races    {:folk (item :folk)}
             ::e5/subraces {:hill (item :hill :race :folk)}}
    :rename [::e5/races :folk :folk-x "folk (X)"]
    :follows? #(= :folk-x (get-in % [::e5/subraces :hill :race]))}

   {:link "spell :spell-lists -> class"
    :gap true
    :plugin {::e5/classes {:caster (item :caster)}
             ::e5/spells  {:bolt (item :bolt :level 1 :spell-lists {:caster true})}}
    :rename [::e5/classes :caster :caster-x "caster (X)"]
    :follows? #(true? (get-in % [::e5/spells :bolt :spell-lists :caster-x]))}

   {:link "class :spellcasting :spell-list-kw -> class (borrowed list)"
    :gap true
    :plugin {::e5/classes {:caster   (item :caster)
                           :borrower (item :borrower :spellcasting {:spell-list-kw :caster})}}
    :rename [::e5/classes :caster :caster-x "caster (X)"]
    :follows? #(= :caster-x (get-in % [::e5/classes :borrower :spellcasting :spell-list-kw]))}

   {:link "class :spellcasting :spell-list -> spell"
    :gap true
    :plugin {::e5/spells  {:bolt (item :bolt :level 1)}
             ::e5/classes {:caster (item :caster :spellcasting {:spell-list {1 #{:bolt}}})}}
    :rename [::e5/spells :bolt :bolt-x "bolt (X)"]
    :follows? #(contains? (get-in % [::e5/classes :caster :spellcasting :spell-list 1]) :bolt-x)}

   {:link "subclass :paladin-spells -> spell"
    :gap true
    :plugin {::e5/spells     {:bolt (item :bolt :level 1)}
             ::e5/subclasses {:oath (item :oath :class :paladin :paladin-spells {1 {0 :bolt}})}}
    :rename [::e5/spells :bolt :bolt-x "bolt (X)"]
    :follows? #(= :bolt-x (get-in % [::e5/subclasses :oath :paladin-spells 1 0]))}

   {:link "class :level-modifiers :spell -> spell"
    :gap true
    :plugin {::e5/spells  {:bolt (item :bolt :level 1)}
             ::e5/classes {:caster (item :caster :level-modifiers [{:type :spell :level 1
                                                                    :value {:key :bolt}}])}}
    :rename [::e5/spells :bolt :bolt-x "bolt (X)"]
    :follows? #(= :bolt-x (get-in % [::e5/classes :caster :level-modifiers 0 :value :key]))}

   {:link "race :spells -> spell"
    :gap true
    :plugin {::e5/spells {:bolt (item :bolt :level 1)}
             ::e5/races  {:folk (item :folk :spells [{:level 1 :value {:key :bolt}}])}}
    :rename [::e5/spells :bolt :bolt-x "bolt (X)"]
    :follows? #(= :bolt-x (get-in % [::e5/races :folk :spells 0 :value :key]))}

   {:link "class :level-selections -> custom selection"
    :gap true
    :plugin {::e5/selections {:tricks (item :tricks)}
             ::e5/classes    {:caster (item :caster :level-selections [{:type :tricks :level 1}])}}
    :rename [::e5/selections :tricks :tricks-x "tricks (X)"]
    :follows? #(= :tricks-x (get-in % [::e5/classes :caster :level-selections 0 :type]))}

   {:link "feat :path-prereqs :race -> race"
    :gap true
    :plugin {::e5/races {:folk (item :folk)}
             ::e5/feats {:knack (item :knack :path-prereqs {:race {:folk true}})}}
    :rename [::e5/races :folk :folk-x "folk (X)"]
    :follows? #(true? (get-in % [::e5/feats :knack :path-prereqs :race :folk-x]))}

   {:link "race :props :language -> language"
    :gap true
    :plugin {::e5/languages {:cant (item :cant)}
             ::e5/races     {:folk (item :folk :props {:language {:cant true}})}}
    :rename [::e5/languages :cant :cant-x "cant (X)"]
    :follows? #(true? (get-in % [::e5/races :folk :props :language :cant-x]))}

   {:link "race :languages (by name) -> language"
    :gap true
    :plugin {::e5/languages {:cant (item :cant :name "Cant")}
             ::e5/races     {:folk (item :folk :languages #{"Cant"})}}
    :rename [::e5/languages :cant :cant-x "Cant (X)"]
    :follows? #(contains? (get-in % [::e5/races :folk :languages]) "Cant (X)")}

   {:link "encounter :creatures :monster -> monster"
    :gap true
    :plugin {::e5/monsters   {:wolf (item :wolf)}
             ::e5/encounters {:ambush (item :ambush :creatures [{:type :monster
                                                                 :creature {:monster :wolf :num 2}}])}}
    :rename [::e5/monsters :wolf :wolf-x "wolf (X)"]
    :follows? #(= :wolf-x (get-in % [::e5/encounters :ambush :creatures 0 :creature :monster]))}])

(deftest each-link-under-a-rename
  (doseq [{:keys [link plugin rename follows? gap]} rename-probes]
    (let [[ct old to new-name] rename
          after (orcbrew-val/rename-key-in-plugin plugin ct old to new-name)]
      (testing link
        (is (some? (get-in after [ct to])) "the renamed item itself moved")
        (if gap
          (is (not (follows? after)) (str "GAP: a rename strands " link))
          (is (follows? after) (str "a rename carries " link)))))))

;; ── Does each link do anything? ─────────────────────────────────────────────────────────────
;; Through the consumer that reads it, so a row in the map that nothing reads fails here.

(defn- plugins! [plugin] (reset! app-db {:plugins {P plugin}}))

(defn- race-modifier-count [race]
  (plugins! {::e5/races {(:key race) race}})
  (count (:modifiers (first @(rf/subscribe [::races5e/plugin-races])))))

(deftest a-homebrew-spell-joins-the-class-list-it-names
  (plugins! {::e5/spells {:bolt (item :bolt :level 1 :spell-lists {:caster true})}})
  (is (some #{:bolt} (get-in @(rf/subscribe [::spells5e/plugin-spell-lists]) [:caster 1]))))

(deftest a-race-language-grant-by-key-is-read
  (is (< (race-modifier-count (item :folk))
         (race-modifier-count (item :folk :props {:language {:cant true}})))
      ":props :language adds a modifier"))

(deftest a-race-spell-grant-is-read
  (is (< (race-modifier-count (item :folk))
         (race-modifier-count (item :folk :spells [{:level 1 :value {:key :bolt :ability :int}}])))
      ":spells adds a modifier"))

(deftest a-feat-race-prerequisite-is-looked-up-by-key-and-matched-by-name
  (is (seq (opt5e/feat-prereqs nil {:race {:folk true}} {:folk {:name "Folk"}}))
      "the race key finds the race")
  ;; race-prereq matches (character/race c) against the looked-up names. A stale key looks up
  ;; nil, so the set is #{nil}: the feat is shown as " Only" and admits no character with a race.
  (is (= [" Only"] (map :orcpub.template/label
                        (opt5e/feat-prereqs nil {:race {:folk true}} {:folk-x {:name "Folk (X)"}})))
      "GAP: once the race is rekeyed, the feat locks for everyone under a blank label"))

;; ── What the player gets after a clash rename ───────────────────────────────────────────────
;; A rename only happens because another item already holds the key: an import conflict settled by
;; renaming the incoming item, or a move into a source that has one. So a stranded link does not
;; point at nothing -- it points at the OTHER item. These run the realistic case end to end through
;; the consumers the builder reads: the library already has the item, the import brings its own
;; and a dependent, and the import's copy is renamed.

(defn- after-import-rename! [plugins ct old to new-name]
  (reset! app-db {:plugins (orcbrew-val/rename-key-in-plugins plugins "Import" ct old to new-name)}))

(deftest a-stranded-spell-feeds-the-other-class-list
  (after-import-rename!
   {"Library" {::e5/classes {:caster (item :caster :name "Caster")}}
    "Import"  {::e5/classes {:caster (item :caster :name "Caster")}
               ::e5/spells  {:bolt (item :bolt :level 1 :spell-lists {:caster true})}}}
   ::e5/classes :caster :caster-imp "Caster (Imp)")
  (let [lists @(rf/subscribe [::spells5e/plugin-spell-lists])]
    (is (some #{:bolt} (get-in lists [:caster 1]))
        "GAP: the imported spell joins the LIBRARY's Caster list")
    (is (nil? (get-in lists [:caster-imp 1]))
        "GAP: and the imported class it was written for offers nothing")))

(deftest a-stranded-feat-prerequisite-requires-the-other-race
  (after-import-rename!
   {"Library" {::e5/races {:folk (item :folk :name "Folk")}}
    "Import"  {::e5/races {:folk (item :folk :name "Folk")}
               ::e5/feats {:knack (item :knack :path-prereqs {:race {:folk true}})}}}
   ::e5/races :folk :folk-imp "Folk (Imp)")
  (let [race-map (into {} (map (juxt :key identity)) @(rf/subscribe [::races5e/plugin-races]))
        feat     (get-in @app-db [:plugins "Import" ::e5/feats :knack])]
    (is (= ["Folk Only"] (map :orcpub.template/label
                              (opt5e/feat-prereqs nil (:path-prereqs feat) race-map)))
        "GAP: the imported feat now requires the LIBRARY's Folk, not the Folk (Imp) it was written for")))

(deftest a-stranded-level-selection-offers-the-other-list
  (after-import-rename!
   {"Library" {::e5/selections {:tricks (item :tricks :name "Library Tricks"
                                              :options [{:name "Juggle"}])}}
    "Import"  {::e5/selections {:tricks (item :tricks :name "Import Tricks"
                                              :options [{:name "Vanish"}])}
               ::e5/classes    {:mage (item :mage :name "Mage"
                                            :level-selections [{:type :tricks :level 1}])}}}
   ::e5/selections :tricks :tricks-imp "Import Tricks (Imp)")
  (let [mage (first (filter #(= :mage (:key %)) @(rf/subscribe [::classes5e/plugin-classes])))
        pick (first (get-in mage [:levels 1 :selections]))]
    (is (= "Library Tricks" (:orcpub.template/name pick))
        "GAP: the imported class's level-1 choice offers the LIBRARY's list")
    (is (= ["Juggle"] (map :orcpub.template/name (:orcpub.template/options pick)))
        "GAP: with the library's options, not the ones it was written with")))
