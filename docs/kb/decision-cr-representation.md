# Decision: how CR is represented

*What shape Challenge Rating should have across the app, and why. Measured 2026-09-16 on
`feature/extras-companions`. Written because the Extras plan called "CR is two different types" a
blocker, and the first thing to establish was whether anything actually compares them.*

## The short answer

**Nothing compares character CR to monster CR today, so there is no live defect.** But the app
carries three representations of one value and two formatters for it, and one of those
representations behaves differently on the JVM than in the browser. The Extras creature query is
the first thing that will make that matter.

**Recommendation: one numeric representation — a plain `double` — plus one formatter and one
parser in a shared namespace.** Change it once, now, before the query is written.

## What exists — VERIFIED, not recalled

| where | representation | read by |
| --- | --- | --- |
| SRD monster data (`monsters.cljc`) | ratio literal `1/4` — a **Ratio on the JVM**, a **double in CLJS** | the sort and the XP lookup |
| `challenge-ratings` (CR -> XP, `monsters.cljc:44`) | keyed the same way, `(/ 1 8)` etc. | display, the builder dropdown |
| Homebrew monster (`views.cljs:8380`) | `(js/parseFloat %)` — always a **double** | same |
| Character side, `?wild-shape-cr` (`classes.cljc:801`) | a **string**, `"1/4"` | nothing but prose |

317 monsters, 77 with fractional CR.

**Live comparisons: two, both CLJS-only.** `sort-by :challenge` (`spell_subs.cljs:1412`) and the
XP map lookup. Neither crosses the character/monster boundary.

**`?wild-shape-cr` is display-only.** Set by druid level to `"1/4"` / `"1/2"` / `"1"`, and its one
consumer 20 lines later interpolates it into an action summary — "You can transform into a beast
you have seen with CR 1/4". `?wild-shape-limitation` is the same shape, a sentence fragment. Nothing
filters on either. The `#_`-discarded Circle of the Moon (`classes.cljc:995`) sets the same
attribute to an **integer**, `(max 1 (int (/ (?class-level :druid) 3)))` — so the switched-off code
has the type that matches the data and the live code does not.

**Two formatters, same job, different implementations:**

- `views.cljs:1394` — `(case challenge 0.125 "1/8" 0.25 "1/4" 0.5 "1/2" challenge)`
- `views.cljs:8374` — `(if (< 0 v 1) (str "1/" (/ 1 v)) v)`

## The trap, which is latent rather than live

A ratio literal in a `.cljc` file is not one value. On the JVM it is a `clojure.lang.Ratio`; in
ClojureScript, which has no ratios, it is a double. Measured on the JVM:

```
(m/challenge-ratings (/ 1 8))  =>  25
(m/challenge-ratings 0.125)    =>  nil
(= (/ 1 8) 0.125)              =>  false
```

It does not bite today because **nothing JVM-side reads CR** — checked `pdf.clj` and
`pdf_spec.cljc`, no hits. It bites the first time the server renders a stat block with an XP
figure, or a JVM validator range-checks a homebrew CR. A homebrew monster's CR is already a double,
so it would miss the XP table on the JVM and print nothing — a silent wrong answer, not an error.

## The recommendation, and why

**One numeric type: `double`. One formatter. One parser. In `.cljc`, shared.**

- **CR must be ordered.** The whole Extras feature is a bounded query — "CR 1/4 or lower", "CR equal
  to your level or lower", and bucket B trading count against CR. A display string cannot answer
  that; the character side has to become numeric regardless of what else changes.
- **CLJS is already doubles on both sides**, SRD and homebrew alike. This is not a new convention,
  it is the existing one made explicit.
- **The fractional CRs are exact in floating point.** The only ones 5e uses are 1/8, 1/4 and 1/2 —
  negative powers of two, so exactly representable, safe as map keys and safe under `=`. Verified:
  each round-trips through `str`/`parse` unchanged. (The usual `0.1 + 0.2 ≠ 0.3` objection does not
  apply — CR is never summed.)
- **It removes the runtime divergence at the source.** Writing `0.25` instead of `1/4` in data and
  map keys makes JVM and CLJS agree, which is what `.cljc` is supposed to mean.
- **No user-data migration.** Homebrew monsters already store doubles.

### What changes

1. `monsters.cljc` — ratio literals to decimal, in `challenge-ratings` keys and in the 77 monsters.
   Mechanical, and pinned by a characterization test first.
2. One `cr->label` / `label->cr` pair, replacing both formatters.
3. `?wild-shape-cr` becomes numeric, and the druid action summary calls the formatter. The
   `#_`-discarded Moon code stops being the odd one out.
4. A spec for CR, so a homebrew monster cannot carry a string.

### What does not change

The XP table's values, the sort, and every rendered sentence — "CR 1/4" still reads "CR 1/4",
which is the point of keeping a formatter rather than pushing the display form into storage.

## Risk

Low, and it is all in step 1. The exposure is the 77 SRD rows: a typo there changes a monster's XP
silently. Characterize `challenge-ratings` lookups and the sort order across all 317 monsters
*before* touching the data, so the diff shows up as expected values rather than as nothing.

## What this does not settle

Whether the character-side bound is stored as CR at all. "CR equal to your level or lower"
(Polymorph, True Polymorph) compares a level to a CR, and bucket B's count-vs-CR trade is a table,
not a scalar. Those are query-shape questions for `decision-creature-query.md`, not
representation questions. This doc only says: whatever reads it, CR is one number.
