// Link-icon candidates, captured in the running credit and card.
//
// For each candidate, swaps the icon on the artist's site link -- the left
// mark in the drawer credit, and the featured row on the profile card -- and
// screenshots both in both themes. The icons go in as data-URI masks at
// capture time; nothing in the source changes.
//
//   lein fig:build && lein e2e-server
//   NODE_PATH=$(npm root -g) node docs/design/artist-profiles/capture-icons.js <icon-dir> <out-dir>
//
// <icon-dir> holds the candidate SVGs, normalised to black shapes on nothing
// (game-icons ship a black square behind a white glyph; strip it first).

const { chromium } = require('playwright');
const fs = require('fs');
const path = require('path');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const [ICONS, OUT] = [process.argv[2], process.argv[3] || 'icon-shots'];

function findChrome() {
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  const dir = fs.readdirSync(base).filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
  return path.join(base, dir, 'chrome-linux', 'chrome');
}

const uri = f => 'data:image/svg+xml;base64,' + fs.readFileSync(path.join(ICONS, f)).toString('base64');

// Candidates for the artist's own site: [file, label]
const SITE = [
  ['now-site.svg', 'Globe (current)'],
  ['lu-globe.svg', 'Globe, Lucide'],
  ['lu-house.svg', 'House, Lucide'],
  ['ph-house-line.svg', 'House, Phosphor'],
  ['tb-world-www.svg', 'WWW, Tabler'],
  ['lu-link.svg', 'Chain link, Lucide'],
  ['gi-lorc-quill-ink.svg', 'Quill and ink, game-icons'],
  ['gi-lorc-quill.svg', 'Quill, game-icons'],
  ['ph-pen-nib.svg', 'Pen nib, Phosphor'],
  ['ph-feather.svg', 'Feather, Phosphor'],
  ['gi-delapouite-palette.svg', 'Palette, game-icons'],
  ['lu-palette.svg', 'Palette, Lucide'],
  ['gi-delapouite-paint-brush.svg', 'Paint brush, game-icons'],
  ['ph-paint-brush.svg', 'Paint brush, Phosphor'],
  ['gi-lorc-castle.svg', 'Castle, game-icons']
];

// A full link list in two families, to judge consistency.
const FAMILIES = {
  gameicons: [
    ['gi-lorc-quill-ink.svg', '#f0a100', 'Site', 'fusspot.rip'],
    ['now-twitch.svg', '#9146ff', 'Twitch', 'twitch.tv/fusspot'],
    ['gi-delapouite-paint-brush.svg', '#e0a24d', 'Portfolio', 'example.test/work'],
    ['gi-lorc-scroll-unfurled.svg', '#c9a36a', 'Commissions', 'example.test/commissions'],
    ['gi-lorc-open-book.svg', '#7a94b8', 'Blog', 'example.test/blog'],
    ['lu-link.svg', '#8b95a5', 'Anything else', 'example.test']
  ],
  lucide: [
    ['lu-house.svg', '#f0a100', 'Site', 'fusspot.rip'],
    ['now-twitch.svg', '#9146ff', 'Twitch', 'twitch.tv/fusspot'],
    ['lu-palette.svg', '#e0a24d', 'Portfolio', 'example.test/work'],
    ['lu-scroll-text.svg', '#c9a36a', 'Commissions', 'example.test/commissions'],
    ['lu-book-open.svg', '#7a94b8', 'Blog', 'example.test/blog'],
    ['lu-link.svg', '#8b95a5', 'Anything else', 'example.test']
  ]
};

const CARD_CSS = `
.mk-links { display: flex; flex-direction: column; gap: 8px; width: 420px; padding: 14px; }
.mk-link { display: flex; align-items: center; gap: 10px; padding: 11px 14px; border-radius: 5px;
  border: 1px solid rgba(255,255,255,0.10); color: #ebeef4; text-decoration: none; font: 600 13px/1 'Open Sans', sans-serif; }
.mk-link .host { margin-left: auto; font-weight: 400; font-size: 11px; color: var(--lk-dim); }
.mk-link.feature { border-color: #f0a100; background: rgba(240,161,0,0.07); }
.ap-root.light-theme .mk-link { border-color: rgba(0,0,0,0.14); color: #363636; }
.ap-root.light-theme .mk-link.feature { border-color: #33658A; background: rgba(51,101,138,0.06); }
.mk-link .lk-ico { width: 16px; height: 16px; }`;

const icoStyle = (u, color) =>
  `color:${color};-webkit-mask-image:url(${u});mask-image:url(${u})`;

async function newPage(browser, theme) {
  const ctx = await browser.newContext({ viewport: { width: 1280, height: 900 }, deviceScaleFactor: 2 });
  await ctx.addInitScript(t => { try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {} }, theme);
  const page = await ctx.newPage();
  return { ctx, page };
}

async function dismiss(page) {
  const cookie = page.locator('#cookie-btn');
  if (await cookie.count()) { await cookie.click({ timeout: 3000 }).catch(() => {}); await page.waitForTimeout(250); }
  await page.addStyleTag({ content: '.whats-new-backdrop { display: none !important; }' });
}

(async () => {
  fs.mkdirSync(OUT, { recursive: true });
  const browser = await chromium.launch({ executablePath: findChrome() });
  for (const theme of ['dark', 'light']) {
    // --- the drawer credit ---
    const { ctx, page } = await newPage(browser, theme + '-theme');
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await dismiss(page);
    const desc = page.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await desc.count()) { await desc.click(); await page.waitForTimeout(300); }
    await page.locator('.pl-launcher').click();
    await page.locator('.pl-drawer').waitFor({ state: 'visible' });
    await page.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    await page.mouse.move(5, 5);
    await page.waitForTimeout(500);
    for (const [file] of SITE) {
      await page.evaluate(s => {
        const ico = document.querySelector('.pl-attribution .lk-mark .lk-ico');
        ico.setAttribute('style', s);
      }, icoStyle(uri(file), '#f0a100'));
      await page.waitForTimeout(80);
      await page.locator('.pl-attribution').screenshot({ path: path.join(OUT, `credit-${theme}-${file.replace('.svg', '')}.png`) });
    }
    await ctx.close();

    // --- the card's link rows ---
    const p2 = await newPage(browser, theme + '-theme');
    await p2.page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
    await dismiss(p2.page);
    await p2.page.addStyleTag({ content: CARD_CSS });
    const row = (u, color, label, host, feature) =>
      `<a class="mk-link${feature ? ' feature' : ''}" href="#"><span class="lk-ico" style="${icoStyle(u, color)}"></span>${label}<span class="host">${host}</span></a>`;
    for (const [file] of SITE) {
      await p2.page.evaluate(html => {
        const card = document.querySelector('.ap-card, .mk-links');
        card.className = 'mk-links'; card.innerHTML = html;
      }, row(uri(file), '#f0a100', 'Site', 'fusspot.rip', true));
      await p2.page.waitForTimeout(80);
      await p2.page.locator('.mk-links').screenshot({ path: path.join(OUT, `card-${theme}-${file.replace('.svg', '')}.png`) });
    }
    for (const [fam, rows] of Object.entries(FAMILIES)) {
      await p2.page.evaluate(html => {
        const card = document.querySelector('.ap-card, .mk-links');
        card.className = 'mk-links'; card.innerHTML = html;
      }, rows.map(([f, c, l, h], i) => row(uri(f), c, l, h, i === 0)).join(''));
      await p2.page.waitForTimeout(120);
      await p2.page.locator('.mk-links').screenshot({ path: path.join(OUT, `family-${theme}-${fam}.png`) });
    }
    await p2.ctx.close();
    console.log('  ' + theme + ' done');
  }
  await browser.close();
})().catch(e => { console.error(e); process.exit(1); });
