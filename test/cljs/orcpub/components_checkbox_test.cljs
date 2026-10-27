(ns orcpub.components-checkbox-test
  "The tick box is a keyboard control: a screen reader hears a checkbox and its state, Tab reaches
   it, and Space or Enter does what a click does."
  (:require [cljs.test :refer-macros [deftest is]]
            [re-frame.core :as rf]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [orcpub.components :as comps]
            [orcpub.dnd.e5.views :as views]))

(defn- with-rendered
  "Renders `view` into a detached element, calls `f` with it, and cleans up."
  [view f]
  (let [el (.createElement js/document "div")]
    (try
      (.appendChild (.-body js/document) el)
      (rdom/render view el)
      (r/flush)
      (f el)
      (finally
        (rdom/unmount-component-at-node el)
        (.remove el)))))

(defn- press!
  "Dispatch a keydown of `key` on `el`."
  [el key]
  (.dispatchEvent el (js/KeyboardEvent. "keydown" #js {:key key :bubbles true :cancelable true})))

(deftest a-box-is-a-checkbox-you-can-reach-and-press
  (let [clicks (atom 0)]
    (with-rendered [:div {:on-click #(swap! clicks inc)} [comps/checkbox true false "Ritual"]]
      (fn [el]
        (let [box (.querySelector el ".checkbox")]
          (is (= "checkbox" (.getAttribute box "role")))
          (is (= "true" (.getAttribute box "aria-checked")))
          (is (= "0" (.getAttribute box "tabindex")) "Tab reaches it")
          (is (= "Ritual" (.getAttribute box "aria-label")))
          (press! box " ")
          (press! box "Enter")
          (is (= 2 @clicks) "Space and Enter click it, so the caller's handler runs"))))))

(deftest a-disabled-box-is-skipped-and-says-so
  (with-rendered [comps/checkbox false true]
    (fn [el]
      (let [box (.querySelector el ".checkbox")]
        (is (= "false" (.getAttribute box "aria-checked")))
        (is (= "true" (.getAttribute box "aria-disabled")))
        (is (= "-1" (.getAttribute box "tabindex")))))))

(deftest a-labelled-box-is-one-control-named-by-its-label
  (let [clicks (atom 0)]
    (with-rendered [comps/labeled-checkbox "Verbal" false false #(swap! clicks inc)]
      (fn [el]
        (let [row (.querySelector el "[role=checkbox]")]
          (is (some? row))
          (is (= "Verbal" (.-textContent row)) "named by its own label")
          (is (= "0" (.getAttribute row "tabindex")))
          (is (= 1 (.-length (.querySelectorAll el "[tabindex='0']"))) "one stop, not two")
          (press! row "Enter")
          (is (= 1 @clicks)))))))

(deftest a-spell-cannot-be-prepared-past-the-limit
  (let [sent (atom [])]
    (with-redefs [rf/dispatch #(swap! sent conj (first %))]
      (with-rendered [views/prepared-spell-box 1 :wizard :shield "Shield" false false 0]
        (fn [el]
          (let [box (.querySelector el ".checkbox")]
            (is (= "true" (.getAttribute box "aria-disabled")) "no preparations left: the box is off")
            (.click box)
            (is (empty? @sent) "and a click prepares nothing"))))
      (with-rendered [views/prepared-spell-box 1 :wizard :shield "Shield" false false 1]
        (fn [el]
          (.click (.querySelector el ".checkbox"))
          (is (= 1 (count @sent)) "with one left, a click prepares it"))))))
