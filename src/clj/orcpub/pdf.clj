(ns orcpub.pdf
  "PDF generation utilities for character sheets, spell cards, and monster stat blocks.
   
   ## PDFBox 3.x Migration Notes (January 2026)
   
   This namespace was updated from PDFBox 2.x to 3.x. Key API changes:
   
   1. **Standard fonts** - In PDFBox 2.x, fonts were static fields like `PDType1Font/HELVETICA`.
      In PDFBox 3.x, you must create font instances using the `Standard14Fonts$FontName` enum:
      ```clojure
      ;; Old (2.x): PDType1Font/HELVETICA
      ;; New (3.x): (PDType1Font. Standard14Fonts$FontName/HELVETICA)
      ```
      We define these as module-level constants (HELVETICA, HELVETICA_BOLD, etc.) for convenience.
   
   2. **Loading PDFs** - In PDFBox 2.x, use `PDDocument/load`. In 3.x, use `Loader/loadPDF`.
      See routes.clj for this change.
   
   3. **Java interop syntax** - The `$` in `Standard14Fonts$FontName` is Clojure's way of
      accessing a Java nested/inner class. `Standard14Fonts.FontName` in Java becomes
      `Standard14Fonts$FontName` in Clojure imports."
  (:require [clojure.string :as s]
            [clojure.stacktrace :as strace]
            [clojure.java.io :as io]
            [orcpub.common :as common]
            [orcpub.dnd.e5.display :as dis5e]
            [orcpub.dnd.e5.monsters :as monsters]
            [orcpub.dnd.e5.options :as options]
            [clj-http.client :as client])
  (:import (org.apache.pdfbox.pdmodel.interactive.form PDCheckBox PDTextField PDTerminalField)
           (org.apache.pdfbox.pdmodel.interactive.annotation PDAnnotationWidget)
           (org.apache.pdfbox.cos COSName)
           (org.apache.pdfbox.pdmodel PDPage PDDocument PDPageContentStream PDResources)
           ;; PDFBox 3.x: AppendMode enum replaces boolean flags in PDPageContentStream constructor
           ;; Use APPEND when adding content to existing pages (templates)
           ;; APPEND adds new drawing/text operators to the end of the page’s existing content stream, preserving everything already on the page.
           (org.apache.pdfbox.pdmodel PDPageContentStream$AppendMode)
           (org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory)
           (org.apache.pdfbox.pdmodel.graphics.state PDExtendedGraphicsState)
           ;; PDFBox 3.x: Standard14Fonts$FontName is a nested enum class
           ;; In Java: org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName
           ;; In Clojure: use $ to access nested classes
           (org.apache.pdfbox.pdmodel.font PDType1Font PDFont PDType0Font Standard14Fonts$FontName)
           (javax.imageio ImageIO)
           (java.net URL)))

;; =============================================================================
;; Standard PDF Fonts (PDFBox 3.x)
;; =============================================================================
;;
;; PDFBox 3.x changed how standard fonts are accessed:
;;   - OLD (2.x): PDType1Font/HELVETICA (static field)
;;   - NEW (3.x): (PDType1Font. Standard14Fonts$FontName/HELVETICA) (constructor + enum)
;;
;; We create these constants at load time so the rest of the code can use them
;; like the old static fields. These are the standard "Base 14" PDF fonts that
;; are guaranteed to be available in all PDF readers.
;;
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


;; ─── Orphaned widgets ────────────────────────────────────────────────────────
;;
;; The style 1 templates were made by taking a nine-page master and deleting
;; pages for each variant. Deleting a page removes the page and its annotations
;; but leaves the FIELDS in the AcroForm, so a widget can survive pointing at no
;; page at all. On a real level 20 export that is 1596 of 1931 widgets, and
;; roughly 88% of a 2.6 MB download -- the artwork is only 329 KB of it.
;;
;; Such a widget can never be drawn or filled, so dropping it is lossless.
;; Measured on a production export: 1407 fields / 2679 KB -> 333 / 1313 KB, all
;; 248 valued fields present and unchanged, no page differing by a pixel.
;;
;; NOTE this is deliberately per-WIDGET. Several fields own widgets on more than
;; one page; keeping a whole field because any one widget is live leaves the
;; rest orphaned (that mistake left 101 behind). See docs/kb/pdf-form-techniques.md.

(defn- on-page-widgets
  "The set of annotation COS objects actually reachable from some page."
  [doc]
  (into #{}
        (for [page (.getPages doc)
              annotation (.getAnnotations page)]
          (System/identityHashCode (.getCOSObject annotation)))))

(defn prune-orphan-widgets!
  "Drop widgets that belong to no page, then fields left with no widgets.
   Returns the number of widgets removed. Lossless: an off-page widget cannot
   render or be filled."
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
                             ;; Only terminal fields own widgets directly.
                             (instance? PDTerminalField field)
                             (do (.setWidgets field (java.util.ArrayList. good))
                                 (conj acc field))
                             :else (conj acc field))))
                       [] (vec (.getFields form)))]
      (.setFields form (java.util.ArrayList. kept))
      @removed)
    0))

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

   RETURNS the sorted seq of field names the template had no field for, and logs
   them. These used to be skipped in silence, which is how several faults went
   unnoticed for years: styles 2-4 ship without a `backstory` field so every
   character's backstory vanished from those sheets, `spells-3-11` is missing from
   style 1 so a wizard with a full third-level list quietly loses one, and a
   character with more than six spellcasting classes loses two of them entirely
   (50 values on an eight-class build). Every one of those is a silent skip.
   Callers that care should check the return value; nobody could check nil."
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
    (let [unplaceable (sort (map name (remove #(some? (.getField form (name %))) (keys fields))))]
      (when (seq unplaceable)
        (println (format "pdf/write-fields!: %d value(s) had no field in this template and were dropped: %s"
                         (count unplaceable) (s/join ", " unplaceable))))
      (doseq [[k v] fields]
      (try
        (let [field (.getField form (name k))]
          (when field
            ;; font-sizes gated on flatten?: interactive forms keep the template's
            ;; `/Helv 0 Tf` auto-sizing; flattening bakes a concrete size, so we
            ;; rewrite the DA to the caller's size first.
            (when (and flatten? (font-sizes k) (instance? PDTextField field))
              (.setDefaultAppearance field (str "/Helv " " " (font-sizes k) " Tf 0 0 0 rg")))
            (.setValue
             field
             (cond
               (instance? PDCheckBox field) (if v "Yes" "Off")
               (instance? PDTextField field) (normalize-text v)
               :else nil))))
        (catch Exception e (prn "failed writing field: " k v (strace/print-stack-trace e)))))
      ;; Drop widgets that belong to no page. Done AFTER writing so a value is
      ;; never written into a field that is about to disappear, and skipped when
      ;; flattening because .flatten consumes the form anyway. Halves the file on
      ;; a real export; see prune-orphan-widgets!.
      (if flatten?
        (do (fix-widget-page-refs! doc)
            (.flatten form))
        (prune-orphan-widgets! doc))
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
    (let [u (java.net.URL. url)
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
  (let [^java.net.HttpURLConnection conn (.openConnection (java.net.URL. url))]
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

(defn string-width [text ^PDFont font font-size]
  (if text
    (/ (* (/ (.getStringWidth font (if (keyword? text) (common/safe-name text) text)) 1000.0) font-size) 72)
    0))

(defn split-lines [text ^PDFont font font-size width]
  (let [words (s/split text #"\s")]
    (loop [lines []
           current-line nil
           [next-word & remaining-words :as current-words] words]
      (if next-word
        (let [line-with-word (str current-line (when current-line " ") next-word)
              new-width (string-width line-with-word font font-size)]
          (if (> new-width width)
            (recur (conj lines current-line)
                   nil
                   current-words)
            (recur lines
                   line-with-word
                   remaining-words)))
        (if current-line
          (conj lines current-line)
          lines)))))

;; ─── Fitting text to a box ───────────────────────────────────────────────────
;;
;; These fields AUTO-SIZE. The template default appearance is `/Helv 0 Tf`, and
;; 0 means "shrink to fit", so an overlong value is NOT cropped at some fixed
;; length -- it gets smaller. PDFBox stops shrinking at 4pt and only then starts
;; clipping, so a wordy field produces a sheet that is illegible before it starts
;; losing text, with nothing to warn about either.
;;
;; The cutoff therefore has to be a minimum readable size, not a character count.
;; Measured budgets at 7pt: ideals/bonds/flaws 25 words, personality-traits 44,
;; attacks-and-spellcasting 127, other-profs 147, equipment list 447, backstory
;; 987, features-and-traits-2 3369. See docs/kb/pdf-form-techniques.md.

(def min-font-size
  "Smallest point size worth printing. 7pt is comfortable, 6pt is small but
   readable, below 5pt is not worth the ink. Text that would need less than this
   spills to a continuation page rather than shrinking further."
  7.0)

(def ^:private line-height-factor
  "Leading as a multiple of font size, matching draw-lines-to-box."
  1.1)

(defn widget-box
  "The drawable [width height] of `field`, in POINTS, or nil if it has none.

   Measures a widget that is actually on a page. Every field in these templates
   carries two widgets and the FIRST is the orphaned one, so the obvious
   `(first (.getWidgets field))` measures the wrong box -- that mistake produced
   a capacity table off by a factor of six."
  [doc field]
  (let [live (on-page-widgets doc)]
    (when-let [w (first (filter #(contains? live (System/identityHashCode (.getCOSObject %)))
                                (.getWidgets field)))]
      (let [r (.getRectangle w)]
        ;; 2pt inset per side, matching the appearance stream's padding.
        [(- (.getWidth r) 4.0) (- (.getHeight r) 4.0)]))))

(defn fit-text
  "Split `text` into what fits in a `width` x `height` point box at `size`, and
   what does not.

   Returns {:head <fits> :tail <remainder, nil when it all fits> :lines <count>}.
   Callers spill :tail onto a continuation page instead of letting the field
   auto-shrink below min-font-size.

   Wrapping reuses split-lines, whose width argument is in INCHES because
   string-width divides by 72 -- hence the conversion here."
  ([text width height] (fit-text text width height min-font-size))
  ([text width height size]
   (if (s/blank? (str text))
     {:head "" :tail nil :lines 0}
     (let [lines (vec (split-lines (str text) HELVETICA size (/ width 72.0)))
           per-box (max 1 (int (Math/floor (/ height (* size line-height-factor)))))]
       (if (<= (count lines) per-box)
         {:head (s/join " " lines) :tail nil :lines (count lines)}
         {:head (s/join " " (take per-box lines))
          :tail (s/join " " (drop per-box lines))
          :lines per-box})))))

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