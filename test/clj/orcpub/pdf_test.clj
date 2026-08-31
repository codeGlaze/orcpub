(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.cos COSName)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream)
           (org.apache.pdfbox.pdmodel.interactive.form PDTextField)))

(defn- all-fields [form]
  (iterator-seq (.iterator (.getFieldTree form))))

(defn- load-template []
  (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-2-0-spells.pdf"))]
    (Loader/loadPDF (.readAllBytes in))))

(deftest write-fields-generates-appearances
  (testing "REGRESSION: a filled text field gets a baked appearance stream and the
            form stays interactive, so the sheet renders in Firefox/PDF.js (which
            ignores NeedAppearances) AND remains fillable. Before the fix it set
            NeedAppearances true with no /Helv in the default resources, so no
            appearance stream was generated and Firefox showed blank."
    (with-open [doc (load-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            text-field (->> (all-fields form)
                            (filter #(instance? PDTextField %))
                            first)
            fname (.getFullyQualifiedName text-field)]
        (is (some? text-field) "template has a text field to fill")

        (pdf/write-fields! doc {(keyword fname) "Testvalue"} false {})

        (is (false? (.getNeedAppearances form))
            "NeedAppearances is OFF — we bake appearances, not defer to the viewer")
        (is (some? (.getFont (.getDefaultResources form) (COSName/getPDFName "Helv")))
            "/Helv font is in the AcroForm default resources")
        (let [f (.getField form fname)
              widget (first (.getWidgets f))]
          (is (= "Testvalue" (.getValueAsString f)) "value was written")
          (is (some? (.getAppearance widget))
              "widget has an /AP appearance dictionary (renders in every viewer)"))
        (is (pos? (count (all-fields form)))
            "form is still interactive (not flattened) — fillable")))))

(deftest write-fields-flatten-still-works
  (testing "when flatten is requested the values are baked and the form is removed"
    (with-open [doc (load-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            fname (.getFullyQualifiedName
                   (->> (all-fields form) (filter #(instance? PDTextField %)) first))]
        (pdf/write-fields! doc {(keyword fname) "Baked"} true {})
        (is (empty? (all-fields (.getAcroForm (.getDocumentCatalog doc))))
            "flattened form has no interactive fields left")))))

(deftest write-fields-handles-non-winansi-without-blanking
  (testing "a field value with smart quotes / CJK is coerced (not thrown on), so the
            field still gets written — regression for PDFBox 3's WinAnsi encoder"
    (with-open [doc (load-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            fname (.getFullyQualifiedName
                   (->> (all-fields form) (filter #(instance? PDTextField %)) first))]
        (pdf/write-fields! doc {(keyword fname) "“René” — 日"} false {})
        (is (= "\"René\" -- ?" (.getValueAsString (.getField form fname)))
            "smart quotes/dash downgraded, CJK -> '?', Latin-1 kept")))))

(deftest make-image-loader-embeds-once
  (testing "the per-document loader memoizes: the same icon path returns the SAME
            embedded image object (embedded/decoded once, referenced thereafter)"
    (with-open [doc (PDDocument.)]
      (let [img (pdf/make-image-loader doc)
            a (img "public/image/shiny-purse.png")
            b (img "public/image/shiny-purse.png")]
        (is (identical? a b) "same path -> same PDImageXObject, not a re-embed")))))

(deftest print-spells-runs-with-preloaded-fonts-and-loader
  (testing "print-spells takes pre-loaded fonts + the memoized image loader and
            renders a spell without throwing (load-fonts + icon embed hoisted out)"
    (with-open [doc (PDDocument.)]
      (.addPage doc (PDPage.))
      (let [fonts (pdf/load-fonts doc)
            img (pdf/make-image-loader doc)
            page (.getPage doc 0)]
        (with-open [cs (PDPageContentStream. doc page)]
          (let [spell [{:spell {:name "Bolt" :level 1 :school "evocation"
                                :components {:verbal true}}
                        :class-nm "Wizard" :dc 13 :attack-bonus 5}]]
            (is (sequential?
                 (pdf/print-spells cs doc fonts img 2.5 3.5 spell 0 false false false))
                "color icons: returns the remaining-lines sequence without throwing")
            (is (sequential?
                 (pdf/print-spells cs doc fonts img 2.5 3.5 spell 0 false true false))
                "B&W solid: solid-black -bw icons + halo labels, renders without throwing")
            (is (sequential?
                 (pdf/print-spells cs doc fonts img 2.5 3.5 spell 0 false true true))
                "B&W faded: grayscale icons (reduced alpha), renders without throwing")))))))

(deftest print-backs-renders-with-optional-logo
  (testing "print-backs draws the card backs given a resolved logo image path (or
            nil = off), both without throwing (logo is opt-in for double-sided
            printing; the grayscale/black choice is a caller-resolved resource)"
    (with-open [doc (PDDocument.)]
      (.addPage doc (PDPage.))
      (let [fonts (pdf/load-fonts doc)
            img (pdf/make-image-loader doc)
            page (.getPage doc 0)
            lines (vec (repeat 9 {:remaining-lines ["overflow line"] :spell-name "Bolt"}))]
        (with-open [cs (PDPageContentStream. doc page)]
          (is (sequential? (pdf/print-backs cs fonts img 2.5 3.5 lines 0 "public/image/dmv-logo-bw.png"))
              "grayscale logo on")
          (is (sequential? (pdf/print-backs cs fonts img 2.5 3.5 lines 0 "public/image/dmv-logo-black.png"))
              "solid-black logo on")
          (is (sequential? (pdf/print-backs cs fonts img 2.5 3.5 lines 0 nil))
              "logo off"))))))

(deftest normalize-text-coerces-to-winansi
  (testing "unit: smart punctuation downgraded, controls dropped, non-Latin-1 -> '?'"
    (let [nt #'orcpub.pdf/normalize-text]
      (is (= "'q' \"d\" - -- ..."
             (nt "‘q’ “d” – — …")))
      (is (= "ab c" (nt "ab\tc")) "BEL dropped, tab -> space")
      (is (= "??" (nt "日本")) "CJK -> placeholders, no throw")
      (is (nil? (nt nil))))))

(deftest fonts-test
  (testing "Font creation and ability to print latin and cyrillic characters"
    (let [^PDDocument doc (PDDocument.)
          ^PDPage page (PDPage.)
          fonts (pdf/load-fonts doc)
          required-keys [:plain :bold :italic :bold-italic]]
      (is (every? some? (map fonts required-keys)))
      (.addPage doc page)
      ;; Single content stream tests all fonts - more efficient than 4 separate streams
      (is (with-open [cs (PDPageContentStream. doc page)]
            (doseq [font-type required-keys]
              (doto cs
                (.beginText)
                (.setFont (font-type fonts) 14)
                (.newLineAtOffset (float 72) (float 700))
                (.showText (str (name font-type) ": abcABC012_?%абвАБВ"))
                (.endText)))
            true))
      (.close doc))))

;; ─── Orphaned widgets, overflow, and the silent skip ─────────────────────────

(defn- style-1-template []
  (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-1-1-spells.pdf"))]
    (Loader/loadPDF (.readAllBytes in))))

(defn- valued-fields
  "{fully-qualified-name value} for every field currently holding something."
  [form]
  (into {} (for [f (all-fields form)
                 :let [v (str (.getValueAsString f))]
                 :when (not (str/blank? v))]
             [(.getFullyQualifiedName f) v])))

(deftest prune-orphan-widgets-loses-nothing
  (testing "Pruning drops only widgets that belong to no page, so no value can be
            lost. The style 1 templates were cut from a 9-page master by DELETING
            pages, which leaves the fields behind -- about 1600 of 1900 widgets on
            a real export, and most of the download.

            Values are set through the PDFBox API rather than write-fields! here,
            because write-fields! now prunes for us and there would be nothing
            left to remove."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (doseq [[nm v] {"character-name" "Prune Test"
                        "class-level" "Wizard 20"
                        "backstory" "A sentence that must survive."}]
          (.setValue (.getField form nm) v))
        (let [before (valued-fields form)
              removed (pdf/prune-orphan-widgets! doc)
              after (valued-fields form)]
          (is (pos? removed) "the template really does carry orphaned widgets")
          (is (= before after)
              "every value present before the prune is present and unchanged after")
          (is (seq before) "the fixture actually set some values to compare"))))))

(deftest write-fields-prunes-orphans-when-not-flattening
  (testing "The exporter prunes on the way out, so a normal fillable export does
            not ship widgets that belong to no page. On a production sheet this is
            the difference between 2679 KB and 1313 KB."
    (with-open [doc (style-1-template)]
      (pdf/write-fields! doc {:character-name "Pruned On Export"} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (is (empty? (for [f (all-fields form)
                          w (.getWidgets f)
                          :when (nil? (.getPage w))]
                      f))
            "no field is left holding a widget that belongs to no page")
        (is (some? (.getField form "character-name"))
            "and the fields that ARE on a page survive")))))

(deftest write-fields-reports-names-it-cannot-place
  (testing "REGRESSION: unknown names used to be skipped in silence, which is how
            styles 2-4 shipped with no `backstory` field and how a character with
            more than six spellcasting classes loses two of them with no error."
    (with-open [doc (style-1-template)]
      (let [dropped (pdf/write-fields! doc {:character-name "Real Field"
                                            :spells-3-11-1 "missing from style 1"
                                            :spellcasting-class-7 "past the ceiling"}
                                       false {})]
        (is (= ["spellcasting-class-7" "spells-3-11-1"] dropped)
            "returns the unplaceable names, sorted, and not the ones that landed")))))

(deftest fit-text-splits-on-a-legible-size
  (testing "These fields auto-size, so an overlong value shrinks toward a 4pt floor
            and only then clips -- illegible before it loses anything. fit-text is
            the cutoff: what fits at min-font-size stays, the rest spills."
    (let [;; roughly the bonds/ideals/flaws box
          w 149.0 h 31.0]
      (testing "short text fits whole"
        (let [{:keys [head tail]} (pdf/fit-text "Owes a patron he has never seen." w h)]
          (is (nil? tail))
          (is (= "Owes a patron he has never seen." head))))
      (testing "long text splits, and nothing is lost between head and tail"
        (let [words (str/join " " (map #(str "w" %) (range 200)))
              {:keys [head tail]} (pdf/fit-text words w h)]
          (is (some? tail) "200 words cannot fit a three-line box at 7pt")
          (is (= words (str/join " " [head tail]))
              "head and tail together reproduce the input exactly")))
      (testing "blank and nil are handled without blowing up"
        (is (= {:head "" :tail nil :lines 0} (pdf/fit-text "" w h)))
        (is (= {:head "" :tail nil :lines 0} (pdf/fit-text nil w h))))
      (testing "a bigger box holds more of the same text"
        (let [words (str/join " " (map #(str "w" %) (range 200)))
              small (pdf/fit-text words w h)
              large (pdf/fit-text words 350.0 300.0)]
          (is (> (count (:head large)) (count (:head small)))))))))

(deftest widget-box-measures-the-on-page-widget
  (testing "Every field in these templates carries two widgets and the FIRST is the
            orphan, so measuring (first (.getWidgets f)) gives the wrong box."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            field (.getField form "features-and-traits-2")
            [w h] (pdf/widget-box doc field)]
        (is (> w 400.0) "the continuation box is most of a page wide")
        (is (> h 600.0) "and most of a page tall")))))

(deftest documented-word-budgets-still-hold
  (testing "Pins how much text each box holds at pdf/min-font-size, so re-cutting
            a template cannot silently change capacity. Tolerance is a factor of
            two: this catches a box resized, not a word of wrap drift."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ;; ~5 characters plus a space, the average the budgets assume
            words (fn [n] (str/join " " (repeat n "spells")))
            budget (fn [field-name]
                     (let [[w h] (pdf/widget-box doc (.getField form field-name))]
                       ;; largest n that still fits whole
                       (loop [lo 1 hi 6000]
                         (if (>= (inc lo) hi)
                           lo
                           (let [mid (quot (+ lo hi) 2)]
                             (if (nil? (:tail (pdf/fit-text (words mid) w h)))
                               (recur mid hi)
                               (recur lo mid)))))))]
        (doseq [[field-name documented] {"bonds" 25
                                         "ideals" 25
                                         "flaws" 25
                                         "personality-traits" 44
                                         "attacks-and-spellcasting" 127
                                         "other-profs" 147
                                         "backstory" 987}]
          (let [actual (budget field-name)]
            (is (< (* 0.5 documented) actual (* 2.0 documented))
                (str field-name " holds ~" actual " words at " pdf/min-font-size
                     "pt; the KB documents ~" documented))))))))

(deftest split-lines-terminates-on-an-unfittable-word
  (testing "A word wider than the box gets its own line. Retrying it against an
            empty current-line never consumes it, so this loops forever and grows
            `lines` without bound. Reachable from /character.pdf, which is
            unauthenticated: draw-text-to-box renders custom spell names into a
            roughly two-inch card box."
    (let [result (future (pdf/split-lines "supercalifragilisticexpialidocious tail"
                                          pdf/HELVETICA 8.0 0.2))
          value (deref result 5000 ::timed-out)]
      (when (= ::timed-out value) (future-cancel result))
      (is (not= ::timed-out value) "split-lines must terminate")
      (is (= ["supercalifragilisticexpialidocious" "tail"] value)
          "the oversized word takes a line of its own and the rest continues"))))

(deftest fit-text-preserves-hard-line-breaks
  (testing "Line breaks carry meaning: traits-fields in pdf_spec separates its
            Actions/Reactions sections with them, and spill headings sit on their
            own line. Wrapping must not flatten them."
    (let [text "FIRST\nalpha beta\n\nSECOND\ndelta"]
      (testing "kept intact when everything fits"
        (is (= text (:head (pdf/fit-text text 300.0 60.0)))))
      (testing "kept on both sides of a split"
        (let [{:keys [head tail]} (pdf/fit-text text 300.0 12.0)]
          (is (some? tail))
          (is (str/includes? (str head tail) "\n")
              "breaks survive rather than being joined with spaces"))))))

(deftest spill-overflow-moves-the-excess-and-keeps-everything
  (testing "Values too long for their box are trimmed to what fits at
            pdf/min-font-size and the remainder is written to appended pages.
            Without this the field auto-sizes down to 4pt and then clips, so the
            sheet goes unreadable before it loses anything."
    (with-open [doc (style-1-template)]
      (let [long-bonds (str/join " " (repeat 120 "obligation"))
            fields {:character-name "Spill Test"
                    :ideals "Short enough to stay put."
                    :bonds long-bonds}
            before (.getNumberOfPages doc)
            trimmed (pdf/spill-overflow! doc fields)]
        (is (> (.getNumberOfPages doc) before) "a continuation page was appended")
        (is (= (:ideals fields) (:ideals trimmed)) "a value that fits is untouched")
        (is (< (count (:bonds trimmed)) (count long-bonds)) "the long value was trimmed")
        (pdf/write-fields! doc trimmed false {})
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              written (str/join " " (map #(str (.getValueAsString %)) (all-fields form)))]
          (is (str/includes? written "BONDS")
              "the spilled section is labelled on the continuation page")
          (is (every? #(str/includes? written %)
                      (distinct (str/split long-bonds #"\s+")))
              "no word of the input is missing from the finished document"))))))
