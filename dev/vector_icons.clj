;; Draft vector replacements for the five raster card icons, drawn beside the
;; PNGs they would replace so the two can be compared before anything is wired in.
;;
;;   lein run -m clojure.main dev/vector_icons.clj
;;   OUT=/tmp/icons.pdf lein run -m clojure.main dev/vector_icons.clj
;;
;; Nothing here is used by the exporter. The drawing functions live in this file
;; rather than orcpub.pdf on purpose: they are a proposal, not a decision.
;;
;; Why bother: a page of nine spell cards draws 49 raster images and costs 6.92 MB
;; and 20.2 ms per card, against 3.94 MB and 13.9 ms for the all-vector item
;; cards. Vector also stays sharp at any size and prints properly in black and
;; white, where a 32px PNG scaled to a third of an inch does not.

(require '[orcpub.pdf :as pdf])
(import '[org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream]
        '[org.apache.pdfbox.pdmodel.font PDType1Font Standard14Fonts$FontName]
        '[java.io File])

(def U 72.0)                                  ; PDF units per inch
(def K 0.5522847)                             ; circle-from-beziers constant

(defn- pt [x y] [(float (* U x)) (float (* U y))])

(defn- path! [cs pts close? fill?]
  (let [[[px py] & more] pts]
    (.moveTo cs px py)
    (doseq [[x y] more] (.lineTo cs x y))
    (cond fill? (do (.closePath cs) (.fill cs))
          close? (.closeAndStroke cs)
          :else (.stroke cs))))

(defn- arc!
  "Stroked circular arc, centre (cx cy), radius r, from a1 to a2 radians."
  [cs cx cy r a1 a2]
  (let [steps 28
        step (/ (- a2 a1) steps)
        p (fn [i] (let [a (+ a1 (* step i))]
                    (pt (+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a))))))]
    (path! cs (map p (range (inc steps))) false false)))

(defn- arrowhead!
  "Filled triangle of side `s` at (x y), pointing along `angle`."
  [cs x y s angle]
  (let [tip (fn [a d] [(+ x (* d (Math/cos a))) (+ y (* d (Math/sin a)))])
        [ax ay] (tip angle s)
        [bx by] (tip (+ angle 2.4) (* s 0.9))
        [cx2 cy2] (tip (- angle 2.4) (* s 0.9))]
    (path! cs [(pt ax ay) (pt bx by) (pt cx2 cy2)] true true)))

;; ── the five icons, each drawn centred on (cx cy) at `s` inches across ───────

(defn hourglass!
  "Duration. Two bowls meeting at a waist, between capping bars."
  [cs cx cy s]
  (let [h (/ s 2) w (* s 0.36)]
    (path! cs [(pt (- cx w) (+ cy h)) (pt (+ cx w) (+ cy h))] false false)
    (path! cs [(pt (- cx w) (- cy h)) (pt (+ cx w) (- cy h))] false false)
    (path! cs [(pt (- cx w) (+ cy h)) (pt (+ cx w) (+ cy h)) (pt cx cy)] true false)
    (path! cs [(pt (- cx w) (- cy h)) (pt (+ cx w) (- cy h)) (pt cx cy)] true false)
    ;; the grain that has already run through
    (path! cs [(pt cx (- cy (* h 0.15))) (pt cx (- cy (* h 0.8)))] false false)))

(defn spiral!
  "Casting time. A coil drawn inward, standing in for the hand and swirl."
  [cs cx cy s]
  (let [turns 2.25 steps 90 r0 (/ s 2)
        p (fn [i] (let [t (/ i (double steps))
                        a (* t turns 2 Math/PI)
                        r (* r0 (- 1 (* t 0.82)))]
                    (pt (+ cx (* r (Math/cos a))) (+ cy (* r (Math/sin a))))))]
    (path! cs (map p (range (inc steps))) false false)))

(defn range-arrow!
  "Range. A lobbed arc falling to a point."
  [cs cx cy s]
  (let [r (* s 0.52)]
    (arc! cs cx (- cy (* s 0.18)) r 0.35 2.79)
    (arrowhead! cs (+ cx (* r (Math/cos 0.35))) (+ (- cy (* s 0.18)) (* r (Math/sin 0.35)))
                (* s 0.26) -1.15)))

(defn pouch!
  "Components. A tied bag with its neck, and the glints around it."
  [cs cx cy s]
  (let [r (* s 0.30)
        k (* r K)
        [bx by] (pt (+ cx r) (- cy (* s 0.06)))]
    ;; body
    (.moveTo cs bx by)
    (let [c (fn [a b] (let [[x1 y1] (pt (first a) (second a))
                            [x2 y2] (pt (first b) (second b))] [x1 y1 x2 y2]))
          cy' (- cy (* s 0.06))]
      (.curveTo cs (float (* U (+ cx r))) (float (* U (+ cy' k)))
                (float (* U (+ cx k))) (float (* U (+ cy' r)))
                (float (* U cx)) (float (* U (+ cy' r))))
      (.curveTo cs (float (* U (- cx k))) (float (* U (+ cy' r)))
                (float (* U (- cx r))) (float (* U (+ cy' k)))
                (float (* U (- cx r))) (float (* U cy')))
      (.curveTo cs (float (* U (- cx r))) (float (* U (- cy' k)))
                (float (* U (- cx k))) (float (* U (- cy' r)))
                (float (* U cx)) (float (* U (- cy' r))))
      (.curveTo cs (float (* U (+ cx k))) (float (* U (- cy' r)))
                (float (* U (+ cx r))) (float (* U (- cy' k)))
                (float (* U (+ cx r))) (float (* U cy'))))
    (.closeAndStroke cs)
    ;; neck
    (path! cs [(pt (- cx (* s 0.11)) (+ cy (* s 0.24)))
               (pt (+ cx (* s 0.11)) (+ cy (* s 0.24)))] false false)
    ;; glints
    (doseq [i (range 8)]
      (let [a (+ 0.39 (* i (/ (* 2 Math/PI) 8)))
            r1 (* s 0.40) r2 (* s 0.50)]
        (path! cs [(pt (+ cx (* r1 (Math/cos a))) (+ cy (* r1 (Math/sin a))))
                   (pt (+ cx (* r2 (Math/cos a))) (+ cy (* r2 (Math/sin a))))]
               false false)))))

(defn recharge!
  "Overflow and recharge. A ring open at one end, with the head on the gap."
  [cs cx cy s]
  (let [r (* s 0.42)]
    (arc! cs cx cy r 1.0 (+ 1.0 (* 1.62 Math/PI)))
    (arrowhead! cs (+ cx (* r (Math/cos 1.0))) (+ cy (* r (Math/sin 1.0)))
                (* s 0.26) -0.55)))

(def icons
  [["duration"      "sands-of-time"      hourglass!]
   ["casting time"  "magic-swirl"        spiral!]
   ["range"         "arrow-dunk"         range-arrow!]
   ["components"    "shiny-purse"        pouch!]
   ["recharge"      "clockwise-rotation" recharge!]])

(let [doc (PDDocument.) page (PDPage.)
      font (PDType1Font. Standard14Fonts$FontName/HELVETICA)
      bold (PDType1Font. Standard14Fonts$FontName/HELVETICA_BOLD)]
  (.addPage doc page)
  (let [img (pdf/make-image-loader doc)]
    (with-open [cs (PDPageContentStream. doc page)]
      (pdf/draw-text cs "Card icons: raster today, vector proposed" bold 15 0.7 10.2)
      (pdf/draw-text cs "left pair at the size a card uses them, right pair enlarged 4x"
                     font 9 0.7 10.0)
      (doseq [[i [label png draw]] (map-indexed vector icons)]
        (let [y (- 9.2 (* i 1.7))]
          (pdf/draw-text cs label bold 11 0.7 (+ y 0.55))
          (pdf/draw-text cs "raster" font 7 0.75 (- y 0.30))
          (pdf/draw-text cs "vector" font 7 1.55 (- y 0.30))
          ;; card size
          (pdf/draw-imagex cs (img (str "public/image/" png ".png")) 0.72 (- 11 y 0.20) 0.30 0.30)
          (.setLineWidth cs (float 0.9))
          (draw cs 1.68 y 0.30)
          ;; enlarged
          (pdf/draw-imagex cs (img (str "public/image/" png ".png")) 2.9 (- 11 y 0.6) 1.2 1.2)
          (.setLineWidth cs (float 2.4))
          (draw cs 5.0 y 1.2)
          (.setLineWidth cs (float 1))))))
  (.save doc (File. (or (System/getenv "OUT") "target/card-icons.pdf")))
  (.close doc))
(println "wrote" (or (System/getenv "OUT") "target/card-icons.pdf"))
