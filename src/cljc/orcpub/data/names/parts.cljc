;; Curated surname parts pools. These are neutral, themed lists intended to be
;; mixed into per-species `::surname-pre` and `::surname-post` lists as needed.
;; Keep this namespace CLJC so it can be used from CLJ and CLJS.

(ns orcpub.data.names.parts)

(def neutral-pre
  ["Mar" "Kel" "Ash" "Briar" "Vale" "River" "Stone" "Oak" "Moss" "High" "Kel" "Lun" "Rin" "Vor" "Thal"])

(def neutral-post
  ["ford" "crest" "ridge" "vale" "holm" "field" "borne" "weaver" "keeper" "bloom" "mark" "shade" "mere" "stone" "glen"])

(def nature-pre
  ["Briar" "Thorn" "Fen" "Heath" "River" "Glen" "Hollow" "Willow" "Ash" "Birch" "Haw" "Fern"])

(def nature-post
  ["grove" "wood" "mere" "holm" "fell" "grove" "bower" "haven" "branch" "leaf" "stream" "vale"])

(def occupational-post
  ["wright" "smith" "mason" "farer" "weaver" "keeper" "seeker" "runner" "finder" "wright"])

(def elemental-pre
  ["Ember" "Gale" "Frost" "Storm" "Tide" "Flint" "Glow" "Ash" "Sere" "Brum"])

(def elemental-post
  ["fire" "wind" "ice" "tide" "spark" "flare" "glow" "crest" "shade" "storm"])
