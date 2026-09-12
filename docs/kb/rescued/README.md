# Rescued docs — knowledge pulled off branches before they are deleted

Every file here was copied **verbatim** from a branch that is dead, merged, or slated for pruning,
because it existed on no other branch in the repo. Each carries a banner naming its source ref, tip
and blob, so the copy can be checked against the original for as long as the branch survives:

```bash
diff <(tail -n +8 docs/kb/rescued/<file>.md) <(git show <source-ref>:<source-path>)
```

## What this directory is not

**These are not KB docs.** A KB doc is current, verified, and maintained in place
([documentation-discipline.md](../documentation-discipline.md)). These are snapshots of branch state
written by sessions that had a working tree we no longer have. They carry stale status lines
("not yet merged", "awaiting e2e verification"), `file:line` references against their own branch,
and links to files that do not exist here.

They are here so the knowledge is not lost when the branches go — **not** because they have been
accepted into the knowledge base. Promoting one means reading it, verifying its claims against the
current tree, rewriting it as a KB doc, indexing it in [../README.md](../README.md), and deleting
the copy here. Until then, treat anything in this directory as a lead, not a fact.

Because the content is archival, `scripts/check-docs.sh` skips this directory in its dangling-link
check — the links point at branch state on purpose. The orphan check still applies, so every file
below stays listed.

## Manifest

Triage detail and the evidence for each verdict:
[../unsaved-knowledge-on-prunable-branches.md](../unsaved-knowledge-on-prunable-branches.md).

### Promote — a finding, verified on its branch, absent from every KB

| file | lines | from | what it holds |
|---|---|---|---|
| [datomic-crash-analysis.md](datomic-crash-analysis.md) | 204 | `042fd072^` (pre-deletion state; identical blob on 30 branches) | Deleted by the 2026-07-04 commit that said the KB "belongs on the `agents/develop` silo". It never arrived. Restoring it is the one item here that was already decided |
| [single-colon-keyword.md](single-colon-keyword.md) | 196 | `claude/fix-single-colon-keyword` `bab40288` | `name-to-kw-aux` returns the empty keyword for `""`/apostrophe-only names; one bare `:` makes the whole EDN read throw and the page die. REPL-verified, affected character identified. **The fix shipped** — `sanitize-edn-colons`, `common.cljc:16` on `integration` — **and no KB doc mentions it.** Code whose reasoning was never written down |
| [character-notes-merge-investigation.md](character-notes-merge-investigation.md) | 230 | `claude/fix-character-notes-merge-4YNzf` `702fffc7` | Multi-tab contamination of the floating `:character` slot: a reload restores it from `localStorage["character"]`, which holds whichever character was saved last *across tabs*, so edits land on the wrong entity. Explains a real data-loss report. "multi-tab" appears in no KB doc |
| [custom-items-investigation.md](custom-items-investigation.md) | 1074 | `claude/fix-custom-items-disappearing-DW8rb` `509431f2` | Root cause for Orcpub#669 — `filtered-items`/`filtered-spells` were not reactive. Five ranked patches with commit-level provenance. "filtered-items" appears in no KB doc |
| [export-unification-handoff.md](export-unification-handoff.md) | 142 | `claude/fix-brave-export-bug-2Tt7j` `3a1f5bfb` | The "Brave export bug" was browser-agnostic: a stringification regression at `events.cljs:560`/`:675`. Records the wrong turn (browser-specific) that someone will otherwise re-take |

### Promote in part — most of it duplicates an existing KB doc

| file | lines | from | lift only |
|---|---|---|---|
| [feature-tab-black-screen.md](feature-tab-black-screen.md) | 286 | `claude/character-black-screen-feature-i8lvk3` `9869d07a` | **§6 only.** §1–§5 duplicate [../fail-soft-rendering.md](../fail-soft-rendering.md). §6 is a reusable diagnostic playbook: monkeypatch `Array.prototype.sort` to capture the throwing comparator's operands, decode cljs maps from JS (`.arr`/`.cnt`, keyword `.fqn`), pickaxe a shallow clone when blame dead-ends at a re-import |
| [key-vs-name-separation.md](key-vs-name-separation.md) | 87 | `claude/fix-cantrips-selection-bug-CSwVv` `edf9655b` | The stated rule (`:key` is identity, `:name` is display), the four-site leak table, and the design call that parked orphans are re-derived per load rather than persisted. The case study and the benign-vs-leak heuristic already live in [../spell-selection-source-fix.md](../spell-selection-source-fix.md) and [../name-to-kw-audit.md](../name-to-kw-audit.md) |
| [half-caster-prepared-spells-handoff.md](half-caster-prepared-spells-handoff.md) | 519 | `claude/half-caster-prepared-spells-Jo0ai` `ed5e13da` | The plan, as a plan doc. [../homebrew-class-spellcasting.md](../homebrew-class-spellcasting.md) documents the constraint (`:prepares-spells?` is hardcoded per `known-mode`); nothing documents the intent to lift it. Its own header says the source plan lived in session scratch and is already gone |

### Keep pending a decision — not findings

| file | lines | from | why it is here anyway |
|---|---|---|---|
| [unblock-features-tab.html](unblock-features-tab.html) | 45 | `claude/character-black-screen-feature-i8lvk3` `9869d07a` | Not prose — a drag-to-install bookmarklet that lets a stuck user open their Features tab before a fix deploys. Promote to `docs/tools/` or drop deliberately |
| [configuration-pattern.md](configuration-pattern.md) | 153 | `claude/cloud-drive-integration-SC31k` `07f086c6` | Says `profiles.clj` is the primary local-dev env-var mechanism and warns against wrapper namespaces around `environ/env`. `profiles.clj` appears in no KB doc, but [../env-and-auth.md](../env-and-auth.md) and [../dev-tooling-decisions.md](../dev-tooling-decisions.md) cover the surrounding ground |
| [ui-ux-evaluation.md](ui-ux-evaluation.md) | 178 | `claude/ui-ux-evaluation-Kngfm` `36354fcc` | A 2026-04 survey describing `styles/core.clj` at ~1600 lines. `refactor/garden-inline-styles` and `redesign/growable-option-menus` have since reworked that surface, so the measurements are stale |
| [ui-ux-plan.md](ui-ux-plan.md) | 273 | `claude/ui-ux-evaluation-Kngfm` `36354fcc` | The plan built on that survey. Same staleness caveat |

## Rescued, deliberately, without judging the content

The last four are here because rescuing is reversible and deleting a branch is not. They were copied
on that basis alone, not because they were found to be worth keeping. If a read says otherwise,
delete the file — that is the intended outcome for at least some of them.

## What is NOT here

- **Superseded corpora.** ~6,944 lines of fighting-style analysis
  (`claude/explore-fighting-styles-K56lQ`) and 6,115 lines of class-creation design
  (`claude/upgrade-class-creation-zYV46`) lose to KB docs that already exist and are better sourced —
  `fighting-style-vocabulary-gap.md` and `class-feature-catalogue.md` respectively.
- **The 53 KB docs on non-`claude` branches** that `agents/develop` lacks. Those branches are live,
  not prunable, so nothing is at risk today. That gap is a separate decision — see the triage doc.
- **Anything duplicated elsewhere.** 180 of the 217 doc files across the `claude/*` branches are
  byte-identical to a copy on another branch.
