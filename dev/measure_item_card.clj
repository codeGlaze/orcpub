;; Builds the worst magic item card the layout can be asked for -- the longest
;; name, the longest attunement clause, a charge track, a legendary frame and a
;; description that runs onto the back -- renders it, and reports the gap between
;; every pair of stacked elements.
;;
;;   lein run -m clojure.main dev/measure_item_card.clj
;;   OUT=/tmp/torture.pdf lein run -m clojure.main dev/measure_item_card.clj
;;
;; Reading the gaps beats looking at the card: a sixteenth of an inch is invisible
;; on screen and obvious in the hand. Anything under MIN-GAP is reported as tight
;; so it can be fixed once rather than found later one card at a time.
;;
;; PDFBox's renderer cannot rasterise the embedded faces -- open the PDF in a
;; browser, or every card will look like a text encoding bug.

(require '[orcpub.pdf :as pdf] '[clojure.string :as str])
(import '[org.apache.pdfbox.pdmodel PDDocument PDPage PDPageContentStream] '[java.io File])

(def MIN-GAP
  "Least clear space between two stacked elements before it reads as crowded."
  0.06)

(def torture
  "Everything at once, so nothing can hide behind an easy case."
  {:name "Instrument of the Bards, Anstruth Harp of Deepest Sorrow"
   :orcpub.dnd.e5.magic-items/name "Instrument of the Bards"
   :orcpub.dnd.e5.magic-items/type :wondrous-item
   :orcpub.dnd.e5.magic-items/subtype :instrument
   :orcpub.dnd.e5.magic-items/rarity :legendary
   ;; The longest attunement list in the data.
   :orcpub.dnd.e5.magic-items/attunement [:bard :cleric :druid :sorcerer :warlock :wizard]
   :orcpub.dnd.e5.magic-items/description
   (str "This instrument has 12 charges and can be played while you are holding "
        "it. It has a spell save DC of 18. You can use an action to play it and "
        "expend 1 charge to cast one of the spells written on it. The instrument "
        "regains 1d4 + 4 expended charges daily at dawn. "
        (str/join " " (repeat 14 "The harp answers only to a hand that has known grief.")))})

(defn- text-span
  "Where a run of text actually puts ink, given the y its box is anchored at.

   Lines are set a leading below the anchor and downward from there, so the
   anchor itself is empty. Ink reaches about seven tenths of the size above a
   baseline and two tenths below it. Comparing anchors instead of ink is how a
   layout comes to look cramped while every number in it says it is fine."
  [anchor size lines]
  (let [leading (/ (* size 1.1) 72)
        first-base (+ anchor leading)
        last-base (+ anchor (* leading lines))]
    [(- first-base (* 0.72 (/ size 72)))
     (+ last-base (* 0.22 (/ size 72)))]))

(defn- rule-span [at] [at at])
(defn- mark-span [centre r] [(- centre r) (+ centre r)])

(defn elements
  "The card's stack as [label ink-top ink-bottom], for the worst case."
  [{:keys [down up]} h name-size name-lines body-lines]
  [["frame (top)"          (rule-span (:frame down))]
   ["rarity rail"          (mark-span (:rail down) 0.038)]
   ["name"                 (text-span (condp = name-lines
                                       1 (:name-one-line down)
                                       2 (:name-two-line down)
                                       (:name-three-line down))
                                     name-size name-lines)]
   ;; The badge sits BESIDE the subtitle, right-aligned on the same row, so it
   ;; is not part of the vertical stack. Its span is folded into the subtitle's.
   ["subtitle + badge"     (let [[t b] (text-span (:subtitle down) 7.5 1)]
                             [(min t (:badge down))
                              (max b (+ (:badge down) 0.145))])]
   ["header rule"          (rule-span (:rule down))]
   ["charge label"         (text-span (- (:charge-label down) 0.084) 5.5 1)]
   ["charge marks"         (mark-span (:charge-marks down) 0.052)]
   ["charge rule"          (rule-span (:charge-under down))]
   ["description"          (text-span (:body-charged down) 8 body-lines)]
   ["continued note"       (text-span (- (- h (:continued up)) 0.095) 6.2 1)]
   ["attunement clause"    (text-span (- h (:clause up)) 6.8 2)]
   ["foot ornament"        (mark-span (- h (:ornament up)) 0.032)]
   ["frame (bottom)"       (rule-span (- h (:frame down)))]])

(defn report
  "Prints the stack with the clear space between one element's ink and the next."
  [rows]
  (let [tight (atom 0)]
    (printf "%-22s %7s %7s %8s%n" "element" "top" "bottom" "gap")
    (doseq [[[l1 [t1 b1]] [_ [t2 _]]] (map vector rows (rest rows))]
      (let [gap (- t2 b1)]
        (when (< gap MIN-GAP) (swap! tight inc))
        (printf "%-22s %7.3f %7.3f %8.3f%s%n" l1 t1 b1 gap
                (cond (neg? gap) "   OVERLAP"
                      (< gap MIN-GAP) "   tight"
                      :else ""))))
    (let [[l [t b]] (last rows)]
      (printf "%-22s %7.3f %7.3f%n" l t b))
    (printf "%ngaps under %.2fin: %d%n" MIN-GAP @tight)))

(let [doc (PDDocument.) page (PDPage.)]
  (.addPage doc page)
  (let [fonts (pdf/load-fonts doc) img (pdf/make-image-loader doc)]
    (with-open [cs (PDPageContentStream. doc page)]
      (pdf/print-items cs doc fonts img 2.5 3.5 [torture torture torture] 0 false false nil)))
  (.save doc (File. (or (System/getenv "OUT") "target/item-card-torture.pdf")))
  (.close doc))

(println "charges parsed:" (pdf/item-charges (:orcpub.dnd.e5.magic-items/description torture)))
(println "subtitle      :" (pdf/magic-item-subtitle torture))
(println "clause        :" (pdf/attunement-phrase
                            (:orcpub.dnd.e5.magic-items/attunement torture) nil))
(println)
;; The name shrinks to fit two lines, so ask the renderer what size it settled on
;; rather than assuming the nominal one.
(let [doc (PDDocument.)
      fonts (pdf/load-fonts doc)
      width (- 2.5 0.56)
      lines-at (fn [pt] (count (#'pdf/split-lines (:name torture) (:bold fonts) pt width)))
      floor (get-in pdf/card-layout [:down :name-floor])
      height (get-in pdf/card-layout [:down :name-height])
      fits? (fn [pt] (let [n (lines-at pt)]
                       (and (<= n 3) (<= (* n (/ (* pt 1.1) 72)) height))))
      size (or (first (filter fits? (take-while #(>= % floor) (iterate #(- % 0.5) 13))))
               floor)
      body-lines (count (#'pdf/split-lines
                         (:orcpub.dnd.e5.magic-items/description torture)
                         (:plain fonts) 8 (- 2.5 0.4)))
      capacity (int (dec (/ (* 72 (- 3.5 1.74 0.80)) (* 8 1.1))))]
  (printf "name set at %.2fpt over %d lines; description %d lines, %d fit%n%n"
          (double size) (lines-at size) body-lines capacity)
  (report (elements pdf/card-layout 3.5 size (lines-at size) (min body-lines capacity)))
  (.close doc))
