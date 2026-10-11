#!/usr/bin/env python3
"""Sweep open5e's SRD data for the defect kinds we have found and fixed by hand.

Usage: lint-open5e.py OPEN5E_CLONE [SRD52_WORKTREE] [--docs srd-2014,srd-2024] [--examples N]

Each check is a kind of defect a correction has already been made for, so the next one is found by
running this, not by noticing. It locates candidates; a person (or the SRD PDF) confirms them
before anything is fixed. SRD52_WORKTREE is optional: with it, the run-together-word check uses the
SRD transcripts in resources/srd/ as its vocabulary.

Checks:
  literal-newline   the characters backslash-n in text, where a line break was meant
  carriage-return   \\r in text
  soft-hyphen       U+00AD, PDF text-layer debris
  odd-hyphen        U+2010 / U+2011, where "-" is meant
  odd-apostrophe    U+02BC, where ' or ’ is meant
  double-space      two spaces inside a sentence (table rows excepted)
  break-hyphen      "move- ment": a PDF line-break hyphen kept with a space
  run-together      a word the SRD never uses that splits into two words it does ("relyingon")
  stray-bracket     a name with unbalanced ( ) or [ ]
  name-case         a name not in Title Case (open5e's and OrcPub's convention)
  zero-cost         an item with no cost and no weight, where the SRD prices it
  duplicate-text    two records sharing a long run of text (Critical Hits once copied Damage Rolls)
  upcast-not-data   a spell with "at higher levels" text but no per-slot options
  bullet-no-space   a line opening "*Word", a bullet missing its space, read as plain text
  collapsed-md      markdown whose line breaks were lost: a list run onto one line
                    ("following:  - Hold your breath - March..."), a heading or quote mid-line
"""
import collections, glob, json, os, re, sys

SMALL = {'of', 'the', 'and', 'or', 'a', 'an', 'to', 'in', 'on', 'for', 'with', 'from', 'at', 'by',
         'into', 'against', 'without', 'via', 'vs', 'vs.', 'per', 'as', 'than', 'up'}


def text_fields(rec):
    """The string fields of a fixture record worth linting, as (field, value) pairs."""
    for k, v in rec.get('fields', {}).items():
        if isinstance(v, str) and v and k not in ('document', 'key', 'parent', 'ruleset', 'category'):
            yield k, v


def vocabulary(srd52):
    """Every word in the SRD transcripts, lower-case, or an empty set without them."""
    words = set()
    if srd52:
        for p in glob.glob(os.path.join(srd52, 'resources/srd/*.readable.txt')):
            words |= set(re.findall(r"[a-z]+", open(p, encoding='utf-8').read().lower()))
    return words


def run_together(token, vocab):
    """The two SRD words `token` splits into, if it is not one itself, else None."""
    t = token.lower()
    if len(t) < 6 or t in vocab or not vocab:
        return None
    for i in range(2, len(t) - 1):
        a, b = t[:i], t[i:]
        if a in vocab and b in vocab and (len(a) > 2 or a in ('a', 'an', 'at', 'of', 'to', 'up', 'in', 'on', 'or', 'as', 'by', 'is', 'it', 'be', 'no')) and len(b) > 2:
            return f'{a} {b}'
    return None


def title_case_ok(name):
    words = re.split(r'[\s/]+', re.sub(r'\([^)]*\)', '', name).strip())
    for i, w in enumerate(words):
        core = w.strip(',.;:"“”')
        if not core or not core[0].isalpha():
            continue
        if core[0].islower() and (i == 0 or core.lower() not in SMALL):
            return False
    return True


def lint(path, records, vocab, findings):
    fname = os.path.basename(path)
    for rec in records:
        pk = rec.get('pk')
        for field, v in text_fields(rec):
            where = (fname, pk, field)
            if '\\n' in v:
                findings['literal-newline'].append((*where, v[max(0, v.find('\\n') - 30):v.find('\\n') + 20]))
            if '\r' in v:
                findings['carriage-return'].append((*where, ''))
            if '­' in v:
                findings['soft-hyphen'].append((*where, v[max(0, v.find('­') - 20):v.find('­') + 20]))
            if re.search('[‐‑]', v):
                findings['odd-hyphen'].append((*where, ''))
            if 'ʼ' in v:
                findings['odd-apostrophe'].append((*where, ''))
            for line in v.split('\n'):
                if not line.lstrip().startswith('|'):
                    m = re.search(r'\S  +\S', line)
                    if m:
                        findings['double-space'].append((*where, line[max(0, m.start() - 20):m.end() + 20]))
                        break
            m = re.search(r'(?m)^\*[A-Za-z][^*\n]*$', v)
            if m:
                findings['bullet-no-space'].append((*where, m.group(0)[:50]))
            for line in v.split('\n'):
                m = re.search(r'\S {2,}(- |#{1,6} |> |\* )\S', line)
                if m and not line.lstrip().startswith('|'):
                    findings['collapsed-md'].append((*where, line[max(0, m.start() - 25):m.end() + 25]))
                    break
            for m in re.finditer(r'\b([a-z]{2,})- ([a-z]{2,})\b', v):
                if not re.match(r'(or|and|to)\b', v[m.end(1) + 2:m.end(1) + 6]):
                    findings['break-hyphen'].append((*where, m.group(0)))
            for tok in set(re.findall(r"\b[A-Za-z]{6,}\b", v)):
                split = run_together(tok, vocab)
                if split:
                    findings['run-together'].append((*where, f'{tok} -> {split}'))
        name = rec.get('fields', {}).get('name')
        if isinstance(name, str):
            if name.count('(') != name.count(')') or name.count('[') != name.count(']'):
                findings['stray-bracket'].append((fname, pk, 'name', name))
            if fname in ('Item.json', 'MagicItem.json', 'Weapon.json', 'Armor.json', 'Spell.json', 'Creature.json') \
                    and not title_case_ok(name):
                findings['name-case'].append((fname, pk, 'name', name))
        f = rec.get('fields', {})
        if fname == 'Item.json' and f.get('category') in ('adventuring-gear', 'tools', 'weapon', 'armor') \
                and str(f.get('cost')) in ('0', '0.00', 'None') and str(f.get('weight')) in ('0', '0.000', 'None'):
            findings['zero-cost'].append((fname, pk, 'cost/weight', name))


def duplicates(path, records, findings):
    fname = os.path.basename(path)
    grams = collections.defaultdict(set)
    for rec in records:
        words = re.findall(r'[a-z0-9]+', ' '.join(v for _, v in text_fields(rec)).lower())
        for i in range(0, max(0, len(words) - 11), 6):
            grams[tuple(words[i:i + 12])].add(rec.get('pk'))
    pairs = collections.Counter()
    for pks in grams.values():
        if 1 < len(pks) < 4:
            for a in pks:
                for b in pks:
                    if a < b:
                        pairs[(a, b)] += 1
    for (a, b), n in pairs.items():
        if n >= 8:
            findings['duplicate-text'].append((fname, a, b, f'{n} shared 12-word runs'))


def upcasts(doc_dir, findings):
    sp = os.path.join(doc_dir, 'Spell.json')
    so = os.path.join(doc_dir, 'SpellCastingOption.json')
    if not (os.path.exists(sp) and os.path.exists(so)):
        return
    has = {o['fields']['parent'] for o in json.load(open(so, encoding='utf-8'))
           if str(o['fields'].get('type', '')).startswith(('slot_level', 'player_level'))}
    for s in json.load(open(sp, encoding='utf-8')):
        if (s['fields'].get('higher_level') or '').strip() and s['pk'] not in has:
            findings['upcast-not-data'].append(('Spell.json', s['pk'], 'higher_level', s['fields']['higher_level'][:80]))


def main(argv):
    clone = argv[1]
    srd52 = argv[2] if len(argv) > 2 and not argv[2].startswith('--') else None
    docs = 'srd-2014,srd-2024'
    examples = 3
    if '--docs' in argv:
        docs = argv[argv.index('--docs') + 1]
    if '--examples' in argv:
        examples = int(argv[argv.index('--examples') + 1])
    vocab = vocabulary(srd52)
    for doc in docs.split(','):
        doc_dir = os.path.join(clone, 'data/v2/wizards-of-the-coast', doc)
        findings = collections.defaultdict(list)
        for path in sorted(glob.glob(os.path.join(doc_dir, '*.json'))):
            try:
                records = json.load(open(path, encoding='utf-8'))
            except (ValueError, OSError):
                continue
            lint(path, records, vocab if 'srd' in doc else set(), findings)
            duplicates(path, records, findings)
        upcasts(doc_dir, findings)
        print(f'== {doc}')
        if not findings:
            print('   clean')
        for check, hits in sorted(findings.items()):
            files = collections.Counter(h[0] for h in hits)
            print(f'   {check:16} {len(hits):4}  ' + ', '.join(f'{f} {n}' for f, n in files.most_common()))
            for h in hits[:examples]:
                print(f'      {h[1]}  {h[-1]!r}'[:150])


if __name__ == '__main__':
    main(sys.argv)
