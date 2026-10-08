#!/usr/bin/env python3
"""Emit the SRD weapons and armor, both editions, as our EDN data files.

Usage: emit-equipment.py OPEN5E_CLONE SRD52_WORKTREE

Reads the corrected open5e data (branch srd-corrections) and writes
  SRD52_WORKTREE/resources/public/srd/2014/equipment.edn
  SRD52_WORKTREE/resources/public/srd/2024/equipment.edn

Each weapon: {:key :name :category (:simple-melee ...) :cost {:num :type} :weight lb
              :damage {:dice "1d8" :type :slashing} :properties [{:key :versatile :detail "1d10"}]
              :mastery :topple}   ; :mastery in 2024 only
Each armor:  {:key :name :category (:light :medium :heavy :shield) :cost :weight
              :ac {:base 11 :dex? true :max-dex 2} :strength 13 :stealth-disadvantage? true}
Costs use the coin the SRD prices in (2 sp, not 0.2 gp), the app's {:num :type} shape. Every
value here was checked against the SRD's equipment tables (srd-2024-coverage.md, equipment).
"""
import json, re, subprocess, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import srd_format as F

EDITIONS = {2014: ('srd-2014', 'srd_', 'SRD 5.1', 'SRD-5.1.readable.txt'),
            2024: ('srd-2024', 'srd-2024_', 'SRD 5.2.1', 'SRD-5.2.1.readable.txt')}
SECTIONS = {'Simple Melee Weapons': 'simple-melee', 'Simple Ranged Weapons': 'simple-ranged',
            'Martial Melee Weapons': 'martial-melee', 'Martial Ranged Weapons': 'martial-ranged'}


def weapon_categories(transcript):
    """Weapon name (letters only, lower case) -> category, from the section of the SRD weapons
    table its row sits under. open5e has no melee/ranged flag, and properties mislead: the Dart
    and Net are thrown, yet ranged weapons in the SRD."""
    cats, current = {}, None
    for line in transcript.split('\n'):
        line = line.strip()
        if line in SECTIONS:
            current = SECTIONS[line]
        elif current and re.match(r'^(Weapons|Armor|Name |Martial Weapons|Simple Weapons)$', line):
            pass
        elif current:
            name = re.match(r"^([A-Z][A-Za-z’', ]+?)(?= \d| —)", line)
            if name:
                cats.setdefault(re.sub(r'[^a-z]', '', name.group(1).lower()), current)
        if current and line.startswith(('Armor', 'Weapon Properties', 'Properties')) and line not in SECTIONS:
            current = None if line in ('Armor', 'Weapon Properties') else current
    return cats


def coin(gp):
    """open5e's decimal gp -> {:num :type} in the coin the SRD prices in."""
    cp = round(float(gp) * 100)
    for unit, n in (('gp', 100), ('sp', 10), ('cp', 1)):
        if cp % n == 0 and cp >= n:
            return {'num': cp // n, 'type': F.Kw(unit)}
    return {'num': cp, 'type': F.Kw('cp')}


def number(s):
    v = float(s)
    return int(v) if v == int(v) else v


def slug(name):
    return re.sub(r'[^a-z0-9]+', '-', name.lower().replace("'", '')).strip('-')


def main(clone, srd52):
    clone, srd52 = Path(clone), Path(srd52)
    rev = subprocess.run(['git', '-C', str(clone), 'rev-parse', '--short', 'HEAD'],
                         capture_output=True, text=True).stdout.strip()
    for ed, (d, pre, doc, tname) in EDITIONS.items():
        cats = weapon_categories(open(srd52 / 'resources/srd' / tname, encoding='utf-8').read())
        base = clone / 'data/v2/wizards-of-the-coast' / d
        items = json.load(open(base / 'Item.json', encoding='utf-8'))
        weapons = {w['pk']: w['fields'] for w in json.load(open(base / 'Weapon.json', encoding='utf-8'))}
        armors = {a['pk']: a['fields'] for a in json.load(open(base / 'Armor.json', encoding='utf-8'))}
        props = {p['pk']: p['fields'] for p in json.load(open(base / 'WeaponProperty.json', encoding='utf-8'))}
        assigned = {}
        for a in json.load(open(base / 'WeaponPropertyAssignment.json', encoding='utf-8')):
            assigned.setdefault(a['fields']['weapon'], []).append((props[a['fields']['property']], a['fields']['detail']))
        out_w, out_a = [], []
        for it in items:
            f = it['fields']
            if f.get('weapon') and f['category'] == 'weapon':
                w = weapons[f['weapon']]
                cat = cats.get(re.sub(r'[^a-z]', '', f['name'].lower()))
                assert cat, f"{ed} {f['name']}: not found under a section of the SRD weapons table"
                ps, mastery = [], None
                for p, detail in sorted(assigned.get(f['weapon'], []), key=lambda x: x[0]['name']):
                    if p.get('type') == 'Mastery':
                        mastery = F.Kw(slug(p['name']))
                    else:
                        k = 'special' if p['name'].startswith('Special') else slug(p['name'])
                        ps.append({'key': F.Kw(k), **({'detail': detail} if detail else {})})
                rec = {'key': F.Kw(slug(f['name'])), 'name': f['name'],
                       'category': F.Kw(cat),
                       'cost': coin(f['cost']), 'weight': number(f['weight']),
                       'damage': {'dice': w['damage_dice'], 'type': F.Kw(w['damage_type'].lower())} if w['damage_dice'] else None,
                       'properties': ps}
                if ed == 2024:
                    rec['mastery'] = mastery
                out_w.append(rec)
            elif (f.get('armor') and f['category'] == 'armor') or f['category'] == 'shield':
                if f['category'] == 'shield' or (f.get('armor') and armors[f['armor']]['name'] == 'Shield'):
                    out_a.append({'key': F.Kw('shield'), 'name': 'Shield', 'category': F.Kw('shield'),
                                  'cost': coin(f['cost']), 'weight': number(f['weight']), 'ac': {'bonus': 2}})
                    continue
                a = armors[f['armor']]
                cat = 'heavy' if not a['ac_add_dexmod'] else ('medium' if a['ac_cap_dexmod'] else 'light')
                out_a.append({'key': F.Kw(slug(re.sub(r' Armor$', '', f['name']))), 'name': f['name'], 'category': F.Kw(cat),
                              'cost': coin(f['cost']), 'weight': number(f['weight']),
                              'ac': {'base': a['ac_base'], 'dex?': bool(a['ac_add_dexmod']),
                                     **({'max-dex': a['ac_cap_dexmod']} if a['ac_cap_dexmod'] else {})},
                              'strength': a['strength_score_required'] or None,
                              'stealth-disadvantage?': bool(a['grants_stealth_disadvantage'])})
        order = ['light', 'medium', 'heavy', 'shield']
        out_a.sort(key=lambda a: (order.index(a['category'].name), a['cost']['num'] * {'gp': 100, 'sp': 10, 'cp': 1}[a['cost']['type'].name]))
        for w in out_w:      # the SRD table and open5e's is_simple must agree
            assert w['category'].name.startswith('simple') == bool(weapons[pre + w['key'].name]['is_simple'] if (pre + w['key'].name) in weapons else w['category'].name.startswith('simple')), w['name']
        out_w.sort(key=lambda w: (w['category'].name, w['name']))
        data = {'srd/document': doc, 'srd/edition': ed, 'srd/license': 'CC BY 4.0', 'weapons': out_w, 'armor': out_a}
        body = F.edn(data).replace(' :weapons [{', '\n :weapons\n [{').replace(' :armor [{', '\n :armor\n [{').replace('} {:key', '}\n  {:key')
        out = srd52 / f'resources/public/srd/{ed}/equipment.edn'
        out.write_text(f';; Generated by scripts/srd/emit-equipment.py (agents/develop) from open5e-api\n'
                       f';; srd-corrections {rev}. Do not edit: fix the source and regenerate.\n' + body + '\n',
                       encoding='utf-8')
        print(f'  {out}: {len(out_w)} weapons, {len(out_a)} armor'
              + (f', mastery on {sum(1 for w in out_w if w.get("mastery"))}' if ed == 2024 else ''))


if __name__ == '__main__':
    main(*sys.argv[1:3])
