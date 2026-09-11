#!/usr/bin/env python3
"""Check that documentation does not cite DOCUMENTS which do not exist.

A dead reference is worse than a missing one: it sends a reader looking for
guidance that was never written, and a remembered-but-wrong filename reads
exactly like a real citation. This repository has had a document confidently
cited -- repeatedly, as evidence -- that had never existed on any branch.

Deliberately narrow. It checks cited `.md` files only, and only that a file of
that NAME exists somewhere in the tree: prose abbreviates source paths
(`views/conflict_resolution.cljs`, `src/cljs/.../events.cljs`) constantly and
flagging those buries the one finding that matters under noise a reader learns
to skip.

Two exemptions, both because the citation is legitimately to something not here:
docs/TODO.md names files that a roadmap item proposes to create, and a line
saying in prose that a doc lives on another branch is doing the right thing
already.

    scripts/lint-doc-links.py                # check docs/, CLAUDE.md, the READMEs
    scripts/lint-doc-links.py path/to/f.md   # check one file

Exit status is 0 when clean and 1 when anything is found, which is the whole
contract with CI.
"""

import os
import re
import sys

DEFAULT_TARGETS = ["CLAUDE.md", "docs", "test/browser/README.md"]

# A path we are prepared to judge: repo-relative, with a suffix we know lives in
# the tree. Bare basenames (content_specs.cljc) are resolved by search, since
# prose names namespaces that way and the file genuinely exists somewhere.
PATH_RE = re.compile(r"`([A-Za-z0-9_][A-Za-z0-9_./-]*\.md)`"
                     r"|\]\(([A-Za-z0-9_][A-Za-z0-9_./-]*\.md)\)")

# Named to show a shape, not to point at a file.
IGNORE = {"path/to/file.md", "path/to/f.md", "file.md"}

# A roadmap names files an item proposes to create; that is not a dead link.
SKIP_FILES = {"docs/TODO.md"}

# A line that says the doc is elsewhere has already told the reader the truth.
ELSEWHERE = re.compile(r"another branch|on `?refactor/|lives on|resolve only there", re.I)


def find_by_basename(name, root="."):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "node_modules", "target")]
        if name in filenames:
            return True
    return False


def check(path, found):
    if path.replace("./", "") in SKIP_FILES:
        return
    try:
        text = open(path, encoding="utf-8").read()
    except (FileNotFoundError, UnicodeDecodeError):
        return
    lines = text.splitlines()
    for n, line in enumerate(lines, 1):
        # A cross-branch note may sit a line or two above the citation.
        context = " ".join(lines[max(0, n - 4):n + 1])
        if ELSEWHERE.search(context):
            continue
        for m in PATH_RE.finditer(line):
            ref = m.group(1) or m.group(2)
            if ref in IGNORE or ref.startswith("http"):
                continue
            if os.path.exists(ref) or find_by_basename(os.path.basename(ref)):
                continue
            found.append((path, n, ref))
            print(f"::error file={path},line={n}::Cites the document '{ref}', and no file of "
                  f"that name exists anywhere in this tree. Check the name before citing it; "
                  f"if it genuinely lives on another branch, say so in prose.")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    targets = args or DEFAULT_TARGETS
    found = []
    checked = 0
    for t in targets:
        if os.path.isdir(t):
            for dirpath, dirnames, filenames in os.walk(t):
                dirnames[:] = [d for d in dirnames if d != ".git"]
                for f in sorted(filenames):
                    if f.endswith(".md"):
                        check(os.path.join(dirpath, f), found)
                        checked += 1
        elif os.path.exists(t):
            check(t, found)
            checked += 1

    if found:
        print(f"\n{len(found)} dead document reference(s) across {checked} file(s).")
        return 1
    print(f"OK - every document cited across {checked} file(s) exists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
