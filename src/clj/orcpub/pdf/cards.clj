(ns orcpub.pdf.cards
  "Spell cards and magic-item cards: the frame, its ornaments, and nine to a sheet.

   Split out of orcpub.pdf, which keeps the AcroForm character sheet. The seam is
   one-way -- nothing here is called from there."
  (:require [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.magic-items :as mi]
            [orcpub.dnd.e5.spell-print :as sp]
            [orcpub.pdf.draw :as draw]))

;; UNITS: inches, y from the page TOP. draw/draw-text takes y from the BOTTOM, so every
;; text call converts through draw/in-from-top. Page extents come from draw/page-width and
;; draw/page-height. Nothing here names a page size, so the whole file follows
;; draw/*page*.

(defn- one-line-size
  "The size, from 8 pt down to a 6 pt floor in quarter points, at which `text` in `font` fits
   `width` inches on one line. Returns the floor when nothing larger fits."
  [text font width]
  (or (first (filter #(<= (draw/string-width text font %) width)
                     (range 8.0 5.99 -0.25)))
      6.0))

(def ^:private site-stamp-strip
  "Inches reserved at the foot of a card for the site line.

   Sized so the last line of overflow text clears the stamp by about an eighth of
   an inch. draw-lines-to-box takes `(dec (/ (* 72 height) leading))` lines, and
   `take` given a fraction rounds UP -- a box shortened by 0.22 computed 24.2 and
   laid down 25 lines, whose descenders sat on the stamp."
  0.35)

(defn- draw-site-stamp!
  "Centres the site line, in grey even in B&W mode, just above a card's bottom
   edge. `x` and `y` are the card's top-left in inches, y from the page TOP; this
   flips it to draw-text's baseline from the page BOTTOM."
  [cs fonts x y box-width box-height]
  (let [font (:plain fonts)
        width (draw/string-width draw/site-stamp font draw/site-stamp-size)]
    (draw/draw-text cs
               draw/site-stamp
               font
               draw/site-stamp-size
               (+ x (/ (- box-width width) 2))
               (draw/in-from-top (+ y (- box-height 0.16)))
               [0.45 0.45 0.45])))

(defn print-backs
  "Draws one page of spell card BACKS, mirroring the fronts for duplex printing.
   `remaining-lines-vec` is indexed by the card position `print-spells` used, each
   entry `{:remaining-lines :spell-name}` from that call; a card with no overflow
   gets only its name and the site stamp. Read with `get`, not as a function: a short
   vector threw, and the caller's try swallowed it along with every card. Inches."
  [cs fonts img box-width box-height remaining-lines-vec page-number logo-img]
  ;; `img` is the memoized per-document image loader; `logo?` opts each card back
  ;; into showing the card logo (matching the fronts) for double-sided printing.
  ;; When logo? is false NO images are loaded here — the old code decoded+embedded
  ;; the logo/rotation PNGs every page and never drew them (pure waste).
  (let [{:keys [cols rows margin-x margin-y]} (draw/grid-layout box-width box-height)]
    (draw/draw-grid cs box-width box-height)
    (draw/draw-text cs
               (str "Page " (inc page-number) " (reverse)")
               (:italic fonts)
               8
               0.12
               (draw/in-from-top 0.15))
    (doall
     (for [i (range cols)
           j (range rows)]
       (let [x (+ margin-x (* box-width i))
             y (+ margin-y (* box-height j))
             spell-index (+ i (* j cols))
             {:keys [remaining-lines spell-name]} (get remaining-lines-vec spell-index)]
         (if (seq remaining-lines)
           ;; This card back carries overflow text from the front.
           (do
             (draw/draw-text-to-box cs
                               spell-name
                               (:bold fonts)
                               10
                               (+ x 0.12)
                               (draw/in-from-top (+ y 0.08))
                               (- box-width 0.3)
                               0.25)
             ;; The card's own box, less the strip the stamp sits in:
             ;; draw-lines-to-box fills its height to the last line that fits, so
             ;; overflow text prints over the stamp unless the room is taken away.
             (draw/draw-lines-to-box cs
                               remaining-lines
                               (:plain fonts)
                               8
                               (+ x 0.12)
                               (draw/in-from-top (+ y 0.24))
                               (- box-height 0.2 site-stamp-strip))
             (draw/draw-text-to-box cs
                               "(reverse)"
                               (:italic fonts)
                               10
                               (+ x 0.15 (draw/string-width spell-name (:bold fonts) 10))
                               (draw/in-from-top (+ y 0.08))
                               (- box-width 0.3)
                               (- box-height 0.2)))
           ;; Blank back: a large, CENTERED logo (~80% of the card). logo-img is the
           ;; caller's resource path (a hi-res grayscale or solid-black DMV mark, not
           ;; the front's tiny card-logo.png), or nil when the logo is turned off.
           (when logo-img
             (draw/draw-imagex cs
                          (img logo-img)
                          (+ x (* box-width 0.1))
                          (+ y (* box-height 0.1))
                          (* box-width 0.8)
                          (* box-height 0.8))))
         ;; Every back, whichever branch drew it. The blank one leaves the bottom
         ;; tenth of the card clear below the mark, and the overflow one reserves
         ;; the same strip above.
         (draw-site-stamp! cs fonts x y box-width box-height))))))

;; ─── Spell card fronts ──────────────────────────────────────────────────────────
;; Laid out as measured boxes before anything is drawn, in points from the card's top-left:
;; name top-left, ATK and DC at the right, concentration and ritual ribbons in the corner, the
;; level and school with the spell's uses on one line, the source, the casting facts with their
;; labels on a rule, the material, then the text. Design and its rules for long content:
;; plan-spell-uses-by-source.md (agents/develop), "Settled spell card".

(def ^:private spell-card-w 180.0)
(def ^:private spell-card-h 252.0)
(def ^:private sc-left 11.0)
(def ^:private sc-right 169.0)
(def ^:private sc-footer 12.0)
(def ^:private sc-body-size 7.5)

(def ^:private school-abbreviations
  "The schools as a spell card prints them, always abbreviated."
  {"abjuration" "Abjur." "conjuration" "Conj." "divination" "Div." "enchantment" "Ench."
   "evocation" "Evoc." "illusion" "Illus." "necromancy" "Necro." "transmutation" "Trans."})

(defn concentration?
  "Whether `duration` (a spell's: a string such as \"Concentration, up to 1 minute\", or a
   units map) needs concentration."
  [duration]
  (boolean (or (and (map? duration) (:concentration duration))
               (and (string? duration) (re-find #"(?i)^concentration" duration)))))

(defn- spell-wrap
  "`text` broken into lines no wider than `width` points, measured by `width-of`."
  [width-of width text]
  (reduce (fn [lines word]
            (let [cur (peek lines) cand (if (s/blank? cur) word (str cur " " word))]
              (if (or (s/blank? cur) (<= (width-of cand) width))
                (conj (pop lines) cand)
                (conj lines word))))
          [""] (s/split (s/replace (str text) #"\s+" " ") #" ")))

(defn- spell-ellipsize
  "`text` cut with an ellipsis to fit `width` points, measured by `width-of`."
  [width-of text width]
  (if (<= (width-of text) width)
    text
    (loop [t text]
      (let [c (str (s/trimr t) "…")]
        (if (or (<= (width-of c) width) (< (count t) 2)) c (recur (subs t 0 (dec (count t)))))))))

(defn spell-card-layout
  "One spell card front as {:elements [...] :text-top pt}, each element with :box [x0 y0 x1 y1]
   in points from the card's top-left. `card` is {:spell :class-nm :dc :attack-bonus :uses {:n
   :label} :cast-at level :print-dc-mod? bool}; `fonts` a draw/load-fonts map. The name wraps
   beside ATK and DC; the uses leave the level line for a row of their own when they do not fit;
   the facts wrap to two rows; the material to two lines."
  [fonts {:keys [spell class-nm dc attack-bonus uses cast-at print-dc-mod?]}]
  (let [serif (:plain fonts) serif-b (:bold fonts) serif-i (:italic fonts)
        sans (draw/sans fonts :plain) sans-b (draw/sans fonts :bold)
        {spell-name :name spell-range :range :keys [level school casting-time components duration ritual]} spell
        spell-name (or spell-name "(Unknown Spell)")
        ;; Serif strings with figures are drawn as outlines in lining figures (Vollkorn's own
        ;; figures are old-style: 1 reads as I) and measured that way; the rest stay real text,
        ;; so a spell's name can still be searched and copied from the PDF.
        lining? (fn [font t] (and (contains? #{serif serif-b serif-i} font) (re-find #"\d" (str t))))
        width (fn [font t size] (if (lining? font t)
                                  (draw/lining-width font size (str t) 0)
                                  (* 72 (draw/string-width (str t) font size))))
        W (- sc-right sc-left)
        out (atom [])
        add! (fn [e] (swap! out conj e) e)
        text! (fn [font size t x baseline & [grey inside]]
                (add! (cond-> {:kind :text :font font :size size :text t :x x :y baseline :grey (or grey 0)
                               :lining (boolean (lining? font t))
                               :box [x (- baseline (* 0.72 size)) (+ x (width font t size)) (+ baseline (* 0.22 size))]}
                        inside (assoc :inside inside))))
        legend! (fn [t cx rule-y]
                  (let [w (width sans-b t 4.2)]
                    (add! {:kind :legend :text t :x (- cx (/ w 2)) :y (+ rule-y 1.5)
                           :box [(- cx (/ w 2) 1.5) (- rule-y 2.2) (+ cx (/ w 2) 1.5) (+ rule-y 2.2)]})))
        rule! (fn [y] (add! {:kind :rule :y y :box [sc-left y sc-right y]}))
        uses! (fn [label n x0 baseline]
                (text! sans-b 4.8 label (- x0 (width sans-b label 4.8) 3) baseline 0.4)
                (doseq [i (range n)]
                  (add! {:kind :use :cx (+ x0 (* i 9) 3.5) :cy (- baseline 1.7) :r 3.0
                         :box [(+ x0 (* i 9)) (- baseline 5.2) (+ x0 (* i 9) 7) (+ baseline 1.8)]})))
        conc? (concentration? duration)
        rw 11
        ribbons (cond-> [] conc? (conj [:conc "C" 0.0]) ritual (conj [:rit "R" 1.0]))
        rib-left (- sc-right (* (count ribbons) (+ rw 2)))
        ;; ATK and DC, right-aligned before the ribbons; their left edge bounds the name.
        stats-left (if-not print-dc-mod?
                     (- rib-left 4)
                     (loop [x (- rib-left (if (seq ribbons) 5 0))
                            [[l v] & more] (remove (comp nil? second)
                                                   [["DC" (some-> dc str)] ["ATK" (some-> attack-bonus common/bonus-str)]])
                            left x]
                       (if-not l
                         left
                         (let [vw (width serif-b v 9.0) lw (width sans-b l 4.5)
                               vx (- x vw) lx (- vx lw 2)]
                           (text! serif-b 9.0 v vx 17)
                           (text! sans-b 4.5 l lx 16.7 0.45)
                           (recur (- lx 7) more lx)))))
        room (- stats-left 6 sc-left)
        fits? (<= (width serif-b spell-name 10.5) room)
        lines (if fits? [spell-name] (spell-wrap #(width serif-b % 9.5) room spell-name))
        ;; A name that needed wrapping stays at 9.5 pt, even when it then fits on one line.
        title-size (if fits? 10.5 9.5)
        sy (+ 17 (* 11 (dec (count lines))) 10)
        y (atom 0)
        uses-on-level-line (atom false)]
    (doseq [[i [k t fill]] (map-indexed vector ribbons)
            :let [x (+ rib-left (* i (+ rw 2))) top 5.5 bot 27
                  pts [[x top] [(+ x rw) top] [(+ x rw) bot] [(+ x (/ rw 2)) (- bot 4)] [x bot]]]]
      (add! {:kind :ribbon :k k :pts pts :fill fill :box [x top (+ x rw) bot]})
      (text! serif-b 8.0 t (+ x (/ (- rw (width serif-b t 8.0)) 2)) 17 (if (zero? fill) 1.0 0) k))
    (doseq [[i l] (map-indexed vector lines)] (text! serif-b title-size l sc-left (+ 17 (* i 11))))
    (add! {:kind :sigil :school school :x sc-left :y (- sy 5.9) :box [sc-left (- sy 5.9) (+ sc-left 6.5) (+ sy 0.6)]})
    (let [lv (or level 0)
          level-t (if (zero? lv) "Cantrip" (str (common/ordinal lv) " Level"))
          sch (get school-abbreviations (common/ascii-lower-case (str school)) (common/ascii-capitalize (str school)))
          x0 (+ sc-left 9)
          x1 (+ x0 (width serif-b level-t 7.0))
          rest-t (when (seq sch) (str "  ·  " sch))]
      (text! serif-b 7.0 level-t x0 sy)
      (when rest-t (text! serif-i 7.0 rest-t x1 sy 0.15))
      (when uses
        (let [end (+ x1 (if rest-t (width serif-i rest-t 7.0) 0))
              ;; The ribbons bound the line only when they hang down beside it.
              edge (if (and (seq ribbons) (< (- sy 6) 27)) (- rib-left 4) sc-right)
              need (+ (width sans-b (:label uses) 4.8) 4 (* (:n uses) 9))]
          (when (<= need (- edge end 8))
            (reset! uses-on-level-line true)
            (uses! (:label uses) (:n uses) (- edge (* (:n uses) 9)) sy)))))
    (reset! y (+ (max sy 27) 5))
    (rule! @y)
    (swap! y + 9)
    (let [src (common/ascii-upper-case (str class-nm (when cast-at (str " · cast at " (common/ordinal cast-at) " level"))))]
      (text! sans 4.8 (spell-ellipsize #(width sans % 4.8) src W) sc-left @y 0.4)
      (when (and uses (not @uses-on-level-line))
        (swap! y + 9)
        (uses! (:label uses) (:n uses) (- sc-right (* (:n uses) 9)) @y))
      (swap! y + 11))
    (let [comps (s/join ", " (keep (fn [[c l]] (when (get components c) l)) [[:verbal "V"] [:somatic "S"] [:material "M"]]))
          facts (remove (comp s/blank? second)
                        [["CAST" (some-> casting-time str (s/split #",") first sp/abbreviate-casting-time)]
                         ["RANGE" (some-> spell-range str sp/abbreviate-range)]
                         ["COMP" comps]
                         ["DUR" (some-> duration sp/abbreviate-duration str)]])
          cell-w (fn [[l v]] (max (width serif v 7.0) (+ (width sans-b l 4.2) 4)))
          row! (fn [fs]
                 (let [ws (map cell-w fs) gap (/ (- W (reduce + ws)) (max 1 (dec (count fs))))
                       xs (reductions + sc-left (map #(+ % gap) ws)) ry (+ @y 4)]
                   (doseq [[[l v] x w] (map vector fs xs ws) :let [cx (+ x (/ w 2))]]
                     (text! serif 7.0 v (- cx (/ (width serif v 7.0) 2)) @y)
                     (legend! l cx ry))
                   (rule! ry)
                   (reset! y (+ ry 10))))]
      (when (seq facts)
        (if (<= (reduce + (map cell-w facts)) (- W 12))
          (row! facts)
          (do (row! (take 2 facts)) (row! (drop 2 facts))))))
    (when-let [mat (get-in spell [:components :material-component])]
      (let [mw #(width serif-i % 6.5)
            ls (spell-wrap mw W (common/ascii-capitalize (s/replace (str mat) #"\s+" " ")))
            ls (if (> (count ls) 2)
                 [(first ls) (spell-ellipsize mw (str (second ls) " … (full text on the back)") W)]
                 ls)]
        (doseq [l ls] (text! serif-i 6.5 l sc-left @y 0.2) (swap! y + 7.5))
        (swap! y + 1.5)))
    {:elements @out :text-top @y}))

(defn spell-card-problems
  "What is wrong with a spell-card-layout: content boxes overlapping (a mark inside its own
   ribbon excepted) and content within 3 pt of the frame (the ribbons hang from it by design).
   A vector of strings, empty when the card is clean."
  [{:keys [elements]}]
  (let [content (remove #(= :rule (:kind %)) elements)
        overlap? (fn [[a0 b0 a1 b1] [c0 d0 c1 d1]] (and (< a0 c1) (< c0 a1) (< b0 d1) (< d0 b1)))
        own? (fn [a b] (and (:inside a) (= (:inside a) (:k b))))
        label (fn [e] (str (name (:kind e)) " " (or (:text e) (some-> (:k e) name) (:school e))))]
    (vec (concat
          (for [e content :when (not= :ribbon (:kind e))
                :let [[x0 y0 x1 _] (:box e)] :when (or (< x0 8) (< y0 8) (> x1 (- spell-card-w 8)))]
            (str (label e) " is within 3 pt of the frame"))
          (for [[i a] (map-indexed vector content) b (drop (inc i) content)
                :when (and (overlap? (:box a) (:box b)) (not (own? a b)) (not (own? b a)))]
            (str (label a) " overlaps " (label b)))))))

(defn- black!
  "Sets fill and stroke back to black."
  [cs]
  (.setNonStrokingColor cs (float 0) (float 0) (float 0))
  (.setStrokingColor cs (float 0) (float 0) (float 0)))

(defn- spell-card-frame!
  "Strokes a spell card's frame, rounded corners inset 5 pt, the card's top-left at page
   points (X, T), T down from the page top."
  [cs X T]
  (let [x (+ X 5) w (- spell-card-w 10) h (- spell-card-h 10) r 8.0 k (* 0.5523 r)
        y (draw/py (+ T 5 h)) x2 (+ x w) y2 (+ y h)]
    (.setLineWidth cs (float 0.9))
    (.moveTo cs (float (+ x r)) (float y))
    (.lineTo cs (float (- x2 r)) (float y))
    (.curveTo cs (float (+ (- x2 r) k)) (float y) (float x2) (float (- (+ y r) k)) (float x2) (float (+ y r)))
    (.lineTo cs (float x2) (float (- y2 r)))
    (.curveTo cs (float x2) (float (+ (- y2 r) k)) (float (+ (- x2 r) k)) (float y2) (float (- x2 r)) (float y2))
    (.lineTo cs (float (+ x r)) (float y2))
    (.curveTo cs (float (- (+ x r) k)) (float y2) (float x) (float (+ (- y2 r) k)) (float x) (float (- y2 r)))
    (.lineTo cs (float x) (float (+ y r)))
    (.curveTo cs (float x) (float (- (+ y r) k)) (float (- (+ x r) k)) (float y) (float (+ x r)) (float y))
    (.closePath cs)
    (.stroke cs)))

(defn draw-spell-card!
  "Draws `card` (spell-card-layout) as the card whose top-left is `x`, `y` inches (y from the
   page top), then `description` in the space left. Returns the lines that did not fit."
  [cs fonts x y {:keys [elements text-top]} description]
  (let [X (* 72 x) T (* 72 y)
        py #(draw/py (+ T %))
        sans-b (draw/sans fonts :bold)
        serif (:plain fonts) serif-i (:italic fonts)]
    (black! cs)
    (spell-card-frame! cs X T)
    (doseq [{ry :y} (filter #(= :rule (:kind %)) elements)
            :let [on (filter #(and (= :legend (:kind %)) (< (Math/abs (double (- (:y %) 1.5 ry))) 0.1)) elements)
                  first-l (first (filter #(< (first (:box %)) (+ sc-left 8)) on))
                  last-l (first (filter #(> (nth (:box %) 2) (- sc-right 8)) on))
                  x0 (if first-l (nth (:box first-l) 2) sc-left)
                  x1 (if last-l (first (:box last-l)) sc-right)]]
      ;; A legend at a rule's end ends the rule, so no stub of line shows past it.
      (.setLineWidth cs (float 0.5))
      (.moveTo cs (float (+ X x0)) (float (py ry))) (.lineTo cs (float (+ X x1)) (float (py ry))) (.stroke cs))
    (doseq [e elements]
      (case (:kind e)
        :ribbon (let [[[fx fy] & more] (:pts e)
                      path! #(do (.moveTo cs (float (+ X fx)) (float (py fy)))
                                 (doseq [[px pyy] more] (.lineTo cs (float (+ X px)) (float (py pyy))))
                                 (.closePath cs))]
                  (.setNonStrokingColor cs (float (:fill e)) (float (:fill e)) (float (:fill e)))
                  (path!) (.fill cs)
                  (black! cs)
                  (.setLineWidth cs (float 0.7))
                  (path!) (.stroke cs))
        :use (do (.setLineWidth cs (float 0.8))
                 (draw/circle-path! cs (fn [px pty] [(float px) (draw/py pty)]) (+ X (:cx e)) (+ T (:cy e)) (:r e))
                 (.closePath cs) (.stroke cs))
        :sigil (draw/sigil! cs (:school e) (+ X (:x e)) (+ T (:y e)) 6.5)
        :text (if (:lining e)
                (draw/text! cs (:font e) (:size e) (+ X (:x e)) (+ T (:y e)) (:text e) {:lining true :color (:grey e)})
                (draw/text! cs (:font e) (:size e) (+ X (:x e)) (+ T (:y e)) (:text e) {:color (:grey e)}))
        :legend (let [[x0 _ x1 _] (:box e)]
                  (.setNonStrokingColor cs (float 1) (float 1) (float 1))
                  (.addRect cs (float (+ X x0)) (float (py (+ (:y e) 1.2))) (float (- x1 x0)) (float 5.5))
                  (.fill cs)
                  (draw/text! cs sans-b 4.2 (+ X (:x e)) (+ T (:y e)) (:text e) {:color 0.45}))
        nil)
      ;; draw/text! and sigil! leave fill and stroke at their own colour.
      (black! cs))
    (let [over (draw/draw-text-to-box cs (str description) serif sc-body-size (/ (+ X sc-left) 72.0) (/ (py text-top) 72.0)
                                      (/ (- sc-right sc-left) 72.0) (/ (- spell-card-h sc-footer 5 text-top) 72.0))]
      (when (seq over)
        (let [t "continued on the back" w (* 72 (draw/string-width t serif-i 5.5))]
          (draw/text! cs serif-i 5.5 (+ X (/ (- spell-card-w w) 2)) (+ T (- spell-card-h 8)) t {:color 0.45})
          (black! cs)))
      over)))

(defn print-spells
  "Draws one page of spell card FRONTS and returns what did not fit, for
   `print-backs`. `spells` is a seq of `{:class-nm :dc :attack-bonus :spell}`; cards
   fill right to left within a row so a duplex flip lines the backs up.
   `print-spell-card-dc-mod?` prints save DC and attack bonus. The front is black on white, so
   `bw?`/`bw-faded?` change nothing here (they are the backs' and item cards'). Inches."
  [cs document fonts img box-width box-height spells page-number print-spell-card-dc-mod? bw? bw-faded?]
  (let [{:keys [cols rows margin-x margin-y]} (draw/grid-layout box-width box-height)]
    (draw/draw-grid cs box-width box-height)
        (draw/draw-text cs
                   (str "Page " (inc page-number))
                   (:italic fonts)
                   8
                   0.12
                   (draw/in-from-top 0.15))
        (doall
         (for [j (range rows)
               i (range (dec cols) -1 -1)
               :let [spell-index (+ i (* j cols))]]
           (when-let [{:keys [class-nm dc attack-bonus spell] :as spell-data}
                      (get (vec spells) spell-index)]
             (let [x (+ margin-x (* box-width i))
                   y (+ margin-y (* box-height j))
                   {:keys [page source description summary]} spell
                   spell-name (or (:name spell) "(Unknown Spell)")
                   text (or description
                            (if summary
                              (str summary " (see " (if source (common/ascii-upper-case (name source)) "PHB")
                                   " " page " for more details)")
                              ""))
                   card (spell-card-layout fonts (assoc spell-data :print-dc-mod?
                                                        (and print-spell-card-dc-mod? (not= class-nm "Homebrew"))))]
               {:remaining-lines (draw-spell-card! cs fonts x y card text)
                :spell-name spell-name}))))))

(defn- card-pt
  "A point `dx` right and `dy` DOWN from the top-left of the card at (x, y), in
   PDF page units. Card coordinates run downward from the page top, page units run
   up from the page bottom."
  [x y dx dy]
  [(float (* 72 (+ x dx))) (draw/in->pt (draw/in-from-top (+ y dy)))])

(defn- card-mapper
  "`draw/path!`'s `pt` for the card at (x, y): card-relative inches to page units."
  [x y]
  (fn [dx dy] (card-pt x y dx dy)))

(defn- polyline!
  "Strokes, and optionally closes, a path through card-relative points."
  [cs x y points close?]
  (draw/path! cs (card-mapper x y) points)
  (if close? (.closeAndStroke cs) (.stroke cs)))

(defn- diamond!
  "A small diamond of radius `r` centred at (dx, dy) on the card. Filled ones mark
   the item's rarity rank, outlined ones the ranks above it."
  [cs x y dx dy r filled?]
  (draw/path! cs (card-mapper x y)
                [[dx (- dy r)] [(+ dx r) dy] [dx (+ dy r)] [(- dx r) dy]])
  (.closePath cs)
  (if filled? (.fill cs) (.stroke cs)))

(defn- circle!
  "An outlined circle of radius `r` centred at (dx, dy) on the card."
  [cs x y dx dy r]
  (draw/circle-path! cs (card-mapper x y) dx dy r)
  (.closeAndStroke cs))

(def ^:private rarity-rank
  "How the rarities order, for the diamonds along the top edge. :varies has no
   rank and draws none -- an item whose rarity depends on the table cannot be
   ranked against one that does not."
  {:common 1 :uncommon 2 :rare 3 :very-rare 4 :legendary 5})

(defn item-charges
  "How many charges the item's description says it has, or nil. The data has no
   charge count, so it is read off the prose; a die expression takes its maximum
   (`1d8 + 1 charges` gives nine). Only \"<number or dice> charges\" matches, so the
   Manuals and Tomes \"charged with magic\" get no tracker; over 99 is noise, nil."
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
  "The italic line under a magic item's name: what kind of thing it is and how rare,
   in the order the books use. GOTCHA: the attunement clause is NOT here; the card
   prints it at the foot, and a long clause clipped a one-line subtitle."
  [{:keys [::mi/type ::mi/subtype ::mi/rarity]}]
  (let [kind (when type
               ;; Capitalised, and only the first word: the books write "Wondrous
               ;; item, rare", not "Wondrous Item".
               (common/ascii-capitalize
                (str (common/kw-to-name type)
                     (when subtype (str " (" (common/kw-to-name subtype) ")")))))
        rare (when rarity
               (if (= :varies rarity)
                 "rarity varies"
                 (common/ascii-lower-case (common/kw-to-name rarity))))
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
  "The rank marks on their own rule across the top of the card: five diamonds, filled
   to the item's rank, centred, with a hairline out to each side. Its own row so it
   does not compete with the name; the filled count sorts a fanned deck. :varies has
   no rank, so its rail is drawn unfilled."
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
  (draw/draw-text cs text font size (- cx (/ (draw/string-width text font size) 2)) baseline))

(def card-layout
  "Where everything sits on a 2.5 x 3.5in item card.

   `:down` values are inches from the card's TOP edge, `:up` values from its
   BOTTOM. Declared in one place rather than spelled out at each draw site, so the
   vertical rhythm can be measured -- scripts/research/measure_item_card.clj on agents/develop reports the gap
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
   artwork for it, and a letter cannot be misread as decoration. In `fonts`' Nimbus bold."
  [cs fonts x y w dy]
  (let [bw 0.155 bh 0.145
        left (- w 0.28 bw)
        c 0.035]
    (.setLineWidth cs (float 0.7))
    (polyline! cs x y [[(+ left c) dy] [(- (+ left bw) c) dy]
                       [(+ left bw) (+ dy c)] [(+ left bw) (- (+ dy bh) c)]
                       [(- (+ left bw) c) (+ dy bh)] [(+ left c) (+ dy bh)]
                       [left (- (+ dy bh) c)] [left (+ dy c)]]
               true)
    (draw-centred-text! cs "A" (draw/sans fonts :bold) 7
                        (+ x left (/ bw 2))
                        (draw/in-from-top (+ y dy bh -0.036)))
    (.setLineWidth cs (float 1))))

(defn- draw-centred-lines!
  "Wraps `text` to `width` and centres each line on `cx`, returning what did not
   fit. draw-text-to-box only sets flush left."
  [cs text font size cx top width max-lines]
  (let [lines (draw/split-lines text font size width)
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
  "Somewhere to track `n` charges, drawn at `cy` in its own band under the header.
   Up to `tickable-charges` it is a circle each; past it (a Staff of the Magi has
   fifty) a rule to write the remaining count on, over the total. Labels in `fonts`'
   Nimbus regular."
  [cs fonts x y w n cy label-y]
  (.setLineWidth cs (float 0.8))
  (if (<= n tickable-charges)
    (let [r 0.052
          gap 0.145
          cx (- (/ w 2) (/ (* gap (dec n)) 2))]
      (draw-centred-text! cs "CHARGES" (draw/sans fonts :plain) 5.5 (+ x (/ w 2)) (draw/in-from-top (+ y label-y)))
      (doseq [i (range n)]
        (circle! cs x y (+ cx (* gap i)) cy r)))
    ;; The rule and the total are centred as one group, not the rule alone: the
    ;; total hangs off its right end and pulls the pair off centre otherwise.
    (let [total (str "/ " n)
          rule-w 0.62
          gap 0.05
          group (+ rule-w gap (draw/string-width total (draw/sans fonts :plain) 7))
          left (- (/ w 2) (/ group 2))]
      (draw-centred-text! cs "CHARGES" (draw/sans fonts :plain) 5.5 (+ x (/ w 2)) (draw/in-from-top (+ y label-y)))
      (polyline! cs x y [[left cy] [(+ left rule-w) cy]] false)
      (draw/draw-text cs total (draw/sans fonts :plain) 7 (+ x left rule-w gap) (draw/in-from-top (+ y cy -0.015)))))
  (.setLineWidth cs (float 1)))

(defn print-items
  "Draws one page of magic item cards, and returns what did not fit for the backs.
   Same grid, box and overflow handling as print-spells; everything is vector, so
   cards stay sharp and cost the file almost nothing. The room a blank template
   gives labelled slots goes to the description; attunement sits at the foot, only
   when needed. `opts`: `:flourish` (one of card-flourishes), `:name-face` (a key
   into `fonts`, or a font), `:name-size` in points, `:name-tracking` letter spacing."
  ([cs document fonts img box-width box-height items page-number bw? bw-faded?]
   (print-items cs document fonts img box-width box-height items page-number
                bw? bw-faded? nil))
  ([cs document fonts img box-width box-height items page-number bw? bw-faded? opts]
  (let [{:keys [flourish name-size name-tracking]
         :or {flourish :diamonds name-size 13 name-tracking 0.15}} opts
        name-face (fn [fs] (let [f (:name-face opts :bold)]
                             (if (keyword? f) (get fs f) f)))
        {:keys [cols rows margin-x margin-y]} (draw/grid-layout box-width box-height)]
    (draw/draw-grid cs box-width box-height)
    (draw/draw-text cs (str "Page " (inc page-number)) (:italic fonts) 8 0.12 (draw/in-from-top 0.15))
    (doall
     (for [j (range rows)
           i (range (dec cols) -1 -1)
           :let [item-index (+ i (* j cols))]]
       (when-let [item (get (vec items) item-index)]
         (let [x (+ margin-x (* box-width i))
               y (+ margin-y (* box-height j))
               item-name (or (:name item) (::mi/name item) "(Unknown Item)")
               {:keys [::mi/description ::mi/summary ::mi/page ::mi/source
                       ::mi/rarity ::mi/attunement ::mi/attunement-details]} item
               body (or description
                        (when summary
                          (str summary " (see "
                               (if source (common/ascii-upper-case (name source)) "DMG")
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
               ;; the note saying so has reserved room. Measured against the box
               ;; WITHOUT the note, which only shrinks it. The LINES are kept, not
               ;; just their count, because splitting is the expensive part.
               body-lines (draw/split-lines body (:plain fonts) 8 (- box-width 0.4))
               capacity (fn [h] (int (dec (/ (* 72 h) (* 8 1.1)))))
               spills? (> (count body-lines)
                          (capacity (- box-height body-top (body-stops [false (some? clause)]))))
               body-bottom (body-stops [spills? (some? clause)])]
           (draw-card-frame! cs x y box-width box-height rarity flourish)
           (draw-rarity-rail! cs x y box-width rarity (:rail down))
           (draw-foot-ornament! cs x y box-width (- box-height (:ornament up)))
           (when clause
             (draw-attunement-badge! cs fonts x y box-width (:badge down)))
           ;; The name is indented further and set larger, so it reads as a title.
           ;; It shrinks to fit two lines, but not below :name-floor, where it takes
           ;; a third instead. The block's height is fixed so header rules fall level
           ;; across a sheet; a one-line name sits in the middle of it.
           (let [face (name-face fonts)
                 width (- box-width 0.56)
                 lines-at (fn [pt] (count (draw/split-lines item-name face pt width)))
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
             (draw/draw-text-to-box cs item-name face size
                               (+ x 0.28) (draw/in-from-top (+ y top)) width (:name-height down))
             (.setCharacterSpacing cs (float 0)))
           ;; The subtitle stops short of the badge so the two never meet.
           (draw/draw-text-to-box cs (magic-item-subtitle item) (:italic fonts) 7.5
                             (+ x 0.2) (draw/in-from-top (+ y (:subtitle down)))
                             (- box-width (if clause 0.68 0.4)) 0.2)
           ;; A rule and a hairline under it: the header carries two kinds of
           ;; information, so it closes with more than the body's plain divisions.
           (.setLineWidth cs (float 0.9))
           (polyline! cs x y [[0.2 (:rule down)] [(- box-width 0.2) (:rule down)]] false)
           (.setLineWidth cs (float 0.35))
           (polyline! cs x y [[0.2 (:rule-under down)] [(- box-width 0.2) (:rule-under down)]] false)
           (.setLineWidth cs (float 1))
           (when charges
             (draw-charge-track! cs fonts x y box-width charges (:charge-marks down) (:charge-label down))
             (.setLineWidth cs (float 0.35))
             (polyline! cs x y [[0.2 (:charge-under down)] [(- box-width 0.2) (:charge-under down)]] false)
             (.setLineWidth cs (float 1)))
           (let [remaining-desc-lines
                 (draw/draw-lines-to-box cs body-lines (:plain fonts) 8
                                    (+ x 0.2) (draw/in-from-top (+ y body-top))
                                    (- box-height body-top body-bottom))]
             ;; Centred, like the ornament under it and the note above it. Flush
             ;; left it was the only thing at the foot on its own axis.
             (when clause
               (draw-centred-lines! cs clause (:italic fonts) 6.8
                                    (+ x (/ box-width 2))
                                    (draw/in-from-top (+ y (- box-height (:clause up))))
                                    (- box-width 0.4) 2))
             ;; A phrase rather than the recharge icon. At the bottom right the
             ;; icon sat on the corner diamond and its arms, every other spot down
             ;; there belongs to the clause or the ornament, and an arrow does not
             ;; say what it means anyway.
             (when (seq remaining-desc-lines)
               (draw-centred-text! cs "continued on the back" (:italic fonts) 6.2
                                   (+ x (/ box-width 2))
                                   (draw/in-from-top (+ y (- box-height
                                                           (if clause (:continued up) (:continued-bare up)))))))
             {:remaining-lines remaining-desc-lines
              :spell-name item-name}))))))))
