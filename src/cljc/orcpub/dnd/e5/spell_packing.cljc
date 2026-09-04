(ns orcpub.dnd.e5.spell-packing
  "Which spell level goes in which box of a printed spell page.

   A sheet's boxes are fixed: ten of them, in three columns, each holding a
   different number of rows. Today one box carries one level of one class, so a
   character with three level 1 spells and two level 2s spends two pages on five
   spells, and eight casting classes spend eight pages.

   This decides the assignment instead. It runs in the BUILDER -- the server sees
   a flat map of field names and knows nothing about classes or levels -- and the
   result travels to the export as field values plus a small list of relabel
   instructions the server applies with pdf/relabel-spell-level!."
  (:require [clojure.string :as s]))

(def sheet-geometry
  "Rows each level box holds, by sheet style, level 0 through 9.

   The count of FIELDS in the box, not of printed rows, because a row with no
   field behind it cannot be filled: whatever is placed there is reported
   unplaceable and dropped. The two are equal today and a test keeps them so.

   Counted off the masters in resources/, and wrong twice when they were not.
   Styles 1 and 3 were recorded as holding 13 at level 3, which they printed but
   could not fill -- their fields were numbered 1-10, 12, 13, 14, so spells-3-11
   went nowhere. Style 4 was recorded with 12 at level 1 where it has 13, and 13
   at level 2 where it had 11. dev/fix_spell_row_fields.clj repaired the
   templates; these are the counts that survived it."
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

(defn- assign
  "The boxes `klass` would occupy in `col`, or nil if it does not fit.

   Each level is checked against the SPECIFIC box it would land in. Column totals
   are not enough and using them is the obvious way to get this wrong: the boxes
   are unequal, so a twelve-row level 1 list does not fit a nine-row box however
   many rows the column has left over.

   The class stays contiguous and in level order -- that is what lets a player read
   one down a column -- but it may start at any free box, not only the next one. A
   cantrip list too long for box 0 can begin at box 1 instead, and skipping a box
   costs nothing since an unused box has to be blanked either way."
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
               (when (every? (fn [[box [_ need]]] (<= need (nth rows box))) pairs)
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
  "Lays one class across a whole page's boxes in order, checking each level fits
   the box it lands in.

   For a class with more levels than any column holds. Keeping it in one column is
   what stops several classes interleaving and becoming hard to read; a class that
   fills the sheet on its own has nothing to be confused with, and this is how the
   sheet already reads today."
  [style {:keys [class levels]}]
  (let [rows (style-rows style)
        wanted (sort-by key levels)
        all-boxes (apply concat columns)]
    (when (<= (count wanted) (count all-boxes))
      (let [pairs (map vector all-boxes wanted)]
        (when (every? (fn [[box [_ need]]] (<= need (nth rows box))) pairs)
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
  "The column a pact caster is given: boxes 0, 1 and 2.

   A 5e Warlock casts every spell at its highest slot level, so it needs ONE
   level box however high it climbs -- the numeral is relabelled as the character
   levels rather than a new box being used. Cantrips take box 0 and the spell list
   takes box 1, spilling into box 2 only because a level 20 Warlock knows 15
   spells against box 1's 12 rows.

   Reserving the first column rather than fitting it like any other class is what
   keeps its slot count off everything else: the boxes it holds carry its own
   spell-slots fields, and the rest of the sheet is left to casters whose slots
   come from the shared table."
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
  "Assigns `classes` to boxes: first fit by column, never splitting a class.

   `classes` is `[{:class label :levels {level row-count}} ...]` in the order they
   should read. Returns a vector of pages, each a vector of three columns carrying
   `:placed` entries of `{:class :level :box :rows :capacity}`.

   Keeping a class within one column is what makes the result worth reading: a
   player finds their list by looking down one column rather than hunting across a
   page, and a column takes more than one class when they fit. A class with more
   levels than any column holds is the exception -- it gets a page laid out the way
   the sheet already reads, since with nothing beside it there is nothing to
   confuse it with."
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
  "The boxes whose printed numeral no longer matches what they hold.

   Two kinds, both of which read as a lie if left alone: a box carrying a level
   other than its own needs renumbering, and a box a class did not use still
   carries its printed numeral, so an unused box 4 in a Paladin column reads as
   Paladin level 4 spells.

   `:section` counts from ONE, matching the suffix every field name carries --
   spells-3-1-1 and spell-slots-1-1 are section 1. It came off map-indexed and so
   counted from zero, which named a section no template has.

   Caller-supplied by the time the server sees them, so section, box and label are
   bounds-checked there before use -- the same hole as the sheet style id."
  [pages]
  (vec (for [[index page] (map-indexed vector pages)
             col page
             box (:column col)
             :let [held (first (filter #(= box (:box %)) (:placed col)))]
             :when (or (nil? held) (not= box (:level held)))]
         {:section (inc index)
          :box box
          :label (when held (str (:level held)))})))

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

(defn packed-fields
  "The field map a packing produces.

   `classes` is what pack was given, plus what to print:

       [{:class \"Wizard\" :levels {0 [\"Fire Bolt\" ...] 1 [...]}
         :slots {1 4, 2 3}} ...]

   `:levels` holds the spell NAMES, and their counts are what pack fits.
   `:slots` is that class's own slot totals by level -- which is the point of
   packing by class rather than by ability. Each of the nine level boxes carries
   its own spell-slots field, so a class holding its own column carries its own
   slot counts in those boxes, and a Warlock's Pact Magic stops being averaged
   into whatever else shares its casting ability.

   Returns {:fields :relabels :pages}. `:relabels` is the instruction list the
   server bounds-checks and applies; the browser cannot renumber a printed
   numeral itself."
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
     :relabels (relabel-instructions pages)
     ;; Where to write each class's name: the cantrips box its column starts
     ;; with. A class with no cantrips -- a Paladin, a Ranger -- starts at a level
     ;; box whose slot inputs the player writes in, so it gets no heading and the
     ;; section header is all that names it.
     ;; The save DC and attack bonus travel with the heading rather than as
     ;; section fields. The sheet gives a section ONE ability/DC/attack triple,
     ;; and a packed page holds several classes whose numbers differ, so filling
     ;; it would print one class's DC over everyone's list.
     ;; One heading a class, on the FIRST box of its run. A class with cantrips
     ;; starts at a cantrips box, whose bar is free because the box has no slots.
     ;; A Paladin or Ranger has no cantrips and starts at a level box, whose bar
     ;; carries live slot inputs -- so it is flagged, and the drawing side makes
     ;; room there rather than skipping it and leaving the column unnamed.
     :headings (vec (for [[class entries] (group-by :class placements)
                          :let [first-box (apply min (map :box entries))
                                {:keys [section level]} (first (filter #(= first-box (:box %))
                                                                       entries))
                                box first-box
                                k (get by-class class)]]
                      {:class class :box first-box :section section
                       ;; True when the bar is FREE, which is what the drawing
                       ;; side needs to know. A box holding cantrips has no slots;
                       ;; so does box 0, whatever level it ends up holding, since
                       ;; it has no slot inputs until reuse-cantrips-box! adds
                       ;; them. A lone Paladin packs into box 0 and would
                       ;; otherwise be treated as needing room made in a bar that
                       ;; has nothing in it.
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
            ;; The slot total belongs to the class that holds the box, at the
            ;; level it is holding -- not to the level the box is printed with.
            ;; Box 0 is the cantrips box and has no slots until reuse-cantrips-box!
            ;; gives it some.
            ;; Only the box a level STARTS in carries the slot total: a
            ;; continuation is the same pool, and printing it twice reads as two
            ;; sets of slots for one level.
            (for [{:keys [class level box section offset]} placements
                  :when (and (pos? box) (zero? (or offset 0)))
                  :let [n (get-in by-class [class :slots level])]
                  :when n]
              [(keyword (str "spell-slots-" box "-" section)) (str n)])))}))
