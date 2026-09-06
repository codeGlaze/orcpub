# Documentation tenets: what goes in the KB, and when

`docs/kb/` is the audit trail. Its job is that someone arriving cold — a person or an agent
— can find what was decided, what was measured, and what was already tried and rejected,
without reading the commit log or re-running the work.

## The tenets

**1. A finding is not a finding until it is written down with its numbers.**
Record what was measured, the instrument, and the conditions. A claim without a number is a
hypothesis; label it as one. Numbers that came from a proxy (a `pr-str` length standing in
for heap, an operation count standing in for cost) must say so in the same breath.

**2. Record reversals; never silently overwrite superseded reasoning.**
When a conclusion flips, the old conclusion and the reason it was wrong are the most
valuable content on the page — that is what stops the next person re-deriving it. Mark it
as a correction in place. `perf-homebrew-builder-loop.md` carries three of these and is
more useful for them, not less.

**3. Dead ends are content.** A measurement that went nowhere, a fixture that turned out to
be wrong, a tool that silently lied — write it down. The cost of rediscovering a dead end is
the same as discovering it the first time.

**4. Decisions get their "why", and their alternatives.** "We chose X" is half a decision.
"We chose X over Y because Y trades a one-time cost for a per-change cost" is a decision
someone can revisit when the tradeoff changes.

**5. One doc per investigation, not one per commit.** Extend the doc that covers the area;
create a new one when the subject is genuinely new. A doc that grows through an
investigation reads as a narrative; a pile of small docs does not.

**6. Comments are dense or absent.** In code, explain the non-obvious: why this branch
exists, what breaks without it, which trap it avoids. Do not narrate what the code plainly
says. A genuinely confusing case earns a longer comment, but it must carry information per
line — a comment that takes many words to say little is worse than none.

**7. Sync docs with the commit that changes behaviour**, not in a later cleanup pass. The
commit message says what changed; the KB says what is now true.

## What belongs here

| kind | example in this KB |
|---|---|
| investigation + measurements | `perf-entity-build.md`, `perf-homebrew-builder-loop.md` |
| method and its failure modes | `verification-discipline.md` |
| how a subsystem actually works | `built-character-representation.md` |
| a refactor's reasoning and its baseline | `armor-class-refactor.md` |
| tenets and conventions | this file |

## The reminder hook

`.claude/` is gitignored on code branches by deliberate policy (agentic tool files belong in
dotfiles or the agents branch), so the hook itself cannot live in the repo. It is
reproduced here so it can be installed on any machine.

`.claude/hooks/kb-audit-reminder.sh`, made executable, plus a `Stop` entry in
`.claude/settings.json` pointing at it. It looks at ONE window — the uncommitted tree while
dirty, otherwise the last commit — and prints a reminder only when that window touches
`src/`, `test/` or `dev/` and contains no `docs/` change. Everything else is silent.

Unioning the two windows was tried first and is wrong: a docs-only commit at HEAD then
silences every later code change, which is exactly the case the reminder exists for.

```bash
#!/usr/bin/env bash
set -uo pipefail
cd "${CLAUDE_PROJECT_DIR:-.}" 2>/dev/null || exit 0
git rev-parse --git-dir >/dev/null 2>&1 || exit 0

changed=$(git status --porcelain 2>/dev/null | sed 's/^...//' | sed 's/^"//; s/"$//' | sort -u)
[ -n "$changed" ] || changed=$(git diff --name-only HEAD~1 HEAD 2>/dev/null | sort -u)
[ -n "$changed" ] || exit 0

code=$(printf '%s\n' "$changed" | grep -E '^(src|test|dev)/' | head -20)
docs=$(printf '%s\n' "$changed" | grep -E '^docs/')
[ -n "$code" ] || exit 0
[ -z "$docs" ] || exit 0

n=$(printf '%s\n' "$code" | wc -l | tr -d ' ')
printf '{"systemMessage":"KB gap: %s code/test file(s) changed with no docs/ update. ..."}\n' "$n"
exit 0
```

```json
{ "hooks": { "Stop": [ { "hooks": [ {
  "type": "command",
  "command": "\"$CLAUDE_PROJECT_DIR/.claude/hooks/kb-audit-reminder.sh\"",
  "timeout": 15,
  "statusMessage": "Checking KB audit trail"
} ] } ] } }
```

It is a reminder, not a gate: it always exits 0 and never blocks. Ignoring it is the right
call for a lint fix, a rename, or a mechanical refactor.

## Related

- [README.md](README.md) -- KB index; add new docs there so they are findable.
- [verification-discipline.md](verification-discipline.md) -- the corrections these tenets
  exist to preserve.
