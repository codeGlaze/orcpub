# Git hooks: one set, one installer

How the repository's git hooks are kept, armed and checked, and why there is exactly one way to
arm them. Read this before adding a hook, changing `core.hooksPath`, or writing anything that runs
at session start.

## The rule

- **Every hook lives in `.githooks/` on `agents/develop`.** Today: `commit-msg` (strips agent
  attribution trailers), `pre-commit` (docs check, where `scripts/check-docs.sh` exists),
  `pre-push` (refuses an un-folded branch changelog onto integration, develop or main).
- **`scripts/agent-setup.sh` is the only thing that sets `core.hooksPath`.** It copies the hooks
  into `.git/agent-hooks/` and points `core.hooksPath` there. That directory is inside `.git`, so
  no branch can track or commit it, and `core.hooksPath` lives in the clone's shared config, so one
  run arms every worktree.
- **The other two entry points call it.** `scripts/setup-hooks.sh` and the SessionStart hook
  (`.claude/hooks/session-start.sh`) both run `agent-setup.sh --hooks-only`, which reads the local
  `agents/develop` ref and needs no network.
- **`agent-setup.sh --check` verifies, it does not trust.** It reports the hooks as armed only when
  `core.hooksPath` points at `.git/agent-hooks/` and each installed hook matches the copy on
  `agents/develop` byte for byte. A stale or replaced hook reads as `NOT ARMED or older`.

To change a hook: edit it in `.githooks/` on `agents/develop`, push, and re-run the setup. A hook
added anywhere else will be replaced on the next session start, which is the point.

## Why it is built this way

By 2026-09-30 four separate things were arming hooks, each written without seeing the others:

| Arming | Set `core.hooksPath` to |
|---|---|
| `scripts/setup-hooks.sh` | `.githooks` |
| `agent-setup.sh` | `.githooks` |
| `.claude/hooks/session-start.sh` | `.githooks`, unless something was already set |
| a commit-msg trailer stripper kept only on one machine, `~/.config/git/orcpub-hooks` | that directory |

`core.hooksPath` takes one value, so these could never all be active. The trailer stripper was
invisible to every script and to any agent on another machine, so the setup START-HERE prescribes
would have silently switched it off; while it stayed set, the session-start hook declined to arm
the docs and changelog guards, and `--check` reported the hooks as not armed. Each fix was
reasonable alone. Together they guaranteed that whichever agent touched hooks next would find the
rules "wrong" and change them.

The failure was not any one script. It was a rule that lived somewhere no setup or document could
see. The cure is a single versioned home and a single installer, with a check that compares rather
than assumes.

## Limits

- Hooks only run where the setup has run. A cloud or web agent that never runs it commits without
  them, so the authorship and trailer scan before anything public stays required
  (`AGENTS.md`, "Authorship").
- `--no-verify` skips them. AGENTS.md forbids it.
- Integration also tracks a `.githooks/pre-push`, identical to this one, for contributors who are
  not agents. Keep the two identical; `--check` compares against `agents/develop`'s.
