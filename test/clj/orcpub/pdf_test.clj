(ns orcpub.pdf-test
  ;; explicit :refer to avoid namespace pollution from :refer :all
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [orcpub.pdf :as pdf]
            [orcpub.dnd.e5.spell-packing :as packing])
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
  (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-2-1-spells.pdf"))]
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
  "Style 1 grown to its widest, six spellcasting sections. Built rather than
   loaded: only the masters in pdf/sheet-masters ship, and this is what the
   export makes of style 1 for a character with six casting classes."
  []
  (let [doc (with-open [in (.openStream (io/resource "fillable-char-sheetstyle-1-1-spells.pdf"))]
              (Loader/loadPDF (.readAllBytes in)))]
    (pdf/grow-spell-sections! doc 6 :all)
    doc))

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
    (doseq [{:keys [file without-casters]} (vals pdf/sheet-masters)
            variant (remove nil? [file without-casters])]
      (with-open [in (.openStream (io/resource variant))
                  doc (Loader/loadPDF (.readAllBytes in))]
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              names (map #(.getFullyQualifiedName %) (all-fields form))]
          (is (empty? (for [f (all-fields form)
                            w (.getWidgets f)
                            :when (nil? (.getPage w))]
                        f))
              (str variant " still has a widget belonging to no page"))
          (is (empty? (->> names frequencies (filter (fn [[_ n]] (> n 1))) (map key)))
              (str variant " still has duplicate field names"))
          ;; Only style 1 is asserted here. The naming pass pairs a checkbox with
          ;; the spell row beside it using style 1's geometry, so the other
          ;; styles keep a few "Check Box N" names; dev/prepare_templates.clj
          ;; reports the count rather than treating it as a failure. Duplicates
          ;; and orphans above are required of every master, an anonymous name is
          ;; only unhelpful.
          (when (re-find #"style-1-" variant)
            (is (empty? (filter #(re-matches #"(?i)check box \d+" %) names))
                (str variant " still has an anonymous checkbox"))))))))

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
            style 4 shipped with no `backstory` field and how a character with
            more than six spellcasting classes loses two of them with no error.

            spells-3-14-1 is the example because it is the name style 1's level 3
            box used to end on: thirteen fields numbered 1-10, 12, 13, 14, so
            spells-3-11 fell in the gap and 14 was never written. The box is
            numbered 1..13 now, which leaves 14 genuinely absent."
    (with-open [doc (style-1-template)]
      (let [dropped (pdf/write-fields! doc {:character-name "Real Field"
                                            :spells-3-14-1 "past the last row"
                                            :spellcasting-class-7 "past the ceiling"}
                                       false {})]
        (is (= ["spellcasting-class-7" "spells-3-14-1"] dropped)
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
            box is bound to that level by the page. Covering the numeral is what
            would let a spare box take a level whose own box is full -- a level 5
            cleric spills 13 spells while 59 rows sit empty in levels it cannot
            reach."
    (with-open [doc (style-1-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (testing "the hexagon is located from the slots box, not hardcoded"
          (doseq [level (range 1 10)]
            (let [[x y w h] (pdf/spell-level-numeral-box doc level 1)
                  slots (.getRectangle (first (.getWidgets
                                               (.getField form (str "spell-slots-" level "-1")))))]
              (is (< x (.getLowerLeftX slots))
                  (str "level " level "'s hexagon sits left of its slots box"))
              (is (< (+ x w) (+ (.getLowerLeftX slots) 2))
                  (str "level " level "'s hexagon abuts the slots box"))
              (is (and (< 15 w 24) (< 32 h 42))
                  (str "level " level "'s hexagon is about 19 x 37 points")))))

        (testing "relabelling adds an addressable field carrying the new number"
          (let [field (pdf/relabel-spell-level! doc 3 1 "1")]
            (is (some? field))
            (is (= "1" (str (.getValueAsString
                             (.getField form "spell-level-label-3-1")))))))

        (testing "the patch is drawn as the hexagon's own shape, not a rectangle"
          (let [widget (first (.getWidgets (.getField form "spell-level-label-3-1")))
                stream (.getAppearanceStream (.getNormalAppearance (.getAppearance widget)))
                ops (with-open [in (.createInputStream (.getCOSObject stream))]
                      (String. (.readAllBytes in) "ISO-8859-1"))]
            (is (= 5 (count (re-seq #"\bl\n" ops)))
                "five lineto operators close a six-cornered path")
            (is (str/includes? ops " f\n") "and the path is filled")
            (is (not (str/includes? ops " re\n"))
                "no rectangle is drawn -- a square patch would cut through the
                 printed outline and the grey bevel around the numeral")))

        (testing "a level with no box on this template is left alone"
          (is (nil? (pdf/spell-level-numeral-box doc 12 1))))))))

(deftest the-cantrips-box-can-take-a-spell-level
  (testing "Cantrips only print once, so on a continuation page the cantrips box
            is eight dead rows. Reusing it needs more than a numeral: it has no
            spell-slots field to locate it by, its bar reads CANTRIPS, and it has
            no slot labels because cantrips do not use slots."
    (with-open [doc (style-1-template)]
      (let [added (pdf/reuse-cantrips-box! doc 1 "1")
            form (.getAcroForm (.getDocumentCatalog doc))]
        (is (= 5 (count added))
            "a renumbered hexagon, a patched bar, the slot labels, and two inputs")
        (is (some? (.getField form "spell-level-label-0-1")))
        (is (some? (.getField form "cantrips-bar-patch-1")))
        (is (some? (.getField form "cantrips-slot-labels-1")))

        (testing "the bar gets the two inputs its labels name, either side of a
                  divider drawn where a level bar has one"
          (let [total (.getField form "cantrips-slots-total-1")
                expended (.getField form "cantrips-slots-expended-1")
                rect-of #(.getRectangle (first (.getWidgets %)))
                patch (rect-of (.getField form "cantrips-bar-patch-1"))
                ops (with-open [in (.createInputStream
                                    (.getCOSObject
                                     (.getAppearanceStream
                                      (.getNormalAppearance
                                       (.getAppearance (first (.getWidgets (.getField form "cantrips-bar-patch-1")))))))) ]
                      (String. (.readAllBytes in) "ISO-8859-1"))]
            (is (and total expended) "both inputs exist")
            (is (not (.isReadOnly total)) "and the player can type in them")
            (is (not (.isReadOnly expended)))
            (is (< (+ (.getLowerLeftX (rect-of total)) (.getWidth (rect-of total)))
                   97.5
                   (.getLowerLeftX (rect-of expended)))
                "the divider at x 97.5 falls in the gap between them")
            ;; the patch covers the cantrips bar's own divider at x 51-59 and the
            ;; printed word at 112-142, and must stay inside the bar's rules
            (is (< (.getLowerLeftX patch) 51.0))
            (is (> (+ (.getLowerLeftX patch) (.getWidth patch)) 142.0))
            ;; the bar's inner rules occupy y 625.5-625.9 and 645.4-645.9, so the
            ;; patch has to fill exactly between them: short of either and the
            ;; old divider's sloped ends are left behind as stubs, over either
            ;; and the rule itself is painted out
            ;; rects are floats, so the bounds land a ten-thousandth out; the
            ;; rules are 0.4pt thick, well clear of that
            (let [slack 0.01]
              (is (and (>= (.getLowerLeftY patch) (- 625.9 slack))
                       (<= (+ (.getLowerLeftY patch) (.getHeight patch))
                           (+ 645.4 slack)))
                  "between the bar's inner rules, covering all of the gap"))
            (is (str/includes? ops " l\nS") "the divider is stroked")))

        (testing "the labels land on the printed line, raised to the cantrips box"
          (let [widget (first (.getWidgets (.getField form "cantrips-slot-labels-1")))
                stream (.getAppearanceStream (.getNormalAppearance (.getAppearance widget)))
                ops (with-open [in (.createInputStream (.getCOSObject stream))]
                      (String. (.readAllBytes in) "ISO-8859-1"))
                rect (.getRectangle widget)
                ;; the appearance draws in its own space, so page position is the
                ;; widget's lower-left corner plus the Td offset
                page-x (fn [n] (+ (.getLowerLeftX rect)
                                  (Double/parseDouble (nth (re-find (re-pattern (str "([\\d.]+) ([\\d.]+) Td\\n\\(" n "\\)")) ops) 1))))
                page-y (+ (.getLowerLeftY rect)
                          (Double/parseDouble (second (re-find #"[\d.]+ ([\d.]+) Td" ops))))
                size (Double/parseDouble (second (re-find #"/Helv ([\d.]+) Tf" ops)))
                close? (fn [a b] (< (Math/abs (- a b)) 0.05))]
            (is (str/includes? ops "(SLOTS TOTAL)"))
            (is (str/includes? ops "(SLOTS EXPENDED)"))
            (is (not (str/includes? ops "re f"))
                "no fill: the labels sit on blank page above the bar, like the
                 printed ones above level 1, not over printed art")
            ;; measured off the artwork: the page prints this line once, above
            ;; level 1, at x 50.83 and 127.71 on baseline 483.17 at 5pt, and the
            ;; cantrips box is 167.73pt higher
            (is (close? size 5.0) "same size as the printed labels")
            (is (close? (page-x "SLOTS TOTAL") 50.83))
            (is (close? (page-x "SLOTS EXPENDED") 127.71))
            (is (close? page-y (+ 483.17 167.73)))
            ;; text running past the BBox is clipped, not overflowed, which is how
            ;; the first attempt lost the last letter of SLOTS EXPENDED.
            ;; getStringWidth is in thousandths of an em, so size x raw/1000 is
            ;; already points -- no further conversion
            (let [longest (* size (/ (.getStringWidth pdf/HELVETICA "SLOTS EXPENDED") 1000.0))]
              (is (<= (+ (- (page-x "SLOTS EXPENDED") (.getLowerLeftX rect)) longest)
                      (.getWidth rect))
                  "the rightmost label ends inside the patch"))))

        (testing "the hexagon is level 1's, raised to the cantrips box"
          (let [hexagon (.getRectangle (first (.getWidgets (.getField form "spell-level-label-0-1"))))
                level-1 (pdf/spell-level-numeral-box doc 1 "1")]
            (is (< (Math/abs (- (.getLowerLeftX hexagon) (first level-1))) 0.05)
                "same column as level 1")
            (is (< (Math/abs (- (.getLowerLeftY hexagon) (+ (second level-1) 167.73))) 0.05))))

        (testing "it sits over the cantrips rows, not another box"
          (let [widget (first (.getWidgets (.getField form "spell-level-label-0-1")))
                row (first (.getWidgets (.getField form "spells-0-1-1")))]
            (is (> (.getLowerLeftY (.getRectangle widget))
                   (.getLowerLeftY (.getRectangle row)))
                "the hexagon is above the first cantrip row")))))))

(defn- drawn-text
  "The strings a field's generated appearance actually shows."
  [form field-name]
  (let [widget (first (.getWidgets (.getField form field-name)))
        stream (.getAppearanceStream (.getNormalAppearance (.getAppearance widget)))]
    (with-open [in (.createInputStream (.getCOSObject stream))]
      (map second (re-seq #"\(([^)]*)\)\s*Tj" (String. (.readAllBytes in) "ISO-8859-1"))))))

(deftest slot-totals-and-expended-reach-the-page
  (testing "SLOTS TOTAL is spell-slots-<level>-<class> and SLOTS EXPENDED is a
            free-text field beside it. Both are written by the exporter, so both
            must survive to the appearance stream, not merely be stored."
    (with-open [doc (style-1-template)]
      (pdf/write-fields! doc {:spell-slots-1-1 "4"
                              :slots-expended-1-1 "2"
                              :spell-slots-2-1 "3"} false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))]
        (is (= "4" (str (.getValueAsString (.getField form "spell-slots-1-1")))))
        (is (some #{"4"} (drawn-text form "spell-slots-1-1"))
            "the slot total is drawn, not just stored")
        (is (some #{"2"} (drawn-text form "slots-expended-1-1"))
            "the expended blank beside it is drawn too")
        (is (= "3" (str (.getValueAsString (.getField form "spell-slots-2-1"))))
            "and each level keeps its own total")))))

(deftest checkboxes-tick-independently
  (testing "Every checkbox was named 'Check Box N' and many shared a name, so
            ticking one ticked its twins. After naming they must be independent."
    (with-open [doc (six-caster-template)]
      (pdf/write-fields! doc {:prepared-1-1-1 true
                              :prepared-1-2-1 false
                              :prepared-1-1-2 false
                              :death-save-success-1 true
                              :death-save-failure-1 false}
                         false {})
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            value (fn [nm] (str (.getValueAsString (.getField form nm))))]
        (is (= "Yes" (value "prepared-1-1-1")) "the box asked for is ticked")
        (is (= "Off" (value "prepared-1-2-1")) "its neighbour on the same page is not")
        (is (= "Off" (value "prepared-1-1-2"))
            "and neither is the same slot on another class's page")
        (is (= "Yes" (value "death-save-success-1")))
        (is (= "Off" (value "death-save-failure-1"))
            "a success does not tick a failure")))))

(deftest nothing-mirrors-by-accident
  (testing "Fields sharing a name share one value, which is right only when it is
            meant. Writes a distinct value to every text field on a spell page and
            reads them all back: anything that mirrors shows up as a field holding
            a value meant for another."
    (with-open [doc (six-caster-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            targets (->> (all-fields form)
                         (filter #(instance? PDTextField %))
                         (map #(.getFullyQualifiedName %))
                         (filter #(re-find #"^spells-\d+-\d+-\d+$" %))
                         sort
                         (take 60))
            written (into {} (map-indexed (fn [i nm] [nm (str "unique-" i)]) targets))]
        (pdf/write-fields! doc (into {} (map (fn [[k v]] [(keyword k) v]) written)) false {})
        (let [wrong (for [[nm expected] written
                          :let [actual (str (.getValueAsString (.getField form nm)))]
                          :when (not= expected actual)]
                      [nm expected actual])]
          (is (empty? wrong)
              (str "fields holding a value written to a different field: "
                   (vec (take 5 wrong)))))
        (testing "and every value landed somewhere exactly once"
          (let [values (->> (all-fields form)
                            (filter #(instance? PDTextField %))
                            (map #(str (.getValueAsString %)))
                            (filter #(str/starts-with? % "unique-")))]
            (is (= (count values) (count (distinct values)))
                "a repeated value means two fields are showing the same thing")))))))

(deftest no-baked-template-has-a-field-on-two-pages
  (testing "A field is one value however many widgets show it, so a widget on two
            spell pages ticks or fills both at once. dev/prepare_templates.clj
            splits those, and this asserts the committed resources came out of it
            -- a template re-cut or replaced without a re-bake fails here rather
            than shipping a sheet where one class mirrors another."
    (doseq [style (range 1 5)
            spells (range 0 7)
            :let [resource (io/resource (str "fillable-char-sheetstyle-"
                                             style "-" spells "-spells.pdf"))]
            :when resource]
      (with-open [doc (Loader/loadPDF (.readAllBytes (.openStream resource)))]
        (let [form (.getAcroForm (.getDocumentCatalog doc))
              pages (into {} (map-indexed (fn [i p] [p (inc i)]) (.getPages doc)))
              spanning (for [field (iterator-seq (.iterator (.getFieldTree form)))
                             :let [on (into #{} (keep #(pages (.getPage %))
                                                      (.getWidgets field)))]
                             :when (> (count on) 1)]
                         (str (.getFullyQualifiedName field) " on pages "
                              (str/join "," (sort on))))]
          (is (empty? spanning)
              (str "style " style " with " spells " spell page(s): "
                   (str/join "; " (take 3 spanning)))))))))

(deftest generated-spell-pages-stay-with-the-other-spell-pages
  (testing "Styles 1 and 2 carry a features and traits page after their spell
            pages. add-spell-page! appended, so a character with more casting
            classes than the template holds got that page wedged between its
            spell pages -- the eight-class fixture had spell pages 3-8, features
            at 9, then spell pages 10 and 11."
    (with-open [doc (six-caster-template)]
      (let [form (.getAcroForm (.getDocumentCatalog doc))
            page-no (fn [field]
                      (let [pages (into {} (map-indexed (fn [i p] [p (inc i)]) (.getPages doc)))]
                        (some->> (.getWidgets field) (keep #(pages (.getPage %))) first)))
            pages-of (fn [pattern]
                       (into (sorted-set)
                             (for [f (iterator-seq (.iterator (.getFieldTree form)))
                                   :when (re-matches pattern (.getFullyQualifiedName f))
                                   :let [n (page-no f)] :when n]
                               n)))
            ;; the unsuffixed features-and-traits is the equipment list on the
            ;; character page; -2 is the features page itself
            before (pages-of #"features-and-traits-2")]
        (is (seq before) "the template has a features page to be displaced")
        (pdf/add-spell-page! doc "1" "9")
        (let [spells (pages-of #"spells-\d+-\d+-\d+")
              features (pages-of #"features-and-traits-2")]
          (is (= (inc (apply min before)) (apply min features))
              "inserting one page ahead of it moves it down exactly one")
          (is (< (apply max spells) (apply min features))
              (str "every spell page comes before the features page; spells were "
                   (pr-str spells) " and features " (pr-str features)))
          (is (= (.getNumberOfPages doc) (apply min features))
              "and it is still the last page"))))))

;; ─── Growing every master ────────────────────────────────────────────────────

(defn- spell-page-indexes
  "Page index of each spellcasting section, section order."
  [doc]
  (let [pages (vec (.getPages doc))]
    (mapv (fn [[_ page]] (.indexOf pages page)) (#'pdf/spell-sections doc))))

(defn- has-text-block?
  "Whether the page draws text of its own -- how a style's attribution footer
   shows up, since it is an appended BT/ET block in the content stream."
  [page]
  (let [b (java.io.ByteArrayOutputStream.)]
    (with-open [in (.getContents page)] (.transferTo in b))
    (str/includes? (String. (.toByteArray b) "ISO-8859-1") "BT")))

(deftest every-master-grows-to-any-caster-count
  (testing "REGRESSION: every style grows to N spellcasting sections, numbered 1..N
            in page order, with the attribution footer on each.

            Styles 3 and 4 keep their spell page LAST, so add-spell-pages! has no
            page to insert before and used to fall back to PDPageTree.add. That
            walks the whole object graph checking for a cycle and overflows the
            stack on these masters, so both styles threw StackOverflowError at two
            or more casting classes while styles 1 and 2 passed."
    (doseq [style [1 2 3 4]
            casters [1 2 3 6]]
      (let [{:keys [file marks]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (pdf/grow-spell-sections! doc casters marks)
          (let [pages (vec (.getPages doc))
                indexes (spell-page-indexes doc)
                label (str "style " style ", " casters " caster(s)")]
            (is (= casters (count indexes))
                (str label ": one section per casting class"))
            (is (= indexes (sort indexes))
                (str label ": sections run in page order"))
            (is (apply distinct? -1 indexes)
                (str label ": no two sections share a page"))
            (is (every? #(has-text-block? (nth pages %)) indexes)
                (str label ": every spell page carries the attribution footer"))))))))

;; ─── The site line on sheets ─────────────────────────────────────────────────

(defn- page-text [doc index]
  (let [stripper (doto (org.apache.pdfbox.text.PDFTextStripper.)
                   (.setStartPage (inc index))
                   (.setEndPage (inc index)))]
    (str/replace (.getText stripper doc) #"\s+" " ")))

(deftest site-line-once-on-every-sheet-page
  (testing "every page of every style carries the site line exactly once.

            Style 4's artwork prints its own on its spell pages, so those must be
            left alone rather than stamped a second time in the same corner; the
            pages it does NOT print it on still get one."
    (doseq [style [1 2 3 4]
            casters [0 1 3]]
      (let [{:keys [file marks without-casters site-line prints-site-line?]}
            (get pdf/sheet-masters style)
            source (if (zero? casters) without-casters file)]
        (with-open [in (.openStream (io/resource source))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (pdf/grow-spell-sections! doc casters (if (zero? casters) :all marks))
          (pdf/stamp-site-line! doc site-line (boolean prints-site-line?))
          (doseq [i (range (.getNumberOfPages doc))]
            (is (= 1 (count (re-seq (re-pattern pdf/site-stamp) (page-text doc i))))
                (str "style " style ", " casters " caster(s), page " (inc i)))))))))

(defn- ink-free?
  "Whether the stamp-sized box at `x`,`y` inches from the page's bottom-left is
   free of ink on `index`, rendered at 150 dpi."
  [doc index x y]
  ;; 300, not 150: at 150 the bar under the foot of styles 1 and 2's last page
  ;; reads as white and the check passes on a page that visibly overprints.
  (let [dpi 300.0
        img (.renderImageWithDPI (org.apache.pdfbox.rendering.PDFRenderer. doc)
                                 index (float dpi))
        w (pdf/string-width pdf/site-stamp pdf/HELVETICA 6)
        h (/ 8.0 72)
        height (.getHeight img)
        x0 (int (* x dpi))
        x1 (int (* (+ x w) dpi))
        ;; Inches up from the page foot, into rows down from the image top.
        y-top (int (- height (* (+ y h) dpi)))
        y-bottom (int (- height (* y dpi)))]
    (every? (fn [py]
              (every? (fn [px]
                        (let [p (.getRGB img px py)]
                          (and (> (bit-and (bit-shift-right p 16) 0xff) 244)
                               (> (bit-and (bit-shift-right p 8) 0xff) 244)
                               (> (bit-and p 0xff) 244))))
                      (range x0 x1)))
            (range y-top y-bottom))))

(deftest site-line-lands-on-blank-paper
  ;; REGRESSION: the positions were first picked from where each page's lowest
  ;; TEXT sat, which is all PDFTextStripper reports -- page artwork is invisible
  ;; to it. Every style shared one spot and the line printed through the corner
  ;; flourish on the last page of styles 1 and 2 and through the frame on style
  ;; 4. Ink is what matters, so this renders the page and counts pixels.
  (testing "every page the stamp goes on is blank where it goes"
    (doseq [style [1 2 3 4]
            :let [{:keys [file without-casters marks site-line prints-site-line?]}
                  (get pdf/sheet-masters style)
                  [x y] site-line]
            [source casters] [[file 3] [without-casters 0]]]
      (with-open [in (.openStream (io/resource source))
                  doc (Loader/loadPDF (.readAllBytes in))]
        (pdf/grow-spell-sections! doc casters (if (zero? casters) :all marks))
        (doseq [i (range (.getNumberOfPages doc))
                ;; A page printing its own footer is skipped at export, and its
                ;; own line is the ink that would fail this.
                :when (not (and prints-site-line?
                                (#'pdf/page-prints-site-line? doc i)))]
          (is (ink-free? doc i x y)
              (str "style " style ", " casters " caster(s), page " (inc i)
                   ": artwork at " (pr-str site-line))))))))

;; ─── Spell row capacity ──────────────────────────────────────────────────────

(defn- field-names [doc]
  (map #(str (.getFullyQualifiedName %))
       (iterator-seq (.iterator (.getFieldTree (.getAcroForm (.getDocumentCatalog doc)))))))

(defn- indexed-families
  "Row numbers of every NAME-LEVEL-ROW-1 field, keyed by [family level].

   Covers spells, the prepared ticks beside them and slots-expended together:
   they are numbered by the same convention and were broken by the same mistake."
  [doc]
  (->> (for [n (field-names doc)
             :let [m (re-matches #"([a-z-]+)-(\d+)-(\d+)-1" n)]
             :when m]
         [[(nth m 1) (Integer/parseInt (nth m 2))] (Integer/parseInt (nth m 3))])
       (group-by first)
       (reduce-kv (fn [acc k pairs] (assoc acc k (sort (map second pairs)))) {})))

(defn- spell-row-fields
  "spells-LEVEL-ROW-1 field numbers present in `doc`, grouped by level."
  [doc]
  (reduce-kv (fn [acc [family level] rows]
               (if (= family "spells") (assoc acc level rows) acc))
             {}
             (indexed-families doc)))

(deftest indexed-rows-are-numbered-without-gaps
  ;; REGRESSION: pdf_spec emits NAME-LEVEL-ROW-1 counting from 1 with no gaps, so
  ;; a template whose field numbers skip one drops the value at the gap and leaves
  ;; its highest row blank. Styles 1 and 3 numbered level 3 as 1-10, 12, 13, 14 --
  ;; thirteen fields for thirteen printed rows, but Glyph of Warding vanished off
  ;; every wizard's sheet. Style 1's PREPARED ticks beside those rows carried the
  ;; same numbering, so a prepared spell printed unticked. Style 4's level 2 ran
  ;; 1-6 then 9-13, losing two spells mid-list.
  (testing "every indexed family runs 1..n with nothing missing"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (doseq [[[family level] numbers] (sort (indexed-families doc))]
            (is (= (vec numbers) (vec (range 1 (inc (count numbers)))))
                (str "style " style " " family " level " level
                     ": fields are " (vec numbers)))))))))

(deftest no-two-fields-share-a-name
  ;; Two fields of one name are one field with one value, so the second row prints
  ;; blank however carefully it was placed. This is what a re-run of the migration
  ;; that renumbered those rows produced: the renames shift a run down by one, so
  ;; a second pass renamed the next field into the name just vacated.
  (testing "field names are unique within a master"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (let [dupes (->> (field-names doc) frequencies (filter #(> (val %) 1)) (into {}))]
            (is (empty? dupes) (str "style " style ": " (pr-str dupes)))))))))

(deftest sheet-geometry-matches-the-templates
  ;; The packer decides what fits from sheet-geometry. If it claims more rows than
  ;; the box has fields, it packs spells into a box that cannot print them.
  (testing "spell-packing/sheet-geometry is the templates' real capacity"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (let [actual (spell-row-fields doc)]
            (doseq [[level rows] (map-indexed vector (get packing/sheet-geometry style))]
              (is (= rows (count (get actual level)))
                  (str "style " style " level " level
                       ": geometry says " rows
                       ", template has " (count (get actual level)))))))))))

(def ^:private character-fields
  "One value per non-spell field the app writes to a sheet.

   A literal list rather than the template's own names, so it says what the EXPORT
   emits: reading the names off the template under test would make every template
   pass itself, which is the check that was missing."
  {:character-name "Ysolde Vantreaux" :character-name-2 "Ysolde Vantreaux"
   :class-level "Wizard 20" :background "Sage" :player-name "fixture"
   :race "High Elf" :alignment "Neutral Good" :xp "355,000"
   :str "-1" :str-mod "8" :dex "+2" :dex-mod "14" :con "+3" :con-mod "16"
   :int "+5" :int-mod "20" :wis "+1" :wis-mod "13" :cha "+0" :cha-mod "10"
   :str-save "-1" :dex-save "+2" :con-save "+3"
   :int-save "+11" :wis-save "+7" :cha-save "+0"
   :ac "15" :initiative "+2" :speed "30 ft." :hp-max "122" :hp-current "122"
   :hp-temp "0" :hd "20d6" :prof-bonus "+6" :passive "11" :inspiration "1"
   :arcana "+11" :history "+11" :investigation "+11" :insight "+7"
   :perception "+1" :medicine "+1" :nature "+5" :religion "+5"
   :age "241" :height "5'11\"" :weight "134 lb."
   :eyes "Pale grey" :skin "Fair" :hair "Silver, braided"
   :weapon-name-1 "Quarterstaff" :weapon-attack-bonus-1 "+5" :weapon-damage-1 "1d6+3"
   :gp "1,240" :pp "60" :cp "0" :sp "0" :ep "0"
   :personality-traits "Speaks to books as though they can hear her."
   :ideals "Knowledge withheld is knowledge wasted."
   :bonds "The Vantreaux archive burned."
   :flaws "Assumes she is the smartest person in the room."
   :backstory "Apprenticed at eleven to a conjurer."
   :allies "The Candlewrights; Archivist Bell."
   :other-profs "Common, Elvish, Draconic."
   :features-and-traits "Quarterstaff, dagger, spellbook."
   :features-and-traits-2 "Arcane Recovery. Evocation Savant."
   :treasure "Robe of the Archmagi"
   :attacks-and-spellcasting "Evocation save DC 19."
   :spellcasting-class-1 "Wizard" :spellcasting-ability-1 "Intelligence"
   :spell-save-dc-1 "19" :spell-attack-bonus-1 "+11"})

(defn- filled-to-capacity
  "Every value a full character puts on `style`: the character block, a slot row,
   and every spell row the style's geometry says it has."
  [style]
  (merge character-fields
         (into {} (for [level (range 1 10)]
                    [(keyword (str "spell-slots-" level "-1")) "4"]))
         (into {} (for [[level rows] (map-indexed vector (get packing/sheet-geometry style))
                        row (range 1 (inc rows))]
                    [(keyword (format "spells-%d-%d-1" level row))
                     (format "L%dR%d" level row)]))))

(deftest nothing-a-full-character-carries-is-silently-dropped
  ;; THE GUARD. write-fields! returns what it could not place, and until this test
  ;; nothing looked at it: styles 1 and 3 dropped a level 3 spell, style 4 dropped
  ;; two level 2 spells plus its second-page name, and style 3 printed an empty
  ;; HIT DICE box, on every export, in silence.
  ;;
  ;; Exact equality, not a subset. A new drop fails here, and so does a stale
  ;; entry in pdf/unsupported-fields once its template gains the field.
  (testing "a fully populated character loses only what the style cannot print"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (let [fields (pdf/merge-style-fields style (filled-to-capacity style))
                dropped (set (pdf/write-fields! doc fields false {}))
                declared (get pdf/unsupported-fields style #{})]
            (is (= declared dropped)
                (str "style " style
                     "\n  declared unsupported: " (pr-str (sort declared))
                     "\n  actually dropped:     " (pr-str (sort dropped))))))))))

(deftest merge-style-fields-folds-sections-into-one-box
  (testing "a style with no shared box passes its fields through untouched"
    (let [fields {:backstory "b" :allies "a" :character-name "n"}]
      (is (= fields (pdf/merge-style-fields 1 fields)))))

  (testing "style 4's backstory and allies become one headed Notes value"
    (let [out (pdf/merge-style-fields 4 {:backstory "Raised at sea."
                                         :allies "The Candlewrights."
                                         :character-name "Ysolde"})]
      (is (= "BACKSTORY\nRaised at sea.\n\nALLIES & ORGANIZATIONS\nThe Candlewrights."
             (:Notes out)))
      (is (= "Ysolde" (:character-name out)) "other fields are left alone")
      (is (not (contains? out :backstory)) "the sources are consumed")
      (is (not (contains? out :allies)))))

  (testing "sections keep their declared order whatever order the fields arrive in"
    (is (= (:Notes (pdf/merge-style-fields 4 {:allies "A" :backstory "B"}))
           (:Notes (pdf/merge-style-fields 4 {:backstory "B" :allies "A"})))))

  (testing "an empty section contributes no heading"
    (let [out (pdf/merge-style-fields 4 {:backstory "Only this." :allies "   "})]
      (is (= "BACKSTORY\nOnly this." (:Notes out))
          "a blank allies must not print a bare ALLIES heading")))

  (testing "nothing to merge leaves the box unwritten rather than blank-headed"
    (let [out (pdf/merge-style-fields 4 {:backstory "" :allies nil :character-name "n"})]
      (is (not (contains? out :Notes)))
      (is (= {:character-name "n"} out))))

  (testing "the merged value is what actually lands in the template"
    (let [{:keys [file]} (get pdf/sheet-masters 4)]
      (with-open [in (.openStream (io/resource file))
                  doc (Loader/loadPDF (.readAllBytes in))]
        (let [fields (pdf/merge-style-fields 4 {:backstory "Raised at sea."
                                                :allies "The Candlewrights."})
              dropped (pdf/write-fields! doc fields false {})
              form (.getAcroForm (.getDocumentCatalog doc))]
          (is (empty? dropped) "neither value is dropped once merged")
          (is (str/includes? (str (.getValueAsString (.getField form "Notes")))
                             "ALLIES & ORGANIZATIONS")))))))

;; ─── Annotation columns ──────────────────────────────────────────────────────

(deftest annotation-columns-are-reserved-before-values-are-written
  ;; The rows auto-size, so narrowing the box is what makes a long name shrink to
  ;; clear the columns instead of running under them. Doing it after write-fields!
  ;; would leave the baked appearance at its old width.
  (testing "every spell row loses exactly the annotation zone"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (let [widths (fn [] (for [n (field-names doc)
                                    :when (re-matches #"spells-\d+-\d+-1" n)
                                    :let [f (.getField (.getAcroForm (.getDocumentCatalog doc)) n)
                                          r (some-> (first (.getWidgets f)) .getRectangle)]
                                    :when r]
                                [n (.getWidth r)]))
                before (into {} (widths))]
            (pdf/reserve-annotation-columns! doc)
            (doseq [[n w] (widths)]
              (is (< (Math/abs (- (- (get before n) w) pdf/annotation-zone)) 0.01)
                  (str "style " style " " n)))))))))

(deftest a-filled-row-keeps-its-name-clear-of-the-columns
  ;; The columns only work if the name stops before them. A name that still
  ;; overflowed would print under the mark and undo the whole point of aligning.
  (testing "the longest real spell names fit the narrowed row on every style"
    (doseq [style [1 2 3 4]]
      (let [{:keys [file]} (get pdf/sheet-masters style)
            long-names ["Globe of Invulnerability" "Conjure Minor Elementals"
                        "Comprehend Languages" "Arcanist's Magic Aura"]]
        (with-open [in (.openStream (io/resource file))
                    doc (Loader/loadPDF (.readAllBytes in))]
          (pdf/reserve-annotation-columns! doc)
          (let [rows (into {} (for [[level rows] (spell-row-fields doc)
                                    row rows]
                                [(format "spells-%d-%d-1" level row)
                                 (nth long-names (mod row (count long-names)))]))]
            (pdf/write-fields! doc rows false {})
            (let [form (.getAcroForm (.getDocumentCatalog doc))]
              (doseq [[n v] (take 20 rows)
                      :let [f (.getField form n)
                            r (some-> (first (.getWidgets f)) .getRectangle)
                            ap (some-> (first (.getWidgets f)) .getAppearance
                                       .getNormalAppearance .getAppearanceStream)]
                      :when (and r ap)]
                (let [b (java.io.ByteArrayOutputStream.)
                      _ (with-open [s (.createInputStream (.getCOSObject ap))]
                          (.transferTo s b))
                      size (some-> (re-find #"/\S+\s+([0-9.]+)\s+Tf" (String. (.toByteArray b)))
                                   second Double/parseDouble)]
                  (when size
                    (is (<= (* 72 (pdf/string-width v pdf/HELVETICA size))
                            (.getWidth r))
                        (str "style " style " " n " \"" v "\" at " size "pt"))))))))))))
