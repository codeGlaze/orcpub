(ns orcpub.running-head-test
  "The spellbook's running head must stay on the paper.

   The level tabs are placed AFTER the class statistics, so nothing bounded the pair:
   measured, three classes run to 344pt of A5's 347pt body, and a 60-character class
   label overruns Letter by 75pt. The tabs then print off the edge.

   Scoped to the tabs: chap-unit draws a class heading at 20pt with no bound, which
   overflows on Letter today and is not this work's to fix."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.common :as common]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw]
            [orcpub.spellbook :as sb]
            [orcpub.dnd.e5.spells :as spells])
  (:import (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument)))

(def ^:private spells-by-class
  {"Cleric" ["Guidance" "Bless" "Spirit Guardians"]
   "Wizard" ["Fire Bolt" "Shield" "Fireball"]
   "Druid"  ["Druidcraft" "Entangle" "Call Lightning"]})

(defn- caster [nm dc atk icon]
  {:class nm :class-kw (common/name-to-kw nm) :level 5 :ability "Wisdom"
   :dc dc :attack atk :icon icon})

(defn- render
  "Spellbook pages for `classes`, as drawing operators per page."
  [page classes]
  (binding [draw/*page* page]
    (let [known (->> (for [{:keys [class]} classes
                           ;; Any class name gets spells, including the 60-character one:
                           ;; an empty book draws no pages and the assertion goes vacuous.
                           n (get spells-by-class class (get spells-by-class "Cleric"))]
                       {:key (common/name-to-kw n) :class class
                        :level (:level (get spells/spell-map (common/name-to-kw n)))})
                     (group-by :level))
          out (ByteArrayOutputStream.)]
      (with-open [doc (PDDocument.)]
        (sb/add-spellbook! doc (draw/load-fonts doc) (draw/make-image-loader doc)
                           {:layout :book :order :level :class-break :runon :tabs :head
                            :classes classes :slots {1 4} :pact-slots {}
                            :character-name "Aldric"}
                           known spells/spell-map)
        (.save doc out))
      (with-open [doc (Loader/loadPDF (.toByteArray out))]
        (ops/doc-ops doc)))))

(defn- tabs-right
  "The right edge of the rightmost level tab. The tabs are the 10x10 `re` squares
   running-head! draws; scanning every operator instead would also catch chap-unit's
   unbounded class heading, which is a separate, pre-existing overflow."
  [pages]
  (apply max 0.0
         (for [page pages
               op page
               :let [ts (str/split op #" ")]
               :when (and (= 5 (count ts)) (= "re" (last ts))
                          (= "10.00" (nth ts 2)) (= "10.00" (nth ts 3)))]
           (+ (Double/parseDouble (first ts)) 10.0))))

(deftest the-running-head-stays-on-the-paper
  (doseq [[page-kw label] [[:letter "Letter"] [:a4 "A4"] [:a5 "A5"]]]
    (let [limit (first (draw/page-size page-kw))]
      ;; Guards the guard: 0.0 above means a book with no tabs would pass silently.
      (is (pos? (tabs-right (render page-kw [(caster "Cleric" 15 "+7" "holy-symbol")])))
          "tabs were drawn")
      (testing (str label ": three classes fit")
        ;; A5's body is 347.53pt and three classes' statistics alone reach 344.17pt,
        ;; so before the fix the tab strip started past the page edge.
        (is (<= (tabs-right (render page-kw [(caster "Cleric" 15 "+7" "holy-symbol")
                                             (caster "Wizard" 16 "+8" "spell-book")
                                             (caster "Druid" 14 "+6" "holy-symbol")]))
                limit)
            (str "the level tabs ran past " limit "pt on " label)))
      (testing (str label ": an over-long class label does not push the tabs off")
        (is (<= (tabs-right (render page-kw [(caster (apply str (repeat 60 "W"))
                                                     15 "+7" "spell-book")]))
                limit)
            (str "the level tabs ran past " limit "pt on " label))))))
