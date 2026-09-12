# Rescued docs — the holding pen

Docs copied verbatim off branches that are dead, merged, or slated for pruning, because they existed
on no other branch. Each carries a banner naming its source ref, tip and blob, so the copy stays
checkable against the original for as long as the branch survives:

```bash
diff <(tail -n +8 docs/kb/rescued/<file>.md) <(git show <source-ref>:<source-path>)
```

**These are not KB docs.** They are snapshots of branch state, with stale status lines and
`file:line` references against their own branch. They live here until someone reads them against the
current tree and either promotes the content or deletes the file. Treat anything here as a lead, not
a fact.

`scripts/check-docs.sh` skips this directory in its dangling-link check — the links point at branch
state on purpose. The orphan check still applies, so every file stays listed below.

## Still here

| file | lines | from | why it is still waiting |
|---|---|---|---|
| [half-caster-prepared-spells-handoff.md](half-caster-prepared-spells-handoff.md) | 519 | `claude/half-caster-prepared-spells-Jo0ai` `ed5e13da` | **The constraint is confirmed live.** `options.cljc:3113` still gates prepared casting on `(:prepares-spells? spellcasting)`, and the class builder hardcodes `:known-mode :schedule` with no control for it (`views.cljs:6786`) — so a homebrew half-caster that prepares (a Paladin analogue) still cannot be authored. [homebrew-class-spellcasting.md](../homebrew-class-spellcasting.md) documents the constraint; nothing documents the intent to lift it. Needs distilling into a plan doc by someone who will own the work — 519 lines of session handoff is not a plan |
| [ui-ux-evaluation.md](ui-ux-evaluation.md) | 178 | `claude/ui-ux-evaluation-Kngfm` `36354fcc` | A 2026-04 survey measuring `styles/core.clj` at ~1600 lines. `refactor/garden-inline-styles` and `redesign/growable-option-menus` have reworked that surface since. The measurements are stale; whether the *observations* still hold is a design judgement, not a code check |
| [ui-ux-plan.md](ui-ux-plan.md) | 273 | `claude/ui-ux-evaluation-Kngfm` `36354fcc` | The plan built on that survey. Same caveat. Keep only if the plan is still wanted |

## Processed — 2026-09-12

Nine of the original twelve were read against `integration` `36766010` and resolved.

### Promoted

| was | now |
|---|---|
| `datomic-crash-analysis.md` | [../datomic-crash-analysis.md](../datomic-crash-analysis.md) — restored to the KB with a provenance and currency header. Analysed on Datomic Free; the tree pins Pro `1.0.7482` (`project.clj:82`) but `docker-compose.yaml:52` still defaults to `datomic:dev://` (H2-backed), so the `writeConcurrency`/heartbeat findings still apply. Correctly speculation-flagged already |
| `custom-items-investigation.md` | [../filtered-list-staleness.md](../filtered-list-staleness.md) — **the bug is live.** `events.cljs:3004-3007` stores a filter result computed from the item list as it was at that keystroke; `subs.cljs:1053-1058` reads that stored value and short-circuits past its own `::char5e/sorted-items` signal. Rewritten as a KB doc; the 1,074-line branch log was raw material |
| `character-notes-merge-investigation.md` | [../multi-tab-character-contamination.md](../multi-tab-character-contamination.md) — **the defect is live.** `db.cljs:34` keys the character draft as `"character"` with no id; two tabs share the slot. `schema.clj:182-185` and `:234-237` still hold exactly as the original claimed |
| `feature-tab-black-screen.md` §6 | [../fail-soft-rendering.md](../fail-soft-rendering.md) § *Diagnosing a new occurrence* — the comparator-wrapping technique, reading cljs values from JS, and pickaxing a shallow clone. §1–§5 duplicated that doc and were dropped with the file |

### Dropped, with the reason

| file | why |
|---|---|
| `single-colon-keyword.md` | Both halves of the fix shipped — prevention at `common.cljc:65`, self-heal at `common.cljc:16` — and `http_safe.cljs`'s ns docstring explains the detonation path. **The first verdict here was "nothing durable was left to lift"; that was wrong.** A docstring is only reachable by someone already in the file, and nothing pointed there from the KB, so the knowledge was unfindable rather than recorded. Superseded by [../empty-keyword-corruption.md](../empty-keyword-corruption.md), which is the short version worth keeping |
| `key-vs-name-separation.md` | Fully covered by [../name-to-kw-audit.md](../name-to-kw-audit.md) (all four "leak sites" are already inventoried at `:158`, `:160`, `:170` and §7.1–7.2, and `:457` states the rule) and [../spell-selection-source-fix.md](../spell-selection-source-fix.md). Its one genuinely unique claim — that the reconciler returns `{:rewrote […] :parked […]}` — **is false**: `content_reconciliation.cljs` returns only `:rewrote`, and [../duplicate-key-durability-roadmap.md](../duplicate-key-durability-roadmap.md) already carries a section titled "`:parked` never existed" |
| `configuration-pattern.md` | **Stale, not wrong — and the first verdict here overstated it.** It was written the same day as `ccbdacba` (2026-01-15, "Document local dev (profiles.clj) as primary, Docker as secondary"), so it was accurate when written. `profiles.clj` is **gitignored** (`.gitignore:35`), which is why a repo-wide grep found nothing — absence of hits was not evidence it was unused, and claiming it "appears nowhere in the repo" was a mistake. Leiningen still reads it. The current loading paths are documented in [../env-and-auth.md](../env-and-auth.md), which has gained a `profiles.clj` entry rather than resurrecting this doc |
| `unblock-features-tab.html` | The bookmarklet unblocked a user stuck behind the black screen *before a fix deployed*. The fix deployed — `render-guard` (`views.cljs:1872`), `error-boundary` (`:3948`), `character-health-warning` (`:4034`) are all live on `integration`. An obsolete workaround |
| `export-unification-handoff.md` | Its bug 1 was already fixed on the branch before that work started. Its bug 2 — asymmetric validation across four parallel export paths — is resolved structurally: every export now goes through one `save-orcbrew-blob!` → `serialize-orcbrew` (`events.cljs:4413-4431`), and the one remaining unvalidated path is deliberate and documented in place as a WIP rescue hatch |

## What was never rescued

Superseded corpora (~13,000 lines) that lose to KB docs which already exist, and the 53 KB docs on
live non-`claude` branches that `agents/develop` lacks. See
[../unsaved-knowledge-on-prunable-branches.md](../unsaved-knowledge-on-prunable-branches.md).
