(ns orcpub.dnd.e5.spell-packing
  "Which spell level goes in which box of a printed spell page (ten fixed boxes in three
   columns, each with its own row count), so classes and levels share pages instead of
   taking one box per level per class. Runs in the BUILDER -- the server sees only a flat
   field map -- and the result travels to the export as field values plus relabel
   instructions the server applies with pdf/relabel-numeral!."
  (:require [clojure.string :as s]))

(def sheet-geometry
  "Rows each level box holds, by sheet style, level 0 through 9.
   The count of FIELDS in the box, not printed rows: a row with no field behind it cannot be
   filled, so whatever lands there is reported unplaceable. A test keeps the two equal.
   Counted off the template masters in resources/ (dev/fix_spell_row_fields.clj)."
  {1 [8 12 13 13 13 9 9 9 7 7]
   2 [8 12 13 13 13 9 9 9 7 7]
   3 [8 12 13 13 13 9 9 9 7 7]
   4 [7 13 13 13 13 9 9 9 7 7]})

(def columns
  "The boxes in each column, top to bottom. The same on every style; only the row
   counts differ, which is why the fitting arithmetic is per style and the shape
   is not."
  [[0 1 2] [3 4 5] [6 7 8 9]])

(defn column-capacity
  "Boxes and rows a column offers on `style`."
  [style column]
  (let [rows (get sheet-geometry style (get sheet-geometry 1))]
    {:boxes (count column)
     :rows (reduce + (map #(nth rows %) column))}))

(defn- empty-page
  "A page's three columns, each with everything still free."
  [style]
  (mapv (fn [column] {:column column :placed []}) columns))

(defn- style-rows [style]
  (get sheet-geometry style (get sheet-geometry 1)))

(defn- box-may-hold?
  "Whether `level` may be printed in `box`. Box 0 is the cantrips box and takes cantrips
   only: its bar has no slot inputs or numeral field, and drawing them onto the artwork is
   measured for style 1 alone. A no-cantrips class that would start at box 0 starts at box 1."
  [box level]
  (or (not= box 0) (= level 0)))

(defn- assign
  "The boxes `klass` would occupy in `col` (as `{:class :level :box :rows :capacity}`
   entries), or nil if it does not fit. Each level is checked against the SPECIFIC box it
   lands in; column totals are not enough, since the boxes are unequal. The class stays
   contiguous and in level order but may start at any free box, not only the next one."
  [col style {:keys [class levels]}]
  (let [rows (style-rows style)
        taken (set (map :box (:placed col)))
        free (remove taken (:column col))
        wanted (sort-by key levels)
        n (count wanted)
        runs (partition n 1 free)]
    (first
     (keep (fn [run]
             (let [pairs (map vector run wanted)]
               (when (every? (fn [[box [level need]]]
                               (and (<= need (nth rows box))
                                    (box-may-hold? box level)))
                             pairs)
                 (mapv (fn [[box [level need]]]
                         {:class class :level level :box box :rows need
                          :capacity (nth rows box)})
                       pairs))))
           runs))))

(defn- place
  "Puts `klass` in `col` at the boxes `assign` chose."
  [col style klass]
  (if-let [entries (assign col style klass)]
    (update col :placed into entries)
    col))

(def ^:private widest-column
  "Boxes in the largest column. A class needing more than this cannot be held by
   any single column."
  (apply max (map count columns)))

(defn- spread-across-page
  "Lays one class across a whole page's boxes in order, for a class with more levels than any
   column holds. Returns its entries, or nil when the boxes run out or a level does not fit
   the box it lands in."
  [style {:keys [class levels]}]
  (let [rows (style-rows style)
        wanted (sort-by key levels)
        ;; A class with no cantrips starts at box 1: box 0 takes cantrips only.
        all-boxes (cond->> (apply concat columns)
                    (not (contains? levels 0)) rest)]
    (when (<= (count wanted) (count all-boxes))
      (let [pairs (map vector all-boxes wanted)]
        (when (every? (fn [[box [level need]]]
                        (and (<= need (nth rows box)) (box-may-hold? box level)))
                      pairs)
          (mapv (fn [[box [level need]]]
                  {:class class :level level :box box :rows need
                   :capacity (nth rows box)})
                pairs))))))

(defn- add-spread-page
  "A page carrying one class laid across all three columns."
  [style klass entries]
  (mapv (fn [column]
          {:column column
           :placed (vec (filter #(contains? (set column) (:box %)) entries))})
        columns))

(def pact-column
  "The column a pact caster is given, reserved so its own spell-slots fields stay off other
   classes: boxes 0, 1 and 2. A 5e Warlock casts at one slot level, so it needs ONE level box,
   relabelled as it levels. Cantrips take box 0 and the list box 1, spilling into box 2 when
   it outgrows box 1."
  0)

(defn- place-pact
  "Lays a pact caster across the first column: cantrips, then the one level it
   casts at, spilling into the third box when the list outgrows the second."
  [style {:keys [class levels]}]
  (let [rows (style-rows style)
        boxes (nth columns pact-column)
        cantrips (get levels 0)
        [cast-level cast-rows] (first (sort-by key (dissoc levels 0)))]
    (vec
     (concat
      (when (and cantrips (pos? cantrips))
        [{:class class :level 0 :box 0 :rows (min cantrips (nth rows 0))
          :capacity (nth rows 0)}])
      (when cast-level
        (let [first-box (nth boxes 1)
              second-box (nth boxes 2)
              head (min cast-rows (nth rows first-box))
              tail (- cast-rows head)]
          (cond-> [{:class class :level cast-level :box first-box :rows head
                    :offset 0 :capacity (nth rows first-box)}]
            (pos? tail)
            ;; The continuation carries the REST of the same list, so it starts
            ;; where the first box stopped. Without the offset both boxes print
            ;; the list from the top and the second is a duplicate.
            (conj {:class class :level cast-level :box second-box
                   :rows (min tail (nth rows second-box))
                   :offset head
                   :capacity (nth rows second-box)}))))))))

(defn pack
  "Assigns `classes` to boxes: first fit by column, never splitting a class, so a player reads
   one list down one column. `classes` is `[{:class label :levels {level row-count} :pact?}
   ...]` in reading order. Returns a vector of pages, each a vector of three columns carrying
   `:placed` entries of `{:class :level :box :rows :capacity}` (a pact spill adds `:offset`).
   A pact caster takes `pact-column` of page one; a class wider than any column gets a page of
   its own. A class that fits nowhere is dropped (see `unplaced`)."
  [style classes]
  (->>
   (reduce
    (fn [pages klass]
     (if (:pact? klass)
       ;; The first column, always, and never shared. A pact caster's slots are a
       ;; separate pool at a separate level, so a box of its own is what keeps
       ;; them off the classes beside it.
       (let [entries (place-pact style klass)
             pages (if (seq pages) pages [(empty-page style)])]
         (update-in pages [0 pact-column] update :placed into entries))
       (if (> (count (:levels klass)) widest-column)
       (if-let [entries (spread-across-page style klass)]
         (conj pages (add-spread-page style klass entries))
         pages)
       (let [;; The first column the class actually fits, scanning pages in order
             ;; then columns left to right.
             hit (first (for [[pi page] (map-indexed vector pages)
                              [ci col] (map-indexed vector page)
                              :when (assign col style klass)]
                          [pi ci]))]
         (if hit
           (update-in pages hit place style klass)
           ;; Nothing open anywhere: start a page. A class too big for any column
           ;; on an empty page cannot be placed at all, and is dropped rather than
           ;; silently overflowing a box.
           (let [fresh (empty-page style)
                 ci (first (keep-indexed #(when (assign %2 style klass) %1) fresh))]
             (cond-> (conj pages fresh)
               ci (update-in [(count pages) ci] place style klass))))))))
    [(empty-page style)]
    ;; Pact casters first, so the column is theirs before anything else is fitted.
    (concat (filter :pact? classes) (remove :pact? classes)))
   ;; The seed page, and any a spread class stepped over, hold nothing.
   (filterv (fn [page] (some (comp seq :placed) page)))))

(defn relabel-instructions
  "The boxes whose printed numeral no longer matches what they hold, as `{:section :box
   :label}`: a box holding another level gets that level, and an unused box gets a nil label
   so its printed numeral does not claim spells it lacks. `:section` counts from ONE,
   matching every field-name suffix (spells-3-1-1 and spell-slots-1-1 are section 1).
   GOTCHA: caller-supplied by the time the server sees them, so the server bounds-checks
   section, box and label before use."
  [pages]
  (vec (for [[index page] (map-indexed vector pages)
             col page
             box (:column col)
             :let [held (first (filter #(= box (:box %)) (:placed col)))]
             :when (or (nil? held) (not= box (:level held)))]
         {:section (inc index)
          :box box
          :label (when held (str (:level held)))})))

(defn unplaced
  "What `pages` failed to hold, as `{class {level rows}}`, empty when it holds everything.
   Packing drops silently in three places: a wide class no page-wide spread fits, a class no
   column of a fresh page fits, and a pact list past its two boxes. The counts that went in
   are compared with those placed; nothing is re-placed, and the caller should not print a
   packing that loses spells."
  [classes pages]
  (let [placed (reduce (fn [acc {:keys [class level rows]}]
                         (update-in acc [class level] (fnil + 0) rows))
                       {}
                       (for [page pages col page e (:placed col)] e))]
    (into {}
          (keep (fn [{:keys [class levels]}]
                  (let [missed (into {}
                                    (keep (fn [[level need]]
                                            (let [got (get-in placed [class level] 0)]
                                              (when (< got need) [level (- need got)]))))
                                    levels)]
                    (when (seq missed) [class missed]))))
          classes)))

(defn packing-shape
  "`classes` as pack wants them: a row COUNT per level, not the spell names.
   packed-fields is handed the names, and fits? the counts, so callers holding
   names go through here first."
  [classes]
  (mapv (fn [{:keys [class levels pact?]}]
          {:class class
           :pact? pact?
           :levels (into {} (map (fn [[lvl names]]
                                   [lvl (if (number? names) names (count names))]))
                         levels)})
        classes))

(defn fits?
  "Whether `classes` -- in packing-shape form -- can be packed on `style` with
   nothing lost."
  [style classes]
  (empty? (unplaced classes (pack style classes))))

(defn page-count [pages] (count pages))

(defn utilisation
  "Rows used against rows offered, for judging whether packing earned its keep."
  [style pages]
  (let [used (reduce + (for [page pages col page e (:placed col)] (:rows e)))
        offered (* (count pages)
                   (reduce + (map #(:rows (column-capacity style %)) columns)))]
    {:pages (count pages) :rows-used used :rows-offered offered}))

;; ─── From a packing to the fields the export writes ──────────────────────────

(defn- section-of
  "1-based section number for a page index, matching every field-name suffix."
  [index]
  (inc index))

(def styles-with-measured-numerals
  "Styles whose printed level numeral has been measured, and so whose boxes can be
   renumbered. Mirrors pdf/numeral-boxes, which holds the measurements -- the fact
   is repeated here because the builder decides the layout and cannot see the
   server's templates."
  #{1 2 3 4})

(defn packing-supported?
  "Whether a packed layout may be printed on `style`."
  [style]
  (contains? styles-with-measured-numerals style))

(defn packed-fields
  "The field map a packing produces. `classes` is pack's input with `:levels` holding spell
   NAMES (their counts are what pack fits), plus per class `:slots` {level total} (printed in
   the class's own boxes), `:ability`, `:dc`, `:attack` and `:pact?`.
   Returns {:pages <count> :unplaced :relabels :headings :fields}. `:relabels` is the list
   the server bounds-checks and applies; the browser cannot renumber a printed numeral."
  [style classes]
  (let [by-class (into {} (map (juxt :class identity)) classes)
        ;; :pact? has to survive into what pack sees, or the pact caster is fitted
        ;; like any other class and loses its reserved column.
        counted (mapv (fn [{:keys [class levels pact?]}]
                        {:class class
                         :pact? pact?
                         :levels (into {} (map (fn [[lvl names]] [lvl (count names)])) levels)})
                      classes)
        pages (pack style counted)
        placements (for [[index page] (map-indexed vector pages)
                         col page
                         entry (:placed col)]
                     (assoc entry :section (section-of index)))]
    {:pages (count pages)
     ;; Empty unless the packing lost something. A caller that prints anyway
     ;; prints a sheet missing spells, so pdf_spec takes this as the signal to
     ;; fall back to a page per class.
     :unplaced (unplaced counted pages)
     :relabels (relabel-instructions pages)
     ;; One heading per class, on the FIRST box of its run, carrying its ability, DC and
     ;; attack: a section has ONE such triple and a packed page holds several classes.
     ;; A class without cantrips (Paladin, Ranger) starts at a level box whose bar holds
     ;; live slot inputs; `:cantrips?` false tells the drawing side to make room there.
     :headings (vec (for [[class entries] (group-by :class placements)
                          :let [first-box (apply min (map :box entries))
                                {:keys [section level]} (first (filter #(= first-box (:box %))
                                                                       entries))
                                box first-box
                                k (get by-class class)]]
                      {:class class :box first-box :section section
                       ;; True when the bar is FREE, which is what the drawing
                       ;; side needs to know. A box holding cantrips has no slots,
                       ;; and box 0 only ever holds cantrips.
                       :cantrips? (or (zero? box) (zero? level))
                       :ability (:ability k) :dc (:dc k) :attack (:attack k)}))
     :fields
     (into {}
           (concat
            ;; One header a page. The template carries one class name per
            ;; section, so two classes sharing a page share the band above it.
            (for [[index page] (map-indexed vector pages)
                  :let [names (distinct (for [col page e (:placed col)] (:class e)))]
                  :when (seq names)]
              [(keyword (str "spellcasting-class-" (section-of index)))
               (s/join ", " names)])
            ;; A page holding ONE class is unambiguous, so its triple is filled
            ;; the way an unpacked sheet fills it.
            (apply concat
                   (for [[index page] (map-indexed vector pages)
                         :let [names (distinct (for [col page e (:placed col)] (:class e)))]
                         :when (= 1 (count names))
                         :let [k (get by-class (first names))
                               n (section-of index)]]
                     (cond-> []
                       (:ability k) (conj [(keyword (str "spellcasting-ability-" n))
                                           (:ability k)])
                       (:dc k) (conj [(keyword (str "spell-save-dc-" n)) (str (:dc k))])
                       (:attack k) (conj [(keyword (str "spell-attack-bonus-" n))
                                          (:attack k)]))))
            ;; The names, into the box the packer chose rather than the box that
            ;; shares the level's number.
            (for [{:keys [class level box section rows offset]} placements
                  :let [all (vec (get-in by-class [class :levels level]))
                        from (or offset 0)
                        mine (subvec all (min from (count all))
                                     (min (+ from (or rows (count all))) (count all)))]
                  [row nm] (map-indexed vector mine)]
              [(keyword (str "spells-" box "-" (inc row) "-" section)) nm])
            ;; The slot total belongs to the class holding the box, at the level it holds,
            ;; not the box's printed level. Box 0 (cantrips) has no slots, and only the box
            ;; a level STARTS in carries the total: a continuation is the same pool.
            (for [{:keys [class level box section offset]} placements
                  :when (and (pos? box) (zero? (or offset 0)))
                  :let [n (get-in by-class [class :slots level])]
                  :when n]
              [(keyword (str "spell-slots-" box "-" section)) (str n)])))}))
