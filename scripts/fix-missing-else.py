#!/usr/bin/env python3
"""
Fix clj-kondo 'Missing else branch' warnings by replacing:
  if     -> when
  if-let -> when-let
  if-not -> when-not

Reads kondo output from stdin, applies fixes to source files.
Usage: lein lint 2>&1 | grep 'Missing else branch' | python3 scripts/fix-missing-else.py
"""

import sys
import re
from collections import defaultdict

def parse_warnings(lines):
    """Parse kondo output into {file: [(line, col), ...]} sorted by line desc."""
    by_file = defaultdict(list)
    for line in lines:
        # Format: src/clj/foo.clj:42:3: warning: Missing else branch.
        m = re.match(r'^(.+?):(\d+):(\d+):', line.strip())
        if m:
            by_file[m.group(1)].append((int(m.group(2)), int(m.group(3))))
    # Sort each file's locations by line descending (process bottom-up to avoid offset shifts)
    for f in by_file:
        by_file[f].sort(reverse=True)
    return by_file

def fix_at(source_line, col):
    """Replace if/if-let/if-not with when/when-let/when-not at the given column (1-based)."""
    idx = col - 1  # convert to 0-based

    # kondo column points to '(' — the 'if' token starts at idx+1
    if source_line[idx:idx+1] == '(':
        idx += 1

    rest = source_line[idx:]

    if rest.startswith('if-let'):
        return source_line[:idx] + 'when-let' + source_line[idx + 6:]
    elif rest.startswith('if-not'):
        return source_line[:idx] + 'when-not' + source_line[idx + 6:]
    elif rest.startswith('if-some'):
        return source_line[:idx] + 'when-some' + source_line[idx + 7:]
    elif rest.startswith('if-first'):
        return source_line[:idx] + 'when-first' + source_line[idx + 8:]
    elif rest.startswith('if'):
        # Make sure it's standalone 'if', not 'if-' something else
        after = source_line[idx + 2:idx + 3] if len(source_line) > idx + 2 else ''
        if after in (' ', '\t', '\n', ''):
            return source_line[:idx] + 'when' + source_line[idx + 2:]
        else:
            print(f"  SKIP: unexpected token at col {col}: {rest[:20]}", file=sys.stderr)
            return source_line
    else:
        print(f"  SKIP: no if at col {col}: {rest[:20]}", file=sys.stderr)
        return source_line

def main():
    warnings = parse_warnings(sys.stdin)
    total_fixed = 0
    total_skipped = 0

    for filepath, locations in sorted(warnings.items()):
        with open(filepath, 'r') as f:
            lines = f.readlines()

        fixed = 0
        for line_num, col in locations:
            if line_num > len(lines):
                print(f"  SKIP: {filepath}:{line_num} beyond file length", file=sys.stderr)
                total_skipped += 1
                continue

            old = lines[line_num - 1]
            new = fix_at(old, col)
            if old != new:
                lines[line_num - 1] = new
                fixed += 1
            else:
                total_skipped += 1

        if fixed > 0:
            with open(filepath, 'w') as f:
                f.writelines(lines)

        total_fixed += fixed
        print(f"  {filepath}: {fixed} fixed" + (f", {len(locations) - fixed} skipped" if len(locations) > fixed else ""))

    print(f"\nTotal: {total_fixed} fixed, {total_skipped} skipped")

if __name__ == '__main__':
    main()
