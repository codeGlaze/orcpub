// One builder, photographed in the state that actually shows the difference.
//
// An EMPTY form flatters the flat layout: `:when` hides the tags until a bonus is typed, so both
// arrangements look small. The comparison is only honest once the same style is authored in both —
// an AC bonus, an attack bonus and a damage bonus, which is when the flat form puts nineteen
// controls and seven duplicated labels on screen at once.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
// Run:      LABEL=before node test/e2e/form-shape-comparison.js
const fs = require('fs');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, controlFor, fillEffectBonus, dismissCookieBar, pickOption } = require('./lib');

const LABEL = process.env.LABEL || 'current';

// Full-page JPEG. These are documentation assets that get committed, and a full-page PNG of a
// dark UI is ~800KB; the same frame as JPEG is a tenth of that and loses nothing that matters
// for reading a form layout.
const shot = (page, file) => page.screenshot({ path: file, fullPage: true, type: 'jpeg', quality: 72 });


(async () => {
  const dir = path.join(SHOTS, 'form-shape');
  fs.mkdirSync(dir, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1100, height: 1400 } });

  await page.goto(`${BASE}/pages/dnd/5e/fighting-style-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1800);
  await dismissCookieBar(page);

  const count = async () => page.evaluate(() =>
    [...document.querySelectorAll('#app input, #app select, #app textarea, #app .select-menu-btn, #app .chip')]
      .filter(e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; }).length);

  const empty = await count();
  await shot(page, path.join(dir, `${LABEL}-01-empty.jpg`));

  // Add rows if this build has them; on the flat build the buttons are absent and the fields are
  // already on screen, so the same script authors the same style either way.
  for (const title of ['AC Bonus', 'Attack Bonus', 'Damage Bonus']) {
    await page.evaluate((t) => {
      const b = [...document.querySelectorAll('button')].find(e => e.textContent.trim() === `+ ${t}`);
      if (b) b.click();
    }, title);
    await page.waitForTimeout(250);
  }

  for (const [label, value] of [['AC Bonus', '1'], ['Attack Bonus', '2'], ['Damage Bonus', '2']]) {
    if (!await fillEffectBonus(page, label, value)) console.log(`  (could not fill ${label})`);
  }
  // Set one restriction, so the shot shows a tag that actually carries a value — this is
  // Archery, and it is the case the mockup's `select.set` highlight exists for.
  await pickOption(page, 'Ranged', /^ranged (weapons )?only$/);
  await page.waitForTimeout(600);

  const filled = await count();
  await shot(page, path.join(dir, `${LABEL}-02-authored.jpg`));

  // AMBIGUOUS labels, which is the defect that matters — not raw repetition. "Melee" appearing
  // once under a group titled *Attack Bonus* and once under *Damage Bonus* is fine; the reader can
  // tell them apart. "Melee" twice with nothing distinguishing them is the flat form's problem.
  // So: group each visible label by its nearest titled row, and count collisions WITHIN a group
  // (including the ungrouped "group", which is where the flat form puts everything).
  const dupes = await page.evaluate(() => {
    const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
    const groupOf = (el) => {
      for (let n = el; n; n = n.parentElement) {
        const prev = n.previousElementSibling;
        if (prev && prev.querySelector && prev.querySelector('i.fa-times')) {
          const t = prev.querySelector('span');
          if (t) return t.textContent.trim();
        }
      }
      return '(ungrouped)';
    };
    const seen = {};
    [...document.querySelectorAll('#app .f-w-b')]
      .filter(e => vis(e) && e.children.length === 0 && e.textContent.trim())
      .forEach(e => {
        const k = `${groupOf(e)} :: ${e.textContent.trim()}`;
        seen[k] = (seen[k] || 0) + 1;
      });
    return Object.entries(seen).filter(([, c]) => c > 1).map(([l, c]) => `${l} x${c}`);
  });

  const height = await page.evaluate(() => document.querySelector('#app').scrollHeight);
  const out = { label: LABEL, controlsEmpty: empty, controlsAuthored: filled, duplicateLabels: dupes, pageHeight: height };
  fs.writeFileSync(path.join(dir, `${LABEL}.json`), JSON.stringify(out, null, 2));
  console.log(JSON.stringify(out, null, 2));
  await browser.close();
})().catch(e => { console.error(e); process.exit(2); });
