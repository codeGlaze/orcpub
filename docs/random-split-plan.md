Title: Random data split & RNG refactor plan

Goal
- Move large static data lists (names, tavern-names, race-name lists, etc.) out of `src/cljc/orcpub/dnd/e5/character/random.cljc` into standalone data files; create a seedable, testable RNG API and keep the code tidy and maintainable.

Why
- `random.cljc` currently mixes algorithm logic and very large static lists. This makes the file noisy, hard to review, and painful to diff or unit test.
- Separating data from logic improves code readability, reduces merge conflicts, enables lazy-loading selective data, and simplifies translation to other formats (EDN/JSON) if needed.

High-level approach
1. Data separation
  - Create a `data/` directory under `resources/` or `src/cljc/orcpub/data/` (choose `src/cljc/orcpub/data` for CLJC-accessible EDN files or `resources/data` for runtime-loaded EDN).
  - Split each large list into its own EDN file and namespace-aware map. Example:
    - `src/cljc/orcpub/data/names.edn` : contains maps like `{:turami-names [...], :chondathan-male-names [...], ...}`
    - `src/cljc/orcpub/data/taverns.edn` : contains `{:tavern-names [...], :tavern-prefixes [...]}`
  - Keep file sizes reasonable (group related sets but avoid monolithic files).

2. Loading strategy
  - For CLJ server-side code and tests, load EDN via `clojure.edn/read-string` or `clojure.edn/read` from the classpath using `io/resource`.
  - For CLJS (browser), convert EDN to CLJS readable data at compile time by checking in a `src/cljs/orcpub/data/*.cljs` var file that def's the maps as Clojure data (generated from EDN by a tiny script) OR store JSON under `resources/public/data/*.json` and fetch via XHR where needed.
  - Option: Use `src/cljc/orcpub/data/*.edn` compiled into CLJS via the build pipeline if your toolchain supports it (e.g., tools.build or script to generate CLJS data files).

3. RNG split (summary)
  - Add a cljc facade `src/cljc/orcpub/random.cljc` exposing a small pure API: `make-rng`, `rng-next-int`, `rng-next-float`, `rand-nth-with`, `shuffle-with`, plus convenience wrappers.
  - Implement `src/clj/orcpub/random_impl.clj` (SplittableRandom) and `src/cljs/orcpub/random_impl.cljs` (mulberry32/xorshift) to back the facade.

Data layout examples
- src/cljc/orcpub/data/names.edn
  {:turami-names ["Ael" "Bel" ...]
   :chondathan-male-names [...]
   :chondathan-female-names [...]
   :dwarf-names [...]
   }

- src/cljs/orcpub/data/names.cljs (generated)
  (ns orcpub.data.names)
  (def turami-names ["Ael" "Bel" ...])
  (def chondathan-male-names [...])

Migration/checklist
- [ ] Create data files for the largest groups in `character/random.cljc` (start with tavern names and top 3 race-name groups).
- [ ] Add small loader helpers in `src/cljc/orcpub/data_load.cljc` to read EDN from classpath. For CLJS, add a generated `src/cljs` file generator script `scripts/generate-cljs-data.clj` to convert EDN to CLJS def files.
- [ ] Replace in-file references in `character/random.cljc` to refer to `orcpub.data.names/turami-names` or to loader functions where appropriate.
- [ ] Run `lein test` and a Figwheel REPL smoke test to verify UI flows that use the orcacle and tavern name generators.
- [ ] Iterate over other lists and move them, keeping PRs small (one or two data files per PR).

Testing
- Add tests to assert that name lists load correctly and functions return items from the lists.
- Add deterministic tests once RNG facade is in place: with a known seed produce expected name(s).

Notes & tradeoffs
- If you expect to update lists often (community contributions), keep them as separate EDN files in `resources/data` and load them at runtime; this allows editing without code changes.
- If CLJS must access data at compile time, prefer generating `src/cljs` data files as part of the build or include small JSON files in `resources/public/data` and fetch them when the relevant UI is shown.

Next steps I can take
- Scaffold the `src/cljc/orcpub/data` layout and move the top 2–3 largest lists into EDN files and wire CLJ/CLJS loading (small PR).
- Scaffold the `src/cljc/orcpub/random.cljc` facade and simple platform impls.

If you want me to do the scaffolding now, tell me which lists to extract first (I recommend: tavern-names, turami-names, chondathan-names).