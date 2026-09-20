#!/usr/bin/env python3
"""Grep Clojure sources, separating live code from `#_` discarded forms.

`#_` discards the NEXT form, however long it is. A plain grep hit inside one looks
exactly like live code, so `classes.cljc` reports a Beast Master and a Circle of the
Moon that the app has never compiled -- 27 subclasses are discarded that way, one per
class kept, the SRD 5.1 set.

Default output reports both, in separate sections. It does NOT hide discards: knowing
the code exists but is switched off is usually the answer you wanted.

Usage:
  scripts/clj-grep.py PATTERN [PATH ...]     both sections (default: src/ test/)
  scripts/clj-grep.py --live PATTERN [...]   live hits only, as path:line:text
  scripts/clj-grep.py --dead PATTERN [...]   discarded hits only, same shape
  scripts/clj-grep.py -i PATTERN [...]       case-insensitive

Exits 0 when the selected section has hits, 1 when it does not (grep convention).
"""

import os
import re
import sys

CLJ_EXT = (".clj", ".cljs", ".cljc", ".edn")
OPEN = {"(": ")", "[": "]", "{": "}"}
CLOSE = set(")]}")
# Reader prefixes that bind to the form after them; a discard has to step over these
# to find the form it actually kills.
PREFIX = set("'`~@^#")


def discarded_ranges(src):
    """Character spans killed by `#_`, as a list of (start, end) index pairs.

    One pass with a reader that knows strings, character literals and line comments,
    because each of those can hold delimiters that would otherwise unbalance the scan.
    """
    spans = []
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == ";":
            i = src.find("\n", i)
            if i < 0:
                break
        elif c == '"':
            i = skip_string(src, i)
        elif c == "\\":
            i = skip_char_literal(src, i)
        elif c == "#" and i + 1 < n and src[i + 1] == '"':
            i = skip_string(src, i + 1)
        elif c == "#" and i + 1 < n and src[i + 1] == "_":
            # `#_#_ a b` discards two forms, so count the run before consuming any.
            j, pending = i, 0
            while src.startswith("#_", j):
                pending += 1
                j += 2
                while j < n and src[j] in " \t\n\r,":
                    j += 1
            for _ in range(pending):
                j = skip_ws_and_comments(src, j)
                if j >= n:
                    break
                end = form_end(src, j)
                spans.append((j, end))
                j = end
            i = j
        else:
            i += 1
    return spans


def skip_string(src, i):
    """Index just past the string literal opening at `i`."""
    i += 1
    while i < len(src):
        if src[i] == "\\":
            i += 2
            continue
        if src[i] == '"':
            return i + 1
        i += 1
    return i


def skip_char_literal(src, i):
    """Index just past a `\\x` / `\\newline` / `\\(` literal opening at `i`."""
    i += 1
    if i < len(src):
        i += 1  # the character itself, even when it is a delimiter or a quote
    while i < len(src) and (src[i].isalnum() or src[i] == "-"):
        i += 1  # named literals: \newline, \space,
    return i


def skip_ws_and_comments(src, i):
    n = len(src)
    while i < n:
        if src[i] in " \t\n\r,":
            i += 1
        elif src[i] == ";":
            nl = src.find("\n", i)
            i = n if nl < 0 else nl + 1
        else:
            return i
    return i


def form_end(src, i):
    """Index just past the single form starting at `i`."""
    n = len(src)
    # Step over reader prefixes (' ` ~ @ ^ # and #{ ), plus metadata maps, which bind
    # to the form after them rather than being the form.
    while i < n and src[i] in PREFIX:
        if src[i] == "#" and i + 1 < n and src[i + 1] == '"':
            return skip_string(src, i + 1)
        if src[i] == "^":
            i += 1
            i = form_end(src, skip_ws_and_comments(src, i))
            i = skip_ws_and_comments(src, i)
            continue
        i += 1
        if i < n and src[i] in OPEN:
            break
    if i >= n:
        return n
    c = src[i]
    if c == '"':
        return skip_string(src, i)
    if c == "\\":
        return skip_char_literal(src, i)
    if c in OPEN:
        depth, i = 0, i
        while i < n:
            ch = src[i]
            if ch == ";":
                nl = src.find("\n", i)
                i = n if nl < 0 else nl
            elif ch == '"':
                i = skip_string(src, i)
                continue
            elif ch == "\\":
                i = skip_char_literal(src, i)
                continue
            elif ch in OPEN:
                depth += 1
            elif ch in CLOSE:
                depth -= 1
                if depth == 0:
                    return i + 1
            i += 1
        return n
    # An atom: runs to the next delimiter or whitespace.
    while i < n and src[i] not in " \t\n\r,()[]{}\";":
        i += 1
    return i


def line_starts(src):
    starts, pos = [0], src.find("\n")
    while pos >= 0:
        starts.append(pos + 1)
        pos = src.find("\n", pos + 1)
    return starts


def scan(path, rx):
    """Hits in one file as (line_no, text, discard_line_or_None)."""
    try:
        src = open(path, encoding="utf-8", errors="replace").read()
    except OSError:
        return []
    spans = discarded_ranges(src)
    starts = line_starts(src)

    def line_of(idx):
        lo, hi = 0, len(starts) - 1
        while lo < hi:
            mid = (lo + hi + 1) // 2
            if starts[mid] <= idx:
                lo = mid
            else:
                hi = mid - 1
        return lo + 1

    out = []
    for m in rx.finditer(src):
        ln = line_of(m.start())
        end = src.find("\n", m.start())
        text = src[starts[ln - 1]:(len(src) if end < 0 else end)]
        killed = next((s for s, e in spans if s <= m.start() < e), None)
        out.append((ln, text, None if killed is None else line_of(killed)))
    return out


def walk(paths):
    for p in paths:
        if os.path.isfile(p):
            yield p
        for root, dirs, files in os.walk(p):
            dirs[:] = [d for d in dirs if d not in {".git", "node_modules", "target", "out"}]
            for f in sorted(files):
                if f.endswith(CLJ_EXT):
                    yield os.path.join(root, f)


def main(argv):
    mode, flags = "both", 0
    args = []
    for a in argv:
        if a == "--live":
            mode = "live"
        elif a == "--dead":
            mode = "dead"
        elif a in ("-i", "--ignore-case"):
            flags |= re.IGNORECASE
        elif a in ("-h", "--help"):
            print(__doc__)
            return 0
        else:
            args.append(a)
    if not args:
        print(__doc__, file=sys.stderr)
        return 2

    pattern, paths = args[0], args[1:] or ["src", "test"]
    paths = [p for p in paths if os.path.exists(p)]
    rx = re.compile(pattern, flags)

    live, dead = [], []
    for path in walk(paths):
        for ln, text, killed in scan(path, rx):
            (dead if killed else live).append((path, ln, text, killed))

    if mode in ("live", "dead"):
        rows = live if mode == "live" else dead
        for path, ln, text, _ in rows:
            print(f"{path}:{ln}:{text}")
        return 0 if rows else 1

    if live:
        print(f"LIVE ({len(live)})")
        for path, ln, text, _ in live:
            print(f"  {path}:{ln}:{text.strip()[:100]}")
    if dead:
        if live:
            print()
        print(f"DISCARDED BY #_ ({len(dead)})  -- present in the file, never compiled")
        for path, ln, text, killed in dead:
            print(f"  {path}:{ln}:{text.strip()[:100]}")
            print(f"      discarded at {path}:{killed}")
    if not live and not dead:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
