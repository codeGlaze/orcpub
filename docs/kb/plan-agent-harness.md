# Plan: shared agent core, thin harness layers (v4.17.0, 2026-11-27; versions are major.minor.revision, a revision only corrects or rewords)

**Goal.** Claude Code and Cline (Copilot as an emergency hatch) work in codeGlaze/orcpub from one
shared core: one rules file (`AGENTS.md`), one set of checks (`scripts/`, `.githooks/`, CI), one
set of skills (`.claude/skills/`), one knowledge base (`docs/kb/`). Each harness gets only a thin
layer that loads context and translates check output. A rule, check or skill added for one agent
serves all of them. Every session starts at `START-HERE.md`. Agents are **armed** (setup and
context loaded at the start), **guarded** (hooks, CI and Cline's plugin stop what must not happen)
and **guided** (skills, named at the moment they apply). Free models get closed tasks, sources in
context, and a guard that catches their mistakes before they run.

**Cline alone is the floor** (owner, 2026-11-27). When Claude's funding runs out, Cline works with
the owner as the main agent. Every row must work with Claude absent: blocks escalate to the owner,
never to Claude; checks are scripts, not a Claude review; the owner approves what the guard does
not, one command at a time in Cline's own dialog, and runs a blocked command in their own
terminal (row 3). Claude orchestrating one to three cheap agents is the upgrade
("Several agents at once"), never a dependency.

**Three tenets** (owner, 2026-11-24) judge every row: **Reliability** (the work can be trusted:
claims cite evidence, something other than the author checks it, unknowns are said), **Efficiency**
(the least work and context for the result: scripts over judgement for mechanical work, stop when
done or blocked), **Efficacy** (the work does what was asked: acceptance criteria met and shown,
blockers raised early). A row need not serve all three; it must not harm one without saying why.

**State.** Design decisions 1 to 5 of v4.4 approved (owner, 2026-11-24; decision 4 the same day,
after fact 10). Row 1 **done** (owner's go 2026-11-24; pushed as `492b5e800`..`0563c819f`,
`agents/develop` now at `0563c819f`; `pedestal-0.8.md` had already been carried by `205948a06`;
`topic-index.md` regenerated, not patched). Row 2 **done** (`ca54524a5`). No other row approved.
v4.17.0 (owner, 2026-11-27): **row 3 done**, pushed to `agents/develop` as `c69e3faa9` (guard,
self-test, replay) and `c263fa5e5` (KB "Cline plugin payloads"); demo passed in a throwaway
worktree. Adds one lein per worktree to the guard (a second `lein` there asks), and
`GIT_PAGER=cat` in Cline's shell (`~/.bashrc`, when `CLINE_ACTIVE` is set): `git log` hung on
its pager for 300 seconds in the demo.
v4.16.0 (owner, 2026-11-27) writes row 3's build into its rules: `||` split, reads anywhere and
writes at home, `/tmp` redirects, `$?`, bash's backslash rules in double quotes, git-only hook
checks, self-protection on the installed guard only, `git fetch --prune` at home, a few more read
commands, no separate allowlist file; all six run and write tools covered, an unreadable write
refused; acceptance is the safe cases plus the reported ask rate (22.9% measured, 10% target); the
demo runs in a throwaway worktree; row 14 moves the generic layer to dotfiles last.
v4.15.0 (owner, 2026-11-27) numbers the rows in build order: the guard core is row 3, the
benchmark baseline becomes row 4, the claim checker 5, the `BRANCH.md` rewrite 6, then skills 7
through bootstrap 13; the guard's reminders and hard stop are row 11.
v4.14.0 (owner, 2026-11-27) splits row 8 into 8a (guard core, built next, needs nothing) and 8b (guides and `pre-push` stop, after rows 4 and 6); own-branch pushes ask in 8a until row 3; adds row 11 (`BRANCH.md` rewrite, lossless, after row 3 and the baseline); takes the v4.12.0 review (7 blocks, 4 changes; each reproduced): checks on the words bash runs, escapes and globs ask, output and program flags ask, `sed -n` print scripts only, a git config check, pathspec magic and `.git/` writes blocked, fetch and ls-remote narrowed.
v4.13.0 (owner, 2026-11-27) adds "Making free models better": six proposals, benchmark first.
v4.12.0 (owner's yes, 2026-11-27) rebuilds row 8's approval on facts 12 and 13: Cline's global
command auto-approve stays off and the guard approves safe commands itself, so Cline's dialog
appears only for the rest; chains are split and every part checked, and anything the parser does
not fully understand asks; pipes only into named read-only filters; the read-only list is spelled
out; the owner's `allow` file is gone (blocks stay blocks, whoever approves); 118 real Cline
commands become a replay test with an ask-rate target of 10% or less, which the rules as measured
do not yet meet (58%).
v4.11.0 takes the v4.10.0 review (ChatGPT 6 findings, Cline 4; 7 held at source): the `allow` file
moves to `/etc/orcpub-guard/`, root-owned, since a test run by `lein test` could write a file in
`~/.cline`; the folder claim covers git writes; only explicit own-branch pushes run, and only
with no configured push refspec or push URL; the unused `push` grant is gone; rows 6 and the review
and design decisions name who does Claude's part when Claude is absent.
v4.10.0 (owner, 2026-11-27): Cline alone is the floor (Goal); the guard's `allow` file lets the
owner open a block without Claude (row 8 step 4); the folder claim and "Several agents at once"
(one named worktree per agent per task, no lease); row 5's worktree rule names the folders; the
claim-file lease was dropped (a linked worktree's `.git` is a file, and leases go stale).
v4.9.0 (owner, 2026-11-27): Cline pushes to its own working branch without asking (row 8 step 5);
force, other-branch and protected-branch pushes still ask or block. v4.9.1 adds the detached
docs-worktree form, `git push origin HEAD:agents/develop`, which v4.9.0 would have asked about.
v4.8.0 takes the v4.7.0 review (ChatGPT, 6 of 7 findings held at source): the guard blocks every
stage-everything spelling, matches setup commands with exact arguments, asks when an allowlisted
script was changed, and gains a tripwire on protected files before commit and push; the self-test
covers each operator and near miss; the guard's limit (code run by `lein test`) is stated; row 5's
worktree rule names signs of another agent instead of claiming `git log` proves there is none.
v4.7.0 takes the v4.6.0 review (Cline, Gemini, ChatGPT): the guard lets `gh` run only as listed
read commands and treats any shell operator as a chain to ask about (reviewers found writes and
chains that passed the v4.6.0 lists), with a self-test of those bypasses; row 5 gains "one agent per
worktree" for every agent (owner, 2026-11-26, after another session's commit swept up Cline's files),
promoted from Later; coverage names line 412.
v4.6.0 (owner's yes, 2026-11-26) rebases rows 4, 5 and 8 on `agents/develop` `6aeea4ab8`, after other
sessions changed `AGENTS.md`, `pre-push`, `GIT_WORKFLOW.md` and added `scripts/line-counts.sh`: the
heavy Docker rule is now landmarks and a label (owner, 2026-11-25), so the `heavy-docker` skill and
row 5's "Deployment changes" text are rewritten; row 5's line numbers are re-counted; the new
upstream-issues rule in "Changelogs" is kept (short in `AGENTS.md`, steps in `fold-changelog`);
`move-big-file-code` names `line-counts.sh`; the guard also blocks GitHub writes (`gh issue`,
writing `gh api`) and asks for chained commands.
v4.5.0 adds decision 4 to row 8: a `pre-push` hard stop for Cline (step 9), which makes row 8 touch
a protected file.
v4.4.0 adds the three tenets, the claim checker as row 3 (rows 3-9 become 4-10), the
`count-and-compare` skill, the skill-citation check, guard self-protection and the review rule.
v4.3 trims four `AGENTS.md` sections into the skills (row 4; owner, 2026-11-23). v4.2 takes the first review round (KB count, inbox wording, merge-date source, the `--check` lines). v4.1 adds skills (tested), decides how Cline gets the branch state (owner: A), and keeps the full
Cline guard (owner, 2026-11-23: "arms and guards and guides agents as best as possible"). Rows are
in build order; each names its number in v3.7.

## Tested (2026-11-23, Cline 4.1.26 in WSL, model `cline-free/nemotron-3.5-super-vl`)

Each fact: what was run, what showed it.

1. **Cline hook scripts do not run.** Test scripts named for every event (`TaskStart`,
   `PreToolUse`, ...) in `~/.cline/hooks/`, `~/Documents/Cline/Hooks/`, `.cline/hooks/`,
   `.clinerules/hooks/`, with and without `.sh`. The Hooks tab listed them all as on. Across three
   tasks, after a window reload and a toggle: none wrote its log (the same script wrote it when run
   by hand), Cline's output log has no hook entry, the chat shows no hook status row. The extension
   still carries the hook code (`dist/extension.js`), but tasks run on the SDK runtime
   ("VscodeSessionHost Initialized with ClineCore").
2. **Cline plugins run, despite the docs.** [customization/plugins](https://docs.cline.bot/customization/plugins):
   "This feature is not applicable on VSCode and JetBrains Extension for now." Yet a CommonJS file
   `~/.cline/plugins/cline-hook-test-guard.js` (`module.exports = {name, manifest: {capabilities:
   ["hooks"]}, hooks: {...}}`) ran in a VS Code task: `beforeRun`, `beforeTool`, `afterRun` fired,
   in that order. The extension ships the plugin runtime (`dist/plugin-runtime/`).
3. **A plugin can block a command.** `beforeTool` saw `{"commands": ["echo hi", "echo BLOCKME"]}`
   (context keys `snapshot`, `tool`, `toolCall`, `input`); `{skip: true, reason}` refused the whole
   call; the model read the reason, re-ran `echo hi` alone, and said it would not work around the
   guard. `toolCall.name` was empty: the tool name sits in another field.
4. **Cline tool names** (task messages): `run_commands`, `search_codebase`, `read_files`, `editor`.
5. **Cline rules load.** The Rules tab listed a global rule (`~/Documents/Cline/Rules/*.md`) and the
   workspace `AGENTS.md` (the gitignored copy `agent-setup.sh` makes). A task asked, without tools,
   to quote the first line of each rules file it had: it obeyed the global rule's marker and quoted
   `AGENTS.md`'s first line exactly (`<!-- generated by agent-setup.sh ... -->`).
6. **Skills: found by both harnesses, used by Nemotron only when named.** A skill is
   `<folder>/<name>/SKILL.md` opening with YAML frontmatter (`name`, `description`); Cline rejects
   one without it (`dist/extension.js`: "SKILL.md must start with YAML frontmatter"). Cline's Skills
   tab listed a global skill (`~/.cline/skills/`) and a workspace one (`.claude/skills/`), both on;
   Claude Code listed the `.claude/skills/` one too. A task that fit both skills but named neither
   used neither. A task naming one loaded it, applied its decision rule (neither fix nor new, so
   "Changed", saying why) and adapted the result to the repo's changelog style. Cline's skill
   folders (`dist/extension.js`): workspace `.clinerules/`, `.cline/`, `.claude/`, `.agents/` +
   `skills/`; global `~/.cline/skills/`, `~/.agents/skills/`.
7. **Auto-approve is all on** (`~/.cline/data/globalState.json`, `autoApprovalSettings`: edit files
   inside and outside the workspace, execute all commands, browser, MCP). Cline has no command
   allowlist; "safe commands" is the model's judgement.
8. **Plan mode hands off to Act** with no further prompt.
9. **Where files land.** A Cline task's working folder is the VS Code window's folder; a task in the
   orcpub window wrote `test.txt` into orcpub twice (removed).
10. **Cline's shell sets `CLINE_ACTIVE=true`** (owner's test, 2026-11-24: `env | grep -i cline` in a
   Cline task). Set on purpose: both terminal-creating paths in `dist/extension.js` pass
   `env: {CLINE_ACTIVE: "true"}`. Claude Code's shell has no `CLINE_*` variable (exit 1). Not
   documented by Cline, so an update may drop it.
11. **Nemotron writes a fixed JSON result format** (one trial, 2026-11-24: the v4.4 proposal
   review). Valid block; 35 of 41 quotes at the cited line, 6 one to six lines off, none invented;
   every count against the repo right; its one count of the reviewed file wrong (9 for 10).
12. **A plugin approves one command at a time** (2026-11-27, probe plugin, Cline auto-approve set
   to Read, Edit, Web Fetch, MCP, commands off). `beforeTool` returning `{policy: {autoApprove:
   true}}` ran `git status` with no dialog; a command it returned nothing for showed Cline's
   Approve dialog; a third command asked again (an approval covers one command, not the next); `skip`
   refused `BLOCKME`. The reverse cannot work: `handleRequestToolApproval` in `dist/extension.js`
   approves when `policy.autoApprove === true` **or** the global setting allows the tool kind, so
   with global command auto-approve on, a plugin cannot make Cline ask. Payload: `ctx.input` is a
   JSON string (`{"commands":["git status"]}`); `ctx.toolCall.name` was undefined.
13. **Cline chains its commands** (2026-11-27, the 118 `run_commands` calls in the 10 tasks in
   `~/.cline/data/sessions/`). 105 were chained, 63 began with `cd`. Under v4.11.0's step 5 (any
   operator asks): 4 run, 113 ask, 1 block (a false positive: a `sed -n` script that read
   `/tmp/agents.md`). Split at `&&`, `;`, `|`, with `cd` and `2>/dev/null` allowed and each part
   checked against the v4.11.0 list: 49 run, 68 ask, 1 block. The asks were mostly read-only `git`
   subcommands not on the list (`-C`, `grep`, `rev-parse`, `ls-remote`, `worktree list`), then 23
   redirects or `$(…)`, then `timeout`, `true`, loops, `printf`. All 10 were review tasks; building
   tasks are unmeasured.

## Sources for reviewers

Repo files, pinned (`agents/develop` `6aeea4ab8`, `integration` `18a091a`). For line numbers swap
`raw.githubusercontent.com/codeGlaze/orcpub/<sha>/` for `github.com/codeGlaze/orcpub/blob/<sha>/`.

`agents/develop`:

- [AGENTS.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/AGENTS.md)
- [CLAUDE.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/CLAUDE.md)
- [BRANCH.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/BRANCH.md)
- [START-HERE.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/START-HERE.md)
- [scripts/agent-setup.sh](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/scripts/agent-setup.sh)
- [scripts/check-docs.sh](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/scripts/check-docs.sh)
- [scripts/clj-grep.py](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/scripts/clj-grep.py)
- [scripts/fold-branch-changelog.sh](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/scripts/fold-branch-changelog.sh)
- [scripts/srd/check-srd-data.py](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/scripts/srd/check-srd-data.py)
- [.claude/hooks/kb-audit-reminder.sh](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.claude/hooks/kb-audit-reminder.sh)
- [.claude/hooks/kb-doc-reminder.sh](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.claude/hooks/kb-doc-reminder.sh)
- [.claude/settings.json](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.claude/settings.json)
- [.claude/config.json](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.claude/config.json)
- [.githooks/pre-commit](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.githooks/pre-commit)
- [docs/DOC-CONVENTIONS.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/DOC-CONVENTIONS.md)
- [docs/GIT_WORKFLOW.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/GIT_WORKFLOW.md)
- [docs/TESTING.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/TESTING.md)
- [docs/kb/README.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/kb/README.md)
- [docs/kb/copilot-agents.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/kb/copilot-agents.md)
- [docs/kb/agent-hooks.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/docs/kb/agent-hooks.md)
- [.github/copilot-instructions.md](https://raw.githubusercontent.com/codeGlaze/orcpub/6aeea4ab8d46c167855e7e96e0b3c80347437c1e/.github/copilot-instructions.md)

`integration`: [.gitignore](https://raw.githubusercontent.com/codeGlaze/orcpub/18a091a201c18d1614713145836fff78fc596af5/.gitignore),
[.github/workflows/docker-heavy.yml](https://raw.githubusercontent.com/codeGlaze/orcpub/18a091a201c18d1614713145836fff78fc596af5/.github/workflows/docker-heavy.yml).
Commit that added `.claude/config.json`: [3cb53584](https://github.com/codeGlaze/orcpub/commit/3cb53584922c540432a0078fb91dc951ecb7f81b).

Cline: [rules](https://docs.cline.bot/customization/cline-rules),
[plugins (customization)](https://docs.cline.bot/customization/plugins),
[SDK plugins](https://docs.cline.bot/sdk/plugins), [config](https://docs.cline.bot/getting-started/config).
VS Code: [custom instructions reference](https://code.visualstudio.com/docs/agents/reference/custom-instructions-reference).
The "Tested" facts above come from the owner's machine; reviewers cannot re-run them, only check
that the plan uses them consistently.

## Review rules (read before reviewing)

1. Review for **errors that would cause wrong behaviour**, and for **order and completeness**:
   a row that needs something a later row makes; a later row that changes text an earlier row
   wrote; a row whose builder would have to invent content the plan does not give. New ideas go
   under "Later".
2. Every finding: quote the plan line, then the evidence (`file:line`, a command and its output,
   or a URL). No evidence: list it under "Unverified", where it changes nothing.
3. A **closed decision** reopens only with evidence that it is wrong.
4. Order findings: blocks a row / should change a row / later. At most ten.

## Closed decisions

- `AGENTS.md` stays the one rules file, agent-neutral. Claude-only material lives in
  `CLAUDE.md` below its imports; `agent-setup.sh` already passes it through (no parser change).
- **Cline gets the branch state through the root `AGENTS.md` copy** (owner, 2026-11-23: A): the
  gitignored copy carries `BRANCH.md` and the handoff below the rules, as `CLAUDE.md` does. No
  `.clinerules/` folder: it is untested (fact 5 tested only `AGENTS.md` and the global folder), and
  Copilot also reads the root `AGENTS.md`, so it gets the state too.
- **One skills folder, `.claude/skills/` on agents/develop**, for both harnesses (fact 6);
  `agent-setup.sh` already extracts all of `.claude/` into each worktree (`scripts/agent-setup.sh:90`).
- **A skill is named where it applies** (fact 6: unnamed skills went unused): the `AGENTS.md`
  index, the Cline guard's prompts, and task briefs. `AGENTS.md` keeps every MUST rule in one line
  or more; a skill holds the how and the decision points, and points at the doc section that
  holds the steps (one source).
- `agent-setup.sh` is the installer for every agent; Cline support goes there, not a second script.
- Check logic lives once, in `scripts/`; harness layers only translate output.
- Cline KB notes go to `docs/kb/inbox/` until reviewed.
- `KB: none` + dash (— or -) + non-blank reason exempts that commit only.
- Cline guards are built from captured real plugin payloads, never from documented tool names.
- Guards that must hold live in git hooks and CI; Cline's plugin is a layer on top, with a canary.
- The Cline guard keeps its full scope: blocks, allowlist, protected-file edits, reminders
  (owner, 2026-11-23).
- Design and protected files: Claude Code. Closed, test-backed tasks: Cline. With Claude absent
  (owner, 2026-11-27, "Cline alone is the floor"): design is the owner's call; Cline drafts a
  protected file's change as a patch outside the protected paths, and the owner applies it.
- Rule 2 stays in `AGENTS.md`, as SHOULD FOLLOW.
- **The three tenets go in `AGENTS.md`** (owner, 2026-11-24; row 8; its push needs a separate yes).
- **The claim checker is row 5** (owner, 2026-11-24). Model verdicts and checker verdicts are two
  separate value sets. The plan's review prompts keep their own format, separate from row 5's.
- **Guard self-protection and the skill-citation check** (owner, 2026-11-24). The citation check is
  new code in `check-docs.sh`, which today scans only `docs/` and reads no headings.
- **Review rule** (owner, 2026-11-24): a model never reviews its own work. Claude is the default
  reviewer only when Claude was not the author; when Claude wrote it, another model reviews or it
  goes to the owner. Anything touching protected files goes to the owner. With Claude absent,
  Cline's work is reviewed by another model (ChatGPT, Gemini) or the owner, with `check-claims.py`.

## Approvals

Only the owner's own message approves a protected push. An agent restating, summarizing or
drafting an approval is not one: twice in this plan's history a drafted reply was taken for a yes.

## Rows, in build order

**Rows are numbered in build order** (v4.15.0). The "Was" column gives each row's v4.14.0
number; the version history above uses the numbers of its own time.

Each row is its own push, and each needs the owner's go to start. Rows 7, 8, 10, 11 (its `pre-push`
step) and 12 touch protected files and also need the owner's yes per push (**none given**). A later row only adds the
sections it names to a file an earlier row wrote; none rewrites earlier text.

| # | Was | Change | Needs | Who | Done when |
|---|---|---|---|---|---|
| 1 | 1 | **Docs, true today.** `row0.patch` as drafted (four commits: `agent-harnesses.md` + KB index + topic index, `copilot-agents.md` step 2; `copilot-instructions.md` one-line pointer; `GIT_WORKFLOW.md` heavy-Docker dates; `docs/migration/pedestal-0.8.md`), plus: `agent-harnesses.md` gains "Cline in VS Code, tested" and "Models"; `copilot-agents.md` "The prompt" gets the START-HERE sentence (texts below) | nothing | any | `kb lint`, its self-test and `check-docs.sh` pass |
| 2 | 2 | **KB inbox**: `docs/kb/inbox/README.md`, linked from `docs/kb/README.md` (text below) | nothing | Cline | `kb lint`, `check-docs.sh` pass |
| 3 | 8a | **Cline guard core**: `scripts/cline/orcpub-guard.js`, `orcpub-guard-selftest.js`, `replay-commands.json` (row 3 steps 1-5, 7, 8): blocks, per-command approval, parser, self-test, replay, canary, self-protection; installed by hand until row 12 | nothing | Claude Code | row 3 step 8 |
| 4 | new | **Benchmark baseline**: the first run of "Making free models better" item 1, with Cline under row 3's guard (spec below) | row 3 | Claude Code drafts the tasks; Cline runs them | every task has a brief, acceptance criteria and a scoring script; raw outputs kept; model name and harness version recorded per run |
| 5 | 3 | **Claim checker**: `scripts/check-claims.py`, the result format, the brief template, the log (spec below) | nothing | Claude Code | `check-claims.py --self-test` passes; one real Nemotron task's block parses and is checked |
| 6 | 11 | **`BRANCH.md` rewrite** for brevity, losing nothing (spec below) | row 5, the benchmark baseline | Claude Code (absent: Cline drafts, the owner reviews with `check-claims.py`) | every item of the old file is in the new one or a named KB page (row 6's coverage list); startup loading unchanged; `check-docs.sh` passes |
| 7 | 4 | **Skills**: ten `.claude/skills/<name>/SKILL.md` (texts below); new skill-citation check in `check-docs.sh` | rows 1, 2, 5 (they cite the inbox, the corrected heavy-Docker section and the checker) | Claude Code | Cline's Skills tab and Claude Code both list all ten; a Cline task naming `before-commit` loads it and picks the checks for a given file list; the citation check fails on a broken heading and passes on the ten |
| 8 | 5 | **`AGENTS.md`**: the tenets, the two rules, the branch/worktree line, one agent per worktree, the skills index (texts below); the three Claude-only passages move to `CLAUDE.md` (list below); four sections trimmed to their rules, the detail now in row 7's skills (texts below); `DOC-CONVENTIONS.md` import depth 5 → 4 | row 7 | Claude Code | `agent-setup.sh --check` on a code branch shows the new `CLAUDE.md`; `check-docs.sh` passes; every instruction in the removed lines is in the kept text or the named skill (the coverage list below) |
| 9 | 6 | **`scripts/kb-audit.sh`**, the one KB check (spec below) | row 7 (its reminder names a skill) | Cline drafts; Claude reviews (absent: the owner, with `check-claims.py`) | `scripts/kb-audit.sh --self-test` passes all 7 cases |
| 10 | 7 | **`.claude/hooks/kb-*.sh` become wrappers** around row 9 (spec below); delete `.claude/config.json` (added in `3cb53584` as a pointer to the deleted `agents.md`; nothing reads it) | row 9 | Claude Code | replay test passes (spec below) |
| 11 | 8b | **Cline guard guides and hard stop**: row 11 step 6 (skill reminders) and step 9 (`pre-push` hard stop, protected) | rows 7, 9, 3 | Claude Code | row 11 step 8 |
| 12 | 9 | **`agent-setup.sh`**: the root `AGENTS.md` copy carries `BRANCH.md` + handoff; new `scripts/cline/global-rule.md`; `--cline` installs the guard and the global rule and checks the canary; `--check` reports Cline separately (spec below) | row 3 | Claude Code | on a fresh worktree: the copy has all three parts; each canary case prints its message |
| 14 | new | **The generic layer moves to dotfiles** (`codeGlaze/dotfiles`), only after rows 1-13 are done (spec below) | rows 1-13 | Claude Code | a second repo, with only its own policy file, gets the guard, the claim checker, the generic skills and the global rules from one dotfiles install; orcpub works as before |
| 13 | 10 | **Bootstrap.** `START-HERE.md` first paragraph; `agent-harnesses.md` gains "Starting a session" (texts below); the owner copies the global rule once per machine (row 12 keeps it current after) | row 12 | Claude Code drafts; owner installs | a new Cline task in an orcpub worktree, with only the global rule, runs `agent-setup.sh --cline` and reads `BRANCH.md` before other work (owner watches once) |

Dropped from v3.7: row 6 (the Cline test: done, "Tested" above); row 8 (`.clinerules` guard in
pre-commit: no `.clinerules/` folder).

## Row specs and texts

### Row 1: `agent-harnesses.md`, new section

```text
## Cline in VS Code, tested

Cline 4.1.26 in WSL, 2026-11-23. Re-test after a Cline update that mentions hooks, plugins, rules or skills.

- Hook scripts (`.cline/hooks/`, `~/.cline/hooks/`, ...) are listed in the Hooks tab but never run:
  tasks run on the SDK runtime, which does not call them.
- Plugins run, although the plugins page says they do not apply to VS Code yet. A file in
  `~/.cline/plugins/` exporting `{name, manifest: {capabilities: ["hooks"]}, hooks}` gets `beforeRun`,
  `beforeTool` (context keys `snapshot`, `tool`, `toolCall`, `input`) and `afterRun`.
  `{skip: true, reason}` from `beforeTool` refuses the call and the model sees the reason.
- Rules load: the root `AGENTS.md` (also the gitignored copy) and `~/Documents/Cline/Rules/*.md`.
- Skills load from `.claude/skills/` (shared with Claude Code) and `~/.cline/skills/`. The free
  model used a skill only when the task named it.
- Tool names: `run_commands` (`input.commands` is a list), `search_codebase`, `read_files`, `editor`.
- Cline has no command allowlist: auto-approve is per kind of action, and "safe commands" is the
  model's judgement. Switching a planned task to Act mode runs the plan without asking.
- A task's working folder is the VS Code window's folder: open the worktree the task belongs to.
```

`copilot-agents.md`, "The prompt", new setup sentence:

```text
First read `START-HERE.md` on agents/develop (`git show origin/agents/develop:START-HERE.md`) and follow it.
```

Already checked in `row0.patch`: `GIT_WORKFLOW.md` "Heavy Docker checks" reads "`docker-heavy.yml`:
`integration` since #75 (2026-10-26), `develop` since #32 (2026-11-05), so every such PR into either
runs it, older branches included." (Merge dates: `BRANCH.md` lines 22 and 72 at `f732010`.) The Copilot row and `copilot-agents.md` step 2 are as in v3.7.

`agent-harnesses.md` also gains, after "Cline in VS Code, tested":

```text
## Models

What each model is good and bad at here, from tests and the claim log. Rules for every agent stay
in AGENTS.md; nothing here loosens them.

- **Nemotron 3.5 Super VL** (Cline, free). Uses a skill only when the task names it. Follows a
  fixed JSON result format (one trial). Its quotes and line numbers hold up, a few lines off at
  worst; its counts and totals do not: every count must come from a check (`count-and-compare`).
  Reads evidence right more often than it concludes right: its verdicts that change something
  are reviewed.
```

### Row 2: `docs/kb/inbox/README.md`

```text
# KB inbox

Notes from Cline tasks wait here until Claude Code or the owner reviews them.

- One file per finding: `YYYY-MM-DD-<topic>.md`, holding what was found, its evidence (`file:line`,
  a command and its output, or a URL) and the task it came from.
- Add a line for the note to the list below, or `check-docs.sh` fails the note as an orphan.
- Review: a confirmed note moves into the KB page it belongs to (or a new page, indexed in
  `../README.md`); an unconfirmed one is deleted with a one-line reason in the commit message. Either
  way, remove the file and its line here in the same commit.

## Waiting

(none)
```

Index line in `docs/kb/README.md`: `| [inbox/README.md](inbox/README.md) | Notes from Cline tasks waiting for review |`.

### Rows 3 and 11: Cline guard plugin

Row 3 builds steps 1-5, 7 and 8; row 11 adds steps 6 and 9.

Source on `agents/develop`: `scripts/cline/orcpub-guard.js` (CommonJS, Node built-ins only: `fs`,
`os`, `path`, `child_process`; the command list lives in it, since its rules are command shapes,
not prefixes), `scripts/cline/orcpub-guard-selftest.js` (the self-test's case table) and
`scripts/cline/replay-commands.json` (step 5, "Replay"). Installed to `~/.cline/plugins/orcpub-guard.js`, data to `~/.cline/orcpub-guard/` (by row 12; by
hand while building this row).

1. **Capture first.** Log one task's `beforeRun` and `beforeTool` contexts in full to find where the
   tool name and the session's folder live (fact 3). Record them in `agent-harnesses.md`, new
   section "Cline plugin payloads". Build only on those fields. Read from 4.1.28's code
   (2026-11-27): the tool name is `tool.name`; the context has no folder, so the guard reads `cwd`
   from Cline's session file for `snapshot.conversationId`, else the workspace a plugin's `setup`
   receives; it appends what it used to `~/.cline/orcpub-guard/capture.log`.
   **Tools covered:** `run_commands` (a string, a list or `{commands}`) and `bash` (`{command}`)
   are judged as commands; `editor`, `edit`, `write` and `apply_patch` as writes, their paths taken
   from any path key and from patch text (`*** Update File: <path>`). A write whose target cannot be
   read is refused.
2. **Scope.** Acts only when the session folder is a checkout whose `origin` is codeGlaze/orcpub;
   elsewhere it does nothing. **Folder claim:** it refuses edits and commits in a worktree whose
   folder name holds `claude` or `copilot` as a dash-separated word (`orcpub-claude-kits`), and
   every `git` command that writes there (`add`, `commit`, `merge`, `push`, `rebase`, `switch`): that
   folder is another harness's task. Any other folder, the main checkout included, is open to it,
   so Cline alone works anywhere.
3. **Canary.** `beforeRun` writes `~/.cline/orcpub-guard/alive`: `<epoch> <sha256 of the plugin file>
   <commands-auto-approve on|off>`, the last read from `autoApprovalSettings` in
   `~/.cline/data/globalState.json`. With it on, nothing asks (fact 12): the guard still blocks, but
   every other command runs; row 12 reports it.
4. **Block** (`skip` + a reason naming the rule) any command that: is a git command using
   `--no-verify`, `commit -n`, `-c core.hooksPath` or `git config core.hooksPath` (the same words in
   echoed text or a grep pattern are not blocked); pushes to `develop`,
   `integration` or `main`, or to a remote other than `origin`; deletes a branch (`branch -D`,
   `push --delete`, `push origin :x`); runs `git reset --hard`, `git clean`, `git checkout .`,
   `git checkout -- .`, `git stash`, `git -c`; stages anything but named files (one agent per
   worktree, row 8): `git add` with `-A`, `--all`, `-u`, `--update`, `.`, `./`, any pathspec
   starting with `:` (pathspec magic: `:/`, `':!AGENTS.md'` stages everything else), a glob or a
   folder, `git commit` with `-a`, `--all` or a short-flag cluster holding `a` (`-am`); runs any `gh` command except these reads: `gh pr view|diff|list|status|checks`,
   `gh issue view|list`, `gh run view|list`, and `gh api` with none of `-X`, `--method`, `-f`, `-F`,
   `--field`, `--raw-field`, `--input` in any spelling (`-XPOST`, `--method=post`) (reason: "posting
   to GitHub needs the owner's yes; draft it and tell the owner"). Default-deny: a `gh` write the
   list did not foresee is blocked too.
   Block writes (any of the four write tools) to `AGENTS.md`, `CLAUDE.md`, `BRANCH.md`, `.claude/`, `.githooks/`,
   `scripts/agent-setup.sh` (they change what every agent loads; reason: "a protected file needs the
   owner's yes; tell the owner"), and any write under `.git/` (git config can make a read
   command run a program: `core.fsmonitor` runs on `git status`).
   Every block here is checked twice: on a quote-aware loose split of the whole text (quoted text
   stays one word; the body of every `$( )` or backtick, even inside double quotes, is checked as a
   command), and on the words bash will run (`git add --a\ll` is `git add --all`).
   **A block is never approved through Cline.** No dialog, file or setting turns a block into run:
   the owner runs a blocked command in their own terminal, or applies Cline's drafted patch to a
   protected file (closed decisions). Cline's reason text says which.
   **Self-protection:** block any command or write that names `~/.cline/plugins/` or
   `~/.cline/orcpub-guard/`, the installed guard (the repo's `scripts/cline/` source is ordinary
   code) (reason: "the guard's own files
   are the owner's; tell the owner"). The off switch is the owner renaming the plugin file;
   `--check` and `--cline` then report the guard as missing (row 12).
   **Tripwire:** before any `git commit` or `git push`, run `git status --porcelain` on the
   protected paths above; if any changed, block ("a protected file changed outside the editor; tell
   the owner"). It catches a write by any route the lists miss: a redirect, a script, a test.
5. **Run, or leave it to Cline's dialog** (facts 12, 13). Cline's global command auto-approve is
   off. The guard returns `{policy: {autoApprove: true}}` only for a command that passes every
   check below; for any other it returns nothing, and Cline shows its Approve dialog, which covers
   that one command. It never returns `autoApprove: false` (it changes nothing, fact 12). Step 4's
   blocks are checked first (both ways, step 4). Every check below applies to the words bash will
   run, never to the raw text alone.
   **Parsing is default-deny:** a part the parser does not fully understand makes the whole
   command ask. Nothing partly parsed runs.
   - Split only at `&&`, `||`, `;`, `|` and a newline outside quotes. Every part must pass on its own.
   - Ask on: a lone `&`; `(`, `)`, `{`, `}`; `` ` ``, `$(`, `<(`, `>(`; `$NAME` or `${…}`
     outside single quotes; a here-doc; `for`, `while`, `until`, `do`, `done`, `if`, `then`,
     `fi`, `case`; a leading assignment (`A=x cmd`); an unterminated quote or trailing `\`; a
     `\` outside quotes. Two exceptions: `$?` (always a number) and, inside double quotes, `\` as
     bash reads it (it escapes only `$`, `` ` ``, `"`, `\` and newline); an unquoted `*`, `?` or `[` (a file named `--output=out` turned
     `sort *` into a write, review of v4.12.0).
   - Redirects: `n>&m` duplications; writes (`>`, `>>`) only to `/dev/null` or under `/tmp/` (no
     `..`); reads (`<`) from anywhere. A here-doc or process substitution asks.
   - `cd <path>` and `git -C <path>` may go anywhere for reads (no `~`, no variable). **Writes stay
     at home:** `git add`, `git commit`, `git fetch --prune` and the runners run unasked only when
     the folder is surely the session folder or under it, reached by `&&` from the start; a `cd`
     after `;`, `||` or a newline makes later writes ask, and `cd` in a pipe asks.
   - The right side of a pipe must be a filter: `head`, `tail`, `grep`, `rg`, `wc`, `sort`,
     `uniq`, `cut`, `tr`, `cat`, `sed -n`, each as limited below. A pipe into anything else asks.
   - Flags that write or run a program ask, in any spelling (`-o`, `-oX`, `--output`,
     `--output=X`): `sort` `-o`, `--output`, `--compress-program`; `rg` `--pre`, `--pre-glob`;
     any git command's `--output`, `--ext-diff`, `--textconv`; `git grep` `-O`,
     `--open-files-in-pager`.
   - `sed -n` runs only with print scripts: `Np`, `N,Mp`, `/re/p`, `/re/,/re/p`, `$p`. Any other
     script asks (the `s///e` flag runs a shell command; `w`, `W`, `r`, `R`, `e` read, write or run).
   **The list** (in the guard; flags named here as asking override the command):
   - **Git config check first:** before any git command runs unasked, the guard reads
     `git config --list`; if any key that runs a program is set (`core.fsmonitor`, `core.pager`,
     `pager.*`, `diff.external`, `diff.*.command`, `*.textconv`, `filter.*`, `alias.*`,
     `core.sshCommand`), the command asks and the reason names the key.
   - Read-only git, optionally after `git --no-pager` or `git -C <path>`: `git status`, `git diff`,
     `git log`, `git show`, `git grep`, `git ls-files`, `git ls-tree`, `git rev-parse`,
     `git merge-base`, `git cat-file`, `git blame`, `git shortlog`, `git describe`,
     `git check-ignore`, `git symbolic-ref` with at most one name, `git reflog` (alone or `show`), `git branch`
     with only `--show-current`, `--list`, `-a`, `-r`, `-v`, `-vv`, `--contains`, `--merged`,
     `--no-merged` and a pattern; `git remote -v`, `git remote get-url`; `git worktree list`;
     `git ls-remote origin` (an intended network read, origin only); `git fetch`, `git fetch origin`
     and `git fetch origin <branch>`, with `--prune` (`-p`) or `-q` allowed, `--prune` only at home
     (`AGENTS.md` tells every agent to run `git fetch --prune origin`); `--all`, `--prune-tags`,
     `--force`, another remote and `:` refspecs ask.
   - Git writes: `git add <named files>` and `git commit` (step 4 blocks the other forms), and
     the own-branch push below.
   - Files and text: `ls`, `pwd`, `cat`, `head`, `tail`, `wc`, `grep`, `rg`, `find` (no `-exec`,
     `-execdir`, `-ok`, `-okdir`, `-delete`, `-fprint`, `-fprint0`, `-fprintf`, `-fls`),
     `sed -n` (as for filters), `sort` (as for filters), `uniq`, `cut`, `tr`, `diff`, `stat`, `file`,
     `echo`, `printf`, `true`, `test`, `sleep <n>`, `date` (no `-s`), `which`, `uniq` with at most
     one file (a second is its output), `file` (no `-C`); `mkdir` under `/tmp/` only.
   - `timeout <seconds>` before a listed command, judged as that command.
   - Runners: `python3 scripts/clj-grep.py`, `lein test`, `lein lint`,
     `lein trampoline with-profile lint`, `bash docs/kb/tools/topic-index.sh`,
     `python3 docs/kb/tools/kb.py`, `bash scripts/check-docs.sh`, at home only. A runner asks
     instead when `git status --porcelain` shows its script changed. Any `lein` asks while
     another `lein` process runs in the same checkout (`AGENTS.md`: one per worktree).
   - The plan's setup commands, as one whole command, never split:
     `git show origin/agents/develop:scripts/<agent-setup.sh|kb-audit.sh|line-counts.sh> | bash`
     followed by nothing, `-s -- --check` or `-s -- --cline` (`agent-setup.sh`), `-s -- --push`
     (`kb-audit.sh`), or `-s -- <ref>` / `-s -- <ref> <ref>` (`line-counts.sh`; a ref holds only
     letters, digits and `._/-`). Anything else in that shape asks.
   **Replay** (`scripts/cline/replay-commands.json`, run by the self-test): the 118 commands of
   fact 13, exactly as Cline sent them, each with the expected decision and reason code (`run`,
   `ask:<rule>`, `block:<rule>`), set by hand when the file is made. It includes the `sed -n`
   command fact 13 blocked by mistake (expected: not block). The self-test fails on any changed
   decision or reason. Before its commit, the file passes the personal-data scan and is read
   through for anything private. Commands from later Cline tasks, building tasks first, are added.
   **Acceptance:** every self-test case gives its listed result, the replay gives each command its
   recorded decision with no block the file does not expect, and the ask rate is reported against
   the target of 10% (11 of 118). The target is never reached by letting run a part the parser
   does not fully understand, or by weakening a block: the safety cases win over the rate.
   **Measured 2026-11-27** (the build in `orcpub-claude-guard`): 90 run, 27 ask (22.9%), 1 block
   (a here-doc writing text that names the guard's folder, expected). The asks left are loops,
   `$VAR`, subshells, here-docs, `env`, and writes or runners outside the session folder.
   **Own-branch push runs** (owner, 2026-11-27): exactly `git push [-u] origin <current branch>`
   or `git push [-u] origin HEAD:<current branch>`, and in a detached worktree exactly
   `git push origin HEAD:agents/develop` (the `GIT_WORKFLOW.md` docs-worktree form; the only `:`
   refspec allowed besides `HEAD:<current branch>`), run without asking when: the current branch is
   not `develop`, `integration` or `main`; the tripwire is clear; `git config --get-all
   remote.origin.push` prints nothing; `git remote get-url --push origin` is codeGlaze/orcpub; and
   no other word follows (no `--force`, `-f`, `--force-with-lease`, `--mirror`, `--all`, `--tags`,
   `+`). Every other push asks, bare `git push` included (its target comes from config). Step 4's
   push blocks still apply, and a bare `git push` is judged by the current branch: on `develop`,
   `integration` or `main` it blocks. **In row 3 this push asks too:** it runs unasked only once row 5
   exists, because the diff review before a push uses `check-claims.py`.
   **Self-test** (`node scripts/cline/orcpub-guard.js --self-test`): a table of commands and the
   expected result (run / ask / block), holding at least every bypass the v4.6.0 reviewers gave:
   `gh pr close 12`, `gh pr ready 12`, `gh gist create f`, `gh workflow run x`, `gh api -XPOST …`,
   `gh api --method=post …`, `gh api … --raw-field body=hi`, `gh extension exec …`, `git status`
   newline `curl -X POST …`, `git status & curl …`, `cat <(curl …)`, `(curl …)`, `cat x > AGENTS.md`,
   `env curl …`, `bash -c '…'`, `git -c alias.x='!curl' x`, `git add -A`; and the v4.7.0
   reviewer's: `git status; curl …`, `git status | curl …`, `git status || curl …`,
   ``git status `curl …` ``, `git status $(curl …)` (ask); the three setup commands as written (run)
   and near misses (ask): `… | bash; curl …`, `… | bash -s -- --cline && curl …`,
   `git show origin/agents/develop:scripts/other.sh | bash`; `git add --all`, `git add -u`,
   `git add ./`, `git add :/`, `git commit --all`, `git commit -am x` (block);
   `python3 scripts/clj-grep.py` with that script changed (ask); `git commit` with `AGENTS.md`
   changed (block); on branch `fix/x`: `git push -u origin fix/x` (run), `git push origin fix/y`,
   `git push --force`, `git push origin +fix/x` (ask); on `integration`: `git push` (block);
   detached: `git push origin HEAD:agents/develop` (run), `git push origin HEAD:fix/y` (ask);
   `gh pr create …`, `gh pr merge 12` and an `editor` write to `AGENTS.md` (block);
   in a folder `orcpub-claude-x`: an `editor`
   write and `git push origin HEAD:feature/kits` (block); in `orcpub`: an `editor` write (run);
   on `fix/x`: bare `git push` (ask); with `remote.origin.push` set: `git push origin fix/x` (ask);
   with origin's push URL changed: `git push origin fix/x` (ask).
   The v4.12.0 parser: `cd <session folder> && git status`, `git log --oneline | head -5`,
   `grep -rn x src 2>/dev/null | wc -l`, `timeout 30 git fetch origin`,
   `git -C <session folder> rev-parse HEAD`, `sed -n '1,20p' f`, `cd /tmp && git status`,
   `cd .. && git status` (run); `cd /tmp && git add a.txt`, `git log > out.txt`, `git log 2>out.txt`, `for f in *; do cat $f; done`,
   `A=1 git status`, `git status || curl …`, `git log | sh`, `git log | sed 'w AGENTS.md'`,
   `sed -n 'e curl …' f`, `sort -o AGENTS.md x`, `find . -delete`, `find . -exec rm {} +`,
   `sed -i 's/a/b/' f`, `echo $(curl …)`, `echo "$HOME"`, `git status 'unterminated` (ask);
   `cd src && git add -A`, `git status && git push origin develop`,
   `git log | head; git stash` (block).
   The v4.12.0 review's and Claude's (each reproduced in a throwaway repo, 2026-11-27):
   `git diff --output=AGENTS.md`, `git log --output=x -1`, `git diff --ext-diff`,
   `git log --textconv`, `git grep -O x`, `rg --pre cat x`, `sort --output=AGENTS.md x`,
   `sort --compress-program=sh x`, `sort *`, `sed -n 's|.*|touch x|e' f`,
   `git fetch --all --prune --prune-tags`, `git ls-remote https://example.com/x`; any git command
   with `core.fsmonitor` or `diff.external` set (ask); `git add ':!AGENTS.md'`, `git add --a\ll`,
   an `editor` write to `.git/config` (block); `git fetch origin main`, `git ls-remote origin`,
   `sed -n '/defn/,/^$/p' f` (run); in row 3, on `fix/x`: `git push -u origin fix/x` (ask).
   **Limit:** code an allowlisted runner executes (`lein test` runs test files Cline writes) is not
   inspected. The tripwire and the diff review before a push (Claude's, or the owner's with
   `check-claims.py` when Cline works alone) catch what it changes, not a
   network call it makes. The guard catches a cooperative model's mistakes; it is not a sandbox.
6. **Guide** (`appendContext`, the command still runs): `git merge` → "Use the merge-integration
   skill."; `git commit` → "Use the before-commit skill." (and "the fail-first-test skill" when a
   staged file is under `test/`); `git push` → the output of
   `git show origin/agents/develop:scripts/kb-audit.sh | bash -s -- --push` run in the session
   folder, then "If this push touches deployment files: the heavy-docker skill."; `editor` on a
   big file (the move-big-file-code list) → "Use the move-big-file-code skill."; `editor` under
   `scripts/srd/` or on SRD data → "Use the srd-data skill."; `grep`/`rg` over
   `.clj`/`.cljc`/`.cljs` → "Use `scripts/clj-grep.py`: plain grep counts `#_`-discarded code as live."
7. **Fail closed for git.** An error inside the plugin blocks a `git` or `gh` command with "orcpub
   guard error: <message>; tell the owner" and lets others through (a throwing hook is ignored by
   default, so the guard must not throw).
8. **Done when, row 3 (steps 1-5 and 7):** the self-test passes, the replay meets step 5's
   acceptance, and one Cline task (global command auto-approve off) shows: an allowlisted chain
   runs with no dialog; a non-listed command shows Cline's dialog, and approving it does not
   approve the next one; each block case in 4 refuses with its reason; the folder claim refuses an
   `orcpub-claude-*` folder; the canary file updates and records the auto-approve setting; the
   capture log names the session folder. The demo runs in a throwaway worktree (`orcpub-demo`,
   detached at `origin/agents/develop`), where a write to `AGENTS.md` is refused and the file is
   left unchanged; the guard and the worktree are removed afterwards.
   **Done when, row 11 (steps 6 and 9):** each guide case in 6 shows its line in one Cline task, and
   step 9's runtime test passes.
9. **`pre-push` hard stop** (decision 4, owner, 2026-11-24; `.githooks/pre-push` is protected, so its
   push needs the owner's yes). When `CLINE_ACTIVE` is set, refuse the push if
   `~/.cline/orcpub-guard/alive` is missing or over 10 minutes old: "Cline's guard is not running;
   tell the owner". When `CLINE_ACTIVE` is unset, do nothing new: a push from any other shell is never
   blocked because the guard is down. Source for the variable: fact 10 (the owner also cites
   `VscodeTerminalRegistry.ts`, `TerminalRegistry.createTerminal()`).
   **Operational only after a runtime test:** a Cline task runs `git push` in a Cline terminal against
   a test remote, once with a fresh `alive` and once with it removed; the hook must see
   `CLINE_ACTIVE` and refuse only the second. Also push from Claude Code's shell with no `alive` file:
   it must pass. **If the variable does not reach the hook, stop and report;** do not substitute
   another way to detect Cline.

### Row 4: benchmark baseline

The first run of "Making free models better" item 1: 5 to 10 real tasks, each with a brief
(row 5's template), acceptance criteria and a scoring script; Cline runs each under row 3's guard,
global command auto-approve off. Kept as `docs/kb/benchmark/` on `agents/develop`: the tasks, the
raw outputs, and per run the model name, harness version, pass or fail, rule violations and tool
errors. Unsupported claims are scored by row 5 once it exists.

### Row 5: claim checker

`scripts/check-claims.py` on agents/develop: Python standard library only, read-only, run with
`git show origin/agents/develop:scripts/check-claims.py | python3 -I - <args>`. Two uses: a
checker for a result block (`check-claims.py check <file>`), and a counting tool for agents
(`check-claims.py <check> <args>` prints the answer). Not protected.

**Two value sets, never mixed.** `model_verdict` is what the agent says: for `kind: fact`,
`CONFIRMED`, `WRONG` or `COULD_NOT_CHECK`; for `kind: judgement`, `OK`, `SHOULD_CHANGE` or
`BLOCKS`. `checker_verdict` is what the script finds: `VERIFIED`, `NEAR_MATCH`, `MISMATCH`,
`NOT_FOUND`, `UNCITED` or `NEEDS_REVIEW`. The plan's own review prompts ("Verification prompts")
keep their table format; this format is for task results.

Result format: the agent ends every task with one fenced `json` block:

```json
{
  "task": "one line: what was asked",
  "outcome": "DONE | BLOCKED | INSUFFICIENT_EVIDENCE",
  "blocker": "required when outcome is BLOCKED, else omitted",
  "rev": "the commit the evidence was read at (git rev-parse HEAD or the ref named in the brief)",
  "criteria": [
    {"id": "A1", "text": "the acceptance criterion, copied from the brief",
     "met": true, "evidence": [ <check> ]}
  ],
  "claims": [
    {"id": "C1", "text": "the claim, one sentence",
     "kind": "fact | judgement",
     "model_verdict": "fact: CONFIRMED | WRONG | COULD_NOT_CHECK; judgement: OK | SHOULD_CHANGE | BLOCKS",
     "evidence": [ <check> ]}
  ]
}
```

A `<check>` is one of five built-in checks, never a command:

| check | fields | passes when |
|---|---|---|
| `quote` | `path`, `line`, `text` | `text` is at `line` (see matching) |
| `lines` | `path`, `value` | the file has `value` lines |
| `count` | `path`, `pattern`, `value` | the fixed string occurs on `value` lines |
| `heading` | `path`, `text` | a `#` heading line equals `text` |
| `exists` | `path`, `value` (true/false) | the path exists or not, as stated |

Every check reads `git show <rev>:<path>`. A check may add `"where": "worktree"` to read the file
on disk instead (uncommitted work, or a file outside the repo by absolute path); see dirty
worktree, below.

The checker writes its report as JSON, then a short table for people:

```json
{
  "rev": "f732010...", "dirty": true, "uncommitted": ["path", "..."],
  "verification": "PASS | REVIEW",
  "task": "DONE | BLOCKED | INSUFFICIENT_EVIDENCE (as reported) + criteria_rerun: PASS | FAIL | NOT_RUN",
  "claims": [{"id": "C1", "model_verdict": "CONFIRMED", "checker_verdict": "VERIFIED",
              "found_at": [42], "worktree": false}],
  "totals": {"VERIFIED": 0, "NEAR_MATCH": 0, "MISMATCH": 0, "NOT_FOUND": 0, "UNCITED": 0, "NEEDS_REVIEW": 0}
}
```

**Checker verdicts**, one per claim, worst of its checks:

- `VERIFIED`: every check passed at its cited place.
- `NEAR_MATCH`: a quote is in the file, but not at the cited line; `found_at` lists every line.
  Never promoted to VERIFIED.
- `MISMATCH`: a check ran and the file says otherwise (wrong count, part of a quote present).
- `NOT_FOUND`: the path, heading or quote is not there at all.
- `UNCITED`: no evidence. For `COULD_NOT_CHECK` this is the honest answer and is listed, not
  counted as a failure.
- `NEEDS_REVIEW`: the evidence may hold, but the claim needs judgement. Always for `kind:
  judgement` other than `OK`, and for `model_verdict: WRONG` (both would change something).

**Matching:** both sides have whitespace runs collapsed to one space and ends trimmed. Contained in
the cited line: VERIFIED (a quote may be part of a line). Contained elsewhere: NEAR_MATCH. A quote over several lines is matched
against the same number of lines starting at the cited one.

**Dirty worktree:** a dirty tree invalidates nothing by itself. Evidence read at `rev` is checked
at `rev`, whatever the disk holds. Evidence marked `"where": "worktree"` is checked on disk, and
its claims are listed apart as uncommitted; they count as VERIFIED for the quote, but the work
itself is proven only by the task's criteria (tests, and CI after the commit). The report records
`dirty` and the list of uncommitted files, so a code task shows its committed baseline and its
uncommitted changes separately.

**Two outcomes, kept apart:** `verification` says whether the evidence held (PASS when no claim
is MISMATCH, NOT_FOUND or NEEDS_REVIEW). `task` says whether the work was done: Cline's
`outcome`, plus `criteria_rerun` when the reviewer reran the criteria. A clean verification never
marks a task done.

**Review rule** (closed decisions): never the author's own model; Claude only when Claude was not
the author, else another model or the owner; the owner for protected files. The reviewer reads
every claim that is not VERIFIED or UNCITED-by-COULD_NOT_CHECK, every NEAR_MATCH whose line number
feeds an edit, and every criterion.

**The checks are also Cline's counting tool.** `check-claims.py count <path> <pattern>`,
`lines <path>`, `quote <path> <line>` and `heading <path>` print their answer, so Cline gets a
number from the script while it works instead of counting by eye; the same check then proves it.
The `count-and-compare` skill (row 7) says so; the brief template names it.

**Brief template:** "Working rules for free models" below carries the outcome wanted, acceptance
criteria, "analysis tasks change no files", "a blocker: report BLOCKED", the counting rule and
this format.

**Log:** one JSON line per run in `~/.cline/orcpub-guard/claims.jsonl` (local, not in git):
date, model, task type (free text), the totals, the task outcome, review result when known. No
routing and no analysis until about 20 runs.

**Done when:** `check-claims.py --self-test` passes one case per checker verdict plus a
dirty-tree case; one real Nemotron task produces a block the checker parses.

### Row 6: `BRANCH.md` rewrite

On `agents/develop`. Shorter and easier to follow, losing nothing (owner, 2026-11-27).

1. **Coverage list first:** every item in today's `BRANCH.md` (rule, state line, decision,
   pointer), one per line, with where it goes: kept, merged with a named duplicate (in `AGENTS.md`,
   a handoff or a skill), or moved to a named KB page (history and finished items, as
   `branch-md-2026-10-23.md` was). Nothing is marked dropped without the owner's yes.
2. **Rewrite** to the coverage list. Owner decisions keep their date and wording.
3. **Startup loading stays as it is** (decision A). What Cline loads at start is decided later,
   from the benchmark ("Making free models better", 6), not in this row.
4. `check-claims.py` checks the coverage list against both versions.

### Row 7: skills

Ten skills. Each file is `.claude/skills/<name>/SKILL.md`, written exactly as below. A body reads its doc
section with `git show origin/agents/develop:<path>` (code branches may hold older copies of docs).

```text
---
name: merge-integration
description: Use when merging integration, or any trunk, into a branch, and when a merge stops with conflicts.
---
Steps: docs/GIT_WORKFLOW.md, "Merging integration in". Read it now and follow it.

- Always `git merge --no-commit --no-ff`: a clean merge otherwise commits itself, unchecked.
- `docs/branch-changelog.md` conflicts or changes: keep this branch's file. After the merge,
  check `head -1 docs/branch-changelog.md` is still this branch's.
- On agents/develop, keep the `.gitignore` exception for `.claude/`.
- Before `git commit`: the full `lein lint` and the "Before committing" checks (the before-commit
  skill). One fails: do not commit; report it in Needs you.
- The docstring hook checks only functions new to both sides, and any you write while resolving.
```

```text
---
name: open-pr
description: Use when opening a pull request, asking for a review, or answering review comments.
---
Rules: AGENTS.md, "PRs and releases". Steps: docs/GIT_WORKFLOW.md, "A piece of work, start to
finish" and "CodeRabbit". Read them now.

- Open it only when the owner says. Base: `integration`; a refactor leaf goes into `refactor/main`.
- Before opening: docs/TESTING.md, "Before a PR", including the What's New item for a user-facing
  feature; the heavy-docker skill if the diff touches deployment files.
- The description names the agents/develop commit that carries its docs.
- Ask CodeRabbit once: `@coderabbitai full review`; batch later behaviour changes into one more ask.
- Review fixes: push them in the same turn, then reply on each thread naming the fixing commit.
- Never merge unless the owner says to merge this PR. Never post upstream (Orcpub/orcpub).
```

```text
---
name: fold-changelog
description: Use when starting a branch, writing a line in docs/branch-changelog.md, and when the owner says a PR is about to merge.
---
Rule: AGENTS.md, "Changelogs". The steps are here.

- At the branch's start: copy docs/branch-changelog.template.md to docs/branch-changelog.md; fill
  it in as work lands.
- One line per outcome a player or maintainer would notice ("New sign-up, sign-in and
  account-recovery pages"), never one per commit.
- Fixed: only bugs that existed before this branch. A fix to something this branch or release
  built gets no line. Tests, CI, refactors and tooling get none.
- Neither new nor a pre-existing bug: Changed.
- Sections: Added / Changed / Fixed / Security. Each line opens with its area in bold
  (`**Accounts:**`) and cites the upstream issue (#N) if one matches; no commit hashes.
- It stays unfolded while the PR is reviewed: the fold drops the reviewer-only section.
- Upstream issues (`docs/kb/upstream-issues.md`): a bug or feature that reached upstream `develop`
  or a dev-site build (the DMV pre-merge) gets an issue on `Orcpub/orcpub`; one made and fixed
  between builds gets none. One problem or feature is one issue; related but different is a new
  one; small polish goes in the area's umbrella issue. At the fold, draft an issue for every line
  that needs one and lacks it; post each only with the owner's yes.
- Fold only when the owner says the PR is about to merge: `scripts/fold-branch-changelog.sh "<release>"`,
  commit, push, then add the `ready to merge` label. The "Branch changelog folded" check is
  skipped until the label is on, then passes only once the fold is done.
```

```text
---
name: heavy-docker
description: Use before pushing a branch whose changes touch Docker, compose, deploy, run, env or start scripts, or the crash and alert code.
---
Steps: docs/GIT_WORKFLOW.md, "Heavy Docker checks". Rule: AGENTS.md, "Tests", "Deployment changes".

- The files that count: the `paths:` list in `.github/workflows/docker-heavy.yml` (Docker, compose,
  deploy, `run`, the swarm, release, env and start scripts, the crash and alert code). Compare it
  with `git diff --name-only origin/integration...HEAD`.
- None listed: push as usual.
- Listed: the checks (about 20 minutes) run at landmark pushes only. From the first push that
  brings a listed file into a PR, the PR carries the label `heavy-docker: later`, and ordinary
  pushes go out without asking:
  `gh api -X POST repos/codeGlaze/orcpub/issues/<PR>/labels -f 'labels[]=heavy-docker: later'`.
- At a landmark, remove the label (which starts a run): before asking for review, after a push
  that changes a listed file itself, and before merge. Put it back for the pushes after.
- Cline: its guard refuses label changes; say which landmark is due and ask the owner.
- No open PR: a push starts no CI at all.
- Before asking for review of such a PR, its heavy Docker run must be green.
```

```text
---
name: fail-first-test
description: Use when adding or changing a test, before committing it.
---
Commands: docs/TESTING.md, "Seeing a test fail". Rule: AGENTS.md, "Tests". Read the commands now.

- Commit the code under test first, without the test.
- For each claim, break only the files (or hunk) it depends on, back to the source before that
  commit; for code committed earlier, hand-edit that path.
- Never break a file with uncommitted changes: the restore overwrites it for good.
- See the expected checks fail, and only those. A break that fails nothing means the test is weak:
  strengthen the test, never weaken the break.
- Several claims: one break each, batched into one build and run.
- Restore only the files you broke, never a whole directory; never commit a break. Then run the
  test green.
- The failing output goes in the test's commit message and in the PR, beside the pass.
```

```text
---
name: before-commit
description: Use before every commit on a code branch.
---
Steps: docs/TESTING.md, "Before committing". Read its table now.

- Pick the rows from the files you changed (`git diff --name-only --cached`); run each once.
- Lint only the changed Clojure files while working; the full `lein lint` before a merge commit,
  a PR, or marking a draft ready.
- Never two lein processes in one worktree. Stop a running server first.
- A check fails: do not commit. Find why; report it in Needs you if it is not yours to fix.
- A test you added or changed: the fail-first-test skill first.
```

```text
---
name: write-agents-develop
description: Use when writing a KB page, a doc fix or a branch handoff, or moving KB pages off a code branch.
---
Steps: docs/GIT_WORKFLOW.md, "Writing to agents/develop". Read it now.

- From a separate worktree of agents/develop, one per code branch; never switch the code branch's worktree.
- Cline: write findings to docs/kb/inbox/ (its README), not straight into KB pages.
- Index every new page in docs/kb/README.md; run `bash docs/kb/tools/topic-index.sh` before committing.
- A push changing AGENTS.md, CLAUDE.md, .githooks/, scripts/agent-setup.sh or .claude/ needs the
  owner's yes first.
```

```text
---
name: srd-data
description: Use when editing SRD data: open5e fixtures, generated SRD files, or anything under scripts/srd/.
---
Rules: AGENTS.md, "SRD text is verbatim". Why: docs/kb/open5e-upstream-notes.md, "SRD data checks".

- Use only wording the SRD uses: no paraphrase, no "clearer" rewording. A fix touches only what
  is broken.
- A field with no SRD sentence of its own (a casting option's `desc`): the SRD sentence it comes
  from, changing only the slot number.
- Our own summaries go in a separate field.
- Edit open5e records only with scripts/srd/open5e_records.py.
- Before committing, run scripts/srd/check-srd-data.py (on agents/develop; about a second); the
  open5e clone's pre-push hook runs it too. A new kind of defect: add its rule first and see it fail.
```

```text
---
name: move-big-file-code
description: Use when adding a feature (a page, a builder panel, a route group), or changing code in views.cljs, events.cljs, styles/core.clj, options.cljc, pdf.clj, character_builder.cljs or routes.clj.
---
Rule: AGENTS.md, "Shrinking the big files". Plans: docs/kb/monolith-decomposition-plan.md,
docs/kb/class-builder-extraction-plan.md.

- A new feature goes in its own namespace; the big file gets only the wiring (a require, a route
  entry, a call).
- Where: a folder that already holds its kind. Server handlers by domain in src/clj/orcpub/routes/,
  client screens in views/<domain>.cljs, events in events/<domain>.cljs. Reuse a namespace that
  already holds the family before adding one; count only new files when weighing a plan.
- A namespace holds a concept family (`inventory`, with kits inside it), not one small concept.
- Similar things (forms, routes, rules of one kind) call one higher-order function and pass their
  differences as options. Tests walk that data table, plus one pin test of required rows (a
  table-driven test cannot see a deleted row). Example: docs/kb/auth-abuse-limits.md.
- Reworking a self-contained group in a big file: move it out with the work. A one-line fix does
  not trigger a move. Small (one group, callers updated in the same commit): same branch, its own
  commit, no behaviour change. Anything bigger, or any doubt: ask the owner first, naming a branch;
  a big move left in a long-lived branch races the mainline and dies.
- A fix can stay in place when moving its code out would be a big move of its own.
- Don't move code you are not otherwise touching, code an open PR is changing in the same file, or
  a group tangled with half the file: note it as a candidate in the branch handoff.
- A move changes nothing else: same names, same re-frame event and sub ids, callers updated, tests
  green before and after. A moved function without a docstring gets one in the move commit.
- Data files are exempt: monsters.cljc, spells.cljc, magic_items.cljc, classes.cljc.
- Report what you moved, with the line-count table from
  `git show origin/agents/develop:scripts/line-counts.sh | bash -s -- <base>`; never count by hand.
  Code that could move and did not: ask in Needs you (what it is, where it would go, why).
```

```text
---
name: count-and-compare
description: Use whenever an answer contains a number, a total, a line number, or a list compared with another list.
---
Never count, add up or compare lists by reading. Run the check and use its answer.

- How many lines: `check-claims.py lines <path>`. How many lines hold a string:
  `check-claims.py count <path> <string>`. Is a quote at a line: `check-claims.py quote <path> <line> <text>`.
- Run it through git: `git show origin/agents/develop:scripts/check-claims.py | python3 -I - <check> ...`.
- Put the same check in the claim's evidence, with the value it printed.
- A total over several checks: leave it out. The checker adds totals up.
- Two lists to compare: give each as its own claim with its own check; say which items differ.
```

**New code in `scripts/check-docs.sh`** (not protected; it scans only `docs/` today and reads no
headings): a fourth section, "skill citations". For each `.claude/skills/*/SKILL.md`, every
`docs/...` path it names must exist, and every heading quoted right after a path
(`docs/X.md, "Heading"`) must equal a `#` heading line's text in that file, read from
`origin/agents/develop` when the skill says `git show origin/agents/develop:`, else from the
working tree. A miss prints `SKILL CITE  <skill>: <path>, "<heading>"` and fails like a dangling link.

### Row 8: `AGENTS.md`

New section, before the first rule (after the opening paragraphs), verbatim:

```text
## Three tenets — every rule below serves one

- **Reliability:** your work can be trusted. Claims cite evidence; something other than you
  checks the result; say what you could not check instead of guessing.
- **Efficiency:** the least work and context for the result. Use a script, not judgement, for
  anything mechanical; load what the task needs; stop when done or blocked.
- **Efficacy:** the work does what was asked. Name the acceptance criteria, show each one met,
  and raise a blocker as soon as you hit it.
When two pull apart, say which you traded and why.
```

New rules, verbatim:

**Check before you plan — MUST FOLLOW** (owner, 2026-11-22). A plan built on a guess fails
at the guess, after the owner approved it. Before proposing a plan, design or
recommendation, check every fact it rests on that you can check here (the repo, the tool's
current docs, a command), and list those facts in the plan with their sources. Never defer
a check you could make now. Another agent's claim, a summary or your own memory is
unchecked until you check it, whether you would confirm it or dismiss it. Mark what you
cannot check **unchecked**, ask about it in Needs you, and plan nothing on it. Claims about
how code behaves have a stricter bar: `docs/kb/verification-discipline.md`.

**Write instructions as if every word costs — SHOULD FOLLOW** (owner, 2026-11-22). Each
line of an instruction file takes attention on every task, so a weak line taxes every
session and hides the strong ones. In anything another agent will follow (this file, a
handoff, a subagent brief, a hook message): write only what the reader cannot find in the
repo, and link rather than repeat; say what to do, checkably (a command, a path, a number),
not how hard to try; give the reason in one clause, because a reader who knows why applies
the rule to cases it does not name; use plain wording, because capitals and stacked
emphasis make rules over-apply. A rule that can have no exceptions goes in a hook or a
test, not in louder words. When you add a line, cut the one it makes redundant.

Replaces "Name the branches" in Reporting back:

- **Name the branch and worktree.** Open every reply with `Working on: <branch> · <worktree>`
  (`git branch --show-current`, `git rev-parse --show-toplevel`), and give the branch beside
  every PR or issue number you mention: `#69 (feature/asi-spreads)`.

In "Stop rules and budget", after the `lein` bullet (line 158 at `6aeea4ab8`), new:

```text
- **One agent per worktree, every agent:** work only in a worktree you were given or made for
  this task; parallel work gets its own, named `orcpub-<harness>-<task>`. Never work in one named
  for another harness. Signs another agent is in it: changes you did not make,
  commits or reflog entries from the last hour (`git status`, `git reflog -3 --date=iso`); if you
  see them, stop and ask. Where one is shared anyway, stage files by name (never `-A`, `--all`,
  `-u`, `.` or a folder; never `git commit -a`), and never `git stash`, `git checkout .` or
  `git clean`: they sweep up or wipe another agent's work.
```

New section, after "Reference — read when the task needs it":

```text
## Skills — use the one named here, at that moment

Each skill is `.claude/skills/<name>/SKILL.md`. Open it when its moment comes, even if you think you
know the steps: it carries the decision points.

| When | Skill |
|---|---|
| Merging `integration` (or any trunk) in; a merge conflict | `merge-integration` |
| Before every commit on a code branch | `before-commit` |
| Adding or changing a test | `fail-first-test` |
| Starting a branch; writing a changelog line; the owner says a PR is about to merge | `fold-changelog` |
| Opening a PR, asking for review, answering review comments | `open-pr` |
| Pushing changes to Docker, deploy, env or start scripts | `heavy-docker` |
| Writing a KB page, doc fix or handoff | `write-agents-develop` |
| Editing SRD data | `srd-data` |
| Adding a feature; changing code in one of the big files | `move-big-file-code` |
| An answer with a number, a total, a line number or two lists compared | `count-and-compare` |
```

**Moved to `CLAUDE.md`, "Claude-specific", word for word** (line numbers at `6aeea4ab8`, unchanged since `f732010`):

1. Lines 103-107, "The 'Generated by Claude Code' footer" (a Claude Code cloud session's GitHub
   connection adds it).
2. Lines 151-153, "Watch the only budget signal there is" (task notifications with
   `subagent_tokens` are Claude Code's).
3. Lines 168-180, "A long chat orchestrates; subagents do the noisy work", with its model table
   (`haiku`, `sonnet`, Opus).

**Stay, checked** (line numbers at `6aeea4ab8`): line 17 (SessionStart: describes setup), 64-68 (`claude/*` branches: a rule for
any agent given one), 142-147 and 193 (subagent limits: Cline has subagents too), 241 (one clause
names the Stop hook; the rest is general), 269 (`~/.claude/settings.json` is an example in a
general rule).

**Trimmed into the skills** (line numbers at `6aeea4ab8`). Each block below replaces the lines named,
word for word. Every removed instruction is in the skill named; the coverage list after the blocks
maps each one.

Lines 298-338 ("Shrinking the big files", the heading stays) become:

```text
Most logic sits in a few huge files: `views.cljs`, `events.cljs`, `styles/core.clj`,
`options.cljc`, `pdf.clj`, `character_builder.cljs`, `routes.clj`. Don't add new features to them:
new code goes in its own namespace, and the big file gets only the wiring. When your work reworks a
self-contained group there, move it out with the work; a big move gets its own branch, and you ask
first. Similar things share one function, with their differences as data, so a fix lands once.
Where new code goes, what counts as big, and what to report: the `move-big-file-code` skill.
```

Lines 362-369 ("SRD text is verbatim", the heading stays) become:

```text
- **Never write wording the SRD does not use into SRD data** (open5e fixtures, generated SRD
  files): no paraphrase, no "clearer" rewording; our own summaries go in a separate field. How,
  and the check to run: the `srd-data` skill.
```

In "Tests", lines 373-378 become:

```text
- **Before committing:** the checks for what you changed, once, on the final change; the full
  `lein lint` before a merge commit, a PR, or marking a draft ready. Merge with `--no-commit`, so
  the checks run before the merge commit. Which checks: the `before-commit` skill.
```

Lines 385-393 become:

```text
- **A test is seen to fail before it is committed:** a test only ever seen passing proves
  nothing. Its failing output goes in the test commit's message and in the PR. How: the
  `fail-first-test` skill.
```

Lines 395-402 become:

```text
- **Deployment changes:** a PR that touches a file in the `paths:` list of
  `.github/workflows/docker-heavy.yml` gets about 20 minutes of heavy Docker checks, run at
  landmark pushes only (owner, 2026-11-25): the `heavy-docker: later` label holds them for ordinary
  pushes, and comes off before review, after a push that changes a listed file, and before merge.
  The run is green before review. How: the `heavy-docker` skill.
```

Lines 379-384 (`--no-verify`), 394 (schema changes) and 403-405 (test layers, "Before a PR") stay
as they are.

Lines 409-428 ("Changelogs", the heading stays) become:

```text
- **A changelog says what the branch did:** one line per outcome a user or maintainer would
  notice, never one per commit. Fixed is only for bugs that existed before; internal work (tests,
  CI, refactors, tooling) gets no line.
- **Upstream issues track what reached people:** at the fold, draft an issue for every line that
  needs one; post each only with the owner's yes. What needs one: `docs/kb/upstream-issues.md`.
- **Fold only when the owner says the PR is about to merge.** The template, the format, the fold,
  the label and the issues: the `fold-changelog` skill.
```

Coverage (removed instruction → where it now lives):

- 298-300 file sizes: dropped (stale; `integration` `18a091a` has `views.cljs` at 10,711 lines).
- 300-303 fix may stay, report moves, ask about unmoved code; 305-306; 307-311; 312-317; 318-320;
  321-329; 330-332; 333-335; 336-337; 338 → `move-big-file-code` (317's example as a KB link; 338's
  line count now through `line-counts.sh`).
- 363-365 slot-number rule; 366-369 check script, pre-push, `open5e_records.py`, rule first →
  `srd-data`.
- 373-375 which checks, `docs/TESTING.md` → `before-commit`; 376-378 why `--no-commit` →
  `merge-integration`.
- 386 spellbook example: dropped (an example; the rule keeps its reason). 387-393 the steps →
  `fail-first-test`.
- 396-402 which paths, landmarks, the label and when it comes off, `GIT_WORKFLOW.md` →
  `heavy-docker` (the label command included).
- 410-411 example line; 412-413 Fixed and a fix to the same branch or release; 414-415 sections, area, issue, no
  hashes (412-413: the Fixed rule, kept short in the new text); 416-422 upstream issues: rule kept short, definitions → `fold-changelog` and
  `docs/kb/upstream-issues.md`; 423-428 template, unfolded while reviewed, fold, label, check →
  `fold-changelog`.

The other `AGENTS.md` lines that point into these sections still resolve: "Verify once" (Stop
rules) cites "Tests"; the merge gate cites "Changelogs"; `BRANCH.md` cites "Shrinking the big
files". All four headings stay. Checked with `git grep` on agents/develop: of the docs that cite
these sections, only `docs/GIT_WORKFLOW.md:174` ("When to ask the owner: `AGENTS.md`, "Tests"")
relied on removed detail. Row 8 changes it to: "When to remove the label: the `heavy-docker` skill
(rule: `AGENTS.md`, "Tests")." Row 1's patch leaves that line as it is.

### Row 9: `scripts/kb-audit.sh`

- `--push [dir]`: the commits a push would send (`@{u}..HEAD`; no upstream: `origin/HEAD`, else
  `origin/integration`, as `kb-doc-reminder.sh` does today).
- `--stop --since <epoch> <dir>...`: today's Stop logic, moved unchanged (`kb-audit-reminder.sh`:
  dirty files by mtime since `<epoch>`, else the last commit if made since then).
- Judgement: a commit touching `src/` or `test/` (push) or `src|test|dev|scripts/` (stop), with no
  `docs/` file, is a gap unless its message has `KB: none` + dash + a non-blank reason. Uncommitted
  changes are always judged. Any commit on `agents/develop` or `origin/agents/develop` newer than
  the oldest judged change silences the run (today's rule: code branches keep docs there).
- Output: plain text: today's reminder wording from each hook, moved into the script (one source),
  plus one closing sentence: "How: the write-agents-develop skill." Exit 1 on a gap, 0 when
  silent. Needs `git` only.
- `--self-test`, in a temp repo: (1) `src/` commit, no docs, no trailer → reminds; (2) `KB: none —
  typo fix` → silent; (3) `KB: none` with a blank reason → reminds; (4) two commits, only the first
  has the trailer, the second touches `src/` → reminds; (5) uncommitted `src/` change after an
  exempt commit → reminds; (6) an `agents/develop` commit after the change → silent; (7) a `docs/`
  file in the same commit → silent.

### Row 10: Claude wrappers

- `kb-audit-reminder.sh` keeps its stdin parsing, `stop_hook_active` guard, session-start time and
  transcript worktree discovery, calls `kb-audit.sh --stop`, and wraps its text in
  `{"decision":"block","reason":...}`.
- `kb-doc-reminder.sh` keeps its push detection and target-directory parsing, calls
  `kb-audit.sh --push`, and wraps its text in `additionalContext`.
- Replay test: record each hook's output before the change on four inputs (push with a code-only
  commit; push with docs; Stop with a dirty `src/` file; Stop with a clean tree), then after it.
  Same output, except the closing skill sentence (row 9) and the `KB: none` exemption.

### Row 12: `agent-setup.sh`

- **Root `AGENTS.md` copy** = marker line + `AGENTS.md` + the same `BRANCH.md` + handoff block
  `entry_point()` writes into `CLAUDE.md` (one function for both). `BRANCH.md` copy unchanged.
- **`scripts/cline/global-rule.md`**, new, the global Cline rule (text below).
- **`--cline`**: install or update `~/.cline/plugins/orcpub-guard.js`, `~/.cline/orcpub-guard/`
  and `~/Documents/Cline/Rules/orcpub.md` from `agents/develop`, then check the canary and print
  exactly one of:
  - plugin newly installed: "Cline guard installed; it starts with your next task. Say so in your
    first reply."
  - plugin updated: "Cline guard updated; the new version starts with your next task."
  - plugin current, `alive` written in the last 10 minutes with the same hash: "Cline guard: active."
    If `alive` also records command auto-approve on, the line after it: "Cline's command
    auto-approve is on, so the guard can only block, never ask: turn off Execute commands in
    Cline's Auto-approve menu."
  - plugin current, `alive` missing, older, or another hash: "CLINE GUARD DID NOT RUN THIS TASK (last
    ran: <time or never>). Make no commit or push. Tell the owner first: plugins may be off, Cline
    may have updated, or the plugin failed (Cline output log)."
- **`--check`** changes nothing and prints two lines, exactly one form each:
  - `Claude entry point: current.` or `Claude entry point: missing or out of date; run scripts/agent-setup.sh.`
  - `Cline guard: not installed (run agent-setup.sh --cline if you use Cline).`, `Cline guard: out of
    date; run agent-setup.sh --cline.`, `Cline guard: installed, last ran <time>.` or `Cline guard:
    installed, never ran.`
  - Exit code 1 only when the Claude line is the second form; the Cline line never fails it.

The global Cline rule (`scripts/cline/global-rule.md`, installed as `~/Documents/Cline/Rules/orcpub.md`):

> In a checkout of the codeGlaze/orcpub repo, before any other work in a task, run
> `git show origin/agents/develop:scripts/agent-setup.sh | bash -s -- --cline`, then follow
> `START-HERE.md` (`git show origin/agents/develop:START-HERE.md`). If it prints CLINE GUARD DID
> NOT RUN, stop and tell the owner. Run one command per call where you can, and never `cd` into
> the folder you are already in: chained commands are more often stopped for approval.

### Row 13: bootstrap texts

`START-HERE.md`, new first paragraph:

> If your harness did not load this repo's rules when the session started (Claude Code: no
> `CLAUDE.md`; Cline or Copilot: no `AGENTS.md` at the worktree root), you have no MUST FOLLOW
> rules, no skills and no armed git hooks. Step 1 fixes that. Claude Code's SessionStart hook runs
> it where `.claude/` exists; Cline's global rule runs it everywhere, with `--cline`, which also
> installs and checks Cline's guard (`docs/kb/agent-harnesses.md`, "Starting a session"). Then
> read `CLAUDE.md` (Claude Code) or `AGENTS.md` (others).

`agent-harnesses.md`, new section:

```text
## Starting a session

- Claude Code: the SessionStart hook runs `agent-setup.sh` where `.claude/` exists.
- Cline: one global rule, `~/Documents/Cline/Rules/orcpub.md`, copied once per machine by the
  owner from `scripts/cline/global-rule.md`; `agent-setup.sh --cline` keeps it, the guard plugin
  and its allowlist current after that.
- Copilot: the prompt in `copilot-agents.md` points it at `START-HERE.md`.
- Skills, for all three: `.claude/skills/`, extracted by `agent-setup.sh`; the `AGENTS.md` table
  "Skills" says when to open each.
```

### Row 14: the generic layer in dotfiles

Owner, 2026-11-27: other repos should get this work when they set up their agent instructions.
Done last, so two copies are never kept in step while rows change.

- **Moves (engine):** the guard's parser, decisions, tool coverage, git config check, tripwire,
  self-protection and canary; `check-claims.py`; the generic skills (`count-and-compare`,
  `fail-first-test`, a generic `before-commit`); the global Claude and Cline rules (tenets, stop
  rules, free-model working rules, one command per call); the trailer-strip and personal-data hooks;
  the benchmark and replay formats and runners.
- **Stays per repo (policy):** protected files and branches, runners, setup commands, and the
  "this is my repo" check, in a small file the repo keeps (`.agents/guard-policy.json`); the guard
  acts only where that file exists. orcpub's rules, `BRANCH.md`, handoffs and `agent-setup.sh`
  stay in orcpub.
- **Never moves:** replay data and anything derived from sessions (the dotfiles repo is public).
- **dotfiles changes:** an install that updates (version-checked, as `agent-setup.sh` checks the
  guard), not a first-run seed only; WSL and plain Linux as well as Codespaces.

## Working rules for free models

One task per row (`/newtask`). Sources `@`-mentioned in the brief, never recalled; the skill the
task needs named in the brief. A command that must pass. Every claim cites `file:line` or URL, else
**unchecked**. Model: Nemotron 3.5 Super VL (tested); not Kimi, MiniMax, Qwen or DeepSeek. Open
Cline in the worktree the task belongs to (fact 9).

## Several agents at once

The upgrade over Cline alone: Claude architects and reviews, one to three cheap agents build.

1. **One worktree per agent per task**, made by whoever hands out the task, named
   `orcpub-<harness>-<task>` (`orcpub-cline-kb-inbox`), detached or on the task's branch. The
   name is the claim: no lease, no expiry; the folder lives while the task does and is removed
   once its work is merged. `git worktree list` is the register. Long-lived docs worktrees keep
   their `GIT_WORKFLOW.md` names (`orcpub-agents-cline`, made 2026-11-27 at `daa0e03b7`).
2. **Each harness keeps out of the others' folders.** Cline's guard enforces it (row 3 step 2);
   for Claude and Copilot it is the row 8 rule.
3. **Handing out a task:** a closed brief (row 5 template) in the folder; the agent returns a
   result block; `check-claims.py` checks it; the orchestrator reads only mismatches and verdicts
   that change something, then merges one task at a time.
4. **Starting Cline** is the bottleneck: today the owner opens a VS Code window on the folder and
   pastes the brief. Whether a Cline command-line tool can start a task without that is unchecked
   (Unchecked); read its docs and the installed code before designing around it.
5. **Shared machine:** at most one `lein` process per worktree; dev servers in parallel worktrees
   need distinct ports (Later).

## Making free models better

Part of the plan (owner, 2026-11-27): raise what a free model gets done through the harness, not
the weights. Each item below is a **proposal**; each becomes a row only with the owner's go, and
counts as an improvement only once the benchmark (item 1) shows it. Research results are
hypotheses here, not facts about Cline.

1. **Benchmark first, kept for good.** 5 to 10 real orcpub tasks (a KB page, a small fix with a
   test, a review that should find a planted defect, an investigation with no change), each with a
   brief, acceptance criteria and a scoring script. Every run records the model name, the
   harness version, pass or fail, rule violations, unsupported claims (row 5) and tool errors.
   Baseline before any item below; every later harness change reruns it, so nothing quietly makes
   things worse. The baseline (row 4) runs after row 3 and before row 5: its raw outputs are kept as they are
   and scored by row 5's checker once it exists, never by hand in its place.
2. **A fresh reviewer.** A second `/newtask` gets only the diff and the acceptance criteria, never
   the first task's history; `check-claims.py` checks its answer. Source: *Three Roles, One Model*
   (arXiv 2604.11465: Qwen3-8B, AppWorld, 5.4% to 8.9%; the corrector sees code without history).
3. **Cline's own tools before shell.** The global rule (row 12) asks for `read_files` and
   `search_codebase` over `cd … && sed … | grep` chains. Source: *An Empirical Study of Harness
   Design for Coding Agents* (arXiv 2609.20804: predefined tools help models weak at bash). Also
   cuts the guard's asks (fact 13).
4. **Search that helps small models.** Whether `search_codebase` uses embeddings is unchecked:
   read the installed code first. Source: arXiv 2610.33958 (gains of 0.06 to 0.10 for models up
   to about 14B, mostly from the embedding index).
5. **Fix by failure kind.** Group benchmark failures by cause, then change the rule, the skill or
   the guard that the cause points at, one at a time. Source: *Beyond Prompts* (EMNLP 2026
   Findings, 2026.findings-emnlp.1455).
6. **Useful context, not less context.** Measure startup context by tokens, duplication across
   `AGENTS.md`, `BRANCH.md`, handoffs and skills, share relevant to each benchmark task, and rule
   violations; decide what loads at start from those numbers (decision A stands until then).

## Rejected, with reasons (do not re-propose without new evidence)

- **Cline hook scripts:** they do not run in 4.1.26 (fact 1).
- **A `.clinerules/` rules file for the branch state:** untested, and needs a guard; the `AGENTS.md`
  copy carries it (closed decisions).
- **A skill that lists the skills:** a skill is opened only when named, so an index skill would go
  unread; the index sits in `AGENTS.md`, which loads every task.
- **A per-project `.cline/plugins/`:** untested; the global folder is tested, and `agent-setup.sh`
  keeps it current, so one copy serves every worktree.
- **Skills that copy their doc's steps:** two copies drift; a skill holds the decision points and
  points at the section.
- **MCP for instructions:** adds dependencies, no gain; a script covers tool needs.
- **A separate `cline-setup.sh`:** duplicates `agent-setup.sh`.
- **Compiling Cline rules by stripping Claude sections out of `AGENTS.md`:** fragile; moving
  Claude-only text into `CLAUDE.md` makes it unnecessary.
- **Agents editing a code branch's `.gitignore`:** `info/exclude` already does it from `agent-setup.sh`.
- **Copying `docs/kb/` into every worktree:** 276 files at `f732010`; `AGENTS.md` already says to read docs with
  `git show origin/agents/develop:<path>`.
- **A line-count cap on `KB: none`:** a required reason makes misuse visible instead.
- **A task baseline file for the KB check** (v3.7 row 2): it needed TaskStart/TaskComplete hooks,
  which do not run; judging the commits a push sends needs no state.
- **Removing `MUST FOLLOW` labels file-wide:** they mark a tier, not emphasis.
- **Moving rule 2 into the KB:** it governs writing `AGENTS.md` and briefs.
- **`.devcontainer` bootstrap:** unused (owner, 2026-11-22).

## Later (not this version)

- Model-to-task routing and analysis of the claim log, after about 20 runs.

- One lein dev build or server per worktree unless ports differ (the agent-per-worktree rule is in
  row 8).
- Split `BRANCH.md`; a length check on the generated files.
- Guard additions: big-file advisory with live line counts, concurrent `lein` in one worktree, bare
  `lein test`. A KB reminder at the end of a Cline task (no plugin hook can speak then).
- Cline in codespaces (a new codespace has an empty home: the global rule needs a dotfiles repo or
  a by-hand copy). Re-test skills on Nemotron 3.5 Lightning before giving it tasks.

## Unchecked

- Which context field carries the tool name and the session folder (row 3, step 1;
  `ctx.toolCall.name` was undefined, fact 12).
- The ask rate on building tasks (fact 13 measured review tasks only), and whether the global
  rule's "one command per call" holds across many tasks (one test, fact 12).
- Whether a Cline policy or update turns plugins off (the canary reports it, row 12).
- Whether Copilot reads `.claude/skills/`.
- Nemotron's result format over many tasks (one trial passed, fact 11; fallback: a lenient
  table parser in row 5).
- Whether `CLINE_ACTIVE` is set on every path Cline runs commands through, and reaches a git hook
  (seen on one task; fact 10; row 11 step 9 tests it).
- Whether Cline has a command-line tool that can start a task in a given folder without a VS Code
  window ("Several agents at once", 4).

## Verification prompts

Two reviewers by different routes (Cline with git in the repo; a web model with the links above),
then every disagreement checked against the primary source. Gemini cannot open
`raw.githubusercontent.com` links: give it `github.com/.../blob/` links. Prepend `/newtask` for Cline.

```text
Review only: no fixes, no commits. Read agent-harness-plan.md and follow its "Review rules".
Check: (a) every factual claim against the source cited beside it; (b) build order: for each row,
does it need anything a later row makes, and does any later row change text an earlier row wrote;
(c) completeness: could a builder do each row from the plan's texts and specs alone, without
inventing content? Skip "Later", "Unchecked", and the "Tested" facts (they come from the owner's
machine; check only that the plan uses them consistently).
One table row per finding:
| # | Plan line (quoted, short) | Kind (fact/order/complete) | Evidence (source + quote ≤ 1 line, or command + output) | Verdict |
Verdict for facts: CONFIRMED / WRONG / NOT FOUND / COULD NOT CHECK. For order and completeness:
BLOCKS / SHOULD CHANGE / OK. A verdict without evidence is invalid; never from memory. Line
numbers and counts come from a command shown in Evidence. Write the table once, then counts.
```

Brief template for any free-model task:

```text
/newtask
Task: <one row or one file>.
Sources (read these, do not recall): @/<file> @<doc URL>
Skill: <the skill this task needs, from the AGENTS.md "Skills" table>
Outcome wanted: <one line>.
Acceptance criteria: A1 <...>; A2 <...>.
Done when: <command> passes.
Analysis task: change no files. A blocker: stop, and report outcome BLOCKED.
Every count, total or line number comes from check-claims.py (the count-and-compare skill).
End with the result block in the plan's row 5 format.
```
