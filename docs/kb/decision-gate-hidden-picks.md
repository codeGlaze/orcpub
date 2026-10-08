# Decision: gate hidden picks at build time, and tell the player

## Current decision (owner, 2026-10-01 to 2026-10-03) — supersedes the sections below where they differ

**The rule a player is told, in full:** *Your sheet saves as you go. A choice that stops applying
is set aside, and comes back by itself if it applies again and still fits. Anything set aside is
forgotten when you leave or reload the page.* If a design change needs a "sometimes" added to
that answer, the change is wrong.

| decided | what |
|---|---|
| set aside, not refunded | a pick whose gate closes (level lowered, class removed, moved, or changed) leaves the character and is held. Mistaken clicks and build experiments cost nothing |
| held in memory only | the hold lives with the open page: never saved, exported, or sent to the server. No session/local storage (tab and device behaviour is not predictable enough to explain) |
| forgotten when | the page is left or reloaded; or the player clears it, or its place is taken: a one-pick slot now holds another pick, or the same pick was chosen again. A slot with room still takes it back (owner, 2026-10-07: Defense + Archery, Archery held at 9, Defense swapped for Dueling, back at 10 is Dueling + Archery) |
| comes back | automatically when its gate reopens, if it still fits (a skill now held from elsewhere, deleted homebrew, a smaller slot: not restored, and named). A pick that comes back in view (the section reappears with it selected) gets no notice; picks that come back out of view, or several at once, get a short notice with **Undo**, persistent until dismissed |
| no reload warning | browsers show only a generic "changes may not be saved" on reload (custom text ignored), it is unreliable on mobile, and the app already uses it for pending autosave |
| one path, nothing hides | on open and on every change, a pick that does not apply goes through the same set-aside path and is named. Opening a character with old ghost picks counts as the moment they stopped applying: set aside, the cleaned character saves, a short notice names them. The build-time check is a backstop for the same path, never a silent filter |
| shared slots: newest first | a slot that loses room sets aside the most recently chosen pick. Stored order is click order (`event_handlers.cljc` `update-multi-select`, `conj`); nothing records which level granted a pick. To keep the other one the player just switches; no "Keep instead" action (round 3) |
| the marker | the word is **"Planned"** (round 3): "Planned: Steel Will (L7)", status first, then the name, then the level. A fourth state of the section header's counter (`remaining-component`); tapping it shows one line, "Steel Will returns at level 7.", with Clear. Its placement beside "complete", and the notice's, are OPEN: the owner wants a design pass on the header layout first (round 4) |
| copy | notices are glanceable: a few words, names not keys. **For testing** (owner, 2026-10-08): tapping the marker, one pick "Steel Will auto-fills at level 7.", several "Auto-fills later:" then "Steel Will · level 7" per line; the restore notice "Rogue picks restored (7) · Undo", then "Undone · Redo"; on opening an old character "3 choices don't apply right now · Show details", details listing each with "auto-fills at level 7", or "can't return" when its homebrew is not loaded. "Set aside" is the code's word for the hold, not player copy. Caveat: "auto-fills" reads as permanent while a reload forgets it |
| order of work | 1. the fix (picks stop applying, set-aside path) · 2. showing set-aside picks · 3. "Make starting class" |

Rounds 3 and 4 are the owner's answers on the mockup review page (2026-10-03/04), stored with the
page; the summaries above quote them.

**Dropped:** the repair-log attribute (`::char5e/repair-log`, below). It would store ghost data on
the character, which the owner ruled out: only the character the player decides on is saved.

**OPEN:** what "Make starting class" does when the class moved to second fails its multiclass
prerequisites (block, or allow with the builder's usual prerequisite warning).

*DESIGN — nothing built. Fixes the defect in [hidden-selection-picks.md](hidden-selection-picks.md).
Evidence measured 2026-09-14, `feature/grant-rows` at `ec04da1b`.*

## Decision

**A pick stored under a selection whose `prereq-fn` fails must stop applying, and the character
must say so.** Chosen over grandfathering by save version, and over reporting without fixing.

Rationale: the rule is the rule, and a character carrying a skill with no control to remove it is
worse than one that loses a skill and is told why. Existing characters are corrected on their next
build. The notice is what turns "where did my skill go" into an answer.

## The trace closes: there is no unknown family

**VERIFIED.** `skill-selection-2` and `tool-proficiency-selection-2` accept a caller-supplied
`prereq-fn`, and an earlier version of this doc left their callers as an unknown. Traced: of the
thirteen live call sites, exactly **two** pass one —

| caller | gate |
| --- | --- |
| `background-skills-cfg` (`:2855`) | held by another source |
| `class-skill-selection` (`:3264`) | `first-class?` / `(complement first-class?)` |

Every other caller passes the 2- or 3-arg form and is ungated. So the complete list of gated
selections is: class skills (2 arms), class tools (2 arms), class starting equipment (5), and the
background replacement (1). **Nine, all accounted for**, and only class tools is uncharacterized.

## The real diagnosis: the same rule, implemented twice, one of them wrong

The multiclass rule is enforced in two different places depending on whether the proficiency is
fixed or chosen — **in the same file**, for the same rule.

**Fixed** — the condition rides on the modifier and is re-evaluated on every build:

```clojure
(defn weapon-proficiency [key & [first-class? cls-kw]]
  (if first-class?
    (mods/set-mod ?weapon-profs key nil nil [(= cls-kw (first ?classes))])   ; ← here
    (mods/set-mod ?weapon-profs key)))
```

**Chosen** — the condition lives only on the selection, which is a display filter, and the
option's own modifiers carry nothing:

```clojure
(defn class-skill-selection [{…} key prereq-fn]
  (skill-selection skill-kws skill-num skill-select-order key prereq-fn))
;; → skill-selection-2 → :prereq-fn prereq-fn        (display only)
;;   options' modifiers: (modifiers/skill-proficiency skill-kw source)   ← no condition
```

**The fixed path is correct by construction. The chosen path relies on a filter that only the
builder runs.** That asymmetry *is* the defect — not a missing gate at the entity layer.

And the mechanism to fix it is already present and unused on this path: `skill-proficiency` takes
a `conditions` argument.

```clojure
(defmacro skill-proficiency [skill-kw & [source conditions]] …)
```

## Where the gate goes — REVISED

Put the condition on the chosen path's modifiers, exactly as the fixed path already does. Not a
filter at the entity layer.

```clojure
;; class-skill-selection, with the owning class threaded in
(modifiers/skill-proficiency skill-kw source [(= cls-kw (first ?classes))])
```

Why this over gating in `apply-options`:

| | condition on the modifier | filter in `apply-options` |
| --- | --- | --- |
| mechanism | already exists, already proven on the fixed path | new |
| hot path | untouched | a predicate over every option, every build |
| ordering | none — conditions are evaluated where every other condition is | needs the raw-vs-derived split below |
| lands | per site, incrementally, each with its own pinning test | all nine at once, one large behavioural change |
| explains itself | the condition is data the builder can also read, so the UI can say *why* | opaque |
| closes the general hole | **no** — a future selection with a `prereq-fn` and no condition still leaks | **yes** |

The last row is the real trade. The modifier fix is right for the nine that exist; the entity-layer
gate is what stops a tenth appearing. **Do the modifier fix now and keep the entity gate as the
backstop**, rather than treating them as alternatives.

### The efficiency this branch already buys

These selections are on the 35-row deletion table — they are being replaced by `:grants` anyway.
`compile-grants` is getting the owner threaded through it for the already-held resolution
([decision-already-held-resolution.md](decision-already-held-resolution.md)), and `:prereq-fn`
passthrough for the class multiclass rows. **A grant compiled with its owner's condition is
gated correctly by construction.**

So converting a site to a grant fixes it as a side effect, and the migration and the bug fix are
the same work. The sequencing follows:

1. **Class tools** — the one uncharacterized family. Pin it.
2. **The condition on chosen class skills** — the smallest real fix, proves the shape.
3. **Everything else as it migrates**, with the grant carrying the condition.
4. **The entity-layer gate last**, as the backstop for anything that never migrates, with the
   full nine-site characterization behind it.

Step 2 is worth doing standalone even though step 3 would subsume it: the migration is long, and
this is shipped behaviour people are hitting now.

## It is a filter for 7 of 9 sites — RETRACTED fixed-point claim

*An earlier version of this section said the gate was a two-pass fixed point that would double
`entity/build`. That was wrong, and the cost estimate with it.*

**The error:** `?classes` is written by a modifier —
`(mods/modifier ?classes (conj ?classes cls-key))` — so I concluded `first-class?` needs a built
character. The modifier *carries* the class order; it does not compute it.

**VERIFIED** (`multiclass_hidden_pick_test.clj`):

```
raw [fighter rogue]  ->  built ?classes [:fighter :rogue]
raw [rogue fighter]  ->  built ?classes [:rogue :fighter]
raw [rogue]          ->  built ?classes [:rogue]
```

Class order is exactly the raw `:class` vector order. So `first-class?` is answerable off the raw
entity with no build at all:

```clojure
(= class-kw (first (map ::entity/key (:class options))))
```

| gate | reads | needs a build? |
| --- | --- | --- |
| `first-class?` / `(complement …)` — both class skill arms, all five starting-equipment sites (**7**) | raw class order | **no** — a plain filter |
| `background-skills-cfg` replacement (**1**) | `?skill-profs`, real modifier output | **yes** |
| caller-supplied on `skill-selection-2`, `tool-*-selection-2` (**2**) | whatever the caller passes | unknown until each caller is checked |

**How it works today:** one build, then `get-all-selections-2` filters the selection list *for
display* against that built character. Nothing builds twice. That is precisely why the bug exists
— the filter is display-only and never feeds back into what gets applied.

**Consequence for the fix.** Seven sites need only a raw-data predicate applied before
`collect-modifiers-2`, which is cheap and has no ordering problem. The background site genuinely
needs the built character and is the only place where a second pass, or a narrower
`unless-held-by-other` condition on its own modifiers, is required. Scope it as *one filter plus
one special case*, not as a rebuild of the hot path.

## The mug does not cover this

**VERIFIED.** The homebrew override affects `count-remaining` (`entity.cljc:790`):

```clojure
homebrew? (get-in character [::homebrew-paths actual-path])
…
(cond homebrew? 0        ; waives min/max
```

It waives **how many** you may pick. `remove-disqualified-selections` takes only
`[selections built-char]` — no character, no homebrew paths — so the mug **cannot un-hide a
gated selection**, and after this fix a player who wants the fifth rogue skill has no route back.

**CORRECTED.** An earlier version said a player would have "no route back" after the fix. Wrong:
the *other* arm is still on screen. Hide the rogue's multiclass skill pick and the first-class
"choose 4" selection remains — mug that and take a fifth skill. Same for equipment: the surviving
class's starting-equipment selections are still there to over-pick.

What the fix removes is the specific stale pick, not the capability. So widening the mug to bypass
`prereq-fn` is **not** required to keep the pressure valve working, and the case for doing it is
much weaker than this doc first claimed. Left **OPEN** only as a question of whether re-picking
through the other arm is good enough UX.

## The paper trail: a new attribute, not an existing text field

The notice should be durable on the character, and **not** written into anything a player owns.

**VERIFIED from the schema** (`db/schema.clj`):

| field | history | verdict |
| --- | --- | --- |
| `::char5e/notes` | `:db/noHistory true` (`:234`) | **worst candidate** — an overwrite is unrecoverable, which is the same hazard [multi-tab-character-contamination.md](multi-tab-character-contamination.md) records |
| `::char5e/description` | declared **twice** — `fulltext-prop` (noHistory) at `:262` and `string-prop` (history kept) at `:278` | latent schema inconsistency; effective value needs checking against a live DB |
| `bonds`, `ideals`, `flaws`, `personality-trait-*` | plain `string-prop`, history kept | safe to write, wrong place — they are roleplay fields |

Appending to any of them means string concatenation into content someone is editing, which is
exactly the accident to avoid.

**DESIGN — use a new attribute instead:**

```clojure
{:db/ident ::char5e/repair-log
 :db/valueType :db.type/string
 :db/cardinality :db.cardinality/many}
```

`:db.cardinality/many` makes it **append-only by construction** — each entry is its own assertion,
so nothing can be overwritten and no concatenation logic has to be careful. History is kept
(no `:db/noHistory`), so the log is recoverable. One entry per repair:

```
"2026-09-14 — removed Athletics: granted by the rogue's multiclass skill choice, which no
 longer applies now that rogue is your first class."
```

**Surfacing — a transient notice is not enough.** An earlier draft said "wherever character
notices already appear", which means a toast the player can miss, and a paper trail nobody can
find is the same invisibility that made this defect hard to track down. The log needs a **durable,
readable place on the character** — its own section — with the load-time notice as a pointer to
it, not as the only sighting.

**OPEN:** whether the log is also shown on the PDF. Probably not by default — it is provenance,
not character content.

## Scope

Ordered, each step its own commit with a green gate.

1. **Characterize all nine sites.** Build a character exercising each `prereq-fn`, pin what
   applies today. The multiclass and background cases exist
   (`multiclass_hidden_pick_test.clj`, `conditional_selection_ref_test.clj`); the other seven do
   not. **Nothing changes until this is green** — it is what proves the fix does not take away
   something it should not.
2. **Decide the mug question.** It changes what the fix means for a player and should not be
   retrofitted.
3. **The gate itself**, in `apply-options`, two-pass, with the convergence check. Measure
   `entity/build` before and after against `entity_build_perf_test`.
4. **The repair log attribute** plus a migration, and the write from the gate.
5. **Surface it** — a notice on load for affected characters, reading from the log.

Steps 1 and 2 gate everything else. Step 3 without step 1 is a silent change to every saved
character in the app.

## Related

- [hidden-selection-picks.md](hidden-selection-picks.md) — the defect, measured, with the
  multiclass reproduction
- [decision-already-held-resolution.md](decision-already-held-resolution.md) — the design that
  would multiply conditional selections from nine sites to every grant, which is why this is
  urgent rather than tidy

## Corrections

- **2026-10-03:** "The paper trail" (a durable `::char5e/repair-log` on the character) and Scope
  steps 4–5 are superseded by the current decision at the top: set-aside picks are held in memory
  and never saved, and the record is the on-open notice. Scope step 3's gate stays, as the
  backstop of the set-aside path rather than a silent filter.

