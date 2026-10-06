(ns orcpub.portrait-pack.strands
  "Work out each hair piece's strand field once and write it beside the art.

     lein run -m orcpub.portrait-pack.strands                      # resources/public/image/portraits
     lein run -m orcpub.portrait-pack.strands /path/to/portraits   # a pack somewhere else

   The field says which way the strands run, read from the linework, so hair
   streaks can follow them (portrait-effects/strand-field). It depends on the
   art alone, so there is no reason to work it out on every server start or in
   every visitor's browser: this writes <name>.strands.png -- a small
   greyscale image, one pixel per few of the art's -- next to each hair piece,
   and the renderers read that. Re-run it when the art changes. A piece with
   no file still streaks; the renderer works its field out once instead.

   The files are derived from the art, so they travel with the art and are
   never committed with the code."
  (:require [clojure.java.io :as io]
            [clojure.string :as s]
            [com.stuartsierra.component :as component]
            [orcpub.dnd.e5.portrait-assets :as pa]
            [orcpub.dnd.e5.portrait-effects :as fx])
  (:import [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]))

(def default-dir "resources/public/image/portraits")

(defn- find-file
  "The art file for `file-name` in `dir`, matched without regard to case."
  ^File [dir file-name]
  (let [d (io/file dir)]
    (when (.isDirectory d)
      (first (filter #(= (.toLowerCase (.getName ^File %) java.util.Locale/ROOT) (.toLowerCase (str file-name) java.util.Locale/ROOT)) (.listFiles d))))))

(defn field-of
  "The strand field for a decoded piece of art."
  [^BufferedImage src layer-key]
  (let [w (.getWidth src) h (.getHeight src)
        argb (BufferedImage. w h BufferedImage/TYPE_INT_ARGB)
        g (.createGraphics argb)
        _ (do (.drawImage g src 0 0 nil) (.dispose g))
        ^ints d (.. argb getRaster getDataBuffer getData)]
    (fx/strand-field (fn [i] (bit-and (aget d (int i)) 0xffffff))
                     (fn [i] (bit-and (unsigned-bit-shift-right (aget d (int i)) 24) 0xff))
                     w h (fx/piece-seed layer-key))))

(defn field->image
  "The field as the greyscale image the renderers read."
  ^BufferedImage [{:keys [gw gh] :as field}]
  (let [img (BufferedImage. gw gh BufferedImage/TYPE_BYTE_GRAY)]
    (.setDataElements (.getRaster img) 0 0 gw gh (fx/field->bytes field))
    img))

(defn missing
  "The hair pieces the server can serve whose strand field it cannot: art on
   the classpath with no .strands.png beside it. Each would be worked out
   in every visitor's browser instead."
  []
  (for [k pa/layer-order :when (fx/hair-layer? k)
        a (pa/assets-for-layer k)
        :let [url (:asset/url a)]
        :when (and (s/starts-with? (str url) "/")
                   (io/resource (str "public" url))
                   (not (io/resource (str "public" (fx/strands-url url)))))]
    url))

(defrecord StrandCheck []
  component/Lifecycle
  (start [this]
    ;; one line, so a deploy that forgot the step says so in its log
    (try
      (let [m (vec (missing))]
        (when (seq m)
          (println (str "portrait strands: " (count m) " hair piece(s) have no .strands.png ("
                        (s/join ", " (map #(last (s/split % #"/")) m))
                        "); run lein run -m orcpub.portrait-pack.strands <art-dir>"))))
      (catch Exception e (println "portrait strands: not checked -" (.getMessage e))))
    this)
  (stop [this] this))

(defn new-strand-check [] (->StrandCheck))

(defn -main [& args]
  (let [dir (or (first args) default-dir)
        pieces (for [k pa/layer-order :when (fx/hair-layer? k)
                     a (pa/assets-for-layer k)]
                 [k (last (s/split (:asset/url a) #"/"))])]
    (println "Strand fields for" (count pieces) "hair pieces in" dir)
    (doseq [[k file-name] pieces]
      (if-let [f (find-file (io/file dir (name k)) file-name)]
        (let [t (System/nanoTime)
              field (field-of (ImageIO/read f) k)
              out (io/file (.getParentFile f) (s/replace (.getName f) #"(?i)\.png$" ".strands.png"))]
          (ImageIO/write (field->image field) "png" out)
          (println (format "  %-12s %-24s %dx%d  %4dms" (name k) (.getName out) (:gw field) (:gh field)
                           (long (/ (- (System/nanoTime) t) 1e6)))))
        (println (format "  %-12s %-24s missing -- skipped" (name k) file-name))))
    (shutdown-agents)))
