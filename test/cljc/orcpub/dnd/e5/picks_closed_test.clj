(ns orcpub.dnd.e5.picks-closed-test
  "`picks/disqualified`, `picks/overflow` and the planned hold on the real fighter, ranger and
   rogue templates."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.character :as char5e]
            [orcpub.dnd.e5.picks :as picks]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]))

(def ^:private language-map (common/map-by-key [{:name "Common" :key :common}]))

;; A background granting Stealth: a second, lasting source of a skill a class can also pick.
(def ^:private urchin
  {:name "Urchin" :key :urchin :option-pack "Test" :profs {:skill {:stealth true}}})

(def ^:private template
  (delay
   (t5e/template
    (t5e/template-selections
     nil nil nil weapons5e/weapons-map weapons5e/weapons
     sl5e/spell-lists spells5e/spell-map
     [urchin] []
     (mapv #(% sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map)
           [classes5e/fighter-option classes5e/ranger-option classes5e/rogue-option])
     []
     language-map))))

(defn- lvl
  "Level `n`; a hit-point pick from level 2 on (level 1's is offered only to a later class)."
  [n & [extra]]
  {::entity/key (keyword (str "level-" n))
   ::entity/options (merge (when (> n 1) {:hit-points {::entity/key :average ::entity/value 5}})
                           extra)})

(defn- class-entry
  "Class `cls` at level `n`; `at-3` is stored in level 3's options; `opts` on the class itself."
  [cls n & [at-3 opts]]
  {::entity/key cls
   ::entity/options (merge opts {:levels (vec (for [i (range 1 (inc n))]
                                                (lvl i (when (= i 3) at-3))))})})

(defn- character [classes & [extra]]
  (merge {::entity/options
          {:ability-scores {::entity/key :standard-roll
                            ::entity/value {::char5e/str 15 ::char5e/dex 15 ::char5e/con 14
                                            ::char5e/int 13 ::char5e/wis 13 ::char5e/cha 10}}
           :class classes}}
         extra))

(defn- closed [ch]
  (let [built (entity/build ch @template)]
    {:disqualified (set (picks/disqualified @template ch built))
     :overflow (set (picks/overflow @template ch built))}))

(defn- hunter [tactic] {:ranger-archetype {::entity/key :hunter
                                           ::entity/options {:defensive-tactics
                                                             {::entity/key tactic}}}})

(def ^:private steel-will
  [:class :ranger :levels :level-3 :ranger-archetype :hunter :defensive-tactics :steel-will])

(deftest a-subclass-level-pick-is-disqualified-below-its-level
  (is (= #{steel-will}
         (:disqualified (closed (character [(class-entry :ranger 6 (hunter :steel-will))]))))
      "Defensive Tactics is a level-7 feature")
  (is (= #{} (:disqualified (closed (character [(class-entry :ranger 7 (hunter :steel-will))])))))
  (testing "an option the template does not offer is never reported"
    (is (= #{}
           (:disqualified (closed (character [(class-entry :ranger 6 (hunter :tidebreaker))])))))))

(def ^:private champion {:martial-archetype {::entity/key :champion}})
(def ^:private two-styles {:fighting-style [{::entity/key :defense} {::entity/key :archery}]})

(deftest the-newest-pick-over-a-shared-slot-is-overflow
  (let [at-9 (character [(class-entry :fighter 9 champion two-styles)])]
    (is (= #{[:class :fighter :fighting-style :archery]} (:overflow (closed at-9)))
        "two styles, room for one below Champion 10: the newest goes")
    (is (= #{} (:disqualified (closed at-9)))
        "the shared slot is still open, so nothing in it is disqualified")
    (testing "a waived limit (the homebrew override) reports none"
      (is (= #{} (:overflow (closed (assoc at-9 ::entity/homebrew-paths
                                            {[:class :fighter :fighting-style] true})))))))
  (is (= #{} (:overflow (closed (character [(class-entry :fighter 10 champion two-styles)]))))))

(def ^:private rogue-first-class-skills
  {:skill-proficiency
   (mapv #(hash-map ::entity/key %) [:stealth :acrobatics :insight :perception])})

(deftest first-class-picks-of-a-second-class-are-disqualified
  (let [ch (character [(class-entry :fighter 1)
                       (class-entry :rogue 1 nil rogue-first-class-skills)])]
    (is (= (set (for [k [:stealth :acrobatics :insight :perception]]
                  [:class :rogue :skill-proficiency k]))
           (:disqualified (closed ch))))))

(deftest a-character-whose-picks-all-apply-reports-nothing
  (doseq [ch [(character [(class-entry :ranger 7 (hunter :steel-will))])
              (character [(class-entry :fighter 10 champion two-styles)])
              (character [(class-entry :rogue 1 nil rogue-first-class-skills)
                          (class-entry :fighter 1)])]]
    (is (= {:disqualified #{} :overflow #{}} (closed ch)))))

(deftest a-level-1-hit-point-roll-on-the-first-class-is-disqualified
  (let [rolled-1 (assoc-in (class-entry :rogue 1)
                           [::entity/options :levels 0 ::entity/options :hit-points]
                           {::entity/key :average ::entity/value 5})]
    (is (= #{[:class :rogue :levels :level-1 :hit-points :average]}
           (:disqualified (closed (character [rolled-1 (class-entry :fighter 1)]))))
        "the first class takes maximum hit points at level 1")
    (is (= #{} (:disqualified (closed (character [(class-entry :fighter 1) rolled-1]))))
        "a later class rolls its level 1")))

;; ---------------------------------------------------------------------------
;; The planned hold: set aside, put back, retired

(defn- levels-path [i] [::entity/options :class i ::entity/options :levels])
(defn- drop-level [ch i] (update-in ch (levels-path i) pop))
(defn- add-level [ch i n] (update-in ch (levels-path i) conj (lvl n)))
(defn- styles [ch] (mapv ::entity/key (get-in ch [::entity/options :class 0 ::entity/options
                                                   :fighting-style])))
(defn- update-planned [ch planned] (picks/update-planned @template ch planned))

(deftest a-level-drop-sets-a-pick-aside-and-raising-it-puts-it-back
  (let [ch7 (character [(class-entry :ranger 7 (hunter :steel-will))])
        r6 (update-planned (drop-level ch7 0) [])
        r7 (update-planned (add-level (:character r6) 0 7) (:planned r6))]
    (is (nil? (get-in (:character r6) [::entity/options :class 0 ::entity/options :levels 2
                                       ::entity/options :ranger-archetype ::entity/options
                                       :defensive-tactics]))
        "at 6 Steel Will is out of the character")
    (is (= [steel-will] (mapv :address (:planned r6))))
    (is (= [steel-will] (mapv :address (:set-aside r6))))
    (is (= ch7 (:character r7)) "back at 7 the character is exactly what it was")
    (is (= [] (:planned r7)))
    (is (= [steel-will] (mapv :address (:restored r7))))))

(deftest the-newest-style-waits-for-champion-10
  (let [ch10 (character [(class-entry :fighter 10 champion two-styles)])
        r9 (update-planned (drop-level ch10 0) [])
        r10 (update-planned (add-level (:character r9) 0 10) (:planned r9))]
    (is (= [:defense] (styles (:character r9))))
    (is (= [[:class :fighter :fighting-style :archery]] (mapv :address (:planned r9))))
    (is (= ch10 (:character r10)))
    (is (= [] (:planned r10)))))

(deftest a-pick-chosen-again-while-held-retires-the-held-copy
  (let [ch10 (character [(class-entry :fighter 10 champion two-styles)])
        r9 (update-planned (drop-level ch10 0) [])
        switched (assoc-in (:character r9) [::entity/options :class 0 ::entity/options
                                            :fighting-style]
                           [{::entity/key :archery}])
        r10 (update-planned (add-level switched 0 10) (:planned r9))]
    (is (= [:archery] (styles (:character r10))) "the player's choice stands; nothing is added")
    (is (= [] (:planned r10)))
    (is (= [] (:restored r10)))
    (is (= [[:class :fighter :fighting-style :archery]] (mapv :address (:retired r10))))))

(deftest swapping-the-class-order-holds-and-returns-first-class-picks
  (let [rogue (class-entry :rogue 1 nil rogue-first-class-skills)
        fighter (class-entry :fighter 1)
        as-second (update-planned (character [fighter rogue]) [])
        first-again (update-planned (assoc-in (:character as-second) [::entity/options :class]
                                              [rogue fighter])
                                    (:planned as-second))]
    (is (= 4 (count (:planned as-second))) "a second class holds its first-class skills")
    (is (= [] (:planned first-again)))
    (is (= (character [rogue fighter]) (:character first-again)))))

(deftest a-character-whose-picks-all-apply-is-left-as-it-is
  (let [ch (character [(class-entry :fighter 10 champion two-styles)])]
    (is (= {:character ch :planned [] :set-aside [] :restored [] :retired []}
           (update-planned ch [])))))


(deftest a-held-pick-whose-own-requirement-fails-waits
  (let [rogue (class-entry :rogue 1 nil rogue-first-class-skills)
        stealth [:class :rogue :skill-proficiency :stealth]
        record (:removed (picks/remove-at (character [rogue]) stealth))
        without (:character (picks/remove-at (character [rogue]) stealth))
        urchin-rogue (assoc-in without [::entity/options :background] {::entity/key :urchin})
        r (update-planned urchin-rogue [record])]
    (is (contains? (char5e/skill-proficiencies (entity/build urchin-rogue @template)) :stealth)
        "the background already gives Stealth")
    (is (= [stealth] (mapv :address (:planned r)))
        "so the rogue's held Stealth waits instead of coming back twice")
    (is (= urchin-rogue (:character r)))
    (testing "and comes back once the other source is gone"
      (let [r2 (update-planned (update (:character r) ::entity/options dissoc :background)
                               (:planned r))]
        (is (= [] (:planned r2)))
        (is (= #{:stealth :acrobatics :insight :perception}
               (set (map ::entity/key (get-in (:character r2) [::entity/options :class 0
                                                               ::entity/options
                                                               :skill-proficiency])))))))))

;; ---------------------------------------------------------------------------
;; Picks an edit removed with their entry: held, and back when the entry is

(defn- empty-levels [ch i from to]
  (update-in ch (levels-path i) into (for [n (range from (inc to))] {::entity/key (keyword (str "level-" n))})))

(def ^:private hunter-address [:class :ranger :levels :level-3 :ranger-archetype :hunter])

(deftest a-level-drop-to-below-the-subclass-holds-it-and-raising-returns-it
  (let [ch7 (character [(class-entry :ranger 7 (hunter :steel-will))])
        ch2 (update-in ch7 (levels-path 0) #(vec (take 2 %)))
        removed (picks/removed-with-container ch7 ch2)
        r2 (update-planned ch2 removed)
        r7 (update-planned (empty-levels (:character r2) 0 3 7) (:planned r2))]
    (is (= (set (cons hunter-address
                      (for [n (range 3 8)]
                        [:class :ranger :levels (keyword (str "level-" n)) :hit-points :average])))
           (set (map :address removed)))
        "Hunter (Steel Will inside it) and each lost level's hit points; not the levels themselves")
    (is (= ch2 (:character r2)) "at 2 they wait: their levels are not there")
    (is (= ch7 (:character r7)) "raised to 7, the character is what it was")
    (is (= [] (:planned r7)))))

(deftest switching-subclass-holds-the-old-subclass-picks
  (let [ch7 (character [(class-entry :ranger 7 (hunter :steel-will))])
        archetype (conj (levels-path 0) 2 ::entity/options :ranger-archetype)
        beast (assoc-in ch7 archetype {::entity/key :beast-master})
        removed (picks/removed-with-container ch7 beast)
        r-beast (update-planned beast removed)
        r-back (update-planned (assoc-in (:character r-beast) archetype {::entity/key :hunter})
                               (:planned r-beast))]
    (is (= [steel-will] (mapv :address removed)) "Hunter itself is the player's own change")
    (is (= beast (:character r-beast)))
    (is (= ch7 (:character r-back)) "back to Hunter, Steel Will returns")))

(deftest changing-the-class-holds-its-picks-until-it-returns
  (let [rogue (class-entry :rogue 1 nil rogue-first-class-skills)
        as-rogue (character [rogue])
        as-fighter (character [(class-entry :fighter 1)])
        removed (picks/removed-with-container as-rogue as-fighter)
        r (update-planned as-fighter removed)
        back (update-planned (character [(class-entry :rogue 1)]) (:planned r))]
    (is (= #{[:class :rogue :skill-proficiency :stealth] [:class :rogue :skill-proficiency :acrobatics]
             [:class :rogue :skill-proficiency :insight] [:class :rogue :skill-proficiency :perception]}
           (set (map :address removed))))
    (is (= 4 (count (:planned r))))
    (is (= as-rogue (:character back)) "rogue again, its skills return")))

(deftest a-pick-the-player-removes-is-not-held
  (let [rogue (class-entry :rogue 1 nil rogue-first-class-skills)
        ch (character [rogue])
        unticked (update-in ch [::entity/options :class 0 ::entity/options :skill-proficiency]
                            #(vec (rest %)))]
    (is (= [] (picks/removed-with-container ch unticked)))))
