;; Renders the same character both ways -- a page per class as it ships today, and
;; packed by orcpub.dnd.e5.spell-packing -- so the layout can be looked at rather
;; than reasoned about.
;;
;;   lein with-profile init-db run -m clojure.main dev/preview_packing.clj
;;   STYLE=4 lein with-profile init-db run -m clojure.main dev/preview_packing.clj
;;
;; Writes target/packing-today.pdf and target/packing-packed.pdf.
;;
;; What this shows: which class and level land in which box, the boxes renumbered
;; to the level they actually hold, and the page count.
;;
;; What it does NOT show yet: the per-column class heading in the band above the
;; bar, the divider row for two classes sharing a box, and blanking the numeral on
;; a box nothing uses. Those are named in the plan and are the next pieces.

(require '[orcpub.pdf :as pdf]
         '[orcpub.dnd.e5.spell-packing :as pk]
         '[orcpub.dnd.e5.spells :as spells]
         '[clojure.java.io :as io]
         '[clojure.string :as st])
(import '[org.apache.pdfbox Loader] '[java.io FileOutputStream File])

(def style (Integer/parseInt (or (System/getenv "STYLE") "1")))

(def party
  "Eight casting classes at realistic list sizes -- the case the packer exists for.
   The fixture in sample_character.clj fills every row of every level it touches,
   which is the worst case rather than a typical one."
  [{:class "Bard 2"     :levels {0 2 1 4}}
   {:class "Cleric 2"   :levels {0 3 1 4}}
   {:class "Druid 2"    :levels {0 2 1 4}}
   {:class "Paladin 4"  :levels {1 3}}
   {:class "Ranger 4"   :levels {1 3}}
   {:class "Sorcerer 2" :levels {0 4 1 3}}
   {:class "Warlock 2"  :levels {0 2 1 2}}
   {:class "Wizard 2"   :levels {0 3 1 4}}])

(def ^:private names
  "Real spell names, so row widths on the page are honest."
  (vec (sort (map (comp :name val) spells/spell-map))))

(defn- rows-for [n seed]
  (mapv #(nth names (mod (+ seed (* 7 %)) (count names))) (range n)))

(defn- open-master []
  (Loader/loadPDF (.readAllBytes (.openStream (io/resource (:file (get pdf/sheet-masters style)))))))

(defn- base-fields [n]
  (into {:character-name "Packing Preview"
         :class-level (st/join "/" (map :class party))}
        (for [i (range 1 (inc n))]
          [(keyword (str "spellcasting-class-" i)) (str "Section " i)])))

;; ── today: a section per class, each level in its own box ────────────────────
(let [doc (open-master)
      n (count party)]
  (pdf/grow-spell-sections! doc n (:marks (get pdf/sheet-masters style)))
  (pdf/add-missing-spell-pages! doc {} 13)
  (let [fields (into (assoc (base-fields n) :class-level (st/join "/" (map :class party)))
                     (for [[i {:keys [class levels]}] (map-indexed vector party)
                           [level need] levels
                           [r nm] (map-indexed vector (rows-for need (+ level (* 13 i))))]
                       [(keyword (format "spells-%d-%d-%d" level (inc r) (inc i))) nm]))
        fields (into fields (for [i (range n)]
                              [(keyword (str "spellcasting-class-" (inc i)))
                               (:class (nth party i))]))]
    (pdf/write-fields! doc fields false {}))
  (with-open [out (FileOutputStream. (File. "target/packing-today.pdf"))]
    (.save doc out))
  (printf "today  style %d: %d pages, a section per class%n" style (.getNumberOfPages doc))
  (.close doc))

;; ── packed: the packer decides, boxes renumbered to what they hold ───────────
(let [pages (pk/pack style party)
      doc (open-master)
      n (count pages)]
  (pdf/grow-spell-sections! doc n (:marks (get pdf/sheet-masters style)))
  (pdf/add-missing-spell-pages! doc {} 13)
  (let [placed (for [[pi page] (map-indexed vector pages)
                     col page
                     e (:placed col)]
                 (assoc e :section (inc pi)))
        fields (into (base-fields n)
                     (for [{:keys [box level rows class section]} placed
                           [r nm] (map-indexed vector
                                               (rows-for rows (+ level (* 13 (hash class)))))]
                       [(keyword (format "spells-%d-%d-%d" box (inc r) section)) nm]))
        fields (into fields
                     (for [[pi page] (map-indexed vector pages)]
                       [(keyword (str "spellcasting-class-" (inc pi)))
                        (st/join " / " (distinct (for [col page e (:placed col)] (:class e))))]))]
    (pdf/write-fields! doc fields false {})
    ;; Renumber every box whose printed numeral is not the level it now holds.
    ;; Box 0 is the cantrips box and has its own treatment: it has no slot inputs
    ;; or labels until reuse-cantrips-box! gives it some.
    (doseq [{:keys [box level section]} placed
            :when (not= box level)]
      (if (zero? box)
        (pdf/reuse-cantrips-box! doc section (str level))
        (pdf/relabel-spell-level! doc box section (str level)))))
  (with-open [out (FileOutputStream. (File. "target/packing-packed.pdf"))]
    (.save doc out))
  (printf "packed style %d: %d pages%n" style (.getNumberOfPages doc))
  (doseq [[pi page] (map-indexed vector pages)]
    (printf "  page %d%n" (inc pi))
    (doseq [col page :when (seq (:placed col))]
      (printf "    boxes %-10s %s%n" (pr-str (:column col))
              (st/join ", " (map #(format "%s L%d in box %d (%d/%d rows)"
                                          (:class %) (:level %) (:box %) (:rows %) (:capacity %))
                                 (:placed col))))))
  (.close doc))
