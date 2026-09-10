(ns orcpub.dnd.e5.spell-annotations
  "The marks printed beside a spell's name on a sheet.

   Everything here is already on the spell, though not always as its own field:
   concentration is the start of :duration rather than a flag, and a costly
   material is a gp figure inside the prose of :material-component.

   Of 319 spells, concentration touches 126, a costly material 52, a bonus action
   14 and a reaction 4. Ritual is deliberately absent: it would want an R beside
   the RE of reaction, and two single capitals that mean unrelated things is the
   confusion these columns exist to avoid.

   Plain V S M is absent for the reason the plan gives -- it is on nearly every
   spell, so it is the widest to print and the least worth reading. Only a
   material with a PRICE is carried, because that is the one that stops the spell
   happening if it is not in the pack."
  (:require [clojure.string :as s]))

(defn concentration?
  "Whether the spell needs concentration.

   5e writes this as the first word of the duration -- \"Concentration, up to 1
   minute\" -- and gives it no field of its own."
  [spell]
  (s/starts-with? (s/lower-case (str (:duration spell))) "concentration"))

(defn casting-tag
  "\"BA\" for a bonus action, \"RE\" for a reaction, nil for anything else.

   The two that change what else can be done in the same turn. An action or a
   longer casting time is the ordinary case and is not marked."
  [spell]
  (let [t (s/lower-case (str (:casting-time spell)))]
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
