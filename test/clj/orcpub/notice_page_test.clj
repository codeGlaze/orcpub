(ns orcpub.notice-page-test
  "The degraded-export notice: a page in the PDF naming what was left out.

   The export already caught failures and carried on -- a broken spellbook must not cost
   someone their sheet. What it did not do is say so: the file downloaded looking
   complete with a section missing. These pin that it says so, and that it stays silent
   when there is nothing to say."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.common :as common]
            [orcpub.errors :as errors]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf :as pdf]
            [orcpub.pdf.draw :as draw])
  (:import (org.apache.pdfbox.pdmodel PDDocument PDPage)
           (org.apache.pdfbox.text PDFTextStripper)))

(defn- page-text [doc i]
  (let [s (doto (PDFTextStripper.) (.setStartPage (inc i)) (.setEndPage (inc i)))]
    (str/replace (.getText s doc) #"\s+" " ")))

(defn- doc-with [n-pages]
  (let [doc (PDDocument.)]
    (dotimes [_ n-pages] (.addPage doc (PDPage.)))
    doc))

(deftest a-clean-export-gets-no-notice-page
  (testing "nothing recorded, nothing added -- the happy path pays nothing"
    (binding [errors/*notices* (atom [])]
      (with-open [doc (doc-with 3)]
        (is (nil? (pdf/add-notice-page! doc (draw/load-sans doc))))
        (is (= 3 (.getNumberOfPages doc))))))
  (testing "and with no collector bound at all it still does not throw"
    (with-open [doc (doc-with 2)]
      (is (nil? (pdf/add-notice-page! doc (draw/load-sans doc))))
      (is (= 2 (.getNumberOfPages doc))))))

(deftest what-was-dropped-is-named-on-page-one
  (testing "the notice is PREPENDED, so it is read before the sheet is printed"
    (binding [errors/*notices* (atom [])]
      (errors/note! "Spell cards" "could not be drawn and were left out (boom)")
      (errors/note! "Spellbook" "could not be drawn and was left out (kaput)")
      (with-open [doc (doc-with 3)]
        (is (some? (pdf/add-notice-page! doc (draw/load-sans doc))))
        (is (= 4 (.getNumberOfPages doc)) "one page added, not replacing a sheet page")
        (let [t (with-open [saved (ops/reloaded doc)] (page-text saved 0))]
          (is (str/includes? t "did not fit"))
          (is (str/includes? t "Spell cards"))
          (is (str/includes? t "Spellbook"))
          (is (str/includes? t "boom") "the reason, not just the section")
          (is (str/includes? t "kaput")))))))

(deftest note-records-without-a-collector
  (testing "note! is a no-op when nothing collects, so callers need no guard"
    (is (nil? (errors/note! "Anything" "detail")))
    (is (= [] (errors/notices))))
  (testing "and collects in order when one is bound"
    (binding [errors/*notices* (atom [])]
      (errors/note! "First" "a")
      (errors/note! "Second" "b")
      (is (= ["First" "Second"] (mapv :what (errors/notices)))))))

(defn- text-baselines
  "The y each drawn string actually sits at, at 2dp.

   Not simply every Td: the FIRST Td in a BT/ET block is absolute and any after it are
   RELATIVE to it, which is how draw-lines-to-box advances a line. Reading them all as
   absolute reports a one-leading offset (-10.45) as an off-page baseline."
  [page]
  (let [{:keys [out]}
        (reduce (fn [{:keys [y out] :as st} op]
                  (cond
                    (= "BT" op) (assoc st :y nil)
                    (re-matches #"[0-9.-]+ [0-9.-]+ Td" op)
                    (let [dy (Double/parseDouble (second (str/split op #" ")))]
                      (assoc st :y (if y (+ y dy) dy)))
                    (re-find #" Tj$" op) (assoc st :out (conj out (common/decimal-str 2 y)))
                    :else st))
                {:y nil :out []}
                (ops/page-ops page))]
    out))

(deftest no-two-lines-share-a-baseline
  (testing "details must not print on top of the next heading.

            This is the failure this page is prone to, and a text-extraction assertion
            reports it as a MISSING WORD -- the first version drew every detail over the
            following heading and the test said \"Spellbook\" was absent. Checking the
            drawn baselines names it directly."
    (binding [errors/*notices* (atom [])]
      (doseq [n ["Spell cards" "Spellbook" "Magic item cards" "Sheet fields"]]
        (errors/note! n (str n " was left out because of a reason that wraps a little")))
      (with-open [doc (doc-with 1)]
        (pdf/add-notice-page! doc (draw/load-sans doc))
        (let [ys (sort (map #(Double/parseDouble %) (text-baselines (.getPage doc 0))))
              gaps (map - (rest ys) ys)]
          (is (seq ys) "something was drawn")
          ;; A GAP, not equality. The bug this was written for put baselines 0.36pt
          ;; apart -- 11pt text overlaps long before two baselines coincide, so an
          ;; equality check could never have caught it.
          (is (every? #(>= % 9.0) gaps)
              (str "lines closer than 9pt overprint; baselines " (pr-str (vec ys))
                   " gaps " (pr-str (vec gaps)))))))))
