(ns orcpub.dnd.e5.portrait-credit-thumb-test
  "The character page's thumbnail credit, mounted into a real DOM: with more
   artists than its 100px strip holds, the lead is named and everyone else is
   one click away -- none of them clipped out of sight."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- split-registry [owners]
  (let [layers (:artist/layers pa/house-pack)]
    (mapv (fn [[id nm link ks]]
            (assoc pa/house-pack :artist/id id :artist/name nm :artist/link link
                   :artist/layers (select-keys layers ks)))
          owners)))

(defn- portrait-of [layer-keys]
  {:layers (into {} (for [k layer-keys
                          :let [a (first (pa/assets-for-layer k))] :when a]
                      [k {:asset/id (:asset/id a) :artist/id (pa/artist-for-asset k (:asset/id a))}]))})

(defn- mount [portrait]
  (let [el (.createElement js/document "div")]
    (.appendChild (.-body js/document) el)
    (rdom/render [#'portrait/linked-credit portrait] el)
    el))

(defn- texts [el sel] (mapv #(.-textContent %) (array-seq (.querySelectorAll el sel))))

(deftest many-artists-collapse-to-a-lead-and-a-button
  (with-redefs [pa/registry (split-registry
                             [[:a "Fusspot the Magnificent" "https://fusspot.rip/" [:head]]
                              [:b "Bexley Wintergreen" "https://bex.example/" [:hair-front]]
                              [:c "Cyrano" "https://cy.example/" [:shirt]]
                              [:d "Dee" nil [:eyes]]
                              [:e "Esme" "https://esme.example/" [:nose]]])]
    (let [el (mount (portrait-of [:head :hair-front :shirt :eyes :nose]))
          button (.querySelector el "button.pl-thumb-more")]
      (is (= ["Fusspot the Magnificent"] (texts el "a.pl-thumb-credit-link")) "only the lead shows at first")
      (is (= "+4" (some-> button .-textContent)))
      (is (= "false" (.getAttribute button "aria-expanded")))
      (.click button)
      (r/flush)
      (testing "the button opens every other artist, each still a link"
        (is (= "true" (.getAttribute button "aria-expanded")))
        (is (= ["Fusspot the Magnificent" "Bexley Wintergreen" "Cyrano" "Esme"]
               (texts el ".pl-thumb-more-list a")))
        (is (some #{"Dee"} (texts el ".pl-thumb-more-list span")) "an artist with no link is listed as text"))
      (.click button)
      (r/flush)
      (is (nil? (.querySelector el ".pl-thumb-more-list")) "and closes again")
      (rdom/unmount-component-at-node el))))

(deftest a-short-credit-is-shown-whole
  (with-redefs [pa/registry (split-registry [[:a "Fuss" "https://fusspot.rip/" [:head]]
                                             [:b "Bex" "https://bex.example/" [:shirt]]])]
    (let [el (mount (portrait-of [:head :shirt]))]
      (is (= "Art: Fuss with Bex" (.-textContent el)))
      (is (nil? (.querySelector el "button.pl-thumb-more")))
      (rdom/unmount-component-at-node el))))
