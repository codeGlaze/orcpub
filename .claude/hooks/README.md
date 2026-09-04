# Hooks

Drop-in hooks for agents working on this repo. Copy what you want into your own setup — nothing
here is applied automatically to your machine.

## `kb-doc-reminder.sh`

Fires on `git push`. Inspects the commits the push would send; if they changed `src/` or `test/`
while touching no documentation, it injects a reminder to write down what was learned before the
knowledge is lost.

**Advisory, never blocking.** Plenty of pushes legitimately need no docs — a typo, a rename, a
test-only tweak — and a hook that blocked those would only train people to work around it. It exits
silently in every other case, including when there is nothing to push.

### Install

Copy the script, then merge this into your `.claude/settings.json` — **merge, don't overwrite**, if
you already have hooks:

```json
{
  "hooks": {
    "PreToolUse": [
      {
        "matcher": "Bash",
        "hooks": [
          {
            "type": "command",
            "if": "Bash(git push*)",
            "command": ".claude/hooks/kb-doc-reminder.sh",
            "timeout": 15,
            "statusMessage": "Checking KB docs for this push"
          }
        ]
      }
    ]
  }
}
```

`chmod +x .claude/hooks/kb-doc-reminder.sh`. Requires `jq` and `git`.

A newly created `.claude/settings.json` is **not picked up mid-session** — the settings watcher only
watches directories that already had a settings file when the session started. Open `/hooks` once,
or restart, before expecting it to fire.

### Verify it works

Both paths, without touching your history:

```sh
# should print a reminder (assuming you have unpushed commits under src/ or test/)
echo '{"tool_name":"Bash","tool_input":{"command":"git push"}}' | .claude/hooks/kb-doc-reminder.sh

# should print nothing
echo '{"tool_name":"Bash","tool_input":{"command":"ls"}}' | .claude/hooks/kb-doc-reminder.sh
```

If the first prints nothing, you probably have nothing unpushed, or your unpushed commits already
include a `docs/` change — both are correct silences.

### Why it exists

On code branches `.gitignore` excludes `.claude/` on purpose (*"Agentic/AI tool files — belong in
dotfiles or agents/ branch, not code branches"*), so the hook cannot live there. This branch is that
home. The discipline it enforces is written up in `docs/kb/documentation-discipline.md` on the code
branches: what earns a doc, updating in place rather than appending a second account, and recording
reversals instead of silently overwriting superseded conclusions.
