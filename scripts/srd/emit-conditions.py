#!/usr/bin/env python3
"""Emit the SRD conditions, both editions, as our EDN data files.

Usage: emit-conditions.py OPEN5E_CLONE SRD52_WORKTREE

Reads the corrected open5e data (the open5e-api clone, branch srd-corrections) and writes
  SRD52_WORKTREE/resources/public/srd/2014/conditions.edn
  SRD52_WORKTREE/resources/public/srd/2024/conditions.edn

Text stays verbatim; only structure changes. Each condition's :body is a vector of blocks:
  [:p "text"]                      a paragraph
  [:list [{:name "Label" :text "..."} ...]]   bullets; :name only where the SRD labels them (2024)
  [:table {:columns [...] :rows [[...] ...]}]
Markdown emphasis is dropped (presentation, not content). Pages come from the readable SRD
transcripts in SRD52_WORKTREE/resources/srd/.

Refuses to write if any word of the source is lost or added: the words of the output, in order,
must equal the words of the source with only markdown markers removed.
"""
import json, re, subprocess, sys
from pathlib import Path

EDITIONS = {
    2014: {'dir': 'srd-2014', 'document': 'SRD 5.1', 'transcript': 'SRD-5.1.readable.txt',
           'labelled': False,
           'attribution': ('This work includes material taken from the System Reference Document 5.1 '
                           '(“SRD 5.1”) by Wizards of the Coast LLC and available at '
                           'https://dnd.wizards.com/resources/systems-reference-document. The SRD 5.1 is '
                           'licensed under the Creative Commons Attribution 4.0 International License '
                           'available at https://creativecommons.org/licenses/by/4.0/legalcode.')},
    2024: {'dir': 'srd-2024', 'document': 'SRD 5.2.1', 'transcript': 'SRD-5.2.1.readable.txt',
           'labelled': True,
           'attribution': ('This work includes material from the System Reference Document 5.2.1 '
                           '(“SRD 5.2.1”) by Wizards of the Coast LLC, available at '
                           'https://www.dndbeyond.com/srd. The SRD 5.2.1 is licensed under the Creative '
                           'Commons Attribution 4.0 International License, available at '
                           'https://creativecommons.org/licenses/by/4.0/legalcode.')},
}
EMPHASIS = re.compile(r'\*([^*\s][^*]*?)\*')

def clean(s):
    return EMPHASIS.sub(r'\1', s).strip()

def parse(desc, labelled):
    """open5e markdown-ish desc -> list of blocks."""
    blocks, items, table = [], None, None
    def flush():
        nonlocal items, table
        if items is not None: blocks.append(['list', items]); items = None
        if table is not None: blocks.append(['table', table]); table = None
    for raw in desc.replace('\r\n', '\n').split('\n'):
        line = raw.strip()
        if not line: continue
        if line.startswith('|'):
            cells = [c.strip() for c in line.strip('|').split('|')]
            if all(re.fullmatch(r'-+', c) for c in cells): continue
            if items is not None: flush()
            if table is None: table = {'columns': cells, 'rows': []}
            else: table['rows'].append([int(c) if c.isdigit() else c for c in cells])
        elif line.startswith('* '):
            if table is not None: flush()
            if items is None: items = []
            text = clean(line[2:])
            if labelled:
                name, sep, rest = text.partition('. ')
                assert sep, f'unlabelled bullet in a labelled edition: {text[:60]}'
                items.append({'name': name, 'text': rest})
            else:
                items.append({'text': text})
        else:
            flush(); blocks.append(['p', clean(line)])
    flush()
    return blocks

def words(s):
    return re.findall(r"[^\s|*]+", s.replace('---', ' '))

def block_words(blocks):
    out = []
    for kind, v in blocks:
        if kind == 'p': out += words(v)
        elif kind == 'list':
            for it in v:
                if 'name' in it: out += words(it['name'] + '.')
                out += words(it['text'])
        else:
            out += [str(c) for c in v['columns']]
            for r in v['rows']: out += words(' '.join(str(c) for c in r))
    return out

def page_of(transcript, name, edition):
    t = transcript
    if edition == 2014:
        start = t.index('Appendix PH-A:')
        m = re.compile(r'^' + re.escape(name) + r'$', re.M).search(t, start)
    else:
        m = re.compile(r'^' + re.escape(name) + r' \[Condition\]$', re.M).search(t)
    assert m, f'{name}: heading not found in the {edition} transcript'
    return int(re.findall(r'<<<PAGE (\d+)>>>', t[:m.start()])[-1])

def edn_str(s):
    return '"' + s.replace('\\', '\\\\').replace('"', '\\"') + '"'

def edn_block(kind, v, ind):
    if kind == 'p': return f'[:p {edn_str(v)}]'
    if kind == 'list':
        rows = []
        for it in v:
            name = f':name {edn_str(it["name"])} ' if 'name' in it else ''
            rows.append(f'{{{name}:text {edn_str(it["text"])}}}')
        sep = '\n' + ' ' * (ind + 8)
        return '[:list [' + sep.join(rows) + ']]'
    cols = ' '.join(edn_str(c) for c in v['columns'])
    rows = ('\n' + ' ' * (ind + 18)).join(
        '[' + ' '.join(str(c) if isinstance(c, int) else edn_str(c) for c in r) + ']' for r in v['rows'])
    return f'[:table {{:columns [{cols}]\n{" " * (ind + 10)}:rows [{rows}]}}]'

def main(clone, srd52):
    clone, srd52 = Path(clone), Path(srd52)
    rev = subprocess.run(['git', '-C', str(clone), 'rev-parse', '--short', 'HEAD'],
                         capture_output=True, text=True).stdout.strip()
    for ed, cfg in EDITIONS.items():
        recs = json.load(open(clone / 'data/v2/wizards-of-the-coast' / cfg['dir'] / 'ConditionDescription.json',
                              encoding='utf-8'))
        transcript = open(srd52 / 'resources/srd' / cfg['transcript'], encoding='utf-8').read()
        conds = []
        for r in sorted(recs, key=lambda r: r['fields']['describes']):
            key, desc = r['fields']['describes'], r['fields']['desc']
            name = key.capitalize()
            blocks = parse(desc, cfg['labelled'])
            src = words(EMPHASIS.sub(r'\1', desc))
            assert block_words(blocks) == src, f'{ed} {key}: words changed in conversion'
            conds.append((key, name, page_of(transcript, name, ed), blocks))
        lines = [f';; Generated by scripts/srd/emit-conditions.py (agents/develop) from open5e-api',
                 f';; srd-corrections {rev}. Do not edit: fix the source and regenerate.',
                 f'{{:srd/document {edn_str(cfg["document"])}',
                 f' :srd/edition {ed}',
                 f' :srd/license "CC BY 4.0"',
                 f' :srd/attribution {edn_str(cfg["attribution"])}',
                 f' :conditions']
        for i, (key, name, page, blocks) in enumerate(conds):
            body = ('\n' + ' ' * 10).join(edn_block(k, v, 2) for k, v in blocks)
            lines.append(f' {"[" if i == 0 else " "}{{:key :{key}\n   :name {edn_str(name)}\n   :page {page}\n'
                         f'   :body [{body}]}}{"]}" if i == len(conds) - 1 else ""}')
        out = srd52 / f'resources/public/srd/{ed}/conditions.edn'
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_text('\n'.join(lines) + '\n', encoding='utf-8')
        print(f'  {out}: {len(conds)} conditions, pages {min(c[2] for c in conds)}-{max(c[2] for c in conds)}')

if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
