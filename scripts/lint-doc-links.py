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

The knowledge base lives on the agents/develop branch, not in this working
tree, so a code branch citing a KB doc is correct and must not be flagged.
Those are resolved against the branch with git ls-tree -- the same way
START-HERE.md tells you to read the KB, and the same way check-docs.sh reads
origin/develop for its superset check. A citation is therefore VERIFIED rather
than excused: prose saying "this lives on another branch" no longer suppresses
anything, because the branch itself can be consulted.

One exemption remains: docs/TODO.md names files that a roadmap item proposes to
create, which is not a dead link.

    scripts/lint-doc-links.py                # check docs/, CLAUDE.md, the READMEs
    scripts/lint-doc-links.py path/to/f.md   # check one file

Exit status is 0 when clean and 1 when anything is found, which is the whole
contract with CI.
"""

import os
import re
import subprocess
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

# Branches that legitimately hold documents a code branch cites. agents/develop
# carries the knowledge base; the refactor line carries its own in-flight docs.
DOC_BRANCHES = ("origin/agents/develop", "origin/refactor/content-extensibility")


def branch_docs():
    """Basenames of every markdown file on the branches that hold documentation.
    Empty when none are fetched -- the check then degrades to local-only rather
    than failing every cross-branch citation on a shallow clone."""
    names = set()
    for br in DOC_BRANCHES:
        try:
            out = subprocess.run(["git", "ls-tree", "-r", "--name-only", br],
                                 capture_output=True, text=True, timeout=30)
        except (OSError, subprocess.SubprocessError):
            continue
        if out.returncode != 0:
            continue
        for line in out.stdout.splitlines():
            if line.endswith(".md"):
                names.add(os.path.basename(line))
    return names


def find_by_basename(name, root="."):
    for dirpath, dirnames, filenames in os.walk(root):
        dirnames[:] = [d for d in dirnames if d not in (".git", "node_modules", "target")]
        if name in filenames:
            return True
    return False


def check(path, found, elsewhere):
    if path.replace("./", "") in SKIP_FILES:
        return
    try:
        text = open(path, encoding="utf-8").read()
    except (FileNotFoundError, UnicodeDecodeError):
        return
    lines = text.splitlines()
    for n, line in enumerate(lines, 1):
        for m in PATH_RE.finditer(line):
            ref = m.group(1) or m.group(2)
            if ref in IGNORE or ref.startswith("http"):
                continue
            base = os.path.basename(ref)
            if os.path.exists(ref) or find_by_basename(base) or base in elsewhere:
                continue
            found.append((path, n, ref))
            print(f"::error file={path},line={n}::Cites the document '{ref}', which exists "
                  f"neither in this tree nor on {' or '.join(DOC_BRANCHES)}. Check the name "
                  f"before citing it.")


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    targets = args or DEFAULT_TARGETS
    found = []
    checked = 0
    elsewhere = branch_docs()
    if not elsewhere:
        print("  note: no documentation branch fetched — cross-branch citations unverifiable")
    for t in targets:
        if os.path.isdir(t):
            for dirpath, dirnames, filenames in os.walk(t):
                dirnames[:] = [d for d in dirnames if d != ".git"]
                for f in sorted(filenames):
                    if f.endswith(".md"):
                        check(os.path.join(dirpath, f), found, elsewhere)
                        checked += 1
        elif os.path.exists(t):
            check(t, found, elsewhere)
            checked += 1

    if found:
        print(f"\n{len(found)} dead document reference(s) across {checked} file(s).")
        return 1
    print(f"OK - every document cited across {checked} file(s) exists.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
