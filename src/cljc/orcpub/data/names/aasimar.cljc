;; Aasimar name samples
;; Inspiration / sources:
;; - Celestial-flavored names from D&D lore (Player's Handbook, Monster Manual)
;; - Light/virtue-themed given names and surnames, angelic/celestial motifs
;; This file contains a small, non-exhaustive sample set for name generation.

(ns orcpub.data.names.aasimar
        (:require [orcpub.data.names.parts :as parts]))

;; Merge of two previously duplicated `aasimar-names` defs. We keep both
;; ::surnames and ::surname keys as aliases for compatibility with callers.
;; `aasimar-names` map structure and usage notes
;; Keys present:
;;  - ::male       => vector of male given names (strings)
;;  - ::female     => vector of female given names (strings)
;;  - ::surnames   => vector of full surname strings (fallback)
;;  - ::surname    => alias to ::surnames kept for compatibility
;;  - ::surname-pre  => vector of surname prefix parts used to assemble surnames
;;  - ::surname-post => vector of surname suffix parts used to assemble surnames
;;
;; Behavior expected by `orcpub.dnd.e5.character.random`:
;;  - If a name map provides `::surname-pre` and `::surname-post`, the generator
;;    will attempt to assemble a surname from those parts (e.g., "Dawn" + "bringer").
;;  - If the name map does not provide parts, the generator will select from
;;    `::surnames` or `::surname` as a fallback.
;;  - We keep both `::surnames` and `::surname` keys so older callers continue
;;    to work while migration happens.
(def aasimar-names
  (let [male ["Caelum" "Lucan" "Aurel" "Seren" "Thalan"
               "Cassiel" "Uriel" "Zadkiel" "Raziel" "Sariel"
               "Ezekiel" "Gabriel" "Elion" "Theron" "Alaric"
               "Rafael" "Mikael" "Lucien" "Oriel" "Soren"]
        female ["Liora" "Seraph" "Aurea" "Miriel" "Elanil"
                "Ariel" "Seraphine" "Elysia" "Lumina" "Mariel"
                "Angelia" "Celestine" "Clariel" "Israfel" "Anielle"]
        ;; Full surnames kept for fallback and for callers that prefer full strings
        surnames ["Dawnbringer" "Lightweaver" "Starwarden"
                  "Dawnwarden" "Lightbringer" "Starborn" "Skylighter"
                  "Sunwarden" "Radiantheart"]
        ;; Parts to assemble surnames. These are intentionally short lists; expand
        ;; them if you want more variety in generated surnames.
                ;; Local parts (kept first to preserve tone)
                surname-pre-local ["Dawn" "Light" "Star" "Sun" "Sky" "Aure" "Lum" "Radi" "Halo" "Ser" "Cel" "Sol"]
                surname-post-local ["bringer" "weaver" "warden" "born" "song" "shield" "bane" "crest" "guard" "veil"]]
        {::male (vec (distinct male))
         ::female (vec (distinct female))
         ::surnames (vec (distinct surnames))
         ::surname (vec (distinct surnames))
                                 ;; Merge in parts pools from shared `parts` namespace (neutral + nature)
                                 ;; Honor per-species opt-out via ::use-parts (default true for
                                 ;; backwards compatibility). To disable parts for aasimar set
                                 ;; ::use-parts false in the map.
                                 ::use-parts true
                                 ::surname-pre (let [use? (get aasimar-names ::use-parts true)]
                                                                                                        (if use?
                                                                                                                (vec (distinct (concat surname-pre-local parts/neutral-pre parts/nature-pre)))
                                                                                                                (vec surname-pre-local)))
                                 ::surname-post (let [use? (get aasimar-names ::use-parts true)]
                                                                                                         (if use?
                                                                                                                 (vec (distinct (concat surname-post-local parts/neutral-post parts/nature-post)))
                                                                                                                 (vec surname-post-local)))}))
