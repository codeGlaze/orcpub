#!/usr/bin/env python3
"""Lint warnings may only go down.

Usage: scripts/lint-ratchet.py <lein-lint-output> [baseline]   (baseline: test/lint-baseline.txt)
       scripts/lint-ratchet.py --write <lein-lint-output> [baseline]

Reads clj-kondo's "path:line:col: warning: message" lines and compares them with the baseline,
one "path: message" per line, ignoring line numbers so moved code does not count as new. Exits 1
when a warning is not in the baseline, printing each one; exits 1 too when a baseline warning is
gone, asking for the line to be removed, so the baseline only shrinks. --write rewrites the
baseline from the output. docs/kb/ci-signal-plan.md
"""
import collections
import re
import sys

WARNING = re.compile(r"^(?P<path>[^:\s]+):\d+:\d+: warning: (?P<msg>.*)$")


def warnings(text):
    """The warnings in clj-kondo output `text`, as a Counter of "path: message" strings."""
    found = collections.Counter()
    for line in text.splitlines():
        m = WARNING.match(line.strip())
        if m:
            found["%s: %s" % (m.group("path"), m.group("msg").strip())] += 1
    return found


def read_baseline(path):
    """The baseline file at `path` as a Counter; comment lines (#) and blanks are skipped."""
    with open(path, encoding="utf-8") as f:
        return collections.Counter(l.rstrip("\n") for l in f if l.strip() and not l.startswith("#"))


def write_baseline(path, found):
    """Writes Counter `found` to `path`, sorted, one line per occurrence, under a short header."""
    with open(path, "w", encoding="utf-8") as f:
        f.write("# Lint warnings that already exist; scripts/lint-ratchet.py fails on any other.\n")
        f.write("# Fix one, then delete its line. Never add a line to hide a new warning.\n")
        for key in sorted(found.elements()):
            f.write(key + "\n")


def main(argv):
    """Runs the ratchet per the module docstring; returns the exit code."""
    write = "--write" in argv
    args = [a for a in argv if a != "--write"]
    if not args:
        print(__doc__, file=sys.stderr)
        return 2
    out = open(args[0], encoding="utf-8").read()
    base_path = args[1] if len(args) > 1 else "test/lint-baseline.txt"
    found = warnings(out)
    if write:
        write_baseline(base_path, found)
        print("lint ratchet: wrote %d warnings to %s" % (sum(found.values()), base_path))
        return 0
    base = read_baseline(base_path)
    new, gone = found - base, base - found
    for key in sorted(new.elements()):
        print("::error::New lint warning (fix it; do not add it to %s): %s" % (base_path, key))
    for key in sorted(gone.elements()):
        print("::error::Fixed, so remove this line from %s: %s" % (base_path, key))
    print("lint ratchet: %d warnings, %d known, %d new, %d to remove from the baseline"
          % (sum(found.values()), sum((found & base).values()), sum(new.values()), sum(gone.values())))
    return 1 if (new or gone) else 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
