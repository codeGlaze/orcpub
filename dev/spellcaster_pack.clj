;; Generate .orcbrew packs that isolate ONE dimension: how many homebrew SPELLCASTING
;; classes/subclasses (each with a full spell list) and how many CUSTOM SPELLS are loaded.
;;
;;   lein with-profile +test run -m clojure.main dev/spellcaster_pack.clj
;;
;; Clones the real spellcasting class out of test/fixtures/test-pak.orcbrew — a full
;; :spellcasting map with :level-factor, :known-mode, :spells-known, :cantrips-known and a
;; 285-entry :spell-list across levels 0-9 — so the shape matches real homebrew rather than
;; something invented. Each clone's spell-list is rewritten to reference that pack's own
;; CUSTOM spell keys, which is what a real homebrew caster does.
;;
;; Packs are kept under ~2.5 MB on purpose: localStorage caps at ~5 MB per origin, and a
;; pack that fails to persist silently measures a builder with no homebrew in it at all.
;; See docs/kb/perf-homebrew-builder-loop.md.
(require '[clojure.edn :as edn])

(def orig (edn/read-string (slurp "test/fixtures/test-pak.orcbrew")))
(defn- all-of [t] (mapcat (fn [[_ v]] (when (map? v) (vals (get v (keyword "orcpub.dnd.e5" t))))) orig))

(def caster    (first (filter :spellcasting (all-of "classes"))))
(def sub-caster (first (filter :spellcasting (all-of "subclasses"))))
(def spells    (vec (all-of "spells")))

(defn- sfx [i k] (keyword (str (name k) "-s" i)))

(defn make-spells
  "n custom spells, cloned from the fixture's real ones, spread across levels 0-9."
  [n src]
  (into {} (for [i (range n)
                 :let [base (nth spells (mod i (count spells)))
                       k (sfx i (or (:key base) :spell))]]
             [k (assoc base :key k :name (str "Custom Spell " i) :option-pack src)])))

(defn make-caster
  "One homebrew spellcasting class whose spell-list is the pack's own custom spells."
  [i src spell-keys]
  (let [k (keyword (str "hb-caster-" i))
        by-level (into {} (for [lvl (range 0 10)]
                            [lvl (set (take 40 (drop (* lvl 7) (cycle spell-keys))))]))]
    (-> caster
        (assoc :key k :name (str "HB Caster " i) :option-pack src)
        (assoc-in [:spellcasting :spell-list] by-level))))

(defn make-sub-caster [i src]
  (let [k (keyword (str "hb-sub-caster-" i))]
    (assoc sub-caster :key k :name (str "HB Sub Caster " i) :option-pack src
           :class (if (even? i) :rogue :fighter))))

(doseq [[n-casters n-spells] [[1 50] [8 200] [32 400] [64 400] [128 400]]]
  (binding [*print-length* nil *print-level* nil *print-namespace-maps* false]
    (let [src (str "Spellcaster Pack " n-casters)
          sp (make-spells n-spells src)
          ks (vec (keys sp))
          pack {src {:orcpub.dnd.e5/spells sp
                     :orcpub.dnd.e5/classes (into {} (for [i (range n-casters)]
                                                       [(keyword (str "hb-caster-" i))
                                                        (make-caster i src ks)]))
                     :orcpub.dnd.e5/subclasses (into {} (for [i (range n-casters)]
                                                          [(keyword (str "hb-sub-caster-" i))
                                                           (make-sub-caster i src)]))
                     :disabled? false}}
          p (str "dev-scratch/paks/spell-" n-casters ".orcbrew")
          s (pr-str pack)]
      (spit p s)
      (let [back (edn/read-string (slurp p))
            c (get back src)]
        (println (format "%-34s %6.0f KB  casters=%-4d subcasters=%-4d spells=%-4d"
                         p (/ (count s) 1024.0)
                         (count (:orcpub.dnd.e5/classes c))
                         (count (:orcpub.dnd.e5/subclasses c))
                         (count (:orcpub.dnd.e5/spells c))))))))
