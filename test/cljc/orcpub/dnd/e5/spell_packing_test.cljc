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
