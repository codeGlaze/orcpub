(ns orcpub.dnd.e5.spell-packing-test
  "Covers which spell level lands in which box."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.spell-packing :as pk]
            [orcpub.pdf-spec :as spec]))

(def ^:private full
  "The eight-class fixture, every row of every level it touches filled. The worst
   case for rows rather than a typical character."
  [{:class "Bard 2" :levels {0 8 1 12}} {:class "Cleric 2" :levels {0 8 1 12}}
   {:class "Druid 2" :levels {0 8 1 12}} {:class "Paladin 4" :levels {1 12}}
   {:class "Ranger 4" :levels {1 12}} {:class "Sorcerer 2" :levels {0 8 1 12}}
   {:class "Warlock 2" :levels {0 8 1 12}} {:class "Wizard 2" :levels {0 8 1 12}}])

(def ^:private realistic
  [{:class "Bard 2" :levels {0 2 1 4}} {:class "Cleric 2" :levels {0 3 1 4}}
   {:class "Druid 2" :levels {0 2 1 4}} {:class "Paladin 4" :levels {1 3}}
   {:class "Ranger 4" :levels {1 3}} {:class "Sorcerer 2" :levels {0 4 1 3}}
   {:class "Warlock 2" :levels {0 2 1 2}} {:class "Wizard 2" :levels {0 3 1 4}}])

(defn- entries [pages] (for [page pages col page e (:placed col)] e))

(deftest no-level-is-put-in-a-box-too-small
  ;; The trap the plan warned about: column totals say a class fits while the
  ;; individual box it lands in does not. Boxes hold 8/12/13/13/13/9/9/9/7/7, so a
  ;; twelve-row list fits four of the ten.
  (doseq [style [1 2 3 4]
          fixture [full realistic]]
    (doseq [{:keys [rows capacity class level]} (entries (pk/pack style fixture))]
      (is (<= rows capacity)
          (str "style " style ": " class " level " level
               " needs " rows " rows in a box holding " capacity)))))

(deftest a-class-is-never-split-across-columns
  (doseq [style [1 4]]
    (doseq [page (pk/pack style realistic)]
      (let [by-class (group-by :class (for [col page e (:placed col)] e))]
        (doseq [[klass es] by-class]
          (let [cols (set (for [col page
                                :when (some #(= klass (:class %)) (:placed col))]
                            (:column col)))]
            (is (= 1 (count cols))
                (str klass " is spread over " (count cols) " columns"))))))))

(deftest every-spell-level-asked-for-is-placed
  (doseq [style [1 4] fixture [full realistic]]
    (is (= (reduce + (map (comp count :levels) fixture))
           (count (entries (pk/pack style fixture))))
        "a level that cannot be placed would vanish from the sheet silently")))

(deftest packing-saves-pages-without-costing-any
  (testing "several short lists share a sheet"
    (is (= 1 (count (pk/pack 1 [{:class "Cleric 5" :levels {0 4 1 5 2 4 3 3}}
                                {:class "Paladin 4" :levels {1 4}}])))
        "two classes that fit one sheet should not take two"))
  (testing "eight classes at realistic sizes"
    (is (= 2 (count (pk/pack 1 realistic))))
    (is (= 2 (count (pk/pack 4 realistic)))))
  (testing "a single class with more levels than a column holds keeps ONE page"
    ;; The regression this rule can cause: never-split would put a wizard's six
    ;; levels on two pages, where the sheet holds them on one today.
    (is (= 1 (count (pk/pack 1 [{:class "Wizard 9"
                                 :levels {0 5 1 6 2 5 3 4 4 3 5 2}}]))))))

(deftest style-4-has-its-own-arithmetic
  (testing "it splits its rows differently, not more meanly"
    ;; One fewer cantrip and one more 1st level. The totals matching is the point:
    ;; style 4 was recorded as holding 99 because its level 1 box was counted as
    ;; 12 when it has 13 fields, so the packer believed a row less than it had.
    (is (= 7 (first (get pk/sheet-geometry 4))))
    (is (= 8 (first (get pk/sheet-geometry 1))))
    (is (= 13 (second (get pk/sheet-geometry 4))))
    (is (= 12 (second (get pk/sheet-geometry 1))))
    (is (= 100 (reduce + (get pk/sheet-geometry 4))))
    (is (= 100 (reduce + (get pk/sheet-geometry 1)))))
  (testing "so a full 8-cantrip list cannot use box 0 there, and starts lower"
    (let [placed (entries (pk/pack 4 [{:class "Bard 2" :levels {0 8 1 12}}]))
          cantrips (first (filter #(zero? (:level %)) placed))]
      (is (pos? (:box cantrips)) "box 0 holds 7 and cannot take 8 rows"))))

(deftest relabel-instructions-cover-every-misleading-numeral
  (let [pages (pk/pack 1 realistic)
        instructions (pk/relabel-instructions pages)]
    (testing "a box holding a level other than its own is renumbered"
      (doseq [{:keys [box level] :as e} (entries pages) :when (not= box level)]
        (is (some #(and (= box (:box %)) (= (str level) (:label %))) instructions)
            (str "box " box " holds level " level " and would read as " box))))
    (testing "a box nothing uses is blanked rather than left reading as a level"
      (is (some #(nil? (:label %)) instructions)))))

(def ^:private realistic-with-names
  "The eight-class party, with names rather than counts, at the sizes the
   preview script uses."
  (mapv (fn [{:keys [class levels]}]
          {:class class
           :levels (into {} (map (fn [[lvl n]]
                                   [lvl (mapv #(str class " L" lvl " #" %) (range n))]))
                         levels)
           :slots (into {} (map (fn [[lvl _]] [lvl (inc lvl)])) levels)})
        realistic))

(deftest relabel-sections-count-from-one
  ;; Every field name carries a 1-based section suffix -- spells-3-1-1,
  ;; spell-slots-1-1 -- and these instructions name a section to relabel. They
  ;; came off map-indexed and so counted from zero, naming a section no template
  ;; has and silently relabelling nothing.
  (let [pages (pk/pack 1 realistic)
        sections (set (map :section (pk/relabel-instructions pages)))]
    (is (not (contains? sections 0)) "section 0 is not a thing")
    (is (every? #(<= 1 % (count pages)) sections)
        (str "sections " (sort sections) " against " (count pages) " page(s)"))))

(deftest sheet-geometry-is-the-only-copy-of-the-row-counts
  ;; pdf_spec carried its own level-max-spells -- style 1's numbers -- and split
  ;; a character's spells by them whatever style was being exported. A style 4
  ;; sheet was handed 8 cantrips for a box with 7 fields and lost one, and 12
  ;; first-level spells for a box that holds 13.
  (testing "pdf_spec asks the geometry, per style"
    (doseq [style [1 2 3 4]]
      (let [rows (spec/level-max-spells style)]
        (is (= (get pk/sheet-geometry style)
               (mapv #(get rows %) (range 10)))
            (str "style " style)))))
  (testing "style 4 differs, which is the whole reason this must not be hardcoded"
    (is (= 7 (get (spec/level-max-spells 4) 0)))
    (is (= 8 (get (spec/level-max-spells 1) 0)))
    (is (= 13 (get (spec/level-max-spells 4) 1)))
    (is (= 12 (get (spec/level-max-spells 1) 1))))
  (testing "an unknown style falls back to style 1, as the server does"
    (is (= (spec/level-max-spells 1) (spec/level-max-spells 99)))
    (is (= (spec/level-max-spells 1) (spec/level-max-spells nil)))))

(deftest classes-sharing-an-ability-share-a-section
  ;; Not an endorsement -- a record of what the export does today, because it is
  ;; the constraint packing has to satisfy before it can be turned on.
  ;;
  ;; make-page-map groups by :ability, so a Warlock and a Sorcerer both land in
  ;; the :cha section and print under ONE slot row, taken from the character-wide
  ;; spell-slots. A Warlock's Pact Magic is a separate pool at a separate level,
  ;; so those spells are already printed under the wrong slot count.
  ;;
  ;; Packing by CLASS is what fixes this, rather than what is blocked by it. Each
  ;; of the nine level boxes carries its own spell-slots-LEVEL-SUFFIX field, so a
  ;; class holding its own column of boxes carries its own slot counts in them.
  ;; The ability grouping is the thing to replace.
  (let [known {0 {:eldritch-blast {:key :eldritch-blast :ability :cha :class "Warlock"}
                  :fire-bolt      {:key :fire-bolt :ability :cha :class "Sorcerer"}
                  :mage-hand      {:key :mage-hand :ability :int :class "Wizard"}}}
        pages (spec/make-pages known false nil nil {} 1)
        cha (first (filter #(= :cha (:ability %)) pages))]
    (is (= 2 (count pages)) "one section per ability, not per class")
    (is (= #{"Sorcerer" "Warlock"} (:classes cha))
        "a pact caster shares a section with another class of the same ability")))

(defn- parse-int
  "Integer/parseInt is JVM-only, and this file runs in both."
  [x]
  #?(:clj (Integer/parseInt x) :cljs (js/parseInt x 10)))

(def ^:private warlock-and-sorcerer
  "The case the whole thing exists for: two short CHA lists that fit one page,
   and whose slot pools must not be averaged together."
  [{:class "Warlock" :levels {0 ["Eldritch Blast" "Mage Hand"] 1 ["Hex" "Bane"]}
    :slots {1 2}}
   {:class "Sorcerer" :levels {0 ["Fire Bolt"] 1 ["Shield" "Magic Missile"] 2 ["Mirror Image"]}
    :slots {1 4 2 3}}])

(deftest two-classes-share-a-page-in-their-own-columns
  (let [{:keys [fields pages relabels]} (pk/packed-fields 1 warlock-and-sorcerer)]
    (testing "both fit one page"
      (is (= 1 pages)))
    (testing "each class keeps its own run of boxes"
      (is (= "Eldritch Blast" (:spells-0-1-1 fields)) "Warlock cantrips in box 0")
      (is (= "Hex" (:spells-1-1-1 fields)) "Warlock level 1 in box 1")
      (is (= "Fire Bolt" (:spells-3-1-1 fields)) "Sorcerer starts a new column at box 3")
      (is (= "Shield" (:spells-4-1-1 fields)))
      (is (= "Mirror Image" (:spells-5-1-1 fields))))
    (testing "a box carrying another level is renumbered to what it holds"
      (is (some #(= {:section 1 :box 3 :label "0"} %) relabels))
      (is (some #(= {:section 1 :box 4 :label "1"} %) relabels))
      (is (some #(= {:section 1 :box 5 :label "2"} %) relabels)))
    (testing "a box nothing uses has its numeral blanked, not left reading as a level"
      (is (some #(= {:section 1 :box 9 :label nil} %) relabels)))))

(deftest a-pact-caster-keeps-its-own-slots
  ;; THE POINT. Grouping by ability merges a Warlock with a Sorcerer and writes
  ;; every box the character-wide slot total, so Pact Magic -- a separate pool at
  ;; a separate level -- is averaged away. Each level box has its own
  ;; spell-slots field, so a class holding its own column carries its own counts.
  (let [{:keys [fields]} (pk/packed-fields 1 warlock-and-sorcerer)]
    (testing "the Warlock's box carries the Warlock's slots"
      (is (= "2" (:spell-slots-1-1 fields))))
    (testing "the Sorcerer's boxes carry the Sorcerer's"
      (is (= "4" (:spell-slots-4-1 fields)))
      (is (= "3" (:spell-slots-5-1 fields))))
    (testing "and they are not the same number"
      (is (not= (:spell-slots-1-1 fields) (:spell-slots-4-1 fields))))))

(deftest the-cantrips-box-takes-no-slot-total
  ;; Box 0 has no slot inputs until reuse-cantrips-box! gives it some, so writing
  ;; one there would be a value with no field.
  (let [{:keys [fields]} (pk/packed-fields 1 warlock-and-sorcerer)]
    (is (nil? (:spell-slots-0-1 fields)))))

(deftest packed-fields-name-only-boxes-the-style-has
  (testing "every field names a box in 0..9 and a section the packing produced"
    (doseq [style [1 2 3 4]]
      (let [{:keys [fields pages]} (pk/packed-fields style realistic-with-names)]
        (doseq [k (keys fields)
                :let [m (re-matches #"spells-(\d+)-(\d+)-(\d+)" (name k))]
                :when m]
          (is (<= 0 (parse-int (nth m 1)) 9) (str k))
          (is (<= 1 (parse-int (nth m 3)) pages) (str k)))))))

(def ^:private pact-party
  "A Warlock beside two ordinary casters. The Warlock is listed LAST on purpose:
   its column is reserved, not first-come."
  [{:class "Sorcerer" :levels {0 ["Fire Bolt"] 1 ["Shield"] 2 ["Mirror Image"]}
    :slots {1 4 2 3}}
   {:class "Warlock" :pact? true
    :levels {0 ["Eldritch Blast" "Mage Hand"]
             5 (mapv #(str "Pact " %) (range 15))}
    :slots {5 2}}])

(deftest a-pact-caster-is-given-the-first-column
  ;; A 5e Warlock casts everything at its highest slot level, so it needs one
  ;; level box however high it climbs -- the numeral is relabelled as it levels.
  ;; Reserving the first column keeps its slot pool off the classes beside it.
  (let [{:keys [fields relabels]} (pk/packed-fields 1 pact-party)]
    (testing "cantrips in box 0 and the cast level in box 1, whatever the input order"
      (is (= "Eldritch Blast" (:spells-0-1-1 fields)))
      (is (= "Pact 0" (:spells-1-1-1 fields))))
    (testing "the box is renumbered to the level it casts at, not left reading 1"
      (is (some #(= {:section 1 :box 1 :label "5"} %) relabels)))
    (testing "and the other class is pushed out of that column"
      (is (= "Fire Bolt" (:spells-3-1-1 fields)))
      (is (= "Shield" (:spells-4-1-1 fields))))))

(deftest a-pact-list-too-long-for-one-box-spills-without-repeating
  ;; A level 20 Warlock knows 15 spells against box 1's 12 rows. Both boxes hold
  ;; the same level, so without an offset each printed the list from the top and
  ;; the second box was a duplicate of the first.
  (let [{:keys [fields relabels]} (pk/packed-fields 1 pact-party)]
    (testing "the first box takes what it holds"
      (is (= "Pact 0" (:spells-1-1-1 fields)))
      (is (= "Pact 11" (:spells-1-12-1 fields)) "twelve rows, ending at index 11")
      (is (nil? (:spells-1-13-1 fields)) "nothing past the box's capacity"))
    (testing "the spill continues where it stopped"
      (is (= "Pact 12" (:spells-2-1-1 fields)))
      (is (= "Pact 14" (:spells-2-3-1 fields)))
      (is (nil? (:spells-2-4-1 fields)) "and ends with the list"))
    (testing "the continuation is renumbered to the same level"
      (is (some #(= {:section 1 :box 2 :label "5"} %) relabels)))
    (testing "every spell appears exactly once"
      (let [printed (for [[k v] fields
                          :when (re-matches #"spells-[12]-\d+-1" (name k))]
                      v)]
        (is (= 15 (count printed)))
        (is (= 15 (count (distinct printed))))))))

(deftest only-the-box-a-level-starts-in-carries-its-slots
  ;; A continuation is the same pool. Printing the total twice reads as two sets
  ;; of slots for one level.
  (let [{:keys [fields]} (pk/packed-fields 1 pact-party)]
    (is (= "2" (:spell-slots-1-1 fields)) "the Warlock's own pact slots")
    (is (nil? (:spell-slots-2-1 fields)) "not repeated on the spill")
    (is (= "4" (:spell-slots-4-1 fields)) "and the Sorcerer's are its own")))

(deftest headings-name-the-class-holding-each-column
  ;; The bar of a cantrips box is the only place with room -- see the plan. A
  ;; class with no cantrips starts at a level box whose slot inputs the player
  ;; writes in, so it gets none and the section header names it.
  (let [{:keys [headings]} (pk/packed-fields 1 pact-party)]
    (is (= #{"Warlock" "Sorcerer"} (set (map :class headings))))
    (is (every? #(zero? (mod (:box %) 3)) headings)
        "a heading only ever sits on a box that starts a column"))
  (testing "a class without cantrips is headed too, on the level box it starts at"
    ;; It used to get none, which left its column unnamed on a packed page.
    (let [{:keys [headings]} (pk/packed-fields 1 [{:class "Warlock" :pact? true
                                                   :levels {0 ["a"] 1 ["b"]} :slots {1 2}}
                                                  {:class "Paladin" :levels {1 ["Bless"]}
                                                   :slots {1 4}}])
          paladin (first (filter #(= "Paladin" (:class %)) headings))]
      (is (some? paladin))
      (is (not (:cantrips? paladin))
          "its bar carries live slot inputs, so room has to be made")))
  (testing "a lone class without cantrips starts at box 1, not the cantrips box"
    ;; Box 0 takes cantrips only, so its heading is on a level box and has to
    ;; make room for that box's live slot inputs.
    (let [{:keys [headings]} (pk/packed-fields 1 [{:class "Paladin" :levels {1 ["Bless"]}
                                                   :slots {1 4}}])]
      (is (= 1 (:box (first headings))))
      (is (not (:cantrips? (first headings)))))))

(deftest every-class-gets-a-heading-including-those-without-cantrips
  ;; A heading sits on the FIRST box of a class's run. A class with cantrips
  ;; starts at a cantrips box, whose bar is free. A Paladin or Ranger has none and
  ;; starts at a level box carrying live slot inputs -- it was left with no name on
  ;; its column at all, which on a packed page is a column nobody can identify.
  (let [{:keys [headings]}
        (pk/packed-fields 1 [{:class "Warlock" :pact? true
                              :levels {0 ["a"] 5 ["c"]} :slots {5 2}}
                             {:class "Paladin" :levels {1 ["Bless"] 2 ["Aid"]}
                              :slots {1 4 2 3}}
                             {:class "Bard" :levels {0 ["x"] 1 ["y"]} :slots {1 4}}])
        by-class (into {} (map (juxt :class identity)) headings)]
    (testing "one per class, none missing"
      (is (= #{"Warlock" "Paladin" "Bard"} (set (map :class headings)))))
    (testing "flagged by whether its box holds cantrips"
      (is (:cantrips? (get by-class "Warlock")))
      (is (:cantrips? (get by-class "Bard")))
      (is (not (:cantrips? (get by-class "Paladin")))
          "a Paladin's first box is a level box, and its bar has live inputs"))
    (testing "each sits on the lowest box its class holds"
      (is (= 0 (:box (get by-class "Warlock"))))
      (is (= 3 (:box (get by-class "Paladin")))))))

;; ─── The builder's side: turning spells-known into per-class lists ───────────

(def ^:private cha :orcpub.dnd.e5.character/cha)

(def ^:private warlock-sorcerer-known
  "spells-known as the builder holds it: keyed by LEVEL, each value spell-key ->
   config carrying the class that granted it."
  {0 {:eldritch-blast {:key :eldritch-blast :ability cha :class "Warlock"}
      :fire-bolt      {:key :fire-bolt :ability cha :class "Sorcerer"}}
   1 {:hex    {:key :hex :ability cha :class "Warlock"}
      :shield {:key :shield :ability cha :class "Sorcerer"}}
   2 {:invisibility {:key :invisibility :ability cha :class "Warlock"}
      :blur         {:key :blur :ability cha :class "Sorcerer"}}})

(deftest spells-are-regrouped-by-class-not-by-ability
  ;; The shipped layout groups by :ability, which is why a Warlock and a Sorcerer
  ;; share one CHA section under one slot row.
  (let [classes (spec/packing-classes warlock-sorcerer-known {} {1 4 2 3} {3 2}
                                      (constantly 15) (constantly 7))
        by-class (into {} (map (juxt :class identity)) classes)]
    (is (= #{"Warlock" "Sorcerer"} (set (keys by-class))))
    (testing "a pact caster is flagged and carries the PACT slots"
      (is (:pact? (get by-class "Warlock")))
      (is (= {3 2} (:slots (get by-class "Warlock")))))
    (testing "everyone else draws on the shared table"
      (is (not (:pact? (get by-class "Sorcerer"))))
      (is (= {1 4 2 3} (:slots (get by-class "Sorcerer")))))
    (testing "the ability is the abbreviation, since it heads a column"
      (is (= "CHA" (:ability (get by-class "Warlock")))))))

(deftest a-pact-caster-reports-one-level-because-that-is-how-it-casts
  ;; A Warlock casts every spell at its highest pact slot, so its list is reported
  ;; at that one level rather than spread across the levels the spells were
  ;; learned at -- which is what lets it hold a single box however high it climbs.
  (let [classes (spec/packing-classes warlock-sorcerer-known {} {1 4 2 3} {3 2}
                                      (constantly 15) (constantly 7))
        warlock (first (filter :pact? classes))
        sorcerer (first (remove :pact? classes))]
    (is (= #{0 3} (set (keys (:levels warlock))))
        "cantrips, and everything else at the pact level")
    (is (= 2 (count (get-in warlock [:levels 3])))
        "the 1st and 2nd level spells both sit there")
    (is (= #{0 1 2} (set (keys (:levels sorcerer))))
        "an ordinary caster keeps the levels it learned at")))

(deftest the-default-layout-follows-the-build
  (let [classes (spec/packing-classes warlock-sorcerer-known {} {1 4} {3 2}
                                      (constantly 15) (constantly 7))]
    (testing "more than one casting class, on a style that can be relabelled"
      (is (= :packed (spec/default-spell-layout classes 1))))
    (testing "a single caster already reads down its own page"
      (is (= :per-class (spec/default-spell-layout [(first classes)] 1))))
    (testing "on every style whose numerals are measured"
      (doseq [style [1 2 3 4]]
        (is (= :packed (spec/default-spell-layout classes style))
            (str "style " style))))
    (testing "and never on a style that cannot be relabelled"
      (is (= :per-class (spec/default-spell-layout classes 5))))))

;; ─── Nothing may be lost without saying so ───────────────────────────────────

(def ^:private wizard-cleric-druid
  "A Wizard 20 beside two other full casters: 151 spells, which two pages hold by
   raw capacity but no single column can seat the Cleric in. Packing used to drop
   the Cleric outright -- 33 spells, with nothing reported."
  [{:class "Wizard"
    :levels {0 6 1 12 2 13 3 13 4 13 5 9 6 9 7 9 8 7 9 7}}
   {:class "Cleric" :levels {0 5 1 10 2 10 3 8}}
   {:class "Druid" :levels {0 4 1 8 2 8}}])

(deftest a-packing-that-cannot-hold-a-class-says-so
  (doseq [style [1 2 3 4]]
    (let [pages (pk/pack style wizard-cleric-druid)
          missed (pk/unplaced wizard-cleric-druid pages)]
      (testing (str "style " style)
        (is (seq missed) "the Cleric does not fit and is reported")
        (is (= #{"Cleric"} (set (keys missed)))
            "the Wizard and the Druid are placed in full")
        (is (= 33 (reduce + (vals (get missed "Cleric"))))
            "every one of its spells is accounted for")
        (is (not (pk/fits? style wizard-cleric-druid)))))))

(deftest a-packing-that-holds-everything-reports-nothing
  (doseq [style [1 2 3 4]]
    (let [classes [{:class "Warlock" :pact? true :levels {0 3 5 10}}
                   {:class "Sorcerer" :levels {0 5 1 6 2 5 3 4}}]
          pages (pk/pack style classes)]
      (testing (str "style " style)
        (is (empty? (pk/unplaced classes pages)))
        (is (pk/fits? style classes))))))

(deftest packing-shape-counts-either-names-or-counts
  (is (= [{:class "Bard" :pact? nil :levels {0 2 1 3}}]
         (pk/packing-shape [{:class "Bard" :levels {0 ["Light" "Mending"]
                                                         1 ["Bane" "Bless" "Heroism"]}}])))
  (is (= [{:class "Bard" :pact? nil :levels {0 2 1 3}}]
         (pk/packing-shape [{:class "Bard" :levels {0 2 1 3}}]))
      "idempotent, so a caller may pass either"))

(deftest a-layout-that-would-lose-spells-is-neither-defaulted-nor-honoured
  (let [named (mapv (fn [{:keys [class levels]}]
                      {:class class
                       :levels (into {} (map (fn [[lvl n]]
                                               [lvl (mapv #(str class " " lvl "-" %)
                                                          (range n))]))
                                     levels)})
                    wizard-cleric-druid)]
    (doseq [style [1 2 3 4]]
      (testing (str "style " style)
        (is (= :per-class (spec/default-spell-layout named style))
            "not offered as the default")
        (is (seq (:unplaced (pk/packed-fields style named)))
            "and packed-fields carries the report for the caller that asks anyway")))))

(deftest box-0-holds-cantrips-only
  ;; A no-cantrips class leading a free column used to start at box 0, which then
  ;; had to be redrawn as a level box from style 1's measurements: on style 3 the
  ;; numeral missed the ring and the printed 0 stayed, and on every style the
  ;; class name was clipped by the input drawn over it.
  (let [paladin-ranger [{:class "Paladin" :levels {1 5 2 3}}
                        {:class "Ranger" :levels {1 4 2 2}}]]
    (doseq [style [1 2 3 4]]
      (let [pages (pk/pack style paladin-ranger)
            placed (for [page pages col page e (:placed col)] e)]
        (testing (str "style " style)
          (is (every? (fn [{:keys [box level]}] (or (not= 0 box) (= 0 level))) placed)
              "no spell level lands in box 0")
          (is (= {"Paladin" [1 2] "Ranger" [3 4]}
                 (into {} (map (fn [[c es]] [c (mapv :box (sort-by :level es))]))
                       (group-by :class placed)))
              "the Paladin takes boxes 1 and 2, leaving box 0 to its printed 0")
          (is (not-any? (fn [{:keys [box label]}] (and (= 0 box) (some? label)))
                        (pk/relabel-instructions pages))
              "so no instruction ever asks for a label on box 0")
          (is (pk/fits? style paladin-ranger)))))
    (testing "a spread class without cantrips starts at box 1 too"
      (let [wide [{:class "Homebrew" :levels {1 5 2 5 3 5 4 5 5 5 6 5}}]
            placed (for [page (pk/pack 1 wide) col page e (:placed col)] e)]
        (is (= [1 2 3 4 5 6] (mapv :box (sort-by :level placed))))))))
