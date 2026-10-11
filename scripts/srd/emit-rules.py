#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = ["markdown-it-py>=4"]
# ///
"""Emit the SRD 5.1 rules as our EDN data file.

Usage: emit-rules.py OPEN5E_CLONE SRD52_WORKTREE LINK_TARGETS_JSON

Reads open5e's srd-2014 RuleSet.json (sections) and Rule.json (the rules within them) from the
corrected clone (branch srd-corrections) and writes
  SRD52_WORKTREE/resources/public/srd/2014/rules.edn

Each section is {:key :name :group :pages [first last] :body [...]}; each rule in it starts with
a heading carrying its anchor. Groups are ours, in the order the Rules page shows them. Links are
marked here, once: spell names (italic in the SRD), conditions where the text names the
condition, quoted rule names, and open5e's srd:slug references. Block format: srd_format.py.

Refuses to write if a section's text differs from its source's (spacing aside), open5e markup
resolved.
LINK_TARGETS_JSON is written by link-targets.clj, from the app's own spell keys.
"""
import json, re, subprocess, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import srd_format as F

GROUPS = [
    ('playing', 'Playing the Game', ['abilities', 'saving-throws', 'time', 'movement', 'environment',
                                     'resting', 'between-adventures', 'inspiration']),
    ('combat', 'Combat', ['combat-sequence', 'movement-and-position', 'actions-in-combat', 'attacking', 'cover', 'damage-and-healing',
                          'mounted-combat', 'underwater-combat']),
    ('spellcasting', 'Spellcasting', ['spellcasting']),
    ('characters', 'Characters', ['races', 'backgrounds', 'alignment', 'languages', 'leveling-up',
                                  'multiclassing', 'feats']),
    ('equipment', 'Equipment', ['coins', 'armor', 'weapons', 'equipment-packs', 'tools', 'mounts-and-vehicles',
                                'trade-goods', 'expenses', 'selling-treasure']),
    ('game-master', 'Game Master', ['traps', 'diseases', 'madness', 'poisons', 'objects', 'planes', 'pantheons',
                                    'magic-items', 'monsters', 'nonplayer-characters']),
]
ATTRIBUTION = ('This work includes material taken from the System Reference Document 5.1 (“SRD 5.1”) by '
               'Wizards of the Coast LLC and available at '
               'https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is licensed under '
               'the Creative Commons Attribution 4.0 International License available at '
               'https://creativecommons.org/licenses/by/4.0/legalcode.')


def pager(transcript):
    """Returns page_of(words): the SRD page where a run of words first appears, or None."""
    tw, marks = [], []
    for m in re.finditer(r'<<<PAGE (\d+)>>>|[^<]+|<', transcript):
        if m.group(1):
            marks.append((len(tw), int(m.group(1))))
        else:
            tw += re.findall(r"[a-z0-9]+", m.group(0).lower().replace('’', "'"))
    idx = {}
    for i in range(len(tw) - 5):
        idx.setdefault(tuple(tw[i:i + 6]), i)
    def page_of(ws):
        ws = [w for w in (re.findall(r"[a-z0-9]+", ' '.join(ws).lower().replace('’', "'")))]
        for k in range(0, max(1, len(ws) - 5), 3):
            i = idx.get(tuple(ws[k:k + 6]))
            if i is not None:
                return max(p for s, p in marks if s <= i)
        return None
    return page_of


# open5e quotes Sage Advice rulings inside two SRD rules. They are Wizards' official guidance but not
# SRD text, and not under the SRD's licence, so the SRD file leaves them out (srd-improvements.md).
SAGE_ADVICE = re.compile(r'\s*> \*\*Sage Advice\*\*.*?Source: \[Sage Advice > Compendium\]\([^)]*\)', re.S)


def srd_text(desc, skipped, where):
    """`desc` without open5e's quoted Sage Advice rulings; each one left out is noted in `skipped`."""
    out, n = SAGE_ADVICE.subn('', desc or '')
    if n:
        skipped.append(where)
    return out


def anchor_subheadings(body):
    """Gives every heading without an anchor one from its text (Death Saving Throws ->
    :death-saving-throws), unique within the section, so links and searches reach it."""
    used = {b[3].name for b in body if b[0] == 'h' and b[3] is not None}
    for b in body:
        if b[0] == 'h' and b[3] is None:
            slug = re.sub(r'[^a-z0-9]+', '-', b[2].lower()).strip('-')
            k, n = slug, 2
            while k in used:
                k, n = f'{slug}-{n}', n + 1
            used.add(k)
            b[3] = F.Kw(k)


def main(clone, srd52, targets):
    clone, srd52 = Path(clone), Path(srd52)
    d = clone / 'data/v2/wizards-of-the-coast/srd-2014'
    sets = {s['pk'][4:]: s['fields'] for s in json.load(open(d / 'RuleSet.json', encoding='utf-8'))}
    rules = {}
    for r in json.load(open(d / 'Rule.json', encoding='utf-8')):
        sec = r['fields']['ruleset'][4:]
        rules.setdefault(sec, []).append((r['pk'][len('srd_' + sec + '_'):], r['fields']))
    grouped = [s for _, _, ss in GROUPS for s in ss]
    assert sorted(grouped) == sorted(sets), f'groups do not cover the sections: {set(sets) ^ set(grouped)}'
    links = F.Links(json.load(open(targets))['spells'])
    for sec, f in sets.items():
        links.rules[f['name'].lower()] = (sec, None)
        links.rule_titles[(sec, None)] = f['name']
        for anchor, rf in rules.get(sec, []):
            links.rules.setdefault(rf['name'].lower(), (sec, anchor))
            links.rule_titles[(sec, anchor)] = rf['name']
    page_of = pager(open(srd52 / 'resources/srd/SRD-5.1.readable.txt', encoding='utf-8').read())
    sections, skipped = [], []
    for gkey, _, secs in GROUPS:
        for sec in secs:
            f = sets[sec]
            body = F.parse(f['desc'] or '', links, (sec, None))
            src = [w for line in (f['desc'] or '').split('\n') for w in F.plain_source(line, links)]
            pages = [page_of(src)] if src else []
            for anchor, rf in sorted(rules.get(sec, []), key=lambda x: x[1]['index']):
                body.append(['h', rf['initialHeaderLevel'], rf['name'], F.Kw(anchor)])
                desc = srd_text(rf['desc'], skipped, f'{sec}/{anchor}')
                body += F.parse(desc, links, (sec, anchor))
                rsrc = [w for line in desc.split('\n') for w in F.plain_source(line, links)]
                src += F.words(rf['name']) + rsrc
                pages.append(page_of(rsrc))
            anchor_subheadings(body)
            got, want = ''.join(F.block_words(body)), ''.join(src)
            if got != want:
                i = next((k for k, (a, b) in enumerate(zip(got, want)) if a != b), min(len(got), len(want)))
                raise SystemExit(f'{sec}: text changed at {i}: got {got[i - 20:i + 40]!r} source {want[i - 20:i + 40]!r}')
            pages = [p for p in pages if p]
            sections.append({'key': F.Kw(sec), 'name': f['name'], 'group': F.Kw(gkey),
                             'pages': [min(pages), max(pages)] if pages else None, 'body': body})
    rev = subprocess.run(['git', '-C', str(clone), 'rev-parse', '--short', 'HEAD'],
                         capture_output=True, text=True).stdout.strip()
    data = {'srd/document': 'SRD 5.1', 'srd/edition': 2014, 'srd/license': 'CC BY 4.0',
            'srd/attribution': ATTRIBUTION,
            'groups': [{'key': F.Kw(k), 'name': n, 'sections': [F.Kw(s) for s in ss]} for k, n, ss in GROUPS],
            'sections': sections}
    out = srd52 / 'resources/public/srd/2014/rules.edn'
    head = (f';; Generated by scripts/srd/emit-rules.py (agents/develop) from open5e-api\n'
            f';; srd-corrections {rev}. Do not edit: fix the source and regenerate.\n')
    body = F.edn(F.keywordize(data)).replace(' :sections [{', '\n :sections\n [{').replace('} {:key', '}\n  {:key')
    out.write_text(head + body + '\n', encoding='utf-8')
    n_links = len(re.findall(r'\[:a \{:kind', body))
    print(f'  {out}: {len(sections)} sections, {n_links} links, '
          f'{len(links.unlinked_conditions)} bare condition words left as text; '
          f'Sage Advice left out of: {", ".join(skipped) or "nothing"}')


if __name__ == '__main__':
    main(*sys.argv[1:4])
