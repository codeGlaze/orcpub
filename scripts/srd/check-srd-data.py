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
  upcast-math     where the higher_level text is a plain step ("increases by 1d6 for each (or every
                  two) slot level
                  above 3rd", "one additional creature for each slot level above 1st"), every
                  option's value is the spell's base plus the step per slot
  base-missing    a spell whose casting options set damage_roll, target_count or shape_size has that field
                  set itself (the base the options scale from)
  slot-below-base no slot option at or below the spell's own level
  desc-math       numbers in a desc agree with the damage_roll beside it and with the step sentence
                  (an absolute "deals 7d8" equals damage_roll; a delta "increases by 3d4" equals the
                  step times the slots above the base)
  punctuation     a hyphen or dash we put between two words is the one the SRD prints there
  value-in-srd    a damage_roll, target_count or shape_size we set on a spell is in its SRD entry
  unverified      every value we set on a casting option is checked by a rule, or hand-verified
                  (srd-data-reviewed.json rule "hand-verified" with the exact values it covers;
                  a changed value is unverified again)
  cantrip-math    a cantrip's damage_roll at each character level is the SRD's total for that level
  desc-consistency within one spell, every slot's desc has the same wording, numbers aside
  lint-ratchet    no lint-open5e.py count rises above srd-lint-baseline.json
  reformat        a diff changes lines only inside records whose content changed

A finding a person has checked and kept goes in srd-data-reviewed.json (rule, file, pk, field,
reason; pk may be a list) and is then reported as reviewed. Exit status 1 while anything is unreviewed.
--all also audits every casting option in both editions, not only the branch's changes (use it to
find upstream errors; the hook does not pass it). --update-baseline lowers the lint baseline to today's counts (never raises it).
"""
import collections, importlib.util, json, os, re, subprocess, sys
from pathlib import Path

HERE = Path(__file__).parent
for _v in ('GIT_DIR', 'GIT_INDEX_FILE', 'GIT_WORK_TREE', 'GIT_PREFIX', 'GIT_COMMON_DIR',
           'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'):
    os.environ.pop(_v, None)   # a git hook exports these; they would point git -C at the wrong repository
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
        self.verified = set()                 # (pk, field) a rule has checked
        self.hand = {(r['file'], r['pk'], r['field']): r['values']
                     for r in json.load(open(HERE / 'srd-data-reviewed.json')) if r['rule'] == 'hand-verified'}

    def add(self, rule, file, pk, field, detail):
        """Records one finding under its rule."""
        why = self.reviewed.get((rule, file, str(pk), field))
        hits = self.found.setdefault(rule, [])
        if not any(h[:4] == (file, pk, field, detail) for h in hits):   # --all sees our changes twice
            hits.append((file, pk, field, detail, why))

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


VALUE_FIELDS = ('damage_roll', 'target_count', 'shape_size', 'duration', 'range', 'concentration', 'desc')


def check_unverified(rep, f, old, new):
    """unverified: every value the branch set on a casting option is checked by a rule, or recorded
    as checked by hand (srd-data-reviewed.json, rule hand-verified, with the values it covers)."""
    need = {}
    for pk, r in new.items():
        o = r['fields']
        if old.get(pk) == r or o['type'] in ('default', 'ritual'):
            continue
        for field in VALUE_FIELDS:
            if o.get(field) not in (None, '') and (pk, field) not in rep.verified:
                need.setdefault((o['parent'], field), {})[o['type']] = o[field]
    rep.need = need
    for (parent, field), values in sorted(need.items()):
        hand = rep.hand.get((f, parent, field))
        if hand != values:
            what = 'hand-verified values differ' if hand else 'checked by no rule and not hand-verified'
            rep.add('unverified', f, parent, field, f'{what}: ' + ', '.join(f'{k.split("_")[-1]}={v}' for k, v in
                                                                                sorted(values.items(), key=lambda kv: int(kv[0].split('_')[-1]))))


CANTRIP_STEP = re.compile(r'(?:(\d+)(?:st|nd|rd|th) level|levels? (\d+)) \((\d*d\d+)\)')


def check_cantrip_math(rep, f, old, new, spells, audit=False):
    """cantrip-math: a cantrip's per-level damage_roll equals the SRD's total for the highest
    threshold reached ("5th level (2d10), 11th level (3d10)"), and the base below the first."""
    for pk, r in new.items():
        o = r['fields']
        if not o['type'].startswith('player_level_') or o.get('damage_roll') in (None, ''):
            continue
        if not audit and old.get(pk) == r and spells.get(o['parent'], {}) == {}:
            continue
        sp = spells.get(o['parent'], {})
        h = sp.get('higher_level') or ''
        steps = [(int(a or b), d) for a, b, d in CANTRIP_STEP.findall(h)]
        listed = re.search(r'levels ((?:\d+ \(\d*d\d+\)(?:,? (?:and )?)?)+)', h)   # 2024: "levels 5 (2d10), 11 (3d10), and 17 (4d10)"
        if listed:
            steps = [(int(t), d) for t, d in re.findall(r'(\d+) \((\d*d\d+)\)', listed.group(1))]
        if not steps:
            continue
        lvl = int(o['type'].split('_')[-1])
        reached = [d for t, d in steps if t <= lvl]
        want = reached[-1] if reached else sp.get('damage_roll')
        norm = lambda x: re.sub(r'^1?d', '1d', x or '') if re.match(r'^1?d\d', x or '') else (x or '')
        rep.verified.add((pk, 'damage_roll'))
        if norm(o['damage_roll']) != norm(want):
            rep.add('cantrip-math', f, pk, o['type'], f'{o["parent"]} {o["damage_roll"]}, the SRD gives {want or "nothing"} at level {lvl}')


def check_desc_consistency(rep, f, old, new, audit=False):
    """desc-consistency: within one spell, every slot's desc has the same wording once numbers,
    ordinals and dice are set aside; a slot worded differently is either a defect or reviewed."""
    by = {}
    for pk, r in new.items():
        o = r['fields']
        if o['type'].startswith(('slot_level_', 'player_level_')) and o.get('desc'):
            by.setdefault(o['parent'], []).append((o['type'], o['desc'], audit or old.get(pk) != r))
    for parent, rows in by.items():
        if not any(changed for _, _, changed in rows):
            continue
        shapes = {}
        for type_, d, _ in rows:
            shapes.setdefault(' '.join(tokens(re.sub(r'(?i)\btwice\b', 'two times', d))), []).append(type_.split('_')[-1])
        if len(shapes) > 1:
            rep.add('desc-consistency', f, parent, 'desc',
                    ' / '.join(f'slots {",".join(sorted(v, key=int))}: {k[:60]!r}' for k, v in shapes.items()))


def check_options(rep, ed, old, new, spells, audit=False):
    """empty-option, slot-gap, dice-only, slot-below-base, upcast-math and desc-math over a
    SpellCastingOption file. `audit` checks every record, not only the ones the branch changed."""
    f = f'{ed}/SpellCastingOption.json'
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
    for parent in {r['fields']['parent'] for r in new.values()}:   # every spell: an emptied base is a change too
        sp = spells.get(parent, {})
        for field in ('damage_roll', 'target_count', 'shape_size'):
            if any(r['fields']['parent'] == parent and r['fields'].get(field) not in (None, '') for r in new.values()) \
                    and sp.get(field) in (None, '', 0):
                rep.add('base-missing', f, parent, field, f'casting options set {field}, the spell itself has {sp.get(field)!r}')
    for parent, slots in by.items():
        missing = [s for s in range(min(slots), 10) if s not in slots]
        if missing:
            rep.add('slot-gap', f, parent, '', f'slots {sorted(slots)} miss {missing}')
        sp = spells.get(parent, {})
        h = sp.get('higher_level') or ''
        m = re.search(r'increases? by (\d+)d(\d+) for (?:each|every)( two)? (?:spell )?slot levels? above (?:the )?(\d+)', h)
        t = re.search(r'\b(one|two|three|\d+) additional (?:creature|target|undead|object|beast)s? for each (?:spell )?slot level above (\d+)', h, re.I)
        for lvl, (pk, o) in sorted(slots.items()):
            several = len(re.findall(r'increases? by \d+d\d+', h)) > 1   # two damages scale: which one is stored is not ours to guess
            if m and not several and sp.get('damage_roll') and DICE.match(sp['damage_roll']) and DICE.match(o.get('damage_roll') or ''):
                n, die, above = int(m.group(1)), m.group(2), int(m.group(4))
                steps = (lvl - above) // 2 if m.group(3) else lvl - above      # "for every two slot levels"
                bn, bd = re.match(r'(\d+)d(\d+)', sp['damage_roll']).groups()
                want = f'{int(bn) + n * steps}d{die}' if bd == die else None
                got = re.match(r'(\d+d\d+)', o['damage_roll']).group(1)
                if want:
                    rep.verified.add((pk, 'damage_roll'))
                if want and got != want:
                    rep.add('upcast-math', f, pk, o['type'], f'{parent} {got}, the step rule gives {want}')
            if t and o.get('target_count') is not None and sp.get('target_count'):
                step = int(t.group(1)) if t.group(1).isdigit() else WORDS[t.group(1)]
                want = sp['target_count'] + step * (lvl - int(t.group(2)))
                rep.verified.add((pk, 'target_count'))
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
        if roll:
            rep.verified |= {(pk, 'desc'), (pk, 'damage_roll')}
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
            rep.verified.add((pk, 'desc'))
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
        rep.verified.add((pk, 'desc'))
        if x != step * (lvl - int(num.group(2))):
            rep.add('desc-math', f, pk, o['type'], f'{o["parent"]} desc {x}, the step rule gives {step * (lvl - int(num.group(2)))}')


SPELL_HEAD = r' (?:Level \d \w+|\d(?:st|nd|rd|th)-level \w+|\w+ [Cc]antrip)'


def spell_entries(text, names):
    """{spell name: its SRD entry}, from the normalised transcript. An entry runs from its heading
    (the name, then its level and school) to the next spell heading."""
    found = sorted(((m.start(), m.end(), n) for n in names for m in re.finditer(re.escape(n) + SPELL_HEAD, text)),
                   key=lambda x: (x[0], -x[1]))
    spans, heads, seen = [], [], set()
    for a, b, n in found:                     # "Fireball 7th-level" inside "Delayed Blast Fireball 7th-level" is not a heading
        if any(s0 <= a < e0 for s0, e0 in spans) or n in seen:
            continue
        spans.append((a, b)); heads.append((a, n)); seen.add(n)
    heads.sort()
    return {n: text[a:(heads[i + 1][0] if i + 1 < len(heads) else a + 4000)] for i, (a, n) in enumerate(heads)}


def check_spell_values(rep, f, old, new, entries):
    """value-in-srd: a damage_roll, target_count or shape_size the branch set on a spell appears in
    that spell's SRD entry ("1d6", "a d8"; "ten" or 10; "20-foot")."""
    for pk, r in new.items():
        sp, before = r['fields'], old.get(pk, {}).get('fields', {})
        entry = entries.get(sp.get('name'))
        for field in ('damage_roll', 'target_count', 'shape_size'):
            v = sp.get(field)
            if v in (None, '', 0) or before.get(field) == v:
                continue
            if entry is None:
                rep.add('value-in-srd', f, pk, field, f'{v!r}: no SRD entry found for {sp.get("name")!r}')
                continue
            if field == 'damage_roll':
                n, die = re.match(r'(\d+)d(\d+)', v).groups()
                pats = [rf'\b{n}d{die}\b'] + ([rf'\ba d{die}\b'] if n == '1' else [])
            elif field == 'target_count':
                word = {v2: k for k, v2 in {**WORDS, 'six': 6, 'seven': 7, 'eight': 8, 'nine': 9, 'ten': 10, 'twelve': 12}.items()}.get(int(v), '')
                pats = [rf'\b{int(v)}\b'] + ([rf'\b{word}\b'] if word else [])
            else:
                pats = [rf'\b{int(float(v))}[- ](?:foot|feet)\b']
            if not any(re.search(p_, entry, re.I) for p_ in pats):
                rep.add('value-in-srd', f, pk, field, f'{v!r} is not in the SRD entry for {sp["name"]}')


def check_punctuation(rep, f, old, new, raw):
    """punctuation: a hyphen or dash the branch put between two words is the one the SRD prints
    there. Only flags when the SRD has the same two words with different punctuation."""
    for pk, r in new.items():
        before = old.get(pk, {}).get('fields', {})
        for field, value in r['fields'].items():
            if not isinstance(value, str) or before.get(field) == value or field in SKIP_TEXT:
                continue
            had = set(re.findall(r'(\w+)([-\u2013\u2014])(\w+)', before.get(field) or ''))
            for a, mark, b in set(re.findall(r'(\w+)([-\u2013\u2014])(\w+)', value)) - had:
                if re.search(rf'\b{re.escape(a)}\s?{re.escape(mark)}\s?{re.escape(b)}\b', raw, re.I):
                    continue                         # the SRD has this mark here (PDF spacing aside)
                other = re.search(rf'\b{re.escape(a)}(\s?[-\u2013\u2014]\s?|\s)?{re.escape(b)}\b', raw, re.I)
                if other:
                    rep.add('punctuation', f, pk, field, f'{a}{mark}{b}: the SRD prints {other.group(0)!r}')


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
    text = {ed: open(Path(srd52) / 'resources/srd' / t, encoding='utf-8').read() for ed, t in EDITIONS.items()}
    srd = {ed: runs(tokens(t)) for ed, t in text.items()}
    raw = {ed: re.sub(r'\s+', ' ', t).replace('\u2019', "'") for ed, t in text.items()}
    rep = Report()
    for path in names.split():
        ed = path.split('/')[3]
        old, new = records(clone, base, path), records(clone, 'HEAD', path)
        check_fidelity(rep, path, ed, old, new, srd)
        check_punctuation(rep, path.split('/')[-1], old, new, raw[ed])
        check_reformat(rep, clone, base, path, old, new)
        if path.endswith('/Spell.json'):
            check_spell_values(rep, f'{ed}/Spell.json', old, new,
                               spell_entries(raw[ed], [r['fields']['name'] for r in new.values()]))
        if path.endswith('SpellCastingOption.json'):
            spells = {k: v['fields'] for k, v in records(clone, 'HEAD', path.replace('SpellCastingOption', 'Spell')).items()}
            check_options(rep, ed, old, new, spells)
            check_unverified(rep, f'{ed}/SpellCastingOption.json', old, new)
            check_desc_consistency(rep, f'{ed}/SpellCastingOption.json', old, new)
            check_cantrip_math(rep, f'{ed}/SpellCastingOption.json', old, new, spells)
    if '--all' in argv:
        for ed in EDITIONS:
            p = f'data/v2/wizards-of-the-coast/{ed}/'
            spells = {k: v['fields'] for k, v in records(clone, 'HEAD', p + 'Spell.json').items()}
            opts = records(clone, 'HEAD', p + 'SpellCastingOption.json')
            check_options(rep, ed, {}, opts, spells, audit=True)
            check_desc_consistency(rep, f'{ed}/SpellCastingOption.json', {}, opts, audit=True)
            check_cantrip_math(rep, f'{ed}/SpellCastingOption.json', {}, opts, spells, audit=True)
            unchecked = sorted({r['fields']['parent'] for pk, r in opts.items()
                                if r['fields']['type'].startswith(('slot_level_', 'player_level_'))
                                and any(r['fields'].get(k) not in (None, '') and (pk, k) not in rep.verified
                                        and (f'{ed}/SpellCastingOption.json', r['fields']['parent'], k) not in rep.hand
                                        for k in VALUE_FIELDS)})
            print(f'audit {ed}: {len(unchecked)} spells have upcast values no rule or hand check covers '
                  f'(upstream data; check before trusting): ' + ', '.join(u.split('_', 1)[1] for u in unchecked))
    check_ratchet(rep, clone, srd52, '--update-baseline' in argv)
    bad = rep.print(['fidelity', 'punctuation', 'name-case', 'empty-option', 'base-missing', 'value-in-srd', 'slot-gap',
                     'dice-only', 'slot-below-base', 'upcast-math', 'cantrip-math', 'desc-math', 'desc-consistency', 'unverified', 'lint-ratchet', 'reformat'])
    print(f'desc-math verified {rep.checked} of {rep.with_desc} records with a desc; the rest match no step sentence')
    print(f'{bad} open finding(s) across {len(names.split())} changed file(s)')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
