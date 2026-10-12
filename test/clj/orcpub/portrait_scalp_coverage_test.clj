(ns orcpub.portrait-scalp-coverage-test
  "Point ORCPUB_PACK at an unpacked art pack (<layer-key>/<file>.png) and this checks every hair
   combination the randomizer can pick for an island of bare scalp. Skips without a pack: the
   art is not in this repository. The galleries this came from are in dev/orcpub/portrait_gallery.clj."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.portrait-layout :as layout]
            [clojure.string :as s])
  (:import [java.awt RenderingHints]
           [java.awt.image BufferedImage]
           [java.io File]
           [javax.imageio ImageIO]))

(def ^:private W 464)
(def ^:private H 638)

(defn- pack-dir []
  (when-let [d (System/getenv "ORCPUB_PACK")]
    (let [f (File. d)] (when (.isDirectory f) f))))

(defn- assets-in [^File pack layer-key]
  (let [d (File. pack (name layer-key))]
    (when (.isDirectory d)
      (->> (.listFiles d)
           (filter #(re-find #"(?i)\.png$" (.getName ^File %)))
           (sort-by #(.getName ^File %))
           vec))))

(defn- px
  "The backing int[] of a TYPE_INT_ARGB image.

   .getRGB/.setRGB go through the ColorModel on every call. These loops run
   over ~300k pixels per layer per variant, so that indirection was most of
   the render time."
  ^ints [^BufferedImage img]
  (.. img getRaster getDataBuffer getData))

(defn- scaled*
  "The asset placed in the frame the way both renderers place it."
  ^BufferedImage [^File f]
  (when-let [src (ImageIO/read f)]
    (let [out (BufferedImage. W H BufferedImage/TYPE_INT_ARGB)
          g (.createGraphics out)
          [x y dw dh] (layout/contain-rect (.getWidth src) (.getHeight src) W H)]
      (try
        (.setRenderingHint g RenderingHints/KEY_INTERPOLATION
                           RenderingHints/VALUE_INTERPOLATION_BILINEAR)
        (.drawImage g src (int x) (int y) (int dw) (int dh) nil)
        (finally (.dispose g)))
      out)))

(def ^:private scaled
  "Memoized: a sweep composes the same ten layers dozens of times over, and
   every call was an ImageIO/read off disk plus a bilinear rescale."
  (memoize scaled*))

;; ---------------------------------------------------------------------------
;; Exposed scalp.
;;
;; One gallery bust showed an island of skin between the bangs and the side
;; lock. It is not a tinting or a z-order fault: hair-front 01 is a crown cap
;; 142px wide and hair-front 02 is a side lock 231px wide that does not cover
;; the crown, so some combinations leave the top of the head bare.
;;
;; Only five layers can cover the skull, so the space is 3 heads x 2 hair-bits
;; x 2 hair-back x 3 hair-front x 3 bangs = 108, small enough to check ALL of
;; it rather than the one bust that happened to be looked at.
;; ---------------------------------------------------------------------------

(def ^:private skull-bottom
  "Below this the head is a face. Bare skin there is a forehead, not a hole."
  (int (* H 0.30)))

(defn- exposed-head-mask
  "Head pixels, above the brow, that no hair layer covers."
  ^booleans [^File head ^File bits ^File back ^File front ^File bangs]
  (let [h (px (scaled head))
        covers (mapv #(px (scaled %)) (remove nil? [bits back front bangs]))
        out (boolean-array (* W H))]
    (dotimes [y skull-bottom]
      (dotimes [x W]
        (let [i (+ x (* y W))]
          (when (and (pos? (bit-and (unsigned-bit-shift-right (aget h i) 24) 0xff))
                     (not-any? (fn [^ints c]
                                 (pos? (bit-and (unsigned-bit-shift-right (aget c i) 24) 0xff)))
                               covers))
            (aset out i true)))))
    out))

(defn- island-px
  "Exposed head that does NOT reach the face -- skin surrounded by hair.

   Counting exposed pixels outright was the wrong measure and said so loudly:
   it called a short fringe the worst case in the pack at 3899px, because a
   high fringe shows more FOREHEAD, and a forehead is supposed to be skin.
   What reads as a fault is an ISLAND -- a patch with hair on every side and no
   path down to the face. So the exposed region is flooded from the brow line,
   and whatever the flood cannot reach is the defect."
  [^booleans exposed]
  (let [seen (boolean-array (* W H))
        stack (java.util.ArrayDeque.)]
    ;; every exposed pixel on the brow line is forehead, and so is anything
    ;; connected to one
    (dotimes [x W]
      (let [i (+ x (* (dec skull-bottom) W))]
        (when (and (aget exposed i) (not (aget seen i)))
          (aset seen i true)
          (.push stack (int i)))))
    (while (not (.isEmpty stack))
      (let [i (int (.pop stack))
            x (rem i W) y (quot i W)]
        (doseq [[dx dy] [[-1 0] [1 0] [0 -1] [0 1]]]
          (let [nx (+ x dx) ny (+ y dy)]
            (when (and (< -1 nx W) (< -1 ny skull-bottom))
              (let [ni (+ nx (* ny W))]
                (when (and (aget exposed ni) (not (aget seen ni)))
                  (aset seen ni true)
                  (.push stack (int ni)))))))))
    (count (for [i (range (* W H))
                 :when (and (aget exposed i) (not (aget seen i)))]
             1))))

(defn- exposed-scalp-px
  [^File head ^File bits ^File back ^File front ^File bangs]
  (island-px (exposed-head-mask head bits back front bangs)))

(def ^:private known-scalp-islands
  "Hair combinations the art does not cover, as [hair-front bangs] -- the head
   makes no difference to any of them.

   This is a ledger, not a suppression. It exists so the test fails on a NEW
   gap, which is the thing nobody will notice: 108 combinations is more than
   anyone looks at, and every one of them renders without complaint.

   Fixing these is the illustrator's call, not code's. Either the fringe is
   redrawn to meet the side lock, or the randomizer learns to avoid the pairs."
  ;; hair front 02 + bangs 03 left ~207px between fringe and lock until hair
  ;; front 02 was patched to meet it (pack v1.775). Bangs 01 leaves slivers of
  ;; skin between its strand tips at the hairline, which read as a fringe and
  ;; are kept as drawn.
  #{["L4_hair_front_01.png" "L9_bangs_01.png"]   ; ~87px at the crown
    ["L4_hair_front_02.png" "L9_bangs_01.png"]
    ["l4_hair_front_03.png" "L9_bangs_01.png"]})

(deftest no-combination-leaves-a-bare-scalp
  (if-let [pack (pack-dir)]
    (let [heads (assets-in pack :head)
          bits (assets-in pack :hair-bits)
          backs (assets-in pack :hair-back)
          fronts (assets-in pack :hair-front)
          bangss (assets-in pack :bangs)
          results (for [hd heads bt bits bk backs ft fronts bg bangss]
                    {:combo [(.getName ^File hd) (.getName ^File ft) (.getName ^File bg)]
                     :px (exposed-scalp-px hd bt bk ft bg)})
          bad (->> results
                   (filter #(> (:px %) 40))
                   (remove #(contains? known-scalp-islands (vec (rest (:combo %)))))
                   (sort-by (comp - :px))
                   distinct)
          ledger-hits (->> results
                           (filter #(> (:px %) 40))
                           (map #(vec (rest (:combo %))))
                           set)]
      (println (format "\n  %d skull combinations; %d leave an island of scalp"
                       (count results) (count bad)))
      (doseq [{:keys [combo px]} (take 10 bad)]
        (println (format "    %5d px  %s" px (s/join " + " combo))))
      (println (format "    worst %d px, median %d px\n"
                       (:px (first (sort-by (comp - :px) results)))
                       (nth (sort (map :px results)) (quot (count results) 2))))

      (testing "a hairstyle that leaves a patch of scalp showing between the
                bangs and the hair is a combination the randomizer can hand
                someone, and 108 of them can be checked rather than noticed"
        (is (empty? bad)
            (str (count bad) " NEW hair combinations leave an island of scalp "
                 "surrounded by hair. The art does not cover them -- it is not "
                 "a rendering fault, and it needs either redrawn art or a "
                 "randomizer that avoids the pair. Add to known-scalp-islands "
                 "only once that decision is made: " (pr-str (map :combo bad)))))

      (testing "and the ledger does not rot. A pair that stops leaving a gap --
                because the art was redrawn -- should be taken off the list,
                not left behind to make the next real gap look expected."
        (doseq [pair known-scalp-islands]
          (is (contains? ledger-hits pair)
              (str (pr-str pair) " is listed as a known scalp gap but no longer "
                   "leaves one; remove it from known-scalp-islands")))))
    (println "\n  ORCPUB_PACK not set -- skipping.\n")))

