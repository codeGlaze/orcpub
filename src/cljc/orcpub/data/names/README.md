This folder contains per-culture name data used by orcpub's name generator.

Parts namespace
---------------

The `orcpub.data.names.parts` namespace provides curated "parts" pools
— small vectors of prefix/suffix pieces like `neutral-pre`, `nature-post`,
`occupational-post`, etc. These pools are meant to be optionally mixed into
per-species name maps to expand surname-building components while preserving
the species' native tone.

How to opt-in (safe pattern)
----------------------------

1. Require the parts ns at the top of the per-species file:

   (ns orcpub.data.names.foo
     (:require [orcpub.data.names.parts :as parts]))

2. After the def of the <species>-names map, merge the desired pools while
   preserving local items first and removing duplicates. Example pattern:

   (let [pre (vec (distinct (concat ::surname-pre parts/neutral-pre parts/nature-pre)))
         post (vec (distinct (concat ::surname-post parts/neutral-post parts/nature-post)))]
     (assoc <species>-names ::surname-pre pre ::surname-post post))

Notes
-----
- Keep the merge local-first (put local parts before shared pools) so the
  species tone remains dominant.
- Use `distinct` to avoid duplicates when multiple species share pieces.
- Do not remove the compatibility mappings in
  `src/cljc/orcpub/dnd/e5/character/random.cljc` until you've run the test
  suite and validated the CLJS Figwheel build locally.

If you add new pools to `parts.cljc`, update this README with a short note
explaining the pool's intended tone (e.g., "nature: flora/fauna-themed").
