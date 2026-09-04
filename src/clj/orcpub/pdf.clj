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
           (org.apache.pdfbox.util Matrix)
           (org.apache.http.conn DnsResolver)
           (org.apache.http.impl.conn BasicHttpClientConnectionManager)
           (org.apache.http.config RegistryBuilder)
           (org.apache.http.conn.socket PlainConnectionSocketFactory)
           (org.apache.http.conn.ssl SSLConnectionSocketFactory)
           (java.net UnknownHostException)
           (org.apache.pdfbox.pdmodel.font PDType1Font PDFont PDType0Font Standard14Fonts$FontName)
           (org.apache.pdfbox.text PDFTextStripper)
           (javax.imageio ImageIO)
           (java.net URL)))

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

(def ^:private svg-token
  #"([MmLlHhVvCcSsQqTtAaZz])|(-?(?:\d*\.\d+|\d+)(?:[eE][-+]?\d+)?)")

(defn- svg-tokens
  "The `d` attribute as an alternating stream of command keywords and numbers."
  [d]
  (for [[_ cmd num] (re-seq svg-token d)]
    (if cmd (keyword cmd) (Double/parseDouble num))))

(defn- arc-points
  "An elliptical arc sampled as points. SVG's A takes radii, a rotation and two
   flags; PDF has no arc operator, and the exact bezier decomposition is a page of
   trigonometry for a curve that spans a few points on an icon, so it is sampled."
  [x0 y0 rx ry rot large? sweep? x1 y1]
  (let [phi (Math/toRadians rot)
        cos-p (Math/cos phi) sin-p (Math/sin phi)
        dx2 (/ (- x0 x1) 2.0) dy2 (/ (- y0 y1) 2.0)
        tx (+ (* cos-p dx2) (* sin-p dy2))
        ty (- (* cos-p dy2) (* sin-p dx2))
        rx (Math/abs rx) ry (Math/abs ry)
        ;; A radius too small to span the endpoints is scaled up, per the spec.
        lam (+ (/ (* tx tx) (* rx rx)) (/ (* ty ty) (* ry ry)))
        [rx ry] (if (> lam 1) [(* rx (Math/sqrt lam)) (* ry (Math/sqrt lam))] [rx ry])
        num (- (* rx rx ry ry) (* rx rx ty ty) (* ry ry tx tx))
        den (+ (* rx rx ty ty) (* ry ry tx tx))
        co (* (if (= large? sweep?) -1 1) (Math/sqrt (max 0.0 (/ num den))))
        cx' (* co (/ (* rx ty) ry))
        cy' (* co (- (/ (* ry tx) rx)))
        cx (+ (* cos-p cx') (- (* sin-p cy')) (/ (+ x0 x1) 2.0))
        cy (+ (* sin-p cx') (* cos-p cy') (/ (+ y0 y1) 2.0))
        ang (fn [ux uy vx vy]
              (let [d (/ (+ (* ux vx) (* uy vy))
                         (* (Math/hypot ux uy) (Math/hypot vx vy)))
                    a (Math/acos (max -1.0 (min 1.0 d)))]
                (if (neg? (- (* ux vy) (* uy vx))) (- a) a)))
        th1 (ang 1 0 (/ (- tx cx') rx) (/ (- ty cy') ry))
        dth (let [d (ang (/ (- tx cx') rx) (/ (- ty cy') ry)
                         (/ (- (- tx) cx') rx) (/ (- (- ty) cy') ry))]
              (cond (and (not sweep?) (pos? d)) (- d (* 2 Math/PI))
                    (and sweep? (neg? d)) (+ d (* 2 Math/PI))
                    :else d))
        steps (max 6 (int (* 24 (/ (Math/abs dth) Math/PI))))]
    (for [i (range 1 (inc steps))
          :let [t (+ th1 (* dth (/ i (double steps))))
                ex (* rx (Math/cos t)) ey (* ry (Math/sin t))]]
      [(+ cx (* cos-p ex) (- (* sin-p ey)))
       (+ cy (* sin-p ex) (* cos-p ey))])))

(defn svg-path-ops
  "Parses an SVG `d` attribute into [:move x y], [:line x y] and
   [:curve x1 y1 x2 y2 x y] in the SVG's own coordinates, plus [:close].

   Covers the commands game-icons.net glyphs use -- M L H V C S Q T A Z and their
   relative forms -- including the rule that coordinate pairs after an M continue
   as implicit L. Quadratics are raised to cubics, which PDF has; arcs are sampled,
   which PDF does not."
  [d]
  (loop [[t & more :as ts] (svg-tokens d)
         cmd nil, cx 0.0, cy 0.0, sx 0.0, sy 0.0
         prev-c nil, prev-q nil, out []]
    (if (nil? t)
      out
      (let [[cmd ts] (if (keyword? t) [t more] [cmd ts])
            rel? (and cmd (Character/isLowerCase (first (name cmd))))
            n (fn [i] (nth ts i))
            ax (fn [v] (if rel? (+ cx v) v))
            ay (fn [v] (if rel? (+ cy v) v))]
        (case (if cmd (keyword (s/upper-case (name cmd))) :none)
          :M (let [x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) (if rel? :l :L) x y x y nil nil
                      (conj out [:move x y])))
          :L (let [x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) cmd x y sx sy nil nil (conj out [:line x y])))
          :H (let [x (ax (n 0))]
               (recur (drop 1 ts) cmd x cy sx sy nil nil (conj out [:line x cy])))
          :V (let [y (ay (n 0))]
               (recur (drop 1 ts) cmd cx y sx sy nil nil (conj out [:line cx y])))
          :C (let [x1 (ax (n 0)) y1 (ay (n 1)) x2 (ax (n 2)) y2 (ay (n 3))
                   x (ax (n 4)) y (ay (n 5))]
               (recur (drop 6 ts) cmd x y sx sy [x2 y2] nil
                      (conj out [:curve x1 y1 x2 y2 x y])))
          ;; S and T take their first control point by reflecting the previous
          ;; one through the current point; with no previous curve it is the
          ;; current point itself.
          :S (let [[px py] (or prev-c [cx cy])
                   x1 (- (* 2 cx) px) y1 (- (* 2 cy) py)
                   x2 (ax (n 0)) y2 (ay (n 1)) x (ax (n 2)) y (ay (n 3))]
               (recur (drop 4 ts) cmd x y sx sy [x2 y2] nil
                      (conj out [:curve x1 y1 x2 y2 x y])))
          :Q (let [qx (ax (n 0)) qy (ay (n 1)) x (ax (n 2)) y (ay (n 3))]
               (recur (drop 4 ts) cmd x y sx sy nil [qx qy]
                      (conj out [:curve (+ cx (* 2/3 (- qx cx))) (+ cy (* 2/3 (- qy cy)))
                                 (+ x (* 2/3 (- qx x))) (+ y (* 2/3 (- qy y))) x y])))
          :T (let [[px py] (or prev-q [cx cy])
                   qx (- (* 2 cx) px) qy (- (* 2 cy) py)
                   x (ax (n 0)) y (ay (n 1))]
               (recur (drop 2 ts) cmd x y sx sy nil [qx qy]
                      (conj out [:curve (+ cx (* 2/3 (- qx cx))) (+ cy (* 2/3 (- qy cy)))
                                 (+ x (* 2/3 (- qx x))) (+ y (* 2/3 (- qy y))) x y])))
          :A (let [x (ax (n 5)) y (ay (n 6))
                   pts (arc-points cx cy (n 0) (n 1) (n 2)
                                   (not (zero? (n 3))) (not (zero? (n 4))) x y)]
               (recur (drop 7 ts) cmd x y sx sy nil nil
                      (into out (map (fn [[px py]] [:line px py]) pts))))
          :Z (recur ts cmd sx sy sx sy nil nil (conj out [:close]))
          (recur more cmd cx cy sx sy prev-c prev-q out))))))

(defn- emit-svg-path!
  "Writes `ops` from svg-path-ops as PDF path operators, mapping SVG coordinates
   through `px` and `py`.

   game-icons.net glyphs are one filled path with holes, which is exactly what the
   nonzero winding rule of `fill` produces."
  [cs ops px py]
  (doseq [op ops]
    (case (first op)
      :move  (.moveTo cs (px (nth op 1)) (py (nth op 2)))
      :line  (.lineTo cs (px (nth op 1)) (py (nth op 2)))
      :curve (.curveTo cs (px (nth op 1)) (py (nth op 2))
                       (px (nth op 3)) (py (nth op 4))
                       (px (nth op 5)) (py (nth op 6)))
      :close (.closePath cs)
      nil))
  (.fill cs))

(def ^:private svg-view
  "game-icons.net glyphs are all drawn in a 512x512 viewBox."
  512.0)

(defn draw-svg-path!
  "Fills `ops` straight into a `size`-inch box whose top-left is (x, y) on the
   page, scaling from a `view` square viewBox.

   SVG counts y downward from the top of its box and PDF counts it upward from the
   bottom of the page, hence the flip. This writes the whole path into the page's
   content stream; anything drawn more than once should go through the image
   loader instead, which writes it once for the document."
  [cs ops x y size view]
  (emit-svg-path! cs ops
                  (fn [v] (float (* 72 (+ x (* size (/ v view))))))
                  (fn [v] (float (* 72 (- 11 y (* size (/ v view))))))))

(defn last-svg-path
  "The `d` of the LAST <path> in an SVG document.

   game-icons.net wraps each glyph in a square background path that is present but
   transparent; filling it would black out the icon, so the glyph is the last one.

   Attribute values are matched in either quote style: the colour icons in this
   repo were saved with double quotes and the `black/` set with single."
  [svg]
  (when-let [m (last (re-seq #"<path\b[^>]*\sd\s*=\s*(?:\"([^\"]*)\"|'([^']*)')" svg))]
    (or (nth m 1) (nth m 2))))

(defn load-svg-icon
  "The drawable path operations of an icon in resources/public/image, or nil if
   that icon was never vendored as an SVG."
  [icon-name]
  (some-> (io/resource (str "public/image/" icon-name ".svg"))
          slurp last-svg-path svg-path-ops))

(defn- round-unit
  "A form coordinate, at one decimal place.

   Every digit written is a byte in the file, and the geometry is the whole cost
   of a vector icon over a raster one -- trimming to a tenth of a unit takes about
   2.5% off a card-heavy export. A tenth of a 512-unit box is 1/5120 of the icon:
   at the 0.25in a card draws one that is a third of a 600 DPI dot, and it stays
   under a dot even at an inch."
  [v]
  (float (/ (Math/round (* 10.0 (double v))) 10.0)))

(defn svg-form
  "An icon's paths as a Form XObject in a `svg-view`-unit box, y already flipped
   so the form's own space is upright.

   A form is written into the file ONCE and referenced wherever it is drawn.
   Emitting the path at each draw site instead costs about 2.8 KB per card, which
   on a 45-card spellbook more than doubles the file.

   The form sets no colour of its own, so the fill colour and alpha in force at
   each draw site apply to it."
  [doc ops]
  (let [form (PDAppearanceStream. doc)]
    (.setBBox form (PDRectangle. 0 0 (float svg-view) (float svg-view)))
    (.setResources form (PDResources.))
    (with-open [cs (PDPageContentStream. doc form)]
      (emit-svg-path! cs ops round-unit (fn [v] (round-unit (- svg-view v)))))
    form))

(defn make-image-loader
  "Returns a memoized (resource-path -> XObject) embedder scoped to ONE document:
   a PDImageXObject for a raster path, a form (see svg-form) for a `.svg` one.

   The card icons and logo are drawn dozens of times across a spellbook; without
   this, each use re-decodes the source and embeds a DUPLICATE object — wasting
   CPU + transient memory per embed and bloating the file. Memoized so each
   distinct resource is embedded exactly once, then referenced thereafter. That
   matters as much for vector as for raster: an icon's paths run to a few kilobytes
   and every card would otherwise carry its own copy."
  [doc]
  (memoize
   (fn [resource-path]
     (if (s/ends-with? resource-path ".svg")
       (svg-form doc (svg-path-ops (last-svg-path (slurp (io/resource resource-path)))))
       (with-open [in (io/input-stream (io/resource resource-path))]
         (LosslessFactory/createFromImage doc (ImageIO/read in)))))))

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
      ;; Both branches insert. PDPageTree.add walks the whole object graph looking
      ;; for a cycle, and these masters nest deeply enough through the AcroForm
      ;; that the walk overflows the stack -- so a style whose spell page is LAST
      ;; (3 and 4) threw StackOverflowError at two or more casters while styles 1
      ;; and 2, which have a page to insert before, were fine. insertAfter does no
      ;; such walk.
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
;; Every style's master is a ONE-spell-page file. Style 4's shipped two, because
;; its licence footer was read as baked into the artwork -- and a baked footer can
;; be spread by cloning but never removed, so a last-page-only style needed a
;; plain page to clone and a marked page to end on.
;;
;; It is not baked. Both of those pages referenced the same background XObject and
;; the marked one was the plain one plus an appended BT/ET block, so keeping the
;; marked page alone gives a master whose clones all carry the footer, like every
;; other style. dev/style4_one_spell_page.clj is what built it and records what
;; removing the page took.

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
   licence line would vanish along with the spell pages.

   :prints-site-line? marks a style whose artwork already carries the site name --
   only style 4, whose footer reads \"dungeonmastersvault.com by permission -
   Petersen Games LLC 2021\". stamp-site-line! leaves those pages alone rather than
   printing it twice.

   :packing? says the style's printed level numeral has been MEASURED, and so
   whether a packed layout may be used on it. relabel-spell-level! covers that
   numeral with a patch cut to hexagon-path, which was traced off style 1 at 1200
   dpi -- and the styles do not merely offset it, they draw a different shape. The
   printed numeral sits at dx -14.4 from its slots box on style 1, -12.4 on 2,
   -28.0 on 3 and -23.0 on 4, and style 3 rings its numerals where style 4 uses a
   small hexagon. Rendering a packed page on 2, 3 and 4 showed both numbers, the
   old one beside the new: \"3 0\", \"4 1\", \"7 2\".

   Only style 1 is measured. A packed layout asked for on another style falls back
   to a page per class, which is correct if not as tight -- rather than printing a
   sheet whose level numbers lie.

   :site-line is where stamp-site-line! puts that line, in inches from the page's
   bottom-left corner, and is MEASURED off rendered pages -- see
   dev/scan_site_line.clj, with a test holding the result.

   Reasoning about it from the page text does not work and was tried: every style
   got one position picked from where its lowest TEXT sat, which is all
   PDFTextStripper reports, and the line came down through the corner flourish on
   the last page of styles 1 and 2 and through the frame on style 4. Nor is a
   coarse render enough -- at 150 dpi the second attempt looked clear and at 300
   the footer band of that same last page turned out to be a solid bar under the
   whole width of it.

   x is shared; the heights are not. Styles 1 and 2 sit at 0.13 to clear that bar,
   which leaves about 0.03in of headroom before the frame above, and styles 3 and
   4 sit lower at 0.06 where their own artwork stops."
  {1 {:file "fillable-char-sheetstyle-1-1-spells.pdf" :marks :all :packing? true
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
  "Values a style's sheet has nowhere to print, by style.

   Style 4 is the Cthulhu Mythos sheet and is laid out differently rather than
   incompletely: it carries \"Conditions and Insanities\" where the others carry
   an inspiration box, so inspiration has nowhere to go at all. Its backstory and
   allies are not here -- see merged-fields, which puts them in its Notes box.

   These are DECLARED so the export can be checked against them: a test fills
   every style and asserts the values write-fields! could not place are exactly
   this set, so a newly missing field fails the build instead of printing an
   empty box. Adding a field to a template means deleting its name from here."
  {4 #{"inspiration"}})

(def merged-fields
  "Values a style prints together in one box, by style: target field -> the
   headed sections that go into it, in order.

   Style 4 has no allies or backstory box and one general Notes box, so the two
   are written into it under headings rather than dropped. Notes is multiline and
   263x252pt against the 354x369 and 176x219 boxes style 1 gives the same two
   values, so a long backstory shrinks to fit and a very long one clips at the
   4pt floor -- which loses the tail of a paragraph rather than all of both."
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

   Every master holds one spell page, so this only ever clones it. `marks` is
   accepted because callers read it from sheet-masters alongside :file, and is not
   consulted: an attribution footer lives in the page's content stream and so is
   carried by every clone whatever the style."
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
   ghosts and share their values.

   `max-sections` is a ceiling on how many this will generate, and callers taking
   `fields` from a request must pass one. The count comes from the field NAMES, so
   a single \"spellcasting-class-9999\" asks for thousands of cloned pages at about
   14 MB each -- an out of memory error from a request of a few dozen bytes."
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

(def icon-red
  "The red the card icons were baked at, as [r g b alpha]. The PNGs carry #910000
   at half opacity; filling the vector with the same two values composites
   identically over whatever is beneath it."
  [0.5686 0.0 0.0 0.5])

(defn draw-svg-icon!
  "Draws a named icon into a `size`-inch box, filled in `color` at `alpha`.

   Takes draw-imagex's coordinates: `y` is inches from the page TOP to the box's
   top edge. (draw-imagex reaches that by way of a bottom-left PDF origin, so it
   reads as though it counted from the bottom. It does not.)

   The icon is a form shared across the document, placed by a transform rather
   than re-emitted, so a page of nine cards costs nine references. Colour and alpha
   are set inside a save/restore, both because the form inherits them and so
   neither leaks into the label drawn over the icon.

   Falls back to the PNG of the same name when no SVG was vendored, which ignores
   `color` because the PNG's colour is baked in."
  [cs img icon-name x y size [r g b] alpha]
  (if (io/resource (str "public/image/" icon-name ".svg"))
    ;; The form is a svg-view-unit square; this maps that square onto `size` inches.
    (let [unit (float (/ (* 72 size) svg-view))]
      (.saveGraphicsState cs)
      (when (< alpha 1)
        (.setGraphicsStateParameters
         cs (doto (PDExtendedGraphicsState.)
              (.setNonStrokingAlphaConstant (float alpha)))))
      (.setNonStrokingColor cs (float r) (float g) (float b))
      (.transform cs (Matrix. unit 0 0 unit
                              (float (* 72 x)) (float (* 72 (- 11 y size)))))
      (.drawForm cs (img (str "public/image/" icon-name ".svg")))
      (.restoreGraphicsState cs))
    (draw-imagex-alpha cs (img (str "public/image/" icon-name ".png"))
                       x y size size alpha)))

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
  "Addresses no user-supplied URL has any business reaching.

   InetAddress has a predicate for most of them. Three it does not, all confirmed
   reachable through this guard before they were added here:

   - fc00::/7, the unique local addresses an internal IPv6 network actually uses.
     isSiteLocalAddress only knows fec0::/10, deprecated in 2004.
   - 100.64.0.0/10 and the other reserved IPv4 blocks in reserved-v4?.
   - the v4-in-v6 wrappers embedded-v4 unpacks."
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
  (some? (validated-addresses url)))

(defn- pinned-connection-manager
  "A connection manager that resolves `host` to `addrs` and nothing else, so the
   connection goes to the address validated-addresses checked rather than to a
   second lookup that may answer differently.

   Two constraints hold this shape:

   - The resolver must go on the connection MANAGER. HttpClientBuilder's
     setDnsResolver is documented as overridden by setConnectionManager, and
     clj-http always sets one, so pinning there is silently ignored.
   - The hostname must stay in the URL, so the socket factories below do their
     ordinary certificate and hostname checks. Addressing the IP directly with a
     Host header needs those checks overridden, which is the larger hole."
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
  "Whether the JVM's proxy settings route this URL through a proxy.

   Behind one the client connects to the PROXY, so the resolver is asked for the
   proxy's host and a pin on the target host refuses it -- every HTTPS fetch fails.
   The pin is also pointless there, since the proxy does the resolving.

   Reads the same ProxySelector as clj-http's route planner, so the two cannot
   disagree about whether a proxy applies."
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

   Redirects are off because they defeat any host check: a permitted host that
   offers an open redirect would otherwise hand the fetch straight to a private
   address. Taking `addrs` rather than resolving again is what closes the gap
   between checking an address and connecting to it.

   Returns [stream connection-manager-or-nil]; the caller closes both."
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
            (throw (ex-info "Image is larger than the 128k limit"
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
  "Total wall clock allowed for pulling one image body.

   setReadTimeout bounds each READ, not the transfer. A server that sends a byte
   just before each timeout would expire holds the connection for the timeout
   times the number of reads -- 128 KB in 8 KB reads is sixteen of them, so 160
   seconds -- and holds an export slot for every one of them. Bounding the bytes
   without bounding the time leaves the same hole the request clamp closed."
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
           (throw (ex-info "Image is larger than the 128k limit"
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

(defn draw-image-bytes!
  "Draws already-fetched image `data` at `x`,`y`.

   Split from draw-image! so an export can fetch its images before it draws any
   of them: fetching is where the seconds go, drawing is arithmetic. `jpg?`
   decides the embedding -- JPEG bytes go into the file as they are, and anything
   else is decoded and re-encoded losslessly, which is the only way PDFBox will
   take it."
  [doc page data jpg? x y width height]
  (try
    (with-open [c-stream (content-stream doc page)]
      (if jpg?
        (with-open [in (java.io.ByteArrayInputStream. data)]
          (draw-imagex c-stream (JPEGFactory/createFromStream doc in) x y width height))
        (let [buff (ImageIO/read (java.io.ByteArrayInputStream. data))]
          (when (nil? buff)
            (throw (ex-info "Unable to read image" {:error :invalid-image-format})))
          (draw-imagex c-stream (LosslessFactory/createFromImage doc buff)
                       x y width height))))
    (catch Exception e
      (println "ERROR: Failed to draw image for PDF:" (.getMessage e))
      nil)))

(defn jpeg-url?
  "Whether `url` names a JPEG, and so whether its bytes embed without re-encoding."
  [url]
  (let [lower (s/lower-case (str url))]
    (or (s/ends-with? lower "jpg") (s/ends-with? lower "jpeg"))))

(defn fetch-image
  "Fetches `url` and returns {:data bytes :jpg? bool}, or nil if it cannot be had.

   Returns nil rather than throwing for the same reason safe-image-url? does: a
   picture that will not load is a state the sheet already handles, and it must
   not cost the character their sheet. Validation happens here, in
   safe-image-bytes, whose resolved addresses are the ones the fetch is pinned
   to -- so a caller does NOT need to call safe-image-url? first, and a caller
   that does resolves the host twice."
  [url]
  (try
    {:data (safe-image-bytes url) :jpg? (jpeg-url? url)}
    (catch Exception e
      (println "pdf: image unavailable -" (.getMessage e) "-" url)
      nil)))

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
  ;;   color (default)  -> icon in icon-red, plain black text
  ;;   B&W solid (A)     -> solid-black icon, WHITE-HALO text so it reads
  ;;   B&W faded (B)     -> black icon at 40% (light backdrop), plain black text
  ;; One vector source covers all three, so the `-bw` PNGs -- a second copy of the
  ;; same art, recoloured -- are no longer read.
  (let [iy (- 11 y 0.12)
        [color alpha] (cond (and bw? bw-faded?) [[0 0 0] 0.4]
                            bw?                 [[0 0 0] 1.0]
                            :else               [(vec (take 3 icon-red)) (last icon-red)])]
    (draw-svg-icon! cs img title x iy 0.25 color alpha)
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

(def site-stamp
  "The site line printed on card backs."
  "dungeonmastersvault.com")

(def ^:private site-stamp-size 6)

(def ^:private site-stamp-strip
  "Inches reserved at the foot of a card for the site line.

   Sized so the last line of overflow text clears the stamp by about an eighth of
   an inch. draw-lines-to-box takes `(dec (/ (* 72 height) leading))` lines, and
   `take` given a fraction rounds UP -- a box shortened by 0.22 computed 24.2 and
   laid down 25 lines, whose descenders sat on the stamp."
  0.35)

(defn- draw-site-stamp!
  "Centres the site line just above a card's bottom edge.

   `x` and `y` are the card's top-left in the grid's own terms: x from the page
   left, y from the page TOP, both in inches. draw-text wants a baseline measured
   from the page BOTTOM, which is the flip every caller in here does by hand.

   Grey rather than black so it reads as a mark on the card and not as part of the
   card's text, and it stays grey in B&W mode -- greyscale is what that mode is
   for."
  [cs fonts x y box-width box-height]
  (let [font (:plain fonts)
        width (string-width site-stamp font site-stamp-size)]
    (draw-text cs
               site-stamp
               font
               site-stamp-size
               (+ x (/ (- box-width width) 2))
               (- 11.0 y (- box-height 0.16))
               [0.45 0.45 0.45])))

(defn- page-prints-site-line?
  "Whether `index` already shows the site name in its own artwork.

   Reads the page's text rather than its content stream bytes: style 4 sets its
   footer in a Type0 subset, so the glyphs are hex codes and the literal string
   appears nowhere in the stream."
  [doc index]
  (let [stripper (doto (PDFTextStripper.)
                   (.setStartPage (inc index))
                   (.setEndPage (inc index)))]
    (s/includes? (s/replace (.getText stripper doc) #"\s+" " ") site-stamp)))

(def annotation-zone
  "Points reserved at the right of a spell row for its annotation columns.

   The row is 158pt on style 1, 163-172 on styles 2 and 3 and 141 on style 4, and
   at the size the rows draw at the longest real spell name takes about 88pt --
   so 56 leaves every style room for the name and the columns both."
  56.0)

(def ^:private annotation-columns
  "Where each mark sits, in points left of the row's RIGHT edge, with its size.

   FIXED columns, which is the whole point. Appending the marks to the name fits,
   but a C among letters is the same visual class as the letters -- single
   capital, same weight -- so finding it is a serial search and the eye has to
   read every row. A column turns that into one vertical sweep. Spacing does not
   fix a serial search; alignment does."
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

(defn reserve-annotation-columns!
  "Narrows every spell row so its annotation columns are not written over.

   Runs BEFORE write-fields!. The rows auto-size, so narrowing the box is what
   makes a long name shrink to clear the columns instead of running under them --
   doing it afterwards would leave the baked appearance at its old width."
  [doc]
  (doseq [{:keys [widget rect]} (spell-row-widgets doc)]
    (.setRectangle widget (PDRectangle. (.getLowerLeftX rect)
                                        (.getLowerLeftY rect)
                                        (max 1.0 (- (.getWidth rect) annotation-zone))
                                        (.getHeight rect)))))

(defn annotate-spell-rows!
  "Draws each filled spell row's marks in the reserved columns.

   `annotate` takes the row's printed value -- the spell name -- and returns
   {:concentration? :tag :material} or nil. Looking the spell up by the name in
   the field is what keeps this side free of the spell data: the server is handed
   a flat map of field names to values and knows nothing else about them. A name
   it cannot place, a renamed spell or a homebrew one simply gets no marks.

   Drawn rather than written into fields. Measured over 594 annotated rows, the
   marks cost 11 bytes a row drawn against 671 as form fields -- 6.6 KB against
   389 KB, on a branch whose point was making these files smaller."
  [doc annotate]
  (let [rows (->> (spell-row-widgets doc)
                  (keep (fn [{:keys [field rect page]}]
                          (let [v (str (.getValueAsString field))]
                            (when-not (s/blank? v)
                              (when-let [a (annotate v)]
                                {:page page :rect rect :marks a})))))
                  (group-by :page))]
    (doseq [[page items] rows]
      (with-open [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND
                                           true true)]
        (doseq [{:keys [rect marks]} items]
          (let [right (+ (.getLowerLeftX rect) (.getWidth rect) annotation-zone)
                ;; The row was narrowed by annotation-zone, so its own right edge
                ;; is where the columns start rather than where they end.
                base-y (/ (+ (.getLowerLeftY rect) 1.5) 72.0)]
            (doseq [[k text] [[:concentration (when (:concentration? marks) "C")]
                              [:tag (:tag marks)]
                              [:material (:material marks)]]
                    :when text]
              (let [{:keys [dx size bold? grey]} (get annotation-columns k)]
                (draw-text cs text
                           (if bold? HELVETICA_BOLD HELVETICA)
                           size
                           (/ (- right dx) 72.0)
                           base-y
                           [grey grey grey])))))))))

(def ^:private max-relabels-per-section
  "Ten level boxes to a page, so an honest instruction list never exceeds this
   many per section."
  10)

(defn valid-relabel?
  "Whether one caller-supplied relabel instruction may be applied.

   These arrive from the browser, which is where the packing decision is made,
   and reach field names and a drawn label -- so they are checked the way the
   sheet style id is, which reached a resource path before anyone validated it.

   `section` names a page the document actually has, `box` is one of the ten
   level boxes, and `label` is a single digit or nil. nil blanks a box nothing
   uses, which otherwise keeps printing a numeral that reads as a level the
   character does not have."
  [{:keys [section box label]} sections]
  ;; boolean, not the last truthy value: group-by keys on what this RETURNS, and
  ;; re-matches hands back the matched string, so the groups came out keyed "2"
  ;; and nil instead of true and false.
  (boolean
   (and (integer? section) (<= 1 section sections)
        (integer? box) (<= 0 box 9)
        (or (nil? label)
            (and (string? label) (re-matches #"\d" label))))))

(defn packing-supported?
  "Whether `style`'s level numerals can be relabelled, and so whether a packed
   layout may be printed on it. See :packing? in sheet-masters."
  [style]
  (boolean (:packing? (get sheet-masters style))))

(defn apply-relabel-instructions!
  "Renumbers the boxes `instructions` names. Returns [applied refused].

   Refuses rather than throws, and counts what it refused: a malformed list is a
   client sending something this server does not understand, which must not cost
   the character their sheet. The count is returned so the caller can log it.

   Box 0 is the cantrips box and is not a level box -- it has no slot inputs or
   labels until reuse-cantrips-box! gives it some -- so it takes the other path."
  ([doc instructions sections] (apply-relabel-instructions! doc instructions sections 1))
  ([doc instructions sections style]
  (let [wanted (if (packing-supported? style)
                 (take (* sections max-relabels-per-section) (filter map? instructions))
                 ;; An unmeasured style would print the new number beside the old.
                 [])
        {ok true bad false} (group-by #(valid-relabel? % sections) wanted)]
    (doseq [{:keys [section box label]} ok]
      (try
        (if (zero? box)
          (when label (reuse-cantrips-box! doc section label))
          (relabel-spell-level! doc box section (or label "")))
        (catch Exception e
          (println "pdf: relabel failed for box" box "section" section "-" (.getMessage e)))))
    [(count ok) (+ (count bad) (max 0 (- (count (filter map? instructions)) (count wanted))))])))

(def ^:private cantrips-label
  "What the narrow compartment of a packed cantrips bar says."
  "CANTRIPS")

(def ^:private cantrips-label-size 6.5)

(def ^:private cantrips-label-pad
  "Points of clear space to the left of the CANTRIPS label.

   Measured from whatever bounds the word on that bar, which is not the same thing
   on both kinds of box: a level bar's narrow compartment simply opens at its
   SLOTS TOTAL field, while box 0's bar puts a divider at x 51-59, right where the
   compartment borrowed from level 1 begins. Padding from the compartment alone
   left box 0's word two points off its divider while a level box's sat in nine
   points of open bar -- the same number, and visibly different."
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
  (let [natural (* 72 (string-width label HELVETICA_BOLD heading-target-size))]
    (if (<= natural width)
      heading-target-size
      (max heading-floor-size (* heading-target-size (/ width natural))))))

(defn- fit-heading
  "`label` and the size to draw it at, both trimmed to `width`.

   Class names are not a fixed length -- Bard against Eldritch Knight -- and the
   compartment is. Shrinking alone does not solve it: at the 6pt floor \"Eldritch
   Knight\" still measures 43pt against a narrow compartment, and would print
   through the bar's rules. So a name that will not fit even floored is shortened
   to what does, with an ellipsis saying so."
  [label width]
  (let [size (heading-size label width)]
    (if (<= (* 72 (string-width label HELVETICA_BOLD size)) width)
      {:label label :size size}
      (loop [n (dec (count label))]
        (let [candidate (str (s/trimr (subs label 0 (max 0 n))) "\u2026")]
          (cond
            (<= n 1) {:label "\u2026" :size size}
            (<= (* 72 (string-width candidate HELVETICA_BOLD size)) width)
            {:label candidate :size size}
            :else (recur (dec n))))))))

(defn- bar-compartments
  "The bar's two compartments for `box`, as [[x width] [x width]] -- the narrow
   one a level bar gives SLOTS TOTAL, then the wide one it gives SLOTS EXPENDED.

   Read off the live fields rather than written down, so they follow the artwork.
   Box 0 has no slots fields of its own, so it borrows level 1's raised by
   cantrips-box-rise, which is how cantrips-slot-boxes locates the inputs it adds.

   A style with no slots-expended field -- styles 2 and 4 -- has no second rect to
   read, so the wide compartment is taken from the spell row's right edge instead."
  [doc box suffix]
  (let [form (.getAcroForm (.getDocumentCatalog doc))
        ;; Box 0 has no slots fields, so its compartments come from level 1's.
        ;; Only the X is taken: the height comes from the cantrips hexagon, which
        ;; is already raised, and adding the rise again put the text 168pt above
        ;; the bar and left it blank.
        level (if (zero? box) 1 box)
        rect (fn [nm] (some-> (.getField form nm) .getWidgets first .getRectangle))]
    (when-let [total (rect (str "spell-slots-" level "-" suffix))]
      (let [narrow [(.getLowerLeftX total) (.getWidth total)]
            wide (if-let [expended (rect (str "slots-expended-" level "-" suffix))]
                   [(.getLowerLeftX expended) (.getWidth expended)]
                   ;; From the divider to the row's right edge, less a margin.
                   (let [from (+ (.getLowerLeftX total) (.getWidth total) 12.0)]
                     (when-let [row (rect (str "spells-" box "-1-" suffix))]
                       [from (- (+ (.getLowerLeftX row) (.getWidth row)) from 2.0)])))]
        (when wide
          {:narrow narrow :wide wide})))))

(defn- shrink-slots-expended!
  "Moves the SLOTS EXPENDED input's left edge to `x`, so a class name can sit
   beside it in the same compartment.

   Only for the first box of a class that has no cantrips. Its bar is the only
   one that must carry both a heading and a live input the player writes in --
   every other heading sits on a cantrips box, whose slot inputs are meaningless
   and can simply be drawn over."
  [doc box suffix x]
  (when-let [field (some-> (.getAcroForm (.getDocumentCatalog doc))
                           (.getField (str "slots-expended-" box "-" suffix)))]
    (when-let [widget (first (.getWidgets field))]
      (let [r (.getRectangle widget)
            right (+ (.getLowerLeftX r) (.getWidth r))]
        (when (< x (- right 12.0))
          (.setRectangle widget (PDRectangle. (float x) (.getLowerLeftY r)
                                              (float (- right x)) (.getHeight r))))))))

(defn draw-column-heading!
  "Labels a packed cantrips box: CANTRIPS in the narrow compartment, `label` --
   the class holding the column -- centred in the wide one.

   The bar of a CANTRIPS box is the only place with room for this. Scanning for a
   clear band above each box found one above two of the ten, and the sheet is
   dense everywhere else. A cantrips box has no slots, so the two compartments a
   level bar gives SLOTS TOTAL and SLOTS EXPENDED are free there -- which is why
   this is only ever called for a box holding cantrips, and never for one whose
   slot inputs the player writes in.

   Box 0 additionally has CANTRIPS printed into its artwork, in the middle of the
   bar where the class name now goes, so that word is covered before drawing."
  ([doc box suffix label] (draw-column-heading! doc box suffix label nil))
  ([doc box suffix label {:keys [ability dc attack cantrips?]
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
            (when (zero? box)
              ;; The bar's interior is 19.5pt between its rules, where the hexagon
              ;; is 37 tall: patching to the hexagon painted over the rules and
              ;; left the bar looking cut through.
              (.setNonStrokingColor cs (float 1) (float 1) (float 1))
              (.addRect cs (float wx) (float (- middle 9.0)) (float ww) (float 18.0))
              (.fill cs)
              (.setNonStrokingColor cs (float 0) (float 0) (float 0)))
            (when cantrips?
             (let [;; Shrunk only if a style's narrow compartment cannot take the
                  ;; word at its padded position.
                  {csize :size ctext :label}
                  (let [room (- nw cantrips-label-pad 2.0)
                        natural (* 72 (string-width cantrips-label HELVETICA_BOLD
                                                    cantrips-label-size))]
                    (if (<= natural room)
                      {:size cantrips-label-size :label cantrips-label}
                      {:size (max 4.5 (* cantrips-label-size (/ room natural)))
                       :label cantrips-label}))]
              (draw-text cs ctext HELVETICA_BOLD csize
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
                  ;; A level bar has to keep its SLOTS EXPENDED input, so the name
                  ;; takes the left of that compartment and the input is moved to
                  ;; the right of it -- the alternative being a column with no
                  ;; name on it at all, which is what a Paladin had.
                  room (if cantrips? (- ww 6.0) (* ww 0.5))
                  {label :label size :size} (fit-heading label room)
                  lw (* 72 (string-width label HELVETICA_BOLD size))
                  start (if cantrips? (+ wx (/ (- ww lw) 2.0)) (+ wx 4.0))]
              (when-not cantrips?
                (shrink-slots-expended! doc box suffix (+ wx lw 10.0)))
              (draw-text cs label HELVETICA_BOLD size
                         (/ start 72.0)
                         (/ (+ middle (* -0.36 size)) 72.0)
                         [0.15 0.15 0.15])
              (when-not (s/blank? stats)
                ;; Bold and near-black. These are numbers a player reads mid-turn
                ;; -- the save DC of the spell they are casting and what they add
                ;; to hit -- so they are set like the class name rather than like
                ;; the CANTRIPS caption, which is a label nobody needs to find.
                (let [sw (* 72 (string-width stats HELVETICA_BOLD small))]
                  (draw-text cs stats HELVETICA_BOLD small
                             (/ (+ wx (/ (- ww sw) 2.0)) 72.0)
                             ;; Clear of the bar's upper rule, which sits 9.75
                             ;; above its middle.
                             (/ (+ middle 14.5) 72.0)
                             [0.1 0.1 0.1])))))))))))

(defn stamp-site-line!
  "Prints the site line in the bottom-left corner of every page that lacks one.

   `position` is the style's :site-line from sheet-masters. It is not one shared
   spot: the corner carries a flourish on the last page of styles 1 and 2, a panel
   border on style 3's first page and a frame on style 4's, so each style has the
   position that clears all of its own pages.

   `prints-own?` says the style's artwork carries the line on SOME of its pages --
   style 4, and only on its spell pages, leaving the rest to be stamped. It gates
   the per-page text scan so the styles that never print their own do not pay for
   it.

   Appends a content stream per page rather than editing the page's own. Cloned
   spell pages SHARE the master's content stream, so writing into it would print
   the line once per clone on every one of them; PDFBox's append mode leaves the
   shared stream alone and gives each page its own small addition."
  [doc [x y] prints-own?]
  (doseq [[index page] (map-indexed vector (vec (.getPages doc)))
          :when (not (and prints-own? (page-prints-site-line? doc index)))]
    (with-open [cs (PDPageContentStream. doc page PDPageContentStream$AppendMode/APPEND
                                         true true)]
      (draw-text cs site-stamp HELVETICA site-stamp-size x y [0.45 0.45 0.45]))))

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
             ;; The card's own box, less the strip the stamp sits in:
             ;; draw-lines-to-box fills its height to the last line that fits, so
             ;; overflow text prints over the stamp unless the room is taken away.
             (draw-lines-to-box cs
                               remaining-lines
                               (:plain fonts)
                               8
                               (+ x 0.12)
                               (- 11.0 y 0.24)
                               (- box-height 0.2 site-stamp-strip))
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
                          (* box-height 0.8))))
         ;; Every back, whichever branch drew it. The blank one leaves the bottom
         ;; tenth of the card clear below the mark, and the overflow one reserves
         ;; the same strip above.
         (draw-site-stamp! cs fonts x y box-width box-height))))))

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
                 (draw-svg-icon! cs img "clockwise-rotation" (+ x 2.3) (+ y 3.3) 0.15
                                 [0 0 0] (if (and bw? bw-faded?) 0.4 1.0)))
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

(defn- draw-centred-text!
  "Draws `text` centred on `cx`, measuring the string rather than guessing how
   wide it sets. Halving a hardcoded width puts it near the middle and no nearer,
   and the miss moves with the font and the size."
  [cs text font size cx baseline]
  (draw-text cs text font size (- cx (/ (string-width text font size) 2)) baseline))

(def card-layout
  "Where everything sits on a 2.5 x 3.5in item card.

   `:down` values are inches from the card's TOP edge, `:up` values from its
   BOTTOM. Declared in one place rather than spelled out at each draw site, so the
   vertical rhythm can be measured -- dev/measure_item_card.clj reports the gap
   between every pair -- instead of adjusted a sixteenth at a time by eye."
  {:down {:frame 0.09
          :rail 0.245
          :name-three-line 0.32
          :name-two-line 0.36
          :name-one-line 0.46
          :name-height 0.56
          :name-floor 10.5
          :subtitle 1.00
          :badge 0.985
          :rule 1.22
          :rule-under 1.255
          :charge-label 1.39
          :charge-marks 1.55
          :charge-under 1.71
          :body 1.32
          :body-charged 1.79}
   :up   {:ornament 0.185
          :clause 0.60
          :continued 0.70
          :continued-bare 0.30}
   ;; What the body must stop short of, by which of the foot pieces are drawn.
   :body-stops {[true true] 0.80      ; a note and a clause
                [true false] 0.40     ; a note alone
                [false true] 0.68     ; a clause alone
                [false false] 0.28}})

(defn- draw-attunement-badge!
  "A boxed A at the right of the subtitle line, for an item needing attunement.

   The foot says who may attune; this says THAT it must be, at the top where the
   rarity is, because that is the pair of facts anyone sorting a handful of cards
   is actually looking for. A letter rather than an invented glyph: there is no
   artwork for it, and a letter cannot be misread as decoration."
  [cs x y w dy]
  (let [bw 0.155 bh 0.145
        left (- w 0.28 bw)
        c 0.035]
    (.setLineWidth cs (float 0.7))
    (polyline! cs x y [[(+ left c) dy] [(- (+ left bw) c) dy]
                       [(+ left bw) (+ dy c)] [(+ left bw) (- (+ dy bh) c)]
                       [(- (+ left bw) c) (+ dy bh)] [(+ left c) (+ dy bh)]
                       [left (- (+ dy bh) c)] [left (+ dy c)]]
               true)
    (draw-centred-text! cs "A" HELVETICA_BOLD 7
                        (+ x left (/ bw 2))
                        (- 11 y dy bh -0.036))
    (.setLineWidth cs (float 1))))

(defn- draw-centred-lines!
  "Wraps `text` to `width` and centres each line on `cx`, returning what did not
   fit. draw-text-to-box only sets flush left."
  [cs text font size cx top width max-lines]
  (let [lines (split-lines text font size width)
        leading (/ (* size 1.1) 72)]
    (doseq [[i line] (map-indexed vector (take max-lines lines))]
      (draw-centred-text! cs line font size cx (- top (* leading (inc i)))))
    (drop max-lines lines)))

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
  [cs x y w n cy label-y]
  (.setLineWidth cs (float 0.8))
  (if (<= n tickable-charges)
    (let [r 0.052
          gap 0.145
          cx (- (/ w 2) (/ (* gap (dec n)) 2))]
      (draw-centred-text! cs "CHARGES" HELVETICA 5.5 (+ x (/ w 2)) (- 11 y label-y))
      (doseq [i (range n)]
        (circle! cs x y (+ cx (* gap i)) cy r)))
    ;; The rule and the total are centred as one group, not the rule alone: the
    ;; total hangs off its right end and pulls the pair off centre otherwise.
    (let [total (str "/ " n)
          rule-w 0.62
          gap 0.05
          group (+ rule-w gap (string-width total HELVETICA 7))
          left (- (/ w 2) (/ group 2))]
      (draw-centred-text! cs "CHARGES" HELVETICA 5.5 (+ x (/ w 2)) (- 11 y label-y))
      (polyline! cs x y [[left cy] [(+ left rule-w) cy]] false)
      (draw-text cs total HELVETICA 7 (+ x left rule-w gap) (- 11 y cy -0.015))))
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
         :or {flourish :diamonds name-size 13 name-tracking 0.15}} opts
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
               {:keys [down up body-stops]} card-layout
               ;; The charge band sits between the header rule and the body, so
               ;; the description starts lower when there is one and reclaims the
               ;; room when there is not.
               body-top (if charges (:body-charged down) (:body down))
               ;; Whether the description spills is settled before it is drawn, so
               ;; the note saying so has reserved room rather than being squeezed
               ;; in afterwards. Measured against the box WITHOUT the note: adding
               ;; it only shrinks the box, so anything that overflowed still does.
               ;;
               ;; The LINES are kept, not just their count. Splitting a 1300
               ;; character description measures every word against the font and
               ;; costs 4ms and 2MB; doing it once to decide and again inside
               ;; draw-text-to-box to draw threw half of that away on every card.
               body-lines (split-lines body (:plain fonts) 8 (- box-width 0.4))
               capacity (fn [h] (int (dec (/ (* 72 h) (* 8 1.1)))))
               spills? (> (count body-lines)
                          (capacity (- box-height body-top (body-stops [false (some? clause)]))))
               body-bottom (body-stops [spills? (some? clause)])]
           (draw-card-frame! cs x y box-width box-height rarity flourish)
           (draw-rarity-rail! cs x y box-width rarity (:rail down))
           (draw-foot-ornament! cs x y box-width (- box-height (:ornament up)))
           (when clause
             (draw-attunement-badge! cs x y box-width (:badge down)))
           ;; The name is indented further than anything else and set larger, so
           ;; it reads as a title rather than a wide block of type. It gets two
           ;; lines at whatever size fits them: holding the size loses the end of
           ;; "Amulet of Proof against Detection and Location", and a card nobody
           ;; can find in a stack has failed at its only job. The block reserves
           ;; both lines whatever size it lands on, so the rule under the header
           ;; falls level across a sheet and a stack cuts square; a one-line name
           ;; is dropped into the middle of it rather than left on top of a gap.
           ;; Shrinking stops at :name-floor and the name takes a third line
           ;; instead. Without a floor "Instrument of the Bards, Anstruth Harp of
           ;; Deepest Sorrow" set itself at 8.5pt to hold two lines -- smaller
           ;; than the description under it, which is not a title any more.
           (let [face (name-face fonts)
                 width (- box-width 0.56)
                 lines-at (fn [pt] (count (split-lines item-name face pt width)))
                 fits? (fn [pt] (let [n (lines-at pt)]
                                  (and (<= n 3)
                                       (<= (* n (/ (* pt 1.1) 72)) (:name-height down)))))
                 size (or (first (filter fits?
                                         (take-while #(>= % (:name-floor down))
                                                     (iterate #(- % 0.5) name-size))))
                          (:name-floor down))
                 top (condp = (lines-at size)
                       1 (:name-one-line down)
                       2 (:name-two-line down)
                       (:name-three-line down))]
             (.setCharacterSpacing cs (float name-tracking))
             (draw-text-to-box cs item-name face size
                               (+ x 0.28) (- 11.0 y top) width (:name-height down))
             (.setCharacterSpacing cs (float 0)))
           ;; The subtitle stops short of the badge so the two never meet.
           (draw-text-to-box cs (magic-item-subtitle item) (:italic fonts) 7.5
                             (+ x 0.2) (- 11.0 y (:subtitle down))
                             (- box-width (if clause 0.68 0.4)) 0.2)
           ;; A rule and a hairline under it: the header carries two kinds of
           ;; information, so it closes with more than the body's plain divisions.
           (.setLineWidth cs (float 0.9))
           (polyline! cs x y [[0.2 (:rule down)] [(- box-width 0.2) (:rule down)]] false)
           (.setLineWidth cs (float 0.35))
           (polyline! cs x y [[0.2 (:rule-under down)] [(- box-width 0.2) (:rule-under down)]] false)
           (.setLineWidth cs (float 1))
           (when charges
             (draw-charge-track! cs x y box-width charges (:charge-marks down) (:charge-label down))
             (.setLineWidth cs (float 0.35))
             (polyline! cs x y [[0.2 (:charge-under down)] [(- box-width 0.2) (:charge-under down)]] false)
             (.setLineWidth cs (float 1)))
           (let [remaining-desc-lines
                 (draw-lines-to-box cs body-lines (:plain fonts) 8
                                    (+ x 0.2) (- 11.0 y body-top)
                                    (- box-height body-top body-bottom))]
             ;; Centred, like the ornament under it and the note above it. Flush
             ;; left it was the only thing at the foot on its own axis.
             (when clause
               (draw-centred-lines! cs clause (:italic fonts) 6.8
                                    (+ x (/ box-width 2))
                                    (- 11.0 y (- box-height (:clause up)))
                                    (- box-width 0.4) 2))
             ;; A phrase rather than the recharge icon. At the bottom right the
             ;; icon sat on the corner diamond and its arms, every other spot down
             ;; there belongs to the clause or the ornament, and an arrow does not
             ;; say what it means anyway.
             (when (seq remaining-desc-lines)
               (draw-centred-text! cs "continued on the back" (:italic fonts) 6.2
                                   (+ x (/ box-width 2))
                                   (- 11 y (- box-height
                                              (if clause (:continued up) (:continued-bare up))))))
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