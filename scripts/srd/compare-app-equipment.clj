;; Compares a generated srd_equipment.cljc with the app's weapons and armor, key by key, field by field.
;; Usage (from an app worktree): java -cp "$(lein classpath)" clojure.main THIS_FILE GENERATED_CLJC
;; Prints keys on one side only and every differing field. Exit 0 always: read the report.
;; KB: docs/kb/equipment-parity.md ("The app-shape generator")
(require '[orcpub.dnd.e5.weapons :as w] '[orcpub.dnd.e5.armor :as a] '[clojure.set :as set])
(load-file (first *command-line-args*))
(def gw (deref (resolve 'orcpub.dnd.e5.srd-equipment/weapons)))
(def ga (deref (resolve 'orcpub.dnd.e5.srd-equipment/armor)))

(defn by-key
  "Entries as a sorted map from :key to the entry. Args: xs, a seq of maps with :key."
  [xs] (into (sorted-map) (map (juxt :key identity)) xs))

(defn report
  "Prints the key and field differences between gen and app. Args: label, a heading; gen and app,
   seqs of entries. Returns nil."
  [label gen app]
  (let [g (by-key gen) a (by-key app)]
    (println "==" label ":" (count g) "generated," (count a) "in the app")
    (println "  only generated:" (vec (sort (set/difference (set (keys g)) (set (keys a))))))
    (println "  only in app:   " (vec (sort (set/difference (set (keys a)) (set (keys g))))))
    (doseq [[k gv] g :let [av (a k)] :when av]
      (let [fields (sort-by str (set/union (set (keys gv)) (set (keys av))))
            diffs (for [f fields :when (not= (gv f) (av f))] [f (gv f) (av f)])]
        (doseq [[f x y] diffs]
          (println (format "  %-18s %-34s generated %-28s app %s" (name k) (str f) (pr-str x) (pr-str y))))))))

(report "weapons" gw w/weapons)
(report "armor" ga a/armor)
