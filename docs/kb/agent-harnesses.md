# Agent harnesses: what each one loads, and how to write for them

What Claude Code, Cline (VS Code extension) and Copilot (VS Code agent mode) read from this repo,
how skills and hooks reach each, and what the evidence says makes instructions get followed.
Checked 2026-11-22 against the live docs linked in each section and `agents/develop` `f732010`;
re-checked after two independent reviews the same day.
A harness's docs describe its latest release: check your installed version when a fact matters.

## Rules files

| Harness | Reads | Does not read |
|---|---|---|
| Claude Code | `CLAUDE.md`; `@path` imports, up to four hops ([memory](https://code.claude.com/docs/en/memory)) | `AGENTS.md`, unless imported |
| Cline | `.cline/rules/` and the older `.clinerules/`, root `AGENTS.md` (plus nested ones, when a root one exists), `~/.agents/AGENTS.md`, global rules under `~/.cline/` ([rules](https://docs.cline.bot/customization/cline-rules), [configuration](https://docs.cline.bot/cline-cli/configuration)) | `CLAUDE.md` (not in any listed location) |
| Copilot | `.github/copilot-instructions.md` or root `AGENTS.md`, plus nested `AGENTS.md` files ([VS Code reference](https://code.visualstudio.com/docs/agents/reference/custom-instructions-reference)); VS Code's deprecated Local harness (removed February 2027) also reads `CLAUDE.md`, with `chat.useAgentsMdFile` (on by default) and `chat.useClaudeMdFile` among its settings | `CLAUDE.md` (Copilot harness) |

- **`AGENTS.md` loads in Cline even when `.clinerules/` exists**, and each rule can be toggled in
  the Rules panel. Unconditional rules load on every request.
- **On a code branch, Claude gets everything; the others get only `AGENTS.md`.** `agent-setup.sh`
  builds `CLAUDE.md` by reading `agents/develop:CLAUDE.md` line by line: `@BRANCH.md` becomes
  `BRANCH.md` plus the branch handoff, any other whole-line `@path` is inlined, and every other
  line passes through (`scripts/agent-setup.sh:119-145`). It also drops plain copies of `AGENTS.md`
  and `BRANCH.md` at the root (`:168-171`). Cline and Copilot never load `BRANCH.md` or the
  handoff unless told to read them.
- **Claude-only text belongs in `CLAUDE.md` below its imports:** it already reaches code branches
  through the pass-through above, with no script change.
- **Size:** Claude Code's guidance is under 200 lines per `CLAUDE.md`; "longer files consume more
  context and reduce adherence". A code-branch `CLAUDE.md` is about 780 lines (`AGENTS.md` 459,
  `BRANCH.md` 285, `CLAUDE.md` 37, plus the handoff).
- **`.github/copilot-instructions.md` links `../agents.md`**, which does not exist (the file is
  `AGENTS.md`; `agents/` is a folder). It resolves only on a case-insensitive filesystem.

## Skills

A skill is a folder with a `SKILL.md`: the harness shows the agent each skill's name and
description, and loads the body only when the task matches or the skill is invoked. Open standard,
adopted by Copilot and VS Code among others ([Anthropic](https://www.anthropic.com/news/donating-agent-skills-to-the-agentic-ai-foundation)).

| Harness | Project skill folders | Mid-session additions |
|---|---|---|
| Claude Code | `.claude/skills/` | picked up without restart ([skills](https://code.claude.com/docs/en/skills)); one open report of the Desktop app missing them until a new session |
| Cline | `.cline/skills/`, `.clinerules/skills/`, `.claude/skills/` ([skills](https://docs.cline.bot/customization/skills)); the CLI also lists `.agents/skills/` | unchecked: the list is shown "when you send a message" |
| Copilot | `.github/skills/`; the page lists one location per harness, "not every directory it recognizes" ([VS Code](https://code.visualstudio.com/docs/copilot/customization/agent-skills)) | unchecked |

- **One copy in `.claude/skills/` on `agents/develop` serves Claude Code and Cline.**
  `agent-setup.sh` already copies `.claude/` into every worktree, gitignored or listed in
  `info/exclude` (`agent-hooks.md`). Copilot would need a copy in `.github/skills/` plus an exclude
  entry, since code branches track `.github/`.
- **Cline:** an older copy of its skills page said skills need Settings → Features → Enable
  Skills; the current page does not. Unchecked.
- **A skill is chosen by its description, so use is not guaranteed.** Write the description as a
  trigger ("Use before running `git merge integration`"); for a step that matters, add a hook on
  the command (below).

## Hooks

**Cline's hook details below are unconfirmed against its current docs (checked 2026-11-22).** Its
hooks page now only links to [SDK Plugins](https://docs.cline.bot/sdk/plugins.md), which documents
programmatic plugins (`beforeTool`, `afterTool`, …), not hook scripts. The Cline column comes from an
earlier version of its docs. Current project configuration lives in `.cline/` (`rules/`, `workflows/`,
`skills/`, `hooks/`, `agents/`, `plugins/`), with `.clinerules/` read as "the older layout"; global
configuration is `~/.cline/`, shared by the IDE, CLI and SDK
([configuration](https://docs.cline.bot/cline-cli/configuration)). The configuration page does not
give hook file names or format. Before relying on any Cline hook detail, run a test hook on the
installed extension and record which folder it reads and the payload it receives.

| | Claude Code ([hooks](https://code.claude.com/docs/en/hooks)) | Cline ([hooks](https://docs.cline.bot/customization/hooks)) |
|---|---|---|
| Where | `.claude/settings.json` | `.cline/hooks/` (current layout) or `.clinerules/hooks/` (older); earlier docs: file named after the hook, extensionless, `chmod +x`, Settings → Features → Enable Hooks |
| Events | SessionStart, PreToolUse, Stop, … | TaskStart, TaskResume, TaskCancel, TaskComplete, PreToolUse, PostToolUse, UserPromptSubmit, PreCompact |
| Block a call | stdout `{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"deny","permissionDecisionReason":"…"}}` | stdout `{"cancel":true,"errorMessage":"…"}` |
| Add context | `additionalContext` in `hookSpecificOutput` | `contextModification`; reaches the next request, not the current tool call |
| Match a command | `"if": "Bash(git merge *)"`; best-effort, not for hard rules | read `preToolUse.parameters` from stdin |

- Cline stdout must hold only the JSON; debug output goes to stderr. Global hooks run before
  workspace ones, and either can cancel.
- **Cline's TaskComplete fires after the task finished;** the docs give no way for it to block
  completion. Enforce in `.githooks/` and CI; use TaskComplete to remind.
- Unchecked: the name of Cline's shell tool and the shape of its command parameter.

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

## Models

What each model is good and bad at here, from tests and the claim log. Rules for every agent stay
in AGENTS.md; nothing here loosens them.

- **Nemotron 3.5 Super VL** (Cline, free). Uses a skill only when the task names it. Follows a
  fixed JSON result format (one trial). Its quotes and line numbers hold up, a few lines off at
  worst; its counts and totals do not: every count must come from a check (`count-and-compare`).
  Reads evidence right more often than it concludes right: its verdicts that change something
  are reviewed.

## Writing instructions that get followed

- **Every always-loaded line costs attention on every task:** keep only what the agent could not
  work out from the repo ([best practices](https://code.claude.com/docs/en/best-practices)).
- **Give the reason in a clause;** the model generalizes from it
  ([prompting](https://platform.claude.com/docs/en/build-with-claude/prompt-engineering/be-clear-and-direct)).
- **Say what to do,** not what to avoid (same page).
- **Plain wording.** Stacked emphasis (IMPORTANT, capitals) makes current models over-apply a
  rule; a rule with no exceptions belongs in a hook or a deny rule (best practices).
- **Tier labels are not emphasis.** `MUST FOLLOW` / `SHOULD FOLLOW` tell the reader which tier a
  rule is in; no source tested section labels, so they stay.

## Corrections made on the way (2026-11-22)

- "Code branches lack `AGENTS.md`": wrong. `agent-setup.sh` copies it to the root of every
  set-up worktree.
- "Moving Claude-only text into `CLAUDE.md` needs an `agent-setup.sh` change": wrong; see Rules
  files.
- Cline's native `AGENTS.md` support was called "unverified" while its docs answered it in two
  searches. The rule that came out of it: check before you plan, and a dismissal needs a source as
  much as a confirmation does.

## Related

- [agent-hooks.md](agent-hooks.md): the git hooks and the one way to arm them
- [copilot-agents.md](copilot-agents.md): running a Copilot agent here
- [verification-discipline.md](verification-discipline.md): the bar for claims about code
