(ns orcpub.pdf-locale-test
  "PDF geometry must not depend on the JVM's default locale.

   docs/kb/locale-safety.md section 3 already states the rule -- pin the locale whenever
   you parse or format a machine-readable string -- and the PDF work broke it anyway:
   an unqualified (format \"%.2f\") writes \"5,00\" under tr_TR/es_ES, and
   draw/svg-path-ops tokenizes dot-decimals only, so one coordinate became two."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [orcpub.pdf-ops :as ops]
            [orcpub.pdf.draw :as draw])
  (:import (java.util Locale)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)))

(defn- under-locale
  "`f` with the JVM default locale set to `tag`, restored afterwards."
  [tag f]
  (let [saved (Locale/getDefault)]
    (try
      (Locale/setDefault (Locale/forLanguageTag tag))
      (f)
      (finally (Locale/setDefault saved)))))

(defn- sigil-paths
  "The spellbook's school sigils, re-read with the ns reloaded under the current locale.
   `sigils` is a load-time def, so the locale in force at load bakes into its strings --
   the same trap page-w/page-h hit, in a different guise."
  []
  (require 'orcpub.spellbook :reload)
  (->> @(resolve 'orcpub.spellbook/sigils)
       vals
       (mapcat identity)
       (filter string?)
       vec))

(deftest sigil-paths-do-not-depend-on-the-jvm-locale
  (let [en (under-locale "en-US" sigil-paths)
        tr (under-locale "tr-TR" sigil-paths)
        es (under-locale "es-ES" sigil-paths)]
    (testing "no sigil path carries a decimal comma"
      (doseq [[tag paths] [["tr-TR" tr] ["es-ES" es]]
              p paths]
        (is (not (str/includes? p ",")) (str tag ": " p))))
    (testing "and the paths are identical across locales"
      (is (= en tr) "tr-TR")
      (is (= en es) "es-ES"))
    ;; Guards the guard: if sigil-paths ever stops reaching the generated entry, the
    ;; comparisons above pass vacuously.
    (is (some #(str/starts-with? % "M5") en) "the evocation sigils are in what we read")
    ;; Restore the ns to the ambient locale for the rest of the suite.
    (require 'orcpub.spellbook :reload)))

(deftest a-decimal-comma-is-read-as-a-second-coordinate
  (testing "why the above matters: the tokenizer does not mis-read, it mis-counts"
    ;; Measured: 2 ops become 4, and 30.0 is off a 10-unit box -- the sigil draws as a
    ;; scribble rather than a star. This is the damage, not the string difference.
    (is (= [[:move 6.1 5.0] [:line 9.3 5.0]]
           (draw/svg-path-ops "M6.10 5.00 L9.30 5.00")))
    (is (= [[:move 6.0 10.0] [:line 5.0 0.0] [:line 9.0 30.0] [:line 5.0 0.0]]
           (draw/svg-path-ops "M6,10 5,00 L9,30 5,00")))))

(defn- line-ops
  "One drawn line as baseline operators, under the current locale."
  []
  (with-open [doc (PDDocument.)]
    (let [pg (PDPage.)]
      (.addPage doc pg)
      (with-open [cs (PDPageContentStream. doc pg)]
        ;; Non-integral coordinates, or %.2f and %,.2f render alike.
        (draw/draw-line cs 90.25 180.5 486.75 180.5))
      (ops/page-ops pg))))

(deftest baseline-operators-do-not-depend-on-the-jvm-locale
  (testing "pdf-ops renders operands the same in any locale, or every baseline flaps"
    (let [en (under-locale "en-US" line-ops)]
      (is (= en (under-locale "tr-TR" line-ops)) "tr-TR")
      (is (= en (under-locale "es-ES" line-ops)) "es-ES")
      (is (every? #(not (str/includes? % ",")) en) "no operand carries a decimal comma"))))

(def ^:private decimal-format-re
  "A `format` call with a float conversion and no explicit locale. String/format with
   Locale/ROOT does not match: its first argument is the locale, not the pattern."
  #"\(format\s+\"[^\"]*%[-+ 0,(#]*[0-9]*(?:\.[0-9]+)?[feEgGa]")

(def ^:private console-re
  "A format going straight to the console. A locale's own digits are RIGHT for a human
   reading a log line; this rule is about numbers that become machine tokens."
  #"\((?:println|print|printf)\s")

(deftest no-unlocalised-decimal-format-in-the-pdf-path
  (testing "the KB rule, as a check that fires at write time rather than in CI"
    ;; The KB's own survey grep covers only case-folding, so no grep would have caught
    ;; the sigil bug. This one covers formatting, which the rule also names.
    (let [offenders (for [root ["src" "test"]
                          f (file-seq (io/file root))
                          :when (and (.isFile f) (re-find #"\.cljc?$" (.getName f)))
                          :let [src (slurp f)]
                          [i line] (map-indexed vector (str/split-lines src))
                          :when (and (re-find decimal-format-re line)
                                     (not (re-find console-re line)))]
                      (str (.getPath f) ":" (inc i) " " (str/trim line)))]
      (is (empty? offenders)
          (str "use String/format with Locale/ROOT:\n" (str/join "\n" offenders))))))
