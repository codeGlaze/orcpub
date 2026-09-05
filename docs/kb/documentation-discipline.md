# Documentation discipline

The KB is the point. Code and tests say what the system does now; the KB says what was **learned** —
what was traced, what was measured, what turned out to be false, and what was decided. That is the
part that cannot be recovered by reading the diff.

## Write a doc when the work produced knowledge

Not for every change. Write or update a doc when the work produced any of:

- **a traced behaviour** — what a function actually does, established by running it
- **a measured number** — a benchmark, a coverage count, a before/after
- **a bug and its cause** — especially a shipped one, and especially how it was found
- **a decision** — what was chosen, what was rejected, and why
- **a reversal** — something previously believed that turned out wrong

A typo, a rename, a test-only tweak needs nothing. Say so in one line and move on.

## Update in place; record reversals separately

Two failure modes this KB has actually hit:

1. **Appending a second account** of the same subject instead of updating the first, leaving two
   docs that disagree. Update the existing doc.
2. **Silently overwriting a superseded conclusion.** This happened here: a section arguing a
   limitation was unavoidable was replaced wholesale by the section saying it was not, so the
   reasoning behind the original call became unrecoverable. The current truth goes at the TOP; the
   superseded reasoning and what reversed it go in a **Corrections** or **History** section at the
   tail. See `armor-class-refactor.md`, which carries both.

## Structure: current truth first, audit trail last

Agents (and people) read the top of a long file and may never reach the bottom. So:

- **current state** first — what is true now, in a form someone can act on
- **the plan / what is open** next
- **`# History`** last — a dated ledger of what landed, and a `## Corrections` list of what was
  believed and what reversed it

Stale content at the top is the most expensive kind.

## Claims must be proven, not asserted

The recurring failure in this repo is a confident claim built on a partial read: a `tag->flag` map
built from a grep that missed half the fields; "this part is irreducible" repeated from a table
until someone checked; a benchmark conclusion drawn from operation counts rather than time. D22 in
`content-extensibility-decisions.md` records the general rule — *treat "this is irreducible" as a
claim to be proven against the code, not asserted* — and it has been violated more than once since
being written.

Related: `verification-discipline.md` (how to check a thing without fooling yourself).

## The push reminder hook

`.claude/hooks/kb-doc-reminder.sh` is a `PreToolUse(Bash)` hook filtered to `git push`. It inspects
the commits a push would send and, if they changed `src/` or `test/` while touching no
documentation, injects a reminder to write down what was learned. **Advisory, never blocking** — a
gate would only train people to work around it.

Both paths are pipe-tested: a code-only push emits the reminder, and any `docs/` change in the same
push silences it.

**Placement is unresolved.** `.gitignore` excludes `.claude/` on purpose — *"Agentic/AI tool files
— belong in dotfiles or agents/ branch, not code branches"* — so the hook as written does NOT
travel with this branch. It works locally for whoever creates it. To make it shared it needs to
move to the dotfiles or an `agents/` branch per that convention, or the convention needs an
explicit carve-out. Recorded here so the reminder logic survives even though the wiring does not.

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
