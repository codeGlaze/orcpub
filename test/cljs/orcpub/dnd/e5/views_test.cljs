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
;; IS testable without a DOM — which is the part that broke. Wrapping the input
;; in `str` to collapse blank lines rendered hiccup messages as their own source
;; text, and the save banner's export link stopped being a link at all. Nothing
;; caught it because every test in the suite passed strings.
;; ---------------------------------------------------------------------------

(defn- rendered-body
  "The banner's content node: [:div.pointer [:div.message _ BODY [:i]]]."
  [msg]
  (-> (notifications/message :warning msg identity) (nth 2) (nth 2) second))

(deftest hiccup-messages-are-not-stringified
  (testing "a hiccup message reaches the DOM as hiccup, not as printed source"
    (let [msg [:div "Saved. " [:span.pointer.underline {:on-click identity} "Export"]]]
      (is (vector? (rendered-body msg))
          "a stringified message renders markup as text and kills every control in it")
      (is (= msg (rendered-body msg)))))

  (testing "the clickable child survives with its handler"
    (let [handler (fn [_] :clicked)
          msg [:div [:span.pointer {:on-click handler} "Export"]]
          body (rendered-body msg)
          link (nth body 1)]
      (is (vector? link))
      (is (= handler (:on-click (second link)))))))

(deftest string-messages-still-collapse-blank-lines
  (testing "runs of blank lines become one break, so parts stay on their own lines"
    (is (= "one\ntwo\nthree" (rendered-body "one\n\ntwo\n\n\nthree"))))
  (testing "a single break is left alone"
    (is (= "one\ntwo" (rendered-body "one\ntwo")))))
