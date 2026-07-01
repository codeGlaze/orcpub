# Data-safety layers — prevent / harden / heal / surface

How to make a content feature robust against bad data (legacy, hand-edited, ghost formats, junk).
Distilled from a cross-branch design review of the toggle-nil / map-collapse defect that both
`claude/zen-wright-04xhdz` and `claude/custom-class-source-error-2k5ykd` hit from opposite ends.

**"Hardening vs self-healing" is the wrong axis.** They're two of FOUR layers, and a robust design
usually uses several — chosen by *where you are in the data's life and whose data it is*, not picked
once globally.

## The four layers (preference order)

1. **Prevent** — *shape / type.* Push the invariant into the field type or storage shape so the bad
   state can't be authored. Absent = off; pick boolean-vs-set-member-vs-map-key deliberately; a field
   type carries its own read-coercion + write-coercion + validation so no caller can reintroduce the
   bug. The cheapest bug is the one that can't be created. Do this first.
2. **Harden** — *boundary / read.* The non-negotiable floor: no input may crash or corrupt. Defensive
   reads, skip-uncompilable, fan-out safety. Always present.
3. **Heal** — *write / repair.* ONLY when there's an unambiguous correct target AND the data is worth
   preserving. Repair lazily on read/interaction — no load-time migration pass.
4. **Surface** — *migration.* When you can't heal but the data MIGHT be meaningful: park it named /
   recoverable and tell the user. Silent drop is allowed only for provably-meaningless junk.

## The rule that picks between them

| Situation | Layer |
|---|---|
| Fresh input from the current UI | **Prevent** — make the malformed state unauthorable |
| Real/legacy data with a known-correct shape | **Heal** — you must accept it (save ⊆ load), so repair it forward |
| Genuinely un-compilable garbage (no principled repair) | **Harden** — skip; healing would be *guessing* |
| Anything a human might have meant, that you'd otherwise drop | **Surface** — never a quiet skip |

The clarifying frame is **lifecycle**: prevent at authoring, harden at read, heal at write, surface at
migration. Two cross-cutting invariants sit under all of it:
- **save ⊆ load** — anything that SAVES must LOAD; load is never stricter than save. This is what makes
  healing possible at all (you can't repair data forward if load already rejected it). Never "tighten
  validation on import."
- **Rejections say what and where** — carry a precise `:in` path into a human message. A filled-in
  value that won't save, with no reason, is worse than a blank one.

## Worked examples (this codebase)

| Mechanism | Layer | Why |
|---|---|---|
| `bf/*` `:boolean` field type (planned, one primitive) | **Prevent** | invariant lives in the type; no caller can reintroduce nil |
| `opt5e/pool-entry?` (skip junk spread/save entries) | **Harden** | `[:bad]`/`[]` have no canonical value to heal to |
| `opt5e/toggle-increment-save` (rebuild increment canonically) | **Heal** | a malformed increment has ONE obvious right shape |
| `common/toggle-in` (collapsed `false` → fresh map) *(other branch)* | **Heal** | live user data with a known repair |
| `(not (true? v))` defensive read | **Harden** | garbage/nil/absent → off |
| `save ⊆ load` generative guard *(other branch)* | invariant | enables all healing |

## Anti-patterns

- **Harden-by-silent-drop on meaningful data.** Skipping is for garbage; meaningful data that can't be
  handled must be *surfaced*, not quietly dropped. (Our current gap — see below.)
- **Heal speculatively.** Self-healing is more code, more test surface, and a subtle risk: a *wrong*
  repair corrupts in a new way. If the correct target isn't unambiguous, harden or surface — don't
  guess. Over-healing is guessing dressed up as robustness.

## Tracked follow-ups

- **`pool-entry?` should surface, not silently skip (harden → surface).** `compile-ability-increases`
  / `compile-save-proficiencies` drop malformed `[amount pool]` / `[count pool]` entries silently for
  fan-out crash-safety (correct — one bad entry can't crash the pack). But a *creator* authoring in the
  builder gets no signal that an entry was ignored. Climb it from harden to surface: have the authoring
  form (`save-coverage-notes` is the natural home) report "N ability-increase / save entries were
  ignored as malformed", ideally naming the index. Runtime stays silent-skip (no per-entry UI in a
  sub's fan-out); the *authoring* path is where a human sees it. Guardrail 6.

## See also
- `ability-increase-spreads.md` — the feature these examples come from (compile crash-safety section).
- `builder_fields.cljc` — the convergence note for the shared `:boolean` toggle primitive.
- `content-extensibility-decisions.md` — D33 (terse export data), D34 (deprecate-not-delete).
