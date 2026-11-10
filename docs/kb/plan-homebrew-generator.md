# Plan: a homebrew generator for test and dev libraries

**Status:** planned, not started (owner, 2026-11-10: plan it, pin it, build when needed). Pinned
in `BRANCH.md`, "Someday".

## Why

Test libraries today are either hand-made for one purpose or cloned:

- `dev/scale_orcbrew_pack.clj` clones a real pack N times: new keys, same names. Realistic for
  name collisions (it exposed the reference lists' ghost rows, 2026-11-09), but the same content
  repeated.
- `dev/spellcaster_pack.clj`: one purpose-built spellcaster pack.
- `test/fixtures/*.orcbrew`: nine fixtures, each for one test (`coverage-pak`, `test-pak`,
  `sourced-classes`, and the deliberately broken `broken-*`, `junk-bases`, `keyword-trap`,
  `cruft-shapes`).
- Browser tests build their own small packs by hand (`test/browser/reference_lists_e2e.js`).

Missing: varied, valid content of every type, in any amount, the same every run.

## Design

1. **One generator, driven by the shape table.** `homebrew_shapes.cljc` describes every field of
   every homebrew type, so one function covers all of them and a new type is covered when its
   row is added (AGENTS.md: similar things share one function, differences as data).
2. **Seeded.** The same seed gives the same library, so tests can rely on exact counts.
3. **Modes, as options:**
   - `:varied` (default): distinct names and keys, plausible values.
   - `:collisions`: some entries share a name under different keys, across sources and within
     one, the case name-keyed code gets wrong. Keep this: it is a real-world shape, not noise.
   - `:overrides`: some entries take a built-in SRD key, which replaces the built-in entry.
   - `:junk` stays with the existing broken fixtures and the junk audit
     ([homebrew-guard.md](homebrew-guard.md)); the generator makes valid content.
4. **Valid is not meaningful.** Linked types must point at things that exist: subclasses at a
   class, a class's level selections at a selection, spell lists at spells. Build standalone types
   first (spells, items, monsters, feats, backgrounds, languages), then linked ones in dependency
   order.
5. **Three ways in:** a REPL call returning a library map, a command (`lein run -m ...`) writing
   an `.orcbrew` file, and a call from tests (the cljs tests and the browser tests' pack
   builders).
6. **Later, the dev button.** A "Fill randomly" button on each builder that calls the same
   generator for its type, exercising the real builder save path. It must be compiled out of the
   production bundle (900 KB ceiling, `test/page-load-budget`). The 6 schema-driven builders
   share one form; the 10 hand-written ones each need wiring.

## Where it goes

Code branch, not agents/develop: a tool a human developer uses (AGENTS.md branch table). The
generator in a shared `.cljc` namespace beside the shape table; the command in `dev/`.

## Open when it starts

- Its branch name (ask the owner).
- How plausible the text should be (lorem words, or SRD-like phrasing from a word list).
