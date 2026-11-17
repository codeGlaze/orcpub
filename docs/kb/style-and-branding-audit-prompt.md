# Style and branding audit: the start prompt

The first message for a fresh agent that audits the stylesheets and the branding layer and plans
their cleanup (owner, 2026-11-16: near-duplicate styles and a messy branding layer had piled up
despite the rules). Paste the block below into a new session. It is audit-only on purpose: a
cleanup that starts before the inventory is how things went off the rails.

Update the scope lines when the files move (for example, when an open PR it names has merged).

```text
Read START-HERE.md on agents/develop and follow it. Then do the job below. It is an AUDIT AND A
PLAN, not a cleanup: change no app code, push no code branch, create no branch.

## The job

The owner keeps finding near-duplicate styles (classes that differ by a colour, a padding or a
radius) and a branding layer that is a mess, despite rules meant to prevent both. Produce:
1. An inventory of what has accumulated, with evidence.
2. A roadmap for cleaning it up, in small, separately mergeable steps.
Both go in docs/kb/ on agents/develop, linked from docs/kb/README.md.

## Scope

A. Styles: src/clj/orcpub/styles/core.clj (~4,250 lines), portrait.clj, weapons.clj (Garden),
   resources/public/css/cookiestyles.css, and inline :style maps in src/cljs and web/cljs.
B. Branding: src/clj/orcpub/fork/branding.clj, src/cljs/orcpub/fork/branding.cljs,
   test/clj/orcpub/branding_guard_test.clj, docs/BRANDING-AND-INTEGRATIONS.md, and every caller
   of these. What a fork (the DMV deployment) is meant to override, and what actually leaks
   around that layer (hardcoded names, logos, colours, URLs outside it).
C. Only what A and B touch. Do not audit the whole codebase.

## Read first

docs/design/style-guide.md, docs/kb/ux-guide.md, docs/kb/garden-inline-styles-harvest.md,
docs/kb/monolith-decomposition-plan.md, docs/kb/branding-setup.md, docs/kb/code-comment-style.md,
and AGENTS.md "Shrinking the big files" and "Code". The style guide's reuse tenet: new styles
are allowed, but only on purpose, after checking what exists.

## What to find (each finding: file:line, the compiled CSS or a computed style as evidence, where it is used)

- Near-duplicate classes, grouped by ROLE (pill/chip/badge, button, link, toggle/segmented
  control, callout, tile, input ring, focus ring), not by name. For each group: the property
  differences in a table, and which screens use each member.
- Hardcoded values that duplicate a def (colours like "#f0a100" beside `orange`, the same rgba
  written several ways, magic spacing and font sizes).
- Light-theme overrides: where `.app.light-theme` repeats a rule instead of a token doing the
  work. docs/THEMING.md describes an unmerged theming branch; say how a cleanup stays compatible
  with it without depending on it.
- Dead styles: classes no cljs/cljc emits. Garden classes are often built from keywords
  (`:div.kit-row`), so search the keyword forms too.
- Inline :style maps that duplicate an existing class.
- Branding: what is a fork override and what is a default; what bypasses the layer; what the
  guard test actually guards and what it misses; where the docs and the code disagree.

## How to measure: ground truth, not grep

- Grep and scripts LOCATE; the compiled output CONCLUDES. Build the stylesheet once
  (`lein garden once` -> resources/public/css/compiled/styles.css) and compare rules there.
  An earlier audit gave four confident wrong answers from grep alone.
- Use scripts/clj-grep.py and scripts/clj-liveness.py (agents/develop): they separate `#_`
  discarded code from live code; plain grep cannot.
- For "minor differences" between screens, measure computed styles in a real browser
  (Playwright, which scripts/e2e/run.sh boots) at E2E_PORT=8893. Port 8890 is the owner's live
  site: never use it or restart it.
- No screenshots unless a finding cannot be shown as numbers.

## The roadmap

- Order it by risk and by what unblocks what.
- Each step: one concept family (for example "pills and chips -> one .chip with variants"),
  the branch name it would need (ask the owner before any branch exists), the files and callers
  it touches, how to prove no visual change (before/after computed styles on named screens,
  both themes, phone and desktop), and its rough size.
- Follow "Shrinking the big files": a small move is one commit with no behaviour change; a big
  one gets its own branch, asked first. Don't plan moves in files an open PR is changing (check
  the open PRs first).
- Say which steps need an owner decision (picking the one survivor of a duplicate group is a
  design choice) and list those as questions.

## Rules for this run

- AGENTS.md applies in full. Every working response starts with stop rules. Fetch before
  reading any origin/* ref.
- Subagents: one per scope area (styles, inline styles, branding), model sonnet, each with a
  tool-call ceiling of 60, writing its raw findings to a file in your scratchpad and returning a
  summary of 30 lines or fewer. Check their claims against the compiled CSS before writing
  them down.
- Budget: stop and report after the inventory if it runs past about 150 tool calls in total;
  the roadmap can be a second session.
- Write findings as you go: a run cut short must leave its findings on disk.
- No app code changes, not even "obvious" one-line fixes; note them in the inventory instead.

## Report back

3-6 lines of outcome, then Needs you: the KB page names and agents/develop commit hashes, the
top five duplicate groups by how many screens they touch, the branding leaks, and the decisions
the owner must make before step 1.
```
