(ns orcpub.dnd.e5.portrait-whites-test
  "The builder's filled-in whites, mounted into a real DOM: under the eye they
   belong to, and painted between the two lids."
  (:require [cljs.test :refer-macros [deftest is testing async]]
            [reagent.dom :as rdom]
            [orcpub.dnd.e5.portrait :as portrait]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(defn- see-through-png
  "A transparent square with a dark dot in the middle: an eye style drawn
   without its whites."
  [n]
  (let [c (.createElement js/document "canvas")]
    (set! (.-width c) n) (set! (.-height c) n)
    (let [g (.getContext c "2d")]
      (set! (.-fillStyle g) "#141414")
      (.beginPath g) (.arc g (/ n 2) (/ n 2) (/ n 20) 0 (* 2 js/Math.PI)) (.fill g))
    (.toDataURL c "image/png")))

(def eye
  {:asset/id :test-eyes :asset/whites true :asset/pupil 0.45
   :asset/url (see-through-png 200)
   :asset/iris [{:cx 0.5 :cy 0.5 :rx 0.05 :ry 0.05 :rot 0.0
                 :lid {:x0 0.3 :y0 0.5 :mx 0.5 :my 0.3 :x1 0.7 :y1 0.5}
                 :lower {:x0 0.3 :y0 0.5 :mx 0.5 :my 0.7 :x1 0.7 :y1 0.5}}]})

(deftest whites-sit-under-the-eye-and-are-painted
  (async done
    (let [el (.createElement js/document "div")
          restore pa/asset-by-id]
      (set! pa/asset-by-id (fn [k id] (if (= [k id] [:eyes :test-eyes]) eye (restore k id))))
      (set! (.. el -style -width) "200px") (set! (.. el -style -height) "250px")
      (.appendChild (.-body js/document) el)
      (rdom/render [portrait/composite {:layers {:eyes {:asset/id :test-eyes}}
                                        :colors {:eyes "#3a7bd5" :whites "#a8322c"}}] el)
      (let [finish (fn []
                     (set! pa/asset-by-id restore)
                     (rdom/unmount-component-at-node el)
                     (done))
            check (fn check [tries]
                    (let [whites (.querySelector el "canvas.portrait-layer-whites")]
                      (if (and whites (= 200 (.-width whites)))
                        (let [layers (vec (array-seq (.querySelectorAll el "canvas.portrait-layer")))
                              px (.-data (.getImageData (.getContext whites "2d") 75 100 1 1))]
                          (is (= whites (first layers)) "the whites come before the eye, so the eye draws on top")
                          (is (= [168 50 44 255] (vec (array-seq px))) "painted between the lids in the colour picked")
                          (is (zero? (aget (.-data (.getImageData (.getContext whites "2d") 100 20 1 1)) 3))
                              "and nothing outside them")
                          (finish))
                        (if (pos? tries)
                          (js/setTimeout #(check (dec tries)) 50)
                          (do (is false "the whites canvas was never painted") (finish))))))]
        (check 60)))))
