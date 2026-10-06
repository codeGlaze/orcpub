(ns orcpub.loading-spinner
  "The spinner the server page shows until the app mounts: a spiral, a tumbling d20 or a spell
   circle, picked at random per load, or by `?spinner=` for testing. Inline markup and CSS, so it
   shows before any stylesheet or script loads.
   GOTCHA: animate only transform and opacity, each moving piece its own HTML element. Anything
   else repaints on the main thread and freezes while the app is building, when it is on screen."
  (:require [clojure.string :as str]))

(def kinds [:spiral :d20 :spell])

(defn- n
  "`x` to one decimal. Locale/ROOT: a default locale can write 100,5 and break the SVG."
  [x]
  (String/format java.util.Locale/ROOT "%.1f" (object-array [(double x)])))

(def ^:private spiral-path
  (let [steps 218]
    (str "M"
         (str/join " L"
                   (for [i (range (inc steps))
                         :let [th (Math/toRadians (* 7.0 i))
                               r (+ 5 (* 79.0 (/ i steps)))]]
                     (str (n (+ 100 (* r (Math/cos th)))) " " (n (+ 100 (* r (Math/sin th))))))))))

(defn- spiral []
  (str "<svg class=\"ls-spiral\" viewBox=\"0 0 200 200\" aria-hidden=\"true\"><path d=\"" spiral-path
       "\" fill=\"none\" stroke=\"#f0a100\" stroke-width=\"9\" stroke-linecap=\"round\""
       " stroke-linejoin=\"round\"/></svg>"))

;; d20: the 12 vertices of an icosahedron, its 20 faces found as vertex triples one edge apart, and
;; for each face the matrix3d that lays a flat 60px triangle onto it, front facing outward.
(defn- v- [a b] (mapv - a b))
(defn- v+ [a b] (mapv + a b))
(defn- v* [a k] (mapv #(* % k) a))
(defn- dot [a b] (reduce + (map * a b)))
(defn- cross [[a1 a2 a3] [b1 b2 b3]] [(- (* a2 b3) (* a3 b2)) (- (* a3 b1) (* a1 b3)) (- (* a1 b2) (* a2 b1))])
(defn- unit [a] (v* a (/ 1 (Math/sqrt (dot a a)))))
(defn- dist [a b] (Math/sqrt (dot (v- a b) (v- a b))))

(def ^:private d20-faces
  (let [p (/ (+ 1 (Math/sqrt 5)) 2)
        verts (vec (for [a [-1 1] b [-1 1] v [[0 a (* b p)] [a (* b p) 0] [(* b p) 0 a]]] v))
        faces (filter (fn [[i j k]] (every? #(< (Math/abs (- (apply dist (map verts %)) 2.0)) 1e-6)
                                            [[i j] [j k] [i k]]))
                      (for [i (range 12) j (range (inc i) 12) k (range (inc j) 12)] [i j k]))
        side 60.0
        h (* side (/ (Math/sqrt 3) 2))
        light (unit [-0.5 -0.8 0.6])]
    (for [f faces
          :let [[a b c] (map #(v* (verts %) (/ side 2)) f)
                centre (v* (v+ (v+ a b) c) (/ 1 3.0))
                y (unit (v- c (v* (v+ a b) 0.5)))
                [a b] (if (neg? (dot (cross (unit (v- b a)) (v* y -1)) centre)) [b a] [a b])
                x (unit (v- b a))
                z (cross x (v* y -1))
                t (v+ a (v* y h))
                lit (+ 34 (int (* 30 (max 0 (dot (unit centre) light)))))]]
      {:matrix (str/join "," (map #(String/format java.util.Locale/ROOT "%.4f" (object-array [(double %)]))
                                  (concat x [0] (v* y -1) [0] z [0] t [1])))
       :lit lit})))

(defn- d20 []
  (str "<div class=\"ls-d20-stage\" aria-hidden=\"true\"><div class=\"ls-d20\">"
       (apply str (for [{:keys [matrix lit]} d20-faces]
                    (str "<i style=\"transform:matrix3d(" matrix ");--l:" lit "%\"></i>")))
       "</div></div>"))

;; Spell circle: twelve made-up runes, each three strokes between points of a 3x3 grid, around a ring.
(def ^:private rune-points [[-4 -6] [4 -6] [-4 0] [4 0] [-4 6] [4 6] [0 -6] [0 6] [0 0]])

(def ^:private runes
  (apply str
         (for [i (range 12)
               s (range 3)
               :let [th (/ (* 2 Math/PI i) 12)
                     cx (+ 100 (* 76 (Math/sin th)))
                     cy (- 100 (* 76 (Math/cos th)))
                     rot (fn [[x y]] [(+ cx (- (* x (Math/cos th)) (* y (Math/sin th))))
                                      (+ cy (* x (Math/sin th)) (* y (Math/cos th)))])
                     [x1 y1] (rot (rune-points (mod (+ (* 5 i) (* 2 s)) 9)))
                     [x2 y2] (rot (rune-points (mod (+ (* 5 i) (* 2 s) 1 (* 3 s)) 9)))]]
           (str "M" (n x1) " " (n y1) "L" (n x2) " " (n y2)))))

(def ^:private hexagram
  (let [pt #(let [a (/ (* 2 Math/PI %) 6)] (str (n (+ 100 (* 56 (Math/sin a)))) " " (n (- 100 (* 56 (Math/cos a))))))]
    (str "M" (str/join "L" (map pt [0 2 4])) "Z"
         "M" (str/join "L" (map pt [1 3 5])) "Z")))

(defn- spell []
  (str "<div class=\"ls-spell\" aria-hidden=\"true\">"
       "<svg class=\"ls-spell-outer\" viewBox=\"0 0 200 200\">"
       "<circle cx=\"100\" cy=\"100\" r=\"92\" fill=\"none\" stroke=\"#f0a100\" stroke-width=\"2.5\"/>"
       "<circle cx=\"100\" cy=\"100\" r=\"62\" fill=\"none\" stroke=\"#f0a100\" stroke-width=\"2\"/>"
       "<path d=\"" runes "\" stroke=\"#f0a100\" stroke-width=\"2.2\" stroke-linecap=\"round\" fill=\"none\"/></svg>"
       "<svg class=\"ls-spell-inner\" viewBox=\"0 0 200 200\">"
       "<path d=\"" hexagram "\" fill=\"none\" stroke=\"#f5b942\" stroke-width=\"2.4\" stroke-linejoin=\"round\"/>"
       "<circle cx=\"100\" cy=\"100\" r=\"28\" fill=\"none\" stroke=\"#f5b942\" stroke-width=\"1.6\" stroke-dasharray=\"4 5\"/></svg>"
       "<div class=\"ls-spell-core\"></div></div>"))

(def css
  "Styles for all three, for the page head."
  (str ".ls{display:flex;justify-content:center;padding-top:180px}"
       ".ls-box{width:200px;height:200px;display:grid;place-items:center}"
       "@keyframes ls-turn{to{transform:rotate(360deg)}}"
       ".ls-spiral{width:200px;height:200px;animation:ls-turn 1.6s linear infinite}"
       ".ls-d20-stage{width:160px;height:160px;perspective:520px;display:grid;place-items:center;transform:scale(1.3)}"
       ".ls-d20{position:relative;width:0;height:0;transform-style:preserve-3d;"
       "animation:ls-roll 3.2s cubic-bezier(.45,.05,.35,1) infinite}"
       ".ls-d20 i{position:absolute;left:0;top:0;width:60px;height:51.96px;transform-origin:0 0;"
       "backface-visibility:hidden;clip-path:polygon(0 100%,100% 100%,50% 0);background:hsl(30 70% calc(var(--l) - 12%))}"
       ".ls-d20 i::before{content:'';position:absolute;inset:0;clip-path:polygon(7% 95%,93% 95%,50% 8%);"
       "background:hsl(38 96% var(--l))}"
       "@keyframes ls-roll{to{transform:rotateX(360deg) rotateY(720deg) rotateZ(360deg)}}"
       ".ls-spell{position:relative;width:180px;height:180px}"
       ".ls-spell svg{position:absolute;inset:0;width:100%;height:100%}"
       ".ls-spell-outer{animation:ls-turn 9s linear infinite}"
       ".ls-spell-inner{animation:ls-turn 6s linear infinite reverse}"
       ".ls-spell-core{position:absolute;inset:38%;border-radius:50%;"
       "background:radial-gradient(#f5b942,transparent 70%);animation:ls-pulse 1.6s ease-in-out infinite}"
       "@keyframes ls-pulse{0%,100%{transform:scale(.55);opacity:.45}50%{transform:scale(1);opacity:1}}"
       "@media (prefers-reduced-motion:reduce){.ls-spiral,.ls-d20,.ls-spell-outer,.ls-spell-inner,"
       ".ls-spell-core{animation-duration:8s}}"))

(defn pick
  "The spinner kind for a request: `requested` (a ?spinner= value) when it names one, else random."
  [requested]
  (or (some #{(keyword requested)} kinds) (rand-nth kinds)))

(defn markup
  "The placeholder markup for spinner `kind`, tagged data-spinner with its name."
  [kind]
  (str "<div class=\"ls h-full\" data-spinner=\"" (name kind) "\"><div class=\"ls-box\" role=\"img\" aria-label=\"Loading\">"
       (case kind :spiral (spiral) :d20 (d20) :spell (spell))
       "</div></div>"))
