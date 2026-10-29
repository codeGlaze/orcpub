(ns orcpub.styles.artist-pages-test
  "The artist pages' link and text colours hold up where the browser's own styles would override
   them: a visited link, and the muted text on the light theme."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [garden.core :as garden]
            [orcpub.styles.core :as styles]))

(def ^:private css (garden/css {:pretty-print? false} styles/app))

(defn- channel
  "The linear-light value of one 0-255 sRGB channel `c`."
  [c]
  (let [s (/ c 255.0)]
    (if (<= s 0.03928) (/ s 12.92) (Math/pow (/ (+ s 0.055) 1.055) 2.4))))

(defn- contrast-with-white
  "WCAG contrast ratio of the #rrggbb colour `hex` against white."
  [hex]
  (let [[r g b] (map #(Integer/parseInt % 16) (re-seq #"[0-9a-f]{2}" (subs hex 1)))
        lum (+ (* 0.2126 (channel r)) (* 0.7152 (channel g)) (* 0.0722 (channel b)))]
    (/ 1.05 (+ lum 0.05))))

(deftest visited-links-keep-the-page-colours
  (testing "a visited rule is (0,1,1) and beats a single-class colour, so each link class needs its own"
    (is (str/includes? css ".lk-about:visited{color:var(--lk-dim)}"))
    (is (re-find #"\.ap-foot a:visited,\.ap-missing a:visited\{color:inherit\}" css))
    (is (str/includes? css ".ap-link:visited{"))))

(deftest muted-text-on-the-light-theme-passes-contrast
  (let [[_ hex] (re-find #"\.pl-root\.ap-root\.light-theme\{--lk-dim:(#[0-9a-f]{6})\}" css)]
    (is (some? hex) "the light theme on an artist page redefines --lk-dim")
    (when hex
      (is (>= (contrast-with-white hex) 4.5) (str hex " on white")))))
