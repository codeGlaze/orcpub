(ns orcpub.dnd.e5.portrait-artists-arrive-test
  "Artist credits are fetched after the page loads, so every credit already on screen has to
   update when they arrive, and an export before then still carries the public default."
  (:require [cljs.test :refer-macros [deftest is testing]]
            [re-frame.core :refer [dispatch-sync]]
            [reagent.core :as r]
            [reagent.dom :as rdom]
            [orcpub.dnd.e5.events]
            [orcpub.dnd.e5.subs]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- portrait-of
  "A portrait choosing the first piece of each of `layer-keys`."
  [layer-keys]
  {:layers (into {} (for [k layer-keys
                          :let [a (first (pa/assets-for-layer k))] :when a]
                      [k {:asset/id (:asset/id a) :artist/id (pa/artist-for-asset k (:asset/id a))}]))})

(deftest the-credit-updates-when-the-credits-arrive
  (let [before (pa/current-overrides)
        el (.createElement js/document "div")
        p (portrait-of [:head :shirt])]
    (try
      (dispatch-sync [:portrait/artists-loaded {}])
      (.appendChild (.-body js/document) el)
      (rdom/render [portrait/thumbnail p nil false] el)
      (r/flush)
      (is (re-find #"Fusspot" (.-textContent el)) "the registry's public default until they arrive")
      (dispatch-sync [:portrait/artists-loaded {"house-pack" {:artist/name "Fuss <b>MARK</b>"}}])
      (r/flush)
      (testing "the arrival re-renders the credit already on screen"
        (is (re-find #"Fuss <b>MARK</b>" (.-textContent el)) "shown as text")
        (is (nil? (.querySelector el "b")) "never as markup"))
      (finally
        (rdom/unmount-component-at-node el)
        (.remove el)
        (pa/set-artist-overrides! before)))))

(deftest an-export-before-the-credits-arrive-carries-the-default
  (let [before (pa/current-overrides)]
    (try
      (dispatch-sync [:portrait/artists-loaded {}])
      (is (= ["Fusspot"] (pa/credit-names (portrait-of [:head :shirt])))
          "the PDF's credit falls back to the registry rather than to nothing")
      (finally (pa/set-artist-overrides! before)))))
