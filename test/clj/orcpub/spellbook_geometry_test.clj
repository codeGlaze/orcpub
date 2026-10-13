(ns orcpub.spellbook-geometry-test
  "Pins the spellbook's drawing operators, for the same reason card_geometry_test pins the
   cards: spellbook.clj is the OTHER consumer of pdf.clj's shared surface, and the card
   baseline cannot see it move.

   spellbook_test asserts that every layout renders and that each spell's name appears.
   Neither reads a coordinate, so unifying the duplicated shape primitives (polyline!,
   circle!, line! exist in both files, and bezier-k is typed out twice) would be unchecked
   here. Re-seed a DELIBERATE change with CARD_GEOMETRY=seed.

   Pages are recorded as fingerprints, not full operator lists: a spellbook page runs
   ~8,300 operators, mostly curveTo from the shared SVG emblems, and 18 cases of that is
   7MB of redundant text. See ops/fingerprint for what a failure still tells you."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.pdf-ops :as ops]
            [orcpub.common :as common]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.canvas :as canvas]
            [orcpub.spellbook :as sb]
            [orcpub.dnd.e5.spells :as spells])
  (:import (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdmodel PDDocument)))

(def baseline-file "test/spellbook-geometry-baseline.edn")

(def ^:private book-spells
  "Two classes, six spells each, spanning cantrip to 9th so the level rings and the page
   breaks both get exercised. Same set spellbook_test renders."
  {"Cleric" ["Guidance" "Sacred Flame" "Bless" "Cure Wounds" "Spiritual Weapon"
             "Spirit Guardians"]
   "Wizard" ["Fire Bolt" "Magic Missile" "Shield" "Misty Step" "Fireball" "Wish"]})

(def ^:private base-opts
  {:classes [{:class "Cleric" :class-kw :cleric :level 5 :ability "Wisdom"
              :dc 15 :attack "+7" :icon "holy-symbol"}
             {:class "Wizard" :class-kw :wizard :level 6 :ability "Intelligence"
              :dc 16 :attack "+8" :icon "spell-book"}]
   :slots {1 4 2 3 3 3} :pact-slots {} :character-name "Aldric"})

(def ^:private cases
  "Every layout x order x tab placement: 18, the same matrix spellbook_test iterates.
   The layouts decide page structure, the tabs decide the edge art, and the emblems -
   the line art that shares bezier-k with pdf.clj - are drawn in all of them."
  (vec (for [layout [:book :ledger :prep]
             order [:level :alpha]
             tabs [:head :inset :off]]
         {:layout layout :order order :class-break :runon :tabs tabs})))

(defn- spell-of [n] (get spells/spell-map (common/name-to-kw n)))

(defn- render-one [opts]
  (let [known (->> (for [[cls ns] book-spells n ns]
                     {:key (common/name-to-kw n) :class cls :level (:level (spell-of n))})
                   (group-by :level))
        out (ByteArrayOutputStream.)]
    (with-open [doc (PDDocument.)]
      (sb/add-spellbook! doc (pdf/load-fonts doc) (pdf/make-image-loader doc)
                         (merge base-opts opts) known spells/spell-map)
      (.save doc out))
    (with-open [doc (Loader/loadPDF (.toByteArray out))] (ops/doc-fingerprints doc))))

(defn- all-pages
  "[labels pages] across every case, each page labelled by its case and index."
  []
  (reduce (fn [[labels pages] {:keys [layout order tabs] :as opts}]
            (let [ps (render-one opts)
                  tag (str (name layout) "/" (name order) "/" (name tabs))]
              [(into labels (map #(str tag " p" (inc %)) (range (count ps))))
               (into pages ps)]))
          [[] []] cases))

(deftest spellbook-geometry-is-unchanged
  (let [[labels pages] (all-pages)]
    (ops/check-baseline! baseline-file pages labels "Spellbook")))

(deftest the-cases-cover-what-they-claim
  (testing "every layout, order and tab placement"
    (is (= 18 (count cases)))
    (is (= #{:book :ledger :prep} (set (map :layout cases))))
    (is (= #{:level :alpha} (set (map :order cases))))
    (is (= #{:head :inset :off} (set (map :tabs cases)))))
  (testing "the spells span cantrips to 9th, so every level ring is drawn"
    (let [levels (set (map (comp :level spell-of) (mapcat val book-spells)))]
      (is (contains? levels 0))
      (is (contains? levels 9))))
  (testing "bezier-k lives in one place"
    (is (= 0.5522847 canvas/bezier-k))
    (doseq [ns- ['orcpub.spellbook 'orcpub.pdf]]
      (is (nil? (get (ns-interns ns-) 'bezier-k))
          (str ns- " re-declared bezier-k; it shares orcpub.pdf.canvas/bezier-k")))))
