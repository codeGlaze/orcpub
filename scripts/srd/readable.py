#!/usr/bin/env python3
"""Make a readable SRD transcript from a normalized one.

Usage: readable.py IN.norm.txt OUT.txt [--report]

Rejoins words split across line breaks, drops the per-page footer, and keeps the
<<<PAGE n>>> markers, so a grep finds whole words and the nearest marker above a hit
is its page. Line breaks are otherwise kept: the column layout is not reflowed.

A hyphen at a line end is ambiguous: a split word ("experi-/ence") or a real compound
broken at its hyphen ("long-/term"). Decided by the document's own vocabulary:
  joined form used elsewhere           -> join      (experience)
  hyphenated form used elsewhere       -> keep "-"  (long-term)
  neither, but listed in hyphenation.tsv -> as listed
  neither, not listed                  -> whatever this document does with the decided
                                          ones (5.1 breaks only at real hyphens, 5.2.1
                                          hyphenates syllables), listed by --report to be
                                          decided and added to hyphenation.tsv
"""
import re, sys
from collections import Counter

FOOTERS = [
    re.compile(r'^Not for resale\. Permission granted to print or photocopy this document '
               r'for personal use only\. System Reference Document 5\.1 \d+$'),
    re.compile(r'^System Reference Document 5\.2\.\d\d+$'),
]
import os
# Forms the vocabulary cannot decide, decided once and kept: scripts/srd/hyphenation.tsv.
DECIDED = {}
for _l in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), 'hyphenation.tsv'), encoding='utf-8'):
    if _l.startswith('#') or _l.startswith('form\t') or not _l.strip(): continue
    _form, _decision = _l.split('\t')[:2]
    DECIDED[_form] = _decision == 'keep'
# the second half carries its trailing punctuation and one following space or newline, so
# the rest of that line does not start with a stray space or a stranded "."
SPLIT = re.compile(r"([A-Za-z]+)-\n(<<<PAGE \d+>>>\n)?([a-z]+)([^\s]*)( |\n)?")

def main(src, dst, report):
    lines = [l for l in open(src, encoding='utf-8').read().split('\n')
             if not any(f.match(l) for f in FOOTERS)]
    text = '\n'.join(lines)
    # vocabulary from text with every line-end split removed, so splits don't vote
    body = SPLIT.sub(' ', text)
    words = Counter(re.findall(r"[a-z]+(?:-[a-z]+)*", body.lower()))
    unknown = []
    keeps = joins = 0
    for m in SPLIT.finditer(text):
        j, h = (m.group(1) + m.group(3)).lower(), f'{m.group(1)}-{m.group(3)}'.lower()
        if words[h] and not words[j]: keeps += 1
        elif words[j]: joins += 1
    default_keep = keeps > joins
    def fix(m):
        a, page, b, tail = m.group(1), m.group(2) or '', m.group(3), m.group(4)
        joined, hyph = (a + b).lower(), f'{a}-{b}'.lower()
        undecided = not words[joined] and not words[hyph]
        if undecided and hyph in DECIDED:
            keep = DECIDED[hyph]
        else:
            keep = (words[hyph] and not words[joined]) or (undecided and default_keep)
            if undecided:
                unknown.append(f'{a}-{b} -> {a}-{b}' if keep else f'{a}-{b} -> {a + b}')
        out = f'{a}-{b}' if keep else a + b
        # the word moves to the line it started on; a page marker in between moves after it
        return out + tail + ('\n' + page.rstrip('\n') if page else '') + '\n'
    out = SPLIT.sub(fix, text)
    open(dst, 'w', encoding='utf-8').write(out)
    print(f'  {dst}: {len(SPLIT.findall(text))} line-end hyphens ({keeps} kept, {joins} joined by '
          f'vocabulary); {len(unknown)} not in hyphenation.tsv, decided by the default, which is to {"keep" if default_keep else "join"}')
    if report:
        for u in sorted(set(unknown)): print('    ', u)

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2], '--report' in sys.argv)
