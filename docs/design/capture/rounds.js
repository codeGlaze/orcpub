// Every design capture, by round. Each writes where its review page reads its pictures (`out`,
// under docs/design/, where git ignores images) and prints what it measured. Run with capture.js.
const fs = require('fs');
const path = require('path');
const { iconUri, BASE } = require('./lib');

const PAGES = [['/pages/dnd/5e/character-builder', 'builder'], ['/dnd/5e/my-content', 'mycontent'],
               ['/pages/dnd/5e/spells', 'spells'], ['/pages/dnd/5e/monsters', 'monsters']];
const BUILDER = '/pages/dnd/5e/character-builder';
const ROLL = '.app.dark-button-text .roll-button';
const LIFT = 'text-shadow: 0 1px 1px rgba(0,0,0,0.35), 0 0 1px rgba(0,0,0,0.25);';
const DARK_INK = '#15202e';
const both = ['dark', 'light'];

// The five form buttons, and the six ability buttons, as one clip each.
const formButtons = v => v.clipSpan('.form-button', 4);
const rollButtons = v => v.clipSpan('.roll-button', 5);
const stroke = (w, c) => `${ROLL} { -webkit-text-stroke: ${w}px ${c}; paint-order: stroke fill; }`;
// A stroke can't take a gradient: a copy of the label behind the real one (::before) takes a
// transparent stroke and a gradient clipped to the text, which Chrome paints into the stroke too.
const gradientRim = grad => `
  ${ROLL} { position: relative; isolation: isolate; -webkit-text-stroke: 0 !important; }
  ${ROLL}::before { content: attr(data-label); position: absolute; inset: 0; z-index: -1;
    display: flex; align-items: center; justify-content: center;
    -webkit-text-stroke: 2px transparent; color: transparent; text-shadow: none;
    background: ${grad}; -webkit-background-clip: text; background-clip: text; }`;
const labelRollButtons = v => v.page.evaluate(() => document.querySelectorAll('.roll-button').forEach(e => { e.dataset.label = e.textContent; }));

// Variants of the ability-button rim: white text (`white`, null) on the plain page, the rest with
// dark text on. Shot as roll-<key>.
async function rimRound(cap, variants, suffix = '') {
  for (const darkText of [false, true]) {
    const v = await cap.open(BUILDER, { darkText });
    await labelRollButtons(v);
    for (const [k, css] of Object.entries(variants)) {
      if ((css === null) === darkText) continue;
      await v.restyle(css);
      await v.shoot(`roll-${k}${suffix}`, await rollButtons(v));
    }
    await v.close();
  }
}

// One line per element on the page: a gutter or edge marker, drawn over everything.
const drawLines = (v, lines) => v.page.evaluate(ls => {
  document.querySelectorAll('.capture-lines').forEach(e => e.remove());
  const layer = document.createElement('div'); layer.className = 'capture-lines';
  layer.style.cssText = 'position:fixed;inset:0;pointer-events:none;z-index:99999';
  for (const [x, color] of ls) {
    const line = document.createElement('div');
    line.style.cssText = `position:absolute;top:0;bottom:0;left:${x - 0.5}px;width:1px;background:${color}`;
    layer.append(line);
  }
  document.body.appendChild(layer);
}, lines);

// --- artist-page mock-ups: proposals drawn inside the real /artists page and drawer credit ---
const icon = (uri, color, size) =>
  `<span class="lk-ico" style="width:${size}px;height:${size}px;color:${color};-webkit-mask-image:url(${uri});mask-image:url(${uri})"></span>`;
const LINK_CSS = `
.mk-links { display: flex; flex-direction: column; gap: 8px; }
.mk-link { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 5px;
  border: 1px solid rgba(255,255,255,0.10); color: #ebeef4; text-decoration: none; font: 600 13px/1 'Open Sans', sans-serif; }
.mk-link .host { margin-left: auto; font-weight: 400; font-size: 11px; color: var(--lk-dim); }
.mk-link.feature { border-color: #f0a100; background: rgba(240,161,0,0.07); }
.ap-root.light-theme .mk-link { border-color: rgba(0,0,0,0.14); color: #363636; }
.ap-root.light-theme .mk-link.feature { border-color: #33658A; background: rgba(51,101,138,0.06); }`;
const CARD_CSS = LINK_CSS + `
.mk-card { max-width: 560px; margin: 0 auto; padding: 28px 28px 22px; background: #131924;
  border: 1px solid rgba(240,161,0,0.16); border-radius: 12px; display: flex; flex-direction: column; gap: 20px; }
.ap-root.light-theme .mk-card { background: #fff; border-color: rgba(0,0,0,0.10); }
.mk-head { display: flex; flex-direction: column; gap: 10px; text-align: center; }
.mk-bio { margin: 0; font: italic 13px/1.5 'Open Sans', sans-serif; color: var(--lk-dim); }`;
const PANEL_CSS = `
.pk { max-width: 520px; margin: 0 auto; padding: 22px 24px; background: #131924; border: 1px solid rgba(255,255,255,0.08);
  border-radius: 12px; display: flex; flex-direction: column; gap: 18px; font-family: 'Open Sans', sans-serif; color: #ebeef4; }
.ap-root.light-theme .pk { background: #fff; border-color: rgba(0,0,0,0.10); color: #363636; }
.pk h2 { margin: 0; font: 700 16px/1.3 'Open Sans', sans-serif; }
.pk .row { display: flex; flex-direction: column; gap: 9px; }
.pk .lbl { font: 600 10px/1 'Open Sans', sans-serif; letter-spacing: .14em; text-transform: uppercase; color: var(--lk-dim); }
.pk .icons, .pk .swatches { display: flex; flex-wrap: wrap; gap: 8px; }
.pk .ib { display: flex; flex-direction: column; align-items: center; gap: 6px; width: 72px; padding: 10px 0 8px; border-radius: 6px;
  border: 1px solid rgba(255,255,255,0.10); font: 600 11px/1 'Open Sans', sans-serif; color: var(--lk-dim); }
.ap-root.light-theme .pk .ib { border-color: rgba(0,0,0,0.14); }
.pk .ib.on { border-color: #f0a100; color: #ebeef4; box-shadow: 0 0 0 2px rgba(240,161,0,0.25); }
.ap-root.light-theme .pk .ib.on { border-color: #33658A; color: #363636; box-shadow: 0 0 0 2px rgba(51,101,138,0.2); }
.pk .sw { width: 30px; height: 30px; border-radius: 50%; border: 1px solid rgba(255,255,255,0.25); }
.ap-root.light-theme .pk .sw { border-color: rgba(0,0,0,0.2); }
.pk .sw.on { box-shadow: 0 0 0 2px #131924, 0 0 0 4px #f0a100; }
.ap-root.light-theme .pk .sw.on { box-shadow: 0 0 0 2px #fff, 0 0 0 4px #33658A; }
.pk .swl { display: flex; flex-direction: column; align-items: center; gap: 6px; font: 600 10px/1 'Open Sans', sans-serif; color: var(--lk-dim); width: 46px; }
.pk .mode { display: inline-flex; border: 1px solid rgba(255,255,255,0.12); border-radius: 6px; overflow: hidden; align-self: flex-start; }
.ap-root.light-theme .pk .mode { border-color: rgba(0,0,0,0.14); }
.pk .mode span { display: inline-flex; align-items: center; gap: 8px; padding: 8px 12px; font: 600 12px/1 'Open Sans', sans-serif; color: var(--lk-dim); }
.pk .mode span.on { background: rgba(240,161,0,0.10); color: #ffcc5e; }
.ap-root.light-theme .pk .mode span.on { background: rgba(51,101,138,0.08); color: #33658A; }
.pk .mode i { width: 14px; height: 14px; border-radius: 50%; display: inline-block; }
.pk .note { margin: 0; font-size: 12px; color: var(--lk-dim); }
.pk .prev { border-top: 1px solid rgba(255,255,255,0.06); padding-top: 14px; display: flex; flex-direction: column; gap: 7px; }
.ap-root.light-theme .pk .prev { border-top-color: rgba(0,0,0,0.08); }
.pk .lk-name { text-decoration: underline dotted var(--lk-under); text-decoration-thickness: 1px; text-underline-offset: 3px; }`;
const CARD = '.ap-card, .mk-card, .mk-links, .pk';
const setCreditMark = (v, html) => v.page.evaluate(h => { document.querySelector('.pl-attribution .lk-mark').innerHTML = h; }, html);
const SITE_ICONS = [['ph-browser-fill', 'Browser'], ['ph-house-fill', 'House'], ['ph-compass-fill', 'Compass'], ['ph-sparkle-fill', 'Sparkle']];

// The artist's picker panel: icon, colour (split swatches), and how it reads in the light theme.
function pickerPanel({ chosenIcon, chosenColor, shown, colours, half, modes, note }) {
  return `
    <h2>Your artist page</h2>
    <div class="row"><div class="lbl">Icon for your site</div><div class="icons">
      ${SITE_ICONS.map(([f, l]) => `<div class="ib${f === chosenIcon ? ' on' : ''}">${icon(iconUri(f), f === chosenIcon ? shown : 'currentColor', 18)}${l}</div>`).join('')}
    </div></div>
    <div class="row"><div class="lbl">Colour</div><div class="swatches">
      ${Object.entries(colours).map(([n, c]) => `<div class="swl"><span class="sw${n === chosenColor ? ' on' : ''}" style="background:linear-gradient(135deg, ${c[0]} 0 50%, ${c[half]} 50% 100%)"></span>${n}</div>`).join('')}
    </div>${modes ? '' : `<p class="note">${note}</p>`}</div>
    ${modes ? `<div class="row"><div class="lbl">In the light theme</div>${modes}<p class="note">${note}</p></div>` : ''}
    <div class="prev"><div class="lbl">Preview</div>
      <div class="lk-cap">Art by</div>
      <div class="lk-row"><span class="lk-rule l"></span>${icon(iconUri(chosenIcon), shown, 12)}<span class="lk-name">Fusspot</span>${icon(iconUri('now-twitch'), '#9146ff', 12)}<span class="lk-rule r"></span></div>
    </div>`;
}

module.exports = {
  'buttons-and-tabs': {
    out: 'style-guide/round3', what: 'four pages per theme; form-button fonts; header-tab wrap options',
    async run(cap) {
      const BTN = { arial: 'button { font-family: Arial, sans-serif !important; } .form-button { font-weight: 700 !important; }',
        os600: '.form-button { font-weight: 600 !important; }', os700: '',
        dark: `.app:not(.light-theme) .form-button { color: ${DARK_INK} !important; }` };
      const WRAP = { asis: '', ls02: '.header-tab .title { letter-spacing: -0.02em; }', ls04: '.header-tab .title { letter-spacing: -0.04em; }',
        s13: '.header-tab { font-size: 13px !important; }', w600: '.header-tab { font-weight: 600 !important; }' };
      for (const theme of both) {
        const v = await cap.open(null, { theme, dpr: 1 });
        for (const [url, name] of PAGES) { await v.go(url); await v.shoot(`${theme}-v2-${name}`, { x: 0, y: 150, width: 1280, height: 750 }); }
        await v.go(BUILDER);
        for (const [k, css] of Object.entries(BTN)) { await v.restyle(css); await v.shoot(`${theme}-btn-${k}`, await v.clipSpan('.form-button', 4, 6)); }
        for (const [k, css] of Object.entries(WRAP)) { await v.restyle(css); await v.shoot(`${theme}-wrap-${k}`, await v.clipParent('.header-tab', 4)); }
        await v.close();
      }
    }
  },

  'tab-wrap-and-amber': {
    out: 'style-guide/round4', what: 'narrowest window keeping MY CONTENT on one line, per option; dark text on every amber surface',
    async run(cap) {
      const WRAP = { 'as is (700)': '', 'letter spacing -2%': '.header-tab .title { letter-spacing: -0.02em; }',
        'letter spacing -4%': '.header-tab .title { letter-spacing: -0.04em; }', '13px': '.header-tab { font-size: 13px !important; }',
        '600 weight': '.header-tab { font-weight: 600 !important; }', 'before (400, integration)': '.header-tab, .header-tab * { font-weight: 400 !important; }' };
      const v = await cap.open(BUILDER, { viewport: { width: 1600, height: 900 }, dpr: 1 });
      const oneLine = () => v.page.evaluate(() => {
        const t = [...document.querySelectorAll('.header-tab .title')].find(e => /my content/i.test(e.textContent));
        if (!t || !t.offsetParent) return null;
        const lh = parseFloat(getComputedStyle(t).lineHeight) || parseFloat(getComputedStyle(t).fontSize) * 1.4;
        return t.getBoundingClientRect().height < lh * 1.5;
      });
      for (const [name, css] of Object.entries(WRAP)) {
        await v.restyle(css);
        let fits = null;
        for (let w = 1600; w >= 700; w -= 10) {
          await v.page.setViewportSize({ width: w, height: 900 }); await v.page.waitForTimeout(40);
          const r = await oneLine();
          if (r === null) { fits = fits ?? `labels hidden below ${w}`; break; }
          if (!r) break;
          fits = w;
        }
        console.log(`${name.padEnd(28)} one line down to ${fits}px`);
      }
      await v.close();
      const amber = /rgb\(241, 162, 15\)/.source;
      const q = await cap.open(null, { dpr: 1 });
      for (const [url, name] of PAGES) for (const state of ['white', 'dark']) {
        await q.go(url);
        if (state === 'dark') {
          const n = await q.page.evaluate(([re, ink]) => {
            const hits = [...document.querySelectorAll('*')].filter(el => new RegExp(re).test(getComputedStyle(el).backgroundImage));
            for (const el of hits) [el, ...el.querySelectorAll('*')].forEach(c => c.style.setProperty('color', ink, 'important'));
            return hits.length;
          }, [amber, DARK_INK]);
          console.log(`${name}: ${n} amber elements`);
        }
        await q.shoot(`dark-${state}-${name}`, { x: 0, y: 150, width: 1280, height: 750 });
      }
      await q.close();
    }
  },

  'tab-room': {
    out: 'style-guide/round4', what: 'the room MY CONTENT has in its tab, per option (prints only)',
    async run(cap) {
      const WRAP = { 'before (400, integration)': '.header-tab, .header-tab * { font-weight: 400 !important; }', 'as is (700)': '',
        'letter spacing -2%': '.header-tab .title { letter-spacing: -0.02em; }', 'letter spacing -4%': '.header-tab .title { letter-spacing: -0.04em; }',
        '13px': '.header-tab { font-size: 13px !important; }', '600 weight': '.header-tab, .header-tab * { font-weight: 600 !important; }' };
      const v = await cap.open(BUILDER, { dpr: 1 });
      for (const [name, css] of Object.entries(WRAP)) {
        await v.restyle(css);
        const r = await v.page.evaluate(() => {
          const t = [...document.querySelectorAll('.header-tab .title')].find(e => /my content/i.test(e.textContent));
          const box = t.parentElement, cs = getComputedStyle(box);
          const avail = box.clientWidth - parseFloat(cs.paddingLeft) - parseFloat(cs.paddingRight);
          const probe = t.cloneNode(true); probe.style.cssText += ';position:absolute;visibility:hidden;white-space:nowrap;width:auto';
          box.appendChild(probe); const need = probe.getBoundingClientRect().width; probe.remove();
          return { tab: Math.round(t.closest('.header-tab').getBoundingClientRect().width), avail: Math.round(avail * 10) / 10, need: Math.round(need * 10) / 10 };
        });
        const spare = r.avail - r.need;
        console.log(`${name.padEnd(27)} tab ${r.tab}px, room ${r.avail}px, label needs ${r.need}px -> ${spare >= 0 ? `fits, ${spare.toFixed(1)}px spare` : `wraps, ${(-spare).toFixed(1)}px short`}`);
      }
      await v.close();
    }
  },

  'button-text': {
    out: 'style-guide/round5', what: 'form-button text: shadow, deeper amber, dark text',
    async run(cap) {
      const V = { '1-today': '', '2-shadow': `.form-button { ${LIFT} }`,
        '3-deeper': '.form-button { background-image: linear-gradient(to bottom, #c98300, #b8792a) !important; }',
        '4-deeper-shadow': '.form-button { background-image: linear-gradient(to bottom, #c98300, #b8792a) !important; text-shadow: 0 1px 1px rgba(0,0,0,0.3); }',
        '5-dark': `.form-button { color: ${DARK_INK} !important; }` };
      const v = await cap.open(BUILDER);
      for (const [k, css] of Object.entries(V)) { await v.restyle(css); await v.shoot(k, await formButtons(v)); }
      await v.close();
    }
  },

  'button-dark-text': {
    out: 'style-guide/round6', what: 'form-button white text with a shadow, and dark text with embosses and glows',
    async run(cap) {
      const V = { '6-white-shadow': `.form-button { ${LIFT} }`, '7-dark': `.form-button { color: ${DARK_INK} !important; }`,
        '8-dark-emboss': `.form-button { color: ${DARK_INK} !important; text-shadow: 0 1px 0 rgba(255,255,255,0.35); }`,
        '9-dark-glow': `.form-button { color: ${DARK_INK} !important; text-shadow: 0 0 2px rgba(255,255,255,0.45), 0 1px 0 rgba(255,255,255,0.3); }`,
        '10-dark-emboss-strong': `.form-button { color: ${DARK_INK} !important; text-shadow: 0 1px 0 rgba(255,255,255,0.55), 0 0 1px rgba(255,255,255,0.3); }` };
      const v = await cap.open(BUILDER);
      for (const [k, css] of Object.entries(V)) { await v.restyle(css); await v.shoot(k, await formButtons(v)); }
      await v.close();
    }
  },

  'dark-button-text-setting': {
    out: 'style-guide/round7', what: 'the built dark-button-text setting per theme: form and ability buttons, header tab with and without a lift',
    async run(cap) {
      for (const [theme, darkText] of [['dark', false], ['dark', true], ['light', false]]) {
        const v = await cap.open(BUILDER, { theme, darkText });
        const tag = `${theme}${darkText ? '-darktext' : ''}`;
        console.log(tag, (await Promise.all(['.form-button', '.roll-button'].map(s => v.page.evaluate(s => {
          const cs = getComputedStyle(document.querySelector(s)); return `${s}: ${cs.color} / ${cs.textShadow.slice(0, 40)}`;
        }, s)))).join(' | '));
        await v.shoot(`${tag}-buttons`, await formButtons(v));
        await v.shoot(`${tag}-roll`, await rollButtons(v));
        if (!darkText) for (const [k, css] of [['tab-now', ''], ['tab-lift', `.header-tab { ${LIFT} }`]]) {
          await v.restyle(css);
          const c = await v.clipParent('.header-tab', 4);
          await v.shoot(`${tag}-${k}`, { ...c, width: Math.min(1280 - c.x, 470) });
        }
        await v.close();
      }
    }
  },

  'rim-outline': {
    out: 'style-guide/round9', what: 'outline options for dark text on the ability buttons',
    run: cap => rimRound(cap, {
      'w-white': null, 'd-built': '',
      'e-shadow-ring': `${ROLL} { text-shadow: 1px 0 0 rgba(255,255,255,.55), -1px 0 0 rgba(255,255,255,.55), 0 1px 0 rgba(255,255,255,.55), 0 -1px 0 rgba(255,255,255,.55) !important; }`,
      'f-stroke-hair': `${stroke(1, 'rgba(255,255,255,.75)')} ${ROLL} { text-shadow: none !important; }`,
      'g-stroke-hair-glow': stroke(1, 'rgba(255,255,255,.75)'),
      'h-stroke-full': `${stroke(2, 'rgba(255,255,255,.85)')} ${ROLL} { text-shadow: none !important; }`,
      'i-stroke-full-glow': stroke(2, 'rgba(255,255,255,.85)') })
  },

  'rim-colour': {
    out: 'style-guide/round10', what: 'coloured outline options for dark text on the ability buttons',
    run: cap => rimRound(cap, {
      'w-white': null, 'i-white-2': stroke(2, 'rgba(255,255,255,.85)'), 'j-yellow-1': stroke(1, '#ffd21a'),
      'k-yellow-2': stroke(2, '#ffd21a'), 'l-paleyellow-2': stroke(2, '#ffe680'), 'm-orange-2': stroke(2, '#ff8a00'),
      'n-amber-2': stroke(2, '#f5b942') })
  },

  'rim-gradient': {
    out: 'style-guide/round11', what: 'gradient outline options for dark text on the ability buttons (DPR=1|2)',
    run: cap => rimRound(cap, {
      'w-white': null, 'k-built': '',
      'o-reverse': gradientRim('linear-gradient(to bottom, #dbab50 40%, #f1a20f 62%)'),
      'p-bottom-solid': gradientRim('#dbab50'),
      'q-yellow-reverse': gradientRim('linear-gradient(to bottom, #ffd21a 40%, #dbab50 62%)'),
      'r-bevel': gradientRim('linear-gradient(to bottom, #ffd21a 40%, #b37a00 62%)') }, process.env.DPR ? `@${process.env.DPR}` : '')
  },

  'label-size': {
    out: 'style-guide/round12', what: 'ability-button labels at 14, 15 and 16px: white, glow only, the built rim, the lit rim (DPR=1|2)',
    async run(cap) {
      const dpr = +(process.env.DPR || 2);
      const STYLES = { white: null, glow: `${ROLL} { -webkit-text-stroke: 0 !important; }`, rim: '',
        lit: gradientRim('linear-gradient(to bottom, #ffd21a 40%, #dbab50 62%)') };
      for (const darkText of [false, true]) {
        const v = await cap.open(BUILDER, { darkText, dpr });
        await labelRollButtons(v);
        for (const size of [14, 15, 16]) for (const [k, css] of Object.entries(STYLES)) {
          if ((css === null) === darkText) continue;
          await v.restyle(`.roll-button { font-size: ${size}px !important; } ${css || ''}`);
          if (dpr === 1) console.log(size, k, await v.page.locator('.roll-button').first().evaluate(e => `${getComputedStyle(e).fontSize} h=${e.getBoundingClientRect().height}`));
          await v.shoot(`${size}-${k}@${dpr}`, await rollButtons(v));
        }
        await v.close();
      }
    }
  },

  'label-size-phone': {
    out: 'style-guide/round13', what: 'the builder on 320 and 390px phones: the toggle row, and ability labels at 14-16px keeping a 31px button',
    async run(cap) {
      // The app picks its phone layout from the user agent, not the window width.
      const phone = (width, darkText) => cap.open(BUILDER, { device: 'iPhone 13', viewport: { width, height: 844 }, dpr: 3, darkText });
      const SIZES = { 14: '', 15: '.roll-button { font-size: 15px !important; padding-top: 5.5px !important; padding-bottom: 5.5px !important; }',
        16: '.roll-button { font-size: 16px !important; padding-top: 4.5px !important; padding-bottom: 4.5px !important; }' };
      for (const width of [320, 390]) {
        const v = await phone(width, false);
        const r = await v.page.locator('div.pointer', { hasText: 'Light Theme' }).first().boundingBox();
        const right = await v.page.locator('div.pointer', { hasText: 'Dark Button Text' }).first().evaluate(e => e.getBoundingClientRect().right);
        console.log(`toggles at ${width}px: right edge ${Math.round(right)}, gap ${Math.round(width - right)}px`);
        await v.shoot(`toggles-${width}`, { x: 0, y: r.y - 90, width, height: 150 });
        await v.close();
      }
      for (const width of [320, 390]) for (const darkText of [false, true]) {
        const v = await phone(width, darkText);
        await v.page.getByText(/^details$/i).first().click(); await v.page.waitForTimeout(400);
        for (const [size, css] of Object.entries(SIZES)) {
          await v.restyle(css);
          const btn = v.page.locator('.roll-button').first();
          await btn.scrollIntoViewIfNeeded();
          console.log(width, darkText ? 'dark' : 'white', size, await btn.evaluate(e => { const r = e.getBoundingClientRect(); return `${getComputedStyle(e).fontSize} ${Math.round(r.width)}x${r.height}`; }));
          const a = await btn.boundingBox();
          await v.shoot(`${size}-${darkText ? 'rim' : 'white'}-${width}`, { x: 0, y: Math.max(0, a.y - 110), width, height: 160 });
        }
        await v.close();
      }
    }
  },

  'layout-width': {
    out: 'style-guide/round14', what: 'a desktop window at 1280, the same window narrowed to 390, and a real phone',
    async run(cap) {
      const v = await cap.open(BUILDER, { viewport: { width: 1280, height: 800 } });
      await v.shoot('desktop-1280');
      await v.page.setViewportSize({ width: 390, height: 844 }); await v.page.waitForTimeout(500);
      await v.page.evaluate(() => scrollTo(0, 0));
      await v.shoot('desktop-narrowed-options');
      await v.page.locator('.builder-tab', { hasText: 'Details' }).click(); await v.page.waitForTimeout(400);
      const a = await v.page.locator('.roll-button').first().boundingBox();
      await v.page.evaluate(y => scrollTo(0, y - 300), a.y + await v.page.evaluate(() => scrollY));
      await v.shoot('desktop-narrowed-details');
      await v.close();
      const p = await cap.open(BUILDER, { device: 'iPhone 13' });
      await p.shoot('phone-options');
      await p.close();
    }
  },

  'phone-header': {
    out: 'style-guide/round15', what: 'the header on 390 and 320px phones, and on desktop to show it unchanged',
    async run(cap) {
      for (const [name, opts, height] of [['phone-390', { device: 'iPhone 13' }, 300], ['phone-320', { device: 'iPhone SE' }, 300],
                                          ['desktop-1280', { viewport: { width: 1280, height: 800 } }, 520]]) {
        const v = await cap.open(BUILDER, opts);
        console.log(name, 'sideways overflow', await v.page.evaluate(() => document.documentElement.scrollWidth - innerWidth));
        await v.shoot(name, { x: 0, y: 0, width: v.page.viewportSize().width, height });
        await v.close();
      }
    }
  },

  'left-edges': {
    out: 'style-guide/round16', what: "the phone header's left edges, as built and all on 10px; the bar with search and login",
    async run(cap) {
      const v = await cap.open(BUILDER, { device: 'iPhone 13', dpr: 3 });
      const bar = { x: 0, y: 0, width: 390, height: 52 };
      await v.shoot('bar', bar);
      for (const state of ['now', 'aligned']) {
        if (state === 'aligned') {
          await v.page.evaluate(() => document.querySelector('h1').closest('.flex-wrap').children[1].classList.add('edge-align-buttons'));
          await v.addStyle(`.app-header-bar .w-100-p { padding-left: 10px !important; padding-right: 10px !important; }
            .app-header-menu { padding-left: 8px !important; padding-right: 8px !important; }
            .flex-wrap.edge-align-buttons.edge-align-buttons { margin-left: 5px !important; }`);
          await v.page.waitForTimeout(300);
          await v.shoot('bar-aligned', bar);
        }
        // the ink, not the box: a text's left edge is where its first glyph starts
        const edges = await v.page.evaluate(() => {
          const ink = e => { const r = document.createRange(); r.selectNodeContents(e); return r.getBoundingClientRect().left; };
          return [['logo', document.querySelector('.app-header-bar img').getBoundingClientRect().left, '#ff5c5c'],
            ['tabs', document.querySelector('.header-tab').getBoundingClientRect().left, '#3ddc84'],
            ['title', ink(document.querySelector('h1')), '#4da3ff'],
            ['buttons', document.querySelector('h1').closest('.flex-wrap').children[1].firstElementChild.getBoundingClientRect().left, '#c084fc'],
            ['panel', document.querySelector('.builder-tabs').parentElement.getBoundingClientRect().left, '#ff8fd1']];
        });
        await drawLines(v, edges.map(([, x, c]) => [x, c]));
        console.log(state, edges.map(([n, x]) => `${n} ${Math.round(x)}`).join(', '));
        await v.shoot(`edges-${state}`, { x: 0, y: 0, width: 390, height: 600 });
        await v.shoot(`strip-${state}`, { x: 0, y: 0, width: 70, height: 600 });
      }
      await v.close();
    }
  },

  gutters: {
    out: 'style-guide/round17', what: 'the builder on a 390px phone as built, and with every row on a 10px and a 16px gutter',
    async run(cap) {
      // Each row's own spacing, set so its visible edge lands on the gutter G.
      const css = G => `
        .app-header-bar .w-100-p { padding-left: ${G}px !important; padding-right: ${G}px !important; }
        .app-header-menu { padding-left: ${G - 2}px !important; padding-right: ${G - 2}px !important; }
        h1[data-g] { margin-left: ${G}px !important; }
        [data-g=buttons].flex-wrap { margin-left: ${G - 5}px !important; margin-right: ${G - 5}px !important; }
        [data-g=toggles].flex-wrap { padding-right: ${G}px !important; }
        [data-g=builder-in].flex { padding-left: ${G}px !important; }
        [data-g=builder-out].w-100-p { padding-right: ${G}px !important; }`;
      for (const [name, G] of [['built', null], ['g10', 10], ['g16', 16]]) {
        const v = await cap.open(BUILDER, { device: 'iPhone 13', dpr: 3 });
        if (G) {
          await v.page.evaluate(() => {
            const h1 = document.querySelector('h1'); h1.dataset.g = 'title';
            h1.closest('.flex-wrap').children[1].dataset.g = 'buttons';
            [...document.querySelectorAll('div.pointer')].find(e => /Dark Button Text/.test(e.textContent) && e.children.length < 4).parentElement.dataset.g = 'toggles';
            const inner = document.querySelector('.builder-tabs').parentElement.parentElement;
            inner.dataset.g = 'builder-in'; inner.parentElement.dataset.g = 'builder-out';
          });
          await v.addStyle(css(G)); await v.page.waitForTimeout(400);
          await drawLines(v, [[G, '#4da3ff'], [390 - G, '#4da3ff']]);
        }
        console.log(name, await v.page.evaluate(() => {
          const W = innerWidth, r = e => e.getBoundingClientRect();
          const ink = e => { const x = document.createRange(); x.selectNodeContents(e); return x.getBoundingClientRect().left; };
          const bar = document.querySelector('.app-header-bar .w-100-p'), tabs = [...document.querySelectorAll('.header-tab')];
          const panel = document.querySelector('.builder-tabs').parentElement;
          const toggles = [...document.querySelectorAll('div.pointer')].find(e => /Dark Button Text/.test(e.textContent) && e.children.length < 4);
          const firstBtn = document.querySelector('h1').closest('.flex-wrap').children[1].firstElementChild;
          const m = { logo: r(bar.querySelector('img')).left, tabs: r(tabs[0]).left, title: ink(document.querySelector('h1')),
            buttons: r(firstBtn).left, panel: r(panel).left, 'login R': W - r(bar.lastElementChild).right,
            'tabs R': W - r(tabs[tabs.length - 1]).right, 'toggles R': W - r(toggles).right, 'panel R': W - r(panel).right };
          return Object.entries(m).map(([k, x]) => `${k} ${Math.round(x)}`).join(', ');
        }));
        await v.shoot(name, { x: 0, y: 0, width: 390, height: 640 });
        await v.close();
      }
    }
  },

  'roll-16': {
    out: 'style-guide/round19', what: "16px roll buttons: the builder's ability row on phones and desktop, white and dark text, and the skills",
    async run(cap) {
      const report = async (v, label) => console.log(label, await v.page.evaluate(() => {
        const bs = [...document.querySelectorAll('.roll-button')].filter(b => b.offsetParent);
        const hs = [...new Set(bs.map(b => Math.round(b.getBoundingClientRect().height)))];
        return `${bs.length} buttons, ${getComputedStyle(bs[0]).fontSize}, heights ${hs.join('/')}, labels overflowing ${bs.filter(b => b.scrollWidth > b.clientWidth + 1).length}`;
      }));
      const shootFrom = async (v, name, selector, above, height) => {
        const el = v.page.locator(selector).first();
        await v.page.mouse.move(1, 1);
        await el.scrollIntoViewIfNeeded();
        await v.page.evaluate(() => scrollBy(0, -120)); await v.page.waitForTimeout(200);
        const r = await el.boundingBox();
        await v.shoot(name, { x: 0, y: Math.max(0, r.y - above), width: v.page.viewportSize().width, height });
      };
      const details = async v => { await v.page.getByText(/^details$/i).first().click(); await v.page.waitForTimeout(400); };
      for (const [w, device] of [['390', 'iPhone 13'], ['320', 'iPhone SE']]) for (const darkText of [false, true]) {
        const v = await cap.open(BUILDER, { device, darkText });
        await details(v);
        const tag = `phone-${w}-${darkText ? 'dark' : 'white'}`;
        await report(v, tag); await shootFrom(v, tag, '.roll-button', 120, 560);
        await v.close();
      }
      const d = await cap.open(BUILDER, { darkText: false });
      await report(d, 'desktop'); await shootFrom(d, 'desktop', '.roll-button', 90, 420);
      await d.close();
      const s = await cap.open(BUILDER, { device: 'iPhone 13', darkText: false });
      await details(s); await shootFrom(s, 'phone-390-skills', 'text=Acrobatics', 60, 520);
      await s.close();
    }
  },

  'header-menus': {
    out: 'style-guide/round21', what: 'the Characters flyout on desktop and phone, and the logged-in user menu, with their computed boxes',
    async run(cap) {
      const d = await cap.open(BUILDER);
      console.log('desktop', await d.describe('.header-tab'));
      await d.page.locator('.header-tab').first().hover(); await d.page.waitForTimeout(400);
      console.log('desktop', await d.describe('.header-tab .header-flyout'));
      console.log('desktop', await d.describe('.header-tab .header-flyout > div:nth-child(2)'));
      await d.shoot('desktop-flyout', { x: 560, y: 170, width: 720, height: 330 });
      await d.close();
      const p = await cap.open(BUILDER, { device: 'iPhone 13' });
      await p.page.locator('.header-tab').first().focus(); await p.page.waitForTimeout(400);
      console.log('phone', await p.describe('.header-tab .header-flyout'));
      await p.shoot('phone-flyout', { x: 0, y: 0, width: 390, height: 300 });
      await p.close();
      const u = await cap.open(BUILDER, { login: true });
      await u.page.locator('#user-header').hover(); await u.page.waitForTimeout(400);
      console.log('user', await u.describe('#user-menu'));
      await u.shoot('user-menu', { x: 980, y: 0, width: 300, height: 180 });
      await u.page.mouse.move(400, 600); await u.page.waitForTimeout(400);
      console.log('user closed', await u.describe('#user-menu'));
      await u.close();
    }
  },

  'phone-menus': {
    out: 'style-guide/round21/menus', what: "every header tab's menu on a phone, checked fully on screen and not clipped (fails the run if one is)",
    async run(cap) {
      let bad = 0;
      for (const [w, device] of [[390, 'iPhone 13'], [320, 'iPhone SE']]) {
        const v = await cap.open(BUILDER, { device });
        const tabs = v.page.locator('.header-tab');
        for (let i = 0, n = await tabs.count(); i < n; i++) {
          const tab = tabs.nth(i);
          if (!(await tab.locator('.header-flyout').count())) continue;
          await tab.focus(); await v.page.waitForTimeout(350);
          // the point at the menu's bottom-centre must be the menu itself, not what's behind it
          const r = await tab.locator('.header-flyout').evaluate(f => {
            const b = f.getBoundingClientRect(), hit = document.elementFromPoint(b.left + b.width / 2, b.bottom - 4);
            return { l: Math.round(b.left), r: Math.round(innerWidth - b.right), bottom: Math.round(b.bottom),
                     visible: !!hit && f.contains(hit), over: document.documentElement.scrollWidth - innerWidth };
          });
          const ok = r.l >= 0 && r.r >= 0 && r.visible && r.over <= 0;
          if (!ok) bad++;
          console.log(`${ok ? 'PASS' : 'FAIL'}  ${w}px tab ${i + 1}: left ${r.l}, right ${r.r}, bottom ${r.bottom}, fully shown ${r.visible}`);
          await v.shoot(`${w}-tab${i + 1}`, { x: 0, y: 0, width: w, height: 420 });
          await v.page.evaluate(() => document.activeElement.blur()); await v.page.waitForTimeout(150);
        }
        await v.close();
      }
      if (bad) throw new Error(`${bad} menu(s) off screen or clipped`);
    }
  },

  bold: {
    out: 'style-guide/bold', what: "faux bold (the site's 400-only Open Sans) against the real 600 and 700 faces",
    async run(cap) {
      for (const theme of both) for (const state of ['before', 'after']) {
        const v = await cap.open(BUILDER, { theme });
        if (state === 'after') await v.addFontWeights([600, 700]);
        const loaded = await v.page.evaluate(() => [...new Set([...document.fonts].filter(f => /Open Sans/.test(f.family) && f.status === 'loaded').map(f => f.weight))]);
        console.log(`  ${theme} ${state}: loaded Open Sans weights ${JSON.stringify(loaded)}`);
        const tabs = v.page.locator('.builder-tabs').first();
        await tabs.scrollIntoViewIfNeeded();
        const box = await tabs.boundingBox();
        await v.shoot(`${theme}-${state}-builder`, { x: 0, y: Math.max(0, box.y - 220), width: 1280, height: box.height + 260 });
        await v.shoot(`${theme}-${state}-button`, '.form-button');
        await v.close();
      }
    }
  },

  weights: {
    out: 'style-guide/weights', what: 'header tab and page title at each real Open Sans weight, and the pairs in context',
    async run(cap) {
      const header = v => v.page.locator('.header-tab').first().evaluate(el => { const r = el.parentElement.getBoundingClientRect(); return { x: r.x, y: r.y, width: r.width, height: r.height }; });
      const title = v => v.page.locator('h1.f-s-36').first().boundingBox();
      const VARIANTS = { tabs: ['.header-tab', [400, 500, 600, 700], header], title: ['h1.f-s-36', [400, 600, 700], title] };
      for (const theme of both) {
        const v = await cap.open(BUILDER, { theme });
        await v.addFontWeights([400, 500, 600, 700]);
        await v.page.mouse.move(5, 890);
        for (const [name, [sel, weights, boxOf]] of Object.entries(VARIANTS)) for (const w of weights) {
          await v.restyle(`${sel}, ${sel} * { font-weight: ${w} !important; }`);
          const b = await boxOf(v);
          await v.shoot(`${theme}-${name}-${w}`, { x: Math.max(0, b.x - 10), y: Math.max(0, b.y - 6), width: Math.min(1280, b.width + 20), height: b.height + 12 });
        }
        for (const [tw, hw] of [[400, 400], [500, 600], [700, 600]]) {
          await v.restyle(`.header-tab, .header-tab * { font-weight: ${tw} !important; } h1.f-s-36 { font-weight: ${hw} !important; }`);
          const top = Math.max(0, (await header(v)).y - 10), h1 = await title(v);
          await v.shoot(`${theme}-pair-${tw}-${hw}`, { x: 0, y: top, width: 1280, height: h1.y + h1.height + 30 - top });
        }
        await v.close();
      }
    }
  },

  'survey-weights': {
    out: 'style-guide', what: 'every visible text at weight 600 or more on four pages, and where its weight comes from (prints only)',
    async run(cap) {
      const v = await cap.open(null, { dpr: 1 });
      for (const [url, name] of PAGES) {
        await v.go(url);
        const rows = await v.page.evaluate(() => {
          const out = new Map();
          for (const el of document.querySelectorAll('body *')) {
            const own = [...el.childNodes].filter(n => n.nodeType === 3).map(n => n.textContent.trim()).join(' ').trim();
            if (!own || !el.offsetParent) continue;
            const s = getComputedStyle(el), w = +s.fontWeight; if (w < 600) continue;
            const r = el.getBoundingClientRect(); if (r.top > 900 || r.width === 0) continue;
            let src = 'inherited';
            for (let e = el; e && e !== document.body; e = e.parentElement) {
              const tag = e.tagName.toLowerCase(), cls = [...e.classList].find(c => /^f-w-/.test(c));
              if (e.style && e.style.fontWeight) { src = `inline on <${tag}>`; break; }
              if (cls) { src = `.${cls} on <${tag}>`; break; }
              if (/^h[1-6]$/.test(tag)) { src = `<${tag}> default`; break; }
              if (['button', 'b', 'strong'].includes(tag)) { src = `<${tag}>`; break; }
            }
            const key = `${parseFloat(s.fontSize)}px ${w} ${s.textTransform === 'uppercase' ? 'CAPS' : 'mixed'} | ${src}`;
            if (!out.has(key)) out.set(key, []);
            if (out.get(key).length < 3) out.get(key).push(own.slice(0, 40));
          }
          return [...out.entries()].sort((a, b) => parseFloat(b[0]) - parseFloat(a[0]));
        });
        console.log(`== ${name}`);
        for (const [k, ex] of rows) console.log(`  ${k}  e.g. ${JSON.stringify(ex)}`);
      }
      await v.close();
    }
  },

  'survey-amber': {
    out: 'style-guide', what: 'every text on an amber surface across the main pages, both themes, grouped by class (prints only)',
    async run(cap) {
      const urls = ['/pages/dnd/5e/character-builder', '/dnd/5e/my-content', '/pages/dnd/5e/spells', '/pages/dnd/5e/monsters',
        '/pages/dnd/5e/magic-items', '/pages/dnd/5e/characters', '/pages/dnd/5e/encounter-builder', '/pages/dnd/5e/combat-tracker',
        '/pages/login-page', '/pages/register-page', '/'];
      for (const theme of both) {
        const v = await cap.open(null, { theme, dpr: 1 });
        const found = new Map();
        for (const url of urls) {
          await v.go(url).catch(() => {});
          await v.page.waitForTimeout(500);
          const rows = await v.page.evaluate(() => {
            const amber = s => (s.match(/rgba?\((\d+), (\d+), (\d+)(?:, ([\d.]+))?\)/g) || []).some(c => {
              const [r, g, b, a] = c.match(/[\d.]+/g).map(Number);
              return (a === undefined || a > 0.6) && r > 190 && g > 120 && g < 200 && b < 110;
            });
            const out = [];
            for (const el of document.querySelectorAll('body *')) {
              if (!el.offsetParent) continue;
              const s = getComputedStyle(el);
              if (!(amber(s.backgroundColor) || amber(s.backgroundImage))) continue;
              const text = (el.innerText || '').trim().replace(/\s+/g, ' ');
              if (!text) continue;
              out.push({ key: `${el.tagName.toLowerCase()}.${[...el.classList].join('.') || '(no class)'}${el.getAttribute('style') ? ' +inline style' : ''}`,
                         text: text.slice(0, 24), color: s.color });
            }
            return out;
          });
          for (const r of rows) {
            if (!found.has(r.key)) found.set(r.key, { n: 0, pages: new Set(), ex: r.text, color: r.color });
            const f = found.get(r.key); f.n++; f.pages.add(url);
          }
        }
        console.log(`== ${theme}`);
        for (const [k, f] of [...found.entries()].sort((a, b) => b[1].n - a[1].n))
          console.log(`  ${String(f.n).padStart(3)}  ${k.slice(0, 70)}  text ${f.color}  e.g. "${f.ex}"  on ${[...f.pages].join(' ')}`);
        await v.close();
      }
    }
  },

  checkboxes: {
    out: 'style-guide/checkboxes', what: "the builder's setting boxes and the spell builder's components, checked and not, both themes",
    async run(cap) {
      for (const [theme, darkText] of [['dark', false], ['dark', true], ['light', false]]) {
        const tag = `${theme}${darkText ? '-darktext' : ''}`;
        const v = await cap.open(BUILDER, { theme, darkText, dpr: 3 });
        await v.shoot(`${tag}-settings`, await v.clipAround(v.page.locator('.setting-toggle').first().locator('..'), 8));
        await v.go('/pages/dnd/5e/spell-builder');
        // tick the first of each row, so both states show side by side
        for (const t of ['verbal', 'bard']) await v.page.getByText(t, { exact: true }).first().click();
        await v.page.mouse.move(1, 1); await v.page.waitForTimeout(150);
        for (const [name, t] of [['components', 'verbal'], ['classes', 'bard']])
          await v.shoot(`${tag}-${name}`, await v.clipAround(v.page.getByText(t, { exact: true }).first().locator('xpath=ancestor::div[contains(@class,"flex-wrap")][1]'), 8));
        await v.close();
      }
    }
  },

  // --- artist pages ---

  'artist-pages': {
    out: 'artist-profiles/garden', what: 'the artist profile and index, both themes, desktop and phone (TAG=before|after names the set)',
    async run(cap) {
      const tag = process.env.TAG || 'shot';
      for (const theme of both) for (const [size, opts] of [['desktop', {}], ['phone', { device: 'iPhone 13' }]]) {
        const v = await cap.open(null, { theme, dpr: 1, ...opts });
        for (const [name, url] of [['profile', '/artists/fusspot'], ['index', '/artists']]) {
          await v.go(url);
          await v.page.waitForSelector('.ap-card');
          await v.shoot(`${tag}-${name}-${theme}-${size}`, await v.clipPage('.ap-root'));
        }
        await v.close();
      }
    }
  },

  'artist-cards': {
    out: 'artist-profiles/cards', what: "card layouts: one column, a split card, and no pictures; with the server's own examples",
    async run(cap) {
      const CSS = CARD_CSS + `
        .mk-pill { align-self: center; font: 600 10px/1 'Open Sans', sans-serif; letter-spacing: .12em; text-transform: uppercase;
          color: var(--lk-dim); border: 1px dashed var(--lk-rule); padding: 5px 9px; border-radius: 99px; }
        .mk-link .lk-ico { width: 15px; height: 15px; }
        .mk-ex { display: flex; justify-content: center; gap: 10px; }
        .mk-ex img { width: 150px; height: 188px; border-radius: 8px; display: block; border: 1px solid rgba(240,161,0,0.16); }
        .ap-root.light-theme .mk-ex img { border-color: rgba(0,0,0,0.12); }
        .mk-excap { text-align: center; font: italic 12px/1.4 'Vollkorn', Georgia, serif; color: var(--lk-dim); margin-top: -8px; }
        .mk-pieces { text-align: center; font-size: 12px; color: var(--lk-dim); margin: 0; }
        .mk-pieces a { color: inherit; text-decoration: underline dotted var(--lk-under); text-underline-offset: 3px; }
        .mk-foot { margin: 0; padding-top: 14px; border-top: 1px solid rgba(255,255,255,0.06); font-size: 12px; color: var(--lk-dim); text-align: center; }
        .ap-root.light-theme .mk-foot { border-top-color: rgba(0,0,0,0.08); }
        .mk-split { display: grid; grid-template-columns: 150px 1fr; gap: 22px; align-items: start; }
        .mk-split .mk-head { text-align: left; } .mk-split .mk-pill { align-self: flex-start; }
        .mk-split .lk-row .lk-rule.l { display: none; } .mk-split .ap-name { font-size: 34px; }
        .mk-side { display: flex; flex-direction: column; gap: 16px; min-width: 0; }
        @media (max-width: 560px) {
          .mk-card { padding: 22px 16px 18px; } .mk-split { grid-template-columns: 1fr; }
          .mk-split .mk-head { text-align: center; } .mk-split .mk-pill { align-self: center; }
          .mk-split .lk-row .lk-rule.l { display: block; } }`;
      const ico = (name, color) => `<span class="lk-ico" aria-hidden="true" style="color:${color};-webkit-mask-image:url(/image/social/${name}.svg);mask-image:url(/image/social/${name}.svg)"></span>`;
      const HEAD = `<div class="mk-head"><div class="lk-cap">Portrait artist</div>
        <div class="lk-row"><span class="lk-rule l"></span><h1 class="ap-name">Fusspot</h1><span class="lk-rule r"></span></div>
        <p class="mk-bio">[The artist's own short bio, up to 280 characters.]</p><span class="mk-pill">[Commission status]</span></div>`;
      const LINKS = `<div class="mk-links">
        <a class="mk-link feature" href="#">${ico('site', '#f0a100')}Site<span class="host">fusspot.rip</span></a>
        <a class="mk-link" href="#">${ico('twitch', '#9146ff')}Twitch<span class="host">twitch.tv/fusspot</span></a></div>`;
      const FOOT = '<p class="mk-foot">Every portrait made with these pieces credits Fusspot.</p>';
      const ex = n => `<img src="/artists/fusspot/examples/${n}" alt="Example portrait">`;
      const LAYOUTS = {
        column: `${HEAD}${LINKS}<div class="mk-ex">${ex(1)}${ex(2)}</div><div class="mk-excap">Made with Fusspot's pieces</div>${FOOT}`,
        split: `<div class="mk-split"><div class="mk-ex">${ex(1)}</div><div class="mk-side">${HEAD}${LINKS}</div></div>${FOOT}`,
        minimal: `${HEAD}${LINKS}<p class="mk-pieces">28 pieces in the portrait maker: heads, hair, eyes and 7 more layers.
          <a href="#">Try them in a portrait ›</a></p>${FOOT}` };
      for (const theme of both) for (const [tag, viewport] of [['desktop', { width: 1280, height: 900 }], ['phone', { width: 390, height: 844 }]]) {
        const v = await cap.open('/artists/fusspot', { theme, viewport, dpr: 1 });
        if (theme === 'dark' && tag === 'desktop') for (const n of [1, 2]) {
          fs.mkdirSync(cap.out, { recursive: true });
          const res = await v.page.request.get(`${BASE}/artists/fusspot/examples/${n}`);
          fs.writeFileSync(path.join(cap.out, `example-${n - 1}.jpg`), await res.body());
        }
        await v.addStyle(CSS);
        for (const [name, html] of Object.entries(LAYOUTS)) {
          await v.replace(CARD, 'mk-card', html);
          await v.page.waitForFunction(() => [...document.images].every(i => i.complete));
          await v.shoot(`${name}-${theme}-${tag}`, await v.clipPage('.ap-root'));
        }
        await v.close();
      }
    }
  },

  'artist-icons': {
    out: 'artist-profiles/icons', what: "candidate site icons in the drawer credit and the card's link rows, and two whole icon families (ICONS=dir)",
    async run(cap) {
      const SITE = ['now-site', 'lu-globe', 'lu-house', 'ph-house-line', 'tb-world-www', 'lu-link', 'gi-lorc-quill-ink', 'gi-lorc-quill',
        'ph-pen-nib', 'ph-feather', 'gi-delapouite-palette', 'lu-palette', 'gi-delapouite-paint-brush', 'ph-paint-brush', 'gi-lorc-castle'];
      const FAMILIES = {
        gameicons: [['gi-lorc-quill-ink', '#f0a100', 'Site', 'fusspot.rip'], ['now-twitch', '#9146ff', 'Twitch', 'twitch.tv/fusspot'],
          ['gi-delapouite-paint-brush', '#e0a24d', 'Portfolio', 'example.test/work'], ['gi-lorc-scroll-unfurled', '#c9a36a', 'Commissions', 'example.test/commissions'],
          ['gi-lorc-open-book', '#7a94b8', 'Blog', 'example.test/blog'], ['lu-link', '#8b95a5', 'Anything else', 'example.test']],
        lucide: [['lu-house', '#f0a100', 'Site', 'fusspot.rip'], ['now-twitch', '#9146ff', 'Twitch', 'twitch.tv/fusspot'],
          ['lu-palette', '#e0a24d', 'Portfolio', 'example.test/work'], ['lu-scroll-text', '#c9a36a', 'Commissions', 'example.test/commissions'],
          ['lu-book-open', '#7a94b8', 'Blog', 'example.test/blog'], ['lu-link', '#8b95a5', 'Anything else', 'example.test']] };
      const row = (f, color, label, host, feature) =>
        `<a class="mk-link${feature ? ' feature' : ''}" href="#">${icon(iconUri(f), color, 16)}${label}<span class="host">${host}</span></a>`;
      for (const theme of both) {
        const v = await cap.open(null, { theme });
        await v.openCredit();
        for (const f of SITE) { await setCreditMark(v, icon(iconUri(f), '#f0a100', 12)); await v.shoot(`credit-${theme}-${f}`, '.pl-attribution'); }
        await v.go('/artists/fusspot');
        await v.addStyle(LINK_CSS + '.mk-links { width: 420px; padding: 14px; }');
        for (const f of SITE) { await v.replace(CARD, 'mk-links', row(f, '#f0a100', 'Site', 'fusspot.rip', true)); await v.shoot(`card-${theme}-${f}`, '.mk-links'); }
        for (const [fam, rows] of Object.entries(FAMILIES)) {
          await v.replace(CARD, 'mk-links', rows.map(([f, c, l, h], i) => row(f, c, l, h, i === 0)).join(''));
          await v.shoot(`family-${theme}-${fam}`, '.mk-links');
        }
        await v.close();
      }
    }
  },

  'artist-site-mark': {
    out: 'artist-profiles/marks', what: "filled site icons in five colours, and a badge, beside the credit's Twitch mark (ICONS=dir)",
    async run(cap) {
      const ICON_SET = ['now-site', 'ph-browser-fill', 'ph-app-window-fill', 'ph-house-fill', 'ph-compass-fill', 'ph-sparkle-fill', 'gi-lorc-quill-ink'];
      const BADGE_SET = ['ph-browser-fill', 'ph-house-fill', 'gi-lorc-quill-ink'];
      const COLORS = { amber: '#f0a100', coral: '#ff6b57', teal: '#1fb5a3', rose: '#e6488f', green: '#43b85c' };
      const badge = (u, color) => `<span style="display:grid;place-items:center;width:14px;height:14px;border-radius:3px;background:${color}">` +
        `<span style="display:block;width:10px;height:10px;background:#fff;-webkit-mask:url(${u}) center/contain no-repeat;mask:url(${u}) center/contain no-repeat"></span></span>`;
      for (const theme of both) {
        const v = await cap.open(null, { theme });
        await v.openCredit();
        for (const f of ICON_SET) for (const [cn, c] of Object.entries(COLORS)) {
          await setCreditMark(v, icon(iconUri(f), c, 12)); await v.shoot(`${theme}-${f}-${cn}`, '.pl-attribution .lk-row');
        }
        for (const f of BADGE_SET) for (const [cn, c] of Object.entries(COLORS)) {
          await setCreditMark(v, badge(iconUri(f), c)); await v.shoot(`${theme}-badge-${f}-${cn}`, '.pl-attribution .lk-row');
        }
        await v.close();
      }
    }
  },

  'artist-picks': {
    out: 'artist-profiles/picks', what: 'the four pickable site icons in coral and teal, in the credit and on the card (ICONS=dir)',
    async run(cap) {
      const COLORS = { coral: '#ff6b57', teal: '#1fb5a3' };
      for (const theme of both) {
        const v = await cap.open(null, { theme });
        await v.openCredit();
        for (const [f] of SITE_ICONS) for (const [cn, c] of Object.entries(COLORS)) {
          await setCreditMark(v, icon(iconUri(f), c, 12)); await v.shoot(`credit-${theme}-${f}-${cn}`, '.pl-attribution');
        }
        await v.go('/artists/fusspot');
        await v.addStyle(CARD_CSS);
        for (const [f] of SITE_ICONS) for (const [cn, c] of Object.entries(COLORS)) {
          await v.replace(CARD, 'mk-card', `<div class="mk-head"><div class="lk-cap">Portrait artist</div>
            <div class="lk-row"><span class="lk-rule l"></span><h1 class="ap-name">Fusspot</h1><span class="lk-rule r"></span></div>
            <p class="mk-bio">[The artist's own short bio, up to 280 characters.]</p></div>
            <div class="mk-links"><a class="mk-link feature" href="#">${icon(iconUri(f), c, 16)}Site<span class="host">fusspot.rip</span></a>
            <a class="mk-link" href="#">${icon(iconUri('now-twitch'), '#9146ff', 16)}Twitch<span class="host">twitch.tv/fusspot</span></a></div>`);
          await v.shoot(`card-${theme}-${f}-${cn}`, '.mk-card');
        }
        await v.close();
      }
    }
  },

  'artist-swatches': {
    out: 'artist-profiles/swatches', what: 'themed colour pairs: the picker with split swatches, and each pair in the real credit (ICONS=dir; PAIRS_JSON, SELECTED_JSON)',
    async run(cap) {
      // [dark-theme colour, light-theme colour]; each >= 4.3:1 on its own ground
      const PAIRS = process.env.PAIRS_JSON ? JSON.parse(process.env.PAIRS_JSON) : { coral: ['#ff6b57', '#d9432f'], teal: ['#1fb5a3', '#0f8577'],
        yellow: ['#ffd21a', '#9a7400'], rose: ['#f06aa6', '#c2386f'], green: ['#43b85c', '#2f8a44'] };
      const SELECTED = process.env.SELECTED_JSON ? JSON.parse(process.env.SELECTED_JSON) : [['ph-browser-fill', 'teal'], ['ph-sparkle-fill', 'yellow']];
      for (const theme of both) {
        const ti = theme === 'dark' ? 0 : 1;
        const v = await cap.open('/artists/fusspot', { theme });
        await v.addStyle(PANEL_CSS);
        for (const [f, cn] of SELECTED) {
          await v.replace(CARD, 'pk', pickerPanel({ chosenIcon: f, chosenColor: cn, shown: PAIRS[cn][ti], colours: PAIRS, half: 1,
            note: 'Each colour has a version for the dark theme and one for the light theme. Visitors see whichever matches their theme.' }));
          await v.shoot(`panel-${theme}-${f}-${cn}`, '.pk');
        }
        await v.openCredit();
        for (const [f] of SITE_ICONS) for (const [n, pair] of Object.entries(PAIRS)) {
          await setCreditMark(v, icon(iconUri(f), pair[ti], 12)); await v.shoot(`credit-${theme}-${f}-${n}`, '.pl-attribution .lk-row');
        }
        await v.close();
      }
    }
  },

  'artist-picker': {
    out: 'artist-profiles/picker', what: 'the two-step colour picker: five colours, then a deeper shade or a paired colour for the light theme (ICONS=dir)',
    async run(cap) {
      // name: [dark, light as a deeper shade, light as a paired colour]
      const COLORS = { yellow: ['#ffd21a', '#9a7400', '#33658A'], coral: ['#ff6b57', '#d9432f', '#0f7c80'],
        teal: ['#1fb5a3', '#0f8577', '#b4532a'], rose: ['#f06aa6', '#c2386f', '#1f7a4a'], green: ['#43b85c', '#2f8a44', '#8e3a78'] };
      for (const theme of both) {
        const v = await cap.open('/artists/fusspot', { theme });
        await v.addStyle(PANEL_CSS);
        for (const [f, cn, mode] of [['ph-browser-fill', 'teal', 'shade'], ['ph-browser-fill', 'teal', 'paired'],
                                     ['ph-sparkle-fill', 'yellow', 'shade'], ['ph-sparkle-fill', 'yellow', 'paired']]) {
          const [d, s, p] = COLORS[cn], half = mode === 'shade' ? 1 : 2;
          const modes = `<div class="mode"><span class="${mode === 'shade' ? 'on' : ''}"><i style="background:${s}"></i>A deeper shade</span>
            <span class="${mode === 'paired' ? 'on' : ''}"><i style="background:${p}"></i>A paired colour</span></div>`;
          await v.replace(CARD, 'pk', pickerPanel({ chosenIcon: f, chosenColor: cn, shown: theme === 'dark' ? d : COLORS[cn][half], colours: COLORS, half, modes,
            note: 'The left half of each swatch is the dark theme; the right half is the light theme. Visitors see whichever matches theirs.' }));
          await v.shoot(`picker-${theme}-${f}-${cn}-${mode}`, '.pk');
        }
        await v.close();
      }
    }
  },
};
