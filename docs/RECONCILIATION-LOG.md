# Reconciliation Log — cantrips/spell-selection work across machines

> Working record of where the cantrips-selection-bug solution lives, what was
> decided, and what's still loose. Started 2026-05-31. Append as new complicating
> factors get layered in. This file is meta/scratch — not part of the solution
> itself; move to `docs/` or gitignore if it shouldn't ride along on the branch.

---

## Why this file exists

The solution to the cantrip/spell-selection regression evolved across **three
places**, and work + reasoning got split across **two separate Claude
conversations**. This log keeps the threads straight while we consolidate onto
one development machine and layer in additional factors.

## The three places work has lived

| # | Environment | Role | Status |
|---|-------------|------|--------|
| 1 | **GitHub Codespace** `datomic-pro-upgrade-pjrvpvv9r6wf79xj` (display "Datomic Pro Upgrade", repo `codeGlaze/orcpub`) | Where phase 1 + the HANDOFF were authored (Claude Code in the codespace) | **Shutdown**; last used 2026-01-06 per `gh`, but branch/transcript activity through 2026-05-14 |
| 2 | **Claude Code web** (claude.ai/code) conversation | Picked up the handoff and implemented phase 2, pushing to the **same** remote branch | History lives on claude.ai (not pulled locally) |
| 3 | **This local machine** (WSL2, `DESKTOP-3DTN2QS`, `/home/codeglaze/projects/orcpub`) | Intended **home base** for development going forward | Active |

Branch in play everywhere: **`claude/fix-cantrips-selection-bug-CSwVv`**.

## Access setup (how we reach the codespace from local)

- `gh` CLI installed locally and authenticated as **codeGlaze** over HTTPS
  (scopes include `codespace`, `repo`, `workflow`, `read:org`, `gist`).
- Reach the codespace with:
  `gh codespace ssh -c datomic-pro-upgrade-pjrvpvv9r6wf79xj -- '<cmd>'`
  (auto-starts the VM, which resumes billing).
- Still missing locally: `jq`, `python3` (apt, needs sudo password — not yet
  installed). `claude` CLI is **not** on the local shell PATH; this machine runs
  Claude Code via the VSCode extension.
- Codespace layout: `~/.claude` → `/workspaces/orcpub/.claude-data`; repo at
  `/workspaces/orcpub`; Claude chat transcripts under
  `~/.claude/projects/-workspaces-orcpub/` (~227 MB; newest session
  `5ca42df9…`, May 14, 0.6 MB; one 134 MB monster from Mar 13).

## KEY FINDING: the code is NOT divergent — it's one continuous line

`origin/claude/fix-cantrips-selection-bug-CSwVv`, 7 commits ahead of `develop`
(merge-base = `d42e05d1`, current develop tip). All authored by "Claude":

```
0a4f262d  phase 1 fix + source-suffix toggle ... codespace  2026-05-02
a77d0a18  kb doc (design rule, 4 leak sites) .... codespace  2026-05-04
46310f34  source comments → kb .................. codespace  2026-05-04
af5e6feb  debug log on :rewrote ................. codespace  2026-05-04
dd4144d7  HANDOFF.md ............................ codespace  2026-05-14  ← codespace stopped here
251e1a76  phase 2: derive kw from class-key ..... web        2026-05-14  ← web picked up handoff
1c24a8e0  dedupe base-class-keys (classes.cljc) . web        2026-05-14  ← REMOTE TIP
```

- Codespace **local** branch HEAD = `dd4144d7` (the HANDOFF commit),
  **2 behind / 0 ahead** of the remote. The 2 it lacks (`251e1a76`, `1c24a8e0`)
  are the **web conversation's** phase-2 work.
- Conclusion: codespace handed off cleanly via `HANDOFF.md`; web implemented
  phase 2 on the same branch. Remote is strictly ahead (fast-forward).
  **No conflicting decisions in committed code.** Nothing to merge.

## The ONE stranded artifact in the codespace

- `stash@{0}: On claude/fix-cantrips-selection-bug-CSwVv: wip: test_runner wire-in`
- Content: 6-line edit to `test/cljs/orcpub/test_runner.cljs` that registers
  `orcpub.dnd.e5.content-reconciliation-test` in the require list and the
  `run-tests` call.
- **Verified NOT present on the remote tip.** So the phase-1 reconciler tests
  exist as a file but are not actually wired into the runner anywhere committed.
- **Action pending:** rescue this stash → apply locally → commit. (Approved in
  principle; not yet executed.)

## Other unpushed work stranded in the codespace (NOT cantrips-related)

Flagged so it isn't lost if the codespace is deleted. Separate inventory still
pending:
- Unpushed local commits on other branches incl. `dmv/hotfix-integrations`
  (fork branding/integrations, events.cljs split/refactors, PDFBox 3.0.6 fixes,
  `.env.*` gitignore consolidation, menu-flyout merge…).
- `refactor/garden-inline-styles` work.
- `upgrade/datomic-pro` work.
- Additional stashes:
  - `stash@{1}` WIP on `refactor/garden-inline-styles` (inline-styles conversion)
  - `stash@{2}` WIP on `dmv/hotfix-integrations` (extract SCRIPT_NAME var)
  - `stash@{3}` `dmv wip before branch switch`
  - `stash@{4}` `pre-merge: docs` on `upgrade/datomic-pro`

## The solution's design decisions (distilled from HANDOFF.md on the branch)

Full detail in `HANDOFF.md` (committed at branch root) and
`docs/kb/key-vs-name-separation.md`.

- **The bug:** homebrew Cleric/Druid replacements lost cantrip/spell selections
  after a UX change. Root cause: the `::classes5e/plugin-classes` sub mutated
  class `:name` to `"Cleric (Source)"` for display; downstream consumers
  re-derived identity via `common/name-to-kw` from the mutated `:name`, so saved
  characters keyed on canonical `:cleric-cantrips-known` got orphaned.
- **Phase 1 (shipped):** reverted the `:name` mutation; plumbed `:plugin-source`
  as a distinct `::plugin-source` slot; added display-only
  `::show-class-source-suffix` toggle; added load-time
  `reconcile-spell-selection-keys` in `content_reconciliation.cljs` (auto-rebind
  unambiguous orphans), wired into `:set-character`; added tests.
- **Architectural pivot (the core decision):** identity must flow from
  **`class-key`, never from `:name`**. `:name` is mutable display; nothing
  identity-bearing may depend on it outside the editor. Chose *architecture over
  convention* — phase 1's "don't mutate `:name`" was only a convention.
- **Phase 2 (shipped by web):** switch kw derivation to `class-key` at the
  terminal `name-to-kw` sites: `options.cljc:469` (spell-selection),
  `template_base.cljc:275` (`?prepare-spell-count`), `options.cljc:635`
  (`class-key-name` fallback). Leave editor save path `events.cljs:544` and the
  template `option-cfg`/`selection-cfg` constructors alone.
- **Phase 3 (designed, NOT built):** import-rename hardening — on import
  conflict, compute a source abbreviation (1–3 words → first+last letter each,
  e.g. `KsTy`; 4+ words → initials, e.g. `TCoE`; numeric tie-break on collision)
  and append it to **both** `:key` and `:name` so editor re-save (which
  regenerates `:key` from `:name`) preserves disambiguation. Override = user
  deletes `(KT)` from `:name`; reconciler rebinds orphan on next load.
  Open question: whether to **store** the abbreviation field (user undecided;
  default = don't store unless the collision-resolution UI gets built).
- **Reconciler updates (designed, NOT built):** compute `class->expected-spell-keys`
  from `class-key`; **drop** the `:parked` accumulator (proven unreachable /
  vapor); **add** `:unbound-classes` accumulator + subclass-mismatch detection;
  new return shape `{:character … :rewrote […] :unbound-classes […]}`. Feed the
  reconciler the same `::classes5e/classes` aggregation the dropdown uses
  (built-ins ∪ plugins) rather than a new helper.
- **Relink UI (designed, NOT built):** inline on the character builder, modeled
  on the missing-content banner at `character_builder.cljs:1940-1972`. Sections
  for unbound classes and subclass mismatch; pick → rebind `::entity/key` /
  `:class` then re-dispatch `:set-character`.

## Plan to make this machine home base (approved in principle, pending execution)

1. Check out `claude/fix-cantrips-selection-bug-CSwVv` locally (already fetched;
   remote tip = web's latest = everything).
2. Rescue `stash@{0}` from the codespace → apply here → commit the test wire-in.
3. Develop here going forward; `HANDOFF.md` carries rationale + phase 3 runway.
4. (Optional) pull codespace Claude transcript into local resume picker — likely
   unnecessary given HANDOFF.md.

## Open questions / things to resolve

- [ ] Execute steps 1–2 above (checkout + stash rescue + commit).
- [ ] Decide fate of the codespace (keep alive vs. delete) — gated on rescuing
      the unrelated unpushed work listed above.
- [ ] The "other complicating factors" the user is about to layer in — capture
      here as they arrive.
- [ ] Phase 3 + reconciler updates + relink UI are unbuilt — the real remaining
      engineering work.
- [ ] Whether to store the source-abbreviation field (phase 3) — still undecided.

---
_Last updated: 2026-05-31._
