(ns orcpub.dnd.e5.spell-annotations
  "The marks printed beside a spell's name on a sheet: concentration, bonus action or
   reaction, and a costly material. All are read from the spell's existing fields --
   concentration from the start of :duration, the cost from a gp figure in the prose
   of :material-component. Ritual and plain V S M are deliberately not marked."
  (:require [clojure.string :as s]
            [orcpub.common :as common]))

(defn concentration?
  "Whether the spell needs concentration.

   5e writes this as the first word of the duration -- \"Concentration, up to 1
   minute\" -- and gives it no field of its own."
  [spell]
  (s/starts-with? (common/ascii-lower-case (:duration spell)) "concentration"))

(defn casting-tag
  "\"BA\" for a bonus action, \"RE\" for a reaction, nil for anything else.

   The two that change what else can be done in the same turn. An action or a
   longer casting time is the ordinary case and is not marked."
  [spell]
  (let [t (common/ascii-lower-case (:casting-time spell))]
    (cond
      (s/includes? t "bonus action") "BA"
      (s/includes? t "reaction") "RE")))

(defn material-cost
  "The price of the spell's material as printed, e.g. \"300gp\", or nil.

   Reads the figure out of the prose, since that is where 5e keeps it: \"diamond
   dust worth at least 100 gp, which the spell consumes\". The space goes so the
   column stays narrow, and the comma stays because 25,000gp is read at a glance
   and 25000gp is not."
  [spell]
  (when-let [m (re-find #"(\d[\d,]*)\s*gp"
                        (str (get-in spell [:components :material-component])))]
    (str (second m) "gp")))

(defn annotation
  "What to print beside `spell`, or nil when there is nothing to say.

   nil rather than a map of falses so a caller can skip the row outright: two
   thirds of rows carry nothing, and drawing is per row."
  [spell]
  (let [a (cond-> {}
            (concentration? spell) (assoc :concentration? true)
            (casting-tag spell) (assoc :tag (casting-tag spell))
            (material-cost spell) (assoc :material (material-cost spell)))]
    (when (seq a) a)))
