(ns orcpub.pdf
  "Fills the AcroForm character sheet templates in resources/. Cards live in
   orcpub.pdf.cards, shared drawing in orcpub.pdf.draw.

   Text fields auto-size (`/Helv 0 Tf`): PDFBox shrinks text to 4pt, then clips;
   `fit-text` splits text at `min-font-size` instead. Fields sharing a name share
   one value, so repeated pages need unique names."
  (:require [clojure.string :as s]
            [clojure.stacktrace :as strace]
            [clojure.java.io :as io]
            [orcpub.common :as common]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.display :as dis5e]
            [orcpub.dnd.e5.spell-print :as sp]
            [orcpub.pdf.draw :as draw]
            [orcpub.dnd.e5.options :as options]
            [clj-http.client :as client])
  (:import (java.io ByteArrayOutputStream)
           (java.util.zip Deflater DeflaterOutputStream)
           (org.apache.pdfbox.pdmodel.graphics.image PDImageXObject)
           (org.apache.pdfbox.pdmodel.interactive.form PDCheckBox PDTextField PDTerminalField)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget)
           (org.apache.pdfbox.pdmodel.common PDRectangle)
           (org.apache.pdfbox.cos COSName COSDictionary)
           (org.apache.pdfbox.pdmodel PDPage PDPageContentStream PDResources)
           ;; APPEND appends operators to a page's existing content stream, so
           ;; template artwork survives. The alternative overwrites it.
           (org.apache.pdfbox.pdmodel PDPageContentStream$AppendMode)
           (org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory)
           (org.apache.http.conn DnsResolver)
           (org.apache.http.impl.conn BasicHttpClientConnectionManager)
           (org.apache.http.config RegistryBuilder)
           (org.apache.http.conn.socket PlainConnectionSocketFactory)
           (org.apache.http.conn.ssl SSLConnectionSocketFactory)
           (java.net UnknownHostException)
           (org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName)
           (org.apache.pdfbox.text PDFTextStripper)
           (javax.imageio ImageIO IIOImage ImageWriteParam)
           (javax.imageio.stream MemoryCacheImageOutputStream)
           (java.awt RenderingHints)
           (java.awt.image BufferedImage)
           (java.net URL)))

;; THE ACTUAL API: 46 public fns and 8 public defs, against 39 private fns and 22 private
;; defs. Production calls 16 of them from outside: 15 in routes.clj (export entry points,
;; spell-page and image helpers) and report-image-egress! in system.clj. spellbook.clj no
;; longer calls anything here -- it goes to pdf.draw.

;; safe-image-url? and safe-image-bytes are named in routes.clj comments only; their real
;; external consumers are the tests.

;; The rest are public by `defn` default, not by design. Tests reach 46 names and dev/
;; scripts 25, but that is tooling, not a contract to preserve.

;; UNITS: this file is INCHES and converts at the drawing call; spellbook.clj is POINTS.
;; Images take y from the page TOP (via in-to-coord-y), text from the BOTTOM. All 31 of the
;; hand-written `(- 11 y ...)` flips left with the cards; in-to-coord-y is the one left here
;; and it reads its page height from draw/page-size.

;; Pageless widgets came with the templates; dev/prepare_templates.clj has removed them
;; from resources/. Only add-missing-spell-pages! prunes at export time, before it
;; generates pages, in case it gets an unbaked template. widget-box ignores them.

;; Field names do not describe their contents (pdf_spec/equipment-fields and
;; traits-fields build them): features-and-traits = equipped items; features-and-traits-2
;; = features, actions, reactions; treasure = unequipped items and valuables;
;; equipment = unused (pdf_spec emits no such key); cha = modifier; cha-mod = score.

;; The Base 14 fonts, present in every PDF reader. Constructed once at load time.
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
   GOTCHA: no /AP. An appearance stream is a shared COS object, so a copied /AP
   would make writing one class's spells rewrite the other's page; write-fields!
   generates a fresh appearance from the value instead."
  [COSName/RECT COSName/DA COSName/MK COSName/F COSName/FT])

(defn add-spell-pages!
  "Copies the page for class `from` once per entry in `to-suffixes`, renaming
   every field to that suffix. Returns the number of pages added.
   Same result as add-spell-page! in a loop, but finds the source page's fields
   once and rebuilds the form's field list once, not once per clone."
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
      ;; Both branches insert. PDPageTree.add walks the whole object graph for a
      ;; cycle and overflows the stack on these deeply nested masters (styles 3 and
      ;; 4, whose spell page is last, at two or more casters). insertAfter does not.
      (if anchor-page
        (doseq [[page _] made] (.insertBefore pages page anchor-page))
        (reduce (fn [prev [page _]] (.insertAfter pages page prev) page) template made))
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
;; Each style has one ONE-spell-page master, grown here by cloning (dev/prepare_templates.clj
;; and dev/style4_one_spell_page.clj built them). Never trim a wider file instead:
;; removing a page frees none of the resources it shared, so every export grows.

;; :site-line GOTCHA: PDFTextStripper reports text, not artwork, and a 150 dpi render
;; hides the footer bars, so measure at 300. Styles 1-2 sit at 0.13 to clear a solid bar
;; (0.03in under the frame); 3-4 at 0.06, where their artwork stops. Whether a style
;; takes a packed layout is packing-supported? (via numeral-boxes), not a key here.

(def sheet-masters
  "The file each style grows from. :marks is what the style's pages do: a footer in
   a page's content stream spreads by cloning and cannot be removed. :without-casters
   is the no-spell-page file for non-casters, so pages are only ever added (and style
   4's licence line, on its spell page, is kept). :prints-site-line? = the artwork names
   the site (style 4), so stamp-site-line! skips it. :site-line is where that line goes,
   [x y] inches from bottom-left, MEASURED by dev/scan_site_line.clj and held by a test."
  {1 {:file "fillable-char-sheetstyle-1-1-spells.pdf" :marks :all
      :without-casters "fillable-char-sheetstyle-1-0-spells.pdf"
      :site-line [0.95 0.13]}
   2 {:file "fillable-char-sheetstyle-2-1-spells.pdf" :marks :all
      :without-casters "fillable-char-sheetstyle-2-0-spells.pdf"
      :site-line [0.95 0.13]}
   3 {:file "fillable-char-sheetstyle-3-1-spells.pdf" :marks :none
      :without-casters "fillable-char-sheetstyle-3-0-spells.pdf"
      :site-line [0.95 0.06]}
   4 {:file "fillable-char-sheetstyle-4-1-spells.pdf" :marks :all
      :without-casters "fillable-char-sheetstyle-4-0-spells.pdf"
      :site-line [0.95 0.06]
      :prints-site-line? true}})

(def unsupported-fields
  "Values a style's sheet has nowhere to print, by style. Style 4 (Cthulhu Mythos)
   has \"Conditions and Insanities\" where others have inspiration; its backstory
   and allies go to Notes (see merged-fields). A test asserts the values
   write-fields! cannot place are exactly this set, so adding a field to a template
   means deleting its name here."
  {4 #{"inspiration"}})

(def merged-fields
  "Values a style prints together in one box, by style: target field -> the
   headed sections that go into it, in order. Style 4 has no allies or backstory
   box, so both go into its Notes box under headings rather than being dropped.
   GOTCHA: Notes is smaller than style 1's two boxes, so a long backstory shrinks
   and a very long one clips at the 4pt floor."
  {4 {:Notes [["BACKSTORY" :backstory]
              ["ALLIES & ORGANIZATIONS" :allies]]}})

(defn merge-style-fields
  "Folds the values `style` has no box of its own for into the box it shares.

   Runs before spill-overflow! so the merged text is measured and shrunk as one
   value. A section with nothing in it contributes no heading, and a target whose
   sections are all empty is not written at all, so the box stays blank rather
   than printing bare headings."
  [style fields]
  (reduce
   (fn [acc [target sections]]
     (let [parts (for [[heading k] sections
                       :let [v (get acc k)]
                       :when (not (s/blank? (str v)))]
                   (str heading "\n" v))]
       (cond-> (apply dissoc acc (map second sections))
         (seq parts) (assoc target (s/join "\n\n" parts)))))
   fields
   (get merged-fields style)))

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

(defn renumber-page-section!
  "Renames every field on `page` from spellcasting section `from` to `to`.

   Public because the only caller is dev/style4_one_spell_page.clj, which reaches
   it through the var. It sits with fields-on-page as a template-editing utility."
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
   Every master holds one spell page, so this only clones it. `marks` (read from
   sheet-masters with :file) is ignored: a footer lives in the page's content
   stream, so every clone carries it."
  [doc wanted _marks]
  ;; A character who casts nothing is opened from :without-casters, which has no
  ;; spell page to grow. Answering that before the let keeps the form untouched:
  ;; spell-sections walks every page's annotations, and there is nothing to find.
  (if (zero? wanted)
    0
    (let [[first-n _] (first (spell-sections doc))]
      (add-spell-pages! doc first-n (range 2 (inc wanted))))))

(defn- highest-spell-page
  "The largest N for which the document has a spellcasting-class-N page. 0 when
   the template has no spell pages, as on the non-caster sheet."
  [doc]
  (reduce (fn [best n] (if (spell-page-for-suffix doc n) n best))
          0
          (range 1 21)))

(defn add-missing-spell-pages!
  "Appends a spellcasting page for every class in `fields` beyond those the
   template carries (pdf_spec emits one per class, unbounded), pruning orphan
   widgets first so new pages do not share names with a stale template's ghosts.
   Returns the number of pages added. GOTCHA: pass `max-sections` for request data;
   the count comes from field NAMES, so \"spellcasting-class-9999\" would clone
   thousands of pages."
  ([doc fields] (add-missing-spell-pages! doc fields Integer/MAX_VALUE))
  ([doc fields max-sections]
  (let [wanted (->> (keys fields)
                    (keep #(second (re-matches #"spellcasting-class-(\d+)" (name %))))
                    (keep #(try (Integer/parseInt %) (catch Exception _ nil)))
                    (reduce max 0)
                    (min max-sections))
        source (when (pos? wanted) (highest-spell-page doc))]
    (if (or (nil? source) (zero? source) (<= wanted source))
      0
      (do (prune-orphan-widgets! doc)
          (add-spell-pages! doc source (range (inc source) (inc wanted))))))))

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
  "Points every checkbox widget that draws the same thing (matched on what the
   appearance draws, not box size) at one shared appearance dictionary. Returns
   the number of widgets redirected.
   GOTCHA: checkboxes only. A checkbox's state selects an entry and leaves the
   stream alone; PDFBox rewrites a text field's stream in place when its value
   changes, so a shared one would edit both fields."
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
   `bpp` is bytes per pixel, so the left neighbour is that many bytes back."
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
  "Re-encodes each 8-bit Flate image on the pages that has no PNG predictor,
   adding a Paeth one; the pixels are unchanged. Returns the number of images
   shrunk. Keeps the old encoding when the new one is not smaller or the pixel
   count does not match. Throws ex-info if a re-encoding does not decode back to
   the original pixels."
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
  "Points every page at a single copy of each image the document carries more
   than once, matched on the raw encoded bytes (a re-encoding of the same picture
   is left alone). Returns the number of references redirected. Safe because an
   image XObject is immutable, unlike a text-field appearance."
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
   page, named <name>-pN. Returns the number of fields added. One field is one
   value, so otherwise a shared checkbox ticks on two classes' pages at once.
   GOTCHA: run before the naming passes. They skip a field once it is named, so a
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
   sits beside (same baseline, just to its right), so SlotsRemaining 19 becomes
   slots-expended-1-1 next to spell-slots-1-1. Returns the number renamed. Nothing
   writes these; the player fills them in after download. Run before
   disambiguate-duplicate-fields! so split copies get a level, not a numeric suffix."
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
   one sits beside (about 8pt left, same baseline), so Check Box 25 becomes
   prepared-1-1-1 next to spells-1-1-1. Returns the number renamed. Rows with no
   box (cantrips) are left alone; pdf_spec writes none of these names. Run before
   disambiguate-duplicate-fields! so these get a meaningful name, not a number."
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
   take a name from. Returns the number renamed. Applies only when exactly six
   anonymous checkboxes remain on a page in two rows of three: successes above
   failures, each row left to right. Any other arrangement is left alone."
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
  "Gives each field its own name where several share one: the first in each group
   keeps the name, the rest take a numeric suffix. Returns the number renamed.
   Same-named fields are ONE field with one value, so a prepared tick would show on
   every class's page. Safe for export: pdf_spec writes none of the duplicated names."
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
   GOTCHA: PDFBox sizes a single-line field by HEIGHT alone (8pt on the skill and
   save boxes), so without this a modifier of +10 or worse loses its last character."
  [field widget value]
  (let [rect (.getRectangle widget)
        ;; The generated appearance clips at "1 1 w h re", so 1pt each side.
        available (- (.getWidth rect) 2.0)
        size (baked-font-size widget)
        width (when size (* 72.0 (draw/string-width value draw/HELVETICA size)))]
    (when (and size width (> width available))
      ;; 2% under the exact fit: sizing to the millimetre lands a hair over once
      ;; the size is rounded into the appearance stream.
      (let [fitted (max min-single-line-size (* size 0.98 (/ available width)))]
        (.setDefaultAppearance field (str "/Helv " fitted " Tf 0 g"))
        (.setValue field value)
        fitted))))

(defn write-fields!
  "Populates the AcroForm in `doc` from `fields` {field-name-keyword value}: checkboxes
   take truthy/falsey, text is normalized to WinAnsi. Truthy `flatten?` bakes the values
   into page content and removes the form; `font-sizes` {field-name-keyword pt} applies
   ONLY then. Bakes real appearance streams, as Firefox's print path ignores
   NeedAppearances. Returns the sorted names the template has no field for, and logs
   them; those values are dropped rather than thrown."
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
    ;; One index rather than a lookup per value: PDAcroForm.getField walks the
    ;; field tree on every call, which dominated the export's allocation.
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
            (let [text (when (instance? PDTextField field) (draw/normalize-text v))]
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

(defn in-to-sz
  "Inches to PDF points. `draw/in->pt` under this file's older name, kept because 48
   call sites use it. `inches-to-units` is the same function under a THIRD name."
  [inches]
  (draw/in->pt inches))

(defn in-to-coord-x
  "Inches from the page LEFT edge to a PDF x coordinate. Just `in-to-sz`: x needs no
   flip because both spaces run left to right. It exists so call sites can name both
   axes the same way."
  [inches]
  (draw/in->pt inches))

(defn in-to-coord-y
  "Inches from the page TOP to a PDF y coordinate, which measures from the bottom.
   The page height is no longer inline here -- `draw/pt-from-top` holds it, and
   spellbook.clj flips through the same function.

   Images go through here and so take `y` from the top; the text functions multiply by
   72 directly and take it from the bottom. That split is unchanged."
  [inches]
  (draw/pt-from-top 72 inches))

(defn scale
  "`draw/fit` under this file's older name. Kept for its call sites."
  [rect img]
  (draw/fit rect img))

(def user-agent "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.172")

(def ^:private max-embedded-bytes
  "What a picture may weigh once it is IN the PDF. The 128k the character builder
   has always advertised next to the Image URL field."
  (* 128 1024))

(def ^:private max-image-bytes
  "What the server is willing to DOWNLOAD, which is a different question.

   Not the same number as max-embedded-bytes, and holding them equal is the defect
   this split fixed: a 393 KB portrait a host served without complaint was refused
   for weight. What bounds the danger is the pixel budget below, which caps the
   decode, and the transfer deadline, which caps the time."
  (* 2 1024 1024))

(def ^:private max-image-pixels
  "Width x height ceiling, checked from the header BEFORE any pixels are
   decoded.

   A byte cap alone does not bound memory: a 69-byte PNG can declare
   25000x25000 in its IHDR, and ImageIO/read would allocate 2.5GB honouring it.
   Reading dimensions costs only the header."
  (* 2000 2000))

(defn- reserved-v4?
  "IPv4 blocks that are not routable public internet, beyond the ones
   InetAddress already has a predicate for."
  [o0 o1]
  (or (zero? o0)                        ; 0.0.0.0/8, "this network"
      (and (= 100 o0) (<= 64 o1 127))   ; 100.64.0.0/10, carrier-grade NAT
      (and (= 192 o0) (zero? o1))       ; 192.0.0.0/24 IETF, 192.0.2.0/24 TEST-NET
      (and (= 198 o0) (<= 18 o1 19))    ; 198.18.0.0/15, benchmarking
      (>= o0 240)))                     ; 240.0.0.0/4 reserved, incl. broadcast

(defn- embedded-v4
  "The IPv4 address carried inside an IPv6 one, or nil.

   Two transition mechanisms wrap a v4 address in a v6 one: 64:ff9b::/96 (NAT64)
   and 2002::/16 (6to4). On a network running either, 64:ff9b::7f00:1 and
   2002:7f00:1:: both reach 127.0.0.1, so the wrapper has to come off before the
   address can be judged."
  [^bytes b]
  (let [o (fn [i] (bit-and (aget b i) 0xff))
        v4 (fn [from] (java.net.InetAddress/getByAddress
                       (byte-array (map #(aget b %) (range from (+ from 4))))))]
    (cond
      (and (= 0 (o 0)) (= 0x64 (o 1)) (= 0xff (o 2)) (= 0x9b (o 3))
           (every? zero? (map o (range 4 12))))
      (v4 12)

      (and (= 0x20 (o 0)) (= 0x02 (o 1)))
      (v4 2))))

(defn- private-address?
  "Addresses no user-supplied URL has any business reaching: InetAddress's
   loopback, any-local, link-local, site-local and multicast predicates, plus three
   it lacks: fc00::/7 unique local (isSiteLocalAddress knows only fec0::/10), the
   reserved IPv4 blocks in reserved-v4?, and the v4-in-v6 wrappers embedded-v4
   unpacks."
  [^java.net.InetAddress addr]
  (let [b (.getAddress addr)]
    (or (.isLoopbackAddress addr)
        (.isAnyLocalAddress addr)
        (.isLinkLocalAddress addr)     ; 169.254/16 — cloud instance metadata
        (.isSiteLocalAddress addr)     ; 10/8, 172.16/12, 192.168/16, fec0::/10
        (.isMulticastAddress addr)
        (case (alength b)
          4 (reserved-v4? (bit-and (aget b 0) 0xff) (bit-and (aget b 1) 0xff))
          16 (or (= 0xfc (bit-and (bit-and (aget b 0) 0xff) 0xfe)) ; fc00::/7
                 (boolean (some-> (embedded-v4 b) private-address?)))
          false))))

(defn validated-addresses
  "Every address `url`'s host resolves to, or nil if the URL may not be fetched.

   This is the ONE resolution the fetch is allowed. Handing the answer to the
   connection is what makes the check meaningful -- see pinned-connection-manager."
  [url]
  (try
    (let [u (URL. url)]
      (when (contains? #{"http" "https"} (.getProtocol u))
        (let [addrs (seq (java.net.InetAddress/getAllByName (.getHost u)))]
          ;; every resolved address, not just the first: a hostname can answer
          ;; with both a public and a private address.
          (when (and addrs (not-any? private-address? addrs))
            (vec addrs)))))
    (catch Exception _ nil)))

(defn safe-image-url?
  "Whether the server may fetch this URL: http(s) only, with no resolved address
   private (see validated-addresses). Guards against fetches of cloud instance
   metadata or local files, where the fetch's outcome alone leaks. Returns false
   rather than throwing, so a bad URL is skipped like an image that failed to load."
  [url]
  (some? (validated-addresses url)))

(defn- pinned-connection-manager
  "A connection manager that resolves `host` to `addrs` and nothing else, so the
   connection goes to the address validated-addresses checked. Any other host is
   recorded in `refused` and throws UnknownHostException.
   GOTCHA: the resolver must go on the MANAGER (clj-http always sets one, which
   overrides HttpClientBuilder's setDnsResolver), and the hostname must stay in the
   URL so the socket factories still check certificate and hostname."
  ^BasicHttpClientConnectionManager [host addrs refused]
  (let [pinned (into-array java.net.InetAddress addrs)]
    (BasicHttpClientConnectionManager.
     (-> (RegistryBuilder/create)
         (.register "http" (PlainConnectionSocketFactory/getSocketFactory))
         (.register "https" (SSLConnectionSocketFactory/getSocketFactory))
         (.build))
     nil nil
     (reify DnsResolver
       (resolve [_ h]
         (if (= h host)
           pinned
           ;; Redirects are refused, so no other host should ever be routed here.
           ;; Recorded rather than only thrown: DnsResolver must throw
           ;; UnknownHostException, which is indistinguishable from ordinary DNS
           ;; failure by the time clj-http has wrapped it.
           (do (reset! refused h)
               (throw (UnknownHostException. (str "not the pinned host: " h))))))))))

(defn- proxied?
  "Whether the JVM's proxy settings route this URL through a proxy, read from the
   same ProxySelector as clj-http's route planner so the two agree. Behind a proxy
   the client resolves the PROXY's host, so a pin on the target would refuse every
   fetch, and the proxy does the resolving anyway. False on any error."
  [url]
  (boolean
   (try
     ;; Built from the parts rather than the raw string: ProxySelector only reads
     ;; scheme, host and port, and this constructor escapes, so a URL that URI's
     ;; string constructor would reject cannot make this throw.
     (let [u (URL. url)
           uri (java.net.URI. (.getProtocol u) nil (.getHost u) (.getPort u) nil nil nil)]
       (some #(not= java.net.Proxy$Type/DIRECT (.type ^java.net.Proxy %))
             (.select (java.net.ProxySelector/getDefault) uri)))
     (catch Exception _ false))))

(defn image-egress-status
  "How image fetches will leave this host: {:pinning? bool :proxy str-or-nil}.

   Probes a representative external https URL, so it reflects nonProxyHosts."
  []
  (let [probe "https://example.com/probe.png"
        via (first (remove #(= java.net.Proxy$Type/DIRECT (.type ^java.net.Proxy %))
                           (try (.select (java.net.ProxySelector/getDefault)
                                         (java.net.URI. probe))
                                (catch Exception _ nil))))]
    {:pinning? (not (proxied? probe))
     :proxy (some-> via str)}))

(defn report-image-egress!
  "Prints how image fetches leave this host, once, at boot.

   Which of the two paths is live is otherwise invisible until an export fails,
   and the two fail very differently."
  []
  (let [{:keys [pinning?] :as status} (image-egress-status)]
    (println (if pinning?
               "pdf/image-fetch: DNS pinning ACTIVE (no proxy configured for external https)"
               (str "pdf/image-fetch: DNS pinning OFF -- " (:proxy status)
                    " will resolve and fetch; it is the egress control point")))))

(def ^:private pin-mismatch-reported
  "The explanation below is printed once, not once per export."
  (atom false))

(defn- report-pin-mismatch!
  "Says what a pin mismatch means, because it means EVERY image fetch is failing.

   Reached when the client asked to resolve a host the fetch was not pinned to,
   which means a proxy is routing the connection but ProxySelector did not report
   one -- so proxied? stepped aside where it should not have."
  [host]
  (when (compare-and-set! pin-mismatch-reported false true)
    (println (str "pdf/image-fetch: EVERY character image will fail to load. The "
                  "connection tried to resolve \"" host "\", which is not the host "
                  "the fetch was pinned to -- something is routing it elsewhere "
                  "while the JVM reports no proxy. Set the standard proxy "
                  "properties so it is detected. See docs/CHARACTER-IMAGE-FETCH.md"))))

(defn- open-image-stream
  "Opens `url` against `addrs`, with redirects disabled and the size bounded.
   Returns [stream connection-manager-or-nil]; the caller closes both. Throws
   ex-info on a non-2xx status or a Content-Length over max-image-bytes. Redirects
   are off because a permitted host's open redirect would reach a private address;
   using `addrs` rather than resolving again closes the check-to-connect gap."
  [url addrs]
  (let [refused (atom nil)
        cm (when-not (proxied? url)
             (pinned-connection-manager (.getHost (URL. url)) addrs refused))]
    (try
      (let [resp (client/get url (cond-> {:as :stream
                                          :redirect-strategy :none
                                          :throw-exceptions false
                                          :decompress-body false
                                          :socket-timeout 10000
                                          :connection-timeout 10000
                                          :headers {"User-Agent" user-agent}}
                                   cm (assoc :connection-manager cm)))
            status (:status resp)]
        (when-not (<= 200 status 299)
          (some-> ^java.io.InputStream (:body resp) .close)
          (throw (ex-info (str "Image URL returned " status)
                          {:error :image-load-failed :url url :status status})))
        (let [len (some-> (get-in resp [:headers "content-length"]) Long/parseLong)]
          (when (and len (> len max-image-bytes))
            (some-> ^java.io.InputStream (:body resp) .close)
            (throw (ex-info "Image is larger than the download limit"
                            {:error :image-too-large :url url :bytes len}))))
        [(:body resp) cm])
      (catch Exception e
        (some-> cm .close)
        (if-let [host @refused]
          (do (report-pin-mismatch! host)
              (throw (ex-info "Image fetch was routed to a host it was not pinned to"
                              {:error :image-pin-mismatch :url url :routed-to host} e)))
          (throw e))))))

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

(def ^:private image-transfer-deadline-ms
  "Total wall clock allowed for pulling one image body, in ms. setReadTimeout
   bounds each READ, not the transfer, so a server trickling a byte per timeout
   would otherwise hold the connection, and an export slot, for timeout x reads."
  20000)

(defn- read-bounded-bytes
  "All of the stream, refusing to exceed max-image-bytes or the deadline.

   The clock is checked before each read, so the true ceiling is the deadline plus
   one read timeout; that is bounded, which is the property that matters."
  ([in] (read-bounded-bytes in image-transfer-deadline-ms))
  ([^java.io.InputStream in deadline-ms]
   (let [out (java.io.ByteArrayOutputStream.)
         buf (byte-array 8192)
         deadline (+ (System/currentTimeMillis) deadline-ms)]
     (loop [total 0]
       (when (> (System/currentTimeMillis) deadline)
         (throw (ex-info "Image took too long to transfer"
                         {:error :image-transfer-timeout :bytes total})))
       (let [n (.read in buf)]
         (cond
           (neg? n) (.toByteArray out)
           (> (+ total n) max-image-bytes)
           (throw (ex-info "Image is larger than the download limit"
                           {:error :image-too-large :bytes (+ total n)}))
           :else (do (.write out buf 0 n) (recur (+ total n)))))))))

(defn safe-image-bytes
  "Fetch url and return its bytes, or throw. Every limit is applied before any
   pixel buffer is allocated."
  [url]
  (let [addrs (or (validated-addresses url)
                  (throw (ex-info "Image URL is not permitted"
                                  {:error :image-url-not-permitted :url url})))
        [in cm] (open-image-stream url addrs)
        data (try (read-bounded-bytes in)
                  (finally (.close ^java.io.InputStream in) (some-> cm .close)))]
    (when-not (within-pixel-budget? data)
      (throw (ex-info "Image dimensions exceed the limit"
                      {:error :image-too-large-dimensions :url url})))
    data))

(defn draw-non-jpg
  "Fetches a non-JPEG image from `url` and draws it, re-encoded losslessly (PNG and
   anything else ImageIO reads). Box arguments are `draw-imagex`'s. Opens its own
   content stream, so it cannot interleave with other drawing on the page. THROWS
   ex-info on failure -- `:image-load-timeout`, `:unknown-host`,
   `:invalid-image-format`, `:image-load-failed`; `draw-image!` is the quiet path."
  [doc page url x y width height]
  (try
    (with-open [c-stream (draw/content-stream doc page)]
      (let [buff-image (ImageIO/read (java.io.ByteArrayInputStream.
                                      (safe-image-bytes url)))]
        (when (nil? buff-image)
          (throw (ex-info "Unable to read image from URL"
                          {:error :invalid-image-format
                           :url url})))
        (let [img (LosslessFactory/createFromImage doc buff-image)]
          (draw/draw-imagex c-stream img x y width height))))
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

(defn draw-jpg
  "`draw-non-jpg` for JPEGs: embeds the fetched bytes directly rather than
   re-encoding, so the file stays small and the image lossless-to-source.
   Same box arguments, same throwing contract (`:jpeg-load-failed`)."
  [doc page url x y width height]
  (try
    (with-open [c-stream (draw/content-stream doc page)
                image-stream (java.io.ByteArrayInputStream. (safe-image-bytes url))]
      (let [img (JPEGFactory/createFromStream doc image-stream)]
        (draw/draw-imagex c-stream img x y width height)))
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

(defn draw-image-bytes!
  "Draws already-fetched image `data` at `x`,`y`, so an export can fetch all its
   images before drawing any. `jpg?` true embeds the bytes as they are; anything
   else is decoded and re-encoded losslessly, the only way PDFBox takes it. Logs a
   failure and returns nil rather than throwing."
  [doc page data jpg? x y width height]
  (try
    (with-open [c-stream (draw/content-stream doc page)]
      (if jpg?
        (with-open [in (java.io.ByteArrayInputStream. data)]
          (draw/draw-imagex c-stream (JPEGFactory/createFromStream doc in) x y width height))
        (let [buff (ImageIO/read (java.io.ByteArrayInputStream. data))]
          (when (nil? buff)
            (throw (ex-info "Unable to read image" {:error :invalid-image-format})))
          (draw/draw-imagex c-stream (LosslessFactory/createFromImage doc buff)
                       x y width height))))
    (catch Exception e
      (println "ERROR: Failed to draw image for PDF:" (.getMessage e))
      nil)))

(defn- jpeg-bytes?
  "Whether `data` opens with the JPEG start-of-image marker.

   Sniffed rather than read from a mime type: this decides whether bytes are
   embedded as they are or decoded and re-encoded, and a blob labelled image/jpeg
   that is not one fails the embed."
  [^bytes data]
  (and (>= (alength data) 3)
       (= 0xFF (bit-and 0xFF (aget data 0)))
       (= 0xD8 (bit-and 0xFF (aget data 1)))
       (= 0xFF (bit-and 0xFF (aget data 2)))))

(def ^:private print-edge
  "Longest edge the sheet can show, in pixels. The portrait box is 2.35 x 3.15
   inches and 300dpi is the print target. orcpub.image-capture does the same
   arithmetic in the browser; the two have to agree or one of them is wasting
   bytes the other would have kept."
  945)

(def ^:private fit-attempts
  "Longest edge and JPEG quality, tried in order until the result fits
   max-embedded-bytes. Size is given up before quality, for the reason the
   browser gives it up first: pixels past what the sheet prints cost nothing
   visible, and quality costs something immediately."
  [[945 0.92] [945 0.8] [945 0.68] [945 0.55] [700 0.6] [500 0.5] [350 0.4]])

(defn- scaled-copy
  ^BufferedImage [^BufferedImage img edge]
  (let [w (.getWidth img)
        h (.getHeight img)
        f (min 1.0 (/ (double edge) (double (max w h))))
        nw (int (max 1 (Math/round (* w f))))
        nh (int (max 1 (Math/round (* h f))))
        out (BufferedImage. nw nh BufferedImage/TYPE_INT_RGB)
        g (.createGraphics out)]
    (try
      (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                         RenderingHints/VALUE_INTERPOLATION_BILINEAR)
      (.drawImage g img 0 0 nw nh nil)
      (finally (.dispose g)))
    out))

(defn- jpeg-at
  "JPEG bytes for `img` at `quality`."
  ^bytes [^BufferedImage img quality]
  (let [writer (.next (ImageIO/getImageWritersByFormatName "jpeg"))
        out (ByteArrayOutputStream.)]
    (try
      (with-open [ios (MemoryCacheImageOutputStream. out)]
        (.setOutput writer ios)
        (let [param (.getDefaultWriteParam writer)]
          (.setCompressionMode param ImageWriteParam/MODE_EXPLICIT)
          (.setCompressionQuality param (float quality))
          (.write writer nil (IIOImage. img nil nil) param)))
      (finally (.dispose writer)))
    (.toByteArray out)))

(defn- fit-for-sheet
  "Scales and re-encodes `data` until it is no larger than the sheet can show and
   no heavier than the PDF should carry, or hands it back untouched when it is
   already both. Returns {:data bytes :jpg? bool}, or nil if it cannot be decoded.

   The ceiling belongs on what goes INTO the document, not on what may be
   fetched."
  [^bytes data]
  (when-let [img (ImageIO/read (java.io.ByteArrayInputStream. data))]
    (if (and (<= (alength data) max-embedded-bytes)
             (<= (max (.getWidth img) (.getHeight img)) print-edge))
      {:data data :jpg? (jpeg-bytes? data)}
      (loop [attempts fit-attempts]
        (when-let [[edge q] (first attempts)]
          (let [candidate (jpeg-at (scaled-copy img edge) q)]
            (if (<= (alength candidate) max-embedded-bytes)
              {:data candidate :jpg? true}
              (recur (rest attempts)))))))))

(defn jpeg-url?
  "Whether `url` names a JPEG, and so whether its bytes embed without re-encoding."
  [url]
  (let [lower (s/lower-case (str url))]
    (or (s/ends-with? lower "jpg") (s/ends-with? lower "jpeg"))))

(defn- failure-reason
  "A coarse, reportable reason a picture could not be had, from the ex-data or the
   root cause. Every address refusal collapses into :blocked-address, so the answer
   cannot map what this server can reach; the rest describe the HOST's behaviour,
   which the asker could observe anyway."
  [e]
  (let [{:keys [error status]} (ex-data e)]
    (or (case error
          (:image-url-not-permitted :image-pin-mismatch) :blocked-address
          :image-too-large            :too-large
          :image-too-large-dimensions :too-many-pixels
          :image-transfer-timeout     :timeout
          :invalid-image-format       :not-an-image
          :image-load-failed (cond
                               (nil? status)             :unreachable
                               (<= 300 status 399)       :redirect
                               (= 404 status)            :not-found
                               ;; Temporary, and worth saying so: telling someone
                               ;; their picture is unusable when the host is only
                               ;; asking us to slow down sends them off to find
                               ;; another one for no reason.
                               (= 429 status)            :rate-limited
                               (#{401 403 407 451} status) :refused
                               (<= 500 status 599)       :host-error
                               :else                     :refused)
          nil)
        (let [root (loop [x e] (if-let [c (.getCause ^Throwable x)] (recur c) x))]
          (condp instance? root
            java.net.UnknownHostException   :unreachable
            java.net.ConnectException       :unreachable
            java.net.SocketTimeoutException :timeout
            :unknown)))))

(defn fetch-image-outcome
  "Fetches `url` and returns {:image {:data :jpg?}} or {:reason keyword}.

   The reason is what lets the builder say something a person can act on -- a 404
   and a 2 MB photograph are the same blank space on the sheet otherwise."
  [url]
  (try
    (if-let [image (fit-for-sheet (safe-image-bytes url))]
      {:image image}
      {:reason :not-an-image})
    (catch Exception e
      (println "pdf: image unavailable -" (.getMessage e) "-" url)
      {:reason (failure-reason e)})))

(defn fetch-image
  "Fetches `url` and returns {:data bytes :jpg? bool}, or nil if it cannot be had;
   a picture that will not load must not cost the character their sheet.
   GOTCHA: validation happens inside (safe-image-bytes pins the resolved addresses),
   so do NOT call safe-image-url? first; that resolves the host twice."
  [url]
  (:image (fetch-image-outcome url)))

(def ^:private max-image-base64
  "Ceiling on the ENCODED string, so an oversized image is refused before a byte
   array is allocated to hold it. Base64 spends four characters on every three
   bytes, plus padding."
  (+ 4 (quot (* 4 max-embedded-bytes) 3)))

(defn decode-image-bytes
  "Base64 image bytes the browser read, as {:data bytes :jpg? bool}, or nil rather
   than a throw (the picture is lost, not the sheet). As untrusted as a URL: refused
   over max-embedded-bytes (the encoded length is checked first) or over the pixel
   budget, read from the header since a small file can declare a huge canvas."
  [b64]
  (try
    (when (and (string? b64)
               (not (s/blank? b64))
               (<= (count b64) max-image-base64))
      (let [data (.decode (java.util.Base64/getDecoder) ^String b64)]
        (when (and (pos? (alength data))
                   (<= (alength data) max-embedded-bytes)
                   (within-pixel-budget? data))
          {:data data :jpg? (jpeg-bytes? data)})))
    (catch Exception e
      (println "pdf: supplied image bytes rejected -" (.getMessage e))
      nil)))

(defn draw-image!
  "Draws the image at `url` into the box at [`x` `y`] sized `width` x `height` in
   inches, `y` from the page TOP. Picks `draw-jpg` or `draw-non-jpg` by extension.
   SWALLOWS every failure: logs and returns nil, so one unreachable portrait URL
   cannot fail an export. Callers get no signal that nothing was drawn."
  [doc page url x y width height]
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

(defn get-page
  "The document's `index`th page, zero-based. Thin wrapper over
   PDDocument.getPage so callers need not import PDFBox classes."
  [doc index]
  (.getPage doc index))

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
  "Splits `text` at the last line fitting a `width` x `height` point box at `size`
   (default `min-font-size`). Returns {:head fitting-text :tail remainder-or-nil
   :lines line-count}; callers place :head in the field and :tail on a continuation
   page. Line breaks are hard breaks, kept on both sides of the split (pdf_spec's
   traits-fields separates sections with them). A word too wide for the box still
   takes its own line, so callers cannot loop forever."
  ([text width height] (fit-text text width height min-font-size))
  ([text width height size]
   (let [paragraphs (s/split-lines (str text))
         wrap (fn [p] (if (s/blank? p)
                        [""]
                        (vec (draw/split-lines p draw/HELVETICA size (/ width 72.0)))))
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
;; A level box's number is printed ARTWORK, not a field, so a level with more spells
;; than its box spills to another page while other boxes sit empty. A white rectangle
;; over the numeral re-points a box at another level -- see numeral-boxes.

(def numeral-boxes
  "Where each style prints a level's numeral, relative to that level's SLOTS TOTAL
   box, and the size to set a replacement at. Measured by dev/scan_numerals.clj.
   The box fits the style's widest and TALLEST digit plus a point of margin (style
   3's 1 is taller than its other digits, and a box holding 1 may be relabelled 9).
   It covers the DIGIT, not its badge: the paper around every printed numeral is
   white, so one white patch works on all four styles' badge shapes."
  {1 {:dx -15.8 :dy 4.3 :w 8.4 :h 12.4 :size 9.5}
   2 {:dx -14.4 :dy 5.4 :w 9.4 :h 11.0 :size 10.0}
   3 {:dx -30.4 :dy 6.6 :w 11.2 :h 13.8 :size 11.5}
   4 {:dx -24.8 :dy 5.8 :w 8.4 :h 7.9 :size 7.0}})

(def ^:private hexagon-offset
  "Where a level's hexagon sits relative to its SLOTS TOTAL box, and how big it
   is, in points, measured on the style 1 spell page. Callers use only the
   position, to find the bar a column heading sits in; every style places its
   box-0 badge where style 1 does."
  {:dx -21.0 :dy -7.5 :width 19.0 :height 37.0})

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

(def ^:private cantrips-box-rise
  "How far the cantrips box sits above the level 1 box, in points. The printed
   numerals give it exactly: level 1 at baseline 463.99, and level 3 -- top of
   the middle column, level with the cantrips box -- at 631.72."
  167.73)

;; ─── Reusing the cantrips box ─────────────────────────────────────────
;;
;; Cantrips print once, so on a continuation page the cantrips box's eight rows can
;; carry a spell level. It needs more than a numeral: it has no slots field to locate
;; it by, its bar reads CANTRIPS, and it has no SLOTS TOTAL / EXPENDED labels.

(defn- cantrips-hexagon-box
  "The cantrips bar's hexagon, as [x y width height]. The cantrips box has no
   slots field for spell-level-numeral-box to measure from, so it is level 1's
   hexagon raised by cantrips-box-rise."
  [doc suffix]
  (when-let [[x y w h] (spell-level-numeral-box doc 1 suffix)]
    [x (+ y cantrips-box-rise) w h]))

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
    (.setValue field (draw/normalize-text text))
    page))

(defn spill-overflow!
  "Trims values in `fields` that will not fit their box at min-font-size, moving
   the remainder onto appended continuation pages. Returns the trimmed map.
   Otherwise a field auto-sizes down to 4pt before it clips. All spilled sections
   share one paginated stream, so a few overflowing boxes cost one page, not one each."
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

(defn- page-prints-site-line?
  "Whether `index` already shows the site name in its own artwork.

   Reads the page's text rather than its content stream bytes: style 4 sets its
   footer in a Type0 subset, so the glyphs are hex codes and the literal string
   appears nowhere in the stream."
  [doc index]
  (let [stripper (doto (PDFTextStripper.)
                   (.setStartPage (inc index))
                   (.setEndPage (inc index)))]
    (s/includes? (s/replace (.getText stripper doc) #"\s+" " ") draw/site-stamp)))

(def annotation-zone
  "Points reserved at the right of a spell row for its annotation columns.

   The row is 158pt on style 1, 163-172 on styles 2 and 3 and 141 on style 4, and
   at the size the rows draw at the longest real spell name takes about 88pt --
   so 56 leaves every style room for the name and the columns both."
  56.0)

(def ^:private annotation-columns
  "Where each mark sits, in points left of the row's RIGHT edge, with its size.
   FIXED columns, so a mark is found in one vertical sweep; appended to the name,
   a capital C reads as one more letter and every row must be read."
  {:concentration {:dx 56.0 :size 7.0 :bold? true :grey 0.0}
   :tag           {:dx 46.0 :size 6.0 :bold? true :grey 0.25}
   :material      {:dx 32.0 :size 5.6 :bold? false :grey 0.45}})

(defn- spell-row-widgets
  "Every spells-LEVEL-ROW-SECTION widget, as {:field :widget :page :rect}."
  [doc]
  (when-let [form (.getAcroForm (.getDocumentCatalog doc))]
    (for [field (iterator-seq (.iterator (.getFieldTree form)))
          :let [n (str (.getFullyQualifiedName field))]
          :when (re-matches #"spells-\d+-\d+-\d+" n)
          :let [w (first (.getWidgets field))
                r (some-> w .getRectangle)
                p (some-> w .getPage)]
          :when (and r p)]
      {:field field :widget w :page p :rect r :name n})))

(def ^:private row-right-edge-key
  "Widget dictionary key holding a spell row's right edge as it came off the
   template. Reservation moves that edge left; the annotation columns and the
   cantrips bar on a style with no slots-expended field still need the original,
   and recording it keeps reservation idempotent."
  "OrcpubRowRight")

(defn- row-right-edge
  "The row's right edge before any annotation columns were reserved out of it."
  [widget rect]
  (let [stored (.getFloat (.getCOSObject widget)
                          (COSName/getPDFName row-right-edge-key)
                          Float/MIN_VALUE)]
    (if (= stored Float/MIN_VALUE)
      (+ (.getLowerLeftX rect) (.getWidth rect))
      stored)))

(defn reserve-annotation-columns!
  "Narrows every spell row so its annotation columns are not written over.

   Runs BEFORE write-fields!. The rows auto-size, so narrowing the box is what
   makes a long name shrink to clear the columns instead of running under them --
   doing it afterwards would leave the baked appearance at its old width."
  [doc]
  (doseq [{:keys [widget rect]} (spell-row-widgets doc)
          :let [cos (.getCOSObject widget)
                key (COSName/getPDFName row-right-edge-key)]
          :when (not (.containsKey cos key))]
    (.setFloat cos key (float (+ (.getLowerLeftX rect) (.getWidth rect))))
    (.setRectangle widget (PDRectangle. (.getLowerLeftX rect)
                                        (.getLowerLeftY rect)
                                        (max 1.0 (- (.getWidth rect) annotation-zone))
                                        (.getHeight rect)))))

(defn annotate-spell-rows!
  "Draws each filled spell row's marks in the reserved columns. `annotate` takes the
   row's printed value (the spell name) and returns {:concentration? :tag :material}
   or nil, so the server needs no spell data; a name it cannot place (renamed or
   homebrew) gets no marks. Drawn rather than written as form fields, which cost far
   more bytes per row."
  [doc annotate]
  (let [rows (->> (spell-row-widgets doc)
                  (keep (fn [{:keys [field widget rect page]}]
                          (let [v (str (.getValueAsString field))]
                            (when-not (s/blank? v)
                              (when-let [a (annotate v)]
                                {:page page :widget widget :rect rect :marks a})))))
                  (group-by :page))]
    (doseq [[page items] rows]
      (with-open [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND
                                           true true)]
        (doseq [{:keys [widget rect marks]} items]
          (let [right (row-right-edge widget rect)
                base-y (/ (+ (.getLowerLeftY rect) 1.5) 72.0)]
            (doseq [[k text] [[:concentration (when (:concentration? marks) "C")]
                              [:tag (:tag marks)]
                              [:material (:material marks)]]
                    :when text]
              (let [{:keys [dx size bold? grey]} (get annotation-columns k)]
                (draw/draw-text cs text
                           (if bold? draw/HELVETICA_BOLD draw/HELVETICA)
                           size
                           (/ (- right dx) 72.0)
                           base-y
                           [grey grey grey])))))))))

(def ^:private max-relabels-per-section
  "Ten level boxes to a page, so an honest instruction list never exceeds this
   many per section."
  10)

(defn valid-relabel?
  "Whether one caller-supplied relabel instruction may be applied; these come from
   the browser and reach field names and a drawn label. `section` must name a page
   the document has, `box` one of the ten level boxes (0-9), and `label` a single
   digit or nil (nil blanks a box nothing uses). Box 0, the cantrips box, takes no
   label: the packer never asks, and a level bar drawn onto it is unmeasured."
  [{:keys [section box label]} sections]
  ;; boolean, not the last truthy value: group-by keys on what this RETURNS, and
  ;; re-matches hands back the matched string, so the groups came out keyed "2"
  ;; and nil instead of true and false.
  (boolean
   (and (integer? section) (<= 1 section sections)
        (integer? box) (<= 0 box 9)
        (or (not= 0 box) (nil? label))
        (or (nil? label)
            (and (string? label) (re-matches #"\d" label))))))

(defn relabel-numeral!
  "Covers the printed numeral for `level` in section `suffix` and draws `label`.

   Works on any style with a measured numeral box, because it hides the digit
   rather than repainting the badge -- see numeral-boxes. `label` may be blank, to
   clear a box nothing uses rather than leave it reading as a level the character
   does not have."
  [doc style level suffix label]
  (when-let [{:keys [dx dy w h size]} (get numeral-boxes style)]
    (when-let [slots (some-> (.getAcroForm (.getDocumentCatalog doc))
                             (.getField (str "spell-slots-" level "-" suffix)))]
      (when-let [widget (first (filter #(some? (.getPage %)) (.getWidgets slots)))]
        (let [r (.getRectangle widget)
              x (+ (.getLowerLeftX r) dx)
              y (+ (.getLowerLeftY r) dy)]
          (with-open [cs (PDPageContentStream. doc (.getPage widget)
                                               PDPageContentStream$AppendMode/APPEND
                                               true true)]
            (.setNonStrokingColor cs (float 1) (float 1) (float 1))
            (.addRect cs (float x) (float y) (float w) (float h))
            (.fill cs)
            (.setNonStrokingColor cs (float 0) (float 0) (float 0))
            (when-not (s/blank? (str label))
              (let [lw (* 72 (draw/string-width (str label) draw/HELVETICA_BOLD size))]
                (draw/draw-text cs (str label) draw/HELVETICA_BOLD size
                           (/ (+ x (/ (- w lw) 2.0)) 72.0)
                           (/ (+ y (* 0.22 h)) 72.0)
                           [0 0 0])))))))))

(defn packing-supported?
  "Whether `style`'s level numerals can be relabelled, and so whether a packed
   layout may be printed on it: the styles numeral-boxes has measurements for."
  [style]
  (contains? numeral-boxes style))

(defn apply-relabel-instructions!
  "Renumbers the boxes `instructions` names. Returns [applied refused], counting
   rather than throwing on a malformed list so it cannot cost the character their
   sheet. At most ten apply per section, and none on a style packing-supported?
   rejects. Box 0 takes no label (valid-relabel?); a blank for it is a no-op, having
   no slots field to place a patch by."
  ([doc instructions sections] (apply-relabel-instructions! doc instructions sections 1))
  ([doc instructions sections style]
  (let [wanted (if (packing-supported? style)
                 (take (* sections max-relabels-per-section) (filter map? instructions))
                 ;; An unmeasured style would print the new number beside the old.
                 [])
        {ok true bad false} (group-by #(valid-relabel? % sections) wanted)]
    (doseq [{:keys [section box label]} ok]
      (try
        (relabel-numeral! doc style box section (or label ""))
        (catch Exception e
          (println "pdf: relabel failed for box" box "section" section "-" (.getMessage e)))))
    [(count ok) (+ (count bad) (max 0 (- (count (filter map? instructions)) (count wanted))))])))

(def ^:private cantrips-label
  "What the narrow compartment of a packed cantrips bar says."
  "CANTRIPS")

(def ^:private cantrips-label-size 6.5)

(def ^:private cantrips-label-pad
  "Points of clear space to the left of the CANTRIPS label, from whatever bounds the
   word on that bar: a level bar's SLOTS TOTAL field, or box 0's divider (x 51-59),
   which starts where the compartment borrowed from level 1 begins."
  9.0)

(def ^:private heading-target-size
  "The size a column heading is drawn at when the bar has room for it.

   A heading, not a caption: the rows beneath it set at 6.4 to 8.8pt, so 7pt made
   the class name read as another label rather than as the thing naming the
   column."
  11.0)

(def ^:private heading-floor-size
  "Below this a heading stops reading as one, so a longer name is shortened
   instead of shrunk further."
  6.0)

(defn- heading-size
  "The largest size at or below heading-target-size that fits `label` in `width`,
   floored at heading-floor-size."
  [label width]
  (let [natural (* 72 (draw/string-width label draw/HELVETICA_BOLD heading-target-size))]
    (if (<= natural width)
      heading-target-size
      (max heading-floor-size (* heading-target-size (/ width natural))))))

(defn- fit-heading
  "`label` and the size to draw it at, both trimmed to `width`: shrunk toward
   heading-floor-size, then shortened with an ellipsis if even the floor does not
   fit (\"Eldritch Knight\" at 6pt would print through the bar's rules)."
  [label width]
  (let [size (heading-size label width)]
    (if (<= (* 72 (draw/string-width label draw/HELVETICA_BOLD size)) width)
      {:label label :size size}
      (loop [n (dec (count label))]
        (let [candidate (str (s/trimr (subs label 0 (max 0 n))) "\u2026")]
          (cond
            (<= n 1) {:label "\u2026" :size size}
            (<= (* 72 (draw/string-width candidate draw/HELVETICA_BOLD size)) width)
            {:label candidate :size size}
            :else (recur (dec n))))))))

(defn- bar-compartments
  "The bar's two compartments for `box`, as {:narrow [x width] :wide [x width]} --
   the narrow one a level bar gives SLOTS TOTAL, the wide one SLOTS EXPENDED -- or
   nil. Read off the live fields, so they follow the artwork; box 0 borrows level
   1's x positions. A style with no slots-expended field (2 and 4) takes the wide
   one from the spell row's template right edge."
  [doc box suffix]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        ;; Box 0 has no slots fields, so its compartments come from level 1's.
        ;; Only the X is taken: the height comes from the cantrips hexagon, which
        ;; is already raised, and adding the rise again put the text 168pt above
        ;; the bar and left it blank.
        level (if (zero? box) 1 box)
        widget (fn [nm] (some-> (.getField form nm) .getWidgets first))
        rect (fn [nm] (some-> (widget nm) .getRectangle))]
    (when-let [total (rect (str "spell-slots-" level "-" suffix))]
      (let [narrow [(.getLowerLeftX total) (.getWidth total)]
            wide (if-let [expended (rect (str "slots-expended-" level "-" suffix))]
                   [(.getLowerLeftX expended) (.getWidth expended)]
                   ;; From the divider to the row's right edge, less a margin.
                   ;; The row's edge as the template drew it: reserving the
                   ;; annotation columns cuts 56pt off the live rectangle, which
                   ;; would leave the compartment too narrow for a class name.
                   (let [from (+ (.getLowerLeftX total) (.getWidth total) 12.0)]
                     (when-let [w (widget (str "spells-" box "-1-" suffix))]
                       [from (- (row-right-edge w (.getRectangle w)) from 2.0)])))]
        (when wide
          {:narrow narrow :wide wide})))))

(defn- shrink-slots-expended!
  "Moves the SLOTS EXPENDED input's left edge to `x`, so a class name can sit beside
   it in the same compartment, unless that leaves the input under 12pt. Only for the
   first box of a class with no cantrips: every other heading sits on a cantrips
   box, whose slot inputs are meaningless and are simply drawn over."
  [doc box suffix x]
  (when-let [field (some-> (.getAcroForm (.getDocumentCatalog doc))
                           (.getField (str "slots-expended-" box "-" suffix)))]
    (when-let [widget (first (.getWidgets field))]
      (let [r (.getRectangle widget)
            right (+ (.getLowerLeftX r) (.getWidth r))]
        (when (< x (- right 12.0))
          (.setRectangle widget (PDRectangle. (float x) (.getLowerLeftY r)
                                              (float (- right x)) (.getHeight r))))))))

(def ^:private cantrips-word-patch
  "The band each style prints CANTRIPS in inside its cantrips bar, as a dy and height
   from the bar's middle plus a couple of points of margin; draw-column-heading!
   covers it before writing the class name there. Per style: the word sits at
   different heights, and one band wide enough for all painted over style 4's rules.
   Measured by dev/scan_cantrips_word.clj, except style 4's, read off the render.
   A style with no entry gets no patch and keeps its printed word."
  {1 {:dy -5.3 :h 9.0}
   2 {:dy -5.3 :h 9.2}
   3 {:dy -2.4 :h 10.2}
   4 {:dy -5.0 :h 10.0}})

(defn draw-column-heading!
  "Labels a packed cantrips box: CANTRIPS in the narrow compartment, `label` -- the
   class holding the column -- in the wide one, with any ability/DC/attack above the
   bar. Only a cantrips bar has room, as its slot compartments are free. With
   `cantrips?` false the name shares the wide compartment with the SLOTS EXPENDED
   input. Box 0 prints CANTRIPS in its artwork where the name goes, so that word is
   covered first."
  ([doc style box suffix label] (draw-column-heading! doc style box suffix label nil))
  ([doc style box suffix label {:keys [ability dc attack cantrips?]
                                :or {cantrips? true}}]
  (when-let [{:keys [narrow wide]} (bar-compartments doc box suffix)]
    (when-let [[hx hy hw hh] (if (zero? box)
                             (cantrips-hexagon-box doc suffix)
                             (spell-level-numeral-box doc box suffix))]
      (let [page (some-> (.getAcroForm (.getDocumentCatalog doc))
                         (.getField (str "spells-" box "-1-" suffix))
                         .getWidgets first .getPage)
            [nx nw] narrow
            [wx ww] wide
            middle (+ hy (/ hh 2.0))]
        (when page
          (with-open [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND
                                               true true)]
            (when-let [{pdy :dy ph :h} (and (zero? box) (get cantrips-word-patch style))]
              ;; Only the lettering's own band: a patch reaching the bar's rules
              ;; makes the bar look cut through. It starts at the divider, not wx:
              ;; the printed word is centred on the bar and begins left of wx.
              (let [x0 (+ hx hw 6.0)]
                (.setNonStrokingColor cs (float 1) (float 1) (float 1))
                (.addRect cs (float x0) (float (+ middle pdy))
                          (float (- (+ wx ww) x0 2.0)) (float ph))
                (.fill cs)
                (.setNonStrokingColor cs (float 0) (float 0) (float 0))))
            (when cantrips?
             (let [;; Shrunk only if a style's narrow compartment cannot take the
                  ;; word at its padded position.
                  {csize :size ctext :label}
                  (let [room (- nw cantrips-label-pad 2.0)
                        natural (* 72 (draw/string-width cantrips-label draw/HELVETICA_BOLD
                                                    cantrips-label-size))]
                    (if (<= natural room)
                      {:size cantrips-label-size :label cantrips-label}
                      {:size (max 4.5 (* cantrips-label-size (/ room natural)))
                       :label cantrips-label}))]
              (draw/draw-text cs ctext draw/HELVETICA_BOLD csize
                         (/ (+ (if (zero? box)
                                 ;; Past box 0's own divider, which ends about
                                 ;; nine points beyond the hexagon.
                                 (+ hx hw 9.0)
                                 nx)
                               cantrips-label-pad)
                            72.0)
                         (/ (+ middle (* -0.36 csize)) 72.0)
                         [0.45 0.45 0.45])))
            ;; The class name and, beside it, the numbers the section's own
            ;; ability/DC/attack boxes cannot carry once a page holds more than
            ;; one class. Centred together so the pair reads as one heading.
            (let [stats (->> [(when ability (s/upper-case (str ability)))
                              (when dc (str "DC " dc))
                              (when attack (str attack))]
                             (remove nil?)
                             (s/join "  "))
                  ;; ABOVE the bar, not inside it. Sharing the compartment with
                  ;; the class name left neither readable: the pair came to 96pt
                  ;; in a 92.8pt compartment, so fitting one shrank the other and
                  ;; "Sorcerer" came out as "Sorce...".
                  small 7.0
                  ;; A cantrips bar gives the whole wide compartment to the name.
                  ;; A level bar keeps its SLOTS EXPENDED input, so the name takes
                  ;; the left half and the input moves to the right of it.
                  room (if cantrips? (- ww 6.0) (* ww 0.5))
                  {label :label size :size} (fit-heading label room)
                  lw (* 72 (draw/string-width label draw/HELVETICA_BOLD size))
                  start (if cantrips? (+ wx (/ (- ww lw) 2.0)) (+ wx 4.0))]
              (when-not cantrips?
                (shrink-slots-expended! doc box suffix (+ wx lw 10.0)))
              (draw/draw-text cs label draw/HELVETICA_BOLD size
                         (/ start 72.0)
                         (/ (+ middle (* -0.36 size)) 72.0)
                         [0.15 0.15 0.15])
              (when-not (s/blank? stats)
                ;; Bold and near-black. These are numbers a player reads mid-turn
                ;; -- the save DC of the spell they are casting and what they add
                ;; to hit -- so they are set like the class name rather than like
                ;; the CANTRIPS caption, which is a label nobody needs to find.
                (let [sw (* 72 (draw/string-width stats draw/HELVETICA_BOLD small))
                      sx (+ wx (/ (- ww sw) 2.0))
                      ;; Clear of the bar's upper rule, which sits 9.75 above its
                      ;; middle.
                      sy (+ middle 14.5)]
                  ;; On a backing strip. The band above the bar is open paper on
                  ;; styles 1 and 2 but carries scrollwork on 3 and 4, and set
                  ;; straight onto that the digits were unreadable.
                  (.setNonStrokingColor cs (float 1) (float 1) (float 1))
                  (.addRect cs (float (- sx 2.0)) (float (- sy 1.5))
                            (float (+ sw 4.0)) (float (+ small 1.5)))
                  (.fill cs)
                  (.setNonStrokingColor cs (float 0) (float 0) (float 0))
                  (draw/draw-text cs stats draw/HELVETICA_BOLD small
                             (/ sx 72.0) (/ sy 72.0)
                             [0.1 0.1 0.1])))))))))))

(defn stamp-site-line!
  "Prints the site line at `position` (the style's :site-line from sheet-masters,
   chosen per style to clear its corner artwork) on every page that lacks one.
   `prints-own?` means the artwork carries it on SOME pages (style 4's spell pages)
   and gates the per-page text scan. Appends a stream per page: cloned spell pages
   SHARE the master's content stream, so writing into it would print once per clone."
  [doc [x y] prints-own?]
  (doseq [[index page] (map-indexed vector (vec (.getPages doc)))
          :when (not (and prints-own? (page-prints-site-line? doc index)))]
    (with-open [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND
                                         true true)]
      (draw/draw-text cs draw/site-stamp draw/HELVETICA draw/site-stamp-size x y [0.45 0.45 0.45]))))
