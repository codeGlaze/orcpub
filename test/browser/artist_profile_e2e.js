// Artist profile pages, driven in the running app.
//
// /artists/<slug> is the site's showcase of a portrait artist. This checks the
// page in both themes, the share tags the server sets for it, the 404 for a
// slug nobody has, the /artists list, and the drawer credit's secondary link --
// and that the credit's NAME still goes to the artist's own link.
//
//   lein fig:build
//   lein garden once
//   lein e2e-server
//   node test/browser/artist_profile_e2e.js
//
// Set ORCPUB_SHOTS=dir to save screenshots of each state into dir.
//
// Exits non-zero if any check fails.

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { suppressOverlays } = require('./lib/orcbrew-import');

const BASE = process.env.ORCPUB_BASE || 'http://localhost:8890';
const SHOTS = process.env.ORCPUB_SHOTS;

async function clickIfVisible(locator, { timeout = 2500 } = {}) {
  try { await locator.click({ timeout }); return true; }
  catch (_) { return false; }
}

function findChrome() {
  if (process.env.ORCPUB_CHROME) return process.env.ORCPUB_CHROME;
  const base = process.env.PLAYWRIGHT_BROWSERS_PATH || '/opt/pw-browsers';
  try {
    const dir = fs.readdirSync(base)
      .filter(d => d.startsWith('chromium-') && !d.includes('headless')).sort().pop();
    if (dir) {
      const p = path.join(base, dir, 'chrome-linux', 'chrome');
      if (fs.existsSync(p)) return p;
    }
  } catch (_) {}
  return undefined;
}

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail && !ok ? '  — ' + detail : ''}`);
}

async function shot(page, name, opts = {}) {
  if (!SHOTS) return;
  fs.mkdirSync(SHOTS, { recursive: true });
  const file = path.join(SHOTS, name);
  await page.screenshot({ path: file, ...opts });
  console.log(`  screenshot -> ${file}`);
}

const EMAIL = /[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/;

// The theme lives in the `user` localStorage entry, as EDN. Seeding it before
// the app boots is what the builder's own toggle would have written.
async function themedContext(browser, theme, viewport = { width: 1280, height: 900 }) {
  const ctx = await browser.newContext({ viewport });
  await suppressOverlays(ctx);
  if (theme) {
    await ctx.addInitScript(t => {
      try { localStorage.setItem('user', `{:theme "${t}"}`); } catch (_) {}
    }, theme);
  }
  return ctx;
}

function watch(page) {
  const jsErrors = [];
  const bad = [];
  const IGNORABLE = /styles\.css|fonts\.googleapis|fonts\.gstatic|figwheel|favicon/i;
  page.on('pageerror', e => jsErrors.push(String(e)));
  page.on('response', r => {
    if (r.status() >= 400 && !IGNORABLE.test(r.url())) bad.push(`${r.status()} ${r.url()}`);
  });
  return { jsErrors, bad };
}

async function dismissCookies(page) {
  const btn = page.locator('#cookie-btn');
  if (await btn.count()) { await clickIfVisible(btn); await page.waitForTimeout(150); }
}

async function checkProfile(page, themeLabel) {
  await page.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.ap-card', { timeout: 30000 });
  await dismissCookies(page);
  // The composite is CSS masks over PNGs; give them a moment to paint.
  await page.waitForTimeout(500);

  const name = page.locator('h1.ap-name');
  check(`[${themeLabel}] the artist's name heads the page`,
        (await name.textContent()).trim() === 'Fusspot');
  const font = await name.evaluate(el => {
    const s = getComputedStyle(el); return `${s.fontStyle} ${s.fontFamily}`;
  });
  check(`[${themeLabel}] the name is set in Vollkorn italic, like the credit`,
        /italic/.test(font) && /Vollkorn/.test(font), font);

  const hrefs = await page.locator('.ap-link').evaluateAll(as => as.map(a => a.href));
  check(`[${themeLabel}] the links are exactly the two she listed, in her order`,
        JSON.stringify(hrefs) === JSON.stringify(['https://fusspot.rip/', 'https://www.twitch.tv/fusspot']),
        JSON.stringify(hrefs));
  const targets = await page.locator('.ap-link').evaluateAll(as => as.map(a => a.target));
  check(`[${themeLabel}] they open in a new tab`, targets.every(t => t === '_blank'), JSON.stringify(targets));

  const examples = page.locator('.ap-example');
  check(`[${themeLabel}] three example portraits`, await examples.count() === 3,
        `saw ${await examples.count()}`);
  const layered = await examples.evaluateAll(fs =>
    fs.map(f => f.querySelectorAll('.portrait-layer').length));
  check(`[${themeLabel}] every example is composed`, layered.every(n => n > 0), JSON.stringify(layered));
  check(`[${themeLabel}] a single-artist example credits nobody else`,
        await page.locator('.ap-example figcaption').count() === 0);

  // The page is easy to scrape, so it never lists the art piece by piece: the only
  // pictures are the composed examples, drawn as CSS layers rather than <img> files.
  const pieceImgs = await page.locator('.ap-root img[src*="/image/portraits/"]').count();
  check(`[${themeLabel}] no piece of art is listed on its own`, pieceImgs === 0, `img=${pieceImgs}`);
  const lede = (await page.locator('.ap-lede').textContent()).trim();
  check(`[${themeLabel}] the lede still says how many pieces she drew`,
        /^\d+ hand-drawn pieces? in the portrait maker\.$/.test(lede), lede);

  const text = await page.locator('.ap-root').innerText();
  check(`[${themeLabel}] no email address anywhere on the page`, !EMAIL.test(text));
  check(`[${themeLabel}] no mailto: link anywhere on the page`,
        await page.locator('a[href^="mailto:"]').count() === 0);

  const root = page.locator('.pl-root.ap-root');
  const isLight = await root.evaluate(el => el.classList.contains('light-theme'));
  const cardBg = await page.locator('.ap-card').evaluate(el => getComputedStyle(el).backgroundColor);
  const frameBg = await page.locator('.ap-frame').first().evaluate(el => getComputedStyle(el).backgroundImage);
  return { isLight, cardBg, frameBg };
}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  console.log(`\nartist profile e2e -- ${BASE}\n`);

  // --- server: share tags, og image, 404 -------------------------------
  {
    const ctx = await themedContext(browser, null);
    const req = ctx.request;
    const html = await (await req.get(`${BASE}/artists/fusspot`)).text();
    check('share card is titled for the artist', html.includes('Fusspot — portrait artist'));
    check('og:image is the page\'s lead portrait',
          /<meta(?=[^>]*property="og:image")(?=[^>]*content="[^"]*\/artists\/fusspot\/portrait\.png")[^>]*>/.test(html));
    const png = await req.get(`${BASE}/artists/fusspot/portrait.png`);
    check('the og image is served as a PNG',
          png.status() === 200 && png.headers()['content-type'] === 'image/png',
          `${png.status()} ${png.headers()['content-type']}`);
    const missing = await req.get(`${BASE}/artists/nobody`);
    check('a slug nobody has is a 404', missing.status() === 404, `${missing.status()}`);
    await ctx.close();
  }

  // --- the profile, dark then light ------------------------------------
  const dark = await themedContext(browser, 'dark-theme');
  const dp = await dark.newPage();
  const dw = watch(dp);
  const d = await checkProfile(dp, 'dark');
  check('[dark] the page wears the dark theme', !d.isLight);
  await shot(dp, 'profile-dark.png', { fullPage: true });

  const light = await themedContext(browser, 'light-theme');
  const lp = await light.newPage();
  const lw = watch(lp);
  const l = await checkProfile(lp, 'light');
  check('[light] the page wears the light theme', l.isLight);
  check('the card repaints between themes', d.cardBg !== l.cardBg, `dark=${d.cardBg} light=${l.cardBg}`);
  check('the portrait frames stay dark in both themes', d.frameBg === l.frameBg);
  await shot(lp, 'profile-light.png', { fullPage: true });

  // --- a phone ---------------------------------------------------------
  const phone = await themedContext(browser, 'dark-theme', { width: 390, height: 844 });
  const pp = await phone.newPage();
  await pp.goto(`${BASE}/artists/fusspot`, { waitUntil: 'networkidle' });
  await pp.waitForSelector('.ap-card', { timeout: 30000 });
  await dismissCookies(pp);
  await pp.waitForTimeout(400);
  const overflow = await pp.evaluate(() => document.documentElement.scrollWidth - window.innerWidth);
  check('no sideways scroll at phone width', overflow <= 0, `overflow=${overflow}px`);
  await shot(pp, 'profile-phone.png', { fullPage: true });
  await phone.close();

  // --- nobody here, and the list ---------------------------------------
  await lp.goto(`${BASE}/artists/nobody`, { waitUntil: 'networkidle' });
  await lp.waitForSelector('.ap-card', { timeout: 30000 });
  check('an unknown slug says so and points at the list',
        await lp.locator('.ap-missing a[href="/artists"]').count() === 1);

  await dp.goto(`${BASE}/artists`, { waitUntil: 'networkidle' });
  await dp.waitForSelector('.ap-index', { timeout: 30000 });
  await dp.waitForTimeout(400);
  const cards = await dp.locator('.ap-index-card').evaluateAll(as => as.map(a => a.getAttribute('href')));
  check('the list links each artist to their profile',
        JSON.stringify(cards) === JSON.stringify(['/artists/fusspot']), JSON.stringify(cards));
  await shot(dp, 'index-dark.png', { fullPage: true });

  // --- the drawer credit -----------------------------------------------
  for (const [label, pg] of [['dark', dp], ['light', lp]]) {
    await pg.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle' });
    await pg.waitForSelector('#app', { timeout: 30000 });
    await dismissCookies(pg);
    const descTab = pg.locator('.builder-tab', { hasText: /^Description$/ }).first();
    if (await descTab.count()) { await descTab.click(); await pg.waitForTimeout(300); }
    await pg.locator('.pl-launcher').click();
    await pg.locator('.pl-drawer').waitFor({ state: 'visible', timeout: 10000 });
    await pg.locator('.pl-btn-primary', { hasText: 'Randomize' }).click();
    // Composing grows the credit by a line, which slides the new link under
    // the cursor left where Randomize was -- and a hovered link is lit.
    await pg.mouse.move(5, 5);
    await pg.waitForTimeout(500);

    const nameHref = await pg.locator('.pl-attribution a.lk-name').first().getAttribute('href');
    check(`[${label}] the credit's name still goes to the artist's own link`,
          nameHref === 'https://fusspot.rip/', nameHref);
    const about = pg.locator('.pl-attribution a.lk-about');
    check(`[${label}] the credit carries one link to the profile`, await about.count() === 1);
    check(`[${label}] it goes to her profile, in a new tab`,
          (await about.getAttribute('href')) === '/artists/fusspot' &&
          (await about.getAttribute('target')) === '_blank');
    check(`[${label}] it says what it is`, /About the artist/i.test(await about.innerText()));
    const aboutColor = await about.evaluate(el => getComputedStyle(el).color);
    const capColor = await pg.locator('.pl-attribution .lk-cap').evaluate(el => getComputedStyle(el).color);
    check(`[${label}] it is as quiet as the ART BY label above it`, aboutColor === capColor,
          `about=${aboutColor} cap=${capColor}`);
    await shot(pg, `drawer-credit-${label}.png`, { clip: await pg.locator('.pl-canvas-side').boundingBox() });
    await about.hover();
    await pg.waitForTimeout(250);
    await shot(pg, `drawer-credit-${label}-hover.png`,
               { clip: await pg.locator('.pl-attribution').boundingBox() });
  }

  check('no page errors', dw.jsErrors.length + lw.jsErrors.length === 0,
        [...dw.jsErrors, ...lw.jsErrors].join(' | '));
  // /artists/nobody is SUPPOSED to 404; anything else is not.
  const bad = [...dw.bad, ...lw.bad].filter(b => !/\/artists\/nobody$/.test(b));
  check('no failed app resources', bad.length === 0, bad.join(' | '));

  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
