# Documentation discipline

The KB is the point. Code and tests say what the system does now; the KB says what was
**learned** — what was traced, what was measured, what turned out to be false, and what was
decided. That is the part that cannot be recovered by reading the diff.

Referenced by `.claude/hooks/README.md` and `BRANCH.md`; enforced where it can be by
`scripts/check-docs.sh`.

## What earns a doc

Something a competent person would get wrong without it, that is not obvious from the code:

- **a traced behaviour** — what a function actually does, established by running it
- **a measured number** — a benchmark, a coverage count, a before/after
- **a bug and its cause** — especially a shipped one, and especially how it was found
- **a decision with a rejected alternative.** The alternative is the valuable half
- **a constraint invisible at the call site** — a licensing boundary, a type mismatch across
  two files, an API that blocks
- **a reversal**

Not: what the code already says, a change the diff explains, or a narrative of the session. A
typo, a rename, a test-only tweak needs nothing.

## Verify, don't remember


**Every factual claim in a doc is checked against the code at the time of writing.** Not
recalled, not inferred from a filename. The failure mode is confident and specific and wrong,
which is worse than vague.

Two rules that catch most of it:

- **Line references rot.** Pin them: *"references are against `integration` at `432cf375`"*.
  Branches diverge — `filter-monsters` sat at `:1304` on one branch and `:1147` on another in
  the same week.
- **`#_` is not a comment, it discards the next form.** A grep hit inside one looks live.
  `classes.cljc` carries 160 discarded hits for `:name "` against 346 live. Use
  `scripts/clj-grep.py`, which separates them. **Never write "live" or "discarded" in a doc
  without running it** — reading nearby lines is not enough, because the `#_` that kills a
  form can open dozens of lines above it. The Noble's Retainers trait was called live in the
  companions plan on exactly that error, by the agent who had written the tool that hour.

## Claims must be proven, not asserted


The recurring failure in this repo is a confident claim built on a partial read: a `tag->flag` map
built from a grep that missed half the fields; "this part is irreducible" repeated from a table
until someone checked; a benchmark conclusion drawn from operation counts rather than time. D22 in
`content-extensibility-decisions.md` records the general rule — *treat "this is irreducible" as a
claim to be proven against the code, not asserted* — and it has been violated more than once since
being written.

Related: `verification-discipline.md` (how to check a thing without fooling yourself).

## Update in place; record reversals separately

A doc is the current state of knowledge, not a log of how it was reached. Two failure modes
this KB has actually hit:

1. **Appending a second account** of the same subject instead of updating the first, leaving
   two docs that disagree. Edit the claim.
2. **Silently overwriting a superseded conclusion.** A section arguing a limitation was
   unavoidable was replaced wholesale by the section saying it was not, so the reasoning
   behind the original call became unrecoverable.

When a conclusion is overturned the superseded reasoning stays visible — someone will
otherwise re-derive it and reach the same wrong answer. Current truth at the TOP; what was
believed and what reversed it in a `## Revisions`, `## Corrections` or `## History` section at
the tail. Correct the body; explain in the ledger. Both, not either. See
`armor-class-refactor.md`, which carries both.

## Structure: current truth first, audit trail last


Agents (and people) read the top of a long file and may never reach the bottom. So:

- **current state** first — what is true now, in a form someone can act on
- **the plan / what is open** next
- **`# History`** last — a dated ledger of what landed, and a `## Corrections` list of what was
  believed and what reversed it

Stale content at the top is the most expensive kind.

## Docstrings are SPEC, not prose


What it does, its args, what it returns — and at most a one-or-two-line **GOTCHA** where a reader
would otherwise write a bug. No history, no rationale, no worked examples, no "this used to be…".
That belongs here in the KB, linked by filename. Over ~6 lines is almost certainly carrying
something that is not spec. Comments follow the same rule: what the code does and why it is
surprising, never how it got here.

Measured 2026-09-11: 19 docstrings in the touched namespaces ran over six lines, some to eighteen,
carrying migration history and worked examples. Trimmed to one. Everything cut already existed in a
KB doc, so it was duplication as well as bloat.

This rule was enforced nowhere — not in `CLAUDE.md`, not in `AGENTS.md` or `docs/DOC-CONVENTIONS.md`
on `agents/develop`, and the only hook (`kb-doc-reminder.sh`, pre-push) checks KB docs. It lived in
the maintainer's head, which is why it was violated. It lives here now because this file travels
with the KB; `CLAUDE.md` is per-environment and not committed on code branches.

## Index it or it is invisible


Every `docs/kb/*.md` is linked from `docs/kb/README.md`. An unlinked doc is not findable, and
`check-docs.sh` fails the commit. Two plan docs sat orphaned for weeks because the hook was
never armed.

## Say what is not known


An open question is content. Write it down with what you tried, so the next person does not
repeat the dead end — *"the 5etools schema was not obtained; GitHub returns 403 through the
sandbox proxy"* is more useful than silence, and stops a second agent spending an hour on it.

**But check the KB before calling something open.** "Why is Circle of the Moon commented out?"
was listed as an open question while the answer sat in `srd-vs-plugin-content.md` in the same
directory.

## Arm the hooks

`core.hooksPath` is not set by cloning. Run `scripts/setup-hooks.sh` once per clone or none of
the enforcement above runs, and the discipline degrades to whatever the agent remembers. It
went unarmed for a whole session here; running `check-docs.sh` by hand afterwards found two
orphaned plan docs.

`.claude/hooks/kb-doc-reminder.sh` is a `PreToolUse(Bash)` hook filtered to `git push`. It
inspects the commits a push would send and, if they changed `src/` or `test/` while touching no
documentation, injects a reminder. **Advisory, never blocking** — a gate would only train people
to work around it. Both paths are pipe-tested: a code-only push emits the reminder, any `docs/`
change in the same push silences it.

**Reversal, 2026-09-13.** This doc previously recorded the hook's placement as unresolved,
because `.gitignore` excludes `.claude/` on code branches so the hook did not travel. That is
solved: `.claude/` is tracked on `agents/develop`, and `scripts/agent-setup.sh` copies it into a
code branch's working tree, where the ignore rule keeps it out of any commit. The convention did
not need a carve-out; it needed a copy step.

## Audit history


### 2026-09-05 — full KB audit (45 docs, ~7,700 lines)

Triggered by discovering, in sequence, that: the builder schema system I had "designed" was built in
June; the fighting-style gap I roadmapped HIGH was decided three days before my session in a doc named
after the branch; and the June `declarative-grant-vocabulary.md` had already stated the "repeatable
rows" insight. Common cause: **checking code carefully but not history or the existing docs.**

Drift found and fixed:

| doc | drift | fix |
|---|---|---|
| `content-extensibility-direction.md` | "NO grant compiler yet"; page-map "irreducible, skip"; spec-from-schema "next"; knew nothing of the AC refactor | 5 corrections in place; a "landed since" section |
| `roadmap.md` | 6 sections appended below Critical path; stale "no grant compiler"; stale ⚠️ on `grant-selection`; Track D not marked delivered; dead `datomic-crash-analysis` link; ~15 docs missing from the map | appended sections folded into the ledger/OPEN/Tracks; Track E plan added; doc map rebuilt |
| `README.md` | 4 entries as bullets after "Contribution rules"; D1–D31 (→D34); dead link; ~12 docs unindexed | new "Builders + authored mechanics" table; topic table completed |
| `backfill-ledger.md` | watch-list item 1 done but unticked; ledger "(none yet)" despite six AC deletions and a shim | ticked; 5 rows added; the outright deletions recorded as a **D34 exception with rationale** |
| `builder-form-schemas.md` | §1/§2a/§3 re-derived framework §2/§2e | collapsed to pointers; §6 Track E plan added |
| `fighting-style-vocabulary-gap.md` | re-roadmapped a decided item | cites the decision; keeps only the measurement |
| `armor-class-computation.md` | described the pre-refactor engine as current | HISTORICAL banner → `armor-class-refactor.md` |

Not fixed, flagged: **two parallel E2E directories** — `test/e2e/` (documented in
`cljs-headless-harness.md`, 13 ASI-era scripts) and `test/browser/` (starting-equipment scripts + the
three added this session). One should absorb the other; `test/e2e/` is the documented one.

**Rule added from this audit:** before designing anything, `git log -S` the key identifier and grep the
KB for the branch name. Both take under a minute and would have prevented all three re-derivations.

## Surfacing review lessons where they are needed (2026-09-07)

A session's worth of review produced a dozen rules — check the branches before designing, namespace
a CSS class, a metric goes blind when a control changes shape — and every one was written into the
document where it was *learned*. The KB is indexed by topic, so an agent about to name a CSS class
would have to read a spell-conversion gallery to find the lesson about naming CSS classes. It would
not.

Three tiers now, cheapest first:

1. **[before-you-start.md](before-you-start.md)** — the same lessons indexed by **task**. One screen,
   "if you are about to X, check Y, evidence Z". Linked first in the README.
2. **Enforced where it can be.** A rule a machine can check should not be a paragraph:
   - `builder_class_names_test` fails on an unprefixed builder CSS class. It found a dead allow-list
     entry the day it was written.
   - `builder-gallery.js` counts **labels** as well as controls and diffs against
     `test/e2e/builder-baseline.json`. A control count is written against one rendering and goes
     blind when that changes; a label survives. Verified by deleting a field and watching it fail.
3. **The narrative stays where it happened** — the gallery, the redesign notes. That is where the
   reasoning belongs; the checklist only carries the check.

**The test for whether this worked** is not that the page exists. It is whether the next session's
review finds *new* problems rather than the same ones. Add an entry only when a review catches
something a rule would have caught.

### The two obvious objections, answered

**"How big will that page get?"** Unbounded, if entries only ever arrive — which is how it would rot
into another unread document. So an entry **earns prose only if a machine cannot check it**. The
moment a lesson becomes enforced it collapses to one row in a table (rule → what fails → why), and
two already have. Judgement lessons — *is this layout better*, *does this colour fit this page* —
carry prose, and they are rarer than mechanical ones. Plus an explicit retirement rule: a caution
that has not been re-learned in several sessions, guarding something that has changed shape, gets
deleted. A stale caution costs more than the mistake it prevents.

**"How would an agent know to read it?"** It would not, and that was the real hole. `CLAUDE.md` is
the one file guaranteed to be in context, and it said nothing about the KB at all. It now opens with
a pointer to `before-you-start.md` and to this index. Anything not reachable from `CLAUDE.md` in one
hop is, in practice, not discoverable.

While fixing that: `CLAUDE.md` had been telling every agent that browser e2e lives in
`test/browser/*.js`. The sanctioned directory has been `test/e2e/` since the audit — 25 scripts,
with `lib.js` holding the shared helpers — and `test/browser/` is the older parallel one. **The one
file every agent reads was pointing them at the wrong directory**, which is a good measure of how
easily a guaranteed-context file goes stale.
