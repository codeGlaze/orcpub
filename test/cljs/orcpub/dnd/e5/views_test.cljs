(ns orcpub.dnd.e5.views-test
  "Tests for the pure helpers behind the character-display fail-soft logic.

   The UI components (error-boundary, render-guard, feature-render-error,
   character-health-warning) are React/Reagent class components and are not
   meaningfully unit-testable without a DOM + a mounted re-frame app, so they
   are not covered here. The one piece of pure, side-effect-free logic that
   drives the diagnostics — blank-feature-name? — is tested directly."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [clojure.string :as str]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.views :as views]))

(deftest blank-feature-name?-test
  (testing "nil names are blank"
    (is (true? (boolean (views/blank-feature-name? nil)))))
  (testing "empty string is blank"
    (is (true? (boolean (views/blank-feature-name? "")))))
  (testing "whitespace-only string is blank"
    (is (true? (boolean (views/blank-feature-name? "   "))))
    (is (true? (boolean (views/blank-feature-name? "\t\n")))))
  (testing "a real name is not blank"
    (is (false? (boolean (views/blank-feature-name? "Name")))))
  (testing "a name padded with whitespace is not blank"
    (is (false? (boolean (views/blank-feature-name? "  Rage  "))))))

;; ---------------------------------------------------------------------------
;; item-summary — the My Items row
;;
;; Hiccup is plain data, so the row's adornments can be checked without a DOM.
;; These are the markers that tell someone, at a glance down their item list,
;; that an item needs an answer or is holding magic in reserve.
;; ---------------------------------------------------------------------------

(defn- hiccup-seq
  "Every node in a hiccup tree, so a test can look for one without caring how
   deeply the markup nests it."
  [form]
  (tree-seq coll? seq form))

(defn- rendered-text
  [form]
  (str/join " " (filter string? (hiccup-seq form))))

(defn- titles
  "Every native title attribute in the tree — the hover text."
  [form]
  (keep :title (filter map? (hiccup-seq form))))

(def ^:private base-item
  {::mi/name "Rimefang" ::mi/type :weapon ::mi/owner "kaylee"})

(deftest item-summary-marks-an-item-holding-suspended-magic
  (let [row (views/item-summary
             (assoc base-item
                    ::mi/magical? false
                    ::mi/attunement #{:any}
                    ::mi/magical-attack-bonus 1))]
    (testing "the row says so in its own text, not only on hover"
      ;; Icon-only would be invisible to anyone on a touch device, where there
      ;; is no hover at all.
      (is (str/includes? (rendered-text row) "magic set aside")))
    (testing "and the hover detail names what is being held"
      (let [t (str/join " " (titles row))]
        (is (str/includes? t "attunement"))
        (is (str/includes? t "an attack bonus"))))
    (testing "the subtitle reads mundane and does not claim attunement"
      ;; "mundane (requires attunement)" would advertise a requirement that is
      ;; switched off — the same question-vs-state contradiction the Magic
      ;; Item? checkbox used to have.
      (is (str/includes? (rendered-text row) "mundane"))
      (is (not (str/includes? (rendered-text row) "requires attunement"))))))

(deftest item-summary-still-shows-attunement-on-a-magic-item
  (let [row (views/item-summary
             (assoc base-item ::mi/magical? true ::mi/rarity :rare
                    ::mi/attunement #{:any}))]
    (is (str/includes? (rendered-text row) "requires attunement"))))

(deftest item-summary-is-quiet-when-there-is-nothing-to-say
  (testing "ordinary gear with no magic in it gets no marker"
    (let [row (views/item-summary (assoc base-item ::mi/magical? false))]
      (is (not (str/includes? (rendered-text row) "magic set aside")))
      (is (empty? (titles row)))))
  (testing "a magic item gets no marker either — nothing is suspended"
    (let [row (views/item-summary
               (assoc base-item ::mi/magical? true ::mi/attunement #{:any}))]
      (is (not (str/includes? (rendered-text row) "magic set aside")))))
  (testing "nil renders nothing rather than throwing"
    (is (nil? (views/item-summary nil)))))

(deftest item-summary-flags-an-unanswered-legacy-item
  (let [row (views/item-summary {::mi/name "Old Trinket" ::mi/type :wondrous-item
                                 ::mi/rarity :common ::mi/owner "kaylee"})]
    (is (str/includes? (rendered-text row) "magical or mundane not set"))
    (testing "and does not also claim magic is set aside — there is none"
      (is (not (str/includes? (rendered-text row) "magic set aside"))))))

;; ---------------------------------------------------------------------------
;; item-details shows both kinds of prose
;;
;; Description and Magical Properties are stored apart so they can be told
;; apart. Displaying them has to keep BOTH -- an earlier version of this render
;; returned only one branch, which would have hidden a description the moment
;; an item gained magical prose.
;; ---------------------------------------------------------------------------

(defn- rendered-strings [hiccup]
  (filter string? (tree-seq coll? seq hiccup)))

(deftest item-details-renders-description-and-magical-properties
  (let [out (views/item-details
             {::mi/description "A plain-looking longsword."
              ::mi/magical-properties "Sheds dim light in a 5-foot radius."}
             true)
        strs (rendered-strings out)]
    (testing "both survive to the output"
      (is (some #(re-find #"plain-looking longsword" %) strs))
      (is (some #(re-find #"Sheds dim light" %) strs)))
    (testing "and the magical half is labelled"
      (is (some #(= "Magical Properties. " %) strs)))))

(deftest item-details-with-only-a-description-is-unchanged
  (let [strs (rendered-strings
              (views/item-details {::mi/description "Just a rope."} true))]
    (is (some #(re-find #"Just a rope" %) strs))
    (is (not-any? #(= "Magical Properties. " %) strs)
        "no empty label for an item with no magical prose")))

(deftest item-details-with-nothing-renders-nothing
  (is (nil? (views/item-details {} true))))
