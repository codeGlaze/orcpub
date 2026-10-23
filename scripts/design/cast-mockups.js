// Needs: dev bundle
// Mockups: where spells cast from items show (on the item only, or on the Spells tab). Not a test.
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
  if (t) t.click(); return !!t; }, icon);
const shot = async (page, file) => { await page.waitForTimeout(400); await page.screenshot({ path: path.join(OUT, file) }); console.log(`  shot ${file}`); };

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
      const lv = n => Array.from({ length: n }, (_, i) => `{:${s}/key :level-${i + 1}}`).join(' ');
      const strict = `{:${s}/selections [
        {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
          :${s}/map-value {:${c}/str 10 :${c}/dex 12 :${c}/con 14 :${c}/int 10 :${c}/wis 16 :${c}/cha 8}}}
        {:${s}/key :class :${s}/options [{:${s}/key :cleric :${s}/selections [{:${s}/key :levels :${s}/options [${lv(5)}]}]}]}
        {:${s}/key :other-magic-items :${s}/options [${opt(':wand-of-fireballs', 1)} ${opt(':wand-of-magic-missiles', 1)}]}]}`;
      re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
    });
    await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
    await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')].some(d => d.offsetParent && d.innerHTML.includes('spell-book')), null, { timeout: 20000 }).catch(() => {});
    await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
    await page.addStyleTag({ content: `
      .cx{display:flex;align-items:center;gap:6px;margin-top:4px;font-size:12px;font-weight:400;flex-wrap:wrap}
      .cx-pips{display:flex;gap:3px} .cx-pip{width:11px;height:11px;border-radius:50%;border:1.5px solid #f0a100;background:#f0a100} .cx-pip.used{background:none;opacity:.7}
      .cx-cast{position:relative;height:24px;padding:0 9px;border-radius:5px;border:0;font-size:11.5px;font-weight:700;text-transform:uppercase;color:#fff;
        text-shadow:0 1px 1px rgba(0,0,0,.35);background:linear-gradient(to bottom,#f1a20f,#dbab50)}
      .cx-cast::after{content:"";position:absolute;inset:-10px -2px}
      .cx-pick{display:flex;align-items:center;justify-content:flex-end;gap:6px;margin:6px 0 2px;font-size:13px}
      .cx-sel{height:28px;border-radius:5px;border:1px solid rgba(230,233,238,.4);background:rgba(255,255,255,.06);color:inherit;padding:0 6px;font-size:13px}
      .cx-tag{display:inline-block;margin-left:5px;padding:0 5px;border-radius:3px;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:.04em;
        background:rgba(240,161,0,.16);color:#f0a100;vertical-align:middle}
      .cx-sec{margin:6px 0 24px}
      .cx-sec table td,.cx-sec table th{padding:6px 10px;text-align:left}
    `});
    await page.evaluate(() => { for (const el of document.querySelectorAll('body *')) {
      const p = getComputedStyle(el).position; if (p === 'sticky' || p === 'fixed') el.style.visibility = 'hidden'; } });

    // the spells tab as it is
    check(await tabIcon(page, 'spell-book'), 'spells tab');
    const spells = await page.locator('tbody.spells').first().waitFor({ timeout: 20000 }).then(() => true, () => false);
    check(spells, 'spell tables show');
    await page.evaluate(() => [...document.querySelectorAll('span.f-w-b.f-s-16')].find(e => e.textContent === 'Spells By Level').scrollIntoView({ block: 'start' }));
    await page.evaluate(() => window.scrollBy(0, -80));
    await shot(page, 'X0-spells-today.png');

    // B1: item spells inline in the level tables, Source = the item
    check(await page.evaluate(() => {
      const tables = [...document.querySelectorAll('div.m-t-10.m-b-30')];
      const byLevel = l => tables.find(d => d.querySelector('span.f-w-b.i')?.textContent === l);
      const addRow = (lvlName, name, src, extra) => {
        const t = byLevel(lvlName); if (!t) return false;
        const tb = t.querySelector('tbody.spells');
        const tr = document.createElement('tr'); tr.className = 'spell pointer mk-inline';
        tr.innerHTML = `<td class="p-l-10 p-b-5 p-t-5 f-w-b">${name}</td><td class="p-l-10 p-b-5 p-t-5">${src}</td><td class="p-l-10 p-b-5 p-t-5">—</td><td class="p-l-10 p-b-5 p-t-5">${extra}</td><td>—</td><td class="p-l-10 p-b-5 p-t-5">—</td><td></td>`;
        tb.querySelector('tr.f-w-b').after(tr);
        return true; };
      return addRow('1st Level', 'Magic Missile<span class="cx-tag">wand</span>', 'Wand', '—') &&
             addRow('3rd Level', 'Fireball<span class="cx-tag">wand</span>', 'Wand', '15');
    }), 'B1 rows drawn');
    await page.evaluate(() => document.querySelector('.mk-inline').closest('div.m-t-10').scrollIntoView({ block: 'start' }));
    await page.evaluate(() => window.scrollBy(0, -10));
    await shot(page, 'X2-spells-inline.png');
    await page.evaluate(() => document.querySelectorAll('.mk-inline').forEach(e => e.remove()));

    // B2: a "From items" table above Spells By Level
    check(await page.evaluate(() => {
      const head = [...document.querySelectorAll('span.f-w-b.f-s-16')].find(e => e.textContent === 'Spells By Level').closest('div.flex');
      const d = document.createElement('div'); d.className = 'cx-sec f-s-14 f-w-n'; d.setAttribute('data-x', 'items');
      const row = (name, item, lvl, dc, max, used) => `<tr><td class="f-w-b">${name}<div class="cx"><span class="cx-pips">${Array.from({ length: max }, (_, i) => `<span class="cx-pip${i >= max - used ? ' used' : ''}"></span>`).join('')}</span>${max - used}/${max}</div></td><td>${item}</td><td>${lvl}</td><td>${dc}</td><td><button class="cx-cast">Cast</button></td></tr>`;
      d.innerHTML = `<div class="f-w-b f-s-16 m-b-5">From Items</div><table class="w-100-p t-a-l striped"><tbody>
        <tr class="f-w-b f-s-12"><th>Name</th><th>Item</th><th>Lvl</th><th>DC</th><th></th></tr>
        ${row('Fireball', 'Wand of Fireballs', '3+', '15', 7, 2)}${row('Magic Missile', 'Wand of Magic Missiles', '1+', '—', 7, 3)}</tbody></table>`;
      head.before(d); d.scrollIntoView({ block: 'start' }); window.scrollBy(0, -60); return true;
    }), 'B2 section drawn');
    await shot(page, 'X3-spells-from-items.png');
    await page.evaluate(() => document.querySelector('[data-x=items]').remove());

    // A: on the item only (Equipment tab)
    check(await tabIcon(page, 'backpack'), 'equipment tab');
    const ok = await page.locator('tbody.other-magic-items tr', { hasText: 'Wand of Fireballs' }).first().waitFor({ timeout: 20000 }).then(() => true, () => false);
    check(ok, 'wands listed');
    check(await page.evaluate(() => {
      const cell = n => [...document.querySelectorAll('tbody.other-magic-items tr')].find(r => r.textContent.includes(n)).querySelector('td');
      const add = (n, spell, max, used, picker) => { const c = cell(n);
        c.insertAdjacentHTML('beforeend', `<div class="cx"><span class="cx-pips">${Array.from({ length: max }, (_, i) => `<span class="cx-pip${i >= max - used ? ' used' : ''}"></span>`).join('')}</span>${max - used}/${max}<button class="cx-cast">Cast ${spell}</button></div>` +
          (picker ? `<div class="cx-pick">Cast at level <select class="cx-sel"><option>3rd · 1 charge</option></select> <button class="cx-cast" style="height:28px">Cast</button></div><div class="cx" style="justify-content:flex-end;opacity:.7">DC 15 · up to 7th with 5 charges</div>` : '')); };
      add('Fireballs', 'Fireball', 7, 2, true); add('Magic Missiles', 'Magic Missile', 7, 3, false);
      document.querySelector('tbody.other-magic-items').scrollIntoView({ block: 'center' }); return true;
    }), 'A drawn');
    await shot(page, 'X1-on-the-item.png');
  } catch (e) { console.log('error:', e.message); failures++; }
  await browser.close();
  console.log(failures ? `MOCKUPS: ${failures} failed` : 'MOCKUPS: done');
  process.exit(failures ? 1 : 0);
})();
