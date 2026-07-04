import { test, expect } from '@playwright/test';
import * as fs from 'fs';
import { seedPlugins, gotoMyContent } from '../lib/builder';

// EMERGENCY EXPORT — the escape hatch when a normal export refuses. When a saved
// source fails the hard ::e5/plugin spec check, the export-failure banner offers
// "Download raw backup instead", which dumps the RAW source unvalidated so the
// user can always get their data out. The pure picker is unit-tested; this proves
// the actual in-app link works AND that the raw dump preserves EVERYTHING.
//
// The bad data is deliberately THOROUGH: four content types, each broken a
// DIFFERENT way that defeats normal export, with rich nested fields throughout —
// so the test fails if the raw export drops any of it. Every break is one that
// passes the missing-required-fields check (so it reaches the emergency branch,
// not the auto-fill modal): present names, but invalid keys / bad option-pack.
const BUGGED_SOURCE = `
{"Bugged Homebrew"
 {:orcpub.dnd.e5/classes
  {:9-lives-sorcerer                       ; KEY starts with a digit -> invalid
   {:name "9 Lives Sorcerer" :key :9-lives-sorcerer :option-pack "Bugged Homebrew"
    :hit-die 6 :ability-increase-levels [4 8 12 16 19]
    :subclass-level 1 :subclass-title "Feline Bloodline"
    :spellcasting {:level-factor 1 :known-mode :schedule :ability :cha}
    :traits [{:name "Nine Lives" :level 1
              :description "Spend a life to cheat death; you have nine."}
             {:name "Feline Grace" :level 6
              :description "Advantage on Dexterity saves."}]
    :profs {:save {:con true :cha true}}}}

  :orcpub.dnd.e5/spells
  {:3rd-eye-blast                          ; digit-leading KEY *and* no option-pack
   {:name "3rd Eye Blast" :key :3rd-eye-blast
    :school "evocation" :level 2 :casting-time "1 action"
    :range "60 feet" :duration "Instantaneous"
    :components {:verbal true :somatic true :material true
                 :material-component "a polished obsidian lens"}
    :description "A searing blast erupts from your third eye, dealing 4d8 force."
    :spell-lists {:wizard true :sorcerer true}}}

  :orcpub.dnd.e5/monsters
  {:42-armed-horror                        ; digit-leading KEY *and* numeric option-pack
   {:name "42-Armed Horror" :key :42-armed-horror :option-pack 5
    :size :large :type "aberration" :alignment "chaotic evil"
    :armor-class 17 :hit-points {:die 10 :die-count 18}
    :speed "30 ft., climb 30 ft."
    :str 23 :dex 14 :con 20 :int 19 :wis 16 :cha 21
    :traits [{:name "Forty-Two Limbs"
              :description "Makes up to forty-two attacks, one per arm, per turn."}]
    :description "A writhing nightmare of too many grasping arms."}}

  :orcpub.dnd.e5/races
  {:-voidtouched                           ; KEY starts with a dash -> invalid
   {:name "-Voidtouched" :key :-voidtouched :option-pack "Bugged Homebrew"
    :size :medium :speed 30 :darkvision 60
    :abilities {:cha 2 :int 1}
    :traits [{:name "Void Sight" :description "You can see in magical darkness."}
             {:name "Null Resistance" :description "Resistance to necrotic damage."}]}}}}
`;

test('emergency raw export rescues a thoroughly-bugged source the normal export refuses', async ({ page }) => {
  await seedPlugins(page, BUGGED_SOURCE);
  await gotoMyContent(page);

  // Expand the source and click its real "export" button (My Content UI).
  const source = page.getByText('Bugged Homebrew', { exact: true }).first();
  await expect(source).toBeVisible({ timeout: 10000 });
  await source.click();
  await page.locator('button.form-button', { hasText: /^export$/ }).first().click();

  // Normal export must REFUSE (hard spec failure) and surface the escape hatch.
  await expect(
    page.getByText('contains invalid data', { exact: false }).last(),
    'normal export is correctly refused',
  ).toBeVisible({ timeout: 10000 });
  const rawLink = page.getByText('Download raw backup instead', { exact: true }).last();
  await expect(rawLink).toBeVisible();

  // The escape hatch downloads the RAW source, unvalidated.
  const [download] = await Promise.all([
    page.waitForEvent('download'),
    rawLink.click(),
  ]);
  const content = fs.readFileSync(await download.path(), 'utf8');

  // It must preserve EVERYTHING — every content type, every broken key, and the
  // rich nested fields. If the raw dump silently drops a field, this fails.
  // -- structure / content types --
  expect(content).toContain(':orcpub.dnd.e5/classes');
  expect(content).toContain(':orcpub.dnd.e5/spells');
  expect(content).toContain(':orcpub.dnd.e5/monsters');
  expect(content).toContain(':orcpub.dnd.e5/races');
  // -- the invalid keys themselves are kept verbatim (not "repaired" or dropped) --
  expect(content).toContain(':9-lives-sorcerer');
  expect(content).toContain(':3rd-eye-blast');
  expect(content).toContain(':42-armed-horror');
  expect(content).toContain(':-voidtouched');
  // -- class: names, numeric fields, nested traits --
  expect(content).toContain('9 Lives Sorcerer');
  expect(content).toContain(':hit-die 6');
  expect(content).toContain('Feline Bloodline');
  expect(content).toContain('Nine Lives');
  expect(content).toContain('Feline Grace');
  // -- spell: deep nested components + the bad (missing-then-present) fields --
  expect(content).toContain('3rd Eye Blast');
  expect(content).toContain('a polished obsidian lens');
  expect(content).toContain('third eye');
  // -- monster: the numeric option-pack and hit-points survive untouched --
  expect(content).toContain('42-Armed Horror');
  expect(content).toContain(':option-pack 5');
  expect(content).toContain('Forty-Two Limbs');
  // -- race: dash-named, nested traits --
  expect(content).toContain('-Voidtouched');
  expect(content).toContain('Void Sight');
  expect(content).toContain('Null Resistance');
});
