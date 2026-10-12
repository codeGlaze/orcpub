#!/usr/bin/env python3
"""Flag text we added to open5e's SRD data that the SRD does not say.

Usage: check-srd-fidelity.py OPEN5E_CLONE SRD52_WORKTREE [--base origin/staging]

Compares each SRD fixture file at HEAD with --base. For every text field a commit added or changed
(new records included), it looks for word runs that appear neither in the SRD transcript for that
edition nor in the field's old value. Numbers and dice are normalised ("an additional 35" matches
the SRD's "an additional 5"), as are case, punctuation and markdown, so a correct fix passes and
invented wording does not. Names are skipped: their only allowed change is case.

A flag is not proof of a mistake (a structural fix can legitimately reorder words); it is the list
a person checks against the SRD before a PR. A flag checked and kept goes in
srd-fidelity-reviewed.json with its reason, and is then reported as reviewed. Exit status is 1
while anything is unreviewed. See docs/kb/open5e-upstream-notes.md, "SRD fidelity".
"""
import json, re, subprocess, sys
from pathlib import Path

N = 3          # word-run length; three words is enough to catch invented phrasing
EDITIONS = {'srd-2014': 'SRD-5.1.readable.txt', 'srd-2024': 'SRD-5.2.1.readable.txt'}
SKIP_FIELDS = {'name', 'key', 'parent', 'type', 'document', 'category'}


def tokens(text):
    """Text -> normalised words: lower case, numbers and dice as '#', markup and punctuation gone."""
    t = text.lower().replace('’', "'").replace('‘', "'").replace('—', ' ').replace('–', ' ')
    t = re.sub(r'\d+d\d+', '#d#', t)
    t = re.sub(r'\d+(st|nd|rd|th)\b', '#th', t)
    t = re.sub(r'\d+', '#', t)
    t = re.sub(r"[^a-z#' ]+", ' ', t)
    return [w.strip("'") for w in t.split() if w.strip("'")]


def runs(words):
    """The set of N-word runs in a word list."""
    return {tuple(words[i:i + N]) for i in range(len(words) - N + 1)}


def line_runs(text):
    """N-word runs of a data value, never across a line break (a literal backslash-n counts)."""
    out = set()
    for line in re.split(r'\r?\n|\\n', text):
        out |= runs(tokens(line))
    return out


def strings(fields):
    """(field, value) for the text fields worth checking."""
    for k, v in fields.items():
        if isinstance(v, str) and v.strip() and k not in SKIP_FIELDS:
            yield k, v


def at(clone, rev, path):
    """A fixture file's records at a revision, by pk; empty if the file is new."""
    r = subprocess.run(['git', '-C', clone, 'show', f'{rev}:{path}'], capture_output=True, text=True)
    return {x['pk']: x for x in json.loads(r.stdout)} if r.returncode == 0 else {}


def main(argv):
    clone, srd52 = argv[1], argv[2]
    base = argv[argv.index('--base') + 1] if '--base' in argv else 'origin/staging'
    changed = subprocess.run(['git', '-C', clone, 'diff', '--name-only', base, 'HEAD', '--',
                              'data/v2/wizards-of-the-coast/srd-2014', 'data/v2/wizards-of-the-coast/srd-2024'],
                             capture_output=True, text=True).stdout.split()
    srd = {ed: runs(tokens(open(Path(srd52) / 'resources/srd' / t, encoding='utf-8').read()))
           for ed, t in EDITIONS.items()}
    reviewed = {(r['file'], str(r['pk']), r['field']): r['reason']
                for r in json.load(open(Path(__file__).with_name('srd-fidelity-reviewed.json')))}
    flagged = 0
    for path in changed:
        ed = path.split('/')[3]
        old, new = at(clone, base, path), at(clone, 'HEAD', path)
        for pk, rec in new.items():
            before = dict(strings(old.get(pk, {}).get('fields', {})))
            for field, value in strings(rec['fields']):
                if before.get(field) == value:
                    continue
                novel = line_runs(value) - srd[ed] - line_runs(before.get(field, ''))
                why = reviewed.get((path.split('/')[-1], str(pk), field))
                if novel and why:
                    print(f'reviewed: {path.split("/")[-1]} {pk} {field}: {why}')
                elif novel:
                    flagged += 1
                    sample = ' | '.join(' '.join(r) for r in sorted(novel)[:4])
                    print(f'{path.split("/")[-1]} {pk} {field}: {len(novel)} run(s) not in the SRD: {sample}')
                    print(f'    {value[:160]!r}')
    print(f'{flagged} field(s) flagged across {len(changed)} changed file(s)')
    return 1 if flagged else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
