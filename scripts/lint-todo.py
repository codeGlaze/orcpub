#!/usr/bin/env python3
"""Check docs/TODO.md against the roadmap rules in docs/CONTRIBUTING.md.

Two rules, and the second is the one that gets skipped:

  1. Every "## " section opens with a "**Status:**" line drawn from a fixed
     vocabulary. The Status line is the file's only attribution -- it is how a
     reader tells whose item an entry is -- so a section without one is
     unattributed and a section with a freehand one is unsearchable.

  2. Nothing marked "Shipped on <branch>" survives onto integration/develop/main.
     When a release folds, a shipped item either leaves the file or is cut down
     to whatever it left undecided, at Status Open. A surviving "Shipped" section
     is finished work presented as pending, which is how this file grew to carry
     22 commits without a single section ever being removed.

WHAT THIS CANNOT DO, on purpose: it reads the file, not reality. An item that
quietly went stale -- shipped, but with nobody updating its Status -- is
invisible here, and no hook can see it. This catches the DECLARED stale item at
the moment it matters and keeps the Status line trustworthy enough to be worth
reading. Pruning itself stays a person's judgement, like the changelog fold.

Run it the way CI does:

    scripts/lint-todo.py                 # form only: Status present and valid
    scripts/lint-todo.py --strict        # ... and no Shipped section may survive
    scripts/lint-todo.py path/to/file.md # check some other file

Exit status is 0 when clean and 1 when anything is found, which is the whole
contract with CI: a step fails because the command it ran returned non-zero.
"""

import re
import sys

DEFAULT_TODO = "docs/TODO.md"

# The vocabulary from docs/CONTRIBUTING.md. "Being built on" and "Shipped on"
# name a branch; the rest stand alone. Matched at the head of the Status text so
# a section can still qualify what it means after the term.
VOCABULARY = ("Open", "Not started", "Being built on", "Shipped on", "Unverified")

# A shipped item is due to leave when the release folds, so the check that it did
# belongs on the branches a fold lands on -- the same set changelog-guard uses.
SHIPPED = "Shipped on"


def sections(lines):
    """(start_line, heading) for every "## " heading, 1-indexed, in order."""
    out = []
    for i, text in enumerate(lines, 1):
        if text.startswith("## "):
            out.append((i, text[3:].strip()))
    return out


def status_of(lines, start, end):
    """The Status text of the section, or None. Must be in the section's opening
    block: a Status buried below prose is not the first thing a reader meets."""
    for i in range(start, min(end, start + 8)):
        m = re.match(r"\*\*Status:\*\*\s*(.+)", lines[i - 1].strip())
        if m:
            return i, m.group(1).strip()
    return None


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    todo = args[0] if args else DEFAULT_TODO
    strict = "--strict" in sys.argv

    try:
        lines = open(todo, encoding="utf-8").read().splitlines()
    except FileNotFoundError:
        print(f"::error file={todo}::{todo} not found")
        return 1

    found = []

    def err(line, msg):
        found.append((line, msg))
        print(f"::error file={todo},line={line}::{msg}")

    secs = sections(lines)
    if not secs:
        print(f"::error file={todo}::No '## ' sections found in {todo}.")
        return 1

    bounds = [(s, secs[i + 1][0] if i + 1 < len(secs) else len(lines) + 1, h)
              for i, (s, h) in enumerate(secs)]

    shipped = []
    for start, end, heading in bounds:
        st = status_of(lines, start, end)
        if st is None:
            err(start, f"Section '{heading}' has no **Status:** line in its opening "
                       f"block. Add one: {', '.join(VOCABULARY)}. "
                       f"See docs/CONTRIBUTING.md.")
            continue

        line, text = st
        if not text.startswith(VOCABULARY):
            err(line, f"Section '{heading}' has Status '{text[:40]}', which does not "
                      f"start with one of: {', '.join(VOCABULARY)}.")
        elif text.startswith(SHIPPED):
            shipped.append((line, heading))

    if strict:
        for line, heading in shipped:
            err(line, f"Section '{heading}' is still marked '{SHIPPED} ...' here. A "
                      f"shipped item leaves docs/TODO.md when its release folds — "
                      f"remove the section, or cut it down to what it left undecided "
                      f"and set Status back to Open. See docs/CONTRIBUTING.md.")
    elif shipped:
        # Not an error off the release branches: an item is legitimately marked
        # shipped on the branch that shipped it, right up until the fold.
        for _, heading in shipped:
            print(f"  note: '{heading}' is marked shipped — due to leave at the fold.")

    if found:
        print(f"\n{len(found)} roadmap problem(s) in {todo}. "
              f"Rules: docs/CONTRIBUTING.md")
        return 1
    print(f"OK - all {len(secs)} roadmap section(s) carry a valid Status"
          f"{', and none are still marked shipped' if strict else ''}.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
