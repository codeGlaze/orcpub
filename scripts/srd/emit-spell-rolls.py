#!/usr/bin/env python3
# /// script
# requires-python = ">=3.11"
# dependencies = ["markdown-it-py>=4"]
# ///
"""Emit the SRD 5.1 spells' rolling data (damage, healing, save, attack, area, scaling) as EDN.

Usage: emit-spell-rolls.py OPEN5E_CLONE OUT_WORKTREE [--report]

Reads the corrected open5e data (branch srd-corrections) and writes
  OUT_WORKTREE/resources/public/srd/2014/spell-rolls.edn
a map from our spell key to only the fields that spell has (format: srd-improvements.md, "Spell
rolling data"). The spell text stays in spells.cljc; this file is merged onto it by key.

  :attack :melee|:ranged       a spell attack          :save :dex   a saving throw, and
  :save-dc 15                  a fixed DC, where the spell names one instead of the caster's
  :save-half? true             half damage on a success
  :area {:shape :sphere :size 20}   size in feet: radius (sphere, cylinder), length (cone, line), side (cube)
  :damage [part]  :heal part  :temp-hp part   part = {:dice "8d6" :type :fire}, plus where the spell has them
      :types [:fire :cold]  one type, the caster's choice     :mod? true  + spellcasting modifier
      :per-slot "1d6"  added per slot level above the spell's  :slot-step 2  per two slot levels
      :cantrip? true   the dice count is 1/2/3/4 at character level 1/5/11/17
      :label "Red"     the SRD's own word for a part only some targets or rounds take
      :save :con       a part's own saving throw, where it is not the spell's
  :per-slot part               an extra part per slot level, of a type the caster chooses
  :count {:n 3 :per-slot 1}    darts, rays or beams, each rolling the damage; or {:n 1 :cantrip? true}

Values come from the spell text by pattern; SPELLS below holds every spell the patterns cannot
read, written by hand. Every dice string, number and type is checked against that spell's SRD
text, and differences from open5e's own fields are listed with --report.
"""
import json, re, subprocess, sys
from pathlib import Path
sys.path.insert(0, str(Path(__file__).parent))
import srd_format as F

TYPES = ('acid bludgeoning cold fire force lightning necrotic piercing poison psychic radiant '
         'slashing thunder').split()
ABILITY = {'strength': 'str', 'dexterity': 'dex', 'constitution': 'con', 'intelligence': 'int',
           'wisdom': 'wis', 'charisma': 'cha'}
DMG = re.compile(r'(\d+d\d+(?: \+ \d+)?|\b\d+) (' + '|'.join(TYPES) + r') damage')
HEAL = re.compile(r'(?:regains?|regain) (?:a number of )?hit points equal to (\d+d\d+)( \+ your spellcasting ability modifier)?')
PER_SLOT = re.compile(r'(?:the )?(?:(' + '|'.join(TYPES) + r') )?(?:damage|healing)(?: \(both initial and later\))? increases by (\d+d\d+) for each slot level above')
CANTRIP = re.compile(r"damage increases by (\d+d\d+) when you reach 5th level \(2d\d+\), 11th level \(3d\d+\), and 17th level \(4d\d+\)")
AREA = re.compile(r'(\d+)-foot(?:-radius)?(?:, \d+-foot-high)? (sphere|cone|cube|cylinder)')
LINE = re.compile(r'line (\d+) feet long and \d+ feet wide')


def P(dice, type_=None, **kw):
    """A damage or healing part for SPELLS. Args: dice string, type name or a list to choose from,
    and any part keys (per_slot, slot_step, mod, cantrip, label). Returns the part dict."""
    p = {'dice': dice}
    if isinstance(type_, list):
        p['types'] = [F.Kw(t) for t in type_]
    elif type_:
        p['type'] = F.Kw(type_)
    names = {'per_slot': 'per-slot', 'slot_step': 'slot-step', 'mod': 'mod?', 'cantrip': 'cantrip?'}
    p.update({names.get(k, k): v for k, v in kw.items()})
    return p


# Spells the patterns read wrongly or not at all. Each value is in the spell's SRD text.
SPELLS = {
    'alter-self': {'damage': [P('1d6', ['bludgeoning', 'piercing', 'slashing'], mod=True)]},
    'arcane-hand': {'damage': [P('4d8', 'force', per_slot='2d8', label='Clenched Fist'),
                               P('2d6', 'bludgeoning', mod=True, per_slot='2d6', label='Grasping Hand')]},
    'black-tentacles': {'damage': [P('3d6', 'bludgeoning')]},
    'control-water': {'damage': [P('2d8', 'bludgeoning')]},
    'delayed-blast-fireball': {'damage': [P('12d6', 'fire', per_slot='1d6')]},
    'fire-shield': {'damage': [P('2d8', ['fire', 'cold'])]},
    'forbiddance': {'damage': [P('5d10', ['radiant', 'necrotic'])]},
    'glyph-of-warding': {'damage': [P('5d8', ['acid', 'cold', 'fire', 'lightning', 'thunder'], per_slot='1d8')]},
    'ice-storm': {'damage': [P('2d8', 'bludgeoning', per_slot='1d8'), P('4d6', 'cold')]},
    'magic-missile': {'damage': [P('1d4 + 1', 'force')], 'count': {'n': 3, 'per-slot': 1}},
    'meld-into-stone': {'damage': [P('6d6', 'bludgeoning')]},
    'prismatic-spray': {'damage': [P('10d6', 'fire', label='Red'), P('10d6', 'acid', label='Orange'),
                                   P('10d6', 'lightning', label='Yellow'), P('10d6', 'poison', label='Green'),
                                   P('10d6', 'cold', label='Blue')]},
    'prismatic-wall': {'damage': [P('10d6', 'fire', label='Red'), P('10d6', 'acid', label='Orange'),
                                  P('10d6', 'lightning', label='Yellow'), P('10d6', 'poison', label='Green'),
                                  P('10d6', 'cold', label='Blue')]},
    'scorching-ray': {'damage': [P('2d6', 'fire')], 'count': {'n': 3, 'per-slot': 1}},
    'eldritch-blast': {'damage': [P('1d10', 'force')], 'count': {'n': 1, 'cantrip?': True}},
    'spirit-guardians': {'damage': [P('3d8', ['radiant', 'necrotic'], per_slot='1d8')]},
    'flame-blade': {'damage': [P('3d6', 'fire', per_slot='1d6', slot_step=2)]},
    'flame-strike': {'per-slot': P('1d6', ['fire', 'radiant'])},
    'earthquake': {'save': F.Kw('dex'), 'save-half?': True, 'damage': [P('5d6', 'bludgeoning')]},
    'heal': {'heal': P('70', per_slot='10')},
    'mass-heal': {'heal': P('700')},
    'regenerate': {'heal': P('4d8 + 15')},
    'false-life': {'temp-hp': P('1d4 + 4', per_slot='5')},
    'spiritual-weapon': {'damage': [P('1d8', 'force', mod=True, per_slot='1d8', slot_step=2)]},
    'storm-of-vengeance': {'damage': [P('2d6', 'thunder'), P('1d6', 'acid', label='Round 2'),
                                      P('10d6', 'lightning', label='Round 3', save=F.Kw('dex')),
                                      P('2d6', 'bludgeoning', label='Round 4'),
                                      P('1d6', 'cold', label='Round 5-10')]},
    'symbol': {'damage': [P('10d10', 'necrotic', label='Death')]},
    'wall-of-ice': {'damage': [P('10d6', 'cold', per_slot='2d6'), P('5d6', 'cold', label='frigid air', per_slot='1d6', save=F.Kw('con'))]},
    'wall-of-thorns': {'damage': [P('7d8', 'piercing', per_slot='1d8'), P('7d8', 'slashing', per_slot='1d8')]},
    'wish': {},
}
# No rolling data although the text has a damage roll: none of these is a roll the caster makes.
NOT_ROLLED = {'wish'}


def text(f):
    """A spell's SRD text: its description and its At Higher Levels paragraph, as one string."""
    return f['desc'] + '\n' + (f['higher_level'] or '')


def from_text(f):
    """The rolling data the patterns read from a spell's text. Args: open5e Spell fields. Returns
    a dict of the file's keys, without :save, :attack and :area (see common)."""
    t, hl = f['desc'], f['higher_level'] or ''
    out, parts = {}, []
    for dice, ty in DMG.findall(t):
        if not any(p['dice'] == dice and p['type'].name == ty for p in parts):
            parts.append(P(dice, ty))
    ps = PER_SLOT.search(hl)
    for p in parts:
        if ps and (not ps.group(1) or ps.group(1) == p['type'].name) and ps.group(2).split('d')[1] == p['dice'].split('d')[1].split(' ')[0]:
            p['per-slot'] = ps.group(2)
        c = CANTRIP.search(text(f))
        if c and c.group(1) == p['dice']:
            p['cantrip?'] = True
    if parts:
        out['damage'] = parts
    h = HEAL.search(t)
    if h:
        out['heal'] = P(h.group(1), mod=True) if h.group(2) else P(h.group(1))
        if ps:
            out['heal']['per-slot'] = ps.group(2)
    return out


def common(f):
    """A spell's attack, saving throw and area from its text. Args: open5e Spell fields."""
    t, out = f['desc'], {}
    a = re.search(r'(melee|ranged) spell attack', t)
    if a:
        out['attack'] = F.Kw(a.group(1))
    s = re.search(r'(?:make|makes|succeed on|succeeds on|fails) (?:an? )?(?:DC (\d+) )?(strength|dexterity|constitution|intelligence|wisdom|charisma) saving throw', t, re.I)
    if s:
        out['save'] = F.Kw(ABILITY[s.group(2).lower()])
        if s.group(1):
            out['save-dc'] = int(s.group(1))
        if re.search(r'half as much damage', t):
            out['save-half?'] = True
    m = AREA.search(t)
    if m:
        out['area'] = {'shape': F.Kw(m.group(2)), 'size': int(m.group(1))}
    elif LINE.search(t):
        out['area'] = {'shape': F.Kw('line'), 'size': int(LINE.search(t).group(1))}
    return out


def check(key, rec, f):
    """Problems with a spell's record: each dice string, number and type must be in its text.
    Returns a list of strings, empty when it is right."""
    t, bad = text(f), []
    for p in rec.get('damage', []) + [rec[k] for k in ('heal', 'temp-hp', 'per-slot') if k in rec]:
        for d in [p['dice'], p.get('per-slot')]:
            if d and d not in t and d.replace(' ', '') not in t.replace(' ', ''):
                bad.append(f'{key}: {d} is not in the text')
        for ty in [p.get('type')] + p.get('types', []):
            if ty and ty.name not in t:
                bad.append(f'{key}: {ty.name} is not in the text')
        if 'label' in p and p['label'] not in t:
            bad.append(f'{key}: label {p["label"]} is not in the text')
    if 'area' in rec and str(rec['area']['size']) not in t:
        bad.append(f'{key}: area size {rec["area"]["size"]} is not in the text')
    return bad


def against_open5e(key, rec, f):
    """Where a record differs from open5e's own damage, type and save fields, as strings."""
    out = []
    ours = {p['dice'].replace(' ', '') for p in rec.get('damage', [])} | ({rec['heal']['dice']} if 'heal' in rec else set())
    if f['damage_roll'] and f['damage_roll'].replace(' ', '') not in ours:
        out.append(f'{key}: open5e damage_roll {f["damage_roll"]!r}, ours {sorted(ours)}')
    types = {t.name for p in rec.get('damage', []) for t in [p.get('type')] + p.get('types', []) if t}
    if set(f['damage_types']) != types and not (rec == {} and key in NOT_ROLLED):
        out.append(f'{key}: open5e damage_types {f["damage_types"]}, ours {sorted(types)}')
    save = ABILITY.get(f['saving_throw_ability'])
    if save != (rec['save'].name if 'save' in rec else None):
        out.append(f'{key}: open5e save {save}, ours {rec["save"].name if "save" in rec else None}')
    return out


def kw(name):
    """Our spell key for a name, as common/name-to-kw makes it."""
    n = re.sub(r'\W', '-', name.lower().replace("'", '').replace('’', ''))
    return re.sub(r'-+', '-', n).rstrip('-')


def collect(clone):
    """Every spell with rolling data. Returns ({key: record}, [problems], [open5e differences])."""
    spells = json.load(open(clone / 'data/v2/wizards-of-the-coast/srd-2014/Spell.json', encoding='utf-8'))
    out, bad, diff = {}, [], []
    for s in sorted(spells, key=lambda s: kw(s['fields']['name'])):
        f, key = s['fields'], kw(s['fields']['name'])
        rec = {} if key in NOT_ROLLED else {**common(f), **from_text(f), **SPELLS.get(key, {})}
        bad += check(key, rec, f)
        diff += against_open5e(key, rec, f)
        if rec:
            out[key] = rec
    return out, bad, diff


def main(clone, worktree, *flags):
    clone, worktree = Path(clone), Path(worktree)
    rev = subprocess.run(['git', '-C', str(clone), 'rev-parse', '--short', 'HEAD'],
                         capture_output=True, text=True).stdout.strip()
    data, bad, diff = collect(clone)
    if '--report' in flags:
        print('\n'.join(['open5e differs:'] + diff))
    if bad:
        sys.exit('\n'.join(['not in the SRD text:'] + bad))
    body = '{' + '\n '.join(f':{k} {F.edn(v)}' for k, v in data.items()) + '}'
    out = worktree / 'resources/public/srd/2014/spell-rolls.edn'
    out.write_text(f';; Generated by scripts/srd/emit-spell-rolls.py (agents/develop) from open5e-api\n'
                   f';; srd-corrections {rev}. Do not edit: fix the source and regenerate.\n' + body + '\n',
                   encoding='utf-8')
    print(f'  {out}: {len(data)} spells, {sum(1 for v in data.values() if "damage" in v)} with damage, '
          f'{sum(1 for v in data.values() if "heal" in v)} with healing, {out.stat().st_size} bytes')


if __name__ == '__main__':
    main(*sys.argv[1:])
