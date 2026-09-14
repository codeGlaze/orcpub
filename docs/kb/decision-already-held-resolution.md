# Decision: where the "you already have this" rule lives

*DESIGN — nothing built. Evidence measured 2026-09-14. Companion to
[already-held-grants.md](already-held-grants.md), which pins what the app does today.*

## The question

A grant hands a character something they already have. Who decides what happens — the pool, the
granting feature, or the compiler?

## What the published rules actually do

**VERIFIED** from `codeGlaze/5etools-src` `data/` and the SRD 5.1
(`vitusventure/5thSRD` at `e1a7913`). Not one rule — **four distinct resolutions**, across at
least eight books:

| resolution | examples | sources |
| --- | --- | --- |
| **replace, same kind** | the general background rule | SRD 5.1 / PHB |
| **replace, from a narrower pool** | Tools of the Trade, Tool Proficiency, Royal Envoy, Arcane Archer Lore, Reanimator's Skill Set | TCE, EFA, SCAG, AU, RHW |
| **replace, different kind** — a *saving throw* proficiency | Iron Mind, Elegant Courtier, Unfettered Mind | XGE, XPHB, FRHoF |
| **expertise** | Keen Mind, Observant | **XPHB (2024 PHB)** |

The general rule, SRD `docs/character/backgrounds.md:15`:

> "If a character would gain the same proficiency from two different sources, he or she can choose
> a different proficiency of the same kind (skill or tool) instead."

The 2024 expertise wording, `feats.json`, Keen Mind and Observant, XPHB p.205:

> "If you lack proficiency in the chosen skill, you gain proficiency in it, and if you already
> have proficiency in it, you gain Expertise in it."

Two consequences that decide the design:

- **The resolution is a property of the granting FEATURE, not of the pool.** Two different feats
  granting from `:skills` resolve a duplicate differently — Observant gives expertise, a
  background gives a replacement pick. A flag on the pool cannot express that.
- **Expertise-on-duplicate is a 2024 mechanic.** It does not exist in the 2014 SRD, and the only
  2014-era content that reached for it (`ua_feats.cljc`, `ua_race_feats.cljc`) is `#_` discarded.
  So this is edition drift, and belongs in [edition-drift.md](edition-drift.md) too.

## Decision

**The resolution is declared on the grant, defaulted by the compiler, and `background-skills-cfg`
is retired rather than generalised.**

```clojure
{:pool :skills :key :perception}                  ; default: the SRD general rule
{:pool :skills :key :perception :if-held :expertise}              ; Observant, Keen Mind (XPHB)
{:pool :skills :key :perception :if-held :saving-throw}           ; Iron Mind, Elegant Courtier
{:pool :tools  :key :smiths     :if-held {:from :tools
                                          :filter #{:artisans}}}  ; Tools of the Trade
```

Rejected alternatives, and why:

- **A `:replaceable?` flag on the pool.** An earlier sketch in this investigation. The data kills
  it: the same pool resolves differently per granting feature.
- **Generalising `background-skills-cfg` in place.** It is `background-option`'s private layer-3
  compiler. Uplifting it means every other silo calls into the background's helper, which is the
  bespoke-wiring shape the branch exists to remove.
- **A per-silo context map.** Tried earlier in this investigation and abandoned: two silos, two
  different map shapes, which is exactly the N×M wiring the direction doc rejects.

## Pseudocode

**`:if-held` is DATA, not a call.** The compiler maps a small closed vocabulary to builders it
already has. No predicate, no function, nothing an `.orcbrew` could smuggle in — the PINS rule
that homebrew prereqs are never raw fns applies here too.

```clojure
;; grant_pools.cljc — unchanged. A pool still knows only its own options.
:skills {:name "Skill" :offerable-by char-silos :tags #{:profs :skill-profs}
         :options-fn (fn [_] (map opt5e/skill-option skills5e/skills))}

;; options.cljc — the whole rule, in one place
(def ^:private held-resolutions
  "What a duplicate turns into. Closed vocabulary; an unknown key resolves as :none, so an
   unrecognised pack degrades rather than breaks (same graceful-miss as an unregistered pool)."
  {:replace-same-kind …   ; SRD default for :skills and :tools
   :expertise         …   ; XPHB Observant / Keen Mind
   :saving-throw      …   ; XGE Iron Mind / Elegant Courtier
   :none              …}) ; languages, weapons, armor — no rule exists

(defn compile-grants [grants pools owner]              ; ← owner is the new argument
  (reduce
   (fn [acc {:keys [pool key count if-held] :as g}]
     (let [{:keys [options default-if-held]} (pools pool)]
       (cond
         ;; CHOICE — unchanged. Options already carry their own already-have prereq.
         count (update acc :selections conj (grant-selection g pools))

         ;; FIXED — grant it, plus whatever this feature says a duplicate becomes
         key (let [resolve (held-resolutions (or if-held default-if-held :none))]
               (-> acc
                   ;; the grant itself, suppressed when already held from elsewhere
                   (update :modifiers into
                           (modifiers-of options key {:unless-held-by-other owner}))
                   ;; and the resolution, active only when it IS held from elsewhere
                   (merge-resolution (resolve {:pool pool :key key :owner owner
                                               :options options}))))
         :else acc)))
   {:modifiers [] :selections []} grants))
```

`:unless-held-by-other` and the resolution's own gate are the two halves of what
`background-skills-cfg` writes by hand today — the modifier's `[(not (get ?skill-profs k))]`
condition, and the selection's `(and srcs (not (srcs background-nm)))` prereq. Both need `owner`,
because both ask *"did someone who is not me already give me this?"*

## Terseness: `:keys`, not a row per key

A row per granted thing is worse than what it replaces. Criminal, today:

```clojure
:profs {:skill {:deception true, :stealth true}
        :tool  {:thieves-tools true}
        :tool-options {:gaming-set 1}}
```

as one-row-per-key — four rows, and the two skills read as unrelated:

```clojure
:grants [{:pool :skills :key :deception}
         {:pool :skills :key :stealth}
         {:pool :tools  :key :thieves-tools}
         {:pool :tools  :count 1 :filter #{:gaming-set}}]
```

with `:keys` — three rows, one per concept, same count as the shape it replaces:

```clojure
:grants [{:pool :skills :keys #{:deception :stealth}}
         {:pool :tools  :keys #{:thieves-tools}}
         {:pool :tools  :count 1 :filter #{:gaming-set}}]
```

**`:keys` plural, never `:key` accepting a set.** One field with two types is the
string-vs-keyword footgun in another costume (`dropdown-value-coercion.md`). `:key` stays
singular; `:keys` takes a set; a row carrying both is malformed.

Precedent: D33 chose terse authored shapes for `:ability-increases` for exactly this reason, and
§3c′ of the framework doc settles that authored shape is never a runtime cost — so this is a
readability decision and nothing else.

Worth stating because it changes what "verbose" costs: **nobody hand-writes these.** The builder
emits them, and the SRD backgrounds keep `:profs` through the shim. Terseness buys a readable
diff, not an easier authoring experience.

## `:prereq-fn` — the same field, needed twice

`grant-selection` is **four fields short** of expressing what `skill-selection-2` already does:

```clojure
;; what skill-selection-2 accepts
{:keys [options num min max order key prereq-fn]}

;; what grant-selection emits
{:name … :tags … :multiselect? true :min n :max n :options …}
;;   missing: :key  :order  :prereq-fn  and min ≠ max
```

Three are plain data. `:prereq-fn` is a function, so it is **internal-only** — built-in callers
pass one, authored content never can (the PINS rule again).

It is wanted twice over, which is the argument for adding it early rather than filing it as
cleanup:

| wanted for | why |
| --- | --- |
| class multiclass rows (19, 20) | `class-skill-selection … first-class?` gates the pick today; a grant-compiled version drops it |
| a resolution's replacement pick | the pick appears only while the duplicate does — that gate *is* a `prereq-fn` |

With it, the seven skill/tool selection builders become expressible as grants and the collapse
stops being hypothetical. Without it, neither the class rows nor the resolutions can be built.

**This is not the E3/E4 question.** Those are about what *writes* the data — the builder node and
its rows. This is about what *compiles* it. Related, and frequently confused in this
investigation.

## Before and after

**Before** — `background-option` carries its own compiler:

```clojure
(defn background-skills-cfg [background-nm skill-kws]
  {:modifiers  (map (fn [k] (modifiers/skill-proficiency k background-nm
                                                         [(not (get ?skill-profs k))]))
                    skill-kws)
   :selections (map (fn [k] (skill-selection (map :key skills/skills) 1 0 nil
                              (fn [c] (let [srcs (get (character/skill-proficiencies c) k)]
                                        (and srcs (not (srcs background-nm)))))))
                    skill-kws)})
```

…and every other silo has nothing:

```clojure
(make-feat-modifiers :skill-prof {:athletics true} owner)
;; => (skill-proficiency :athletics) — no condition, no fallback, duplicate wasted
```

**After** — the background is data, and the behaviour is the same everywhere:

```clojure
{:name "Acolyte" :grants [{:pool :skills :key :insight}
                          {:pool :skills :key :religion}]}
```

`background-skills-cfg` is deleted (D34: `#_`-struck with a date and a pinning test first).

### What that actually moves

**VERIFIED counts**, `feature/grant-rows` at `04d4c884`:

| | before | after |
| --- | --- | --- |
| implementations of the rule | 2, in different layers — `skill-option`'s option-prereq and `background-skills-cfg` | 1, in `compile-grants` |
| silos with the behaviour | 1 of 6 (background) | 6 of 6 |
| kinds covered | skills | skills **and tools**, as the SRD says |
| resolutions expressible | 1 (same-kind replacement) | 4, matching published content |
| grant compiler call sites needing change | — | 2, both of which already bind `key` |

**It does not shrink the selection layer.** The seven builders (`skill-selection-2`,
`skill-selection`, `class-skill-selection`, `skill-expertise-selection`, `skilled-selection`,
`tool-prof-selection`, `-aux`) and their 16 call sites are untouched by this decision — the
resolutions call into them. Consolidating *those* is a separate question, and claiming this
decision does it would be overselling.

## The addressing gate — RUN, and it passes

**VERIFIED 2026-09-14** (`conditional_selection_ref_test.clj`), through the shipping
`background-skills-cfg` path rather than a prototype. A background grants Athletics; a race may or
may not also grant it.

| rival race grants it | `?skill-profs` | selection offered |
| --- | --- | --- |
| no | `{:athletics {"Testbound" true}}` | none |
| yes | `{:athletics {nil true}}` — the background's own grant suppressed | `Skill Proficiency` at `[:background :testbound :skill-proficiency]` |

**The replacement selection is NESTED under its owner, not a top-level `:ref`.** So the `:ref`
addressing hazard from `handoff-grant-rows.md` step 3 does not apply to this shape, and the gate
is clear. The mechanism is `remove-disqualified-selections` (`entity.cljc:536`): the selection is
always present in the template with a stable path, and a failing `prereq-fn` only removes it from
what the builder OFFERS.

### But the pick outlives its justification

Measured on the same fixture — pick Insight in the replacement, then remove the duplicate:

```
with duplicate:    {:athletics {nil true},         :insight {nil true}}
duplicate removed: {:athletics {"Testbound" true}, :insight {nil true}}
```

The character keeps **both**. The selection vanishes from the builder, but the stored pick is
untouched and still compiles, so the background's own skill comes back and the replacement stays —
a free extra skill.

That is current behaviour in `background-skills-cfg`, not a consequence of this design. It is rare
today (you must change race after picking) and would become common the moment every silo offers
resolutions.

**OPEN, and it belongs to whoever builds this:** should a resolution's pick be revoked when its
condition lapses? Three options, none obviously right — leave it (today's behaviour, quietly
generous), suppress the pick's modifiers with the same `unless-held-by-other` gate the grant uses,
or surface it as a reconciliation notice like a dangling content reference. The second is
cheapest and the third is the most honest.

## Open
- **`:saving-throw` resolution scope.** Iron Mind and Elegant Courtier grant a saving-throw
  proficiency from a named ability list. Whether that is a resolution or just a differently-poled
  grant is undecided.
- **Default per pool.** `:skills` and `:tools` default to `:replace-same-kind`; everything else to
  `:none`. Whether that default belongs on the pool entry or in the compiler's fallback is a
  coin-flip and should be settled by which reads better at the call site.
- **2014 vs 2024.** `:expertise` is an XPHB resolution. Whether a 2014-only game should refuse it,
  warn, or allow it is the same question `edition-drift.md` carries for every other divergence.
