(ns orcpub.running-head-test
  "The spellbook's page furniture must stay on the paper.

   The level tabs are placed AFTER the class statistics, so nothing bounded the pair:
   measured, three classes run to 344pt of A5's 347pt body, and a 60-character class
   label overruns Letter by 75pt. The tabs then print off the edge.

   chap-unit had the same shape: it drew the class name at 20pt with no bound, so a
   60-character name put the LEVEL text beside it at 1282pt on any paper."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.common :as common]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw]
            [orcpub.spellbook :as sb]
            [orcpub.dnd.e5.spells :as spells])
  (:import (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)))

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

(defn- path-right
  "The largest x any PATH operator reaches. The chapter heading's class name is drawn
   with showText, but the LEVEL text beside it comes from lining outlines, so an
   unbounded name shows up here as paths far off the page."
  [pages]
  (apply max 0.0
         (for [page pages
               op page
               :let [ts (str/split op #" ")
                     k (last ts)]
               :when (and (#{"re" "m" "l" "c"} k) (> (count ts) 2))
               ;; a curve carries three points; the last pair is its end
               t (if (= "c" k) [(nth ts 4)] [(first ts)])
               :let [x (Double/parseDouble t)]]
           (if (= "re" k) (+ x (Double/parseDouble (nth ts 2))) x))))

(deftest nothing-in-the-chapter-head-runs-past-the-paper
  (doseq [[page-kw label] [[:letter "Letter"] [:a4 "A4"] [:a5 "A5"]]]
    (let [limit (first (draw/page-size page-kw))
          long-name (apply str (repeat 60 "W"))]
      (testing (str label ": a 60-character class name is clipped, not drawn off-page")
        ;; chap-unit placed "LEVEL 5" at nm-x + the name's full width, which reached
        ;; 1282pt -- the same unbounded-then-offset shape as the level tabs.
        (is (<= (path-right (render page-kw [(caster long-name 15 "+7" "spell-book")]))
                limit)
            (str "drawing ran past " limit "pt on " label)))
      (testing (str label ": and an ordinary class name still fits")
        (is (<= (path-right (render page-kw [(caster "Cleric" 15 "+7" "holy-symbol")]))
                limit))))))

(defn- name-line-ops
  "name-line! alone on a blank page, as operators. Isolated because the running head
   spans the full width and would mask a name that only overruns its column."
  [spell col-w]
  (with-open [doc (PDDocument.)]
    (let [pg (PDPage.) fonts (draw/load-fonts doc)]
      (.addPage doc pg)
      (with-open [cs (PDPageContentStream. doc pg)]
        (#'sb/name-line! cs fonts (draw/make-image-loader doc) spell :level 36.0 700.0 col-w 10.0))
      (ops/page-ops pg))))

(defn- last-text-x
  "The furthest right a text run starts. The marks are drawn at the end of the name, so
   this is where the name actually ended."
  [page-ops]
  (apply max 0.0
         (let [{:keys [out]}
               (reduce (fn [{:keys [x out] :as st} op]
                         (let [ts (str/split op #" ")]
                           (cond
                             (= "BT" op) (assoc st :x 0.0)
                             (and (= 3 (count ts)) (= "Td" (last ts)))
                             (let [nx (+ x (Double/parseDouble (first ts)))]
                               {:x nx :out (conj out nx)})
                             :else st)))
                       {:x 0.0 :out []} page-ops)]
           out)))

(deftest a-long-spell-name-stays-in-its-column
  ;; No SRD name is long enough -- 0 of 319 overflow even on A5 -- but a custom spell's
  ;; name is bounded only by the request body, and the marks are placed after it.
  (let [long-spell {:key :probe-long :level 1 :school "evocation" :ritual true
                    :name (apply str (repeat 55 "W"))
                    :casting-time "1 action" :range "60 feet"
                    :components {:verbal true} :duration "Instantaneous"
                    :description "Short."}
        short-spell (assoc long-spell :name "Fire Bolt")]
    (doseq [col-w [262.0 165.77]]
      (testing (str "a 55-character name is clipped to a " col-w "pt column")
        (is (<= (last-text-x (name-line-ops long-spell col-w)) (+ 36.0 col-w))
            (str "text started past the column edge at " (+ 36.0 col-w) "pt")))
      (testing "and an ordinary name is untouched"
        (is (<= (last-text-x (name-line-ops short-spell col-w)) (+ 36.0 col-w)))))))
