#!/usr/bin/env bash
# SessionStart: prints the expertise files that fit this session into its context.
#
# A file in ~/.claude/expertise/*.md fits when a header line (before the first "#" heading) matches:
#   session: <id>          this exact chat (the original behaviour)
#   branches: <regex>      the current git branch, e.g.  branches: ^(premerge/|fix/release)
#   dirs: <regex>          the worktree folder's name, e.g. dirs: ^orcpub-(dmv|agents)$
#   always: yes            every session
# A file with none of these lines loads only by session, as before.
# Output past ~9 KB is cut to a short preview by Claude Code, so files that would overflow it are
# named instead of printed, with the command to read them.
exec python3 -c '
import json, re, subprocess, sys, glob, os
try:
    inp = json.load(sys.stdin)
except Exception:
    sys.exit(0)
sid = inp.get("session_id", "")
cwd = inp.get("cwd") or os.getcwd()
def git(*a):
    try:
        return subprocess.run(["git", "-C", cwd, *a], capture_output=True, text=True, timeout=3).stdout.strip()
    except Exception:
        return ""
branch = git("branch", "--show-current")
top = os.path.basename(git("rev-parse", "--show-toplevel") or cwd)

hits = []
for path in sorted(glob.glob(os.path.expanduser("~/.claude/expertise/*.md"))):
    try:
        text = open(path, encoding="utf-8").read()
    except OSError:
        continue
    why = None
    for line in text.splitlines():
        if line.startswith("#"):
            break
        key, _, val = line.partition(":")
        key, val = key.strip().lower(), val.strip()
        try:
            if key == "session" and sid and val == sid:
                why = "this chat"
            elif key == "branches" and branch and re.search(val, branch):
                why = "branch " + branch
            elif key == "dirs" and re.search(val, top):
                why = "folder " + top
            elif key == "always" and val.lower() in ("yes", "true"):
                why = "always"
        except re.error:
            continue
        if why:
            break
    if why:
        hits.append((path, why, text))

BUDGET = 9000
rank = {"this": 0, "branch": 1, "folder": 2, "always": 3}
hits.sort(key=lambda h: rank.get(h[1].split(" ")[0], 1))
if hits:
    print("Session is in folder " + top + ", branch " + (branch or "(none)") + ".")
    used, skipped = 200, []
    for path, why, text in hits:
        if used + len(text.encode()) > BUDGET:
            skipped.append((path, why))
            continue
        used += len(text.encode()) + 80
        print(f"\n=== Expertise: {os.path.basename(path)} (loaded for {why}) ===\n{text}")
    for path, why in skipped:
        print(f"\nAlso fits this session but too long to print here ({why}): read it now with cat {path}")
'
