// The SRD reference: the Rules tab and pages, links in rules text with their previews (hover
// on a desktop, tap on a phone), the conditions pages, the Orcacle finding rules and conditions,
// and the monster stat block's Reactions section and condition links.
//
// Drives the DOM only, so it runs on the production bundle the public site serves:
//   the conditions list page (15 conditions and the CC BY attribution), loaded from the static
//   /srd/2014/conditions.edn; a condition's own page asked for directly, as a refresh or a
//   bookmark would; the Marilith's stat block showing its Parry reaction, and its "poisoned"
//   immunity opening the Poisoned page; the Orcacle's top result for "blinded", typed into the
//   header's search box. The Rules page's groups; a rules section asked for directly; a spell
//   link's hover preview and its click; a link to another section; a phone's first tap showing
//   a condition's preview and its Open link following it; the Orcacle finding a rule.
//
// Needs:     server (`./scripts/e2e/run.sh test/browser/srd_reference_e2e.js`). No login.
// Runs in:   ~40s. SHOT_DIR=<dir> also saves screenshots of the pages it visits.
const { chromium, devices } = require('playwright');
const { findChrome } = require('./lib/find-chrome');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOT_DIR = process.env.SHOT_DIR;

const CONDITIONS = ['Blinded', 'Charmed', 'Deafened', 'Exhaustion', 'Frightened', 'Grappled',
  'Incapacitated', 'Invisible', 'Paralyzed', 'Petrified', 'Poisoned', 'Prone', 'Restrained',
  'Stunned', 'Unconscious'];

const results = [];
function check(name, pass, detail) {
  results.push({ name, pass, detail });
  console.log((pass ? 'PASS  ' : 'FAIL  ') + name + (detail ? '   [' + detail + ']' : ''));
}

// Saves a screenshot as SHOT_DIR/<name>.png when SHOT_DIR is set: the whole page, or only the window
// when `fullPage` is false (the Orcacle is an overlay over a page thousands of pixels tall).
const shot = (page, name, fullPage = true) =>
  SHOT_DIR && page.screenshot({ path: `${SHOT_DIR}/${name}.png`, fullPage });

// The page's text once `text` has appeared in it, or what is there after 20s. Reads the body, not
// #app, so a page the server does not know (no #app) fails its checks instead of the whole run.
async function textOnceShown(page, text) {
  try { await page.getByText(text, { exact: false }).first().waitFor({ timeout: 20000 }); } catch (e) {}
  return page.locator('body').innerText();
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 } });
  await ctx.addInitScript(() => {
    try {
      localStorage.setItem('orcpub:no-cookie-banner', '1');
      localStorage.setItem('whats-new-seen', '"summer-patch-2026"');
    } catch (e) {}
  });
  const page = await ctx.newPage();
  const errors = [];
  page.on('pageerror', e => errors.push(e.message));

  let resp = await page.goto(`${BASE}/pages/dnd/5e/conditions`, { waitUntil: 'domcontentloaded' });
  let text = await textOnceShown(page, 'Unconscious');
  check('the conditions page is served', resp && resp.status() === 200, `status=${resp && resp.status()}`);
  const missing = CONDITIONS.filter(c => !text.includes(c));
  check('it lists all 15 conditions', missing.length === 0, missing.length ? `missing ${missing.join(', ')}` : '');
  check('it shows the CC BY attribution', text.includes('System Reference Document 5.1'));
  check('Exhaustion carries its level table', text.includes('Speed reduced to 0'));
  await shot(page, 'conditions-list');

  resp = await page.goto(`${BASE}/pages/dnd/5e/conditions/grappled`, { waitUntil: 'domcontentloaded' });
  text = await textOnceShown(page, 'thunderwave');
  check('a condition page loads directly', resp && resp.status() === 200 && text.includes('Grappled')
        && text.includes('thunderwave'), `status=${resp && resp.status()}`);
  await shot(page, 'condition-grappled');

  await page.goto(`${BASE}/pages/dnd/5e/monsters/marilith`, { waitUntil: 'domcontentloaded' });
  text = await textOnceShown(page, 'Parry');
  check('the Marilith shows a Reactions section', text.includes('Reactions'));
  check('with its Parry reaction', text.includes('Parry'));
  await shot(page, 'monster-marilith');
  const poisoned = page.locator('span.underline.pointer', { hasText: 'poisoned' }).first();
  if (await poisoned.count()) {
    await poisoned.click();
    await page.waitForURL('**/conditions/poisoned', { timeout: 15000 }).catch(() => {});
    // Not 'Poisoned': getByText ignores case and would match the link still on the monster page.
    text = await textOnceShown(page, 'A poisoned creature');
    check('its "poisoned" immunity opens the Poisoned page', page.url().endsWith('/conditions/poisoned')
          && text.includes('A poisoned creature'), page.url());
  } else {
    check('its "poisoned" immunity is a link', false, 'no link found');
  }

  // The header's search box, which is how a visitor reaches the Orcacle.
  await page.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'domcontentloaded' });
  const input = page.getByPlaceholder('search').first();
  await input.waitFor({ timeout: 20000 });
  await input.fill('blinded');
  text = await textOnceShown(page, 'A blinded creature');
  check('the Orcacle finds "blinded"', text.includes('A blinded creature'));
  await shot(page, 'orcacle-blinded', false);

  // --- Rules ---
  await page.goto(`${BASE}/pages/dnd/5e/rules`, { waitUntil: 'domcontentloaded' });
  text = await textOnceShown(page, 'Game Master');
  const groups = ['Playing the Game', 'Combat', 'Spellcasting', 'Characters', 'Equipment', 'Game Master'];
  const missingGroups = groups.filter(g => !text.includes(g));
  check('the Rules page lists its six groups', missingGroups.length === 0, missingGroups.join(', '));
  check('the header has a Rules tab', await page.locator('.header-tab', { hasText: 'rules' }).count() > 0);
  await shot(page, 'rules-page');

  resp = await page.goto(`${BASE}/pages/dnd/5e/rules/damage-and-healing`, { waitUntil: 'domcontentloaded' });
  text = await textOnceShown(page, 'Death Saving Throws');
  check('a rules section loads directly', resp && resp.status() === 200 && text.includes('Death Saving Throws')
        && text.includes('Temporary Hit Points'), `status=${resp && resp.status()}`);
  const mm = page.locator('span.srd-link', { hasText: 'magic missile' }).first();
  if (await mm.count()) {
    // The preview closes when the window scrolls, and hover() scrolls the link into view first:
    // scroll, let it settle, then hover, as a person's pointer would.
    await mm.scrollIntoViewIfNeeded();
    await page.waitForTimeout(400);
    await mm.hover();
    const preview = page.locator('.srd-preview');
    await preview.waitFor({ timeout: 5000 }).catch(() => {});
    const ptext = (await preview.count()) ? await preview.innerText() : '';
    check('hovering a spell link previews the spell', /Magic Missile/.test(ptext) && /Casting Time/.test(ptext),
          ptext.slice(0, 60));
    await shot(page, 'rules-hover-preview', false);
    await mm.click();
    await page.waitForURL('**/spells/magic-missile', { timeout: 15000 }).catch(() => {});
    check('clicking it opens the spell', page.url().endsWith('/spells/magic-missile'), page.url());
  } else {
    check('Damage and Healing links magic missile', false, 'no link found');
  }

  await page.goto(`${BASE}/pages/dnd/5e/rules/combat-sequence`, { waitUntil: 'domcontentloaded' });
  await textOnceShown(page, 'Initiative');
  const ref = page.locator('span.srd-link', { hasText: 'Actions in Combat' }).first();
  if (await ref.count()) {
    await ref.click();
    await page.waitForURL('**/rules/actions-in-combat', { timeout: 15000 }).catch(() => {});
    text = await textOnceShown(page, 'Disengage');
    check('a link to another section opens it', page.url().endsWith('/rules/actions-in-combat') && text.includes('Disengage'),
          page.url());
  } else {
    check('Combat Sequence links Actions in Combat', false, 'no link found');
  }

  await page.goto(`${BASE}/pages/dnd/5e/spells`, { waitUntil: 'domcontentloaded' });
  const box = page.getByPlaceholder('search').first();
  await box.waitFor({ timeout: 20000 });
  await box.fill('death saving throws');
  text = await textOnceShown(page, 'Roll a d20');
  check('the Orcacle finds a rule', text.includes('Roll a d20'));

  check('no page errors', errors.length === 0, errors.slice(0, 2).join(' | '));

  // --- A phone: the first tap previews, Open follows ---
  const phone = await browser.newContext({ ...devices['iPhone 13'] });
  await phone.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1');
          localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {}
  });
  const tp = await phone.newPage();
  await tp.goto(`${BASE}/pages/dnd/5e/rules/madness`, { waitUntil: 'domcontentloaded' });
  await textOnceShown(tp, 'Short-Term Madness');
  const cond = tp.locator('span.srd-link', { hasText: 'paralyzed' }).first();
  if (await cond.count()) {
    const before = tp.url();
    await cond.tap();
    const pv = tp.locator('.srd-preview');
    await pv.waitFor({ timeout: 5000 }).catch(() => {});
    const ptext = (await pv.count()) ? await pv.innerText() : '';
    check('on a phone, the first tap previews the condition', tp.url() === before && /A paralyzed creature/.test(ptext),
          ptext.slice(0, 50));
    await shot(tp, 'phone-tap-preview', false);
    await tp.locator('.srd-preview-footer span', { hasText: 'Open' }).tap().catch(() => {});
    await tp.waitForURL('**/conditions/paralyzed', { timeout: 15000 }).catch(() => {});
    check('and its Open link opens the page', tp.url().endsWith('/conditions/paralyzed'), tp.url());
  } else {
    check('Madness links paralyzed', false, 'no link found');
  }
  await browser.close();
  const failed = results.filter(r => !r.pass).length;
  console.log(`\n${results.length - failed}/${results.length} checks passed`);
  process.exit(failed ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
