// Phone-viewport pass over the item flows.
//
// The header buttons drop their text labels below 767px, which for a long time
// left them as unlabelled glyphs with no hover to fall back on -- and one of
// them discards the item being edited. Nothing in the desktop suite could see
// that, because the labels are present there.
//
//   E2E_SHOTS=/somewhere node scripts/e2e/mobile.js
//
// Run against a server started the same way scripts/e2e/run.sh starts one.

const { chromium, devices } = require('playwright');
const B = process.env.E2E_BASE || 'http://localhost:8890';
const D = process.env.E2E_SHOTS || '/tmp/e2e-mobile';
require('fs').mkdirSync(D, { recursive: true });

(async () => {
  const br = await chromium.launch({ executablePath: '/opt/pw-browsers/chromium' });
  const ctx = await br.newContext({ ...devices['iPhone 13'] });
  const p = await ctx.newPage();

  await p.goto(B + '/pages/login-page', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('input');
  await p.locator('input').nth(0).fill('kaylee');
  await p.locator('input').nth(1).fill('serenity99');
  await p.locator('button.form-button').click();
  await p.waitForTimeout(3500);
  try { await p.getByText('Got it!', { exact: true }).click({ timeout: 4000 }); } catch (e) {}
  await p.waitForTimeout(500);

  const kindSelect = async () => {
    const s = p.locator('select.builder-option'); const n = await s.count();
    for (let i = 0; i < n; i++) {
      const v = await s.nth(i).evaluate(e => [...e.options].map(o => o.value));
      if (v.includes('mundane')) return s.nth(i);
    }
  };
  // On a phone the header buttons are icon-first; match on the accessible
  // name rather than the visible text.
  const save = async () => {
    const b = p.locator('button.header-button[aria-label^="Save" i]').first();
    if (await b.count() === 0) throw new Error('no save button found on mobile');
    await b.click();
  };

  await p.goto(B + '/pages/dnd/5e/magic-item-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForSelector('select.builder-option');
  await p.locator('input.input.h-40').first().fill('Retired Blade');
  await p.locator('select.builder-option').first().selectOption('weapon');
  await p.waitForTimeout(500);
  await p.locator('input.input[type="number"]').first().fill('2');
  await p.waitForTimeout(400);
  await (await kindSelect()).selectOption('mundane');
  await p.waitForTimeout(900);
  await p.screenshot({ path: `${D}/m1-builder.png`, fullPage: true });
  await save();
  await p.waitForTimeout(3000);

  await p.goto(B + '/pages/dnd/5e/magic-items', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(3500);
  // The list is long and alphabetical; filter to the item under test.
  const search = p.locator('input:visible').first();
  try { await search.fill('Retired', { timeout: 8000 }); await p.waitForTimeout(1500); }
  catch (e) { console.log('no usable search box:', e.message.split('\n')[0]); }
  const y = await p.evaluate(() => {
    const el = [...document.querySelectorAll('*')]
      .filter(e => e.children.length === 0 && /Retired Blade/.test(e.textContent))[0];
    if (!el) return null;
    el.scrollIntoView({ block: 'center' });
    return true;
  });
  await p.waitForTimeout(600);
  await p.screenshot({ path: `${D}/m2-item-list.png` });
  console.log('list row found:', y === true);

  // The busiest header in the app: five buttons on a 390px screen.
  await p.goto(B + '/pages/dnd/5e/character-builder', { waitUntil: 'domcontentloaded' });
  await p.waitForTimeout(4500);
  await p.screenshot({ path: `${D}/m3-busiest-header.png`,
    clip: { x: 0, y: 0, width: 390, height: 260 } });
  console.log('viewport:', JSON.stringify(p.viewportSize()));
  await br.close();
})();
