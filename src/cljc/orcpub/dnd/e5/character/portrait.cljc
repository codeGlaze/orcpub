(ns orcpub.dnd.e5.character.portrait
  "Pure helpers for the random-portrait ('jaunty') button.

   The randomize button fills ::char5e/image-url with a DiceBear seed-based
   avatar URL. DiceBear is a public avatar service that returns a
   deterministic image for a given (style, seed) pair, which keeps QA
   reproducible and lets us pick a style that flatters the character's
   race without shipping any binary assets.

   The URLs produced here match the http(s) regex used server-side in
   orcpub.routes/pdf-response, and the PNG endpoint is used because
   PDFBox does not render SVG."
  (:require [clojure.string :as s]))

(def dicebear-host "https://api.dicebear.com/9.x")

(def default-style "adventurer")

(def race->style
  "Maps a race display-name to a DiceBear style whose look flatters that
   race. Anything not listed falls back to `default-style`."
  {"Elf"        "lorelei"
   "Half-Elf"   "lorelei"
   "Dwarf"      "big-ears"
   "Halfling"   "big-ears"
   "Gnome"      "big-ears"})

(defn style-for-race [race-name]
  (get race->style race-name default-style))

(defn random-seed []
  #?(:clj  (str (java.util.UUID/randomUUID))
     :cljs (str (random-uuid))))

(defn random-portrait-url
  "Build a DiceBear avatar URL for the given seed, optionally styled to
   the character's race. Callers pass a fresh seed each click to get a
   new portrait; passing the same seed always yields the same image."
  ([seed]
   (random-portrait-url seed nil))
  ([seed race-name]
   (str dicebear-host "/" (style-for-race race-name) "/png?seed=" seed)))
