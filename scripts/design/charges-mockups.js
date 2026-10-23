// Needs: dev bundle
// Mockups of item charges on the real sheet (Other Magic Items) and the dawn regain line. Not a test.
//   NODE_PATH=$PWD/scripts/e2e/node_modules SHOTS=<dir> E2E_PORT=8897 ./scripts/e2e/run.sh <this file>
const { chromium } = require('playwright');
const path = require('path');
const { findChrome } = require(path.join(process.cwd(), 'test/browser/lib/find-chrome'));
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.SHOTS;
let failures = 0;
const check = (ok, m) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${m}`); if (!ok) failures++; };
const PHONE = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
  userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' };

const tabIcon = (page, icon) => page.evaluate(icon => {
  const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes(icon));
  if (t) t.click(); return !!t;
}, icon);

const shot = async (page, file) => { await page.waitForTimeout(350); await page.screenshot({ path: path.join(OUT, file) }); console.log(`  shot ${file}`); };

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext(PHONE);
  await ctx.addInitScript(() => { try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {} });
  const page = await ctx.newPage();
  page.on('pageerror', e => console.log('pageerror:', e.message));
  try {
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
    await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
    await page.evaluate(() => {
      const rd = cljs.reader.read_string, s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
      const held = q => `{:${c}.equipment/quantity ${q} :${c}.equipment/equipped? true}`;
      const opt = (k, q) => `{:${s}/key ${k} :${s}/map-value ${held(q)}}`;
      const strict = `{:${s}/selections [
        {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
          :${s}/map-value {:${c}/str 10 :${c}/dex 14 :${c}/con 13 :${c}/int 16 :${c}/wis 12 :${c}/cha 8}}}
        {:${s}/key :class :${s}/options [{:${s}/key :fighter :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
        {:${s}/key :other-magic-items :${s}/options [${opt(':wand-of-magic-missiles', 1)} ${opt(':wand-of-fireballs', 2)} ${opt(':cube-of-force', 1)}]}]}`;
      re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
    });
    await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
    await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')].some(d => d.offsetParent && d.innerHTML.includes('backpack')), null, { timeout: 20000 }).catch(() => {});
    check(await tabIcon(page, 'backpack'), 'equipment tab');
    const ok = await page.locator('tbody.other-magic-items tr', { hasText: 'Wand of Fireballs' }).first().waitFor({ timeout: 20000 }).then(() => true, () => false);
    check(ok, 'the magic items table shows the wands');
    await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
    await page.addStyleTag({ content: `
      .ck{display:flex;align-items:center;gap:6px;margin-top:4px;font-size:12px;font-weight:400}
      .ck-n{min-width:14px;opacity:.6;font-size:11px}
      .ck-pips{display:flex;gap:3px}
      .ck-pip{position:relative;width:11px;height:11px;border-radius:50%;border:1.5px solid #f0a100;background:#f0a100}
      .ck-pip.used{background:none;opacity:.7}
      .ck-count{opacity:.75;font-variant-numeric:tabular-nums}
      .ck-step{position:relative;width:24px;height:22px;border-radius:4px;border:1px solid rgba(230,233,238,.35);background:none;color:inherit;font-weight:700;line-height:1}
      .ck-step::after,.ck-pip::after{content:"";position:absolute;inset:-11px -4px}
      .ck-big{font-size:13px;font-weight:700;font-variant-numeric:tabular-nums}
      .ck-x{opacity:.6;font-size:11px;margin-left:4px}
      .ck-dawn{margin:8px 0 0;padding:8px 10px;border-radius:6px;background:rgba(255,255,255,.05);font-size:13px;line-height:1.5}
      .ck-dawn-row{display:flex;align-items:center;gap:6px;flex-wrap:wrap;margin-top:4px}
      .ck-in{width:38px;height:24px;border-radius:4px;border:1px solid #f0a100;background:rgba(240,161,0,.12);color:inherit;text-align:center;font-weight:700}
      .ck-dice{opacity:.6;font-size:12px}
      .ck-link{color:#f0a100;font-weight:700;text-decoration:underline;margin-left:auto}
    `});
    await page.evaluate(() => { for (const el of document.querySelectorAll('body *')) {
      const p = getComputedStyle(el).position; if (p === 'sticky' || p === 'fixed') el.style.visibility = 'hidden'; } });
    console.log('  rows:', JSON.stringify(await page.evaluate(() => [...document.querySelectorAll('tbody.other-magic-items tr')].map(r => r.textContent.slice(0, 40)))));
    const centre = () => page.evaluate(() => document.querySelector('tbody.other-magic-items').scrollIntoView({ block: 'center' }));
    await centre();
    await shot(page, 'C0-today.png');
    const draw = (mode) => page.evaluate(mode => {
      const row = n => [...document.querySelectorAll('tbody.other-magic-items tr')].find(r => r.textContent.includes(n));
      const cell = n => row(n).querySelector('td');
      document.querySelectorAll('.ck, .ck-xn').forEach(e => e.remove());
      const pips = (max, used) => `<span class="ck-pips">${Array.from({ length: max }, (_, i) => `<span class="ck-pip${i >= max - used ? ' used' : ''}"></span>`).join('')}</span><span class="ck-count">${max - used}/${max}</span>`;
      const line = (html, n) => { const d = document.createElement('div'); d.className = 'ck'; d.innerHTML = (n ? `<span class="ck-n">${n}</span>` : '') + html; return d; };
      cell('Magic Missiles').appendChild(line(pips(7, 3)));
      const fb = cell('Fireballs');
      const x = document.createElement('span'); x.className = 'ck-xn ck-x'; x.textContent = '×2'; fb.firstChild && fb.firstElementChild ? fb.firstElementChild.appendChild(x) : fb.appendChild(x);
      if (mode === 'each') { fb.appendChild(line(pips(7, 2), '1')); fb.appendChild(line(pips(7, 7), '2')); }
      else { fb.appendChild(line(`<span class="ck-count ck-big">5/14</span><button class="ck-step">−</button><button class="ck-step">+</button>`)); }
      cell('Cube of Force').appendChild(line(`<span class="ck-big">21/36</span><button class="ck-step">−</button><button class="ck-step">+</button>`));
      return true;
    }, mode);
    check(await draw('each'), 'drawn: one pool each');
    await centre(); await shot(page, 'C1-pool-each.png');
    check(await draw('shared'), 'drawn: shared pool');
    await centre(); await shot(page, 'C2-pool-shared.png');
    // the dawn regain line, on the Features tab under today's rest buttons
    check(await tabIcon(page, 'vitruvian-man'), 'features tab');
    const rest = await page.locator('button', { hasText: 'finish long rest' }).first().waitFor({ timeout: 15000 }).then(() => true, () => false);
    check(rest, 'rest buttons');
    check(await page.evaluate(() => {
      const long = [...document.querySelectorAll('button')].find(b => b.offsetParent && b.textContent.trim() === 'finish long rest');
      const d = document.createElement('div'); d.className = 'ck-dawn'; d.setAttribute('data-ck', 'dawn');
      d.innerHTML = `<div><i class="fas fa-sun" style="color:#f0a100"></i> <b>Dawn passed.</b> Charges regained:</div>
        <div class="ck-dawn-row">Wand of Fireballs (1) <input class="ck-in" value="5"> <span class="ck-dice">rolled 1d6+1</span></div>
        <div class="ck-dawn-row">Wand of Fireballs (2) <input class="ck-in" value="7"> <span class="ck-dice">rolled 1d6+1</span></div>
        <div class="ck-dawn-row">Cube of Force <input class="ck-in" value="13"> <span class="ck-dice">rolled 1d20</span></div>
        <div class="ck-dawn-row"><span class="ck-dice">Rolled your own? Type it over the number.</span><span class="ck-link">Undo</span></div>`;
      long.parentElement.after(d); d.scrollIntoView({ block: 'center' }); return true;
    }), 'dawn line drawn');
    await shot(page, 'C3-dawn-regain.png');
  } catch (e) { console.log('error:', e.message); failures++; }
  await browser.close();
  console.log(failures ? `MOCKUPS: ${failures} failed` : 'MOCKUPS: done');
  process.exit(failures ? 1 : 0);
})();
