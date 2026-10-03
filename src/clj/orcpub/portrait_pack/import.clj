(ns orcpub.portrait-pack.import
  "Turn a Loom manifest into resources/portrait-pack/loom.edn.

     lein run -m orcpub.portrait-pack.import manifest-v1.6.json
     lein run -m orcpub.portrait-pack.import manifest-v1.6.json --dry-run
     lein run -m orcpub.portrait-pack.import manifest-v1.5.json --prefer first

   Takes only what the Loom places -- iris regions, pupils, lids, whites --
   and says exactly which pieces changed. Choices that are ours (shadows,
   lips, gamma) live in pieces.edn and are never touched.

   It STOPS when the manifest lists one piece twice with different values,
   rather than picking one: that is how a Loom export once carried two
   placements of the same iris and the older one shipped. --prefer first or
   --prefer last decides, once you know which is right. It also lists pieces
   the manifest has and the registry does not, which need adding to
   asset-inventory before they can be chosen."
  (:require [cheshire.core :as json]
            [clojure.data :as data]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.pprint :as pp]
            [clojure.string :as s]
            [orcpub.dnd.e5.portrait-assets :as pa]))

(def loom-file "resources/portrait-pack/loom.edn")

(def header
  ";; What the Loom places on each piece of portrait art: iris regions, pupils,
;; upper lids, and (for an eye style drawn without whites) lower lids.
;;
;; GENERATED -- do not edit by hand. Re-create it from a Loom manifest with
;;
;;   lein run -m orcpub.portrait-pack.import <manifest.json>
;;
;; which says exactly which pieces changed. Choices that are ours rather than
;; the Loom's (which bangs cast shadows, which mouth is lips, iris gamma) live
;; in pieces.edn beside this file.
;;
;; Keyed by layer, then by the art's file name, lower-cased. Positions are
;; fractions of the art's width and height. This is geometry, not artwork:
;; the art itself is never committed.
")

(defn- round5 [x] (when (number? x) (/ (Math/round (* (double x) 100000.0)) 100000.0)))

(defn- curve [c]
  (when (map? c)
    (into {} (for [k [:x0 :y0 :mx :my :x1 :y1]] [k (round5 (get c k))]))))

(defn- placement
  "What the Loom placed on one manifest asset, in loom.edn's shape, or nil."
  [a]
  (when (seq (:iris a))
    (cond-> {:pupil (or (round5 (:pupil a)) 0.45)
             :iris (mapv (fn [e]
                           (cond-> {:cx (round5 (:cx e)) :cy (round5 (:cy e))
                                    :rx (round5 (:rx e)) :ry (round5 (:ry e))
                                    :rot (round5 (:rot e))}
                             (and (number? (:px e)) (not (zero? (:px e)))) (assoc :px (round5 (:px e)))
                             (and (number? (:py e)) (not (zero? (:py e)))) (assoc :py (round5 (:py e)))
                             (:lid e) (assoc :lid (curve (:lid e)))
                             (:lower e) (assoc :lower (curve (:lower e)))))
                         (:iris a))}
      (:whites a) (assoc :whites true))))

(defn registry-files
  "#{[layer \"file.png\"]} for every piece the registry carries."
  []
  (set (for [k pa/layer-order
             a (pa/assets-for-layer k)]
         [k (s/lower-case (last (s/split (:asset/url a) #"/")))])))

(defn manifest->loom
  "Read a parsed Loom manifest. Returns {:loom {layer {file placement}}
   :conflicts [{:piece [layer file] :values [...]}] :unknown #{[layer file]}}.
   `prefer` is nil (conflicts are reported and the piece left out), :first or
   :last."
  [manifest prefer known]
  (let [entries (for [layer (:layers manifest)
                      a (:assets layer)
                      :let [k (keyword (or (:key layer) (:name layer)))
                            f (s/lower-case (str (:file a)))
                            p (placement a)]
                      :when p]
                  [[k f] p])
        grouped (group-by first entries)
        conflicts (for [[piece es] grouped
                        :let [vals (distinct (map second es))]
                        :when (> (count vals) 1)]
                    {:piece piece :values (vec vals)})
        conflicted (set (map :piece conflicts))
        chosen (for [[piece es] grouped
                     :when (or prefer (not (conflicted piece)))]
                 [piece (second (if (= prefer :first) (first es) (last es)))])]
    {:loom (reduce (fn [m [[k f] p]] (assoc-in m [k f] p)) (sorted-map) chosen)
     :conflicts (vec conflicts)
     :unknown (into (sorted-set) (remove known (keys grouped)))}))

(defn changes
  "Which pieces differ between two loom maps: {:added :changed :removed}, each
   a sorted list of [layer file]."
  [old new]
  (let [pieces (fn [m] (set (for [[k fs] m [f _] fs] [k f])))
        o (pieces old) n (pieces new)]
    {:added (sort (remove o n))
     :removed (sort (remove n o))
     :changed (sort (filter #(and (o %) (n %) (not= (get-in old %) (get-in new %))) n))}))

(defn render-loom
  "loom.edn's text: the header, which pack the placements came from (the Loom
   shows it as the pack version), then the placements."
  ([loom] (render-loom loom nil))
  ([loom pack]
   (str header
        (when pack (str ";;\n;; Placed in pack " pack ".\n"))
        (with-out-str (binding [pp/*print-right-margin* 100] (pp/pprint loom))))))

(defn pack-of
  "The pack version loom.edn's placements came from, or nil."
  [text]
  (second (re-find #"(?m)^;; Placed in pack (\S+)\.$" (str text))))

(defn- current-loom []
  (let [f (io/file loom-file)]
    (if (.exists f) (edn/read-string (slurp f)) {})))

(defn -main [& args]
  (let [path (first (remove #(s/starts-with? % "--") args))
        dry? (some #{"--dry-run"} args)
        prefer (some->> (drop-while #(not= "--prefer" %) args) second keyword)]
    (when-not path
      (println "usage: lein run -m orcpub.portrait-pack.import <manifest.json> [--dry-run] [--prefer first|last]")
      (System/exit 2))
    (let [manifest (json/parse-string (slurp path) true)
          {:keys [loom conflicts unknown]} (manifest->loom manifest prefer (registry-files))
          old (current-loom)
          {:keys [added changed removed]} (changes old loom)]
      (println "Loom manifest:" path (str "(" (:generator manifest) ", pack " (:packVersion manifest) ")"))
      (doseq [{:keys [piece values]} conflicts]
        (println (str "\nCONFLICT " (s/join "/" (map name piece)) " is listed " (count values) " ways:"))
        (doseq [[i v] (map-indexed vector values)]
          (println (str "  " (if (zero? i) "first" (str "#" (inc i))) ": ") (pr-str v))))
      (when (and (seq conflicts) (not prefer))
        (println "\nNothing written. Re-run with --prefer first or --prefer last once you know which is right.")
        (System/exit 1))
      (when (seq unknown)
        (println "\nIn the manifest but not the registry (add to asset-inventory to use them):")
        (doseq [[k f] unknown] (println " " (name k) f)))
      (println "\nChanges to" loom-file)
      (doseq [[label ps] [["added" added] ["changed" changed] ["removed" removed]]
              p ps]
        (println (str "  " label ": " (name (first p)) " " (second p))))
      (doseq [p changed]
        (let [[was now] (data/diff (get-in old p) (get-in loom p))]
          (println (str "    " (second p) "\n      was " (pr-str was) "\n      now " (pr-str now)))))
      (when (every? empty? [added changed removed]) (println "  none -- already up to date"))
      (if dry?
        (println "\n--dry-run: nothing written.")
        (do (spit loom-file (render-loom loom (:packVersion manifest)))
            (println "\nWrote" loom-file "-- rebuild to see it."))))
    (shutdown-agents)))
