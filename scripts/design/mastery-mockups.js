// Needs: dev bundle
// Mockups of the Weapon Mastery help: the real Weapons table, with the new parts drawn in using the
// app's own classes. Not a test. Run from the worktree:
//   NODE_PATH=$PWD/scripts/e2e/node_modules SHOTS=<dir> E2E_PORT=8897 ./scripts/e2e/run.sh <path to this file>
// How to turn the shots into a phone review page: docs/kb/mockup-review-pages.md
const { chromium } = require('playwright');
const path = require('path');
const { findChrome } = require(path.join(process.cwd(), 'test/browser/lib/find-chrome'));
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const OUT = process.env.SHOTS;
let failures = 0;
const check = (ok, m) => { console.log(`  ${ok ? 'PASS' : 'FAIL'}  ${m}`); if (!ok) failures++; };

const PHONE = { viewport: { width: 390, height: 844 }, deviceScaleFactor: 2, isMobile: true, hasTouch: true,
  userAgent: 'Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1' };

async function setup(page, phone) {
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => { try { return !!re_frame.core.dispatch_sync; } catch (e) { return false; } }, null, { timeout: 60000 });
  await page.evaluate(() => {
    const rd = cljs.reader.read_string, rf = re_frame.core, m = 'orcpub.dnd.e5.magic-items', w = 'orcpub.dnd.e5.weapons';
    rf.dispatch_sync(rd(`[:${m}/set-custom-items
      [{:db/id 900001 :${m}/name "Ironwood Battleaxe" :${m}/type :weapon :${m}/item-subtype :battleaxe :${w}/mastery :topple}
       {:db/id 900002 :${m}/name "Bronze Glaive" :${m}/type :weapon :${m}/item-subtype :glaive :${w}/mastery :graze}]]`));
    rf.reg_sub(rd(':orcpub.dnd.e5.weapons/mastery-definitions'),
               () => rd(`{:topple {:name "Topple" :trigger :hit :text "Topple."} :graze {:name "Graze" :trigger :miss :text "Graze."}}`));
    rf.reg_sub(rd(':orcpub.dnd.e5.character/weapon-masteries'), () => rd('#{:battleaxe :longsword}'));
    const s = 'orcpub.entity.strict', c = 'orcpub.dnd.e5.character';
    const held = `{:${c}.equipment/quantity 1 :${c}.equipment/equipped? true}`;
    const opt = k => `{:${s}/key ${k} :${s}/map-value ${held}}`;
    const strict = `{:${s}/selections [
      {:${s}/key :ability-scores :${s}/option {:${s}/key :standard-scores
        :${s}/map-value {:${c}/str 16 :${c}/dex 14 :${c}/con 13 :${c}/int 10 :${c}/wis 12 :${c}/cha 8}}}
      {:${s}/key :class :${s}/options [{:${s}/key :fighter
        :${s}/selections [{:${s}/key :levels :${s}/options [{:${s}/key :level-1}]}]}]}
      {:${s}/key :weapons :${s}/options [${opt(':longsword')} ${opt(':handaxe')}]}
      {:${s}/key :magic-weapons :${s}/options [${opt(':ironwood-battleaxe')} ${opt(':bronze-glaive')}]}]}`;
    rf.dispatch_sync(cljs.core.PersistentVector.fromArray([rd(':set-character'), orcpub.dnd.e5.character.from_strict(rd(strict))], true));
  });
  if (phone) {
    await page.locator('.builder-tab', { hasText: 'Details' }).first().click({ timeout: 20000 }).catch(() => {});
    await page.waitForFunction(() => [...document.querySelectorAll('div.b-b-2.pointer')]
      .some(d => d.offsetParent && d.innerHTML.includes('sword-clash')), null, { timeout: 20000 }).catch(() => {});
    await page.evaluate(() => {
      const t = [...document.querySelectorAll('div.b-b-2.pointer')].find(d => d.offsetParent && d.innerHTML.includes('sword-clash'));
      if (t) t.click();
    });
  }
  const tab = name => page.evaluate(name => re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
    [cljs.reader.read_string(':orcpub.dnd.e5.character/set-selected-display-tab'), name], true)), name);
  await tab('summary'); await page.waitForTimeout(400); await tab('combat');
  const ok = await page.locator('.weapon-row .weapon-name', { hasText: 'Ironwood Battleaxe' }).waitFor({ timeout: 20000 }).then(() => true, () => false);
  check(ok, `${phone ? 'phone' : 'desktop'}: the table shows the made-up weapons`);
  await page.locator('.message').first().click({ timeout: 1500 }).catch(() => {});
  // our own mockup styles: only what the app has no class for yet
  await page.addStyleTag({ content: `
    .srd-preview.mk{box-sizing:border-box}
    .weapon-row[data-mk-band]{border-radius:4px;overflow:hidden;margin-bottom:8px}
    .weapon-row[data-mk-band] .weapon-mastery-band{border-radius:0 0 4px 4px}
    .mk-q.mk-glow i{border-radius:50%;box-shadow:0 0 0 4px var(--wpn-glow)}
    .mk-nest b{font-weight:700}
    .mk-q{flex:none;margin-left:auto;width:44px;height:44px;margin:-10px -10px -10px auto;display:flex;align-items:center;justify-content:center;
      background:none;border:0;color:#e6e9ee;cursor:pointer}
    .mk-q i{font-size:17px;opacity:.85}
    .mk-new{flex:none;font-size:10px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;padding:1px 6px;border-radius:8px;
      border:1px solid #f0a100;color:#f0a100}
    .srd-preview .mk-head{display:flex;align-items:center;gap:8px;margin:-12px -14px 10px;min-height:30px;background:var(--wpn-strip,#1a1e28)}
    .srd-preview .mk-head .weapon-mastery-cap{margin:0;align-self:stretch;font-size:12px}
    .srd-preview .mk-head .mk-sub{font-size:12px;opacity:.75}
    .mk-lead{font-size:15px;line-height:21px}
    .mk-lead b{font-weight:700}
    .mk-srd-label{margin-top:12px;font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;opacity:.6}
    .mk-srd{font-size:13px;line-height:18px;opacity:.9;margin-top:4px}
    .srd-link.mk-open{text-decoration:none;border-bottom:2px solid #f0a100}
    .mk-nest{margin:8px 0 4px;padding:8px 10px;border-left:3px solid #f0a100;background:rgba(255,255,255,.07);font-size:13px;line-height:18px}
    .mk-nest .mk-nest-title{font-weight:700;font-size:14px;margin-bottom:4px}
    .mk-nest p{margin:4px 0 0}
    .mk-sheet{left:0!important;right:0;bottom:0!important;top:auto!important;width:auto!important;border-radius:14px 14px 0 0!important;
      max-height:62vh!important;padding-bottom:22px!important}
    .mk-panel{display:flex;flex-direction:column;gap:12px;margin:8px 0 20px}
    .mk-panel-row{display:flex;flex-direction:column;gap:6px;padding:10px 12px;background:rgba(255,255,255,.06);border-radius:4px}
    .mk-panel-label{font-weight:700;font-size:14px}
    .mk-dim-text{font-weight:400;opacity:.65;margin-left:6px}
    .mk-chips{display:flex;flex-wrap:wrap;gap:8px}
    .mk-chip{display:inline-flex;align-items:center;gap:8px;background:var(--wpn-strip,#1a1e28);padding-right:10px;font-size:13px;font-weight:600;min-height:28px}
    .mk-chip .weapon-mastery-cap{margin:0;align-self:stretch;padding:0 14px 0 8px}
    .mk-swap{display:flex;align-items:center;gap:8px}
    .mk-swap select{min-width:0;flex:1;min-height:44px}
    .mk-arrow{font-size:18px;opacity:.7}
    .mk-x{margin-left:auto;width:44px;height:44px;display:flex;align-items:center;justify-content:center;font-size:20px}
    .mk-sheet .mk-handle{width:40px;height:4px;border-radius:2px;background:rgba(255,255,255,.35);margin:0 auto 10px}
    .mk-line{display:flex;gap:6px;align-items:center;flex-wrap:wrap}
    .mk-dim{position:fixed;inset:0;background:rgba(0,0,0,.35);z-index:10000}
  `});
  return ok;
}

// Draws the new band onto a row: the summary in the character's numbers, "New", and the ? helper.
const band = (page, name, isNew, glow) => page.evaluate(([name, isNew, glow]) => {
  const row = [...document.querySelectorAll('.weapon-row')].find(r => r.querySelector('.weapon-name')?.textContent === name);
  const b = row?.querySelector('.weapon-mastery-band');
  if (!b) return false;
  b.querySelector('.weapon-mastery-summary').textContent = 'DC 13 Con save or Prone';
  if (isNew) { const n = document.createElement('span'); n.className = 'mk-new'; n.textContent = 'New';
    b.querySelector('.weapon-mastery-cap').after(n); }
  b.querySelectorAll('.mk-new, .mk-q').forEach(e => e.remove());
  row.setAttribute('data-mk-band', '');
  const q = document.createElement('button'); q.className = 'mk-q' + (glow ? ' mk-glow' : ''); q.setAttribute('aria-label', 'What Topple does');
  q.innerHTML = '<i class="fa fa-question-circle"></i>'; b.appendChild(q);
  row.setAttribute('data-mk', name);
  return true;
}, [name, isNew, glow]);

const PRONE_LINK = '<span class="srd-link mk-prone">Prone</span>';
const PRONE_TEXT = `<div class="mk-nest"><div class="mk-nest-title">Prone <span style="font-weight:400;opacity:.6;font-size:12px">condition</span></div>
  <p>While you have the Prone condition, you experience the following effects.</p>
  <p><span style="font-weight:700">Restricted Movement.</span> Your only movement options are to crawl or to spend an amount of movement equal to half your Speed (round down) to right yourself and thereby end the condition. If your Speed is 0, you can't right yourself.</p>
  <p><span style="font-weight:700">Attacks Affected.</span> You have Disadvantage on attack rolls. An attack roll against you has Advantage if the attacker is within 5 feet of you. Otherwise, that attack roll has Disadvantage.</p></div>`;

// The Topple card: what it means for this character first, then the SRD text. nested: Prone open.
const card = (page, { sheet, nested, anchor }) => page.evaluate(([sheet, nested, PRONE_LINK, PRONE_TEXT, anchor]) => {
  const q = document.querySelector(`[data-mk="${anchor}"] .mk-q`);
  const r = q.getBoundingClientRect();
  if (sheet) { const d = document.createElement('div'); d.className = 'mk-dim'; document.querySelector('.app').appendChild(d); }
  const el = document.createElement('div');
  el.className = 'srd-preview mk' + (sheet ? ' mk-sheet' : '');
  const w = Math.min(440, innerWidth - 32);
  if (!sheet) Object.assign(el.style, { left: Math.max(16, Math.min(r.right - w, innerWidth - w - 16)) + 'px', width: w + 'px',
    top: (r.bottom + 6) + 'px', maxHeight: Math.floor(Math.min(innerHeight * 0.75, innerHeight - r.bottom - 16)) + 'px' });
  const prone = nested ? PRONE_LINK.replace('srd-link', 'srd-link mk-open') : PRONE_LINK;
  el.innerHTML = `${sheet ? '<div class="mk-handle"></div>' : ''}
    <div class="mk-head"><span class="weapon-mastery-cap">Topple</span><span class="mk-sub">Weapon Mastery · Ironwood Battleaxe</span>${sheet ? '<span class="mk-x" aria-label="Close">\u2715</span>' : ''}</div>
    <div class="mk-lead">When you hit with this, you can make the target save: <b>DC 13 Constitution</b>, or it falls ${prone}.</div>
    ${nested ? PRONE_TEXT : ''}
    <div class="mk-lead" style="margin-top:6px;opacity:.85;font-size:14px">After a hit, your roll result offers Topple.</div>
    <div class="mk-srd-label">SRD 5.2 text</div>
    <div class="mk-srd">If you hit a creature with this weapon, you can force the creature to make a Constitution saving throw (DC 8 plus the ability modifier used to make the attack roll and your Proficiency Bonus). On a failed save, the creature has the ${PRONE_LINK} condition.</div>
    ${sheet ? '' : '<div class="srd-preview-footer"><span class="pointer">Close</span></div>'}`;
  document.querySelector('.app').appendChild(el);
  return true;
}, [!!sheet, !!nested, PRONE_LINK, PRONE_TEXT, anchor]);

const centre = (page, name, block) => page.evaluate(([name, block]) => {
  document.querySelector(`[data-mk="${name}"]`).scrollIntoView({ block }); window.scrollBy(0, block === 'start' ? -150 : 0);
}, [name, block]);

const shot = async (page, file) => { await page.waitForTimeout(350); await page.screenshot({ path: path.join(OUT, file) }); console.log(`  shot ${file}`); };
const clean = page => page.evaluate(() => document.querySelectorAll('.srd-preview, .mk-dim').forEach(e => e.remove()));

// No sticky bar over a mockup: the builder header would cover what is shown.
const unstick = page => page.evaluate(() => { for (const el of document.querySelectorAll('body *')) {
  const p = getComputedStyle(el).position;
  if ((p === 'sticky' || p === 'fixed') && !el.closest('.srd-preview, .weapon-sheet')) el.style.visibility = 'hidden'; } });

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const init = () => { try { localStorage.setItem('orcpub:no-cookie-banner', '1'); localStorage.setItem('whats-new-seen', '"summer-patch-2026"'); } catch (e) {} };
  try {
    const ctx = await browser.newContext(PHONE); await ctx.addInitScript(init);
    const page = await ctx.newPage();
    page.on('pageerror', e => console.log('pageerror:', e.message));
    if (await setup(page, true)) {
      check(await band(page, 'Ironwood Battleaxe', true, false), 'the battleaxe has its band');
      await unstick(page);
      // 1a: the band joined to its row, New tag; 1b: no tag, the ? glows until opened
      await centre(page, 'Ironwood Battleaxe', 'start');
      await shot(page, '1a-band-with-new-tag.png');
      await band(page, 'Ironwood Battleaxe', false, true);
      await shot(page, '1b-band-with-glowing-help.png');
      await band(page, 'Ironwood Battleaxe', false, false);
      // 2a: the untrained glaive as it stands, closed
      await centre(page, 'Ironwood Battleaxe', 'start');
      await shot(page, '2a-untrained-closed.png');
      // 2b: the untrained glaive opened
      const glaive = page.locator('.weapon-row').filter({ has: page.locator('.weapon-name', { hasText: /^Bronze Glaive$/ }) });
      await glaive.locator('.weapon-name-line').click({ timeout: 5000 }).catch(() => {});
      const opened = await glaive.locator('.weapon-opened').waitFor({ timeout: 5000 }).then(() => true, () => false);
      check(opened, 'the glaive opens');
      check(await page.evaluate(() => {
        const row = [...document.querySelectorAll('.weapon-row')].find(r => r.querySelector('.weapon-name')?.textContent === 'Bronze Glaive');
        const line = [...row.querySelectorAll('.weapon-opened-props')].find(e => e.textContent.startsWith('Mastery:'));
        if (!line) return false;
        line.className = 'weapon-opened-rules mk-line';
        line.innerHTML = '<span class="weapon-mastery-cap" style="margin:0;padding:2px 14px 2px 8px">Graze</span>' +
          '<span>Not trained. You can master Glaives in place of one of your choices when you finish a Long Rest.</span>';
        row.setAttribute('data-mk', 'Bronze Glaive');
        return true;
      }), 'the glaive\'s mastery line is redrawn');
      await centre(page, 'Bronze Glaive', 'center');
      await shot(page, '2b-untrained-opened.png');
      await glaive.locator('.weapon-name-line').click({ timeout: 5000 }).catch(() => {});
      await page.waitForTimeout(300);
      // 3: a Weapon Mastery panel above the table: what is mastered, and the swap planned for the next Long Rest
      check(await page.evaluate(() => {
        const sec = document.querySelector('.weapons-section');
        const head = [...document.querySelectorAll('span.f-w-b.f-s-18')].find(e => e.offsetParent && e.textContent.trim() === 'Weapons');
        const headRow = head.closest('div.flex') || head.parentElement;
        const sel = [...document.querySelectorAll('select')].find(s => s.offsetParent);
        const panel = document.createElement('div');
        panel.className = 'mk-panel';
        const h = headRow.cloneNode(true);
        h.querySelector('span.f-w-b.f-s-18').textContent = 'Weapon Mastery';
        const chip = (w, m) => `<span class="mk-chip"><span class="weapon-mastery-cap">${m}</span><span>${w}</span></span>`;
        const selCls = sel ? sel.className : '';
        panel.innerHTML = `<div class="mk-panel-row"><div class="mk-panel-label">Mastered <span class="mk-dim-text">3 of 3</span></div>
            <div class="mk-chips">${chip('Battleaxe', 'Topple')}${chip('Longsword', 'Sap')}${chip('Handaxe', 'Vex')}</div></div>
          <div class="mk-panel-row"><div class="mk-panel-label">After your next Long Rest <span class="mk-dim-text">swap one</span></div>
            <div class="mk-swap"><select class="${selCls}"><option>Handaxe</option></select><span class="mk-arrow">\u2192</span>
            <select class="${selCls}"><option>Glaive · Graze</option></select></div></div>`;
        panel.prepend(h);
        sec.parentElement.insertBefore(panel, sec.previousElementSibling && sec.previousElementSibling.contains(head) ? sec.previousElementSibling : sec);
        panel.setAttribute('data-mk', 'panel');
        // the stand-in data tags only the made-up weapons: draw the bands the panel says are mastered
        const src = document.querySelector('[data-mk="Ironwood Battleaxe"] .weapon-mastery-band');
        for (const [name, m, trig, text] of [['Longsword', 'Sap', 'on hit', 'Disadvantage on its next attack'],
                                             ['Handaxe', 'Vex', 'on hit', 'Advantage on your next attack at it']]) {
          const row = [...document.querySelectorAll('.weapon-row')].find(r => r.querySelector('.weapon-name')?.textContent === name);
          const b = src.cloneNode(true);
          b.querySelector('.weapon-mastery-cap').textContent = m;
          b.querySelector('.weapon-mastery-trigger').textContent = trig;
          b.querySelector('.weapon-mastery-summary').textContent = text;
          row.appendChild(b); row.setAttribute('data-mk-band', ''); row.classList.add('mk-added');
        }
        return true;
      }), 'the mastery panel is drawn in');
      await centre(page, 'panel', 'start');
      await shot(page, '3-mastery-panel.png');
      await page.evaluate(() => { document.querySelector('.mk-panel').remove();
        document.querySelectorAll('.mk-added').forEach(r => { r.querySelector('.weapon-mastery-band:last-child').remove();
          r.removeAttribute('data-mk-band'); r.classList.remove('mk-added'); }); });
      await page.waitForTimeout(200);
      // 3: the card beside the band; 4: the same as a bottom sheet; 5: Prone open inside the card
      await centre(page, 'Ironwood Battleaxe', 'start');
      await card(page, { anchor: 'Ironwood Battleaxe', sheet: true }); await shot(page, '4-card-as-bottom-sheet.png'); await clean(page);
      await card(page, { anchor: 'Ironwood Battleaxe', sheet: true, nested: true }); await shot(page, '5-prone-inside-the-card.png'); await clean(page);
      // 6: the roll result after a hit, Topple offered, Prone open inside it
      const axe = page.locator('[data-mk="Ironwood Battleaxe"]');
      await axe.locator('.weapon-hit-tile').click({ timeout: 5000 }).catch(() => {});
      await page.locator('.weapon-sheet .weapon-sheet-option.primary').click({ timeout: 5000 }).catch(() => {});
      const dmg = await page.locator('.weapon-sheet', { hasText: 'damage' }).locator('.weapon-sheet-part').first()
        .waitFor({ timeout: 5000 }).then(() => true, () => false);
      check(dmg, 'the battleaxe rolls damage in the sheet');
      check(await page.evaluate(([PRONE_LINK, PRONE_TEXT]) => {
        const cur = document.querySelector('.weapon-sheet-current');
        if (!cur) return false;
        const after = document.createElement('div');
        after.className = 'weapon-sheet-after';
        after.style.cssText = 'display:block';
        after.innerHTML = `<div class="mk-line"><span class="weapon-mastery-cap" style="margin:0;padding:2px 14px 2px 8px">Topple</span>
          <span>The target makes a <b>DC 13 Con</b> save or falls ${PRONE_LINK.replace('srd-link', 'srd-link mk-open')}.</span>
          <button class="mk-q" style="margin:-10px -10px -10px auto"><i class="fa fa-question-circle"></i></button></div>${PRONE_TEXT}`;
        const parts = cur.querySelectorAll('.weapon-sheet-part, .weapon-sheet-total');
        parts[parts.length - 1].after(after);
        return true;
      }, [PRONE_LINK, PRONE_TEXT]), 'Topple is drawn into the result');
      await shot(page, '6-roll-result-topple.png');
    }
    // the app's own preview card today, on a phone: does it fit the screen?
    await page.goto(`${BASE}/pages/dnd/5e/spells/charm-person`, { waitUntil: 'domcontentloaded' });
    const link = page.locator('.srd-link', { hasText: /charmed/i }).first();
    if (await link.waitFor({ timeout: 30000 }).then(() => true, () => false)) {
      await link.tap({ timeout: 5000 }).catch(() => {});
      const box = await page.locator('.srd-preview').first().waitFor({ timeout: 5000 }).then(
        () => page.evaluate(() => { const b = document.querySelector('.srd-preview').getBoundingClientRect();
                                    return { left: b.left, right: b.right, vw: innerWidth }; }), () => null);
      console.log('  real preview on a phone:', JSON.stringify(box));
      if (box) await shot(page, '8-todays-preview-on-a-phone.png');
    } else console.log('  real preview: no charmed link found');
    await ctx.close();

    const desk = await browser.newContext({ viewport: { width: 1280, height: 900 } }); await desk.addInitScript(init);
    const dp = await desk.newPage();
    if (await setup(dp, false)) {
      check(await band(dp, 'Ironwood Battleaxe', false, false), 'desktop: the battleaxe has its band');
      await unstick(dp);
      await centre(dp, 'Ironwood Battleaxe', 'start');
      await card(dp, { anchor: 'Ironwood Battleaxe', nested: true });
      await shot(dp, '7-desktop-card-prone-open.png');
    }
  } catch (e) { console.log('error:', e.message); failures++; }
  await browser.close();
  console.log(failures ? `MOCKUPS: ${failures} failed` : 'MOCKUPS: done');
  process.exit(failures ? 1 : 0);
})();
