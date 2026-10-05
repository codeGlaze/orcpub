(ns orcpub.spellbook-test
  "The spellbook pages: their order, where the page breaks fall, what a request may ask
   for, and that every layout renders."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [orcpub.common :as common]
            [orcpub.pdf :as pdf]
            [orcpub.routes :as routes]
            [orcpub.spellbook :as sb]
            [orcpub.dnd.e5.emblems :as emblems]
            [orcpub.dnd.e5.spells :as spells])
  (:import (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument)
           (org.apache.pdfbox.text PDFTextStripper)))

(defn- spell [n] (get spells/spell-map (common/name-to-kw n)))

(deftest sort-order
  (let [names ["Fireball" "Fire Bolt" "Shield" "Mage Armor" "Counterspell"]
        sorted (fn [order] (map :name (sort-by (partial sb/spell-sort-key order) (map spell names))))]
    (testing "#520: by level, then name"
      (is (= ["Fire Bolt" "Mage Armor" "Shield" "Counterspell" "Fireball"] (sorted :level))))
    (testing "or by name alone"
      (is (= ["Counterspell" "Fire Bolt" "Fireball" "Mage Armor" "Shield"] (sorted :alpha))))
    (testing "an unknown order is the level order, which is what cards printed before"
      (is (= (sorted :level) (sorted nil))))))

;; Synthetic units: the paginator reads only :kind :h :full? :keep :cls and :spell.
(defn- sp [cls h n] {:kind :spell :cls cls :h h :spell {:name n :level 1}})
(defn- lvh [cls] {:kind :lvh :cls cls :h 20 :keep 1})
(defn- chap [cls] {:kind :chap :cls cls :h 50 :full? true :keep 1})
(defn- cont [cls] [{:kind :chap :cls cls :h 30 :full? true :cont? true}])

(defn- column-units [pages]
  (for [p pages b (:blocks p) col (:cols b)] col))

(deftest page-breaks
  (let [opts {:two-col? true :cap 600 :cont-units cont}]
    (testing "a level heading never ends a column on its own"
      (let [us (concat [(chap 0) (lvh 0)] (map #(sp 0 100 (str "a" %)) (range 9))
                       [(lvh 0)] (map #(sp 0 100 (str "b" %)) (range 4)))
            pages (sb/paginate us opts)]
        (is (every? #(not= :lvh (:kind (last %))) (filter seq (column-units pages))))))
    (testing "every spell is placed exactly once"
      (let [us (concat [(chap 0) (lvh 0)] (map #(sp 0 (+ 40 (* 13 (mod % 7))) (str "s" %)) (range 40)))
            pages (sb/paginate us opts)]
        (is (= (map #(str "s" %) (range 40)) (mapcat :names pages)))))
    (testing "a later class runs on when its head and first spells fit"
      (let [us [(chap 0) (lvh 0) (sp 0 80 "a") (chap 1) (lvh 1) (sp 1 80 "b")]]
        (is (= 1 (count (sb/paginate us (assoc opts :class-break :runon)))))
        (testing "or starts a fresh page when asked"
          (let [pages (sb/paginate us (assoc opts :class-break :page))]
            (is (= 2 (count pages)))
            (is (= ["b"] (:names (second pages))))))))
    (testing "a page continuing a class opens with its continued head"
      (let [us (concat [(chap 0) (lvh 0)] (map #(sp 0 200 (str "s" %)) (range 10)))
            pages (sb/paginate us opts)]
        (is (< 1 (count pages)))
        (is (every? #(:cont? (:full (first (:blocks %)))) (rest pages)))))
    (testing "a piece taller than a column still goes down, on a page of its own"
      (let [pages (sb/paginate [(chap 0) (lvh 0) (sp 0 900 "huge") (sp 0 50 "after")] opts)]
        (is (= ["huge" "after"] (mapcat :names pages)))))))

(defn- page-heights
  "How tall each page's content stands: full-width units plus the taller column of each
   column block."
  [pages]
  (for [p pages]
    (reduce + (for [b (:blocks p)]
                (if (:full b)
                  (:h (:full b))
                  (apply max 0 (map #(reduce + (map :h %)) (:cols b))))))))

(deftest nothing-runs-past-the-foot
  (testing "a level heading over a column-tall piece is not balanced into one too-tall column"
    (let [opts {:two-col? true :cap 600 :cont-units cont}
          ;; Page two opens with a continued head; the heading goes down alone, the tall
          ;; piece into the next column, and the closing rebalance must not stack them.
          us [(chap 0) (lvh 0) (sp 0 500 "a") (sp 0 500 "b") (lvh 0) (sp 0 560 "tall")]]
      (is (every? #(<= % 600) (page-heights (sb/paginate us opts)))))))

(deftest pact-slots-serve-every-level
  (with-open [doc (PDDocument.)]
    (let [us (sb/units (pdf/load-fonts doc) (pdf/make-image-loader doc)
                       {:classes [{:class "Warlock" :class-kw :warlock :level 5 :pact? true :icon "warlock-eye"}]
                        :slots {} :pact-slots {3 2} :layout :book :order :level :tabs :head}
                       {:all [{:key (common/name-to-kw "Charm Person") :class "Warlock"}
                              {:key (common/name-to-kw "Misty Step") :class "Warlock"}
                              {:key (common/name-to-kw "Counterspell") :class "Warlock"}]}
                       spells/spell-map)
          heads (filter #(= :lvh (:kind %)) us)]
      (testing "a Warlock's 1st and 2nd level headings show its pact slots, not none"
        (is (= [1 2 3] (map :level heads)))
        (is (every? #(= 2 (:slots %)) heads))
        (is (every? #(= 3 (:pact-level %)) heads))))))

(deftest the-slot-pool-wraps
  (with-open [doc (PDDocument.)]
    (let [pool #(first (sb/units (pdf/load-fonts doc) (pdf/make-image-loader doc)
                                 {:classes [{:class "Wizard" :class-kw :wizard :level 20 :icon "spell-book"}]
                                  :slots % :pact-slots {} :layout :prep :order :level :tabs :head}
                                 {:all [{:key (common/name-to-kw "Shield") :class "Wizard"}]}
                                 spells/spell-map))]
      (testing "a pool too wide for one line takes more lines, inside its box"
        (is (= :pool (:kind (pool {1 4}))))
        (is (< (:h (pool {1 4})) (:h (pool (into {} (for [l (range 1 10)] [l 20]))))))))))

(deftest abbreviations-and-summaries
  (testing "reaction is not read as re- plus action, on the cards or here"
    (is (= "1 React." (pdf/abbreviate-casting-time "1 reaction")))
    (is (= "1 B.A." (pdf/abbreviate-casting-time "1 bonus action")))
    (is (= "1 Act." (pdf/abbreviate-casting-time "1 action"))))
  (testing "a summary never opens on a list bullet"
    (is (not (str/starts-with? (sb/summary (spell "Wish")) "\u00B7")))))

(deftest request-sanitising
  (let [opts (routes/spellbook-options
              {:layout "nonsense" :order :alpha :class-break "page" :tabs :sideways
               :classes [{:class "Wizard" :level 6 :dc 15 :attack "+7" :icon "../../secrets"}
                         {:class "Cleric" :level 500 :icon "heraldic-sun"}
                         "not a map"]
               :slots {1 4 "2" 3 3 99 :x 1}}
              "Aldric")]
    (testing "unknown choices fall back to the defaults; known ones survive"
      (is (= :book (:layout opts)))
      (is (= :alpha (:order opts)))
      (is (= :page (:class-break opts)))
      (is (= :head (:tabs opts))))
    (testing "an emblem must be a vendored icon; a path is refused for the default"
      (is (= "spell-book" (:icon (first (:classes opts)))))
      (is (= "heraldic-sun" (:icon (second (:classes opts))))))
    (testing "numbers are bounded and junk entries dropped"
      (is (= 2 (count (:classes opts))))
      (is (nil? (:level (second (:classes opts)))))
      (is (= {1 4} (:slots opts)))))
  (testing "no classes, no spellbook"
    (is (nil? (routes/spellbook-options {:classes []} "x")))
    (is (nil? (routes/spellbook-options "junk" "x")))))

(deftest every-default-emblem-is-vendored
  (doseq [[class-kw options] emblems/pools
          [icon _] options]
    (is (some? (io/resource (str "public/image/emblems/" icon ".svg")))
        (str class-kw " offers " icon " but it is not in resources"))))

(def ^:private book-spells
  {"Cleric" ["Guidance" "Sacred Flame" "Bless" "Cure Wounds" "Spiritual Weapon" "Spirit Guardians"]
   "Wizard" ["Fire Bolt" "Magic Missile" "Shield" "Misty Step" "Fireball" "Wish"]})

(defn- render [opts]
  (let [spells-known (->> (for [[cls ns] book-spells n ns :let [k (common/name-to-kw n)]]
                            {:key k :class cls :level (:level (spell n))})
                          (group-by :level))
        out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (let [n (sb/add-spellbook! doc (pdf/load-fonts doc) (pdf/make-image-loader doc)
                                 (merge {:classes [{:class "Cleric" :class-kw :cleric :level 5 :ability "Wisdom"
                                                    :dc 15 :attack "+7" :icon "holy-symbol"}
                                                   {:class "Wizard" :class-kw :wizard :level 6 :ability "Intelligence"
                                                    :dc 16 :attack "+8" :icon "spell-book"}]
                                         :slots {1 4 2 3 3 3} :pact-slots {} :character-name "Aldric"}
                                        opts)
                                 spells-known spells/spell-map)]
        (.save doc out)
        [n (with-open [d (Loader/loadPDF (.toByteArray out))] (.getText (PDFTextStripper.) d))]))))

(deftest every-layout-renders
  (doseq [layout [:book :ledger :prep] order [:level :alpha] tabs [:head :inset :off]]
    (let [[n text] (render {:layout layout :order order :class-break :runon :tabs tabs})]
      (testing (str layout " " order " " tabs)
        (is (pos? n))
        (doseq [nm (mapcat val book-spells)]
          (is (str/includes? text nm) (str nm " is missing")))
        (is (str/includes? text "dungeonmastersvault.com"))))))
