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
| SRD monster data (`monsters.cljc`) | the form `(/ 1 4)` — evaluates to a **Ratio on the JVM**, a **double in CLJS** | the sort and the XP lookup |
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

## Spelled out, site by site

**The CR -> XP table** (`monsters.cljc:44`):

```clojure
;; BEFORE - ratio literals. Two different values depending on runtime.
(def challenge-ratings {0 10, (/ 1 8) 25, (/ 1 4) 50, (/ 1 2) 100, 1 200, ...})
;; AFTER
(def challenge-ratings {0 10, 0.125 25, 0.25 50, 0.5 100, 1 200, ...})
```

```clojure
;; on the JVM, today
(challenge-ratings (/ 1 8))  ; => 25     works
(challenge-ratings 0.125)    ; => nil    where a homebrew CR lands
(= (/ 1 8) 0.125)            ; => false
```

**The 77 monster rows**: `:challenge (/ 1 8)` -> `:challenge 0.125`. (They are written as
forms, not as `1/8` literals — same semantics, and the same per-runtime split.)

**Two formatters become one.** Today `views.cljs:1394` hardcodes
`(case challenge 0.125 "1/8" ...)` and `views.cljs:8374` computes
`(if (< 0 v 1) (str "1/" (/ 1 v)) v)`. Both become `display/cr->label`.

**The character side** (`classes.cljc:801`):

```clojure
;; BEFORE - a string, so it can only ever be printed
(mod/modifier ?wild-shape-cr
  (mod5e/level-val (?class-level :druid) {1 "1/4"  4 "1/2"  8 "1"}))
;; AFTER - a number, printed through the formatter
(mod/modifier ?wild-shape-cr
  (mod5e/level-val (?class-level :druid) {1 0.25  4 0.5  8 1}))
```

The rendered sentence is byte-identical either way. That is the point of a formatter.

**What it unlocks** - this is a type error today and merely true afterwards:

```clojure
(filter #(<= (:challenge %) ?wild-shape-cr) beasts)
```

## Why the current encoding exists - VERIFIED from history

**`?wild-shape-cr` as a string is not a decision.** Commit `3946ce60` (2017-05-23) introduced
the attribute and gave it two types **in the same commit**: the base druid got strings
`{1 "1/4" 4 "1/2" 8 "1"}` because its value feeds a sentence, and Circle of the Moon got
`(max 1 (int (/ (?class-level :druid) 3)))` because its value comes from a formula. Nobody was
choosing a type; each site was filled in with whatever its blank needed. It survived nine years
because nothing ever compared the value to anything.

**The ratio literals are a half-decent reason.** `f4db712c` (2017-06-23), a bulk
"add a bunch of missing monster fields" commit. `1/8` reads like the source book, is exact, and
is quicker to transcribe than `0.125` — real advantages for someone entering 300 statblocks. The
flaw is not the notation, it is that `monsters.cljc` is `.cljc` and ClojureScript has no ratios.
`cr->label` keeps the book's notation at the display layer, where a runtime cannot fork it.

## Knock-on effects - checked, not assumed

| consumer | touches CR? | effect |
| --- | --- | --- |
| **Encounter builder** | **No.** Stores `[:creatures n :creature :monster]` as a **keyword reference** plus a count (`views.cljs`, `creature-selector`). No stats copied | none |
| `encounters.cljc` | spec is `::name ::key ::option-pack` only | none |
| **Custom monster builder** | Yes — writes `(js/parseFloat %)`, already a double | none; it is already the target shape |
| Monster stat block display | Yes — the `case` formatter and the XP lookup | replaced by `cr->label`; output unchanged |
| Monster list sort | Yes — `sort-by :challenge` (`spell_subs.cljs:1412`) | unchanged; doubles sort as ratios did |
| PDF / anything JVM | **No** — checked `pdf.clj` and `pdf_spec.cljc`, no hits | none today; this is the latent trap the change removes |

**Real user content is already decimal.** Measured against a 636-monster `.orcbrew` pack: every
`:challenge` is a plain number, fractional ones written `0.5`, `0.25`, `0.125` — no strings, no
ratios, no nils. So the wire format already uses the shape being adopted, and the SRD's in-source
ratio literal is the outlier rather than the norm. **No user-data migration, and nothing breaks
for existing homebrew.**

## Risk

Low, and it is all in step 1. The exposure is the 77 SRD rows: a typo there changes a monster's XP
silently. Characterize `challenge-ratings` lookups and the sort order across all 317 monsters
*before* touching the data, so the diff shows up as expected values rather than as nothing.

## What this does not settle

Whether the character-side bound is stored as CR at all. "CR equal to your level or lower"
(Polymorph, True Polymorph) compares a level to a CR, and bucket B's count-vs-CR trade is a table,
not a scalar. Those are query-shape questions for `decision-creature-query.md`, not
representation questions. This doc only says: whatever reads it, CR is one number.
