(ns orcpub.dnd.e5.views-test
  "Tests for the pure helpers behind the character-display fail-soft logic.

   The UI components (error-boundary, render-guard, feature-render-error,
   character-health-warning) are React/Reagent class components and are not
   meaningfully unit-testable without a DOM + a mounted re-frame app, so they
   are not covered here. The one piece of pure, side-effect-free logic that
   drives the diagnostics — blank-feature-name? — is tested directly."
  (:require [cljs.test :refer-macros [deftest testing is]]
            [orcpub.dnd.e5.views :as views]
            [orcpub.dnd.e5.views.notifications :as notifications]))

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
;; Banner rendering
;;
;; `message` is a plain function returning hiccup, so what it does to its input
;; IS testable without a DOM — which is the part that broke. Collapsing blank
;; lines by running `str` over the input rendered hiccup messages as their own
;; source text, and the save banner's export link stopped being a link at all.
;; Nothing caught it because every message in the suite was a string.
;;
;; Asserted through the body slot rather than by walking indices from the top:
;; the tone icon and close button have moved once already, and a test that
;; breaks when they move is testing the wrong thing.
;; ---------------------------------------------------------------------------

(defn- message-body
  "The child of the banner's [:div.message-body …] node."
  [msg]
  (-> (notifications/message :warning msg identity) (nth 2) (nth 3) (nth 1)))

(deftest hiccup-messages-are-not-stringified
  (testing "a hiccup message reaches the body as hiccup, not as printed source"
    (let [msg [:div "Saved. " [:span.pointer.underline {:on-click identity} "Export"]]]
      (is (vector? (message-body msg))
          "stringifying renders markup as text and kills every control in it")
      (is (= msg (message-body msg)))))

  (testing "a clickable child keeps the handler that makes it do anything"
    (let [handler (fn [_] :clicked)
          msg [:div [:span.pointer {:on-click handler} "Export"]]
          link (nth (message-body msg) 1)]
      (is (vector? link))
      (is (= handler (:on-click (second link)))))))

(deftest string-messages-become-a-title-and-details
  (testing "the first line titles the banner, the rest are details"
    (let [body (message-body "one\n\ntwo\n\n\nthree")
          title (nth body 1)
          details (vec (nth body 2))]
      (is (= [:div.message-title "one"] title))
      (is (= ["two" "three"] (mapv #(nth % 1) details)))))

  (testing "a single-line string is all title, with no empty detail rows"
    (let [body (message-body "just this")]
      (is (= [:div.message-title "just this"] (nth body 1)))
      (is (empty? (vec (nth body 2)))))))
