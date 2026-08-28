(ns orcpub.dnd.e5.views-test
  "Tests for the pure helpers behind the character-display fail-soft logic.

   The UI components (error-boundary, render-guard, feature-render-error,
   character-health-warning) are React/Reagent class components and are not
   meaningfully unit-testable without a DOM + a mounted re-frame app, so they
   are not covered here. The one piece of pure, side-effect-free logic that
   drives the diagnostics — blank-feature-name? — is tested directly."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [clojure.string :as str]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.equipment-subs]
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

(defn- visible-text
  "Strings that actually render as children, skipping attribute maps.

   rendered-text collects every string in the tree, which includes the value of
   a :title attribute — fine for asking whether something is present anywhere,
   useless for asking whether it is SHOWN, since hover text would count."
  [form]
  (cond
    (string? form) [form]
    (vector? form) (mapcat visible-text (remove map? form))
    (seq? form)    (mapcat visible-text form)
    :else          []))

(defn- marker-opts
  "The options item-summary passes to the shared magic-set-aside marker, or
   ::absent when the row does not carry one. The marker is a component, so its
   text is not in the hiccup until it renders — what a row CAN be asked is
   whether it included the marker and with what detail."
  [form]
  (or (some (fn [node]
              (when (and (vector? node)
                         (= views/magic-set-aside-marker (first node)))
                (or (second node) {})))
            (filter vector? (hiccup-seq form)))
      ::absent))

(def ^:private base-item
  {::mi/name "Rimefang" ::mi/type :weapon ::mi/owner "kaylee"})

(deftest item-summary-marks-an-item-holding-suspended-magic
  (let [row (views/item-summary
             (assoc base-item
                    ::mi/magical? false
                    ::mi/attunement #{:any}
                    ::mi/magical-attack-bonus 1))]
    (testing "the row carries the marker"
      (is (not= ::absent (marker-opts row))))
    (testing "and hands it detail naming what is being held"
      (let [d (:detail (marker-opts row))]
        (is (some? d))
        (is (str/includes? d "attunement"))
        (is (str/includes? d "an attack bonus"))))
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

;; ---------------------------------------------------------------------------
;; The same marker, on the shape the My Items list actually passes
;;
;; The list renders EFFECTIVE items, whose suspended mechanics have already
;; been stripped — so asking the item itself always answers no, and this
;; adornment never appeared there at all. It now reads the same
;; ::items-holding-magic subscription the character sheet uses.
;; ---------------------------------------------------------------------------

(deftest item-summary-marks-an-effective-item-using-the-subscription
  (let [raw {::mi/name "Rimefang" ::mi/type :weapon ::mi/owner "kaylee"
             ::mi/magical? false ::mi/attunement #{:any}
             ::mi/magical-attack-bonus 1}]
    (reset! app-db {::mi/custom-items [raw]})
    (rf/clear-subscription-cache!)
    (let [effective (assoc (mi/effective-item raw) :key :rimefang)]
      (testing "precondition: the stripped item cannot answer for itself"
        (is (not (mi/has-magical-properties? effective))))
      (testing "the marker still appears, via the subscription"
        (is (not= ::absent (marker-opts (views/item-summary effective)))))
      (testing "with no detail, so the general wording is used"
        ;; Passing a detail built from a stripped item would print
        ;; "kept but not applied: ." with an empty list.
        (is (nil? (:detail (marker-opts (views/item-summary effective)))))))))

(deftest item-summary-leaves-plain-gear-alone
  (let [plain {::mi/name "Plain Dagger" ::mi/type :weapon ::mi/owner "kaylee"
               ::mi/magical? false}]
    (reset! app-db {::mi/custom-items [plain]})
    (rf/clear-subscription-cache!)
    (is (= ::absent (marker-opts (views/item-summary (assoc plain :key :plain-dagger))))
        "an item with no suspended magic must not be adorned")))

;; ---------------------------------------------------------------------------
;; The marker's own explainer
;;
;; A title attribute is hover-only and a phone has no hover, so the line named
;; a condition it could never explain there. It opens on tap now, which means
;; there are two states to get right rather than one.
;; ---------------------------------------------------------------------------

(deftest magic-set-aside-closed-shows-only-the-line
  (let [out (views/magic-set-aside-content false identity)
        text (str/join " " (visible-text out))]
    (is (str/includes? text "magic set aside"))
    (testing "the explanation is not shown until asked for"
      (is (not (str/includes? text "switched off"))))
    (testing "but is still available on hover for a pointer"
      (is (some #(str/includes? % "switched off") (titles out))))
    (testing "and it advertises itself as expandable"
      (is (some #(= "false" (:aria-expanded %))
                (filter map? (hiccup-seq out)))))))

(deftest magic-set-aside-open-shows-the-explanation
  (let [out (views/magic-set-aside-content true identity)
        text (str/join " " (visible-text out))]
    (is (str/includes? text "magic set aside"))
    (is (str/includes? text "switched off")
        "tapping it must actually say what it means")
    (is (some #(= "true" (:aria-expanded %)) (filter map? (hiccup-seq out))))))

(deftest magic-set-aside-open-prefers-the-detail-it-was-given
  (let [text (str/join " " (visible-text
                            (views/magic-set-aside-content
                             true identity
                             {:detail "Magical properties kept: an attack bonus."})))]
    (is (str/includes? text "an attack bonus"))
    (is (not (str/includes? text "Open it under My Items"))
        "the specific detail replaces the general wording rather than joining it")))

(deftest magic-set-aside-toggle-does-not-reach-the-row-underneath
  (testing "the click is stopped before it toggles whatever contains it"
    ;; Both hosts -- a weapons row on the sheet and a My Items row -- are click
    ;; targets themselves. Without stopPropagation, asking what the marker
    ;; means would also expand or collapse the row.
    (let [stopped (atom false)
          marker ((views/magic-set-aside-marker))
          handler (some :on-click (filter map? (hiccup-seq marker)))]
      (is (some? handler))
      (handler #js {:stopPropagation #(reset! stopped true)})
      (is @stopped))))
