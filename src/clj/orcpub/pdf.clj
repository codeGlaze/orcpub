(ns orcpub.pdf
  "Fills the AcroForm character sheet templates in resources/, and draws spell
   cards and monster stat blocks.

   Template behaviour that affects callers:

   - Text fields auto-size. Their default appearance is `/Helv 0 Tf`, so PDFBox
     scales text down to fit, stops at 4pt, and clips beyond that. `fit-text`
     splits text at `min-font-size` instead.
   - Widgets with no page came with the templates, and `dev/prepare_templates.clj`
     has already removed them from everything in `resources/`. Nothing prunes at
     export time except `add-missing-spell-pages!`, and only on the branch where
     it is about to generate pages, in case it is handed an unbaked template.
     `widget-box` ignores pageless widgets when measuring.
   - Fields sharing a name share one value, so repeated pages need unique names.
   - Field names do not describe their contents:

       features-and-traits     equipped item list
       features-and-traits-2   features, actions, reactions
       treasure                unequipped items and valuables
       equipment               unused; pdf_spec emits no such key
       cha                     ability modifier
       cha-mod                 ability score

     pdf_spec/equipment-fields and pdf_spec/traits-fields build these maps.

   PDFBox 3.x: fonts are constructed rather than static fields, `Loader/loadPDF`
   replaces `PDDocument/load`, and `PDPageContentStream$AppendMode` replaces the
   old boolean flags."
  (:require [clojure.string :as s]
            [clojure.stacktrace :as strace]
            [clojure.java.io :as io]
            [orcpub.common :as common]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.display :as dis5e]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.options :as options]
            [clj-http.client :as client])
  (:import (java.io ByteArrayOutputStream)
           (java.util.zip Deflater DeflaterOutputStream)
           (org.apache.pdfbox.pdmodel.graphics.image PDImageXObject)
           (org.apache.pdfbox.pdmodel.interactive.form PDCheckBox PDTextField PDTerminalField)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget
                                                              PDAppearanceDictionary
                                                              PDAppearanceStream)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (org.apache.pdfbox.cos COSName COSDictionary)
           (org.apache.pdfbox.pdmodel PDPage PDDocument PDPageContentStream PDResources)
           ;; APPEND appends operators to a page's existing content stream, so
           ;; template artwork survives. The alternative overwrites it.
           (org.apache.pdfbox.pdmodel PDPageContentStream$AppendMode)
           (org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory)
           (org.apache.pdfbox.pdmodel.graphics.state PDExtendedGraphicsState)
           (org.apache.pdfbox.pdmodel.font PDType1Font PDFont PDType0Font Standard14Fonts$FontName)
           (javax.imageio ImageIO)
           (java.net URL HttpURLConnection)))

;; The Base 14 fonts, present in every PDF reader. Constructed once at load time.
(def HELVETICA
  "Standard Helvetica font (regular weight, upright)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA))

(def HELVETICA_BOLD
  "Standard Helvetica font (bold weight, upright)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD))

(def HELVETICA_OBLIQUE
  "Standard Helvetica font (regular weight, italic/oblique)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA_OBLIQUE))

(def HELVETICA_BOLD_OBLIQUE
  "Standard Helvetica font (bold weight, italic/oblique)"
  (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD_OBLIQUE))

(defn load-fonts
  "Loads the fonts for the document. Will contain
  :plain, :italic, :bold and :bold-italic fonts."
  [doc]
  (reduce-kv
    (fn [m type file]
      (assoc m type
               (with-open [stream (.openStream (io/resource file))]
                 (PDType0Font/load doc stream))))
    {}
    {:plain       "Vollkorn-Regular.ttf"
     :italic      "Vollkorn-Italic.ttf"
     :bold        "Vollkorn-Bold.ttf"
     :bold-italic "Vollkorn-BoldItalic.ttf"}))

(defn make-image-loader
  "Returns a memoized (resource-path -> PDImageXObject) embedder scoped to ONE
   document. The card icons and logo are drawn dozens of times across a spellbook;
   without this, each use re-decodes the PNG and embeds a DUPLICATE image object —
   wasting CPU + transient memory per embed and bloating the file. Memoized so each
   distinct image is decoded and embedded exactly once, then referenced thereafter."
  [doc]
  (memoize
   (fn [resource-path]
     (with-open [s (io/input-stream (io/resource resource-path))]
       (LosslessFactory/createFromImage doc (ImageIO/read s))))))

(defn- normalize-text
  "Coerce a string into the WinAnsiEncoding subset PDFBox 3.x can render.
   WinAnsiEncoding maps only 0x20-0xFF; PDType1Font throws on anything else, which
   the appearance generator would hit at fill time -- blanking that field.

   - \\t (U+0009)         -> space (keep separation in proportional fonts)
   - other 0x00-0x1F      -> dropped
   - \\n and \\r          -> preserved (PDF multi-line line break)
   - U+2018 / U+2019      -> apostrophe
   - U+201C / U+201D      -> double quote
   - U+2013 / U+2014      -> hyphen / double hyphen
   - U+2026               -> three dots
   - any remaining > U+00FF -> '?' (a stray glyph degrades to a placeholder rather
                              than blanking the whole field)

   nil -> nil; non-strings stringified."
  [s]
  (when (some? s)
    (-> (str s)
        (s/replace "\t" " ")
        (s/replace #"[\u0000-\u0008\u000B\u000C\u000E-\u001F]" "")
        (s/replace "\u2018" "'")  (s/replace "\u2019" "'")
        (s/replace "\u201C" "\"") (s/replace "\u201D" "\"")
        (s/replace "\u2013" "-")  (s/replace "\u2014" "--")
        (s/replace "\u2026" "...")
        (s/replace #"[^\u0000-\u00FF]" "?"))))

(defn- fix-widget-page-refs!
  "Populate the /P (page reference) entry on widget annotations that are missing it.
   Many fillable templates omit the /P back-pointer; PDFBox 3.x's flatten() then
   logs a WARN per widget whose .getPage returns null. The page->annotation walk
   gives the owning page, so we set it explicitly. Widgets with a valid /P are left
   alone (multi-widget fields may legitimately point at a different page)."
  [doc]
  (doseq [page (.getPages doc)
          annotation (.getAnnotations page)
          :when (and (instance? PDAnnotationWidget annotation)
                     (nil? (.getPage annotation)))]
    (.setPage annotation page)))


(defn string-width
  "Width of `text` in INCHES at `font-size`, not points: the raw glyph width is
   divided by 72. Callers working in points must convert."
  [text ^PDFont font font-size]
  (if text
    (/ (* (/ (.getStringWidth font (if (keyword? text) (common/safe-name text) text)) 1000.0) font-size) 72)
    0))

;; ─── Orphaned widgets ────────────────────────────────────────────────────────

(defn- on-page-widgets
  "Identity hashes of the annotation COS objects reachable from any page."
  [doc]
  (into #{}
        (for [page (.getPages doc)
              annotation (.getAnnotations page)]
          (System/identityHashCode (.getCOSObject annotation)))))

(defn- clone-page
  "A new page sharing `src`'s content stream and resources. Referencing rather
   than copying keeps added pages to their field structure: six clones of a
   421 KB page add about 1 KB."
  [^PDPage src]
  (let [src-dict (.getCOSObject src)
        dict (COSDictionary.)]
    (.setItem dict COSName/TYPE COSName/PAGE)
    (doseq [k [COSName/CONTENTS COSName/RESOURCES COSName/MEDIA_BOX COSName/ROTATE]]
      (when-let [v (.getDictionaryObject src-dict k)]
        (.setItem dict k v)))
    (PDPage. dict)))

(defn prune-orphan-widgets!
  "Removes widgets with no page, then fields left with no widgets. Returns the
   number of widgets removed.

   Filters each field's widget list rather than keeping or dropping whole fields:
   a field may own widgets on several pages."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [live (on-page-widgets doc)
          live? (fn [w] (contains? live (System/identityHashCode (.getCOSObject w))))
          removed (atom 0)
          kept (reduce (fn [acc field]
                         (let [widgets (vec (.getWidgets field))
                               good (filterv live? widgets)]
                           (swap! removed + (- (count widgets) (count good)))
                           (cond
                             (empty? good) acc
                             (= (count good) (count widgets)) (conj acc field)
                             ;; Only terminal fields own widgets; non-terminal ones
                             ;; are containers and are left as they are.
                             (instance? PDTerminalField field)
                             (do (.setWidgets field (java.util.ArrayList. good))
                                 (conj acc field))
                             :else (conj acc field))))
                       [] (vec (.getFields form)))]
      (.setFields form (java.util.ArrayList. kept))
      @removed)
    0))



;; ─── Spellcasting class pages ─────────────────────────────────────────────────

(defn- spell-page-for-suffix
  "The page carrying spellcasting-class-<n>, or nil."
  [doc n]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (when-let [field (some-> form (.getField (str "spellcasting-class-" n)))]
      (let [widgets (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                   (.getWidgets field)))]
        (first (for [page (.getPages doc)
                     annotation (.getAnnotations page)
                     :when (contains? widgets (System/identityHashCode
                                               (.getCOSObject annotation)))]
                 page))))))

(defn- renumber-suffix
  "spells-3-4-6 -> spells-3-4-7 for from 6, to 7.

   A name without the class suffix -- the template's anonymous \"Check Box N\"
   fields -- gets the suffix appended instead. Every field on a copied page needs
   a name of its own: same-named fields are one field with one value, so leaving
   these alone would make a checkbox tick on two pages at once."
  [field-name from to]
  (if (s/ends-with? field-name (str "-" from))
    (str (subs field-name 0 (- (count field-name) (count (str from)))) to)
    (str field-name "-" to)))

(def ^:private widget-entries
  "Widget dictionary entries a cloned spell-page field takes from its source:
   geometry and styling only.

   /AP is deliberately absent. An appearance stream is a shared COS object, so a
   clone carrying its source's /AP would render from the same baked visual and
   writing one class's spells would rewrite the other's page. write-fields!
   generates a fresh appearance from the value instead."
  [COSName/RECT COSName/DA COSName/MK COSName/F COSName/FT])

(defn add-spell-pages!
  "Copies the page for class `from` once per entry in `to-suffixes`, renaming
   every field to that suffix. Returns the number of pages added.

   (2026-09) The same result as calling add-spell-page! in a loop, and much
   cheaper. That scans the whole form for the source page's fields and rebuilds
   the form's field list on every call, so growing style 1 to six sections cost
   65, 71, 75, 81 then 87 ms as the form grew, allocating 39 to 51 MB a clone to
   add 214 fields. The source page does not change between clones, so its fields
   are found once here and the form's list rebuilt once at the end: 321 ms and
   44 MB for the same six sections."
  [doc from to-suffixes]
  ;; A character with one casting class asks for no clones, and that is the common
  ;; case. Everything below -- finding the page, indexing its annotations, scanning
  ;; the form for its 214 fields -- is setup for a loop that would not run.
  (if-let [template (and (seq to-suffixes) (spell-page-for-suffix doc from))]
    (let [form (.getAcroForm (.getDocumentCatalog doc))
          on-template (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                     (.getAnnotations template)))
          ;; Name and widget entries are read once per source field, not once per
          ;; clone. getDictionaryObject returns the same COS object every time, so
          ;; the clones shared these entries already; asking five times only cost
          ;; more -- 131 MB of the 165 MB growing to six sections allocated.
          sources (vec (for [field (vec (.getFields form))
                             :let [widget (first (filter #(contains? on-template
                                                                     (System/identityHashCode
                                                                      (.getCOSObject %)))
                                                         (.getWidgets field)))]
                             :when (and widget (instance? PDTerminalField field))]
                         [field
                          (.getFullyQualifiedName field)
                          (vec (for [k widget-entries
                                     :let [v (.getDictionaryObject (.getCOSObject widget) k)]
                                     :when v]
                                 [k v]))]))
          pages (.getPages doc)
          made (doall
                (for [to to-suffixes]
                  (let [page (clone-page template)
                        new-fields
                        (doall
                         (for [[field fq-name entries] sources]
                           (let [copy (if (instance? PDCheckBox field)
                                        (PDCheckBox. form)
                                        (PDTextField. form))
                                 new-widget (PDAnnotationWidget.)]
                             (.setPartialName copy (renumber-suffix fq-name from to))
                             (doseq [[k v] entries]
                               (.setItem (.getCOSObject new-widget) k v))
                             (.setPage new-widget page)
                             (.setWidgets copy (java.util.ArrayList. [new-widget]))
                             copy)))]
                    (.setAnnotations page (java.util.ArrayList.
                                           (mapv #(first (.getWidgets %)) new-fields)))
                    [page new-fields])))
          ;; After the last spell page, not at the end of the document: styles 1
          ;; and 2 carry a features and traits page after their spell pages.
          anchor-page (some (fn [p] (when (> (.indexOf pages p) (.indexOf pages template)) p))
                            (vec pages))]
      (doseq [[page _] made]
        (if anchor-page (.insertBefore pages page anchor-page) (.addPage doc page)))
      (.setFields form (java.util.ArrayList.
                        (concat (vec (.getFields form)) (mapcat second made))))
      (count made))
    0))

(defn add-spell-page!
  "Appends a spellcasting page for class `to`, copied from the page for class
   `from` with every field renamed to the new suffix. Returns the field count, or
   nil when there is no page for `from`.

   The copy shares the source's content stream, so it costs field structure only."
  [doc from to]
  (when-let [template (spell-page-for-suffix doc from)]
    (let [form (.getAcroForm (.getDocumentCatalog doc))
          on-template (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                     (.getAnnotations template)))
          page (clone-page template)
          new-fields
          (doall
           (for [field (vec (.getFields form))
                 :let [widget (first (filter #(contains? on-template
                                                         (System/identityHashCode
                                                          (.getCOSObject %)))
                                             (.getWidgets field)))]
                 :when (and widget (instance? PDTerminalField field))]
             (let [copy (if (instance? PDCheckBox field)
                          (PDCheckBox. form)
                          (PDTextField. form))
                   new-widget (PDAnnotationWidget.)]
               (.setPartialName copy (renumber-suffix (.getFullyQualifiedName field) from to))
               (doseq [k widget-entries]
                 (when-let [v (.getDictionaryObject (.getCOSObject widget) k)]
                   (.setItem (.getCOSObject new-widget) k v)))
               (.setPage new-widget page)
               (.setWidgets copy (java.util.ArrayList. [new-widget]))
               copy)))]
      ;; After the last spell page, not at the end of the document: styles 1 and 2
      ;; carry a features and traits page after their spell pages, and appending
      ;; put the generated pages behind it.
      (let [pages (.getPages doc)
            after (some (fn [p] (when (> (.indexOf pages p) (.indexOf pages template)) p))
                        (vec pages))]
        (if after
          (.insertBefore pages page after)
          (.addPage doc page)))
      (.setAnnotations page (java.util.ArrayList. (mapv #(first (.getWidgets %)) new-fields)))
      (.setFields form (java.util.ArrayList. (concat (vec (.getFields form)) new-fields)))
      (count new-fields))))


;; ─── Generating a sheet from a master ─────────────────────────────────────────
;;
;; (2026-09, a second pass over the templates.) The first pass baked the static
;; cleanups into resources/ -- see dev/prepare_templates.clj -- but left seven
;; variants of every style on disk, one per spell-page count. Each carries its
;; own copy of that style's artwork: across the 28 files that is 32.7 MB of
;; images against 13.2 MB of distinct pixels. The wider variants are generated
;; here instead, from one master per style.
;;
;; Two measurements shaped this and are easy to undo by accident.
;;
;; Grow a narrow master rather than trimming the widest one. Trimming makes every
;; export bigger -- style 1's one-caster sheet went from 276 KB to 654 -- because
;; removing pages removes no shared resource. Growing shrinks them: six casters
;; on style 1 lands at 328 KB against the 565 KB file that ships today.
;;
;; And the master is the smallest file holding every distinct PAGE KIND, not the
;; narrowest file. Style 4 prints its licence line on its last page only, so the
;; single spell page in its one-spell file is the marked one and cloning that
;; repeats the line on every page. Its two-spell file holds a plain page and a
;; marked one, which is why sheet-masters names it.

(def sheet-masters
  "The file each style grows from, and where that style's artwork carries its
   attribution.

   :marks describes what the style's own pages do, not a preference. A footer
   drawn into a page's content stream can be spread by cloning a marked page but
   never removed from one, so an :all style has no plain spell page to offer.

   :without-casters is the variant with no spell page at all, opened for a
   character who casts nothing. Every style has one, so this only ever ADDS pages
   to a master and never removes any. Removing was tried and is worse twice over:
   a removed page takes its fields but leaves the resources it referenced, so
   style 2's non-caster sheet came out 453 KB against the 241 KB of the file that
   already has no spell page. Shipping four more files costs 1.2 MB of the 44.3
   this replaces.

   It also covers style 4, whose marked page IS a spell page: without this its
   licence line would vanish along with the spell pages."
  {1 {:file "fillable-char-sheetstyle-1-1-spells.pdf" :marks :all
      :without-casters "fillable-char-sheetstyle-1-0-spells.pdf"}
   2 {:file "fillable-char-sheetstyle-2-1-spells.pdf" :marks :all
      :without-casters "fillable-char-sheetstyle-2-0-spells.pdf"}
   3 {:file "fillable-char-sheetstyle-3-1-spells.pdf" :marks :none
      :without-casters "fillable-char-sheetstyle-3-0-spells.pdf"}
   4 {:file "fillable-char-sheetstyle-4-2-spells.pdf" :marks :last
      :without-casters "fillable-char-sheetstyle-4-0-spells.pdf"}})

(defn- fields-on-page
  "Every terminal field with a widget on `page`."
  [doc page]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        here (into #{} (map #(System/identityHashCode (.getCOSObject %))
                            (.getAnnotations page)))]
    (vec (for [field (iterator-seq (.iterator (.getFieldTree form)))
               :when (instance? PDTerminalField field)
               :when (some #(contains? here (System/identityHashCode (.getCOSObject %)))
                           (.getWidgets field))]
           field))))

(defn- renumber-page-section!
  "Renames every field on `page` from spellcasting section `from` to `to`."
  [doc page from to]
  (when (not= from to)
    (doseq [field (fields-on-page doc page)]
      (.setPartialName field (renumber-suffix (.getFullyQualifiedName field) from to)))))

(defn- spell-sections
  "The document's spellcasting sections as [n page], lowest first.

   One pass over the form. Asking spell-page-for-suffix for each n in turn walks
   every page's annotations once per n, which for a sheet with no spell pages at
   all was nineteen scans to find nothing."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (sort-by first
             (for [field (iterator-seq (.iterator (.getFieldTree form)))
                   :let [matched (re-matches #"spellcasting-class-(\d+)"
                                             (.getFullyQualifiedName field))]
                   :when matched
                   :let [page (some-> (first (.getWidgets field)) .getPage)]
                   :when page]
               [(Integer/parseInt (second matched)) page]))
    []))

(defn grow-spell-sections!
  "Reshapes an opened master to hold exactly `wanted` spellcasting sections,
   numbered 1 upward in page order. Returns the number of pages added.

   `marks` comes from sheet-masters. Under :last the master's second section is
   the marked page and is moved to the final section, so the licence line lands
   where the printed sheet puts it instead of on every clone."
  [doc wanted marks]
  ;; A character who casts nothing is opened from :without-casters, which has no
  ;; spell page to grow. Answering that before the let keeps the form untouched:
  ;; spell-sections walks every page's annotations, and there is nothing to find.
  (if (zero? wanted)
    0
    (let [sections (vec (spell-sections doc))
          marked-last? (and (= marks :last) (> (count sections) 1))
          [plain-n plain-page] (first sections)
          [marked-n marked-page] (when marked-last? (last sections))]
      (cond
        marked-last?
        (do
          ;; The marked page moves to its final section BEFORE any clone is made:
          ;; it sits at section 2 in the master, which is the first section a clone
          ;; would claim, and two fields of one name are one field with one value.
          (renumber-page-section! doc marked-page marked-n wanted)
          (if (= wanted 1)
            (do (.removePage doc plain-page) 0)
            (add-spell-pages! doc plain-n (range 2 wanted))))

        :else
        (add-spell-pages! doc plain-n (range 2 (inc wanted)))))))

(defn- highest-spell-page
  "The largest N for which the document has a spellcasting-class-N page. 0 when
   the template has no spell pages, as on the non-caster sheet."
  [doc]
  (reduce (fn [best n] (if (spell-page-for-suffix doc n) n best))
          0
          (range 1 21)))

(defn add-missing-spell-pages!
  "Appends a spellcasting page for every class in `fields` beyond the ones the
   template carries. Returns the number of pages added.

   Templates provide a fixed set of spellcasting sections -- six at most, fewer on
   the variants for characters with fewer casting classes -- while pdf_spec emits
   one per class with no limit. Without this the extra classes are dropped
   outright: write-fields! has nowhere to put their names, slots or spells.

   Prunes orphaned widgets before adding anything, which is required rather than
   tidy: a template that has not been through dev/prepare_templates.clj still
   carries the FIELDS of its deleted pages, so spellcasting-class-4 and its spells
   exist with no page, and a page claiming those names would collide with the
   ghosts and share their values."
  [doc fields]
  (let [wanted (->> (keys fields)
                    (keep #(second (re-matches #"spellcasting-class-(\d+)" (name %))))
                    (map #(Integer/parseInt %))
                    (reduce max 0))
        source (when (pos? wanted) (highest-spell-page doc))]
    (if (or (nil? source) (zero? source) (<= wanted source))
      0
      (do (prune-orphan-widgets! doc)
          (add-spell-pages! doc source (range (inc source) (inc wanted)))))))

(defn- unnamed-checkbox?
  "The templates call every checkbox \"Check Box N\". split-fields-across-pages!
   suffixes its copies, so those must match too or they stay unnamed -- which is
   how class 2's prepared ticks were missed."
  [field-name]
  (boolean (re-matches #"(?i)check box \d+(-p\d+)?" field-name)))

(defn- appearance-fingerprint
  "The bytes a widget's appearance actually draws, keyed by state name, plus its
   box size. Two widgets with equal fingerprints render identically, so one can
   stand in for the other."
  [widget]
  (when-let [normal (some-> widget .getAppearance .getNormalAppearance)]
    (let [r (.getRectangle widget)
          states (if (.isSubDictionary normal)
                   (into (sorted-map)
                         (for [[k v] (.getSubDictionary normal)]
                           [(str k) (with-open [in (.createInputStream (.getCOSObject v))]
                                      (vec (.readAllBytes in)))]))
                   {"" (with-open [in (.createInputStream
                                       (.getCOSObject (.getAppearanceStream normal)))]
                         (vec (.readAllBytes in)))})]
      [(Math/round (.getWidth r)) (Math/round (.getHeight r)) states])))

(defn share-checkbox-appearances!
  "Points every checkbox widget that draws the same thing at one shared appearance
   dictionary. Returns the number of widgets redirected.

   The templates carry a separate appearance stream per checkbox -- 582 on the
   style 1 six-caster sheet, drawing four distinct things between them -- and each
   is a compressed stream object. Collapsing them halves the file.

   Safe for checkboxes and NOT for text fields. A checkbox's appearance is chosen
   by its state, so ticking one selects a different entry in the shared dictionary
   and leaves the stream alone. A text field's appearance encodes its VALUE, and
   PDFBox rewrites that stream in place when the value changes, so sharing one
   between text fields makes an edit to either rewrite both.

   Fields are matched on what their appearance draws, not on their size, so two
   boxes that merely happen to share a rectangle keep their own artwork."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [cache (volatile! {})]
      (count
       (for [field (iterator-seq (.iterator (.getFieldTree form)))
             :when (instance? PDCheckBox field)
             widget (.getWidgets field)
             :let [key (appearance-fingerprint widget)]
             :when key
             :let [master (get @cache key)]]
         (if master
           (.setAppearance widget master)
           (vswap! cache assoc key (.getAppearance widget))))))
    0))

(defn- paeth-filtered
  "PNG-predicts `pixels` row by row with the Paeth filter, the form a PDF image
   asks for with /Predictor 15. Each row gains a leading filter-type byte.

   `bpp` is bytes per pixel, so the left neighbour is that many bytes back.
   Trying all five PNG filters per row and keeping the cheapest is the textbook
   approach and was measured to be no better here -- 1238.1 KB against Paeth's
   1237.0, for seventeen times the work."
  ^bytes [^bytes pixels rows cols bpp]
  (let [stride (* cols bpp)
        out (byte-array (* rows (inc stride)))]
    (dotimes [r rows]
      (let [base (* r stride)
            prev (when (pos? r) (* (dec r) stride))
            off (inc (* r (inc stride)))]
        (aset-byte out (* r (inc stride)) (byte 4))
        (dotimes [i stride]
          (let [a (if (>= i bpp) (bit-and (aget pixels (+ base i (- bpp))) 255) 0)
                b (if prev (bit-and (aget pixels (+ prev i)) 255) 0)
                c (if (and prev (>= i bpp)) (bit-and (aget pixels (+ prev i (- bpp))) 255) 0)
                p (- (+ a b) c)
                pa (Math/abs (- p a)) pb (Math/abs (- p b)) pc (Math/abs (- p c))
                guess (cond (and (<= pa pb) (<= pa pc)) a (<= pb pc) b :else c)]
            (aset-byte out (+ off i)
                       (unchecked-byte (- (bit-and (aget pixels (+ base i)) 255) guess)))))))
    out))

(defn- deflate-bytes
  ^bytes [^bytes b]
  (let [out (ByteArrayOutputStream.)
        deflater (Deflater. Deflater/BEST_COMPRESSION)]
    (with-open [dos (DeflaterOutputStream. out deflater)] (.write dos b))
    (.end deflater)
    (.toByteArray out)))

(defn add-image-predictors!
  "Re-encodes every Flate-compressed image that has no PNG predictor, adding one.
   Returns the number of images shrunk.

   Lossless: the pixels are unchanged, only how they are encoded. A predictor
   stores each byte as its difference from a neighbour, which for the shaded
   backgrounds on the raster sheets compresses far better than the raw values --
   style 4's page background goes from 1629.9 KB to 1237.0. Re-deflating without
   a predictor gains only 6%, so the predictor is doing the work, not the
   compression level.

   Skipped where it cannot be shown safe: anything but 8 bits per component, and
   any image whose re-encoded bytes do not decode back to the original pixels."
  [doc]
  (let [shrunk (volatile! 0)]
    (doseq [page (.getPages doc)
            :let [res (.getResources page)]
            :when res
            nm (vec (.getXObjectNames res))
            :let [x (try (.getXObject res nm) (catch Exception _ nil))]
            :when (instance? PDImageXObject x)
            :let [cos (.getCOSObject x)
                  filter-name (some-> (.getDictionaryObject cos COSName/FILTER) .getName)
                  parms (.getDictionaryObject cos (COSName/getPDFName "DecodeParms"))]
            :when (and (= "FlateDecode" filter-name)
                       (= 8 (.getBitsPerComponent x))
                       (not (and (instance? COSDictionary parms)
                                 (.containsKey ^COSDictionary parms
                                               (COSName/getPDFName "Predictor")))))]
      (let [pixels (with-open [in (.createInputStream cos)] (.readAllBytes in))
            cols (.getWidth x)
            comps (.getNumberOfComponents (.getColorSpace x))
            rows (.getHeight x)
            before (with-open [in (.createRawInputStream cos)] (alength (.readAllBytes in)))]
        (when (= (alength pixels) (* rows cols comps))
          (let [encoded (deflate-bytes (paeth-filtered pixels rows cols comps))]
            (when (< (alength encoded) before)
              (let [parms (COSDictionary.)]
                (.setInt parms (COSName/getPDFName "Predictor") 15)
                (.setInt parms (COSName/getPDFName "Colors") comps)
                (.setInt parms (COSName/getPDFName "BitsPerComponent") 8)
                (.setInt parms (COSName/getPDFName "Columns") cols)
                (with-open [out (.createRawOutputStream cos)] (.write out encoded))
                (.setItem cos (COSName/getPDFName "DecodeParms") parms)
                ;; the guarantee: it has to decode back to what went in
                (let [check (with-open [in (.createInputStream cos)] (.readAllBytes in))]
                  (if (java.util.Arrays/equals pixels check)
                    (vswap! shrunk inc)
                    (throw (ex-info "image re-encode was not lossless"
                                    {:image (str nm) :columns cols}))))))))))
    @shrunk))

(defn share-duplicate-images!
  "Points every page at a single copy of each image it uses more than once.
   Returns the number of references redirected.

   Styles 3 and 4 are raster sheets -- a full-page background per page rather
   than vector art -- and style 3 ships the same 192 KB image twice in every
   template. An image XObject is immutable reference data, so two byte-identical
   ones are interchangeable in a way two text-field appearances are not.

   Matched on the raw encoded bytes, so a re-encoding of the same picture is left
   alone rather than assumed equivalent."
  [doc]
  (let [seen (volatile! {})
        redirected (volatile! 0)]
    (doseq [page (.getPages doc)
            :let [res (.getResources page)]
            :when res
            nm (vec (.getXObjectNames res))
            :let [x (try (.getXObject res nm) (catch Exception _ nil))]
            :when (instance? PDImageXObject x)]
      (let [bytes (with-open [in (.createRawInputStream (.getCOSObject x))]
                    (.readAllBytes in))
            digest (vec (.digest (java.security.MessageDigest/getInstance "SHA-256") bytes))]
        (if-let [master (get @seen digest)]
          (when-not (identical? (.getCOSObject master) (.getCOSObject x))
            (.put res nm master)
            (vswap! redirected inc))
          (vswap! seen assoc digest x))))
    @redirected))

(defn split-fields-across-pages!
  "Splits any field whose widgets sit on more than one page into one field per
   page. Returns the number of fields added.

   A field is one value however many widgets show it, so a checkbox with a widget
   on two spell pages ticks on both at once. The style 1 six-caster template ships
   101 of these: the prepared ticks and the SLOTS EXPENDED fields are shared
   between the first two classes' pages, so a Wizard and a Cleric would mirror
   each other.

   Must run before the naming passes. They name a field after the row beside one
   of its widgets and then skip it, since it no longer looks unnamed -- so a
   spanning field would keep the first page's name and go on mirroring."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [page-index (into {} (for [i (range (.getNumberOfPages doc))
                                    annotation (.getAnnotations (.getPage doc i))]
                                [(System/identityHashCode (.getCOSObject annotation)) i]))
          page-of #(page-index (System/identityHashCode (.getCOSObject %)))
          taken (volatile! (into #{} (map #(.getFullyQualifiedName %) (.getFields form))))
          unique (fn [base]
                   (loop [n 2]
                     (let [candidate (str base "-p" n)]
                       (if (contains? @taken candidate)
                         (recur (inc n))
                         (do (vswap! taken conj candidate) candidate)))))
          added (atom [])]
      (doseq [field (vec (.getFields form))
              :when (instance? PDTerminalField field)
              :let [groups (group-by page-of (vec (.getWidgets field)))]
              :when (> (count groups) 1)]
        (let [[keep-page & other-pages] (sort-by #(or % -1) (keys groups))]
          ;; the field keeps the widgets from its first page
          (.setWidgets field (java.util.ArrayList. (get groups keep-page)))
          (doseq [page other-pages]
            (let [copy (if (instance? PDCheckBox field)
                         (PDCheckBox. form)
                         (PDTextField. form))]
              (.setPartialName copy (unique (.getFullyQualifiedName field)))
              ;; Only text fields carry a default appearance; a checkbox takes its
              ;; look from the widget's appearance states, which move with it.
              (when (instance? PDTextField field)
                (when-let [da (.getDefaultAppearance field)]
                  (.setDefaultAppearance copy da)))
              (.setWidgets copy (java.util.ArrayList. (get groups page)))
              (swap! added conj copy)))))
      (.setFields form (java.util.ArrayList. (concat (vec (.getFields form)) @added)))
      (count @added))
    0))

(defn- unnamed-slots-expended?
  "The templates call the blank beside each SLOTS TOTAL box \"SlotsRemaining N\".
   split-fields-across-pages! suffixes its copies, so those must match too."
  [field-name]
  (boolean (re-matches #"(?i)slotsremaining \d+(-p\d+)?" field-name)))

(defn name-slots-expended!
  "Renames the templates' \"SlotsRemaining N\" blanks after the level box each one
   sits beside, so SlotsRemaining 19 becomes slots-expended-1-1 next to
   spell-slots-1-1. Returns the number renamed.

   The pairing is geometric and exact: the blank shares its level box's baseline
   and sits immediately to its right, and the nine levels are far apart
   vertically. The name is the only link -- the two are separate fields with no
   reference between them.

   Nothing writes these; the sheet leaves them blank for the player to fill in
   after download. Run before disambiguate-duplicate-fields! so the split copies
   get a level rather than a numeric suffix."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [fields (vec (.getFields form))
          widget-boxes (fn [page]
                         (let [on-page (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                                      (.getAnnotations page)))]
                           (for [field fields
                                 widget (.getWidgets field)
                                 :when (contains? on-page (System/identityHashCode
                                                           (.getCOSObject widget)))
                                 :let [r (.getRectangle widget)]]
                             {:field field
                              :name (.getFullyQualifiedName field)
                              :x (.getLowerLeftX r)
                              :y (.getLowerLeftY r)})))
          taken (volatile! (into #{} (map #(.getFullyQualifiedName %) fields)))]
      (reduce
       (fn [renamed page]
         (let [entries (widget-boxes page)
               blanks (filter #(unnamed-slots-expended? (:name %)) entries)
               totals (filter #(re-matches #"spell-slots-\d+-\d+" (:name %)) entries)]
           (+ renamed
              (count
               (for [blank blanks
                     :let [total (->> totals
                                      (filter #(and (< 0 (- (:x blank) (:x %)) 80)
                                                    (< (Math/abs (- (:y %) (:y blank))) 3)))
                                      first)
                           candidate (when total
                                       (str "slots-expended-"
                                            (subs (:name total) (count "spell-slots-"))))]
                     :when (and candidate (not (contains? @taken candidate)))]
                 (do (vswap! taken conj candidate)
                     (.setPartialName (:field blank) candidate)
                     candidate))))))
       0
       (.getPages doc)))
    0))

(defn name-prepared-checkboxes!
  "Renames the templates' anonymous \"Check Box N\" fields after the spell row each
   one sits beside, so Check Box 25 becomes prepared-1-1-1 next to spells-1-1-1.
   Returns the number renamed.

   The pairing is geometric: a row's checkbox sits about 8pt to its left on the
   same baseline, which is unambiguous — the next row is 14pt away vertically.
   Rows without a checkbox (cantrips, which are always prepared) simply have no
   match and are left alone.

   Safe because pdf_spec writes to none of these names; they are ticked by hand
   after download. Run before disambiguate-duplicate-fields! so these get a
   meaningful name rather than a numeric suffix."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [fields (vec (.getFields form))
          widget-boxes (fn [page]
                         (let [on-page (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                                      (.getAnnotations page)))]
                           (for [field fields
                                 widget (.getWidgets field)
                                 :when (contains? on-page (System/identityHashCode
                                                           (.getCOSObject widget)))
                                 :let [r (.getRectangle widget)]]
                             {:field field
                              :name (.getFullyQualifiedName field)
                              :x (.getLowerLeftX r)
                              :y (.getLowerLeftY r)})))
          taken (volatile! (into #{} (map #(.getFullyQualifiedName %) fields)))]
      (reduce
       (fn [renamed page]
         (let [entries (widget-boxes page)
               boxes (filter #(unnamed-checkbox? (:name %)) entries)
               rows (filter #(re-matches #"spells-\d+-\d+-\d+" (:name %)) entries)]
           (+ renamed
              (count
               (for [box boxes
                     :let [row (->> rows
                                    (filter #(and (< 0 (- (:x %) (:x box)) 20)
                                                  (< (Math/abs (- (:y %) (:y box))) 6)))
                                    first)
                           candidate (when row (str "prepared-" (subs (:name row) (count "spells-"))))]
                     :when (and candidate (not (contains? @taken candidate)))]
                 (do (vswap! taken conj candidate)
                     (.setPartialName (:field box) candidate)
                     candidate))))))
       0
       (.getPages doc)))
    0))

(defn name-death-save-checkboxes!
  "Names the death-save ticks on the character page, which carry no spell row to
   take a name from. Returns the number renamed.

   Applies only when exactly six anonymous checkboxes remain on a page in two rows
   of three, which is the death-save block: successes above failures, each row
   left to right. Verified by ticking the upper row and rendering. Any other
   arrangement is left alone rather than guessed at."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [fields (vec (.getFields form))]
      (reduce
       (fn [renamed page]
         (let [on-page (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                      (.getAnnotations page)))
               boxes (for [field fields
                           :when (unnamed-checkbox? (.getFullyQualifiedName field))
                           widget (.getWidgets field)
                           :when (contains? on-page (System/identityHashCode
                                                     (.getCOSObject widget)))
                           :let [r (.getRectangle widget)]]
                       {:field field :x (.getLowerLeftX r) :y (.getLowerLeftY r)})
               rows (->> boxes (group-by #(Math/round (double (:y %)))) sort reverse (map second))]
           (if (and (= 6 (count boxes)) (= 2 (count rows)) (every? #(= 3 (count %)) rows))
             (do (doseq [[label row] (map vector ["success" "failure"] rows)
                         [i box] (map-indexed vector (sort-by :x row))]
                   (.setPartialName (:field box) (str "death-save-" label "-" (inc i))))
                 (+ renamed 6))
             renamed)))
       0
       (.getPages doc)))
    0))

(defn disambiguate-duplicate-fields!
  "Gives each field its own name where several share one. Returns the number
   renamed.

   Fields sharing a fully-qualified name are ONE field with one value, so ticking a
   prepared-spell box on one class's spell page ticks the same box on every other
   class's page. The style 1 templates ship 103 such names: 92 anonymous
   \"Check Box N\" fields repeated across the six spell pages, the SlotsRemaining
   bubbles, and the two image placeholders.

   The first field in each group keeps the original name so anything addressing it
   by name still resolves; the rest take a numeric suffix. Safe for the export
   path because pdf_spec writes to none of the duplicated names — they are filled
   by hand after download."
  [doc]
  (if-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (let [fields (vec (.getFields form))
          taken (volatile! (into #{} (map #(.getFullyQualifiedName %) fields)))
          unique-name (fn [base]
                        (loop [n 2]
                          (let [candidate (str base "-" n)]
                            (if (contains? @taken candidate)
                              (recur (inc n))
                              (do (vswap! taken conj candidate) candidate)))))]
      (reduce (fn [renamed [_ group]]
                (if (< (count group) 2)
                  renamed
                  (do (doseq [field (rest group)]
                        (.setPartialName field (unique-name (.getFullyQualifiedName field))))
                      (+ renamed (dec (count group))))))
              0
              (group-by #(.getFullyQualifiedName %) fields)))
    0))

(def ^:private min-single-line-size
  "Floor for shrinking a single-line field. Below this a modifier is unreadable,
   and a value that still does not fit is left to clip rather than vanish."
  5.0)

(defn- baked-font-size
  "The point size in a widget's generated appearance stream, or nil."
  [widget]
  (some-> widget .getAppearance .getNormalAppearance .getAppearanceStream
          .getCOSObject
          (as-> stream
                (with-open [in (.createInputStream stream)]
                  (let [text (String. (.readAllBytes in) "ISO-8859-1")]
                    (when-let [m (re-find #"/\S+ ([\d.]+) Tf" text)]
                      (Double/parseDouble (second m))))))))

(defn- shrink-single-line-to-fit!
  "Rewrites a single-line field at a smaller size when its value is wider than its
   box, and returns the size used, or nil when nothing was needed.

   PDFBox sizes a single-line field by HEIGHT alone, so the skill and save boxes
   settle on 8pt whatever they hold. Those are 14.4pt wide with a 12.4pt clip, and
   \"+11\" is 13.6pt at 8pt: every modifier of +10 or worse loses its last
   character, which a level 20 caster reaches in its own casting stat."
  [field widget value]
  (let [rect (.getRectangle widget)
        ;; The generated appearance clips at "1 1 w h re", so 1pt each side.
        available (- (.getWidth rect) 2.0)
        size (baked-font-size widget)
        width (when size (* 72.0 (string-width value HELVETICA size)))]
    (when (and size width (> width available))
      ;; 2% under the exact fit: sizing to the millimetre lands a hair over once
      ;; the size is rounded into the appearance stream.
      (let [fitted (max min-single-line-size (* size 0.98 (/ available width)))]
        (.setDefaultAppearance field (str "/Helv " fitted " Tf 0 g"))
        (.setValue field value)
        fitted))))

(defn write-fields!
  "Populate an AcroForm in `doc` from the `fields` map, optionally flattening.

   - `fields`     {field-name-keyword value}. Checkboxes take truthy/falsey; text
                  fields take any value (normalized to WinAnsi).
   - `flatten?`   truthy => bake appearances into page content and remove the
                  interactive form (locked PDF). Falsey => stays fillable.
   - `font-sizes` {field-name-keyword pt-size}, consulted ONLY when flattening —
                  interactive forms keep the template's `/Helv 0 Tf` auto-sizing.

   Bakes real appearance streams (NeedAppearances false + /Helv in the default
   resources) so values render AND print in every viewer — not just ones that honor
   NeedAppearances (Firefox's print path does not).

   Returns the sorted names in `fields` that the template has no field for, and
   logs them; those values are dropped. Templates vary in which fields they
   define, so this is reported rather than thrown."
  [doc fields flatten? font-sizes]
  (let [catalog (.getDocumentCatalog doc)
        form (.getAcroForm catalog)
        res (or (.getDefaultResources form) (PDResources.))]
    ;; The templates' field default appearances reference "/Helv"; ensure it's in
    ;; the form default resources so PDFBox can GENERATE appearance streams (missing
    ;; it -> generation silently fails -> blank in viewers/print paths that don't
    ;; honor NeedAppearances).
    (when (nil? (.getFont res (COSName/getPDFName "Helv")))
      ;; Fresh instance (not the shared module-level HELVETICA): this COS object is
      ;; added to THIS document's resource tree, and sharing one across documents
      ;; risks aliasing during concurrent saves.
      (.put res (COSName/getPDFName "Helv") (PDType1Font. Standard14Fonts$FontName/HELVETICA)))
    (.setDefaultResources form res)
    ;; Bake appearances ourselves rather than deferring to the viewer.
    (.setNeedAppearances form false)
    ;; (2026-09) One index rather than a lookup per value. PDAcroForm.getField
    ;; walks the field tree on every call, and this asked it twice for each name
    ;; -- once to report the unplaceable ones and once to write. On a six-caster
    ;; sheet that is 284 names against 1403 fields, twice, and it dominated the
    ;; export: 294 MB of the 607 MB a full sheet allocated.
    (let [by-name (persistent!
                   (reduce (fn [m field]
                             (assoc! m (.getFullyQualifiedName field) field))
                           (transient {})
                           (iterator-seq (.iterator (.getFieldTree form)))))
          unplaceable (sort (map name (remove #(contains? by-name (name %)) (keys fields))))]
      (when (seq unplaceable)
        (println (format "pdf/write-fields!: %d value(s) had no field in this template and were dropped: %s"
                         (count unplaceable) (s/join ", " unplaceable))))
      (doseq [[k v] fields]
      (try
        (let [field (get by-name (name k))]
          (when field
            ;; font-sizes gated on flatten?: interactive forms keep the template's
            ;; `/Helv 0 Tf` auto-sizing; flattening bakes a concrete size, so we
            ;; rewrite the DA to the caller's size first.
            (when (and flatten? (font-sizes k) (instance? PDTextField field))
              (.setDefaultAppearance field (str "/Helv " " " (font-sizes k) " Tf 0 0 0 rg")))
            (let [text (when (instance? PDTextField field) (normalize-text v))]
              (.setValue
               field
               (cond
                 (instance? PDCheckBox field) (if v "Yes" "Off")
                 (instance? PDTextField field) text
                 :else nil))
              ;; Only single-line fields need this; multiline ones already shrink
              ;; to fit, and fit-text splits them before they get too small.
              (when (and text
                         (instance? PDTextField field)
                         (not (.isMultiline field))
                         (not (s/blank? text)))
                (doseq [widget (.getWidgets field)]
                  (shrink-single-line-to-fit! field widget text))))))
        (catch Exception e (prn "failed writing field: " k v (strace/print-stack-trace e)))))
      (when flatten?
        (fix-widget-page-refs! doc)
        (.flatten form))
      unplaceable)))

(defn content-stream
  "Create a PDPageContentStream for appending content to an existing page.
   
   PDFBox 3.x API: Use AppendMode enum instead of boolean flags.
   - APPEND: Add content after existing page content (what we want for templates)
   - OVERWRITE: Replace existing content (triggers warning on non-empty pages)
   - PREPEND: Add content before existing content
   
   The 4th arg (true) enables compression."
  [doc page]
  (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND true))

(defn in-to-sz [inches]
  (float (* 72 inches)))

(defn in-to-coord-x [inches]
  (in-to-sz inches))

(defn in-to-coord-y [inches]
  (in-to-sz (- 11 inches)))

(defn scale [[r-h r-w] [i-h i-w]]
  (let [height-to-width (/ i-h i-w)
        rect-height-to-width (/ r-h r-w)
        height-ratio (/ r-h i-h)]
    (if (> height-to-width rect-height-to-width)
      [r-h (* r-h (/ i-w i-h))]
      [(* r-w (/ i-h i-w)) r-w])))

(defn draw-imagex [c-stream img x y width height]
  (let [[scaled-height scaled-width] (scale [height width] [(.getHeight img) (.getWidth img)])]
    (.drawImage
     c-stream
     img
     (in-to-coord-x (+ x (if (< scaled-width width)
                           (/ (- width scaled-width) 2)
                           0)))
     (in-to-coord-y (+ height y (if (< scaled-height height)
                                  (/ (- scaled-height height) 2)
                                  0)))
     (in-to-sz scaled-width)
     (in-to-sz scaled-height))))

(defn draw-imagex-alpha
  "draw-imagex at a reduced constant opacity (0.0-1.0). Used for the faded
   grayscale card icons: a black `-bw` icon drawn at ~40% reads as a light
   backdrop the black label sits over. Isolated in a save/restore so the alpha
   doesn't leak into later drawing."
  [cs img x y width height alpha]
  (let [gs (doto (PDExtendedGraphicsState.)
             (.setNonStrokingAlphaConstant (float alpha)))]
    (.saveGraphicsState cs)
    (.setGraphicsStateParameters cs gs)
    (draw-imagex cs img x y width height)
    (.restoreGraphicsState cs)))

(def user-agent "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.172")

(def ^:private max-image-bytes
  "The 128k the character builder has always advertised next to the Image URL
   field. It was label text and nothing enforced it."
  (* 128 1024))

(def ^:private max-image-pixels
  "Width x height ceiling, checked from the header BEFORE any pixels are
   decoded.

   A byte cap alone does not bound memory: a 69-byte PNG can declare
   25000x25000 in its IHDR, and ImageIO/read would allocate 2.5GB honouring it.
   Reading dimensions costs only the header."
  (* 2000 2000))

(defn- private-address?
  "Addresses no user-supplied URL has any business reaching."
  [^java.net.InetAddress addr]
  (or (.isLoopbackAddress addr)
      (.isAnyLocalAddress addr)
      (.isLinkLocalAddress addr)     ; 169.254/16 — cloud instance metadata
      (.isSiteLocalAddress addr)     ; 10/8, 172.16/12, 192.168/16
      (.isMulticastAddress addr)))

(defn safe-image-url?
  "Whether the server may fetch this URL.

   The route's own filter allowed file:// and ftp:// and placed no restriction
   on the host, so a character image of
   http://169.254.169.254/latest/meta-data/ made the PDF exporter fetch cloud
   instance metadata, and file:///etc/passwd made it open local files. Neither
   needs the response to be rendered: whether the fetch succeeds, fails or
   times out is itself the signal.

   Returns false rather than throwing so a bad URL is skipped like an image
   that failed to load, which is a state the sheet already handles."
  [url]
  (try
    (let [u (URL. url)
          protocol (.getProtocol u)]
      (and (contains? #{"http" "https"} protocol)
           (let [addrs (java.net.InetAddress/getAllByName (.getHost u))]
             (and (seq addrs)
                  ;; every resolved address, not just the first: a hostname can
                  ;; answer with both a public and a private address.
                  (not-any? private-address? addrs)))))
    (catch Exception _ false)))

(defn- open-image-stream
  "Opens url with redirects disabled and the response size bounded.

   Redirects are off because they defeat any host check: a permitted host that
   offers an open redirect would otherwise hand the fetch straight to a private
   address."
  [url]
  (let [^HttpURLConnection conn (.openConnection (URL. url))]
    (doto conn
      (.setInstanceFollowRedirects false)
      (.setRequestProperty "User-Agent" user-agent)
      (.setConnectTimeout 10000)
      (.setReadTimeout 10000))
    (let [status (.getResponseCode conn)]
      (when-not (<= 200 status 299)
        (throw (ex-info (str "Image URL returned " status)
                        {:error :image-load-failed :url url :status status})))
      (let [len (.getContentLengthLong conn)]
        (when (> len max-image-bytes)
          (throw (ex-info "Image is larger than the 128k limit"
                          {:error :image-too-large :url url :bytes len}))))
      (.getInputStream conn))))

(defn- within-pixel-budget?
  "Reads the image header only and answers whether decoding it is safe."
  [^bytes data]
  (with-open [iis (ImageIO/createImageInputStream (java.io.ByteArrayInputStream. data))]
    (let [readers (ImageIO/getImageReaders iis)]
      (if-not (.hasNext readers)
        false
        (let [r (.next readers)]
          (try
            (.setInput r iis true true)
            (<= (* (.getWidth r 0) (.getHeight r 0)) max-image-pixels)
            (finally (.dispose r))))))))

(defn- read-bounded-bytes
  "All of the stream, refusing to exceed max-image-bytes."
  [^java.io.InputStream in]
  (let [out (java.io.ByteArrayOutputStream.)
        buf (byte-array 8192)]
    (loop [total 0]
      (let [n (.read in buf)]
        (cond
          (neg? n) (.toByteArray out)
          (> (+ total n) max-image-bytes)
          (throw (ex-info "Image is larger than the 128k limit"
                          {:error :image-too-large :bytes (+ total n)}))
          :else (do (.write out buf 0 n) (recur (+ total n))))))))

(defn safe-image-bytes
  "Fetch url and return its bytes, or throw. Every limit is applied before any
   pixel buffer is allocated."
  [url]
  (when-not (safe-image-url? url)
    (throw (ex-info "Image URL is not permitted"
                    {:error :image-url-not-permitted :url url})))
  (let [data (with-open [in (open-image-stream url)]
               (read-bounded-bytes in))]
    (when-not (within-pixel-budget? data)
      (throw (ex-info "Image dimensions exceed the limit"
                      {:error :image-too-large-dimensions :url url})))
    data))

(defn draw-non-jpg [doc page url x y width height]
  (try
    (with-open [c-stream (content-stream doc page)]
      (let [buff-image (ImageIO/read (java.io.ByteArrayInputStream.
                                      (safe-image-bytes url)))]
        (when (nil? buff-image)
          (throw (ex-info "Unable to read image from URL"
                          {:error :invalid-image-format
                           :url url})))
        (let [img (LosslessFactory/createFromImage doc buff-image)]
          (draw-imagex c-stream img x y width height))))
    (catch java.net.SocketTimeoutException e
      (throw (ex-info (str "Timeout loading image from URL: " url)
                      {:error :image-load-timeout
                       :url url}
                      e)))
    (catch java.net.UnknownHostException e
      (throw (ex-info (str "Unable to resolve host for image URL: " url)
                      {:error :unknown-host
                       :url url}
                      e)))
    (catch Exception e
      (throw (ex-info (str "Failed to load image from URL: " url)
                      {:error :image-load-failed
                       :url url}
                      e)))))

(defn draw-jpg [doc page url x y width height]
  (try
    (with-open [c-stream (content-stream doc page)
                image-stream (java.io.ByteArrayInputStream. (safe-image-bytes url))]
      (let [img (JPEGFactory/createFromStream doc image-stream)]
        (draw-imagex c-stream img x y width height)))
    (catch java.net.SocketTimeoutException e
      (throw (ex-info (str "Timeout loading image from URL: " url)
                      {:error :image-load-timeout
                       :url url}
                      e)))
    (catch java.net.UnknownHostException e
      (throw (ex-info (str "Unable to resolve host for image URL: " url)
                      {:error :unknown-host
                       :url url}
                      e)))
    (catch Exception e
      (throw (ex-info (str "Failed to load JPEG image from URL: " url)
                      {:error :jpeg-load-failed
                       :url url}
                      e)))))

(defn draw-image! [doc page url x y width height]
  (let [lower-case-url (s/lower-case url)
        jpg? (or (s/ends-with? lower-case-url "jpg")
                 (s/ends-with? lower-case-url "jpeg"))
        draw-fn (if jpg? draw-jpg draw-non-jpg)]
    (try
      (draw-fn doc page url x y width height)
      (catch clojure.lang.ExceptionInfo e
        (println "ERROR: Failed to load image for PDF:" (.getMessage e))
        (println "  URL:" url)
        (println "  Details:" (ex-data e))
        nil)
      (catch Exception e
        (println "ERROR: Unexpected error loading image for PDF:" (.getMessage e))
        (println "  URL:" url)
        (clojure.stacktrace/print-stack-trace e)
        nil))))

(defn get-page [doc index]
  (.getPage doc index))

(defn split-lines [text ^PDFont font font-size width]
  (let [words (s/split text #"\s")]
    (loop [lines []
           current-line nil
           [next-word & remaining-words :as current-words] words]
      (if next-word
        (let [line-with-word (str current-line (when current-line " ") next-word)
              new-width (string-width line-with-word font font-size)]
          (if (> new-width width)
            (if current-line
              (recur (conj lines current-line)
                     nil
                     current-words)
              ;; A single word wider than the box takes its own line. Retrying it
              ;; against an empty current-line would never consume it, so the word
              ;; has to be taken here or the loop never terminates.
              (recur (conj lines next-word)
                     nil
                     remaining-words))
            (recur lines
                   line-with-word
                   remaining-words)))
        (if current-line
          (conj lines current-line)
          lines)))))

;; ─── Fitting text to a box ────────────────────────────────────────────────────

(def min-font-size
  "Point size below which text is spilled to a continuation page rather than
   scaled down further. Auto-sizing alone would shrink to 4pt and then clip.

   8pt rather than 7pt costs nothing where it matters: the short boxes hold 3
   lines (ideals, bonds, flaws) or 5 (personality-traits) at either size, and only
   the full-page boxes give up about 12%."
  8.0)

(def ^:private line-height-factor
  "Leading as a multiple of font size. Matches draw-lines-to-box."
  1.1)

(defn widget-box
  "Drawable [width height] of `field` in points, or nil if it has no on-page
   widget. Insets 2pt per side to match the appearance stream's padding.

   Measures an on-page widget: fields here carry several, and the first is
   typically orphaned."
  [doc field]
  (let [live (on-page-widgets doc)]
    (when-let [w (first (filter #(contains? live (System/identityHashCode (.getCOSObject %)))
                                (.getWidgets field)))]
      (let [r (.getRectangle w)]
        ;; 2pt inset per side, matching the appearance stream's padding.
        [(- (.getWidth r) 4.0) (- (.getHeight r) 4.0)]))))

(defn fit-text
  "Splits `text` at the last line fitting a `width` x `height` point box at `size`,
   defaulting to `min-font-size`.

   Returns {:head fitting-text :tail remainder-or-nil :lines line-count}. Callers
   place :head in the field and :tail on a continuation page.

   Line breaks in `text` are hard breaks and are preserved on both sides of the
   split; `traits-fields` in pdf_spec separates its sections with them. Wrapping
   within a paragraph uses `split-lines`, whose width is in inches.

   A single word too wide for the box still occupies its own line, so text always
   makes progress and callers cannot loop forever."
  ([text width height] (fit-text text width height min-font-size))
  ([text width height size]
   (let [paragraphs (s/split-lines (str text))
         wrap (fn [p] (if (s/blank? p)
                        [""]
                        (vec (split-lines p HELVETICA size (/ width 72.0)))))
         per-box (max 1 (int (Math/floor (/ height (* size line-height-factor)))))]
     (if (s/blank? (str text))
       {:head "" :tail nil :lines 0}
       (loop [[p & more] paragraphs, kept [], used 0]
         (cond
           (nil? p)
           {:head (s/join "\n" kept) :tail nil :lines used}

           :else
           (let [lines (wrap p)
                 room (- per-box used)]
             (cond
               ;; whole paragraph fits
               (<= (count lines) room)
               (recur more (conj kept p) (+ used (count lines)))

               ;; nothing left: this paragraph and the rest spill
               (zero? room)
               {:head (s/join "\n" kept)
                :tail (s/join "\n" (cons p more))
                :lines used}

               ;; split inside the paragraph
               :else
               {:head (s/join "\n" (conj kept (s/join " " (take room lines))))
                :tail (s/join "\n" (cons (s/join " " (drop room lines)) more))
                :lines per-box}))))))))


;; ─── Relabelling a spell level box ────────────────────────────────────────────
;;
;; Each spell level's rows live in a box whose level number is printed ARTWORK,
;; not a field, so the boxes are bound to their levels by the page itself. That
;; is why a class with more 1st-level spells than the twelve rows of the level 1
;; box spills to another page while levels 4-9 sit empty -- on a level 5 cleric
;; that is 13 spells moved for want of 59 rows that were right there.
;;
;; A field with a background fill can cover the printed numeral, letting a box be
;; re-pointed at another level. The hexagon's centre carries a light bevel, so a
;; small patch there hides the numeral without touching the outline or the grey
;; edging around it.

(def ^:private hexagon-offset
  "Where a level's hexagon sits relative to its SLOTS TOTAL box, and how big it
   is, in points. Measured on the style 1 spell page, where the hexagon abuts the
   left edge of the slots box at every one of the nine levels.

   Check these against the artwork with the :outline? option to
   relabel-spell-level! before trusting them on another style -- a wrong offset
   looks fine in a normal render and only shows as a sliver of the printed shape
   left uncovered."
  {:dx -21.0 :dy -7.5 :width 19.0 :height 37.0})

(def ^:private hexagon-path
  "The hexagon's corners as fractions of its bounding box, traced from the
   rendered artwork at 1200 dpi rather than assumed. It is not symmetric: the left
   edge is vertical, the points sit near it at about 0.3 across, and the right
   side bulges out. A symmetric hexagon leaves visible wedges where it misses."
  [[0.30 1.00] [1.00 0.65] [1.00 0.33] [0.30 0.00] [0.00 0.16] [0.00 0.83]])

(def ^:private numeral-patch-scale
  "How much of the hexagon the patch covers.

   The shape does not have to match the artwork; it has to stay inside the white
   centre. Overshooting eats the grey bevel, which is what shows. Measured by
   rendering at 300 dpi and counting bevel pixels the patch changes against
   numeral pixels it fails to cover:

     scale   bevel eaten   numeral left
      0.62        ~5050              0
      0.50         ~170              0
      0.46            ~0             0
      0.30            ~0        visible

   0.46 is the widest that covers the numeral without touching the bevel. Above
   it the patch reads as a pale notch in the hexagon's shading."
  0.46)

(defn- hexagon-appearance
  "An appearance stream drawing a hexagon-shaped patch with `label` centred in it.

   The patch is the hexagon's own shape scaled about its centre, so it sits inside
   the printed outline instead of cutting a rectangle out of it.

   `outline?` strokes the path magenta, which is how the offsets above were
   checked: a misplaced patch is invisible in a normal render but obvious against
   the printed hexagon once its edge is drawn."
  [doc width height label outline? patch-scale]
  (let [stream (PDAppearanceStream. doc)
        resources (PDResources.)
        font (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD)
        size 13.0
        cx (/ width 2.0)
        cy (/ height 2.0)
        factor (or patch-scale numeral-patch-scale)
        point (fn [[fx fy]]
                [(+ cx (* factor (- (* fx width) cx)))
                 (+ cy (* factor (- (* fy height) cy)))])
        [[sx sy] & rest-points] (map point hexagon-path)
        text-width (* size (/ (.getStringWidth font label) 1000.0))]
    (.setResources stream resources)
    (.setBBox stream (PDRectangle. 0 0 width height))
    (.put resources (COSName/getPDFName "HelvB") font)
    (with-open [out (.createOutputStream (.getCOSObject stream))]
      (.write out (.getBytes
                   (str (if outline? "1 1 1 rg 1 0 1 RG 0.4 w\n" "1 1 1 rg\n")
                        (format "%.2f %.2f m\n" sx sy)
                        (s/join (for [[x y] rest-points] (format "%.2f %.2f l\n" x y)))
                        (if outline? "h B\n" "h f\n")
                        "BT\n/HelvB " size " Tf\n0 g\n"
                        (format "%.2f %.2f Td\n" (- cx (/ text-width 2.0)) (- cy 4.5))
                        "(" label ") Tj\nET\n")
                   "ISO-8859-1")))
    stream))

(defn spell-level-numeral-box
  "The hexagon carrying the printed level numeral for `level` in the spellcasting
   section `suffix`, as [x y width height], or nil when that level has no slots
   box.

   Derived from the slots box rather than hardcoded, so it follows the artwork if
   the page is re-cut."
  [doc level suffix]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (when-let [field (some-> form (.getField (str "spell-slots-" level "-" suffix)))]
      (when-let [widget (first (filter #(some? (.getPage %)) (.getWidgets field)))]
        (let [r (.getRectangle widget)
              {:keys [dx dy width height]} hexagon-offset]
          [(+ (.getLowerLeftX r) dx) (+ (.getLowerLeftY r) dy) width height])))))

(defn relabel-spell-level!
  "Covers the printed level numeral for `level` in section `suffix` with `label`.
   Returns the field added, or nil when the level has no box on this template.

   The numerals are printed heavy, so the label is drawn in bold to match.

   `opts` takes :outline?, which strokes the patch magenta so its placement can be
   checked against the artwork, and :scale, which overrides how much of the
   hexagon it covers. Both are for fitting these numbers to a style whose spell
   page has not been measured."
  ([doc level suffix label] (relabel-spell-level! doc level suffix label nil))
  ([doc level suffix label {patch-scale :scale :keys [outline?]}]
  (when-let [[x y w h] (spell-level-numeral-box doc level suffix)]
    (let [form (.getAcroForm (.getDocumentCatalog doc))
          resources (.getDefaultResources form)
          field (PDTextField. form)
          widget (PDAnnotationWidget.)
          page (.getPage (first (.getWidgets (.getField form (str "spell-slots-" level "-" suffix)))))]
      (when (nil? (.getFont resources (COSName/getPDFName "HelvB")))
        (.put resources (COSName/getPDFName "HelvB")
              (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD)))
      (.setDefaultResources form resources)
      (.setPartialName field (str "spell-level-label-" level "-" suffix))
      (.setRectangle widget (PDRectangle. x y w h))
      (.setPage widget page)
      (.setWidgets field (java.util.ArrayList. [widget]))
      (.setAnnotations page (java.util.ArrayList.
                             (conj (vec (.getAnnotations page)) widget)))
      (.setFields form (java.util.ArrayList. (conj (vec (.getFields form)) field)))
      (.setDefaultAppearance field "/HelvB 13 Tf 0 g")
      (.setQ field 1)
      ;; The value goes on first: setValue makes PDFBox generate its own
      ;; appearance, which would otherwise be appended after this one and draw the
      ;; numeral a second time. Read-only stops a viewer regenerating it, and this
      ;; is a label rather than something to fill in.
      (.setValue field (str label))
      (let [appearance (PDAppearanceDictionary.)]
        (.setNormalAppearance appearance
                              (hexagon-appearance doc w h (str label) outline? patch-scale))
        (.setAppearance widget appearance))
      (.setReadOnly field true)
      field))))

(def ^:private printed-slot-labels
  "The SLOTS TOTAL / SLOTS EXPENDED line on the style 1 spell page, in page
   coordinates, measured off the artwork with PDFTextStripper and the rendered
   pixels. The page prints the line once, above the level 1 bar; every other
   level box is read from that one line by position.

   :grey is the flat value the artwork uses, which renders [150 151 151]."
  {:size 5.0
   :grey 0.59
   :baseline 483.17
   :total-x 50.83
   :expended-x 127.71
   :end-x 173.80})

(def ^:private cantrips-box-rise
  "How far the cantrips box sits above the level 1 box, in points. The printed
   numerals give it exactly: level 1 at baseline 463.99, and level 3 -- top of
   the middle column, level with the cantrips box -- at 631.72."
  167.73)

(def ^:private label-padding
  "Slack between the labels and their appearance BBox, which clips: a glyph
   flush to the edge loses its last pixel column."
  1.0)

(defn- cantrips-slot-labels-box
  "Rect for the slot labels above a reused cantrips bar: the printed level 1 line
   raised by cantrips-box-rise."
  []
  (let [{:keys [size baseline total-x end-x]} printed-slot-labels]
    [(- total-x label-padding)
     (- (+ baseline cantrips-box-rise) label-padding)
     (+ (- end-x total-x) (* 2 label-padding))
     (+ size (* 2 label-padding))]))

(defn- slot-labels-appearance
  "The SLOTS TOTAL and SLOTS EXPENDED labels in the size, grey and x positions
   printed-slot-labels measured off the printed pair above level 1.

   `origin-x` and `origin-y` are the widget's lower-left corner: the appearance
   stream's own coordinates start there, so the page positions are written as
   offsets from it."
  [doc origin-x origin-y width height]
  (let [{:keys [size grey baseline total-x expended-x]} printed-slot-labels
        stream (PDAppearanceStream. doc)
        resources (PDResources.)
        font (PDType1Font. Standard14Fonts$FontName/HELVETICA)
        text-y (- (+ baseline cantrips-box-rise) origin-y)]
    (.setResources stream resources)
    (.setBBox stream (PDRectangle. 0 0 width height))
    (.put resources (COSName/getPDFName "Helv") font)
    (with-open [out (.createOutputStream (.getCOSObject stream))]
      (.write out (.getBytes
                   ;; No fill: the labels sit on blank page above the bar, the
                   ;; way the printed ones do above level 1.
                   (str (format "BT\n/Helv %.1f Tf\n%.2f g\n" size grey)
                        (format "%.2f %.2f Td\n" (- total-x origin-x) text-y)
                        "(SLOTS TOTAL) Tj\nET\n"
                        (format "BT\n/Helv %.1f Tf\n%.2f g\n" size grey)
                        (format "%.2f %.2f Td\n" (- expended-x origin-x) text-y)
                        "(SLOTS EXPENDED) Tj\nET\n")
                   "ISO-8859-1")))
    stream))


;; ─── Reusing the cantrips box ─────────────────────────────────────────
;;
;; The cantrips box is eight more rows, and cantrips only need printing once, so
;; on a continuation page it is dead space. It can carry a spell level like any
;; other box, but it needs more than a new numeral: it has no slots field to
;; locate it by, its bar reads CANTRIPS, and it has no SLOTS TOTAL / SLOTS
;; EXPENDED labels because cantrips do not use slots.

(def ^:private cantrips-bar
  "Where the cantrips bar differs from a level bar, in page coordinates.

   A level bar divides SLOTS TOTAL from SLOTS EXPENDED at x 93-102, in the gap
   between the two fields. The cantrips bar's divider is at x 51-59 instead,
   right after the hexagon, leaving one long compartment where a level bar has
   two -- and that compartment reads CANTRIPS, printed x 112-142.

   Covering x 50.5 to 148 takes the stray divider and the word together. The
   patch runs the full height between the bar's inner rules -- they sit at
   y 625.5-625.9 and 645.4-645.9, flat from x 62 on -- so it clears the divider's
   sloped ends where they meet those rules without painting over the rules
   themselves. Stopping short of them leaves two visible stubs."
  {:patch-from 50.5
   :patch-to 148.0
   :interior-y 625.9
   :interior-height 19.5
   :divider-x 97.5})

(defn- bar-patch-appearance
  "Paints the patch white and strokes the divider a level bar has, at `divider-x`
   measured from the patch's left edge. 0.6pt matches the bar's own rules."
  [doc width height divider-x]
  (let [stream (PDAppearanceStream. doc)]
    (.setResources stream (PDResources.))
    (.setBBox stream (PDRectangle. 0 0 width height))
    (with-open [out (.createOutputStream (.getCOSObject stream))]
      (.write out (.getBytes (format "1 1 1 rg\n0 0 %.2f %.2f re f\n0 G\n0.6 w\n%.2f 0 m\n%.2f %.2f l\nS\n"
                                     width height divider-x divider-x height)
                             "ISO-8859-1")))
    stream))

(defn- cantrips-slot-boxes
  "The two slot inputs for a reused cantrips bar, as [total expended] rects:
   level 1's SLOTS TOTAL and SLOTS EXPENDED boxes raised by cantrips-box-rise.
   nil when either is missing.

   Taken from the live fields rather than written down, so the inputs land either
   side of the drawn divider the way level 1's land either side of its printed
   one."
  [doc suffix]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        raised (fn [nm]
                 (when-let [field (.getField form nm)]
                   (when-let [widget (first (filter #(some? (.getPage %))
                                                    (.getWidgets field)))]
                     (let [r (.getRectangle widget)]
                       {:rect [(.getLowerLeftX r) (+ (.getLowerLeftY r) cantrips-box-rise)
                               (.getWidth r) (.getHeight r)]
                        :quadding (.getQ field)}))))]
    (when-let [total (raised (str "spell-slots-1-" suffix))]
      (when-let [expended (raised (str "slots-expended-1-" suffix))]
        [total expended]))))

(defn- cantrips-hexagon-box
  "The cantrips bar's hexagon, as [x y width height]. The cantrips box has no
   slots field for spell-level-numeral-box to measure from, so it is level 1's
   hexagon raised by cantrips-box-rise."
  [doc suffix]
  (when-let [[x y w h] (spell-level-numeral-box doc 1 suffix)]
    [x (+ y cantrips-box-rise) w h]))

(defn reuse-cantrips-box!
  "Turns the cantrips box into a spell level box carrying `label`: renumbers the
   hexagon, makes the bar read like a level bar, and gives it the slot labels and
   the two slot inputs a level box has. Returns the fields added.

   The bar is patched rather than redrawn -- the level bars have shaped slot art
   this does not attempt, and a plain divider between two boxes is enough to read.
   On a continuation page the box is otherwise wasted, and eight rows are worth
   more than a matching bar.

   The inputs are named for the box, not for `label`: the level's own box is still
   on the page under spell-slots-<label>-<suffix>, and two fields cannot share a
   name without sharing a value."
  [doc suffix label]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        page (some-> form (.getField (str "spells-0-1-" suffix))
                     .getWidgets first .getPage)
        hexagon (cantrips-hexagon-box doc suffix)
        slots (cantrips-slot-boxes doc suffix)
        {:keys [patch-from patch-to interior-y interior-height divider-x]} cantrips-bar]
    (when (and page hexagon slots)
      (let [attach (fn [field [x y w h]]
                     (let [widget (PDAnnotationWidget.)]
                       (.setRectangle widget (PDRectangle. x y w h))
                       (.setPage widget page)
                       (.setWidgets field (java.util.ArrayList. [widget]))
                       (.setAnnotations page (java.util.ArrayList.
                                              (conj (vec (.getAnnotations page)) widget)))
                       (.setFields form (java.util.ArrayList.
                                         (conj (vec (.getFields form)) field)))
                       widget))
            art (fn [nm [x y w h :as rect] draw]
                  (let [field (PDTextField. form)
                        appearance (PDAppearanceDictionary.)]
                    (.setPartialName field nm)
                    (let [widget (attach field rect)]
                      (.setNormalAppearance appearance (draw x y w h))
                      (.setAppearance widget appearance))
                    (.setReadOnly field true)
                    field))
            input (fn [nm {:keys [rect quadding]}]
                    (let [field (PDTextField. form)]
                      (.setPartialName field nm)
                      (.setDefaultAppearance field (.getDefaultAppearance form))
                      (.setQ field quadding)
                      (attach field rect)
                      field))
            [total expended] slots]
        [(art (str "spell-level-label-0-" suffix) hexagon
              (fn [_ _ w h] (hexagon-appearance doc w h (str label) false nil)))
         (art (str "cantrips-bar-patch-" suffix)
              [patch-from interior-y (- patch-to patch-from) interior-height]
              (fn [x _ w h] (bar-patch-appearance doc w h (- divider-x x))))
         (art (str "cantrips-slot-labels-" suffix) (cantrips-slot-labels-box)
              (fn [x y w h] (slot-labels-appearance doc x y w h)))
         (input (str "cantrips-slots-total-" suffix) total)
         (input (str "cantrips-slots-expended-" suffix) expended)]))))

;; ─── Overflow pages ───────────────────────────────────────────────────────────

(def overflow-labels
  "Fields whose value spills to a continuation page when it will not fit at
   min-font-size, and the heading the spilled part appears under. Order sets the
   order of the spilled sections."
  [[:personality-traits       "PERSONALITY TRAITS"]
   [:ideals                   "IDEALS"]
   [:bonds                    "BONDS"]
   [:flaws                    "FLAWS"]
   [:attacks-and-spellcasting "ATTACKS & SPELLCASTING"]
   [:other-profs              "OTHER PROFICIENCIES & LANGUAGES"]
   [:features-and-traits      "EQUIPMENT"]
   [:treasure                 "TREASURE"]
   [:backstory                "CHARACTER BACKSTORY"]
   [:features-and-traits-2    "FEATURES & TRAITS"]])

(defn- continuation-page
  "The page carrying features-and-traits-2, used as the template for spill pages.
   nil when the template has no such field."
  [doc]
  (let [form (.getAcroForm (.getDocumentCatalog doc))]
    (when-let [field (some-> form (.getField "features-and-traits-2"))]
      (let [widgets (into #{} (map #(System/identityHashCode (.getCOSObject %))
                                   (.getWidgets field)))]
        (first (for [page (.getPages doc)
                     annotation (.getAnnotations page)
                     :when (contains? widgets (System/identityHashCode
                                               (.getCOSObject annotation)))]
                 page))))))

(defn- add-overflow-page!
  "Appends a copy of the continuation page holding `text` under field `field-name`.
   The name must be unique: fields sharing a name share one value."
  [doc ^PDPage template field-name text]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        source-widget (first (.getAnnotations template))
        page (clone-page template)
        field (PDTextField. form)
        widget (PDAnnotationWidget.)]
    (.addPage doc page)
    (doseq [k widget-entries]
      (when-let [v (.getDictionaryObject (.getCOSObject source-widget) k)]
        (.setItem (.getCOSObject widget) k v)))
    (.setPartialName field field-name)
    (.setMultiline field true)
    (.setPage widget page)
    (.setWidgets field (java.util.ArrayList. [widget]))
    (.setAnnotations page (java.util.ArrayList. [widget]))
    (.setFields form (java.util.ArrayList. (conj (vec (.getFields form)) field)))
    (.setValue field (normalize-text text))
    page))

(defn spill-overflow!
  "Trims values in `fields` that will not fit their box at min-font-size, moving
   the remainder onto appended continuation pages. Returns the trimmed map.

   Without this a long value is not cropped: the field auto-sizes, shrinking to
   4pt before it clips, so the sheet becomes unreadable rather than visibly full.

   Sections are gathered into one stream and paginated together so a few
   overflowing boxes cost one page rather than one page each."
  [doc fields]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        [trimmed sections]
        ;; (2026-09) The blank test comes first: getField walks the field tree and
        ;; widget-box walks the pages, and a sheet leaves most of these empty. Doing
        ;; them before knowing there is a value cost more than the fitting did.
        (reduce (fn [[acc sections] [k label]]
                  (let [value (str (get acc k))]
                    (if (s/blank? value)
                      [acc sections]
                      (let [box (some->> (.getField form (name k)) (widget-box doc))
                            {:keys [head tail]} (when box (apply fit-text value box))]
                        (if tail
                          [(assoc acc k head) (conj sections (str label "\n" tail))]
                          [acc sections])))))
                [fields []] overflow-labels)]
    ;; Likewise the continuation page: finding it means matching its widget against
    ;; every annotation on every page, and nothing needs it unless something spilled.
    (when-let [template (when (seq sections) (continuation-page doc))]
      (let [box (let [r (.getRectangle (first (.getAnnotations template)))]
                  [(- (.getWidth r) 4.0) (- (.getHeight r) 4.0)])]
        (loop [remaining (s/join "\n\n" sections), n 1]
          (when-not (s/blank? remaining)
            (let [{:keys [head tail]} (apply fit-text remaining box)]
              (add-overflow-page! doc template (str "overflow-" n) head)
              (recur (or tail "") (inc n)))))))
    trimmed))

(defn draw-lines-to-box [cs lines font font-size x y height]
  (let [leading (* font-size 1.1)
        max-lines (dec (/ (* 72 height) leading))
        units-x (* 72 x)
        units-y (* 72 y)
        fitting-lines (vec (take max-lines lines))]
    (.beginText cs)
    (.setFont cs font font-size)
    (.newLineAtOffset cs units-x units-y)
    (doseq [i (range (count fitting-lines))]
      (let [line (get fitting-lines i)]
        (.newLineAtOffset cs 0 (- leading))
        (.showText cs line)))
    (.endText cs)
    (vec (drop max-lines lines))))

(defn draw-text-to-box [cs text font font-size x y width height]
  (let [lines (split-lines text font font-size width)]
    (draw-lines-to-box cs lines font font-size x y height)))

(defn set-text-color
  "Set text (non-stroking) color. Values must be 0.0-1.0 floats (PDFBox 3.x)."
  [cs r g b]
  (.setNonStrokingColor cs (float r) (float g) (float b)))

(defn draw-text [cs text font font-size x y & [color]]
  (when text
    (let [units-x (* 72 x)
          units-y (* 72 y)]
      (.beginText cs)
      (.setFont cs font font-size)
      (when color
        (apply set-text-color cs color))
      (.newLineAtOffset cs units-x units-y)
      (.showText cs (if (keyword? text) (common/safe-name text) text))
      (when color
        (set-text-color cs 0 0 0))
      (.endText cs))))

(defn draw-halo-text
  "Draw text with a white outline (8 offset copies) then black fill, so a black
   label stays legible on top of a solid-black icon. Used only in printer-friendly
   B&W mode, where icon and text are both pure black; offsets are in inches."
  [cs text font font-size x y]
  (let [d 0.009]
    (doseq [dx [(- d) 0 d]
            dy [(- d) 0 d]
            :when (not (and (zero? dx) (zero? dy)))]
      (draw-text cs text font font-size (+ x dx) (+ y dy) [1 1 1]))
    (draw-text cs text font font-size x y [0 0 0])))

(defn draw-text-from-top [cs text font font-size x y & [color]]
  (draw-text cs text font font-size x (- 11.0 y) color))

(defn draw-line
  "Draw a line. PDFBox 3.x removed drawLine — use moveTo/lineTo/stroke."
  [cs start-x start-y end-x end-y]
  (.moveTo cs (float start-x) (float start-y))
  (.lineTo cs (float end-x) (float end-y))
  (.stroke cs))

(defn inches-to-units [inches]
  (float (* inches 72)))

(defn draw-line-in [cs & coords]
  (apply draw-line cs (map inches-to-units coords)))

(defn draw-grid
  "Draw the spell card grid. Light gray lines for card boundaries."
  [cs box-width box-height]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)
        ;; PDFBox 3.x: setStrokingColor(float,float,float) requires 0.0-1.0 range
        ;; (PDFBox 2.x accepted 0-255 integers via a separate overload)
        light-gray (float (/ 225.0 255.0))]
    (.setStrokingColor cs light-gray light-gray light-gray)
    (doseq [i (range (inc num-boxes-x))]
      (let [x (+ margin-x (* box-width i))]
        (draw-line-in cs
                      x
                      margin-y
                      x
                      (+ margin-y total-height))))
    (doseq [i (range (inc num-boxes-y))]
      (let [y (+ margin-y (* box-height i))]
        (draw-line-in cs
                      margin-x
                      y
                      (+ margin-x total-width)
                      y)))
    (.setStrokingColor cs (float 0) (float 0) (float 0))))

(defn spell-school-level [{:keys [level school]} class-nm]
  (let [school-str (if school (s/capitalize school) "Unknown")]
    (if (and level (zero? level))
      (str class-nm " Cantrip " school-str)
      (str class-nm " Level " (or level "?") " " school-str))))

(defn draw-spell-field [cs img title value x y bw? bw-faded?]
  ;; The label overprints the icon. Three treatments (draw-spell-field is the
  ;; single choke point for all four per-spell icons):
  ;;   color (default)  -> baked-red icon, plain black text
  ;;   B&W solid (A)     -> solid-black `-bw` icon, WHITE-HALO text so it reads
  ;;   B&W faded (B)     -> `-bw` icon at 40% (light backdrop), plain black text
  (let [icon (img (str "public/image/" title (when bw? "-bw") ".png"))
        ix x
        iy (- 11 y 0.12)]
    (if (and bw? bw-faded?)
      (draw-imagex-alpha cs icon ix iy 0.25 0.25 0.4)
      (draw-imagex cs icon ix iy 0.25 0.25))
    (if (and bw? (not bw-faded?))
      (draw-halo-text cs value HELVETICA_BOLD_OBLIQUE 8 x (- y 0.07))
      (do (.setNonStrokingColor cs (float 0) (float 0) (float 0))
          (draw-text cs value HELVETICA_BOLD_OBLIQUE 8 x (- y 0.07))
          (.setNonStrokingColor cs (float 0) (float 0) (float 0))))))

(defn abbreviate-times [time]
  (-> time
      (s/replace #"minute" "min")
      (s/replace #"hour" "hr")))

(defn max-len [s len]
  (if (<= (count s) len)
    s
    (subs s 0 len)))

(defn abbreviate-duration [duration]
  (when duration
    (-> duration
        (s/replace #"Concentration,? up to " "Conc, ")
        abbreviate-times
        (s/replace #"Instantaneous.*" "Inst")
        (s/replace #"round" "Rnd")
        (max-len 16))))

(defn abbreviate-casting-time [casting-time]
  (-> casting-time
      abbreviate-times
      (s/replace #"bonus action" "B.A.")
      (s/replace #"action" "Act.")
      (s/replace #"reaction" "React.")))

(defn abbreviate-range [range]
  (-> range
      (s/replace #"Self.*" "Self")
      (s/replace #"feet" "ft")))

(defn print-backs [cs fonts img box-width box-height remaining-lines-vec page-number logo-img]
  ;; `img` is the memoized per-document image loader; `logo?` opts each card back
  ;; into showing the card logo (matching the fronts) for double-sided printing.
  ;; When logo? is false NO images are loaded here — the old code decoded+embedded
  ;; the logo/rotation PNGs every page and never drew them (pure waste).
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 (* num-boxes-x box-width))
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)]
    (draw-grid cs 2.5 3.5)
    (draw-text cs
               (str "Page " (inc page-number) " (reverse)")
               (:italic fonts)
               8
               0.12
               (- 11 0.15))
    (doall
     (for [i (range num-boxes-x)
           j (range num-boxes-y)]
       (let [x (+ margin-x (* box-width i))
             y (+ margin-y (* box-height j))
             spell-index (+ i (* j num-boxes-x))
             {:keys [remaining-lines spell-name]} (remaining-lines-vec spell-index)]
         (if (seq remaining-lines)
           ;; This card back carries overflow text from the front.
           (do
             (draw-text-to-box cs
                               spell-name
                               (:bold fonts)
                               10
                               (+ x 0.12)
                               (- 11.0 y 0.08)
                               (- box-width 0.3)
                               0.25)
             (draw-lines-to-box cs
                               remaining-lines
                               (:plain fonts)
                               8
                               (+ x 0.12)
                               (- 11.0 y 0.24)
                               (- box-height 0.2))
             (draw-text-to-box cs
                               "(reverse)"
                               (:italic fonts)
                               10
                               (+ x 0.15 (string-width spell-name (:bold fonts) 10))
                               (- 11.0 y 0.08)
                               (- box-width 0.3)
                               (- box-height 0.2)))
           ;; Blank back — a large, CENTERED logo (~80% of the card). logo-img is
           ;; the resource path chosen by the caller (grayscale or solid-black DMV
           ;; mark), or nil when the logo is turned off. Both are hi-res, full-bleed
           ;; 997x997 PNGs, NOT the front's tiny 22x30 card-logo.png (which pixelates
           ;; and is cropped in-source). draw-imagex fits to the box preserving
           ;; aspect and centers it.
           (when logo-img
             (draw-imagex cs
                          (img logo-img)
                          (+ x (* box-width 0.1))
                          (+ y (* box-height 0.1))
                          (* box-width 0.8)
                          (* box-height 0.8)))))))))

(defn print-spells [cs document fonts img box-width box-height spells page-number print-spell-card-dc-mod? bw? bw-faded?]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)]
    (draw-grid cs 2.5 3.5)
        (draw-text cs
                   (str "Page " (inc page-number))
                   (:italic fonts)
                   8
                   0.12
                   (- 11 0.15))
        (doall
         (for [j (range num-boxes-y)
               i (range (dec num-boxes-x) -1 -1)
               :let [spell-index (+ i (* j num-boxes-x))]]
           (when-let [{:keys [class-nm dc attack-bonus spell] :as spell-data}
                      (get (vec spells) spell-index)]
             (let [{:keys [description
                           casting-time
                           duration
                           level
                           ritual
                           range]} spell
                   x (+ margin-x (* box-width i))
                   y (+ margin-y (* box-height j))

                   {:keys [page source description summary components]} spell
                   ;; Handle nil spell name gracefully
                   spell-name (or (:name spell) "(Unknown Spell)")

                   dc-str (str "DC " dc)
                   remaining-desc-lines
                   (draw-text-to-box cs
                                     (or description
                                         (if summary
                                           (str summary
                                                " (see "
                                                (if source
                                                  (s/upper-case (name source))
                                                  "PHB")
                                                " "
                                                page
                                                " for more details)")
                                           ""))
                                     (:plain fonts)
                                     8
                                     (+ x 0.12) ; from the left
                                     (- 11.0 y 1.08) ;from the top down
                                     (- box-width 0.24)
                                     (- box-height 1.13))]
               (when (:material-component components)
                 (draw-text-to-box cs
                                   (str (s/capitalize (:material-component components)))
                                   (:italic fonts)
                                   8
                                   (+ x 0.12)
                                   (- 11.0 y 0.55)
                                   (- box-width 0.24)
                                   0.5))
               (let [card-logo (img (str "public/image/card-logo" (when bw? "-bw") ".png"))]
                 (if (and bw? bw-faded?)
                   (draw-imagex-alpha cs card-logo (+ x 1.9) (+ y 0.02) 1.0 0.25 0.4)
                   (draw-imagex cs card-logo (+ x 1.9) (+ y 0.02) 1.0 0.25)))
               (draw-text-to-box cs
                                 spell-name
                                 (:bold fonts)
                                 10
                                 (+ x 0.12)
                                 (- 11.0 y)
                                 (- box-width 0.3)
                                 0.2)
               (draw-text-to-box cs
                                 (if ritual " (ritual)" "")
                                 (:italic fonts)
                                 10
                                 (+ x 0.12 (string-width spell-name (:bold fonts) 10))
                                 (- 11.0 y)
                                 (- box-width 0.3)
                                 0.2)
               (draw-text-to-box cs
                                 (if (not= class-nm "Homebrew")
                                   (str (spell-school-level spell class-nm) (when print-spell-card-dc-mod? (str " " dc-str " Spell Mod " (common/bonus-str attack-bonus))))
                                   (spell-school-level spell class-nm))
                                 (:italic fonts)
                                 8
                                 (+ x 0.12)
                                 (- 11.0 y 0.19)
                                 (- box-width 0.24)
                                 0.25)
               (when casting-time
                 (draw-spell-field cs
                                   img
                                   "magic-swirl"
                                   (str (abbreviate-casting-time
                                         (first
                                          (s/split
                                           casting-time
                                           #","))))
                                   (+ x 0.12)
                                   (- 11.0 y 0.45)
                                   bw? bw-faded?))
               (when range
                 (draw-spell-field cs
                                   img
                                   "arrow-dunk"
                                   (abbreviate-range range)
                                   (+ x 0.62)
                                   (- 11.0 y 0.45)
                                   bw? bw-faded?))
               (draw-spell-field cs
                                 img
                                 "shiny-purse"
                                 (s/join
                                  ","
                                  (remove
                                   nil?
                                   (map
                                    (fn [[k v]]
                                      (when (-> spell :components k)
                                        v))
                                    {:verbal "V"
                                     :somatic "S"
                                     :material "M"})))
                                 (+ x 1.12)
                                 (- 11.0 y 0.45)
                                 bw? bw-faded?)
               (when duration
                 (draw-spell-field cs
                                   img
                                   "sands-of-time"
                                   (abbreviate-duration duration)
                                   (+ x 1.62)
                                   (- 11.0 y 0.45)
                                   bw? bw-faded?))
               (when (seq remaining-desc-lines)
                 (let [recharge (img (str "public/image/clockwise-rotation" (when bw? "-bw") ".png"))]
                   (if (and bw? bw-faded?)
                     (draw-imagex-alpha cs recharge (+ x 2.3) (+ y 3.3) 0.15 0.15 0.4)
                     (draw-imagex cs recharge (+ x 2.3) (+ y 3.3) 0.15 0.15))))
               {:remaining-lines remaining-desc-lines
                :spell-name spell-name}))))))

(def ^:private bezier-k
  "Control-point offset that turns four cubic curves into a circle, to within a
   fifth of a percent. There is no arc operator in a PDF content stream."
  0.5522847)

(defn- card-pt
  "A point `dx` right and `dy` DOWN from the top-left of the card at (x, y), in
   PDF page units. Card coordinates run downward from the page top, page units run
   up from the page bottom."
  [x y dx dy]
  [(float (* 72 (+ x dx))) (float (* 72 (- 11 (+ y dy))))])

(defn- polyline!
  "Strokes, and optionally closes, a path through card-relative points."
  [cs x y points close?]
  (let [[[px py] & rest-pts] (map (fn [[dx dy]] (card-pt x y dx dy)) points)]
    (.moveTo cs px py)
    (doseq [[qx qy] rest-pts] (.lineTo cs qx qy))
    (if close? (.closeAndStroke cs) (.stroke cs))))

(defn- diamond!
  "A small diamond of radius `r` centred at (dx, dy) on the card. Filled ones mark
   the item's rarity rank, outlined ones the ranks above it."
  [cs x y dx dy r filled?]
  (let [pts [[dx (- dy r)] [(+ dx r) dy] [dx (+ dy r)] [(- dx r) dy]]
        [[px py] & rest-pts] (map (fn [[a b]] (card-pt x y a b)) pts)]
    (.moveTo cs px py)
    (doseq [[qx qy] rest-pts] (.lineTo cs qx qy))
    (.closePath cs)
    (if filled? (.fill cs) (.stroke cs))))

(defn- circle!
  "An outlined circle of radius `r` centred at (dx, dy) on the card."
  [cs x y dx dy r]
  (let [k (* r bezier-k)
        [cx cy] (card-pt x y dx dy)
        rr (* 72 r) kk (* 72 k)]
    (.moveTo cs (float (+ cx rr)) cy)
    (.curveTo cs (float (+ cx rr)) (float (+ cy kk)) (float (+ cx kk)) (float (+ cy rr)) cx (float (+ cy rr)))
    (.curveTo cs (float (- cx kk)) (float (+ cy rr)) (float (- cx rr)) (float (+ cy kk)) (float (- cx rr)) cy)
    (.curveTo cs (float (- cx rr)) (float (- cy kk)) (float (- cx kk)) (float (- cy rr)) cx (float (- cy rr)))
    (.curveTo cs (float (+ cx kk)) (float (- cy rr)) (float (+ cx rr)) (float (- cy kk)) (float (+ cx rr)) cy)
    (.closeAndStroke cs)))

(def ^:private rarity-rank
  "How the rarities order, for the diamonds along the top edge. :varies has no
   rank and draws none -- an item whose rarity depends on the table cannot be
   ranked against one that does not."
  {:common 1 :uncommon 2 :rare 3 :very-rare 4 :legendary 5})

(defn item-charges
  "How many charges the item's own text says it has, or nil.

   Reads the number off the description rather than a field, because the data has
   no charge count -- it is prose. A die expression takes its maximum, so the
   tracker has a circle for the best roll: `1d8 + 1 charges` gives nine.

   Anything past 99 is parse noise rather than a charge pool. Note what is NOT
   matched: the Manuals and Tomes whose words are \"charged with magic\" have no
   charges, and the word alone must not be enough to draw a tracker."
  [description]
  (when description
    (let [flat (s/replace description #"\s+" " ")]
      (when-let [[_ dice faces plus fixed]
                 (re-find #"(?i)\b(?:(\d+)d(\d+)(?:\s*\+\s*(\d+))?|(\d+))\s+charges\b" flat)]
        (let [n (if fixed
                  (parse-long fixed)
                  (+ (* (parse-long dice) (parse-long faces))
                     (if plus (parse-long plus) 0)))]
          (when (<= 1 n 99) n))))))

(def ^:private alignment-attunement
  "Attunement keywords that name an alignment rather than a class or a race. The
   books phrase these differently: a creature OF good alignment, not a good."
  #{:good :evil :lawful :chaotic :neutral})

(defn attunement-phrase
  "The parenthesised attunement clause a magic item carries in the books, or nil
   when the item needs none.

   `attunement` is a vector of keywords: [:any] for anyone, otherwise the classes,
   races or alignments that may attune. `details` overrides it, for the handful of
   items whose condition is a sentence rather than a list."
  [attunement details]
  (cond
    (seq details) (str "(" details ")")
    (empty? attunement) nil
    (= [:any] (vec attunement)) "(requires attunement)"
    :else
    ;; The article goes on the first name only -- "by a sorcerer, warlock, or
    ;; wizard", as the books set it. One per name is both wrong and wide enough to
    ;; run past the card's frame at the foot.
    (let [names (map (fn [k]
                       (if (alignment-attunement k)
                         (str "creature of " (name k) " alignment")
                         (common/kw-to-name k)))
                     attunement)
          listed (case (count names)
                   1 (first names)
                   2 (s/join " or " names)
                   (str (s/join ", " (butlast names)) ", or " (last names)))]
      (str "(requires attunement by a " listed ")"))))

(defn magic-item-subtitle
  "The italic line under a magic item's name: what kind of thing it is and how
   rare, in the order the books use.

   The attunement clause is deliberately NOT here. The card prints it at the foot,
   and a line carrying both was not only saying it twice -- \"requires attunement
   by a sorcerer, warlock, or wizard\" does not fit one line, so the subtitle
   clipped mid-phrase on exactly the items whose condition matters most."
  [{:keys [::mi/type ::mi/subtype ::mi/rarity]}]
  (let [kind (when type
               ;; Capitalised, and only the first word: the books write "Wondrous
               ;; item, rare", not "Wondrous Item".
               (s/capitalize
                (str (common/kw-to-name type)
                     (when subtype (str " (" (common/kw-to-name subtype) ")")))))
        rare (when rarity
               (if (= :varies rarity)
                 "rarity varies"
                 (s/lower-case (common/kw-to-name rarity))))
        ]
    (s/join ", " (remove s/blank? [kind rare]))))

(defn- chamfered-frame!
  "A rectangle with its corners cut, inset `m` from the card edge."
  [cs x y w h m c]
  (polyline! cs x y [[(+ m c) m] [(- w m c) m] [(- w m) (+ m c)] [(- w m) (- h m c)]
                     [(- w m c) (- h m)] [(+ m c) (- h m)] [m (- h m c)] [m (+ m c)]]
             true))

(defn- corner-brackets!
  "A right-angled bracket inside each corner, `len` long, `inset` in from the
   frame. Reads as cornerwork rather than a second border, because it stops."
  [cs x y w h m inset len]
  (let [a (+ m inset)
        r (- w m inset)
        b (- h m inset)]
    (doseq [[cx cy sx sy] [[a a 1 1] [r a -1 1] [a b 1 -1] [r b -1 -1]]]
      (polyline! cs x y [[(+ cx (* sx len)) cy] [cx cy] [cx (+ cy (* sy len))]] false))))

(defn- corner-diamonds!
  "A diamond at each corner of an inset rectangle.

   With `arms?`, two short strokes run from each diamond along the frame edges,
   which turns four marks into four cornerpieces. Adding reach rather than another
   mark is what separates the ranks at card size: filled against outlined is close
   to invisible from a foot away, mass is not."
  [cs x y w h m inset r filled? arms?]
  (let [a (+ m inset)
        rt (- w m inset)
        b (- h m inset)
        arm 0.135]
    (doseq [[dx dy sx sy] [[a a 1 1] [rt a -1 1] [a b 1 -1] [rt b -1 -1]]]
      (diamond! cs x y dx dy r filled?)
      (when arms?
        (polyline! cs x y [[(+ dx (* sx (+ r 0.022))) dy]
                           [(+ dx (* sx (+ r arm))) dy]] false)
        (polyline! cs x y [[dx (+ dy (* sy (+ r 0.022)))]
                           [dx (+ dy (* sy (+ r arm)))]] false)))))

(def card-flourishes
  "The decoration families a card frame can escalate through, weakest rank first.

   Each is a ladder of five: whatever a common gets, a legendary gets that and
   more, so the treatments compare against each other rather than merely differ.
   The eye should reach the rare items before it reads a word."
  [:nested :brackets :diamonds])

(defn- draw-flourish!
  "Draws `family`'s decoration for a rarity of `rank`, 1 to 5."
  [cs x y w h m c family rank]
  (case family
    :nested
    (do (when (>= rank 2)
          (.setLineWidth cs (float 0.45))
          (chamfered-frame! cs x y w h (+ m 0.042) (- c 0.042)))
        (when (>= rank 4)
          (.setLineWidth cs (float 0.35))
          (chamfered-frame! cs x y w h (+ m 0.075) (- c 0.075)))
        (when (>= rank 5)
          (.setLineWidth cs (float 1.9))
          (chamfered-frame! cs x y w h (- m 0.035) c)))

    :brackets
    (do (when (>= rank 2)
          (.setLineWidth cs (float 0.45))
          (chamfered-frame! cs x y w h (+ m 0.042) (- c 0.042)))
        (when (>= rank 3)
          (.setLineWidth cs (float 0.9))
          (corner-brackets! cs x y w h m 0.095 (if (>= rank 4) 0.20 0.12)))
        (when (>= rank 5)
          (.setLineWidth cs (float 0.9))
          (corner-brackets! cs x y w h m 0.155 0.11)))

    ;; Each step adds mass rather than restating the last one in another way:
    ;; a second rule, then cornerpieces, then reach and weight on them, then a
    ;; heavy outer border. Ranks 3, 4 and 5 have to separate at arm's length.
    :diamonds
    (do (when (>= rank 2)
          (.setLineWidth cs (float 0.45))
          (chamfered-frame! cs x y w h (+ m 0.042) (- c 0.042)))
        (when (= rank 3)
          (.setLineWidth cs (float 0.7))
          (corner-diamonds! cs x y w h m 0.105 0.036 false false))
        (when (>= rank 4)
          (.setLineWidth cs (float 0.9))
          (corner-diamonds! cs x y w h m 0.105 0.049 true true))
        ;; The legendary's mark goes at the FOOT. At the head it lands on the
        ;; rarity rail, which is the thing actually carrying the rank.
        (when (>= rank 5)
          (.setLineWidth cs (float 2.1))
          (chamfered-frame! cs x y w h (- m 0.036) c)
          (.setLineWidth cs (float 0.9))
          (diamond! cs x y (/ w 2) (- h m 0.052) 0.062 true)))
    nil))

(defn- draw-rarity-rail!
  "The rank marks on their own rule across the top of the card.

   Diamonds filled to the item's rank, centred, with a hairline running out to
   each side. On its own row rather than beside the name: at the name's shoulder
   the two compete, and neither reads first. Fanned through a deck the filled
   count sorts the cards, which setting the word in type does not do.

   :varies has no rank, so the rail is drawn plain -- an item whose rarity depends
   on the table is not ranked against one that does not."
  [cs x y w rarity dy]
  (let [rank (rarity-rank rarity)
        span 0.115
        half (/ (* span 4) 2)
        mid (/ w 2)]
    (.setLineWidth cs (float 0.5))
    (polyline! cs x y [[0.28 dy] [(- mid half 0.09) dy]] false)
    (polyline! cs x y [[(+ mid half 0.09) dy] [(- w 0.28) dy]] false)
    (.setLineWidth cs (float 0.6))
    (doseq [i (range 5)]
      (diamond! cs x y (+ (- mid half) (* i span)) dy 0.038 (< i (or rank 0))))
    (.setLineWidth cs (float 1))))

(defn- draw-foot-ornament!
  "A single diamond between two hairlines, closing the card the way the rarity
   rail opens it. Purely a frame: it carries nothing, which is why it is one mark
   and not five."
  [cs x y w dy]
  (let [mid (/ w 2)]
    (.setLineWidth cs (float 0.5))
    (polyline! cs x y [[0.5 dy] [(- mid 0.085) dy]] false)
    (polyline! cs x y [[(+ mid 0.085) dy] [(- w 0.5) dy]] false)
    (diamond! cs x y mid dy 0.032 false)
    (.setLineWidth cs (float 1))))

(defn- draw-card-frame!
  "The card's border, plus whatever decoration its rarity has earned.

   The border escalates as well as the rank marks do. The marks say which rarity
   to anyone who counts them; the frame is what carries across a table to someone
   who does not."
  [cs x y w h rarity family]
  (let [m 0.09
        c 0.17]
    (.setLineWidth cs (float 1.1))
    (chamfered-frame! cs x y w h m c)
    (when-let [rank (rarity-rank rarity)]
      (draw-flourish! cs x y w h m c family rank))
    (.setLineWidth cs (float 1))))

(def ^:private tickable-charges
  "The most circles a card can carry in one row and a hand can sensibly tick.
   Past this the tracker becomes a number to write instead."
  12)

(defn- draw-charge-track!
  "Somewhere to track charges, along the bottom of the card.

   Drawn only when the item's text names a number: empty circles on an item with
   nothing to spend are furniture, and the reason to print a card at all is that
   it is the thing you mark during play.

   Up to `tickable-charges` that is a circle each. Past it -- a Staff of the Magi
   has fifty -- it is a rule to write the remaining count on, over the total,
   because nobody ticks fifty boxes at a table. Capping the parse instead would
   have drawn nothing at all for exactly the items that most need tracking.

   Drawn at `cy`, in its own band under the header rather than at the foot. It is
   the one thing on the card anybody touches mid-game, and under the description
   it arrived last and cramped: name, then what the thing is, then what you have
   left to spend, then what it does."
  [cs x y w n cy]
  (.setLineWidth cs (float 0.8))
  (if (<= n tickable-charges)
    (let [r 0.052
          gap 0.145
          cx (- (/ w 2) (/ (* gap (dec n)) 2))]
      (draw-text cs "CHARGES" HELVETICA 5.5 (+ x (/ w 2) -0.19) (- 11 y cy -0.13))
      (doseq [i (range n)]
        (circle! cs x y (+ cx (* gap i)) cy r)))
    (let [total (str "/ " n)
          rule-w 0.62
          left (- (/ w 2) (/ rule-w 2) 0.16)]
      (draw-text cs "CHARGES" HELVETICA 5.5 (+ x (/ w 2) -0.19) (- 11 y cy -0.155))
      (polyline! cs x y [[left cy] [(+ left rule-w) cy]] false)
      (draw-text cs total HELVETICA 7 (+ x left rule-w 0.05) (- 11 y cy -0.015))))
  (.setLineWidth cs (float 1)))

(defn print-items
  "Draws one page of magic item cards, and returns what did not fit for the backs.

   Same grid, box and overflow handling as print-spells, and everything drawn here
   is vector: a chamfered frame, rarity diamonds, a rule under the header and a
   charge track. Nothing is rasterised, so the cards stay sharp at any size and
   cost the file almost nothing.

   The layout differs from a blank card template on purpose. A template spends its
   room on labelled slots to write into; this card already knows the name, the
   kind, the rarity and the attunement, so that room goes to the description --
   the part a player actually rereads at the table. Attunement sits at the foot,
   out of the header, and only when the item needs it.

   `opts` selects the look, so alternatives can be rendered side by side rather
   than argued about: `:flourish` is one of card-flourishes, `:name-face` a key
   into `fonts` or a font, `:name-size` points, `:name-tracking` extra spacing
   between letters."
  ([cs document fonts img box-width box-height items page-number bw? bw-faded?]
   (print-items cs document fonts img box-width box-height items page-number
                bw? bw-faded? nil))
  ([cs document fonts img box-width box-height items page-number bw? bw-faded? opts]
  (let [{:keys [flourish name-size name-tracking]
         :or {flourish :diamonds name-size 12 name-tracking 0.15}} opts
        name-face (fn [fs] (let [f (:name-face opts :bold)]
                             (if (keyword? f) (get fs f) f)))
        num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        margin-x (/ (- 8.5 (* num-boxes-x box-width)) 2)
        margin-y (/ (- 11.0 (* num-boxes-y box-height)) 2)]
    (draw-grid cs box-width box-height)
    (draw-text cs (str "Page " (inc page-number)) (:italic fonts) 8 0.12 (- 11 0.15))
    (doall
     (for [j (range num-boxes-y)
           i (range (dec num-boxes-x) -1 -1)
           :let [item-index (+ i (* j num-boxes-x))]]
       (when-let [item (get (vec items) item-index)]
         (let [x (+ margin-x (* box-width i))
               y (+ margin-y (* box-height j))
               item-name (or (:name item) (::mi/name item) "(Unknown Item)")
               {:keys [::mi/description ::mi/summary ::mi/page ::mi/source
                       ::mi/rarity ::mi/attunement ::mi/attunement-details]} item
               body (or description
                        (when summary
                          (str summary " (see "
                               (if source (s/upper-case (name source)) "DMG")
                               " " page " for more details)"))
                        "")
               clause (attunement-phrase attunement attunement-details)
               charges (item-charges description)
               ;; The body stops short of the foot when something is drawn there.
               ;; The clause gets two lines' worth: the longest -- a bard, cleric,
               ;; druid, sorcerer, warlock, or wizard -- is 2.7in against 2.1in of
               ;; card, and shrinking it to fit one line lands at 5pt.
               body-bottom (if clause 0.58 0.32)
               ;; The charge band sits between the header rule and the body, with
               ;; a hairline under it, so the description starts lower when there
               ;; is one and reclaims the room when there is not.
               body-top (if charges 1.72 1.24)]
           (draw-card-frame! cs x y box-width box-height rarity flourish)
           (draw-rarity-rail! cs x y box-width rarity 0.245)
           (draw-foot-ornament! cs x y box-width (- box-height 0.185))
           ;; The name sits below the rail, clear of it and of the cut corner, and
           ;; runs two lines: a third of the items are longer than one at this
           ;; size, and a clipped name is a card you cannot find in a stack.
           ;; The name block always reserves two lines, so the rule sits at the
           ;; same height on every card and a stack cuts square. A one-line name
           ;; is dropped into the middle of that block rather than left sitting on
           ;; top of an empty line.
           (let [face (name-face fonts)
                 name-lines (count (split-lines item-name face name-size (- box-width 0.4)))]
             (.setCharacterSpacing cs (float name-tracking))
             (draw-text-to-box cs item-name face name-size
                               (+ x 0.2) (- 11.0 y (if (> name-lines 1) 0.38 0.475))
                               (- box-width 0.4) 0.52)
             (.setCharacterSpacing cs (float 0)))
           (draw-text-to-box cs (magic-item-subtitle item) (:italic fonts) 7.5
                             (+ x 0.2) (- 11.0 y 0.96) (- box-width 0.4) 0.2)
           ;; A rule and a hairline under it: the header carries two kinds of
           ;; information, so it closes with more than the body's plain divisions.
           (.setLineWidth cs (float 0.9))
           (polyline! cs x y [[0.2 1.13] [(- box-width 0.2) 1.13]] false)
           (.setLineWidth cs (float 0.35))
           (polyline! cs x y [[0.2 1.165] [(- box-width 0.2) 1.165]] false)
           (.setLineWidth cs (float 1))
           (when charges
             (draw-charge-track! cs x y box-width charges 1.47)
             (.setLineWidth cs (float 0.35))
             (polyline! cs x y [[0.2 1.62] [(- box-width 0.2) 1.62]] false)
             (.setLineWidth cs (float 1)))
           (let [remaining-desc-lines
                 (draw-text-to-box cs body (:plain fonts) 8
                                   (+ x 0.2) (- 11.0 y body-top)
                                   (- box-width 0.4)
                                   (- box-height body-top body-bottom))]
             (when clause
               (draw-text-to-box cs clause (:italic fonts) 6.8
                                 (+ x 0.2) (- 11.0 y (- box-height 0.50))
                                 (- box-width 0.4) 0.24))
             (when (seq remaining-desc-lines)
               (let [recharge (img (str "public/image/clockwise-rotation" (when bw? "-bw") ".png"))]
                 (if (and bw? bw-faded?)
                   (draw-imagex-alpha cs recharge (+ x (- box-width 0.32)) (+ y (- box-height 0.32)) 0.13 0.13 0.4)
                   (draw-imagex cs recharge (+ x (- box-width 0.32)) (+ y (- box-height 0.32)) 0.13 0.13))))
             {:remaining-lines remaining-desc-lines
              :spell-name item-name}))))))))

#_{:clj-kondo/ignore [:unused-private-var]}
(defn- create-monsters-pdf
  "Development/testing function that generates a sample monster stat block PDF.
   
   This function is not used in production - it's a utility for testing PDF
   generation during development. The output is saved to a temporary file.
   
   Returns: The temp file path where the PDF was saved."
  []
  (let [page (PDPage.)
        doc (PDDocument.)]
    (.addPage doc page)
    (with-open [cs (PDPageContentStream. doc page)]
      (let [h (/ 11.0 5)]
        (doseq [y (range h 11.0 h)]
          (draw-line-in cs 0.0 y 8.5 y))
        (let [monsters (vec (take 5 monsters/monsters))]
          (doseq [i (range 0 5)]
            (let [monster (monsters i)]
              (draw-text-from-top cs
                                  (:name monster)
                                  HELVETICA_BOLD
                                  14
                                  0.1
                                  (+ (* i h) 0.25))
              (draw-text-from-top cs
                                  (monsters/monster-subheader monster)
                                  HELVETICA_OBLIQUE
                                  12
                                  0.1
                                  (+ (* i h) 0.45))
              (doseq [j (range 0 6)]
                (let [ability ([:str :dex :con :int :wis :cha] j)
                      x (+ 0.15 (* 0.65 j))]
                  (draw-text-from-top cs
                                      (name ability)
                                      HELVETICA_BOLD
                                      10
                                      x
                                      (+ (* i h) 0.7))
                  (draw-text-from-top cs
                                      (str (ability monster)
                                           " ("
                                           (options/ability-bonus-str (ability monster))
                                           ")")
                                      HELVETICA
                                      12
                                      x
                                      (+ (* i h) 0.85))))
              (draw-text-from-top cs
                                  "Saving Throws"
                                  HELVETICA_BOLD
                                  10
                                  0.1
                                  (+ (* i h) 1.1))
              (draw-text-from-top cs
                                  (common/print-bonus-map (:saving-throws monster))
                                  HELVETICA
                                  10
                                  (+ 0.1 (string-width
                                          "Saving Throws "
                                          HELVETICA_BOLD
                                          10))
                                  (+ (* i h) 1.1))
              (draw-text-from-top cs
                                  "Skills"
                                  HELVETICA_BOLD
                                  10
                                  0.1
                                  (+ (* i h) 1.3))
              (draw-text-from-top cs
                                  (common/print-bonus-map (:skills monster))
                                  HELVETICA
                                  10
                                  (+ 0.1 (string-width
                                          "Skills "
                                          HELVETICA_BOLD
                                          10))
                                  (+ (* i h) 1.3)))))))
    ;; Save to a cross-platform temp file instead of a hardcoded path.
    ;; java.io.File/createTempFile creates a file in the system temp directory
    ;; and returns a File object that PDDocument.save() accepts.
    (.save doc (java.io.File/createTempFile "monsters" ".pdf"))))