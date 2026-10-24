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
    const drawA = (mode) => page.evaluate(mode => {
      document.querySelectorAll('.cx, .cx-row, .cx-sheet, .cx-dim').forEach(e => e.remove());
      const row = n => [...document.querySelectorAll('tbody.other-magic-items tr')].find(r => r.textContent.includes(n));
      const pips = (max, used) => `<span class="cx-pips">${Array.from({ length: max }, (_, i) => `<span class="cx-pip${i >= max - used ? ' used' : ''}"></span>`).join('')}</span>${max - used}/${max}`;
      const add = (n, spell, max, used) => row(n).querySelector('td').insertAdjacentHTML('beforeend', `<div class="cx">${pips(max, used)}<button class="cx-cast">Cast ${spell}</button></div>`);
      add('Fireballs', 'Fireball', 7, 2); add('Magic Missiles', 'Magic Missile', 7, 3);
      const levels = [['3rd', 1], ['4th', 2], ['5th', 3], ['6th', 4], ['7th', 5], ['8th', 6], ['9th', 7]];
      const chips = levels.map(([l, c], i) => `<button class="cx-lv${i === 0 ? ' on' : ''}${c > 5 ? ' off' : ''}"><b>${l}</b><span>${c} ch</span></button>`).join('');
      const body = `<div class="cx-head"><b>Fireball</b><span>Wand of Fireballs · DC 15 · 5 charges left</span></div>
        <div class="cx-lvs">${chips}</div>
        <div class="cx-foot"><span class="cx-note">8d6 fire, +1d6 per level above 3rd</span><button class="cx-cast cx-go">Cast at 3rd · 1 charge</button></div>`;
      if (mode === 'row') {
        const r = row('Fireballs'); const tr = document.createElement('tr'); tr.className = 'cx-row';
        tr.innerHTML = `<td colspan="3"><div class="cx-panel">${body}</div></td>`; r.after(tr);
        r.scrollIntoView({ block: 'start' }); window.scrollBy(0, -60);
      } else {
        const d = document.createElement('div'); d.className = 'cx-dim'; document.body.appendChild(d);
        const s = document.createElement('div'); s.className = 'cx-sheet'; s.innerHTML = `<div class="cx-handle"></div><span class="cx-x">✕</span>${body}`;
        document.body.appendChild(s); document.querySelector('tbody.other-magic-items').scrollIntoView({ block: 'start' });
      }
      return true;
    }, mode);
    await page.addStyleTag({ content: `
      .cx-row td{padding:0 0 10px!important}
      .cx-panel,.cx-sheet{box-sizing:border-box;padding:10px 12px;font-size:13px}
      .cx-panel{margin:0 10px;border-radius:6px;background:rgba(0,0,0,.22);border:1px solid rgba(255,255,255,.14)}
      .cx-sheet{position:fixed;left:0;right:0;bottom:0;z-index:10001;background:#2c3445;border-radius:14px 14px 0 0;padding:10px 16px 22px;color:#e6e9ee;box-shadow:0 -6px 24px rgba(0,0,0,.5)}
      .cx-dim{position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:10000}
      .cx-handle{width:40px;height:4px;border-radius:2px;background:rgba(255,255,255,.35);margin:0 auto 10px}
      .cx-x{position:absolute;right:14px;top:12px;font-size:18px;opacity:.8}
      .cx-head{display:flex;flex-direction:column;gap:1px} .cx-head b{font-size:15px} .cx-head span{opacity:.7;font-size:12px}
      .cx-lvs{display:grid;grid-template-columns:repeat(7,1fr);gap:4px;margin:10px 0}
      .cx-lv{position:relative;height:38px;border-radius:5px;border:1px solid rgba(230,233,238,.3);background:none;color:inherit;display:flex;flex-direction:column;align-items:center;justify-content:center;font-size:11px;line-height:1.15}
      .cx-lv b{font-size:12.5px} .cx-lv span{opacity:.7;font-size:10px}
      .cx-lv.on{background:rgba(240,161,0,.16);border-color:#f0a100;color:#f0a100} .cx-lv.off{opacity:.35}
      .cx-foot{display:flex;align-items:center;gap:8px} .cx-note{opacity:.7;font-size:12px;min-width:0}
      .cx-go{margin-left:auto;height:32px!important;flex:none}
    `});
    check(await tabIcon(page, 'backpack'), 'equipment tab');
    const ok = await page.locator('tbody.other-magic-items tr', { hasText: 'Wand of Fireballs' }).first().waitFor({ timeout: 20000 }).then(() => true, () => false);
    check(ok, 'wands listed');
    check(await drawA('row'), 'A1 drawn'); await shot(page, 'X1a-cast-row.png');
    check(await drawA('sheet'), 'A2 drawn'); await shot(page, 'X1b-cast-sheet.png');
  } catch (e) { console.log('error:', e.message); failures++; }
  await browser.close();
  console.log(failures ? `MOCKUPS: ${failures} failed` : 'MOCKUPS: done');
  process.exit(failures ? 1 : 0);
})();
