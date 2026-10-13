#!/usr/bin/env python3
"""Self-test for check-srd-data.py: every rule must fail on the mistake it exists to catch.

Usage: test-check-srd-data.py [OPEN5E_CLONE] [SRD52_WORKTREE]

Makes a throwaway worktree of the clone's HEAD, checks the clean state passes, then for each rule
plants one mistake, commits it there, runs the check and requires that rule to FAIL. A rule that
stops failing has gone blind (the reformat rule once read no srd-2024 record at all), so this
exits 1. Runs from agents/develop's pre-commit whenever the check or its helpers change. ~30s.
"""
import json, os, subprocess, sys, tempfile
from pathlib import Path

HERE = Path(__file__).parent
for _v in ('GIT_DIR', 'GIT_INDEX_FILE', 'GIT_WORK_TREE', 'GIT_PREFIX', 'GIT_COMMON_DIR',
           'GIT_OBJECT_DIRECTORY', 'GIT_ALTERNATE_OBJECT_DIRECTORIES'):
    os.environ.pop(_v, None)   # a git hook exports these; they would point git -C at the wrong repository
sys.path.insert(0, str(HERE))
import open5e_records as R

CLONE = sys.argv[1] if len(sys.argv) > 1 else '/home/codeglaze/projects/open5e-api'
SRD52 = sys.argv[2] if len(sys.argv) > 2 else '/home/codeglaze/projects/orcpub-srd52'
D14, D24 = 'data/v2/wizards-of-the-coast/srd-2014/', 'data/v2/wizards-of-the-coast/srd-2024/'
OPT14 = D14 + 'SpellCastingOption.json'


def option_pk(wt, path, parent, type_):
    """The pk of a spell's casting option of the given type."""
    return next(r['pk'] for r in json.load(open(wt / path)) if r['fields']['parent'] == parent and r['fields']['type'] == type_)


def field(wt, path, pk, name):
    """A record's current field value."""
    return next(r['fields'][name] for r in json.load(open(wt / path)) if r['pk'] == pk)


def plant_reformat(wt):
    """Adds a space inside a record whose content does not change."""
    p = wt / OPT14
    t = p.read_text(encoding='utf-8')
    p.write_text(t.replace('"model": "api_v2.spellcastingoption",', '"model":  "api_v2.spellcastingoption",', 1), encoding='utf-8')


def plant_lint(wt):
    """Puts a double space into an item description."""
    pk = 'srd_marvelous-pigments'
    R.set_field(wt / (D14 + 'MagicItem.json'), pk, 'desc', field(wt, D14 + 'MagicItem.json', pk, 'desc').replace('a pit', 'a  pit', 1))


def plant_gap(wt):
    """Removes Conjure Animals' slot 6 option."""
    R.drop(wt / OPT14, option_pk(wt, OPT14, 'srd_conjure-animals', 'slot_level_6'))


def plant_below_base(wt):
    """Adds a slot 2 option to Acid Arrow, a 2nd-level spell."""
    R.append(wt / OPT14, {'model': 'api_v2.spellcastingoption', 'pk': R.next_pk(wt, 'SpellCastingOption.json'),
                          'fields': {'parent': 'srd_acid-arrow', 'type': 'slot_level_2', 'damage_roll': '4d4'}})


def acid5(wt):
    """Acid Arrow's slot 5 option pk."""
    return option_pk(wt, OPT14, 'srd_acid-arrow', 'slot_level_5')


# rule -> how to plant its mistake
PLANTS = {
    'fidelity': lambda wt: R.set_field(wt / OPT14, acid5(wt), 'desc', 'Totally invented wording appears here.'),
    'punctuation': lambda wt: R.set_field(wt / (D14 + 'MagicItem.json'), 'srd_marvelous-pigments', 'desc',
                                          field(wt, D14 + 'MagicItem.json', 'srd_marvelous-pigments', 'desc').replace('weapons—that', 'weapons-that')),
    'name-case': lambda wt: R.set_field(wt / (D14 + 'Armor.json'), 'srd_chain-mail', 'name', 'Chainmail Armor'),
    'empty-option': lambda wt: R.set_field(wt / OPT14, option_pk(wt, OPT14, 'srd_aid', 'slot_level_3'), 'desc', None),
    'base-missing': lambda wt: R.set_field(wt / (D14 + 'Spell.json'), 'srd_acid-splash', 'damage_roll', ''),
    'value-in-srd': lambda wt: R.set_field(wt / (D14 + 'Spell.json'), 'srd_acid-splash', 'damage_roll', '1d7'),
    'damage-types': lambda wt: R.set_field(wt / (D14 + 'Spell.json'), 'srd_fireball', 'damage_types', ['fire', 'cold']),
    # damage done TO the wall ("destroyed by dealing at least 60 force damage") is not damage it deals
    'damage-types/destroyed': lambda wt: R.set_field(wt / (D14 + 'Spell.json'), 'srd_prismatic-wall', 'damage_types',
                                                     ['fire', 'cold', 'acid', 'lightning', 'poison', 'force']),
    # "damage of a type you choose: Acid, Cold, ..." must count those types as dealt
    'damage-types/chosen': lambda wt: R.set_field(wt / (D24 + 'Spell.json'), 'srd-2024_sorcerous-burst', 'damage_types', ['acid']),
    'slot-gap': plant_gap,
    'dice-only': lambda wt: R.set_field(wt / OPT14, acid5(wt), 'damage_roll', '+2'),
    'slot-below-base': plant_below_base,
    'upcast-math': lambda wt: R.set_field(wt / OPT14, acid5(wt), 'damage_roll', '8d4'),
    # Wall of Ice scales two damages: a wrong value there must surface as unverified, never pass as checked
    'unverified/two-damages': lambda wt: R.set_field(wt / OPT14, option_pk(wt, OPT14, 'srd_wall-of-ice', 'slot_level_7'), 'damage_roll', '11d6'),
    'cantrip-math': lambda wt: R.set_field(wt / OPT14, option_pk(wt, OPT14, 'srd_fire-bolt', 'player_level_11'), 'damage_roll', '2d10'),
    'desc-math': lambda wt: R.set_field(wt / OPT14, acid5(wt), 'desc', 'The damage (both initial and later) increases by 4d4.'),
    'desc-consistency': lambda wt: R.set_field(wt / OPT14, option_pk(wt, OPT14, 'srd_aid', 'slot_level_4'), 'desc',
                                               "Each target's hit point maximum increases by an additional 10."),
    'unverified': lambda wt: R.set_field(wt / OPT14, option_pk(wt, OPT14, 'srd_fog-cloud', 'slot_level_3'), 'shape_size', 61.0),
    'lint-ratchet': plant_lint,
    'reformat': plant_reformat,
}


def run_check(wt):
    """Runs the check on the worktree; returns {rule: 'ok'|'FAIL'}."""
    out = subprocess.run([sys.executable, str(HERE / 'check-srd-data.py'), str(wt), SRD52],
                         capture_output=True, text=True).stdout
    rules = {line.split()[1]: line.split()[0] for line in out.splitlines() if line.startswith(('ok ', 'FAIL '))}
    return rules or {'(the check crashed)': 'CRASH'}


def git(wt, *args):
    """Runs git quietly in the worktree."""
    subprocess.run(['git', '-C', str(wt), *args], check=True, capture_output=True)


def main():
    """Plants each mistake and requires its rule to fail; returns the process exit code."""
    tmp = Path(tempfile.mkdtemp(prefix='srd-selftest-'))
    wt = tmp / 'wt'
    subprocess.run(['git', '-C', CLONE, 'worktree', 'add', '-q', '--detach', str(wt), 'HEAD'], check=True)
    bad = []
    try:
        clean = run_check(wt)
        if any(v == 'FAIL' for v in clean.values()):
            print('clean HEAD already fails:', [k for k, v in clean.items() if v == 'FAIL'])
            return 1
        for rule, plant in PLANTS.items():
            plant(wt)
            git(wt, '-c', 'commit.gpgsign=false', 'commit', '-qam', f'self-test: {rule}')
            got = run_check(wt).get(rule.split('/')[0])          # "rule/variant" plants a second case for a rule
            print(f'{"ok  " if got == "FAIL" else "BLIND"}  {rule}')
            if got != 'FAIL':
                bad.append(rule)
            git(wt, 'reset', '-q', '--hard', 'HEAD~1')
        missing = set(run_check(wt)) - {k.split('/')[0] for k in PLANTS}
        if missing:
            print('rules with no planted mistake:', sorted(missing))
            bad += sorted(missing)
    finally:
        subprocess.run(['git', '-C', CLONE, 'worktree', 'remove', '--force', str(wt)], capture_output=True)
    print('self-test', 'FAILED: ' + ', '.join(bad) if bad else 'passed: every rule fails on its mistake')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
