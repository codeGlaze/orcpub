#!/usr/bin/env python3
"""Check every change we have made to open5e's SRD data against rules learned from real mistakes.

Usage: check-srd-data.py OPEN5E_CLONE SRD52_WORKTREE [--base origin/staging] [--update-baseline] [--all]

Runs over the whole branch (HEAD against --base), not one commit, so a careless commit from
last week fails today's run and nobody has to remember to recheck it. About a second.

Rules (each from a mistake made on 2026-10-11/12; docs/kb/open5e-upstream-notes.md, "SRD data checks"):
  fidelity        new or changed text uses SRD wording: every three-word run is in the SRD
                  transcript or the old value (numbers, dice, case, markup normalised)
  name-case       a changed name differs only in letter case
  empty-option    a casting option sets at least one field
  slot-gap        a spell's slot options run unbroken from the first to 9
  dice-only       damage_roll holds dice ("4d6", "2d8 + 3"), nothing else
  upcast-math     where the higher_level text is a plain step ("increases by 1d6 for each slot level
                  above 3rd", "one additional creature for each slot level above 1st"), every
                  option's value is the spell's base plus the step per slot
  slot-below-base no slot option at or below the spell's own level
  desc-math       numbers in a desc agree with the damage_roll beside it and with the step sentence
                  (an absolute "deals 7d8" equals damage_roll; a delta "increases by 3d4" equals the
                  step times the slots above the base)
  lint-ratchet    no lint-open5e.py count rises above srd-lint-baseline.json
  reformat        a diff changes lines only inside records whose content changed

A finding a person has checked and kept goes in srd-data-reviewed.json (rule, file, pk, field,
reason; pk may be a list) and is then reported as reviewed. Exit status 1 while anything is unreviewed.
--all also audits every casting option in both editions, not only the branch's changes (use it to
find upstream errors; the hook does not pass it). --update-baseline lowers the lint baseline to today's counts (never raises it).
"""
import collections, importlib.util, json, re, subprocess, sys
from pathlib import Path

HERE = Path(__file__).parent
N = 3
EDITIONS = {'srd-2014': 'SRD-5.1.readable.txt', 'srd-2024': 'SRD-5.2.1.readable.txt'}
SKIP_TEXT = {'name', 'key', 'parent', 'type', 'document', 'category'}
OPTION_FIELDS = ('damage_roll', 'target_count', 'duration', 'range', 'concentration', 'shape_size', 'desc')
DICE = re.compile(r'^\d+d\d+(?: ?[+-] ?\d+)?$')
WORDS = {'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5}


def tokens(text):
    """Text -> normalised words: lower case, numbers and dice as '#', markup and punctuation gone."""
    t = text.lower().replace('’', "'").replace('‘', "'").replace('—', ' ').replace('–', ' ')
    t = re.sub(r'\d+d\d+', '#d#', t)
    t = re.sub(r'\d+(st|nd|rd|th)\b', '#th', t)
    t = re.sub(r'\d+', '#', t)
    t = re.sub(r'\b(one|two|three|four|five|six|seven|eight|nine|ten|eleven|twelve)\b', '#', t)
    t = re.sub(r"[^a-z#' ]+", ' ', t)
    return [w.strip("'") for w in t.split() if w.strip("'")]


def runs(words):
    """The set of N-word runs in a word list."""
    return {tuple(words[i:i + N]) for i in range(len(words) - N + 1)}


def line_runs(text):
    """N-word runs of a data value, never across a line break (a literal backslash-n counts)."""
    out = set()
    for line in re.split(r'\r?\n|\\n', text or ''):
        out |= runs(tokens(line))
    return out


def git(clone, *args):
    """Runs git in the clone; returns (returncode, stdout)."""
    r = subprocess.run(['git', '-C', clone, *args], capture_output=True, text=True)
    return r.returncode, r.stdout


def records(clone, rev, path):
    """A fixture file's records at a revision, by pk; empty if the file does not exist there."""
    code, out = git(clone, 'show', f'{rev}:{path}')
    return {x['pk']: x for x in json.loads(out)} if code == 0 else {}


class Report:
    """Collects findings, splitting reviewed ones out by srd-data-reviewed.json."""

    def __init__(self):
        self.reviewed = {(r['rule'], r['file'], str(pk), r.get('field', '')): r['reason']
                         for r in json.load(open(HERE / 'srd-data-reviewed.json'))
                         for pk in (r['pk'] if isinstance(r['pk'], list) else [r['pk']])}
        self.found = {}
        self.checked = self.with_desc = 0     # desc-math coverage: records verified / records with a desc

    def add(self, rule, file, pk, field, detail):
        """Records one finding under its rule."""
        why = self.reviewed.get((rule, file, str(pk), field))
        self.found.setdefault(rule, []).append((file, pk, field, detail, why))

    def print(self, rules):
        """Prints a pass/fail line per rule and each finding; returns the unreviewed count."""
        bad = 0
        for rule in rules:
            hits = self.found.get(rule, [])
            open_ = [h for h in hits if not h[4]]
            bad += len(open_)
            print(f'{"FAIL" if open_ else "ok  "}  {rule:13} {len(open_)} open, {len(hits) - len(open_)} reviewed')
            for file, pk, field, detail, why in hits:
                print(f'        {"reviewed: " if why else ""}{file} {pk} {field}: {detail}'[:220])
        return bad


def check_fidelity(rep, path, ed, old, new, srd):
    """fidelity and name-case over one changed file."""
    f = path.split('/')[-1]
    for pk, rec in new.items():
        before = old.get(pk, {}).get('fields', {})
        for field, value in rec['fields'].items():
            if not isinstance(value, str) or before.get(field) == value or not value.strip():
                continue
            if field == 'name':
                if before.get('name') is not None and before['name'].lower() != value.lower():
                    rep.add('name-case', f, pk, field, f'{before["name"]!r} -> {value!r}')
                continue
            if field in SKIP_TEXT:
                continue
            novel = line_runs(value) - srd[ed] - line_runs(before.get(field) or '')
            if novel:
                rep.add('fidelity', f, pk, field,
                        ' | '.join(' '.join(r) for r in sorted(novel)[:3]) + f'  in {value[:90]!r}')


def check_options(rep, ed, old, new, spells, audit=False):
    """empty-option, slot-gap, dice-only, slot-below-base, upcast-math and desc-math over a
    SpellCastingOption file. `audit` checks every record, not only the ones the branch changed."""
    f = f'{ed}/SpellCastingOption.json' if audit else 'SpellCastingOption.json'
    if audit:
        old = {}
    touched = {r['fields']['parent'] for pk, r in new.items() if old.get(pk) != r}
    by = {}
    for pk, r in new.items():
        o = r['fields']
        if o['parent'] not in touched:
            continue
        if old.get(pk) != r and o['type'] not in ('default', 'ritual') \
                and all(o.get(k) in (None, '') for k in OPTION_FIELDS):
            rep.add('empty-option', f, pk, o['type'], f'{o["parent"]} sets no field')
        if old.get(pk) != r and o.get('damage_roll') not in (None, '') and not DICE.match(o['damage_roll']):
            rep.add('dice-only', f, pk, o['type'], f'{o["parent"]} damage_roll {o["damage_roll"]!r}')
        if o['type'].startswith('slot_level_'):
            lvl = int(o['type'].split('_')[-1])
            by.setdefault(o['parent'], {})[lvl] = (pk, o)
            if old.get(pk) != r and lvl <= (spells.get(o['parent'], {}).get('level') or 0):
                rep.add('slot-below-base', f, pk, o['type'], f'{o["parent"]} is a level {spells[o["parent"]]["level"]} spell')
            check_desc_math(rep, f, pk, lvl, o, spells.get(o['parent'], {}))
    for parent, slots in by.items():
        missing = [s for s in range(min(slots), 10) if s not in slots]
        if missing:
            rep.add('slot-gap', f, parent, '', f'slots {sorted(slots)} miss {missing}')
        sp = spells.get(parent, {})
        h = sp.get('higher_level') or ''
        m = re.search(r'increases? by (\d+)d(\d+) for each (?:spell )?slot level above (\d+)', h)
        t = re.search(r'\b(one|two|three|\d+) additional (?:creature|target|undead|object|beast)s? for each (?:spell )?slot level above (\d+)', h, re.I)
        for lvl, (pk, o) in sorted(slots.items()):
            if m and sp.get('damage_roll') and DICE.match(sp['damage_roll']) and o.get('damage_roll'):
                n, die, above = int(m.group(1)), m.group(2), int(m.group(3))
                bn, bd = re.match(r'(\d+)d(\d+)', sp['damage_roll']).groups()
                want = f'{int(bn) + n * (lvl - above)}d{die}' if bd == die else None
                got = re.match(r'(\d+d\d+)', o['damage_roll']).group(1)
                if want and got != want:
                    rep.add('upcast-math', f, pk, o['type'], f'{parent} {got}, the step rule gives {want}')
            if t and o.get('target_count') is not None and sp.get('target_count'):
                step = int(t.group(1)) if t.group(1).isdigit() else WORDS[t.group(1)]
                want = sp['target_count'] + step * (lvl - int(t.group(2)))
                if o['target_count'] != want:
                    rep.add('upcast-math', f, pk, o['type'], f'{parent} target_count {o["target_count"]}, the step rule gives {want}')


STEP_DICE = re.compile(r'increases? by (\d+)d(\d+) for each (?:spell )?slot level above (\d+)', re.I)
STEP_NUM = re.compile(r'(?:by (?:an additional )?|\b)(one|two|three|four|five|\d+)(?! ?d\d)(?: additional)?\b[^.]*? for each (?:spell )?slot level above (\d+)', re.I)
NUMBER = {'one': 1, 'two': 2, 'three': 3, 'four': 4, 'five': 5}


def check_desc_math(rep, f, pk, lvl, o, sp):
    """desc-math: numbers in a desc agree with the damage_roll beside it and with the spell's step
    sentence: an absolute "deals 7d8" / "increases to 7d8" equals damage_roll; a delta "increases by
    3d4" / "additional 4d8" / "additional 35" equals the step times the slots above the base."""
    d, h = o.get('desc') or '', sp.get('higher_level') or ''
    if not d:
        return
    rep.with_desc += 1
    roll = o.get('damage_roll') or ''
    for m in re.finditer(r'(?:deals|increases to|is) (\d+)d(\d+)', d):
        rep.checked += bool(roll)
        if roll and DICE.match(roll) and not roll.startswith(f'{m.group(1)}d{m.group(2)}'):
            rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} desc says {m.group(1)}d{m.group(2)}, damage_roll {roll}')
    steps = {m.group(2): (int(m.group(1)), int(m.group(3))) for m in STEP_DICE.finditer(h)}
    for m in re.finditer(r'(?:increases? by|additional) (\d+)d(\d+)', d):
        x, die = int(m.group(1)), m.group(2)
        if die in steps:
            n, above = steps[die]
            if x != n * (lvl - above):
                rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} desc +{x}d{die}, the step rule gives +{n * (lvl - above)}d{die}')
            rep.checked += 1
            base = re.match(r'(\d+)d(\d+)', sp.get('damage_roll') or '')
            same_die = roll and DICE.match(roll) and re.match(r'\d+d(\d+)', roll).group(1) == die
            if base and base.group(2) == die and same_die and int(roll.split('d')[0]) != int(base.group(1)) + x:
                rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} damage_roll {roll} but base {base.group(0)} + desc {x}d{die}')
            elif not base and same_die:              # base not stored: the implied base must be in the spell text
                implied = f'{int(roll.split("d")[0]) - x}d{die}'
                if not re.search(rf'\b{implied}\b', sp.get('desc') or ''):
                    rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} damage_roll {roll} - desc {x}d{die} implies base {implied}, not in the spell text')
    num = STEP_NUM.search(h) if not steps else None
    dm = re.search(r'(?:an additional|increase by|increases by) (\d+)(?![d\d])|\b(\d+) additional', d)
    if num and dm:
        x = int(dm.group(1) or dm.group(2))
        step = int(num.group(1)) if num.group(1).isdigit() else NUMBER[num.group(1).lower()]
        rep.checked += 1
        if x != step * (lvl - int(num.group(2))):
            rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} desc {x}, the step rule gives {step * (lvl - int(num.group(2)))}')


def check_reformat(rep, clone, base, path, old, new):
    """reformat: changed lines must sit inside records whose content changed."""
    code, text = git(clone, 'show', f'HEAD:{path}')
    lines = text.split('\n')
    owner, idx = [None] * (len(lines) + 1), -1
    for i, line in enumerate(lines, 1):
        if re.fullmatch(r' {0,2}\{', line):       # a record opens at column 0 (srd-2014) or 2 (srd-2024)
            idx += 1
        owner[i] = idx
    order = list(json.loads(text))
    _, diff = git(clone, 'diff', '-U0', base, 'HEAD', '--', path)
    stray = 0
    for m in re.finditer(r'^@@ -\d+(?:,\d+)? \+(\d+)(?:,(\d+))? @@', diff, re.M):
        start, count = int(m.group(1)), int(m.group(2) or 1)
        for ln in range(start, start + count):
            if ln <= len(lines) and re.fullmatch(r' {0,2}\},?', lines[ln - 1]):
                continue                     # the comma an appended record adds to the one before
            i = owner[ln] if ln < len(owner) else None
            if i is not None and 0 <= i < len(order):
                pk = order[i]['pk']
                if old.get(pk) == new.get(pk):
                    stray += 1
    if stray:
        rep.add('reformat', path.split('/')[-1], '-', '', f'{stray} changed line(s) in records whose content did not change')


def lint_counts(clone, srd52):
    """{doc: {kind: count}} from lint-open5e.py's checks."""
    spec = importlib.util.spec_from_file_location('lint', HERE / 'lint-open5e.py')
    lint = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(lint)
    vocab, out = lint.vocabulary(srd52), {}
    for doc in EDITIONS:
        d = Path(clone) / 'data/v2/wizards-of-the-coast' / doc
        findings = collections.defaultdict(list)
        for p in sorted(d.glob('*.json')):
            try:
                recs = json.load(open(p, encoding='utf-8'))
            except ValueError:
                continue
            lint.lint(str(p), recs, vocab, findings)
            lint.duplicates(str(p), recs, findings)
        lint.upcasts(str(d), findings)
        out[doc] = {k: len(v) for k, v in findings.items()}
    return out


def check_ratchet(rep, clone, srd52, update):
    """lint-ratchet: no count above the baseline; --update-baseline lowers it."""
    path = HERE / 'srd-lint-baseline.json'
    now = lint_counts(clone, srd52)
    base = json.load(open(path)) if path.exists() else now
    for doc, kinds in now.items():
        for kind, n in kinds.items():
            if n > base.get(doc, {}).get(kind, 0):
                rep.add('lint-ratchet', doc, kind, '', f'{n}, baseline {base.get(doc, {}).get(kind, 0)}')
    if update or not path.exists():
        low = {doc: {k: min(n, base.get(doc, {}).get(k, n)) for k, n in kinds.items()} for doc, kinds in now.items()}
        json.dump(low, open(path, 'w'), indent=1, sort_keys=True)
        print(f'lint baseline written: {path.name}')


def main(argv):
    """Runs every rule; returns 1 while any finding is unreviewed."""
    clone, srd52 = argv[1], argv[2]
    base = argv[argv.index('--base') + 1] if '--base' in argv else 'origin/staging'
    _, names = git(clone, 'diff', '--name-only', base, 'HEAD', '--',
                   'data/v2/wizards-of-the-coast/srd-2014', 'data/v2/wizards-of-the-coast/srd-2024')
    srd = {ed: runs(tokens(open(Path(srd52) / 'resources/srd' / t, encoding='utf-8').read()))
           for ed, t in EDITIONS.items()}
    rep = Report()
    for path in names.split():
        ed = path.split('/')[3]
        old, new = records(clone, base, path), records(clone, 'HEAD', path)
        check_fidelity(rep, path, ed, old, new, srd)
        check_reformat(rep, clone, base, path, old, new)
        if path.endswith('SpellCastingOption.json'):
            spells = {k: v['fields'] for k, v in records(clone, 'HEAD', path.replace('SpellCastingOption', 'Spell')).items()}
            check_options(rep, ed, old, new, spells)
    if '--all' in argv:
        for ed in EDITIONS:
            p = f'data/v2/wizards-of-the-coast/{ed}/'
            spells = {k: v['fields'] for k, v in records(clone, 'HEAD', p + 'Spell.json').items()}
            check_options(rep, ed, {}, records(clone, 'HEAD', p + 'SpellCastingOption.json'), spells, audit=True)
    check_ratchet(rep, clone, srd52, '--update-baseline' in argv)
    bad = rep.print(['fidelity', 'name-case', 'empty-option', 'slot-gap', 'dice-only', 'slot-below-base',
                     'upcast-math', 'desc-math', 'lint-ratchet', 'reformat'])
    print(f'desc-math verified {rep.checked} of {rep.with_desc} records with a desc; the rest match no step sentence')
    print(f'{bad} open finding(s) across {len(names.split())} changed file(s)')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
