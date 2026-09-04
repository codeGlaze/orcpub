(ns orcpub.dnd.e5.spell-packing-test
  "Covers which spell level lands in which box."
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require #?(:clj [clojure.test :refer [deftest is testing]]
               :cljs [cljs.test :refer-macros [deftest is testing]])
            [orcpub.dnd.e5.spell-packing :as pk]))

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
  (testing "its cantrip box holds 7 rows, not 8"
    (is (= 7 (first (get pk/sheet-geometry 4))))
    (is (= 8 (first (get pk/sheet-geometry 1))))
    (is (= 99 (reduce + (get pk/sheet-geometry 4))))
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
