(ns orcpub.dnd.e5.simple-content-builder-test
  "Proves simple-content-builder unifies the per-type builder forms. boon-builder and
   invocation-builder were byte-identical forms differing only by their set-prop event;
   they are now one-liners over simple-content-builder. These tests render each and assert
   the standard fields are present and wired to the RIGHT event — falsifiable: drop a field
   or cross the wires and a test goes red.

   Note: reagent form-2 children (plugin-datalist, textarea-field) appear as fn references in
   the returned hiccup and are not invoked here, so no DOM is needed."
  (:require [cljs.test :refer-macros [deftest testing is use-fixtures]]
            [re-frame.core :as rf]
            [re-frame.db :refer [app-db]]
            [orcpub.dnd.e5.classes :as classes]
            [orcpub.dnd.e5.views :as views]
            ;; Side effects: register the set-*-prop events and the builder-item subs
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.spell-subs]))

(defn reset-db! []
  (reset! app-db {:plugins {}})
  (rf/clear-subscription-cache!))

(use-fixtures :each {:before reset-db!})

(defn nodes [tree] (tree-seq sequential? seq tree))

(defn has-node? [tree pred] (boolean (some pred (nodes tree))))

(defn name-field-wired-to? [tree event]
  (has-node? tree #(and (vector? %)
                        (= views/builder-input-field (first %))
                        (= "Name" (second %))
                        (= event (nth % 4 nil)))))

(defn description-bound-to? [tree expected]
  (has-node? tree #(and (vector? %)
                        (= views/textarea-field (first %))
                        (= expected (:value (second %))))))

(deftest boon-builder-renders-standard-fields-wired-to-boon
  (testing "boon-builder shows Name + Description, wired to ::classes/set-boon-prop"
    (rf/dispatch-sync [::classes/set-boon {:name "B" :description "BoonDesc" :option-pack "P"}])
    (let [tree (views/boon-builder)]
      (is (= :div.p-20.main-text-color (first tree)))
      (is (name-field-wired-to? tree ::classes/set-boon-prop))
      (is (description-bound-to? tree "BoonDesc")))))

(deftest invocation-builder-renders-standard-fields-wired-to-invocation
  (testing "the SAME generic serves invocation, wired to ::classes/set-invocation-prop"
    (rf/dispatch-sync [::classes/set-invocation {:name "I" :description "InvDesc" :option-pack "P"}])
    (let [tree (views/invocation-builder)]
      (is (= :div.p-20.main-text-color (first tree)))
      (is (name-field-wired-to? tree ::classes/set-invocation-prop))
      (is (description-bound-to? tree "InvDesc")))))

(deftest extra-fields-render-for-richer-types
  (testing "a richer type's extra fields are appended (the field-list, not a bespoke form)"
    (rf/dispatch-sync [::classes/set-boon {:name "B" :option-pack "P"}])
    (let [marker [:div.my-extra-field "hi"]
          tree   (views/simple-content-builder ::classes/boon-builder-item
                                               ::classes/set-boon-prop
                                               [marker])]
      (is (has-node? tree #(= % marker))
          "extra-fields are included in the rendered form"))))
