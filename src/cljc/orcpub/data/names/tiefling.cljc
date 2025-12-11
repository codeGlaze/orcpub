;; Extracted tiefling names (small sample)
(ns orcpub.data.names.tiefling
  (:require [orcpub.data.names.parts :as parts]))

(def tiefling-names
  (let [male ["Akmenos" "Barakas" "Damakos" "Ekemon" "Iados"]
        female ["Akta" "Anakis" "Cybel" "Kallista" "Lerissa"]
        surnames ["Zariel" "Rautha" "Vex" "Nerezza"]
        ;; small local parts to allow assembled surnames if desired
        surname-pre-local ["Ra" "Vex" "Ner" "Za"]
        surname-post-local ["th" "ra" "en" "a"]]
  ;; Expand first names with virtue-themed names (fortunate + ironic/undone)
    ;; Tone: :neutral (default), :fortunate, :ironic. Tiefling files can set
    ;; ::tone to pick a particular feel for virtue pools. We still allow
    ;; ::use-parts to opt out entirely (default true).
    ::tone parts/default-tone
  ;; Merge both virtue pools into given-names by default so tieflings get
  ;; a mix of fortunate and ironic virtue names. Use distinct to avoid dupes.
  ::male (vec (distinct (concat male parts/virtue-fortunate parts/virtue-ironic)))
  ::female (vec (distinct (concat female parts/virtue-fortunate parts/virtue-ironic)))
    ::surnames (vec (distinct surnames))
    ::surname (vec (distinct surnames))
    ::use-parts true
    ;; Merge surname parts and optionally merge virtue pools into given names
    ::surname-pre (let [use? (get tiefling-names ::use-parts true)
                         tone (get tiefling-names ::tone parts/default-tone)
                         {:keys [pre post]} (parts/merge-parts-by-tone surname-pre-local surname-post-local :tone tone :use-parts? use? :extra-pre parts/virtue-fortunate :extra-post parts/virtue-fortunate)]
                     pre)
    ::surname-post (let [use? (get tiefling-names ::use-parts true)
                          tone (get tiefling-names ::tone parts/default-tone)
                          {:keys [pre post]} (parts/merge-parts-by-tone surname-pre-local surname-post-local :tone tone :use-parts? use? :extra-pre parts/virtue-fortunate :extra-post parts/virtue-fortunate)]
                     post)}))
