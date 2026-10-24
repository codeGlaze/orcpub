# Agent instructions — OrcPub / Dungeon Master's Vault

Rules every agent follows on every branch. Reference material is linked at the end: read it when
the task needs it, not before.

**Read docs from `agents/develop`:** `git show origin/agents/develop:<path>`. The KB (`docs/kb/`),
`SETUP.md` and `CODEBASE.md` exist only there; other `docs/` pages also sit on code branches,
sometimes as older copies, and the `agents/develop` copy is the current one. A bare `docs/<page>` in
these instructions means that copy, even where your branch has none.
`scripts/agent-setup.sh` on agents/develop sets up the tooling and prints where the KB index is; the SessionStart hook runs it from the local copy, or
run `git show origin/agents/develop:scripts/agent-setup.sh | bash`, which fetches first: do that after
any fetch that moved `origin/agents/develop`, to refresh the copies.

**Write to agents/develop** (a KB finding, a doc fix, a branch handoff) from a separate worktree of
it, one per code branch, never by switching the code branch's worktree, and push straight to it, no PR:
the commands are in `docs/GIT_WORKFLOW.md`, "Writing to agents/develop". Writing there is part of
the job, not "touching someone else's branch". **Except:** a push that changes `AGENTS.md`,
`CLAUDE.md`, `.githooks/`, `scripts/agent-setup.sh` or `.claude/` needs the owner's yes for that
push, asked in Needs you: every session loads or runs them, so one broken push reaches every agent
(the one exception: correcting the branch table, below).
List the worktree and what you will write in your stop rules.

## Branches — MUST FOLLOW

*Verified against `git ls-remote` on 2026-10-24. If a branch named here does not exist, correct
this table rather than working around it (`docs/kb/documentation-discipline.md`); that correction
is the one change to this file that needs no owner's yes.*

| Branch | Protection | Rule |
|--------|-----------|------|
| `develop` | **OWNER ONLY** | The upstream trunk. It takes `integration` through the release PR (#32), which the owner merges. Agents never push to or merge into it. |
| `integration` | **PR REQUIRED** | The active trunk, ahead of `develop`; hotfixes land here. No direct pushes. |
| `agents/develop` | **AGENT HOME** | What agents need to know, remember, run or use: KB, docs, introductions, agent config (`.claude/*`), hooks (`.githooks/*`), branch handoffs (`branches/`), and **agent-facing tooling**: research and measurement scripts, anything that helps an agent check its work and that a human developer would not reach for. **App source, and every test that checks the app (`lein test`, the cljs suite, browser probes and regression scripts, even those run by hand), stay with the code, with what runs them (`scripts/test/run-browser-probes.js`, `trace-probe.js`, `probe-baseline.json`), as do the dev tools a human developer uses (`dev/` REPL tools).** `testing/develop` is retired; its agent-facing parts come here. It carries integration's code for `file:line` accuracy, but code changed here conflicts with the next merge down. |
| `refactor/*` | **OPEN** | The refactor tree. `refactor/main` is its trunk (renamed from `refactor/content-extensibility` 2026-10-24); every other `refactor/*` branch is a leaf, cut from `refactor/main` and merged back into it. |
| `upgrade/*` | **OPEN** | Dependency upgrades (one live: `upgrade/datomic-pro`). Not a place to start other work. |
| `feature/*` `fix/*` `hotfix/*` `perf/*` `docs/*` `test/*` | **OPEN** | Agents may work freely. |
| `claude/*` | **DO NOT MERGE** | Harness auto-branches. Re-home the commits onto a typed branch first. |

"Someone else's branch", below, means any branch other than the one you were given, one the owner
approved for you by name, and `agents/develop`.

1. Branch from `integration` for anything that must ship; its PR goes into `integration`, and a
   refactor leaf's into `refactor/main`, which it was cut from. Push the branch as you go; open the PR only when
   the owner says. Never merge a PR, yours or anyone's, unless the owner tells you to merge that
   PR; the owner merges. Keep PRs small (one upgrade per PR); a breaking change
   carries migration notes.
2. **Given a `claude/*` branch by the harness, ask in your first reply** (in Needs you) to move the
   work to a typed branch, naming it (`Move this to fix/builder-tab from integration?`), so no stray
   branches pile up, and say if it was not cut from `integration`. Until the owner answers, work on
   the `claude/*` branch; on no, keep working there, and it never merges. The check and the move:
   `docs/GIT_WORKFLOW.md`, "Moving off a claude/* branch".
3. **Never create a branch the owner has not approved by name.** A web agent can push a branch but
   cannot delete one, so every branch it makes is the owner's to clean up. Ask it as its own yes/no
   question naming the branch (`Create fix/comment-check from integration?`). A yes to a plan that
   mentions "its own PR" or "a separate branch" is not a yes to the branch. Unapproved, the work
   goes on the branch you were given.
4. **Before calling a bug new or starting a feature, search for it:** the KB (`grep -ril "<term>"
   docs/kb/` on agents/develop, `git grep -il "<term>" origin/agents/develop -- docs/kb/` from a
   code branch), `BRANCH.md`'s "In flight" list, and the remote branches (`git branch -r | grep -i
   <term>`). If a decision is there, build it; do not ask the owner again. If another branch is
   already building it, stop and say so in Needs you.
5. **When a doc contradicts this file, do not pick a winner silently.** If one side plainly does
   not match the code or the remote, fix that side and say so in your report; otherwise ask in
   Needs you. An owner decision recorded in a doc (`BRANCH.md`, a KB page) is not yours to
   change: raise it only. Change `AGENTS.md` itself only with the owner's yes (the branch table above is the
   one exception: correct it when a branch named there does not exist).
6. **A test works around a known bug only if it names the bug and where it is tracked.** A silent
   workaround keeps the bug alive: the Option Source box came back three times this way.

## PRs and releases — MUST FOLLOW

- **Agents never push to or merge into `develop`.** `integration` reaches it only through the
  release PR (#32), which the owner merges.
- **Merge gate:** green CI (apart from "Branch changelog folded", which stays red until the fold;
  Changelogs, below), and a CodeRabbit review of the last push that changed behaviour in which every
  actionable comment is either fixed by a later push that does nothing else, with a reply on its
  thread naming the fixing commit, or answered on its thread.
- **CodeRabbit reviews are scarce: request one on purpose.** All agents and the owner share one
  allowance (every agent pushes as codeGlaze; no paid overage). It reviews a PR once by itself, when
  it is first opened as ready, and never again on its own; for a PR opened as a draft, see
  `docs/GIT_WORKFLOW.md`, "CodeRabbit". After pushes that
  change behaviour the last review did not see, batch them and ask once; a push that only fixes the
  last review's comments needs none; after asking, do not push until the review posts. How to ask
  and what to expect: `docs/GIT_WORKFLOW.md`, "CodeRabbit".
- **Fork-only files** may live on our `develop` and are removed before any PR to `Orcpub/orcpub`.
  The list is in `BRANCH.md` on agents/develop; add each new one there.

## Stop rules and budget — MUST FOLLOW

Nobody can see how many tokens are left, and a run with no written stopping point can spend the
rest of the budget redoing finished work. **Every response that does work starts with its stop
rules**, before any tool runs, so the person can interrupt. A read-only lookup of five tool calls or
fewer is not work here; if it needs a sixth, post stop rules before making it. A task that will
edit anything is work, even when its first steps are reads.

> **Stop rules for this turn**
> - what gets done, and what does not
> - the loop guard: an attempt budget for each change that can loop (usually one)
> - what makes it stop and report instead of retrying
> - anything it will NOT do (push, touch someone else's branch, run a second suite)
> - new branches: none, or the one the owner approved by name

**Loop guard.** A change that can loop is one you only find out about by running it (a fix for a
failing test, a dependency or build change, a conflicted merge, a migration), so a failure tempts
another try. Each gets an attempt budget, usually one. At the limit, stop and analyse; do not retry.

- **Flaky means stop.** A result that changes between runs with nothing changed is reported as
  flaky. Never retry it or change code to make it pass: retrying a race looks like progress and
  never ends. (An agent told to "fix anything that fails" spent 66k tokens re-rewriting a flaky
  test after its real work was committed.)
- **Contained fixes are done, not asked.** A fix that only removes a bug, with no knock-on effect
  (a duplicate require, a missing config entry, a stale comment), is made and reported. Ask when a
  fix changes behaviour, makes a design choice, or touches someone else's branch.
- **Verify once.** Run the "Before committing" checks (Tests, below) once, on the final change. A
  code commit made only so its test can be seen to fail (Tests) and that test's commit are one
  change: run the checks once, after the test is green and before the test's commit.
  If one fails, do not commit over it: report it in Needs you, and do not start another fix unless
  your stop rules budgeted one.
- **Subagents get hard limits** in their prompt: one implementation attempt, a set number of
  verification runs, a tool-call ceiling. In a shared worktree a subagent never wipes or commits
  the parent's edits: it records which files were clean when it started. Finished, verified work it
  commits, only those files; at its limit with the work unfinished it restores only those files; a file that already had changes is reported, never restored or
  committed. Never `git checkout .`, `git stash`, `git clean`, `git commit -a` or `git add -A`
  there. The commands: `docs/GIT_WORKFLOW.md`, "Subagents in a shared worktree".
- **One agent per change that can loop,** not one for a batch, so a single bad change cannot
  spend the whole batch's budget. A well-specified fix or search goes to a smaller model; keep the larger model for
  judgement.
- **Watch the only budget signal there is:** task notifications show a subagent's
  `subagent_tokens` and `tool_uses`. If they keep climbing with nothing new committed, check
  `git log` and `git status` yourself; if the work has landed, stop the agent.
- **Fetch before you read a remote branch:** `git fetch --prune origin`, every time, before reading,
  searching or diffing any `origin/*` ref. `origin/*` is a snapshot from the last fetch, not the
  remote, and other agents push during long sessions: an agent once reported a rule missing that
  had been pushed hours earlier.
- **Never run two `lein` processes in one worktree at once,** main agent or subagent: a REPL,
  `lein e2e-server` and `lein fig:build` each count. They overwrite `.lein-env` and
  corrupt each other's results. Stop a running server before building or testing there, or use a
  second worktree.

## Reporting back — MUST FOLLOW

The owner reads every reply, often on a phone, and the log of a long turn already shows the work.

- **End every response that did work with a short summary:** three to six lines of outcome (what
  changed for players or admins, whether it was verified, anything that went wrong), then
  **Needs you**. Do not restate test counts, file lists or steps already shown; detail lives in
  commits and `docs/kb/`.
- **Questions go only in Needs you,** one per number, each readable on its own: what it is, why
  it matters, what yes or no does.
- **Lead with the answer** when asked a question; a short question gets a short answer. A read-only
  lookup of five tool calls or fewer needs no stop rules (see Stop rules). A question that needs
  more work opens with them, and the answer leads the closing summary.

## Code — MUST FOLLOW

- **Placement:** shared logic in `src/cljc/`, server-only in `src/clj/`, client-only in `src/cljs/`
  or `web/cljs/`. Components follow `com.stuartsierra/component` and restart cleanly through the
  `dev/user.clj` helpers; auth uses Buddy JWS tokens, handled as `orcpub.routes` does.
- **Homebrew keys are user-facing.** Never rename one without a backwards-compatible mapping.
- **Swapping a library keeps its alias** (`[clj-time.core :as t]` becomes `[java-time.api :as t]`).
- **Config comes from one `.env` at the repo root**, git-ignored (`.env.example` has safe defaults).
  Clojure never reads `.env` itself: `scripts/common.sh` sources it with `set -a`, and `environ`
  sees ordinary environment variables. Scripts load it over the shell; Docker Compose lets a shell
  variable beat it; plain `lein` does not load it at all (table: `docs/ENVIRONMENT.md`). There is no
  `dotenv` dependency; do not add one. Read values through `orcpub.env/value`, which treats blank as
  absent (`docs/kb/blank-env-values.md`). Never commit secrets (`SIGNATURE`, DB credentials): they
  go in `.env` or the CI secret store. Variables: `docs/ENVIRONMENT.md`.
- **Built-ins before scripts:** `lein test`, `lein lint`, `lein garden`, `lein uberjar`, `lein deps`,
  local JARs through the `file:lib` repository. A custom script only orchestrates several tools or
  sets up an environment (`scripts/start.sh`, `./menu`). Follow existing patterns and names
  (re-frame event and sub names as in `web/cljs/orcpub/*`).
- **Reuse before you write:** look for an existing function, script or test that does the job, and
  call or extend it rather than writing a second one. Where new code then goes is a separate
  question: a feature of its own gets its own namespace instead of growing a big file ("Shrinking
  the big files" below).
- **UX:** read [docs/kb/ux-guide.md](docs/kb/ux-guide.md) before designing anything a player sees.
  It is a floor: existing screens are not precedent, and an agreed improvement updates the guide.

### re-frame

- **Work derived from an edit never runs inside the edit.** The handler or interceptor that applies
  a click or keystroke does only that. Builds, settles and other derived work run afterwards (a
  follow-up event, or a debounced subscription like `debounced-build-sub`); a failure there logs and
  leaves the edit in place. Measure before adding anything to an edit's path.
- **Nothing loads until something on the page needs it; then once, and cached.** The view that
  shows data fetches it on first use (a subscription that starts its own fetch, like
  `:portrait/artist-overrides`, or a load on opening, like the Orcacle's SRD rules), never at
  start-up or on a page that does not show it. A new start-up step, or a new request on a page's
  load, needs its reason written in the PR description: the artist credits were once fetched on
  every page, and only an unrelated test's 404 showed it. The unmerged branch
  `test/page-load-budget` adds a browser check that fails on any request a page's allowance does
  not list.

### Shrinking the big files — SHOULD FOLLOW

Most logic sits in a few huge files: `views.cljs` (11.5k lines), `events.cljs` (8.8k),
`styles/core.clj` (3.7k), `options.cljc` (3.7k), `pdf.clj` (3.3k), `character_builder.cljs`
(2.9k), `routes.clj` (2.8k). Don't add new features to them. A fix can stay in place when moving its code out would be a
big move of its own; otherwise look for the piece your work touches that could move out with it.
Report what you moved. When you find code that could move out and you did not move it, ask in
Needs you: what it is, where it would go, and why.

- **New code goes in its own namespace** when it is a feature of its own (a page, a builder panel,
  a route group). A big file gets only the wiring: a require, a route entry, a call.
- **A namespace holds a concept family, not one small concept:** `inventory`, with kits inside it,
  not a `kits` namespace of its own. Splitting a monolith into dozens of micro-namespaces trades one
  problem for another.
- **Move what you are already working in.** When a branch adds to or reworks a self-contained group
  in a big file (a feature's views, its events and subs, a route group), move it to its own
  namespace. A one-line fix inside a group does not trigger a move.
  - **A small move goes in the same branch,** as its own commit with no behaviour change.
  - **A big move gets its own branch, and you ask first** (naming the branch). Small is one
    self-contained group whose callers you can update in the same commit; anything more, or any
    doubt, counts as big. A big extraction left in a long-lived branch races the mainline and
    dies: `refactor/views-extraction` did. Plans: `docs/kb/monolith-decomposition-plan.md`,
    `docs/kb/class-builder-extraction-plan.md`.
- **Don't move** code you are not otherwise touching, code an open PR is changing in the same file
  (wait until it lands), or a group tangled with half the file. Note it as a candidate in the
  branch handoff instead.
- **A move changes nothing else:** same names, same re-frame event and sub ids, callers updated,
  tests green before and after. Moved functions count as new to the docstring hook: give any that
  lack one its docstring in the move commit (a docstring is not a behaviour change).
- **Data files are exempt:** `monsters.cljc`, `spells.cljc`, `magic_items.cljc`, `classes.cljc`
  hold SRD data, not logic.
- **Report it:** give the file's line count before and after.

### Comments and docstrings — MUST FOLLOW

- **Every function you add, change or move gets a docstring, and docstrings are spec** (every
  `defn`, `defn-` and `defmacro`, test helpers included; a `deftest` needs none): what it does, its
  args, what it returns. A new dev should know what any function does without reading its body.
- **A GOTCHA line is the rare exception, never a required part:** one or two lines, only for a
  tricky function or code that is easy to break by accident. Most docstrings have none.
- **Comments say what the code does and why it is surprising.** No history ("used to", "was
  once"), no rationale or decision narrative, no worked examples: those go in `docs/kb/`, and the
  comment names the page (`key-collision-behavior.md`).
- **Keep KB pointers** (`page.md`): trimming history leaves a pointer to where it went. Change one
  only if the page moved, the code is gone, or it names the wrong page.
- **`;; FIELD NOTE (id):` comments are protected.** They record a verified fact a reader would get
  wrong (data in the wild, a trap, where to trace a behaviour). **Never remove or shorten one in a
  cleanup**; change it only when the fact changes, and record its new hash in
  `test/comment-baseline.edn` `:field-notes` (`comment_discipline_test` prints the line).
- `comment_discipline_test` fails on history phrasing, docstrings over 6 lines and comment blocks
  over 4. Fix each hit, or approve it in `test/comment-baseline.edn` `:allowed` with a written
  reason. Never approve unread. Examples: `docs/kb/documentation-discipline.md`.

### SRD text is verbatim — MUST FOLLOW

- **Never write wording the SRD does not use into SRD data** (open5e fixtures, generated SRD
  files). A fix touches only what is broken. Where a field has no SRD sentence of its own (a casting
  option's `desc`), use the SRD sentence it comes from, changing only the slot number. **No
  paraphrase, no "clearer" rewording.** Our own summaries go in a separate field.
- `scripts/srd/check-srd-data.py` (on agents/develop) checks a branch in about a second; the open5e
  clone's `pre-push` hook runs it. Edit open5e records only with `scripts/srd/open5e_records.py`;
  for a new kind of defect, add its rule first and see it fail. Why:
  `docs/kb/open5e-upstream-notes.md`, "SRD data checks".

## Tests — MUST FOLLOW

- **Before committing:** the checks for what you changed (`lein test`, `lein fig:build` and the cljs
  suite, and lint of the changed files): which folder needs which is in `docs/TESTING.md`, "Before
  committing". Run the full `lein lint` before committing a merge, before opening a PR, and before
  marking a draft ready. Merge with `--no-commit`, so the checks run before the merge commit: a
  merge with no conflicts otherwise commits itself, unchecked (`docs/GIT_WORKFLOW.md`, "Merging
  integration in").
- **Merges and the docstring hook:** on a merge commit the hook checks only functions new to both
  sides, so the other side's functions pass and any you write while resolving conflicts are checked.
  **Never commit or push with `--no-verify` without the owner's yes for that commit or push**,
  whatever a hook's message suggests. Ask one commit or push at a time, naming the hook that is in
  the way, why its check is wrong here, and what else the flag skips: it skips every hook, including the one that strips session
  URLs from commit messages and the guard that blocks agent files (`CLAUDE.md`, `AGENTS.md`, `BRANCH.md`, `.claude/`) on code branches.
- **A test is seen to fail before it is committed.** A test only ever seen passing proves nothing:
  a spellbook check passed only because the export it measured had lost its spell list. Commit the
  code under test first, without the test; for each claim, break only the files (or hunk) that claim
  depends on back to the source before that commit (for code committed earlier, a hand edit of that
  path); see the expected checks fail, and only those; restore the files; run the test
  green. **Never break a file with uncommitted changes** (the restore overwrites it for good), never
  restore a whole directory, and never commit a break. Put the failing output in the test commit's
  message and in the PR beside the pass. A break that does not fail the test means the test is weak:
  strengthen the test, never weaken the break. The commands: `docs/TESTING.md`, "Seeing a test fail".
- **Large schema changes** need migration scripts and tests.
- **Deployment changes:** a change to any path in the `paths:` list of
  `.github/workflows/docker-heavy.yml` (Docker, compose, deploy, `run`, the swarm, release, env and
  start scripts, the crash and alert code). On a PR whose branch or base carries that workflow, one
  such file anywhere in the PR runs about 20 minutes of heavy Docker checks on **every** push, docs-only
  pushes included. So once such a PR is open, or before you open it, ask in Needs you before the first push that
  brings a listed file in and before every push after (pushes to `feature/email-and-alerts` itself:
  `docs/GIT_WORKFLOW.md`, "Heavy Docker checks"), and push only after the owner says yes (an exception to "push
  the branch as you go"). The owner can waive this for the session; then push and say so in your
  summary. Before asking for review of such a PR, its heavy Docker run must be green. When they run and how to start them: `docs/GIT_WORKFLOW.md`, "Heavy Docker checks".
- **Where a test goes and how to run it:** `docs/TESTING.md` has the four layers. A regression test
  goes in the lowest layer that can reach the bug. **Before a PR:** `lein test`, the cljs suite, the
  full `lein lint`, and the real-app browser suites for what the branch changed.

## Changelogs — MUST FOLLOW

- **A changelog says what the branch DID, not the commits it took:** one line per outcome a user or
  maintainer would notice ("New sign-up, sign-in and account-recovery pages", not the 85 commits
  behind them).
- **Fixed is for bugs that existed before.** A fix to something the same branch or release built
  gets no line. No internal work (tests, CI, refactors, tooling).
- Lines go under Added / Changed / Fixed / Security, open with their area in bold
  (`**Accounts:**`), and cite the upstream issue (#N) when one matches; no commit hashes.
- **Copy `docs/branch-changelog.template.md` to `docs/branch-changelog.md`** on the branch at its
  start and fill it in as work lands. Its "Branch changelog folded" CI check stays red on the PR
  until the fold; that is expected while the PR is reviewed, because the fold drops the
  reviewer-only section. **Fold when the owner says the PR is about to merge**
  (`scripts/fold-branch-changelog.sh "<release>"`), commit, push.

## Docs — MUST FOLLOW

- **TOP PRIORITY: keep the docs current.** When a feature, command or workflow is added or
  changed, update the docs carried on the code branch (README, `docs/` pages there) in the same
  PR, and the KB and agents/develop docs with a commit to agents/develop at the same time (top of
  this file). A doc that exists on both (`README.md`, `docs/ENVIRONMENT.md`): change both copies.
  When the PR is opened, name the agents/develop commit in its description.
- **Read `BRANCH.md` and your branch's handoff at every session start.** `BRANCH.md` on
  agents/develop holds the project-wide state and plan; `branches/<branch>.md` holds this branch's
  own. The generated `CLAUDE.md` inlines both. Start a handoff from `branches/TEMPLATE.md` when the
  branch has state the next agent needs; keep it current and short (`branches/README.md`).
- **Findings go in `docs/kb/`**, linked from `docs/kb/README.md`; an unlinked page is invisible.
  Discoveries belong there, not in session transcripts.
- **One source of truth:** link to a doc instead of repeating it, and put docs close to what they
  document. `CLAUDE.md` is the thin Claude bootstrapper, `AGENTS.md` the owner's rules for every
  agent, `BRANCH.md` the project-wide state and plan, a branch handoff (`branches/<branch>.md`)
  one branch's own state. Structure: `docs/DOC-CONVENTIONS.md`.

## Reference — read when the task needs it

| For | Read |
|-----|------|
| Where things are: entry points, key files, dev profiles | `CODEBASE.md` |
| Running the app: `./menu`, `scripts/start.sh`, Docker | `README.md`, `SETUP.md` |
| Branches, PR flow, agent tooling and hooks | `docs/GIT_WORKFLOW.md` |
| Datomic install; Datomic Free does not run on Java 21 | `docs/DATOMIC_SETUP.md` |
| `.env` and environment variables | `docs/ENVIRONMENT.md` |
| Entity build pipeline | `docs/kb/entity-options-architecture.md`, `docs/kb/perf-entity-build.md` |
| Dependency history (largely historical; nothing tracks CVEs continuously) | `docs/kb/UPGRADE_PLAN.md` |
| Theming, icons, theme screenshots (describes the unmerged `claude/add-color-themes-gyRhI`) | `docs/THEMING.md` |

**Known warnings you cannot fix:** `garden.color/abs` shadows `clojure.core/abs` (Garden);
`datomic.common/requiring-resolve` (Datomic Free).

**A PDFBox font-fallback warning is a bug, not noise:** every drawn font is embedded since
2026-10-22, so exports no longer cause it; one seen means text was read or rendered before save
(`docs/kb/pdf-generation-architecture.md`).
