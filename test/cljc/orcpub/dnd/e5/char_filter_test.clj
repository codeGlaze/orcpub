(ns orcpub.dnd.e5.char-filter-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.char-filter :as char-filter]
            [orcpub.dnd.e5.character :as char5e]))

;; ---------------------------------------------------------------------------
;; Test data

(def aragorn
  {::char5e/character-name    "Aragorn"
   ::char5e/image-url         "http://example.com/aragorn.png"
   ::char5e/faction-image-url nil
   ::char5e/classes           [{::char5e/class-name "Fighter" ::char5e/level 5}
                               {::char5e/class-name "Ranger"  ::char5e/level 3}]})

(def legolas
  {::char5e/character-name    "Legolas"
   ::char5e/image-url         nil
   ::char5e/faction-image-url "http://example.com/mirkwood.png"
   ::char5e/classes           [{::char5e/class-name "Ranger" ::char5e/level 8}]})

(def gimli
  {::char5e/character-name    "Gimli"
   ::char5e/image-url         nil
   ::char5e/faction-image-url nil
   ::char5e/classes           [{::char5e/class-name "Fighter" ::char5e/level 10}]})

(def all-chars [aragorn legolas gimli])

;; Shorthand: call filter-characters with all filters defaulted to "no filter"
(defn filter-by [& {:keys [name levels classes portrait faction]
                    :or   {name "" levels #{} classes #{} portrait nil faction nil}}]
  (vec (char-filter/filter-characters all-chars name levels classes portrait faction)))

;; ---------------------------------------------------------------------------
;; Name filter

(deftest name-filter-blank-returns-all
  (is (= all-chars (filter-by :name ""))))

(deftest name-filter-case-insensitive-substring
  (testing "lowercase query matches mixed-case name"
    (is (= [aragorn] (filter-by :name "ara"))))
  (testing "uppercase query matches"
    (is (= [gimli] (filter-by :name "GIM"))))
  (testing "no match returns empty"
    (is (= [] (filter-by :name "xyz"))))
  (testing "partial match across multiple chars"
    (is (= [legolas gimli] (filter-by :name "l")))))

(deftest name-filter-nil-treated-as-blank
  ;; character-name may be nil for a newly-created character with no name set
  (let [unnamed {::char5e/character-name    nil
                 ::char5e/image-url         nil
                 ::char5e/faction-image-url nil
                 ::char5e/classes           []}
        result (char-filter/filter-characters [unnamed] "" #{} #{} nil nil)]
    (is (= [unnamed] (vec result)))))

;; ---------------------------------------------------------------------------
;; Level filter

(deftest level-filter-empty-returns-all
  (is (= all-chars (filter-by :levels #{}))))

(deftest level-filter-matches-any-class-level
  (testing "level 3 matches Aragorn (Ranger 3)"
    (is (= [aragorn] (filter-by :levels #{3}))))
  (testing "level 5 matches Aragorn (Fighter 5)"
    (is (= [aragorn] (filter-by :levels #{5}))))
  (testing "level 8 matches Legolas"
    (is (= [legolas] (filter-by :levels #{8}))))
  (testing "level in multiple chars"
    (is (= (set [aragorn gimli]) (set (filter-by :levels #{5 10})))))
  (testing "non-existent level returns empty"
    (is (= [] (filter-by :levels #{99})))))

;; ---------------------------------------------------------------------------
;; Class filter

(deftest class-filter-empty-returns-all
  (is (= all-chars (filter-by :classes #{}))))

(deftest class-filter-matches-any-class
  (testing "Ranger matches Aragorn and Legolas"
    (is (= (set [aragorn legolas]) (set (filter-by :classes #{"Ranger"})))))
  (testing "Fighter matches Aragorn and Gimli"
    (is (= (set [aragorn gimli]) (set (filter-by :classes #{"Fighter"})))))
  (testing "multi-class filter: Fighter OR Ranger → all three"
    (is (= (set all-chars) (set (filter-by :classes #{"Fighter" "Ranger"})))))
  (testing "non-existent class returns empty"
    (is (= [] (filter-by :classes #{"Rogue"})))))

;; ---------------------------------------------------------------------------
;; Portrait (has-portrait?) filter

(deftest portrait-filter-nil-returns-all
  (is (= all-chars (filter-by :portrait nil))))

(deftest portrait-filter-true-returns-only-chars-with-portrait
  (is (= [aragorn] (filter-by :portrait true))))

(deftest portrait-filter-false-returns-chars-without-portrait
  (is (= (set [legolas gimli]) (set (filter-by :portrait false)))))

;; ---------------------------------------------------------------------------
;; Faction-pic (has-faction-pic?) filter

(deftest faction-filter-nil-returns-all
  (is (= all-chars (filter-by :faction nil))))

(deftest faction-filter-true-returns-only-chars-with-faction-pic
  (is (= [legolas] (filter-by :faction true))))

(deftest faction-filter-false-returns-chars-without-faction-pic
  (is (= (set [aragorn gimli]) (set (filter-by :faction false)))))

;; ---------------------------------------------------------------------------
;; Combined filters (AND logic)

(deftest combined-name-and-class-filter
  (testing "name contains 'gorn' AND class is Ranger → Aragorn only"
    (is (= [aragorn] (filter-by :name "gorn" :classes #{"Ranger"})))))

(deftest combined-class-and-portrait-filter
  (testing "Fighter AND has portrait → Aragorn only"
    (is (= [aragorn] (filter-by :classes #{"Fighter"} :portrait true))))
  (testing "Fighter AND no portrait → Gimli only"
    (is (= [gimli] (filter-by :classes #{"Fighter"} :portrait false)))))

(deftest combined-level-and-faction-filter
  (testing "any level 8 AND has faction pic → Legolas"
    (is (= [legolas] (filter-by :levels #{8} :faction true)))))

;; ---------------------------------------------------------------------------
;; Toggle event logic (pure functions, no re-frame required)

(def toggle-set-item
  "Pure fn matching the toggle-char-level-filter / toggle-char-class-filter logic."
  (fn [db k v]
    (let [filters (or (get db k) #{})]
      (assoc db k ((if (filters v) disj conj) filters v)))))

(deftest toggle-set-item-adds-to-empty-set
  (let [db {}
        result (toggle-set-item db ::char5e/char-level-filters 3)]
    (is (= #{3} (::char5e/char-level-filters result)))))

(deftest toggle-set-item-removes-existing-value
  (let [db {::char5e/char-level-filters #{3 5}}
        result (toggle-set-item db ::char5e/char-level-filters 3)]
    (is (= #{5} (::char5e/char-level-filters result)))))

(deftest toggle-set-item-adds-to-existing-set
  (let [db {::char5e/char-level-filters #{5}}
        result (toggle-set-item db ::char5e/char-level-filters 3)]
    (is (= #{3 5} (::char5e/char-level-filters result)))))

(deftest toggle-set-item-handles-nil-key-in-db
  ;; When key not yet present, update must not call nil as a function
  (let [db {}
        result (toggle-set-item db ::char5e/char-class-filters "Rogue")]
    (is (= #{"Rogue"} (::char5e/char-class-filters result)))))

;; ---------------------------------------------------------------------------
;; Tri-state cycle logic (portrait / faction-pic toggle)

(def cycle-tristate #(case % nil true, true false, false nil))

(deftest tristate-cycle-nil-to-true
  (is (= true (cycle-tristate nil))))

(deftest tristate-cycle-true-to-false
  (is (= false (cycle-tristate true))))

(deftest tristate-cycle-false-to-nil
  (is (nil? (cycle-tristate false))))
