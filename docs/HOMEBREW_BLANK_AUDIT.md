# Homebrew export — blank/false/empty audit (B6)

> Goal: stop exporting meaningless blanks (`false` flags, `nil`s, empty
> collections) into `.orcbrew` files, **without** dropping the few blanks that
> actually carry meaning. This is the "must-keep" list the export cleanup honors.

## What MUST be kept (do NOT strip)

### Keys where `nil` is a real answer
These are preserved on import already (`orcbrew-validation/nil-preserve-fields`);
dropping their `nil` would change meaning:
- `:spell-list-kw`
- `:ability`
- `:class-key`

### Required fields (must be present with REAL values, never blank)
Not a "keep the blank" case — these must hold real values or import rejects the
item, so the cleanup never touches a present, non-blank value anyway:
- `:name`, `:key`, `:option-pack` (all homebrew items)
- `:class` (subclass), `:race` (subrace), `:hit-points` (monster)
- `:school`, `:level` (spells); `:spell-lists` must keep ≥1 `true`

### "First-class-only" proficiency data — NOT affected
The multiclass "only counts for your first class" rule is stored as
`[prof-kw first-class?]` **pairs** (vectors), e.g. `[:heavy true]`, decoded in
`options.cljc:1962-1979` — it is **not** a `{key false}` map value. The cleanup
strips `false`/`nil`/empty **map values** and recurses into vector *elements*
without dropping them, so these pairs survive intact. (This was the feared
landmine; it isn't one.)

## What is SAFE to strip (meaningless blanks)

- **`false` map values** — for the flag maps content actually uses (skill/save/
  tool profs, spell components, etc.), `false` reads identically to absent:
  consumers do `(not (get profs k))`, where `false` and `nil`/absent both yield
  the same result (`options.cljc:152`). The toggle "false-cruft"
  (`{:athletics false …}` left by clicking on→off) lives here.
- **`nil` map values** — except the three keep-nil keys above.
- **`:disabled?` = `false`/`nil`** — enabled is the default; `false`, `nil`, and
  absent all decode to enabled (`(not (:disabled? x))`). Only `true` matters.
- **Empty collections** (`{}`, `[]`, `#{}`) — absence is equivalent for consumers.

## The rule (one line)
Drop a **map entry** whose (recursively-cleaned) value is `nil` (unless the key is
in the keep-nil set), `false`, or an empty collection. Recurse into vector/set
elements but never drop them positionally (so `[kw bool]` pairs survive).

## Safety net
A **round-trip test** on real content: strip it, and assert (a) it's still
spec-valid, (b) every value that *remains* is byte-identical to the original
(we only removed, never altered), and (c) everything removed was in fact a
blank/false/empty. If a strip ever changes a real value or breaks validity, the
test fails. The cleanup runs on **normal exports only** — the raw/emergency and
draft exports stay byte-for-byte untouched (their whole point is "give me exactly
what I have").
