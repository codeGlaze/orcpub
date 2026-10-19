#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = ["markdown-it-py>=4"]
# ///
"""Emit the 2014 SRD weapons and armor in the app's own shape, as a Clojure source file.

Usage: emit-app-equipment.py OPEN5E_CLONE SRD52_WORKTREE APP_WORKTREE

Reads the corrected open5e data through emit-equipment.py's `collect` and writes
  APP_WORKTREE/src/cljc/orcpub/dnd/e5/srd_equipment.cljc
with `weapons` and `armor` in exactly the shape weapons.cljc and armor.cljc hold today, so the
app can merge its own fields (subtype tags, non-SRD items) onto them by :key. Generated source,
like the e55 files: the app needs the data at build time and reads no files then.
KB: docs/kb/srd-2024-roadmap.md ("One pipeline"), docs/kb/equipment-parity.md.
"""
import importlib.util, re, subprocess, sys
from pathlib import Path

HERE = Path(__file__).parent
sys.path.insert(0, str(HERE))
spec = importlib.util.spec_from_file_location('emit_equipment', HERE / 'emit-equipment.py')
EQ = importlib.util.module_from_spec(spec)
spec.loader.exec_module(EQ)

W = 'orcpub.dnd.e5.weapons'
KEYS = {'studded-leather': 'studded'}   # SRD slug -> the app's existing key (equipment-parity.md)
FLAGS = {'finesse': 'finesse?', 'heavy': 'heavy?', 'light': 'light?', 'loading': 'loading?',
         'reach': 'reach', 'special': 'special?', 'two-handed': 'two-handed?', 'thrown': 'thrown',
         'ammunition': 'ammunition?'}


def kw(name, ns=None):
    """A keyword's EDN text: kw('gp') is :gp, kw('melee?', W) is :orcpub.dnd.e5.weapons/melee?."""
    return f':{ns}/{name}' if ns else f':{name}'


def s(x):
    """A string's EDN text."""
    return '"' + x.replace('\\', '\\\\').replace('"', '\\"') + '"'


def weapon_weight(lb):
    """A weapon weight as the app writes it: 0.25 is "1/4 lb.", 1.5 is "1½ lb.", 0 is "—"."""
    if not lb:
        return '—'
    if lb == 0.25:
        return '1/4 lb.'
    whole, frac = int(lb), lb - int(lb)
    if frac == 0.5:
        return f'{whole}½ lb.'
    assert frac == 0, f'weight {lb} has no app form yet'
    return f'{whole} lb.'


def dice(d):
    """Damage dice as (count, die): "1d8" is (1, 8), a flat "1" is (1, 1)."""
    m = re.fullmatch(r'(\d+)d(\d+)', d or '')
    if m:
        return int(m[1]), int(m[2])
    m = re.fullmatch(r'[1-9]\d*', d or '')
    return (int(m[0]), 1) if m else None


def cost(c):
    """A {:num :type} cost's EDN text."""
    return '{:num %d :type %s}' % (c['num'], kw(c['type'].name))


def weapon(w):
    """One SRD weapon (served shape) as the app's EDN map text."""
    key = KEYS.get(w['key'].name, w['key'].name)
    cat = w['category'].name
    props = {p['key'].name: p.get('detail', True) for p in w['properties']}
    f = [(':key', kw(key)), (':name', s(w['name'])), (':cost', cost(w['cost'])),
         (':weight', s(weapon_weight(w['weight']))),
         (kw('type', W), kw(cat.split('-')[0])),
         (kw('melee?' if cat.endswith('melee') else 'ranged?', W), 'true')]
    dd = dice(w['damage']['dice']) if w.get('damage') else None
    if dd:
        f += [(kw('damage-die-count', W), str(dd[0])), (kw('damage-die', W), str(dd[1])),
              (kw('damage-type', W), kw(w['damage']['type'].name))]
    for p, field in FLAGS.items():
        if p in props:
            f.append((kw(field, W), 'true'))
    r = re.search(r'range (\d+)/(\d+)', str(props.get('ammunition') or props.get('thrown') or ''))
    if r:
        f.append((kw('range', W), '{%s %s, %s %s}' % (kw('min', W), r[1], kw('max', W), r[2])))
    if 'versatile' in props:
        vc, vd = dice(props['versatile'])
        f.append((kw('versatile', W), '{%s %d, %s %d}' % (kw('damage-die', W), vd, kw('damage-die-count', W), vc)))
    return '{' + '\n    '.join(f'{k} {v}' for k, v in f) + '}'


def armor(a):
    """One SRD armor (served shape) as the app's EDN map text."""
    key = KEYS.get(a['key'].name, a['key'].name)
    f = [(':key', kw(key)), (':name', s(a['name'])), (':cost', cost(a['cost'])),
         (':weight', str(int(a['weight']) if a['weight'] == int(a['weight']) else a['weight'])),
         (':type', kw(a['category'].name))]
    ac = a.get('ac', {})
    if 'base' in ac:
        f.append((':base-ac', str(ac['base'])))
        if not ac.get('dex?'):
            f.append((':max-dex-mod', '0'))
        elif 'max-dex' in ac:
            f.append((':max-dex-mod', str(ac['max-dex'])))
    if a.get('strength'):
        f.append((':min-str', str(a['strength'])))
    if a.get('stealth-disadvantage?'):
        f.append((':stealth-disadvantage?', 'true'))
    return '{' + '\n    '.join(f'{k} {v}' for k, v in f) + '}'


def main(clone, srd52, app):
    """Writes APP/src/cljc/orcpub/dnd/e5/srd_equipment.cljc from the clone's 2014 SRD data."""
    clone, srd52, app = Path(clone), Path(srd52), Path(app)
    rev = subprocess.run(['git', '-C', str(clone), 'rev-parse', '--short', 'HEAD'],
                         capture_output=True, text=True).stdout.strip()
    ws, ars = EQ.collect(clone, srd52, 2014)
    out = app / 'src/cljc/orcpub/dnd/e5/srd_equipment.cljc'
    out.write_text(
        ';; Generated by scripts/srd/emit-app-equipment.py (agents/develop) from open5e-api\n'
        f';; srd-corrections {rev}. Do not edit: fix the source and regenerate.\n'
        '(ns orcpub.dnd.e5.srd-equipment\n'
        '  "The SRD 5.1 weapons and armor in the app\'s shape, generated. weapons.cljc and armor.cljc\n'
        '   merge their own fields onto these by :key.")\n\n'
        '(def weapons\n  "SRD 5.1 weapons, keyed by :key, in the shape of orcpub.dnd.e5.weapons/weapons."\n  ['
        + '\n   '.join(weapon(w) for w in ws) + '])\n\n'
        '(def armor\n  "SRD 5.1 armor and the shield, in the shape of orcpub.dnd.e5.armor/armor."\n  ['
        + '\n   '.join(armor(a) for a in ars) + '])\n', encoding='utf-8')
    print(f'  {out}: {len(ws)} weapons, {len(ars)} armor')


if __name__ == '__main__':
    main(*sys.argv[1:4])
