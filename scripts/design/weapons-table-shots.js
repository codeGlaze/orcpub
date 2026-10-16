// Weapons table mockups in the real app (spec: docs/kb/weapons-table-spec.md). Rewrites the
// Combat tab weapons table in the page with the app's own classes, measures it, screenshots it.
// Run from a code worktree: SHOTS=<out dir> ./scripts/e2e/run.sh <path to this file>
const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');
const ex = require(path.join(process.cwd(), 'scripts/e2e/export-character-pdf.js'));
const { settle, clickVisible, openBuilderTab, setClasses, BASE } = ex;

const OUT = process.env.SHOTS;
const f = n => path.join(OUT, `${n}.png`);
let failures = 0;
const check = (ok, m) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${m}`); if (!ok) failures++; };

// The mock's style, scoped under .mock-weapons: body and .hdr rules dropped, .root becomes the
// wrapper itself with its width and padding removed.
function scopedCss(html) {
  const css = html.match(/<style>([\s\S]*?)<\/style>/)[1];
  return css.split('}').map(r => r.trim()).filter(Boolean).map(r => {
    const [sel, body] = r.split('{');
    const s = sel.trim();
    if (s === 'body' || s.startsWith('.hdr')) return '';
    if (s === '.root') {
      const decls = body.split(';').filter(d => d && !/^\s*(width|padding)\s*:/.test(d));
      return `.mock-weapons{${decls.join(';')};width:100%;padding:0 0 16px}`;
    }
    return `.mock-weapons ${s}{${body}}`;
  }).join('\n');
}

async function combatTab(page) {
  for (let i = 0; i < 40; i++) {
    const ok = await page.evaluate(() => {
      const t = [...document.querySelectorAll('div.b-b-2.pointer')]
        .find(d => d.offsetParent && d.innerHTML.includes('sword-clash'));
      if (t) t.click();
      return !!t;
    });
    if (ok) {
      await settle(page);
      const seen = await page.evaluate(() => [...document.querySelectorAll('span.f-w-b.f-s-18')]
        .some(e => e.offsetParent && e.textContent.trim() === 'Weapons'));
      if (seen) return true;
    }
    await page.waitForTimeout(250);
  }
  return false;
}



// Rewrites the app's weapons table in place into the iterated layout. variant: 'S' or 'T'; on: bool.
function rebuild([variant, on, outline]) {
  const tbody = [...document.querySelectorAll('tbody.weapons')].find(t => t.offsetParent);
  const table = tbody.closest('table');
  const section = table.closest('div.m-t-30');
  section.setAttribute('data-shot', 'weapons');
  table.style.tableLayout = 'fixed';
  table.style.width = '100%';
  const dmgW = variant === 'S' ? 120 : 100;
  const cg = document.createElement('colgroup');
  for (const w of [null, 56, dmgW, 24]) { const c = document.createElement('col'); if (w) c.style.width = w + 'px'; cg.appendChild(c); }
  table.insertBefore(cg, tbody);
  // header: the app's th classes (th.p-10 for text, th.t-a-c for the roll columns)
  const hdr = tbody.querySelector('tr.f-w-b');
  hdr.innerHTML = '';
  [['p-10', 'Weapon'], ['t-a-c', 'To hit'], ['t-a-c', 'Damage'], ['p-10', '']].forEach(([c, t]) => {
    const th = document.createElement('th'); th.className = c; th.textContent = t; hdr.appendChild(th);
  });
  const META = { Net: 'Thrown (5/15) · Special', Longsword: 'Melee · Versatile (1d10)', Longbow: 'Ranged (150/600) · Heavy' };
  const TYPE = { Longsword: 'slashing', Longbow: 'piercing' };
  // a phone renders roll buttons bare (no ctrl/shift tooltip wrapper); fit them to the cell the way
  // the app's own phone rule does for the ability-score buttons
  const bare = b => { b.style.minWidth = '0'; b.style.width = '100%'; b.style.margin = '0'; b.style.minHeight = '44px'; b.style.boxSizing = 'border-box'; b.style.lineHeight = '1.15'; return b; };
  for (const tr of tbody.querySelectorAll('tr.weapon')) {
    const tds = [...tr.children];
    const name = tds[0].textContent.trim();
    const btns = [...tr.querySelectorAll('button.roll-button')];
    const atk = btns.find(b => b.textContent.trim().startsWith('1d20'));
    const dmg = btns.find(b => b !== atk && /^\d+d\d/.test(b.textContent.trim()));
    const caret = tds[tds.length - 1];
    // weapon cell
    tds[0].innerHTML = '';
    tds[0].appendChild(document.createTextNode(name));
    const meta = document.createElement('div');
    meta.setAttribute('style', 'color:rgba(255,255,255,0.75); font-size:13px; font-weight:400');
    meta.setAttribute('data-meta', '1');
    meta.textContent = (variant === 'S' && name === 'Longsword') ? 'Melee · Versatile' : META[name];
    tds[0].appendChild(meta);
    // to-hit cell
    const tdAtk = document.createElement('td'); tdAtk.className = 't-a-c';
    atk.textContent = '+4'; tdAtk.appendChild(bare(atk));
    // damage cell
    const tdDmg = document.createElement('td'); tdDmg.className = 't-a-c';
    if (!dmg) {
      const chip = document.createElement('div'); chip.className = 'chip';
      chip.setAttribute('style', 'justify-content:center; width:100%; box-sizing:border-box; min-height:44px; border-radius:2px; font-size:13px');
      chip.textContent = 'Restrains'; tdDmg.appendChild(chip);
    } else {
      const versOn = name === 'Longsword' && on;
      dmg.textContent = versOn ? '1d10+2' : '1d8+2';
      const ty = document.createElement('div'); ty.setAttribute('style', 'font-size:11px; font-weight:600; opacity:.92; margin-top:1px');
      ty.textContent = TYPE[name]; dmg.appendChild(ty);
      bare(dmg);
      if (name === 'Longsword' && variant === 'S') {
        const wrap = document.createElement('div');
        wrap.setAttribute('data-split', '1');
        wrap.style.display = 'flex'; wrap.style.width = '100%'; wrap.style.margin = '0';
        dmg.style.borderRadius = '2px 0 0 2px'; dmg.style.flex = '1'; dmg.style.width = 'auto'; dmg.style.margin = '0';
        const h = document.createElement('button'); h.className = dmg.className; h.textContent = '2H';
        h.setAttribute('aria-pressed', on ? 'true' : 'false');
        h.setAttribute('style', 'border-radius:0 2px 2px 0; border-left:1px solid rgba(0,0,0,.25); padding:0; width:32px; flex:0 0 32px; min-width:0; min-height:44px; margin:0; font-size:14px');
        if (on) h.style.boxShadow = 'inset 0 2px 4px rgba(0,0,0,.45)';
        wrap.appendChild(dmg); wrap.appendChild(h); tdDmg.appendChild(wrap);
      } else tdDmg.appendChild(dmg);
      if (name === 'Longsword' && variant === 'T') {
        meta.textContent = 'Melee · '; meta.style.display = 'flex'; meta.style.alignItems = 'center'; meta.style.gap = '4px';
        const chip = document.createElement('button'); chip.className = 'chip';
        chip.setAttribute('aria-pressed', on ? 'true' : 'false');
        chip.setAttribute('aria-label', 'Use two hands');
        // off: outlined in the app's amber; on: flipped to the roll-button's own fill
        chip.setAttribute('style', on
          ? 'color:#fff; cursor:pointer; border:1px solid #dbab50; background-image:linear-gradient(to bottom,#f1a20f,#dbab50); text-shadow:0 1px 1px rgba(0,0,0,0.35),0 0 1px rgba(0,0,0,0.25); font-size:12px; padding:3px 9px; gap:5px'
          : 'color:#f0a100; cursor:pointer; border:1px solid #f0a100; background:transparent; font-size:12px; padding:3px 9px; gap:5px' + (outline ? '; box-shadow:0 0 6px rgba(240,161,0,0.55)' : ''));
        
        const ic = document.createElement('i'); ic.className = 'fa fa-exchange-alt'; ic.style.fontSize = '11px'; ic.setAttribute('aria-hidden', 'true');
        chip.appendChild(ic); chip.appendChild(document.createTextNode('Versatile (1d10)')); meta.appendChild(chip);
      }
    }
    // drop everything between the weapon cell and the caret (details, old roll cells, the stacked v button)
    tds.slice(1, -1).forEach(td => td.remove());
    tr.insertBefore(tdAtk, caret); tr.insertBefore(tdDmg, caret);
  }
  table.querySelectorAll('th, td').forEach(c => { c.style.verticalAlign = 'middle'; });
  // one spacing scale: every cell 8px top and bottom; the weapon column 10px from the edge,
  // the button columns 4px a side so no button touches its neighbour or the column edge
  table.querySelectorAll('tr').forEach(r => [...r.children].forEach((c, i) => {
    c.style.paddingTop = '8px'; c.style.paddingBottom = '8px';
    c.style.paddingLeft = i === 0 ? '10px' : '4px'; c.style.paddingRight = i === 0 ? '6px' : '4px';
  }));
  table.querySelectorAll('[data-meta]').forEach(m => { m.style.marginTop = '2px'; m.style.whiteSpace = 'nowrap'; });
  return true;
}

function measure() {
  const sec = document.querySelector('[data-shot=weapons]');
  const table = sec.querySelector('table');
  const cont = table.parentElement;
  const cx = el => { const b = el.getBoundingClientRect(); return b.left + b.width / 2; };
  const ths = [...table.querySelectorAll('tr.f-w-b th')];
  const ls = [...table.querySelectorAll('tr.weapon')].find(r => r.children[0].firstChild.textContent === 'Longsword');
  const [atk, dmg] = [...ls.querySelectorAll('button.roll-button')];
  const split = ls.querySelector('[data-split]');
  const overflowing = [...table.querySelectorAll('td *, th *')].filter(e => {
    const td = e.closest('td,th').getBoundingClientRect(), b = e.getBoundingClientRect();
    return b.width && (b.right > td.right + 0.5 || b.left < td.left - 0.5);
  }).map(e => `${e.tagName}.${e.className}:${e.textContent.trim().slice(0, 12)}`);
  const r1 = x => Math.round(x * 10) / 10;
  const problems = [];
  for (const r of [...table.querySelectorAll('tr.weapon')]) {
    const nm = r.children[0].firstChild.textContent;
    const [, a, d] = r.children;
    if (r.children.length !== 4) problems.push(`${nm}: ${r.children.length} cells`);
    const ab = a && a.querySelector('button.roll-button');
    if (!ab || ab.firstChild.textContent.trim() !== '+4') problems.push(`${nm}: To hit cell is "${a ? a.textContent.trim() : ''}"`);
    if (nm === 'Net') {
      if (!(d && d.querySelector('div.chip') && d.textContent.trim() === 'Restrains' && !d.querySelector('button')))
        problems.push(`Net: Damage cell is "${d ? d.textContent.trim() : ''}"`);
    } else {
      const db = d && d.querySelector('button.roll-button');
      const t = db ? db.firstChild.textContent.trim() : '';
      if (!(t === '1d8+2' || (nm === 'Longsword' && t === '1d10+2'))) problems.push(`${nm}: Damage button is "${t}"`);
    }
  }
  const cy = el => { const b = el.getBoundingClientRect(); return b.top + b.height / 2; };
  const align = Object.fromEntries([...table.querySelectorAll('tr.weapon')].map(r => {
    const rg = document.createRange(); rg.selectNodeContents(r.children[0]);
    const nb = rg.getBoundingClientRect();
    const hit = r.children[1].querySelector('button.roll-button');
    const d = r.children[2];
    const dmgEl = d.querySelector('[data-split]') || d.querySelector('button.roll-button') || d.querySelector('.chip');
    return [r.children[0].firstChild.textContent, {
      nameVsHit: Math.round(Math.abs((nb.top + nb.height / 2) - cy(hit)) * 10) / 10,
      hitVsDmg: Math.round(Math.abs(cy(hit) - cy(dmgEl)) * 10) / 10 }];
  }));
  const misaligned = Object.entries(align).filter(([, v]) => v.nameVsHit > 2 || v.hitVsDmg > 2).map(([k]) => k);
  const metaLines = Object.fromEntries([...table.querySelectorAll('[data-meta]')].map(m => {
    // lines = distinct vertical bands among the line boxes of its contents
    const rg = document.createRange(); rg.selectNodeContents(m);
    const tops = [];
    for (const r of rg.getClientRects()) if (r.width && !tops.some(t => Math.abs(t - r.bottom) < 6)) tops.push(r.bottom);
    return [m.closest('tr').children[0].firstChild.textContent, tops.length];
  }));
  const btnH = Object.fromEntries([...table.querySelectorAll('tr.weapon')].map(r => {
    const hs = [...r.querySelectorAll('button.roll-button, td .chip')].filter(e => !e.closest('[data-meta]')).map(e => Math.round(e.getBoundingClientRect().height));
    return [r.children[0].firstChild.textContent, hs];
  }));
  const edgeTouch = [...table.querySelectorAll('tr.weapon td:not(:first-child)')].filter(td => {
    const b = td.getBoundingClientRect(); return [...td.querySelectorAll('button, .chip')].some(e => {
      const r = e.getBoundingClientRect(); return r.left - b.left < 3.5 || b.right - r.right < 3.5; });
  }).length;
  return {
    btnH, edgeTouch, problems, metaLines, align, misaligned,
    tableW: r1(table.getBoundingClientRect().width), tableScrollW: table.scrollWidth, containerW: cont.clientWidth,
    toHitOff: r1(Math.abs(cx(ths[1]) - cx(atk))),
    dmgOff: r1(Math.abs(cx(ths[2]) - cx(split || dmg))),
    dmgButtonOnlyOff: split ? r1(Math.abs(cx(ths[2]) - cx(dmg))) : undefined,
    rows: Object.fromEntries([...table.querySelectorAll('tr')].map((r, i) =>
      [i === 0 ? 'header' : r.children[0].firstChild.textContent, r1(r.getBoundingClientRect().height)])),
    overflowing,
  };
}

async function fresh(page) {
  await page.reload({ waitUntil: 'networkidle' });
  await settle(page);
  await clickVisible(page, '.builder-tab:has-text("Details")');
  await settle(page);
  check(await combatTab(page), 'Combat tab (390)');
}

async function variant(page, name, v, on, outline) {
  await fresh(page);
  await page.evaluate(rebuild, [v, on, !!outline]);
  await settle(page, 200, 2000);
  await page.locator('[data-shot=weapons]').scrollIntoViewIfNeeded();
  await page.mouse.move(0, 0);
  await page.waitForTimeout(300);
  const m = await page.evaluate(measure);
  console.log(`  ${name}: ${JSON.stringify(m)}`);
  check(m.problems.length === 0, `${name}: cells hold what they should${m.problems.length ? ' -- ' + m.problems.join('; ') : ''}`);
  if (m.problems.length) throw new Error(`${name}: wrong cell contents`);
  check(m.misaligned.length === 0, `${name}: rows centred within 2px${m.misaligned.length ? ' -- off: ' + m.misaligned.join(', ') : ''}`);
  check(Object.values(m.metaLines).every(n => n === 1), `${name}: every grey line on one line`);
  check(Object.values(m.btnH).every(hs => new Set(hs).size === 1), `${name}: buttons in a row share one height`);
  check(m.edgeTouch === 0, `${name}: no button touches its cell edge`);
  check(m.tableW <= m.containerW && m.tableScrollW <= m.containerW, `${name}: table fits its container`);
  await page.locator('[data-shot=weapons]').screenshot({ path: f(name) });
}

(async () => {
  const browser = await chromium.launch({ executablePath: process.env.E2E_CHROMIUM });
  const desk = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 1 });
  const page = await desk.newPage();
  page.on('pageerror', e => console.log('pageerror:', e.message));
  try {
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
    await settle(page);
    await clickVisible(page, 'text=Got it!');
    await setClasses(page, [['Fighter', '1']], check);
    check(await openBuilderTab(page, 'Equipment'), 'Equipment step');
    await settle(page);
    for (const w of ['Net', 'Longsword', 'Longbow']) {
      const input = page.locator('.inv-combo-input').first();
      await input.click();
      await input.fill(w);
      await page.waitForTimeout(300);
      const row = page.locator('.inv-combo-row').filter({ hasText: new RegExp(`^${w}$`) });
      check(await row.count() >= 1, `${w} offered`);
      await row.first().click();
      await settle(page);
    }
    check(await combatTab(page), 'Combat tab (1280)');
    const main = await page.evaluate(() => {
      const lbl = [...document.querySelectorAll('div.f-w-b.m-b-5')].find(d => d.textContent.trim() === 'Main Hand Weapon' && d.offsetParent);
      if (!lbl) return false;
      lbl.parentElement.querySelector('select').setAttribute('data-shot', 'main'); return true;
    });
    check(main, 'Main Hand Weapon dropdown');
    if (main) await page.locator('select[data-shot=main]').selectOption({ label: 'Net' });
    await settle(page);
    const state = await desk.storageState();
    const phone = await browser.newContext({ viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, storageState: state });
    const p = await phone.newPage();
    p.on('pageerror', e => console.log('pageerror:', e.message));
    await p.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
    await settle(p);
    await variant(p, 'F-off', 'T', false, false);
    await variant(p, 'F-glow', 'T', false, true);
    await variant(p, 'F-on', 'T', true, false);
    check(true, 'all shots taken');
  } catch (e) {
    check(false, `threw: ${e.message.split('\n')[0]}`);
  }
  await browser.close();
  process.exit(failures ? 1 : 0);
})();
