(ns orcpub.dnd.e5.homebrew-structure-test
  "How a library is stored, put back before its fields are read (homebrew-shapes/read-*), and the
   table's own fixes for cards and keys. These were e5/mend-*, now folded into homebrew-shapes:
   one repair path."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.spec.alpha :as spec]
            [orcpub.dnd.e5 :as e5]
            [orcpub.dnd.e5.homebrew-shapes :as shapes]))

(def ^:private spell-a {:option-pack "P" :key :fire-bolt :name "Fire Bolt" :level 0 :school "evocation"})
(def ^:private spell-b {:option-pack "P" :key :witch-bolt :name "Witch Bolt" :level 1 :school "evocation"})

(deftest read-section-recovers-the-shapes-damage-takes
  (testing "a section stored as text"
    (is (= {:section {:fire-bolt spell-a} :repair :section-from-text}
           (shapes/read-section (pr-str {:fire-bolt spell-a})))))
  (testing "a section stored as a list, keyed by each entry's own key"
    (is (= {:section {:fire-bolt spell-a :witch-bolt spell-b} :repair :section-from-list}
           (shapes/read-section [spell-a spell-b]))))
  (testing "a list entry without a key gets the key its name derives"
    (is (= :fire-bolt (-> (shapes/read-section [(dissoc spell-a :key)]) :section :fire-bolt :key))))
  (testing "an empty section is dropped"
    (doseq [v [nil [] "" "  " '()]]
      (is (= {:dropped :empty-section} (shapes/read-section v)) (pr-str v))))
  (testing "a good section and a true/false are left alone"
    (is (= {:section {:fire-bolt spell-a}} (shapes/read-section {:fire-bolt spell-a})))
    (is (= {:section true} (shapes/read-section true)))))

(deftest read-section-does-not-guess
  (testing "text that does not read as entries"
    (is (= {:damaged "corrupted"} (shapes/read-section "corrupted"))))
  (testing "two list entries claiming one key: keying them would lose one"
    (is (contains? (shapes/read-section [spell-a spell-a]) :damaged)))
  (testing "a list element that is not an entry"
    (is (contains? (shapes/read-section [spell-a 42]) :damaged))))

(deftest read-source-reports-each-repair
  (let [{s :source rs :repairs} (shapes/read-source {:orcpub.dnd.e5/spells (pr-str {:fire-bolt spell-a})
                                                     :orcpub.dnd.e5/feats nil
                                                     :disabled? false})]
    (is (= {:fire-bolt spell-a} (:orcpub.dnd.e5/spells s)))
    (is (not (contains? s :orcpub.dnd.e5/feats)))
    (is (false? (:disabled? s)) "non-content keys are untouched")
    (is (= #{{:section :orcpub.dnd.e5/spells :repair :section-from-text}
             {:section :orcpub.dnd.e5/feats :repair :empty-section}}
           (set rs))))
  (testing "a healthy source reports no repairs, so nothing is written back"
    (is (= [] (:repairs (shapes/read-source {:orcpub.dnd.e5/races {:fine {:key :fine :name "Fine"}}}))))))

(deftest a-read-section-passes-the-load-check
  (is (spec/valid? ::e5/plugin (:source (shapes/read-source {:orcpub.dnd.e5/spells [spell-a spell-b]})))))

(deftest read-library-reads-back-what-was-stored-as-text
  (testing "a source"
    (let [{l :library rs :repairs} (shapes/read-library {"P" (pr-str {:orcpub.dnd.e5/spells {:fire-bolt spell-a}})})]
      (is (= {:fire-bolt spell-a} (get-in l ["P" :orcpub.dnd.e5/spells])))
      (is (= [{:source "P" :repair :source-from-text}] rs))))
  (testing "the whole library"
    (let [lib {"P" {:orcpub.dnd.e5/feats {:tough {:option-pack "P" :key :tough :name "Tough"}}}}
          {:keys [library repairs]} (shapes/read-library (pr-str lib))]
      (is (= lib library))
      (is (= [{:repair :library-from-text}] repairs))))
  (testing "text that reads as a single source is not taken for a library"
    (is (string? (:library (shapes/read-library (pr-str {:orcpub.dnd.e5/feats {}}))))))
  (testing "text that does not read stays as it is"
    (is (= "nope {" (:library (shapes/read-library "nope {"))))))

(deftest read-import-reads-a-whole-file-stored-as-text
  (let [{d :data rs :repairs} (shapes/read-import (pr-str {:orcpub.dnd.e5/spells [spell-a]}))]
    (is (= {:fire-bolt spell-a} (:orcpub.dnd.e5/spells d)))
    (is (= [:file-from-text :section-from-list] (map :repair rs)))))

(defn- clean
  "`entry` of `content-type`, filed under `k` in source \"P\", through clean-library:
   {:entry e' :findings [[path action] ...]}."
  [content-type k entry]
  (let [{l :library fs :findings} (shapes/clean-library {"P" {content-type {k entry}}})]
    {:entry (get-in l ["P" content-type k])
     :findings (set (map (juxt :path :action) fs))}))

(deftest a-card-list-is-put-back-together
  (let [race #(clean :orcpub.dnd.e5/races :elfish {:key :elfish :name "Elfish" :traits %})]
    (testing "text where a card belongs is a card with that name"
      (is (= [{:name "Darkvision"} {:name "B"}] (:traits (:entry (race ["Darkvision" {:name "B"}]))))))
    (testing "a lone card, or lone text, is a list of one"
      (is (= [{:name "Solo"}] (:traits (:entry (race {:name "Solo"})))))
      (is (= [{:name "Darkvision"}] (:traits (:entry (race "Darkvision"))))))
    (testing "anything else cannot be read, so the entry is set aside (it was dropped with the field)"
      (is (contains? (:findings (race 42)) [[:traits] :unreadable]))
      (is (contains? (:findings (race ["Darkvision" 42])) [[:traits 1] :unreadable])))
    (testing "a sound list is left alone"
      (is (empty? (:findings (race [{:name "A"}])))))))

(deftest a-key-that-is-not-a-key-takes-the-one-it-is-filed-under
  ;; a text key stopped the app from starting
  (let [{:keys [entry findings]} (clean :orcpub.dnd.e5/races :elfish {:key "text" :name "Elfish"})]
    (is (= :elfish (:key entry)))
    (is (contains? findings [[:key] :fixed]))))

(deftest a-selection-whose-options-are-not-a-list-is-set-aside
  (is (contains? (:findings (clean :orcpub.dnd.e5/selections :pick {:key :pick :name "Pick" :options 7}))
                 [[:options] :unreadable])))

(deftest a-spell-whose-class-lists-cannot-be-read-is-set-aside
  ;; it stopped the character options from building; it now sets the spell aside
  (is (contains? (:findings (clean :orcpub.dnd.e5/spells :bolt {:key :bolt :name "Bolt" :spell-lists 42}))
                 [[:spell-lists] :unreadable]))
  (is (empty? (:findings (clean :orcpub.dnd.e5/spells :fine {:key :fine :name "Fine" :spell-lists {:wizard true}})))))

(deftest a-subrace-or-subclass-without-its-parent-cannot-be-read
  ;; it would never be shown, with nothing to say why
  (is (contains? (:findings (clean :orcpub.dnd.e5/subraces :kin {:key :kin :name "Kin"})) [[:race] :unreadable]))
  (is (contains? (:findings (clean :orcpub.dnd.e5/subclasses :oath {:key :oath :name "Oath" :class nil}))
                 [[:class] :unreadable])))

(deftest fix-library-keeps-an-unreadable-entry-as-written
  ;; the shape check sets it aside, with that original, when the import is stored
  (let [bad {:key :bad :name "Bad" :abilities 42}
        lib {"P" {:orcpub.dnd.e5/races {:hill {:key :hill :name "Hill" :speed "25"} :bad bad}}}
        {:keys [library findings]} (shapes/fix-library lib)]
    (is (= 25 (get-in library ["P" :orcpub.dnd.e5/races :hill :speed])) "a fix is applied")
    (is (= bad (get-in library ["P" :orcpub.dnd.e5/races :bad])) "the unreadable entry is untouched")
    (is (some #(= [:bad :unreadable] ((juxt :key :action) %)) findings))))
