(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.pdf :as pdf])
  (:import (org.apache.pdfbox Loader)
           (org.apache.pdfbox.cos COSName)
           (org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream PDResources)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget)
           (org.apache.pdfbox.pdmodel.interactive.form PDAcroForm PDTextField)))

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

(defn- six-caster-template
  "The widest style 1 variant. Its duplicate names survive pruning because every
   copy sits on one of the six spell pages; on narrower variants the copies are
   orphans and pruning alone removes them."
  []
  (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-1-6-spells.pdf"))]
    (Loader/loadPDF (.readAllBytes in))))

(defn- dirty-doc
  "A two-page document with an orphaned widget and a duplicated field name, the
   two faults dev/prepare_templates.clj removes from the shipped templates."
  []
  (let [doc (PDDocument.)
        page-one (PDPage.) page-two (PDPage.)
        form (PDAcroForm. doc)
        res (PDResources.)
        mk (fn [nm page]
             (let [field (PDTextField. form)
                   widget (PDAnnotationWidget.)]
               (.setPartialName field nm)
               (.setRectangle widget (PDRectangle. 50 700 100 20))
               (when page
                 (.setPage widget page)
                 (.setAnnotations page (java.util.ArrayList.
                                        (conj (vec (.getAnnotations page)) widget))))
               (.setWidgets field (java.util.ArrayList. [widget]))
               field))]
    (.addPage doc page-one)
    (.addPage doc page-two)
    (.put res (COSName/getPDFName "Helv") (PDType1Font. Standard14Fonts$FontName/HELVETICA))
    (.setDefaultResources form res)
    (.setDefaultAppearance form "/Helv 10 Tf 0 g")
    (.setAcroForm (.getDocumentCatalog doc) form)
    (.setFields form (java.util.ArrayList.
                      [(mk "on-a-page" page-one)
                       (mk "shared" page-one)
                       (mk "shared" page-two)
                       (mk "orphan" nil)]))
    doc))

(deftest shipped-templates-are-already-clean
  (testing "dev/prepare_templates.clj bakes the static cleanups into resources/, so
            the export path does not repeat them on every request. This asserts the
            result, and fails if a template is replaced without being prepared."
    (doseq [variant (range 0 7)]
      (with-open [in (.openStream (io/resource (str "fillable-char-sheetstyle-1-"
                                                    variant "-spells.pdf")))
                  doc (Loader/loadPDF (.readAllBytes in))]
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              names (map #(.getFullyQualifiedName %) (all-fields form))]
          (is (empty? (for [f (all-fields form)
                            w (.getWidgets f)
                            :when (nil? (.getPage w))]
                        f))
              (str variant "-spells still has a widget belonging to no page"))
          (is (empty? (->> names frequencies (filter (fn [[_ n]] (> n 1))) (map key)))
              (str variant "-spells still has duplicate field names"))
          (is (empty? (filter #(re-matches #"(?i)check box \d+" %) names))
              (str variant "-spells still has an anonymous checkbox")))))))

(deftest prune-orphan-widgets-loses-nothing
  (testing "Pruning drops only widgets belonging to no page, so no value can be
            lost. Uses a document made dirty on purpose, since the shipped
            templates are prepared."
    (with-open [doc (dirty-doc)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (.setValue (.getField form "on-a-page") "keep me")
        (let [removed (pdf/prune-orphan-widgets! doc)
              names (set (map #(.getFullyQualifiedName %) (all-fields form)))]
          (is (pos? removed) "the orphan's widget was removed")
          (is (not (contains? names "orphan")) "and the field it left empty went too")
          (is (contains? names "on-a-page") "a field on a page survives")
          (is (= "keep me" (str (.getValueAsString (.getField form "on-a-page"))))
              "with its value untouched"))))))

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

(deftest documented-line-capacities-still-hold
  (testing "Capacity is counted in lines, not characters: a newline costs a full
            line whatever is on it. Pins the per-box line count at
            pdf/min-font-size so re-cutting a template cannot change it quietly."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            ;; one short word per line measures line capacity directly
            lines-of (fn [n] (str/join "\n" (repeat n "x")))
            capacity (fn [field-name]
                       (let [box (pdf/widget-box doc (.getField form field-name))]
                         (loop [lo 1 hi 400]
                           (if (>= (inc lo) hi)
                             lo
                             (let [mid (quot (+ lo hi) 2)]
                               (if (nil? (:tail (apply pdf/fit-text (lines-of mid) box)))
                                 (recur mid hi)
                                 (recur lo mid)))))))]
        (doseq [[field-name expected] {"ideals" 3
                                       "bonds" 3
                                       "flaws" 3
                                       "personality-traits" 5
                                       "attacks-and-spellcasting" 12
                                       "other-profs" 14
                                       "backstory" 41
                                       "features-and-traits-2" 85}]
          (is (= expected (capacity field-name))
              (str field-name " holds " (capacity field-name) " lines at "
                   pdf/min-font-size "pt, expected " expected)))))))

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

(deftest generated-spell-pages-carry-classes-past-the-sixth
  (testing "The templates provide six spellcasting sections. pdf_spec emits one per
            class with no limit, so a seventh or eighth class had nowhere to go and
            write-fields! dropped it -- 50 values on an eight-class character."
    (with-open [doc (style-1-template)]
      (let [fields (into {:character-name "Eight Classes"}
                         (for [n (range 1 9)
                               [k v] [[(str "spellcasting-class-" n) (str "Class " n)]
                                      [(str "spells-1-1-" n) (str "Spell of class " n)]]]
                           [(keyword k) v]))
            before (.getNumberOfPages doc)
            added (pdf/add-missing-spell-pages! doc fields)
            dropped (pdf/write-fields! doc fields false {})]
        ;; This template carries one spell page, so a character with eight
        ;; casting classes needs seven more.
        (is (= 7 added) "a page for every class the template lacks")
        (is (= (+ before 7) (.getNumberOfPages doc)))
        (is (empty? dropped) "nothing is dropped once the pages exist")
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (doseq [n [7 8]]
            (is (= (str "Class " n)
                   (str (.getValueAsString (.getField form (str "spellcasting-class-" n)))))
                (str "class " n " has its own field carrying its own value"))))))))

(deftest generated-pages-introduce-no-duplicate-names
  (testing "Fields sharing a name are one field with one value, so a copied page
            whose fields keep their original names would mirror the page it came
            from. The stock templates already ship duplicate 'Check Box N' names;
            this pins that generation adds none of its own."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            names-before (frequencies (map #(.getFullyQualifiedName %) (all-fields form)))
            _ (pdf/add-missing-spell-pages!
               doc {:spellcasting-class-7 "Seven" :spellcasting-class-8 "Eight"})
            names-after (frequencies (map #(.getFullyQualifiedName %) (all-fields form)))
            worsened (for [[nm n] names-after
                           :let [was (get names-before nm 0)]
                           :when (and (> n 1) (> n was))]
                       nm)]
        (is (empty? worsened)
            (str "generation must not create or worsen a duplicate name: "
                 (vec (take 5 worsened))))))))

(deftest generated-pages-do-not-share-appearance-streams
  (testing "An appearance stream is a shared COS object. Copying /AP onto a
            generated widget makes it and its source render from the same baked
            visual, so writing one class's values rewrites the other's page --
            a Wizard page printed under the Sorcerer heading."
    (with-open [doc (style-1-template)]
      (let [fields {:spellcasting-class-1 "Source Class"
                    :spellcasting-class-2 "Copied Class"
                    :spells-1-1-1 "Source Spell"
                    :spells-1-1-2 "Copied Spell"}]
        (pdf/add-missing-spell-pages! doc fields)
        (pdf/write-fields! doc fields false {})
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              stream-of (fn [nm]
                          (some-> (.getField form nm) .getWidgets first
                                  .getAppearance .getNormalAppearance
                                  .getAppearanceStream .getCOSObject
                                  System/identityHashCode))]
          (is (not= (stream-of "spells-1-1-1") (stream-of "spells-1-1-2"))
              "each page draws from its own appearance stream")
          (is (= "Source Spell" (str (.getValueAsString (.getField form "spells-1-1-1")))))
          (is (= "Copied Spell" (str (.getValueAsString (.getField form "spells-1-1-2"))))
              "values stay on their own page"))))))

(deftest duplicate-field-names-are-made-unique
  (testing "Fields sharing a fully-qualified name are one field with one value, and
            getField can reach only one of them."
    (with-open [doc (dirty-doc)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            duplicate-count #(->> (all-fields form)
                                  (map (fn [f] (.getFullyQualifiedName f)))
                                  frequencies
                                  (filter (fn [[_ n]] (> n 1)))
                                  count)]
        (is (= 1 (duplicate-count)) "the fixture has one duplicated name")
        (is (pos? (pdf/disambiguate-duplicate-fields! doc)))
        (is (zero? (duplicate-count)) "every field ends up with its own name")
        (is (some? (.getField form "shared"))
            "the first of the group keeps the original name")
        (is (some? (.getField form "shared-2"))
            "and the second becomes addressable in its own right")))))

(deftest disambiguation-leaves-exported-names-alone
  (testing "The first field of a group keeps the original name, and pdf_spec writes
            to none of the duplicated names, so the export path is unaffected."
    (with-open [doc (style-1-template)]
      (let [values {:character-name "Named Field"
                    :spells-1-1-1 "Magic Missile"
                    :features-and-traits-2 "Traits text"}]
        (pdf/write-fields! doc values false {})
        (let [form (.getAcroForm (.getDocumentCatalog doc))]
          (doseq [[k v] values]
            (let [field (.getField form (name k))]
              (is (some? field) (str (name k) " still resolves by name"))
              (is (= v (str (.getValueAsString field)))
                  (str (name k) " kept its value")))))))))

(deftest checkboxes-get-names-that-say-what-they-are
  (testing "The templates name every checkbox 'Check Box N', which says nothing and
            collides across pages. Each spell row's tick is renamed after the row it
            sits beside, and the death-save block after its two labelled rows."
    (with-open [doc (six-caster-template)]
      (pdf/write-fields! doc {:character-name "Named Boxes"} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            names (map #(.getFullyQualifiedName %) (all-fields form))]
        (is (empty? (filter #(re-matches #"(?i)check box \d+" %) names))
            "no anonymous checkbox survives")
        (is (some #{"prepared-1-1-1"} names)
            "a spell row's tick is named after that row")
        (is (= #{"death-save-success-1" "death-save-success-2" "death-save-success-3"
                 "death-save-failure-1" "death-save-failure-2" "death-save-failure-3"}
               (set (filter #(str/starts-with? % "death-save-") names))))
        (testing "and each is independently settable"
          (.setValue (.getField form "death-save-success-1") "Yes")
          (is (= "Yes" (str (.getValueAsString (.getField form "death-save-success-1")))))
          (is (= "Off" (str (.getValueAsString (.getField form "death-save-failure-1"))))
              "ticking a success does not tick a failure"))))))

(defn- baked-size
  "Point size in a field's generated appearance stream."
  [form field-name]
  (let [widget (first (.getWidgets (.getField form field-name)))
        stream (.getAppearanceStream (.getNormalAppearance (.getAppearance widget)))]
    (with-open [in (.createInputStream (.getCOSObject stream))]
      (let [text (String. (.readAllBytes in) "ISO-8859-1")]
        (Double/parseDouble (second (re-find #"/\S+ ([\d.]+) Tf" text)))))))

(deftest wide-values-shrink-instead-of-clipping
  (testing "PDFBox sizes a single-line field by height alone, so the skill and save
            boxes settle on 8pt whatever they hold. They are 14.4pt wide with a
            12.4pt clip and '+11' is 13.6pt at 8pt, so every modifier of +10 or
            worse lost its last character."
    (with-open [doc (style-1-template)]
      (pdf/write-fields! doc {:int-save "+7" :wis-save "+11" :cha-save "+100"} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            fits? (fn [nm value]
                    (let [size (baked-size form nm)
                          widget (first (.getWidgets (.getField form nm)))
                          available (- (.getWidth (.getRectangle widget)) 2.0)]
                      (<= (* 72.0 (pdf/string-width value pdf/HELVETICA size)) available)))]
        (is (= 8.0 (baked-size form "int-save"))
            "a value that already fits is left at the size PDFBox chose")
        (is (< (baked-size form "wis-save") 8.0) "a wide value is shrunk")
        (is (fits? "wis-save" "+11") "and now fits its box")
        (is (fits? "cha-save" "+100") "as does a three-digit modifier")))))

(deftest multiline-fields-are-not-shrunk
  (testing "Multiline fields already scale to fit, and fit-text spills them before
            they get too small, so the single-line shrink must leave them alone."
    (with-open [doc (style-1-template)]
      (pdf/write-fields! doc {:backstory (str/join " " (repeat 40 "sentence"))} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (is (.isMultiline (.getField form "backstory")))
        (is (>= (baked-size form "backstory") 6.0)
            "left to its own auto-sizing rather than forced smaller")))))

(deftest a-spell-level-box-can-be-relabelled
  (testing "A level's rows live in a box whose number is printed artwork, so the
            box is bound to that level by the page. A field with a background fill
            covers the numeral, which is what would let a spare box take a level
            whose own box is full -- a level 5 cleric spills 13 spells while 59
            rows sit empty in the levels it cannot reach."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (testing "the numeral box is derived from the slots box, not hardcoded"
          (doseq [level (range 1 10)]
            (let [[x y w h] (pdf/spell-level-numeral-box doc level 1)
                  slots (.getRectangle (first (.getWidgets
                                               (.getField form (str "spell-slots-" level "-1")))))]
              (is (< x (.getLowerLeftX slots))
                  (str "level " level "'s numeral sits left of its slots box"))
              (is (and (< 8 w 14) (< 12 h 20))
                  (str "level " level "'s patch covers the numeral, not the hexagon")))))

        (testing "relabelling adds an addressable field carrying the new number"
          (let [field (pdf/relabel-spell-level! doc 3 1 "1")]
            (is (some? field))
            (is (= "1" (str (.getValueAsString
                             (.getField form "spell-level-label-3-1")))))))

        (testing "a level with no box on this template is left alone"
          (is (nil? (pdf/spell-level-numeral-box doc 12 1))))))))
