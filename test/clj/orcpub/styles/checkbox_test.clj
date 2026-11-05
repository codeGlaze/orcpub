(ns orcpub.styles.checkbox-test
  "The tick box's edge, read from the compiled stylesheet: unticked, it must stand out from what is
   behind it by at least 3:1 (WCAG 1.4.11), in both themes and on a white panel in the dark one."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [garden.core :as garden]
            [orcpub.styles.core :as styles]))

(def ^:private css (delay (garden/css {:pretty-print? false} styles/app)))

(defn- rule
  "The declarations of the first rule whose selector is exactly `selector`."
  [selector]
  (some (fn [[_ sel body]] (when (= sel selector) body))
        (re-seq #"(?:^|\})([^{}]+)\{([^{}]*)" @css)))

(defn- decl
  "The value of `prop` in `body`, or nil."
  [body prop]
  (some->> body (re-find (re-pattern (str "(?:^|;)" prop ":([^;]+)"))) second s/trim))

(defn- rgba
  "[r g b a] of a CSS colour: #rgb, #rrggbb, rgb(), rgba() or white."
  [c]
  (let [c (s/trim c)]
    (cond
      (= c "white") [255 255 255 1.0]
      (re-matches #"#[0-9a-fA-F]{6}" c) (conj (mapv #(Integer/parseInt (subs c % (+ % 2)) 16) [1 3 5]) 1.0)
      (re-matches #"#[0-9a-fA-F]{3}" c) (conj (mapv #(* 17 (Integer/parseInt (subs c % (inc %)) 16)) [1 2 3]) 1.0)
      :else (let [[r g b a] (map #(Double/parseDouble %) (re-seq #"[\d.]+" c))] [r g b (or a 1.0)]))))

(defn- over
  "Colour `top` [r g b a] painted over opaque `under` [r g b]."
  [[r g b a] under]
  (mapv #(+ (* a %1) (* (- 1 a) %2)) [r g b] under))

(defn- contrast
  "WCAG contrast ratio of two opaque [r g b] colours."
  [x y]
  (let [lum (fn [rgb] (let [[r g b] (map #(let [v (/ % 255.0)]
                                            (if (<= v 0.03928) (/ v 12.92) (Math/pow (/ (+ v 0.055) 1.055) 2.4)))
                                         rgb)]
                        (+ (* 0.2126 r) (* 0.7152 g) (* 0.0722 b))))
        [hi lo] (sort > [(lum x) (lum y)])]
    (/ (+ hi 0.05) (+ lo 0.05))))

(def ^:private white [255 255 255])
(def ^:private dark-grounds
  "The dark theme's grounds under a tick box: the page, a menu panel, a portrait card."
  {"page" [8 10 13] "panel" [44 52 69] "card" [19 25 36]})

(deftest the-light-themes-edge-stands-out-on-white
  (let [edge (rgba (decl (rule ".app.light-theme .checkbox") "border-color"))]
    (is (>= (contrast (over edge white) white) 3.0)
        (str "edge " edge " on white: " (contrast (over edge white) white)))))

(deftest the-dark-themes-edge-stands-out-on-its-grounds
  (let [body (rule ".checkbox")
        edge (rgba (last (re-find #"solid (.+)$" (decl body "border"))))]
    (doseq [[k ground] dark-grounds]
      (is (>= (contrast (over edge ground) ground) 3.0) (str "edge on the " k)))))

(deftest the-dark-themes-box-holds-on-a-white-panel
  (testing "an opaque box, so on a white panel its fill (not a see-through tint) meets the white"
    (let [fill (rgba (decl (rule ".checkbox") "background-color"))]
      (is (= 1.0 (last fill)) (str "fill " fill))
      (is (>= (contrast (vec (take 3 fill)) white) 3.0)))))
