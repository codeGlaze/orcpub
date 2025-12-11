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

;; Virtue-themed name pieces. Two pools are provided: `virtue-fortunate`
;; contains positive/hopeful virtue words, while `virtue-ironic` contains
;; darker or ironic counterparts that can be used for tiefling-style names.
(def virtue-fortunate
  ["Grace" "Hope" "Charity" "Valor" "Fortune" "Mercy" "Blessing" "Clemency" "Solace" "Remedy" "Light" "Faith" "Honor" "Bene" "Lucky"])

(def virtue-ironic
  ["Sin" "Guilt" "Woe" "Ruin" "Doom" "Vex" "Vice" "Bane" "Misery" "Sorrow" "Penance" "Fate" "Curse" "Blight" "Scorn"])

;; Tone helpers: allow species to request a particular "tone" when merging
;; parts. This keeps tiefling-specific virtue choices in one place and lets
;; other species reuse the same mapping.
(def default-tone :neutral)

(defn available-tones []
  [:neutral :fortunate :ironic])

(defn tone->virtue-pools
  "Return a map {:virtue-pre [] :virtue-post []} appropriate for the
   requested tone. 'fortunate' prefers `virtue-fortunate`, 'ironic' prefers
   `virtue-ironic`, and 'neutral' returns empty pools." [tone]
  (case tone
    :fortunate {:virtue-pre virtue-fortunate :virtue-post virtue-fortunate}
    :ironic {:virtue-pre virtue-ironic :virtue-post virtue-ironic}
    ;; default/neutral
    {:virtue-pre [] :virtue-post []}))

(defn merge-parts-by-tone
  "Merge local pre/post vectors with the shared pools according to tone and
   optional extras. Options: {:tone <keyword> :use-parts? true|false :extra-pre [] :extra-post []}
   Returns a map {:pre merged-pre :post merged-post}.")
  [local-pre local-post & {:keys [tone use-parts? extra-pre extra-post]
                           :or {tone default-tone use-parts? true extra-pre [] extra-post []}}]
  (let [{vp :virtue-pre vpst :virtue-post} (tone->virtue-pools tone)]
    (if (and use-parts? (not= tone :neutral))
      {:pre (vec (distinct (concat local-pre extra-pre vp parts/neutral-pre)))
       :post (vec (distinct (concat local-post extra-post vpst parts/neutral-post)))}
      {:pre (vec local-pre) :post (vec local-post)}))
