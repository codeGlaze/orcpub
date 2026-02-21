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
            [orcpub.dnd.e5.options :as options])
  (:import (org.apache.pdfbox.pdmodel.interactive.form PDCheckBox PDTextField)
           (org.apache.pdfbox.pdmodel PDPage PDDocument PDPageContentStream PDResources)
           ;; PDFBox 3.x: AppendMode enum replaces boolean flags in PDPageContentStream constructor
           ;; Use APPEND when adding content to existing pages (templates)
           ;; APPEND adds new drawing/text operators to the end of the page’s existing content stream, preserving everything already on the page.
           (org.apache.pdfbox.pdmodel PDPageContentStream$AppendMode)
           (org.apache.pdfbox.pdmodel.graphics.image JPEGFactory LosslessFactory)
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

(defn write-fields! [doc fields flatten font-sizes]
  (let [catalog (.getDocumentCatalog doc)
        form (.getAcroForm catalog)
        res (or (.getDefaultResources form) (PDResources.))]
    (.setNeedAppearances form true)
    (.setDefaultResources form res)
    (doseq [[k v] fields]
      (try
        (let [field (.getField form (name k))]
          (when field
            
            (when (and flatten (font-sizes k) (instance? PDTextField field))
              (.setDefaultAppearance field (str "/Helv " " " (font-sizes k) " Tf 0 0 0 rg")))
            (.setValue
             field
             (cond 
               (instance? PDCheckBox field) (if v "Yes" "Off")
               (instance? PDTextField field) (str v)
               :else nil))))
        (catch Exception e (prn "failed writing field: " k v (strace/print-stack-trace e)))))
    (when flatten
      (.setNeedAppearances form false)
      (.flatten form))))

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

(def user-agent "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.22 (KHTML, like Gecko) Chrome/25.0.1364.172")

(defn draw-non-jpg [doc page url x y width height]
  (try
    (with-open [c-stream (content-stream doc page)]
      (let [connection (doto (.openConnection (URL. url))
                         (.setRequestProperty "User-Agent" user-agent)
                         (.setConnectTimeout 10000)
                         (.setReadTimeout 10000))
            buff-image (ImageIO/read (.getInputStream connection))]
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
                image-stream (.getInputStream
                               (doto
                                 (.openConnection (URL. url))
                                 (.setRequestProperty "User-Agent" user-agent)
                                 (.setConnectTimeout 10000)
                                 (.setReadTimeout 10000)))]
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

(defn draw-lines-to-box [cs lines font font-size x y height]
  (let [leading (* font-size 1.1)
        max-lines (dec (/ (* 72 height) leading))
        units-x (* 72 x)
        units-y (* 72 y)
        fitting-lines (vec (take max-lines lines))]
    (.beginText cs)
    (.setFont cs font font-size)
    (.moveTextPositionByAmount cs units-x units-y)
    (doseq [i (range (count fitting-lines))]
      (let [line (get fitting-lines i)]
        (.moveTextPositionByAmount cs 0 (- leading))
        (.drawString cs line)))
    (.endText cs)
    (vec (drop max-lines lines))))

(defn draw-text-to-box [cs text font font-size x y width height]
  (let [lines (split-lines text font font-size width)]
    (draw-lines-to-box cs lines font font-size x y height)))

(defn set-text-color [cs r g b]
  (.setNonStrokingColor cs r g b))

(defn draw-text [cs text font font-size x y & [color]]
  (when text
    (let [units-x (* 72 x)
          units-y (* 72 y)]
      (.beginText cs)
      (.setFont cs font font-size)
      (when color
        (apply set-text-color cs color))
      (.moveTextPositionByAmount cs units-x units-y)
      (.drawString cs (if (keyword? text) (common/safe-name text) text))
      (when color
        (set-text-color cs 0 0 0))
      (.endText cs))))

(defn draw-text-from-top [cs text font font-size x y & [color]]
  (draw-text cs text font font-size x (- 11.0 y) color))

(defn draw-line [cs start-x start-y end-x end-y]
  (.drawLine cs start-x start-y end-x end-y))

(defn inches-to-units [inches]
  (float (* inches 72)))

(defn draw-line-in [cs & coords]
  (apply draw-line cs (map inches-to-units coords)))

(defn draw-grid [cs box-width box-height]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)]
    (.setStrokingColor cs 225 225 225)
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
    (.setStrokingColor cs 0 0 0)))

(defn spell-school-level [{:keys [level school]} class-nm]
  (let [school-str (if school (s/capitalize school) "Unknown")]
    (if (and level (zero? level))
      (str class-nm " Cantrip " school-str)
      (str class-nm " Level " (or level "?") " " school-str))))

(defn draw-spell-field [cs document title value x y]
  (with-open [img-stream (io/input-stream (io/resource (str "public/image/" title ".png")))]
    (draw-imagex cs
                 (LosslessFactory/createFromImage document (ImageIO/read img-stream))
                 x
                 (- 11 y 0.12)
                 0.25
                 0.25))
  (.setNonStrokingColor cs 0 0 0)
  (draw-text cs
             value
             HELVETICA_BOLD_OBLIQUE
             8
             x
             (- y 0.07))
  (.setNonStrokingColor cs 0 0 0))

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

(defn print-backs [cs document box-width box-height remaining-lines-vec page-number]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)
        fonts (load-fonts document)]
    (with-open [img-stream (io/input-stream (io/resource "public/image/card-logo.png"))
                over-img-stream (io/input-stream (io/resource "public/image/clockwise-rotation.png"))]
      (let [img (LosslessFactory/createFromImage document (ImageIO/read img-stream))
            over-img (LosslessFactory/createFromImage document (ImageIO/read over-img-stream))]
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
             (when (seq remaining-lines)
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
                               (- box-height 0.2))))))))))

(defn print-spells [cs document box-width box-height spells page-number print-spell-card-dc-mod?]
  (let [num-boxes-x (int (/ 8.5 box-width))
        num-boxes-y (int (/ 11.0 box-height))
        total-width (* num-boxes-x box-width)
        total-height (* num-boxes-y box-height)
        remaining-width (- 8.5 total-width)
        margin-x (/ remaining-width 2)
        remaining-height (- 11.0 total-height)
        margin-y (/ remaining-height 2)
        fonts (load-fonts document)]
    (with-open [card-logo-img-stream (io/input-stream (io/resource "public/image/card-logo.png"))
                over-img-stream (io/input-stream (io/resource "public/image/clockwise-rotation.png"))]
      (let [card-logo-img (LosslessFactory/createFromImage document (ImageIO/read card-logo-img-stream))
            over-img (LosslessFactory/createFromImage document (ImageIO/read over-img-stream))]
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
               (draw-imagex cs
                            card-logo-img
                            (+ x 1.9)
                            (+ y 0.02)
                            1.0
                            0.25)
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
                                   document
                                   "magic-swirl"
                                   (str (abbreviate-casting-time
                                         (first
                                          (s/split
                                           casting-time
                                           #","))))
                                   (+ x 0.12)
                                   (- 11.0 y 0.45)))
               (when range
                 (draw-spell-field cs
                                   document
                                   "arrow-dunk"
                                   (abbreviate-range range)
                                   (+ x 0.62)
                                   (- 11.0 y 0.45)))
               (draw-spell-field cs
                                 document
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
                                 (- 11.0 y 0.45))
               (when duration
                 (draw-spell-field cs
                                   document
                                   "sands-of-time"
                                   (abbreviate-duration duration)
                                   (+ x 1.62)
                                   (- 11.0 y 0.45)))
               (when (seq remaining-desc-lines)
                 (draw-imagex cs
                              over-img
                              (+ x 2.3)
                              (+ y 3.3)
                              0.15
                              0.15))
               {:remaining-lines remaining-desc-lines
                :spell-name spell-name}))))))))

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