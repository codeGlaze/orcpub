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
    .mk-panel{margin:4px 0 12px}
    .mk-pbox{display:flex;flex-direction:column;gap:2px;padding:4px 0}
    .mk-pline{display:flex;align-items:center;gap:8px;min-height:32px;font-size:14px}
    .mk-pcap{margin:0!important;padding:3px 14px 3px 8px!important;flex:none}
    .mk-ptext{min-width:0;opacity:.9}
    .mk-pbox .mk-pline:last-child{padding-left:2px}
    .mk-link{margin-left:auto;background:none;border:0;color:#f0a100;font-weight:700;font-size:14px;text-decoration:underline;min-height:44px;padding:0 4px}
    .mk-chev{margin-left:auto;font-size:22px;opacity:.7;padding:0 6px}
    .mk-actions{display:flex;gap:12px;justify-content:flex-end;align-items:center;margin-top:16px}
    .mk-btn-plain{background:none;border:0;color:inherit;font-weight:600;font-size:15px;min-height:44px;padding:0 8px}
    .mk-btn{min-height:44px}
    .mk-panel-row{display:flex;flex-direction:column;gap:6px;padding:10px 12px;background:rgba(255,255,255,.06);border-radius:4px}
    .mk-panel-label{font-weight:700;font-size:14px}
    .mk-dim-text{font-weight:400;opacity:.65;margin-left:6px}
    .mk-chips{display:flex;flex-wrap:wrap;gap:8px}
    .mk-chip{display:inline-flex;align-items:center;gap:8px;background:var(--wpn-strip,#1a1e28);padding-right:10px;font-size:13px;font-weight:600;min-height:28px}
    .mk-chip .weapon-mastery-cap{margin:0;align-self:stretch;padding:0 14px 0 8px}
    .mk-swap{display:flex;align-items:center;gap:8px}
    .mk-swap select{min-width:0;flex:1;min-height:44px}
    .mk-arrow{font-size:18px;opacity:.7}
    .weapon-mastery-band.mk-untrained{min-height:0;padding:1px 10px;box-shadow:none;background:rgba(255,255,255,.04)}
    .mk-untrained .weapon-mastery-cap{background:#8d96a6;color:#1a1e28;font-size:9.5px;margin:-1px 0 -1px -10px}
    .mk-untrained .weapon-mastery-trigger{font-size:9.5px;color:rgba(230,233,238,.45)}
    .mk-untrained .weapon-mastery-summary{font-size:11px;font-weight:400;color:rgba(230,233,238,.55)}
    .mk-brew{margin-left:auto;flex:none;display:flex;align-items:center;gap:2px;width:44px;height:20px;padding:0 3px;border-radius:10px;
      border:1px solid rgba(230,233,238,.35);background:none;position:relative;cursor:pointer}
    .mk-stein{width:14px;height:14px;background:#8d96a6;-webkit-mask:url(/image/beer-stein.svg) center/contain no-repeat;mask:url(/image/beer-stein.svg) center/contain no-repeat}
    .mk-knob{position:absolute;right:3px;width:14px;height:14px;border-radius:50%;background:rgba(230,233,238,.5)}
    .mk-brew.on{background:#f0a100;border-color:#f0a100}
    .mk-brew.on .mk-stein{background:#1a1e28;margin-left:22px}
    .mk-brew.on .mk-knob{right:auto;left:3px;background:#1a1e28}
    .weapon-mastery-band.mk-overridden{min-height:24px;padding:3px 10px;background:var(--wpn-strip)}
    .mk-overridden .weapon-mastery-cap{background:#f0a100;font-size:10.5px;margin:-3px 0 -3px -10px}
    .mk-overridden .weapon-mastery-trigger{font-size:10.5px;color:rgba(230,233,238,.6)}
    .mk-overridden .weapon-mastery-summary{font-size:12px;font-weight:600;color:#e6e9ee}
    .mk-panel{align-self:stretch;width:100%;box-sizing:border-box}
    .mk-pbox2{box-sizing:border-box;width:100%;border:1px solid rgba(230,233,238,.22);border-radius:6px;padding:8px 10px 10px;background:rgba(255,255,255,.035)}
    .mk-ptitle{display:flex;align-items:center;gap:8px;min-height:36px}
    .mk-ptitle-text{font-weight:700;font-size:16px;letter-spacing:.01em;white-space:nowrap}
    .mk-pcount{font-size:13px;opacity:.7;min-width:0}
    .mk-ptitle .mk-brew{margin-left:auto}
    .mk-pchev{background:none;border:0;color:inherit;font-size:24px;width:44px;height:44px;margin:-4px -10px -4px -4px;opacity:.8}
    .mk-kchips{display:flex;flex-wrap:wrap;gap:4px;margin-top:2px}
    .mk-kchip{display:inline-flex;align-items:baseline;gap:4px;padding:3px 7px;border-radius:12px;border:1px solid rgba(230,233,238,.3);font-size:12px;white-space:nowrap}
    .mk-kchip b{font-weight:600}
    .mk-kmast{color:#f0a100;font-size:9.5px;font-weight:700;letter-spacing:.05em;text-transform:uppercase}
    .mk-kx{opacity:.6;margin-left:2px}
    .mk-kbrew{border-style:dashed;border-color:#f0a100}
    .mk-kadd{border-style:dashed;opacity:.8}
    .mk-swapping{border-color:#f0a100}
    .mk-swapping s{opacity:.55}
    .mk-rest-title{display:flex;align-items:center;font-size:18px;font-weight:700;margin-bottom:6px}
    .mk-rest-sec{margin-top:14px;padding-top:12px;border-top:1px solid rgba(255,255,255,.2)}
    .mk-rest-sec-title{font-weight:700;font-size:15px;margin-bottom:8px}
    .mk-rest-hint{font-size:12px;opacity:.65;margin-top:6px}
    .mk-restrow{flex-wrap:wrap;gap:8px 4px;row-gap:8px}
    .mk-dawncheck{display:inline-flex;align-items:center;gap:6px;font-size:14px;margin:0 8px 0 6px;min-height:44px}
    .mk-dawncheck input{width:18px;height:18px;accent-color:#f0a100}
    .mk-restline{display:flex;align-items:center;gap:8px;justify-content:flex-end;font-size:14px;margin-top:6px;opacity:.95}
    .mk-restline .mk-link{margin-left:4px}
    .mk-restbox{box-sizing:border-box;width:100%;border:1px solid rgba(230,233,238,.22);border-radius:6px;padding:8px 10px 10px;background:rgba(255,255,255,.035);margin:8px 0 16px}
    .mk-restbox.bare{border:0;background:none;padding:0}
    .mk-restbtns{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-top:6px}
    .mk-restbtns .form-button{min-height:44px;margin:0;width:100%}
    .mk-dawnrow{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-top:4px}
    .mk-dawnrow .mk-dawncheck{margin:0;font-size:14px}
    .mk-dawnonly{margin-left:0!important;font-size:14px}
    .mk-restundo{display:flex;align-items:center;gap:8px;font-size:14px;padding-top:6px;margin-top:6px;border-top:1px solid rgba(255,255,255,.12)}
    .mk-restundo .mk-link{margin-left:auto}
    .mk-dawnrow2{display:flex;align-items:stretch;gap:8px;margin-top:8px}
    .mk-dawnpill{flex:1;display:flex;align-items:center;gap:10px;min-height:44px;padding:0 12px;border-radius:6px;
      background:rgba(240,161,0,.14);border:1px solid rgba(240,161,0,.45);font-size:14px;font-weight:600}
    .mk-sun{color:#f0a100;font-size:20px;line-height:1}
    .mk-dawnpill input{margin-left:auto;width:20px;height:20px;accent-color:#f0a100;flex:none}
    .mk-dawnbtn{flex:none;display:flex;align-items:center;gap:6px;min-height:44px;padding:0 12px;border-radius:6px;
      background:none;border:1px solid rgba(240,161,0,.6);color:#f0a100;font-weight:700;font-size:14px}
    .mk-crow{display:flex;justify-content:flex-end;align-items:center;gap:5px;flex-wrap:wrap}
    .mk-crow .form-button{margin:0}
    .mk-cdawn{margin-top:5px}
    .mk-cdawn-top{margin-bottom:5px}
    .mk-cpill{position:relative;display:inline-flex;align-items:center;gap:5px;height:26px;padding:0 8px;border-radius:13px;font-size:12px;font-weight:600;
      background:rgba(240,161,0,.14);border:1px solid rgba(240,161,0,.45);color:inherit}
    .mk-cpill::after{content:"";position:absolute;inset:-9px -2px}
    .mk-cpill input{width:14px;height:14px;margin:0 0 0 2px;accent-color:#f0a100}
    .mk-cpill .mk-sun{font-size:14px}
    .mk-cbtn{background:none;color:#f0a100}
    .mk-cundo{display:flex;justify-content:flex-end;align-items:center;gap:6px;font-size:12px;margin-top:4px}
    .mk-cundo .mk-link{margin-left:2px;font-size:12px;min-height:26px}
    .mk-g3{display:grid;grid-template-columns:1fr 1.15fr 1.35fr;gap:6px;align-items:end}
    .mk-g2{display:grid;grid-template-columns:1fr 1fr;gap:6px;align-items:end}
    .mk-tight{margin-bottom:5px}
    .mk-b{position:relative;height:36px;border-radius:5px;border:0;font-size:13px;font-weight:700;text-transform:uppercase;letter-spacing:.02em;
      color:#fff;text-shadow:0 1px 1px rgba(0,0,0,.35);background:linear-gradient(to bottom,#f1a20f,#dbab50);display:flex;align-items:center;justify-content:center;gap:6px;width:100%}
    .mk-b::after{content:"";position:absolute;inset:-4px 0}
    .mk-b2{background:none;border:1.5px solid #f0a100;color:#f0a100;text-shadow:none}
    .mk-small{height:30px;width:auto;padding:0 12px;justify-self:start}
    .mk-tog{position:relative;display:flex;align-items:center;justify-content:center;gap:6px;height:28px;padding:0 8px;border-radius:5px;font-size:12.5px;font-weight:600;
      background:rgba(240,161,0,.14);border:1px solid rgba(240,161,0,.45);white-space:nowrap}
    .mk-tog .fas{color:#f0a100;font-size:13px}
    .mk-tog input{width:15px;height:15px;margin:0 0 0 2px;accent-color:#f0a100}
    .mk-tog::after{content:"";position:absolute;inset:-8px 0}
    .mk-attach{margin-bottom:0}
    .mk-tab{border-bottom:0;border-radius:6px 6px 0 0;height:26px}
    .mk-joined{border-radius:0 0 5px 5px}
    .mk-split{display:flex;flex-direction:column}
    .mk-split-top{border-radius:5px 5px 0 0}
    .mk-split-bot{border-radius:0 0 5px 5px;border-top:0;height:26px}
    .mk-seg{display:grid;grid-template-columns:1fr 1.15fr 1.35fr;border-radius:6px;overflow:hidden;border:1.5px solid #f0a100}
    .mk-sg{height:36px;border:0;border-left:1px solid rgba(26,30,40,.35);font-size:13px;font-weight:700;text-transform:uppercase;color:#fff;
      text-shadow:0 1px 1px rgba(0,0,0,.35);background:linear-gradient(to bottom,#f1a20f,#dbab50);display:flex;align-items:center;justify-content:center;gap:6px}
    .mk-sg2{background:none;color:#f0a100;text-shadow:none;border-left:0}
    .mk-cask{display:flex;justify-content:flex-end;align-items:center;gap:8px;font-size:13px;margin-top:4px}
    .mk-yes{position:relative;display:inline-flex;align-items:center;gap:5px;height:28px;padding:0 12px;border-radius:5px;border:1.5px solid #f0a100;
      background:none;color:#f0a100;font-weight:700;font-size:13px}
    .mk-yes::after{content:"";position:absolute;inset:-8px 0}
    .mk-time{display:grid;grid-template-columns:1fr 1fr;border-radius:6px;overflow:hidden;border:1px solid rgba(230,233,238,.25)}
    .mk-tb{position:relative;height:30px;border:0;background:rgba(255,255,255,.06);color:#e6e9ee;font-size:12.5px;font-weight:700;letter-spacing:.04em;
      text-transform:uppercase;display:flex;align-items:center;justify-content:center;gap:7px}
    .mk-tb::after{content:"";position:absolute;inset:-7px 0}
    .mk-tb + .mk-tb{border-left:1px solid rgba(230,233,238,.25)}
    .mk-tb .fa-sun{color:#f0a100} .mk-tb .fa-moon{color:#b9a7f0}
    .mk-sky{border:0}
    .mk-sky .mk-tb:first-child{background:linear-gradient(90deg,rgba(240,161,0,.35),rgba(240,161,0,.08))}
    .mk-sky .mk-tb:last-child{background:linear-gradient(90deg,rgba(124,98,200,.08),rgba(124,98,200,.38));border-left:0}
    .mk-tc{font-size:10.5px;font-weight:600;text-transform:none;letter-spacing:0;opacity:.7;margin-left:2px}
    .mk-gap{margin-top:6px} .mk-gapt{margin-top:6px}
    .mk-seg2c{grid-template-columns:1fr 1.2fr}
    .mk-seg4{grid-template-columns:.9fr 1.1fr 1.2fr .9fr}
    .mk-seg4 .mk-sg{font-size:12px;gap:4px}
    .mk-dawnseg{color:#f0a100}
    .mk-duskseg{color:#b9a7f0;border-left:1px solid rgba(26,30,40,.35)!important}
    .mk-yesdusk{border-color:#b9a7f0;color:#b9a7f0}
    .mk-seg2e{grid-template-columns:1fr 1fr}
    .mk-card{display:grid;grid-template-columns:1fr 1fr;background:#1a1e28;border:1.5px solid #f0a100;border-bottom:0;border-radius:8px 8px 0 0;overflow:hidden}
    .mk-card .mk-tb{background:none;height:28px}
    .mk-card .mk-tb + .mk-tb{border-left:1px solid rgba(240,161,0,.35)}
    .mk-card-mid{width:64%;margin:0 auto}
    .mk-under{border-top-left-radius:0;border-top-right-radius:0}
    .mk-under-mid{}
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
      // 2c: the owner's idea, a thin grey line for an untrained mastery (behind a display setting)
      await page.evaluate(() => {
        const row = [...document.querySelectorAll('.weapon-row')].find(r => r.querySelector('.weapon-name')?.textContent === 'Bronze Glaive');
        const b = document.createElement('div');
        b.className = 'weapon-mastery-band mk-untrained';
        b.innerHTML = '<span class="weapon-mastery-cap">Graze</span><span class="weapon-mastery-trigger">not trained</span>' +
                      '<span class="weapon-mastery-summary">Swap it in at a Long Rest</span>' +
                      '<button class="mk-brew" aria-label="Use Graze anyway (house rule)"><span class="mk-stein"></span><span class="mk-knob"></span></button>';
        row.appendChild(b); row.setAttribute('data-mk-band', ''); row.classList.add('mk-untrained-row');
      });
      await shot(page, '2c-untrained-thin-line.png');
      // 2d: the brew switch on: the full band, marked as a house rule with the stein
      await page.evaluate(() => {
        const row = document.querySelector('.mk-untrained-row');
        const b = row.querySelector('.mk-untrained');
        b.className = 'weapon-mastery-band mk-overridden';
        b.innerHTML = '<span class="weapon-mastery-cap">Graze</span><span class="weapon-mastery-trigger">on miss</span>' +
          '<span class="weapon-mastery-summary">3 slashing damage</span>' +
          '<button class="mk-brew on" aria-label="Using Graze as a house rule"><span class="mk-stein"></span><span class="mk-knob"></span></button>';
        b.classList.add('mk-untrained');
      });
      await shot(page, '2d-untrained-brew-on.png');
      await page.evaluate(() => { const b = document.querySelector('.mk-untrained-row .mk-overridden'); b.className = 'weapon-mastery-band mk-untrained'; });
      await page.evaluate(() => { const r = document.querySelector('.mk-untrained-row');
        r.querySelector('.mk-untrained').remove(); r.removeAttribute('data-mk-band'); r.classList.remove('mk-untrained-row'); });
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
      // 3: a compact Weapon Mastery panel above the table, in its states
      const panel = (kind) => page.evaluate((kind) => {
        document.querySelectorAll('.mk-panel, .mk-dim, .srd-preview.mk').forEach(e => e.remove());
        const sec = document.querySelector('.weapons-section');
        const head = [...document.querySelectorAll('span.f-w-b.f-s-18')].find(e => e.offsetParent && e.textContent.trim() === 'Weapons');
        const el = document.createElement('div');
        el.className = 'mk-panel'; el.setAttribute('data-mk', 'panel');
        const brew = kind === 'brew';
        const chip = (w, m, extra) => `<span class="mk-kchip${extra ? ' ' + extra : ''}"><b>${w}</b><span class="mk-kmast">${m}</span>${brew ? '<span class="mk-kx">✕</span>' : ''}</span>`;
        const chips = kind === 'planned'
          ? chip('Battleaxe', 'Topple') + chip('Longsword', 'Sap') + `<span class="mk-kchip mk-swapping"><b><s>Handaxe</s> → Glaive</b><span class="mk-kmast">Graze</span></span>`
          : chip('Battleaxe', 'Topple') + chip('Longsword', 'Sap') + chip('Handaxe', 'Vex') + (brew ? chip('Glaive', 'Graze', 'mk-kbrew') + '<span class="mk-kchip mk-kadd">+ Add</span>' : '');
        const status = kind === 'planned' ? 'swap planned' : brew ? '4 · house rule' : '3 of 3';
        el.innerHTML = `<div class="mk-pbox2">
          <div class="mk-ptitle"><span class="mk-ptitle-text">Weapon Mastery</span><span class="mk-pcount">${status}</span>
            <button class="mk-brew${brew ? ' on' : ''}" aria-label="Choose freely (house rule)"><span class="mk-stein"></span><span class="mk-knob"></span></button>
            <button class="mk-pchev" aria-label="Plan a swap">›</button></div>
          <div class="mk-kchips">${chips}</div></div>`;
        head.parentElement.after(el);
        if (kind === 'rest') {
          const d = document.createElement('div'); d.className = 'mk-dim'; document.querySelector('.app').appendChild(d);
          const s = document.createElement('div'); s.className = 'srd-preview mk mk-sheet';
          const sel = [...document.querySelectorAll('select')].find(x => x.offsetParent);
          const cls = sel ? sel.className : '';
          s.innerHTML = `<div class="mk-handle"></div>
            <div class="mk-rest-title">Finish Long Rest<span class="mk-x">✕</span></div>
            <div class="mk-lead" style="font-size:14px;opacity:.85">Hit points, spell slots and uses come back.</div>
            <div class="mk-rest-sec"><div class="mk-rest-sec-title">Weapon Mastery <span class="mk-dim-text">swap one</span></div>
              <div class="mk-swap"><select class="${cls}"><option>Keep all</option></select><span class="mk-arrow">→</span>
              <select class="${cls}" disabled style="opacity:.5"><option>—</option></select></div>
              <div class="mk-rest-hint">A swap you planned shows here already chosen.</div></div>
            <div class="mk-actions"><button class="mk-btn-plain">Cancel</button><button class="form-button mk-btn">Finish Long Rest</button></div>`;
          document.querySelector('.app').appendChild(s);
        }
        return true;
      }, kind);
      await page.evaluate(() => {
        const src = document.querySelector('[data-mk="Ironwood Battleaxe"] .weapon-mastery-band');
        for (const [name, m, text] of [['Longsword', 'Sap', 'Disadvantage on its next attack'], ['Handaxe', 'Vex', 'Advantage on your next attack at it']]) {
          const row = [...document.querySelectorAll('.weapon-row')].find(r => r.querySelector('.weapon-name')?.textContent === name);
          const b = src.cloneNode(true);
          b.querySelector('.weapon-mastery-cap').textContent = m;
          b.querySelector('.weapon-mastery-summary').textContent = text;
          row.appendChild(b); row.setAttribute('data-mk-band', ''); row.classList.add('mk-added');
        } });
      for (const [kind, file] of [['resting', '3a-panel-resting.png'], ['brew', '3b-panel-house-rule.png'],
                                  ['planned', '3c-panel-swap-planned.png'], ['rest', '3d-long-rest-sheet.png']]) {
        check(await panel(kind), `panel: ${kind}`);
        await centre(page, 'panel', 'start');
        await shot(page, file);
      }
      await page.evaluate(() => { document.querySelectorAll('.mk-panel, .mk-dim, .srd-preview.mk').forEach(e => e.remove());
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
    // R: the rest controls on the Features tab: Dawn passed, a Dawn button, and the Undo line
    await page.evaluate(() => re_frame.core.dispatch_sync(cljs.core.PersistentVector.fromArray(
      [cljs.reader.read_string(':orcpub.dnd.e5.character/set-selected-display-tab'), 'features'], true)));
    const restBtn = page.locator('button', { hasText: 'finish long rest' }).first();
    if (await restBtn.waitFor({ timeout: 15000 }).then(() => true, () => false)) {
      const rest = (variant, state) => page.evaluate(([variant, state]) => {
        const long = [...document.querySelectorAll('button')].find(b => b.textContent.trim() === 'finish long rest' && !b.closest('.mk-restbox'));
        const row = long.parentElement;
        row.parentElement.querySelectorAll('.mk-restbox').forEach(e => e.remove());
        if (variant === 'today') { row.style.display = ''; row.setAttribute('data-mk', 'rest'); return true; }
        row.style.display = 'none';
        const box = document.createElement('div');
        box.className = 'mk-restbox' + (variant === 'B' ? ' bare' : '');
        box.setAttribute('data-mk', 'rest');
        const toggle = (cls) => `<label class="mk-tog ${cls || ''}"><i class="fas fa-sun"></i>dawn passes<input type="checkbox" checked></label>`;
        const dawnBtn = `<button class="mk-b mk-b2"><i class="far fa-sun"></i>Dawn</button>`;
        const short = `<button class="mk-b">Short Rest</button>`;
        const longB = `<button class="mk-b">Long Rest</button>`;
        const undo = '<div class="mk-cundo"><i class="fa fa-history orange"></i><span>Long Rest finished, dawn included</span><button class="mk-link">Undo</button></div>';
        const layouts = {
          V1: `<div class="mk-g3 mk-tight"><span></span><span></span>${toggle()}</div>
               <div class="mk-g3">${dawnBtn}${short}${longB}</div>`,
          V2: `<div class="mk-g3 mk-attach"><span></span><span></span>${toggle('mk-tab')}</div>
               <div class="mk-g3">${dawnBtn}${short}<button class="mk-b mk-joined">Long Rest</button></div>`,
          V3: `<div class="mk-g3">${dawnBtn}${short}<div class="mk-split"><button class="mk-b mk-split-top">Long Rest</button>${toggle('mk-split-bot')}</div></div>`,
          V4: `<div class="mk-g2 mk-tight">${dawnBtn.replace('mk-b mk-b2', 'mk-b mk-b2 mk-small')}${toggle()}</div>
               <div class="mk-g2">${short}${longB}</div>`,
          T1: `@@TIME@@<div class="mk-seg mk-seg2c mk-gap">@@SR@@</div>`,
          TA: `<div class="mk-card mk-card-full">@@TIMEIN@@</div><div class="mk-seg mk-seg2e mk-under">@@SR@@</div>`,
          TB: `<div class="mk-card mk-card-mid">@@TIMEIN@@</div><div class="mk-seg mk-seg2e mk-under-mid">@@SR@@</div>`,
          T2: `<div class="mk-seg mk-seg2c">@@SR@@</div><div class="mk-gapt">@@TIME@@</div>`,
          T3: `@@SKY@@<div class="mk-seg mk-seg2c mk-gap">@@SR@@</div>`,
          T4: `<div class="mk-seg mk-seg4">@@DAWNSEG@@<button class="mk-sg">Short Rest</button><button class="mk-sg mk-sg1">Long Rest</button>@@DUSKSEG@@</div>`,
          T5: `@@COUNTS@@<div class="mk-seg mk-seg2c mk-gap">@@SR@@</div>`,
          F: `<div class="mk-seg">${dawnBtn.replace('mk-b mk-b2', 'mk-sg mk-sg2')}<button class="mk-sg">Short Rest</button><button class="mk-sg mk-sg1">Long Rest</button></div>`,
          V5: `<div class="mk-g3 mk-tight"><span></span><span></span>${toggle()}</div>
               <div class="mk-seg">${dawnBtn.replace('mk-b mk-b2', 'mk-sg mk-sg2')}<button class="mk-sg">Short Rest</button><button class="mk-sg mk-sg1">Long Rest</button></div>`
        };
        const parts = {
          TIME: '<div class="mk-time"><button class="mk-tb"><i class="fas fa-sun"></i>Dawn</button><button class="mk-tb"><i class="fas fa-moon"></i>Dusk</button></div>',
          SKY: '<div class="mk-time mk-sky"><button class="mk-tb"><i class="fas fa-sun"></i>Dawn</button><button class="mk-tb"><i class="fas fa-moon"></i>Dusk</button></div>',
          COUNTS: '<div class="mk-time"><button class="mk-tb"><i class="fas fa-sun"></i>Dawn<span class="mk-tc">2 items</span></button><button class="mk-tb"><i class="fas fa-moon"></i>Dusk<span class="mk-tc">Robe of Stars</span></button></div>',
          TIMEIN: '<button class="mk-tb"><i class="fas fa-sun"></i>Dawn</button><button class="mk-tb"><i class="fas fa-moon"></i>Dusk</button>',
          SR: '<button class="mk-sg">Short Rest</button><button class="mk-sg mk-sg1">Long Rest</button>',
          DAWNSEG: '<button class="mk-sg mk-sg2 mk-dawnseg"><i class="fas fa-sun"></i>Dawn</button>',
          DUSKSEG: '<button class="mk-sg mk-sg2 mk-duskseg"><i class="fas fa-moon"></i>Dusk</button>'
        };
        for (const k of Object.keys(layouts)) for (const [name, html] of Object.entries(parts)) layouts[k] = layouts[k].split('@@' + name + '@@').join(html);
        box.className = 'mk-restbox bare';
        const ask = '<div class="mk-cundo"><i class="fa fa-history orange"></i><span>Long Rest finished</span><button class="mk-link">Undo</button></div>' +
          '<div class="mk-cask"><span>Did dawn pass during the rest?</span><button class="mk-yes"><i class="fas fa-sun"></i>Yes</button></div>';
        const askBoth = '<div class="mk-cundo"><i class="fa fa-history orange"></i><span>Long Rest finished</span><button class="mk-link">Undo</button></div>' +
          '<div class="mk-cask"><span>During the rest, did</span><button class="mk-yes"><i class="fas fa-sun"></i>Dawn pass</button><button class="mk-yes mk-yesdusk"><i class="fas fa-moon"></i>Dusk pass</button></div>';
        box.innerHTML = layouts[variant] + (state === 'done' ? undo : state === 'ask' ? ask : state === 'askboth' ? askBoth : '');
        row.after(box);
        return true;
      }, [variant, state]);
      for (const [variant, state, file] of [['today', 'idle', 'R0-rest-today.png'], ['TA', 'idle', 'RTA-card-full.png'], ['TB', 'idle', 'RTB-card-tab.png'],
                                            ['T4', 'idle', 'RT4-bookends.png'], ['TA', 'askboth', 'RTA-ask-both.png']]) {
        check(await rest(variant, state), `rest controls: ${variant} ${state}`);
        await page.evaluate(() => { const r = document.querySelector('[data-mk="rest"]'); r.scrollIntoView({ block: 'center' }); });
        await shot(page, file);
      }
    } else check(false, 'the Features tab shows finish long rest');
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
