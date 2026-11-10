# Keeping a long chat's expertise cheaply

Long chats do better than new agents with handoffs, but reopening one is expensive: 642k tokens of
cache writes in the chat this was worked out in, and about 70% of that context was tool traffic. A
handoff records state; the value is the owner's corrections, failed approaches and traps.

## Where the knowledge goes

- **How to work with the owner** (corrections that apply to every task): `AGENTS.md`, "Working with
  the owner". Every agent loads it, including cloud sessions, which have no user-level hooks.
- **An area's knowledge** (release and DMV, say): `~/.claude/expertise/<area>.md`, under about 8 KB,
  pointing at KB pages for detail. Shared findings go in the KB, not there.
- **Everything else stays searchable:** transcripts remain in `~/.claude/projects/`, and
  `~/.claude/hooks/chat-search.py 'REGEX'` searches them, tool output included. Search before
  re-asking the owner.

## Loading by area, not by chat (2026-11-08)

`~/.claude/hooks/expertise.sh` (SessionStart; versioned copy in `scripts/user-hooks/expertise.sh`)
prints every expertise file whose header matches the session. Header lines, before the first `#`:

| Line | Loads in |
|------|----------|
| `session: <id>` | that chat (several lines allowed: every chat that added to the file) |
| `branches: <regex>` | any chat on a matching branch |
| `dirs: <regex>` | any chat in a matching worktree folder |
| `always: yes` | every chat |

When a file matches, it also prints the session's folder and branch; when none does it prints
nothing, so silence does not mean the hook failed. A chat started in the main checkout (detached,
no branch) matches only `session:` and `always:` lines, whatever worktree it then works in. **Hook output is cut to a short preview somewhere
near 10 KB** (measured once, 2026-11), so the hook prints matches up to about 9 KB, most specific
first, and names the rest with a `cat` command instead of letting them be cut silently. Before
2026-11-08 it matched `session:` only, so a new chat in the same area started with nothing; that is
why long chats kept being resumed.

## Auditing a chat for what the expertise file missed

The writer of an expertise file chooses what is worth keeping, so audit it against the transcript.
`scripts/trim_transcript.py` shrinks a transcript first: it drops subagent side-chains, hook and
meta entries, compaction summaries and system reminders, cuts pasted logs, and redacts tokens, keys
and passwords.

```bash
python3 scripts/trim_transcript.py ~/.claude/projects/*/<session>.jsonl --text --flagged --since <date> > talk.txt
```

`--owner` keeps only the owner's messages; `--flagged` adds Claude's messages that read like a
correction, trap, decision or open item. Measured on the release chat's last week: plain `--talk`
gave 13 MB; `--flagged` with the noise dropped gave 1,628 lines, which one Sonnet chat audited in
full and found 16 gaps and 4 stale entries. Ask the auditor to list everything not covered, without
judging importance; the owner prunes. The first audit (owner's messages only, four months, before
this script) cost 215k tokens.

## What keeps it working

- `expertise.sh` (above) and `expertise-nudge.sh` (Stop, at most once an hour per session). The
  nudge runs `expertise.sh` to decide, so "covered" means the same in both: a covered session is
  asked to add to its file, an uncovered one to start a file for its area. Versioned copies of
  both are in `scripts/user-hooks/`; install with `cp` into `~/.claude/hooks/`. No agent has to
  choose to use them.
- **Until 2026-11-10 the nudge fired only for sessions already named in a file** (`session:`
  lines), so a chat in an area with no file was never asked to start one: a day of homebrew and
  reference-list work went unrecorded until the owner noticed. That is why it now asks uncovered
  sessions too.
- A tool an agent must remember goes unused here: what must happen needs a hook or a failing test;
  what should happen needs a `CLAUDE.md` line naming the moment.
- Compact before leaving a large chat: idle gaps and model switches force a full cache rewrite.
- Not built yet: running the trim and audit automatically at `PreCompact`.
