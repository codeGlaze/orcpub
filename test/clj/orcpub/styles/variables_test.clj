(ns orcpub.styles.variables-test
  "Every credit variable (--lk-*) the stylesheet reads is one it declares: an undeclared one makes
   its rule silently do nothing."
  (:require [clojure.test :refer [deftest is]]
            [garden.core :as garden]
            [orcpub.styles.core :as styles]))

(deftest every-credit-variable-read-is-declared
  (let [css (garden/css {:pretty-print? false} styles/app)
        read (set (map second (re-seq #"var\((--lk-[a-z0-9-]+)" css)))
        declared (set (map second (re-seq #"(--lk-[a-z0-9-]+):" css)))
        ;; set on each link element by the views, not in the stylesheet (portrait.cljs, artist_page.cljs)
        per-element #{"--lk-icon" "--lk-mark-color"}]
    (is (empty? (remove (some-fn declared per-element) read))
        (str "read but never declared: " (sort (remove (some-fn declared per-element) read))))))
