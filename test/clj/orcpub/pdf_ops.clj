(ns orcpub.pdf-ops
  "Reads the drawing operators off a rendered PDF page, and checks them against a
   recorded baseline. Shared by the geometry characterization tests: card_geometry_test
   pins the card sheets, spellbook_geometry_test pins the spellbook."
  (:require [clojure.string :as str]
            [orcpub.common :as common]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pprint]
            [clojure.test :refer [is testing]])
  (:import (java.security MessageDigest)
           (java.io ByteArrayOutputStream)
           (org.apache.pdfbox Loader)
           (org.apache.pdfbox.pdfparser PDFStreamParser)
           (org.apache.pdfbox.contentstream.operator Operator)
           (org.apache.pdfbox.cos COSNumber COSString COSName)))

(defn reloaded
  "`doc` saved and loaded back. A subset font is embedded only at save, so text read or
   rendered before then comes from PDFBox's fallback font, not the one that ships."
  [doc]
  (let [out (ByteArrayOutputStream.)]
    (.save doc out)
    (Loader/loadPDF (.toByteArray out))))

(defn- show-string
  "A drawn string as ASCII. Card and spellbook text go through an embedded Type0 font,
   so the bytes are 2-byte CIDs, not characters: decoding them gives control bytes that
   make the baseline a binary blob to git and kill the diff it exists for. Printable
   ASCII prints as itself; anything else goes to hex."
  [^COSString cs]
  (let [bs (.getBytes cs)]
    (if (every? #(<= 32 % 126) (map #(bit-and % 0xff) bs))
      (pr-str (String. bs "US-ASCII"))
      (str "#hex:" (apply str (map #(format "%02x" (bit-and % 0xff)) bs))))))

(defn- token-str [t]
  (cond
    ;; 2dp of a point is 1/3600 inch: finer than any hand tweak, coarse enough that
    ;; re-associating the same arithmetic does not flap the baseline.
    (instance? COSNumber t) (common/decimal-str 2 (.floatValue ^COSNumber t))
    (instance? COSString t) (show-string t)
    (instance? COSName t) (str "/" (.getName ^COSName t))
    :else (str t)))

(defn page-ops
  "Every operator on `page` as \"arg arg op\", in order: the page's geometry as PDFBox
   will write it."
  [page]
  (loop [[t & more] (seq (.parse (PDFStreamParser. page))), args [], out []]
    (if (nil? t)
      out
      (if (instance? Operator t)
        (recur more [] (conj out (str/join " " (conj args (.getName ^Operator t)))))
        (recur more (conj args (token-str t)) out)))))

(defn doc-ops
  "page-ops for every page of `doc`, in order."
  [doc]
  (mapv #(page-ops (.getPage doc %)) (range (.getNumberOfPages doc))))

(defn- sha12 [coll]
  (let [bs (.digest (MessageDigest/getInstance "SHA-256")
                    (.getBytes (str/join "\n" coll) "UTF-8"))]
    (subs (apply str (map #(format "%02x" (bit-and % 0xff)) bs)) 0 12)))

(defn fingerprint
  "A page's operators as a small map: the count, a histogram by operator, and hashes of
   the whole stream and of its drawing operators alone.

   For pages whose bulk is generated vector art. The spellbook runs ~8,300 operators a
   page, mostly curveTo from the shared SVG emblems, so recording them in full is 7MB of
   redundant text. This still fails on any change; the histogram says whether the shape
   counts changed, and the three hashes say whether geometry, colour or text moved."
  [ops]
  (let [kind #(or (last (str/split % #" ")) "")
        ;; Three disjoint hashes, so a failure says WHICH of the three moved. Everything
        ;; else -- graphics state, form XObjects -- shows only in :sha.
        geometry #{"m" "l" "c" "v" "y" "h" "re" "S" "s" "f" "F" "f*" "B" "b" "n" "w"}
        colour #{"rg" "RG" "g" "G" "k" "K" "sc" "SC" "scn" "SCN" "cs" "CS"}
        text #{"Tj" "TJ" "Td" "TD" "Tm" "Tf" "T*" "TL" "Tc" "Tw" "Tz" "Ts" "BT" "ET"}]
    {:ops (count ops)
     :kinds (into (sorted-map) (frequencies (map kind ops)))
     :sha (sha12 ops)
     :geom (sha12 (filter (comp geometry kind) ops))
     :colour (sha12 (filter (comp colour kind) ops))
     :text (sha12 (filter (comp text kind) ops))}))

(defn doc-fingerprints [doc]
  (mapv fingerprint (doc-ops doc)))

(defn- first-diffs
  "Up to `n` [index was now] where two op sequences disagree."
  [was now n]
  (->> (map vector (range) (concat was (repeat nil)) (concat now (repeat nil)))
       (take (max (count was) (count now)))
       (remove (fn [[_ a b]] (= a b)))
       (take n)
       (mapv (fn [[i a b]] [i a b]))))

(defn seeding? []
  (= "seed" (System/getenv "CARD_GEOMETRY")))

(defn- seed! [file pages what]
  (spit file
        (with-out-str
          (println ";; Written by orcpub's geometry characterization tests:" what)
          (println ";; A diff here is drawing moving. Read it before committing it.")
          (binding [*print-length* nil *print-level* nil]
            (pprint/pprint pages))))
  (println "seeded" (reduce + (map #(if (map? %) (:ops % 0) (count %)) pages))
           "operators across" (count pages) "pages to" file))

(defn check-baseline!
  "Seeds `file` from `pages` under CARD_GEOMETRY=seed; otherwise asserts `pages` matches
   it, printing the first differing operators per page. `labels` names the pages and
   `what` describes the set."
  [file pages labels what]
  (if (seeding?)
    (seed! file pages what)
    (do
      (is (some? (when (.exists (io/file file)) true))
          (str file " is missing: run CARD_GEOMETRY=seed"))
      (when (.exists (io/file file))
        (let [recorded (edn/read-string (slurp file))]
          (is (= (count recorded) (count pages)) "the page count changed")
          (doseq [[i was now nm] (map vector (range) recorded pages labels)]
            (testing (str "page " i " (" nm ")")
              (when-not (= was now)
                (if (map? was)
                  (if-not (:ops was)
                    ;; A fingerprint that is not operator counts -- the sheet's widget
                    ;; boxes. Report the keys that actually differ; the operator
                    ;; branch below would print "null ops" and an empty :kinds diff.
                    (do (println (format "%n%s %d (%s):" what i nm))
                        (doseq [k (sort (into #{} (concat (keys was) (keys now))))
                                :when (not= (get was k) (get now k))]
                          (println (format "  %-10s %s -> %s"
                                           (name k) (pr-str (get was k)) (pr-str (get now k))))))
                    (do (println (format "%n%s page %d (%s): %d ops recorded, %d now"
                                       what i nm (:ops was) (:ops now)))
                      (println "  stream" (:sha was) "->" (:sha now))
                      (let [moved (for [[k label] [[:geom "geometry"] [:colour "colour"]
                                                   [:text "text"]]
                                        :when (not= (k was) (k now))]
                                    label)]
                        (println "  moved:"
                                 (if (seq moved)
                                   (str/join ", " moved)
                                   "none of geometry, colour or text -- graphics state only")))
                      (doseq [k (sort (into #{} (concat (keys (:kinds was)) (keys (:kinds now)))))
                              :let [a (get (:kinds was) k 0) b (get (:kinds now) k 0)]
                              :when (not= a b)]
                        (println (format "  %-4s %d -> %d" k a b)))))
                  (do (println (format "%n%s page %d (%s): %d ops recorded, %d now"
                                       what i nm (count was) (count now)))
                      (doseq [[j a b] (first-diffs was now 12)]
                        (println (format "  op %4d  was %-34s now %s"
                                         j (pr-str a) (pr-str b)))))))
              (is (= was now)
                  "drawing moved; re-seed only if the move was intended"))))))))
