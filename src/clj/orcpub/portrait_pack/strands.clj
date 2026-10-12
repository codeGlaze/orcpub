(ns orcpub.portrait-pack.strands
  "Each hair piece's strand field (which way its drawn strands run), worked out from the art and
   written beside it as <name>.strands.png. The image build runs -main, so a deploy needs only the
   art: lein run -m orcpub.portrait-pack.strands [art-dir] [--all]. Derived from the art, so never
   committed; a piece without one has it worked out by the server instead."
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

(defn- current?
  "Whether `strands` is there and at least as new as the art it was made from."
  [^File art ^File strands]
  (and (.exists strands) (>= (.lastModified strands) (.lastModified art))))

(defn write-missing!
  "Write the strand fields under `dir` that are missing or older than their art (every one with
   `all?`). Returns {[layer file] :written | :kept | :no-art}."
  [dir all?]
  (into (sorted-map)
        (for [k pa/layer-order :when (fx/hair-layer? k)
              a (pa/assets-for-layer k)
              :let [file-name (last (s/split (:asset/url a) #"/"))]]
          [[k file-name]
           (if-let [f (find-file (io/file dir (name k)) file-name)]
             (let [out (io/file (.getParentFile f) (s/replace (.getName f) #"(?i)\.png$" ".strands.png"))]
               (if (and (not all?) (current? f out))
                 (do (println (format "  %-12s %-24s up to date" (name k) (.getName out))) :kept)
                 (let [t (System/nanoTime)
                       field (field-of (ImageIO/read f) k)]
                   (ImageIO/write (field->image field) "png" out)
                   (println (format "  %-12s %-24s %dx%d  %4dms" (name k) (.getName out) (:gw field) (:gh field)
                                    (long (/ (- (System/nanoTime) t) 1e6))))
                   :written)))
             (do (println (format "  %-12s %-24s no art -- skipped" (name k) file-name)) :no-art))])))

(defn -main [& args]
  (let [all? (boolean (some #{"--all"} args))
        dir (or (first (remove #{"--all"} args)) default-dir)
        results (vals (write-missing! dir all?))]
    (println (format "Strand fields in %s: %d written, %d up to date, %d without art"
                     dir (count (filter #{:written} results)) (count (filter #{:kept} results))
                     (count (filter #{:no-art} results))))
    (shutdown-agents)))
