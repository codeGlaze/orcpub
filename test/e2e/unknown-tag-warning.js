// Proof that a typo'd condition tag is SURFACED, not silently swallowed.
//
// weapons/matches? and requirements/meets-all? both IGNORE a tag they do not recognise — deliberate
// forward-compat, so a pack from a newer build still applies what this build understands. The cost
// is that it fails OPEN: `:armour?` (British spelling) is not a tag, so the bonus applies with NO
// condition at all rather than erroring. Nothing at runtime can tell a typo from a future tag, so
// the guard has to be at authoring time.
//
// Drives the real app: import a pack carrying the typo, then check the two places it must surface —
// ON THE BUILDER FORM, where the person who can fix it is looking, and at export.
//
// Prereqs:  lein fig:build && lein e2e-server
// Run:      node test/e2e/unknown-tag-warning.js
const fs = require('fs');
const os = require('os');
const path = require('path');
const { chromium } = require('playwright');
const { BASE, SHOTS, findChrome, checker, dbAt, clickText, dismissCookieBar, dismissWhatsNew } = require('./lib');

const PACK = 'Typo Pack';

// Two feats: one correct, one with :armour? where :armor? was meant. Both are otherwise valid, so
// nothing else can be what trips the warning.
const ORCBREW = `{"${PACK}"
 {:orcpub.dnd.e5/feats
  {:sound-feat  {:name "Sound Feat"  :key :sound-feat  :option-pack "${PACK}"
                 :props {:ac-bonus {:bonus 2 :armor? true}}}
   :typo-feat   {:name "Typo Feat"   :key :typo-feat   :option-pack "${PACK}"
                 :props {:ac-bonus {:bonus 2 :armour? true}}}}}}`;

(async () => {
  fs.mkdirSync(SHOTS, { recursive: true });
  const { check, report } = checker();
  const file = path.join(os.tmpdir(), 'typo-pack.orcbrew');
  fs.writeFileSync(file, ORCBREW);

  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads: true, viewport: { width: 1300, height: 1000 } });
  const page = await ctx.newPage();
  const consoleWarnings = [];
  page.on('console', m => { if (m.type() === 'warning') consoleWarnings.push(m.text()); });

  try {
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);

    const input = await page.$('input[type=file]');
    check('found the import input', !!input);
    await input.setInputFiles(file);
    await page.waitForTimeout(2500);

    const stored = await dbAt(page, `[:plugins "${PACK}"]`);
    check('the pack imported', /typo-feat/.test(stored), stored.slice(0, 160));
    check('and the typo is still in the data — import does not silently repair it',
          /:armour\?/.test(stored));

    // Export through the real button
    await page.locator('#app .item-list-item').filter({ hasText: PACK }).first()
              .locator('text=expand').first().click();
    await page.waitForTimeout(400);
    const [download] = await Promise.all([
      page.waitForEvent('download', { timeout: 20000 }),
      page.locator('#app button:visible').filter({ hasText: /^export$/i }).first().click(),
    ]);
    await download.saveAs(path.join(os.tmpdir(), download.suggestedFilename()));
    await page.waitForTimeout(1200);

    // ── the two surfaces ──────────────────────────────────────────────────────
    const detail = consoleWarnings.find(t => /unrecognised tag/i.test(t)) || '';
    check('the console carries the detail', !!detail, detail.slice(0, 220));
    check('  naming the offending tag', /:armour\?/.test(detail));
    check('  and the item it is on',    /typo-feat/.test(detail));
    check('  and saying what goes wrong — it applies with NO condition',
          /IGNORED|no\s+condition/i.test(detail));
    check('  while the CORRECT feat is not flagged', !/sound-feat/.test(detail), detail.slice(0, 220));

    const body = await page.locator('#app').innerText();
    // The banner names the pack: “Exported “Typo Pack” with warnings” (curly quotes, 5s TTL).
    const banner = (body.match(/Exported[^\n]*with warnings[^\n]*/i) || [''])[0];
    check('and the screen says so too (not console-only)',
          new RegExp(`Exported.*${PACK}.*with warnings`, 'i').test(body), banner || body.slice(0, 200));

    // ── the surface that actually helps: the builder form, on the item ────────
    // Reached the way a person reaches it — My Content, expand down to the item, click its edit
    // button. The builder page has no list of its own.
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil: 'load' });
    await page.waitForTimeout(1500);
    await dismissCookieBar(page);
    await dismissWhatsNew(page);
    // source -> type section -> the item's own edit button. IDEMPOTENT: a row shows "expand" when
    // closed and "collapse" when open, and the export step above already opened the source — so
    // clicking "expand" unconditionally would close it again.
    const openRow = async (hasText) => {
      const row = page.locator('#app .item-list-item').filter({ hasText }).first();
      if (await row.locator('text=expand').count()) {
        await row.locator('text=expand').first().click();
        await page.waitForTimeout(700);
      }
    };
    await openRow(PACK);
    await openRow(/\d+ Feat/);
    // Individual items are not .item-list-item — that class is the SOURCE container, and it holds
    // both feats, so any locator filtered on it clicks whichever edit button comes first. Find the
    // INNERMOST element carrying the item's name that also owns an edit button.
    const opened = await page.evaluate(() => {
      const rows = [...document.querySelectorAll('#app div')].filter(e =>
        /Typo Feat/.test(e.textContent || '') &&
        [...e.querySelectorAll('button')].some(b => b.textContent.trim() === 'edit'));
      const innermost = rows[rows.length - 1];                 // deepest match owns just this item
      if (!innermost) return false;
      const b = [...innermost.querySelectorAll('button')].find(x => x.textContent.trim() === 'edit');
      if (!b) return false;
      b.click();
      return true;
    });
    check('opened the typo feat in the builder (My Content -> edit)', opened);
    await page.waitForTimeout(1600);
    const form = await page.locator('#app').innerText();
    check('  and it is the TYPO feat that is loaded, not its neighbour',
          /typo-feat/.test(await dbAt(page, '[:orcpub.dnd.e5.feats/builder-item]')));
    check('the FORM flags it, where the person who can fix it is looking',
          /unrecognised tag/i.test(form), form.slice(0, 300));
    check('  naming the tag on the form too', /:armour\?/.test(form));
    // Two shots: the whole page, and the advisory on its own — a 12px italic line is easy to miss
    // in a full-page capture of a form this long, and "it is in there somewhere" is not proof.
    const note = page.locator('#app').getByText(/Fix before saving/i).first();
    const box = await note.boundingBox();
    // Not just "the DOM has it" — a 12px line at y=407 passed that and was unreadable in practice.
    check('it is on screen with a real box', !!box && box.height > 0, JSON.stringify(box));
    // KNOWN GAP, not asserted: at ~16px partway down a long form this is still easy to miss. The
    // fix is not more CSS here — builder-notes is SUPERSEDED (it does not exist on integration;
    // views/notifications.cljs replaced it). Redo as a notifications/callout once this branch is
    // current. See roadmap.md.
    if (box && box.height < 24) console.log(`   [gap] advisory is only ${box.height}px tall at y=${box.y}`);
    check('and it reads as something to fix, not as background guidance',
          /fix before saving/i.test(await page.locator('#app').innerText()));
    await page.locator('#app').locator('div', { hasText: /Fix before saving/i }).last()
              .screenshot({ path: path.join(SHOTS, 'unknown-tag-advisory.jpg'), type: 'jpeg', quality: 92 });
    await page.screenshot({ path: path.join(SHOTS, 'unknown-tag-warning.jpg'),
                            fullPage: true, type: 'jpeg', quality: 75 });
    console.log('   [where] advisory box =', JSON.stringify(box));
    console.log('\n--- console warning, verbatim ---\n' + detail + '\n');
  } catch (e) {
    check('ran to completion', false, e.message);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
