// Every PDF option, and Create PDF below them, can be scrolled into view and tapped.
//
//   ./scripts/e2e/run.sh pdf-options-reach.js
//
// Builds a Wizard with spells (so the Cards and Spellbook groups show), opens PDF
// Options, picks a sheet style and opens the Spellbook group, its tallest state.
// Then, on a 1280x720 desktop and a 390x844 phone, it scrolls the page just far
// enough to bring Create PDF into view and checks the button sits inside the window
// and is the topmost element at its centre. The scroll stops well short of the end
// of the page, where the header's containing block runs out and anything shows.

const { chromium } = require('playwright');
const { settle, clickVisible, firstVisible, setClasses, selectSpells,
        BASE, EXECUTABLE } = require('./export-character-pdf');

let failures = 0;
function check(label, ok, detail) {
  if (!ok) failures++;
  console.log(`${ok ? 'PASS' : 'FAIL'}  ${label}${detail !== undefined && !ok ? '  — ' + detail : ''}`);
}

// Scrolls so Create PDF's bottom lands 20px above the window's, then reports where
// it is: {fits, onTop, short, detail}. `short` is false when that scroll reaches
// the end of the page, which would let a clipped panel pass.
async function reachCreatePdf(page) {
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.waitForTimeout(300);
  return page.evaluate(async () => {
    const b = Array.from(document.querySelectorAll('button'))
      .find(x => x.textContent.trim() === 'Create PDF');
    if (!b) return { fits: false, onTop: false, short: false, detail: 'no Create PDF button' };
    const want = Math.max(0, b.getBoundingClientRect().bottom + scrollY - innerHeight + 20);
    window.scrollTo(0, want);
    await new Promise(r => requestAnimationFrame(() => requestAnimationFrame(r)));
    const r = b.getBoundingClientRect();
    const hit = document.elementFromPoint(r.left + r.width / 2, Math.min(r.top + r.height / 2, innerHeight - 1));
    return {
      fits: r.top >= 0 && r.bottom <= innerHeight,
      onTop: !!hit && (hit === b || b.contains(hit)),
      short: scrollY + innerHeight < document.documentElement.scrollHeight - 200,
      detail: `button ${Math.round(r.top)}-${Math.round(r.bottom)} in a ${innerHeight}px window at scrollY ${Math.round(scrollY)}`,
    };
  });
}

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const context = await browser.newContext({ viewport: { width: 1280, height: 720 } });
  const page = await context.newPage();
  await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil: 'networkidle', timeout: 60000 });
  await settle(page);
  const quiet = (ok, m) => { if (!ok) check(m, false); };
  await setClasses(page, [['Wizard', '5']], quiet);
  await selectSpells(page, 4, quiet);

  check('Export opens PDF Options', await clickVisible(page, 'text=Export'));
  await settle(page);
  const styles = await firstVisible(page, 'select');
  check('the panel offers a sheet style', styles !== null);
  if (styles) await styles.selectOption({ index: 1 });
  check('Print Spellbook is offered', await clickVisible(page, 'text=Print Spellbook'));
  await settle(page);

  for (const [label, size] of [['desktop 1280x720', { width: 1280, height: 720 }],
                               ['phone 390x844', { width: 390, height: 844 }]]) {
    await page.setViewportSize(size);
    await settle(page, 400);
    const r = await reachCreatePdf(page);
    check(`${label}: the check stops short of the page end`, r.short, r.detail);
    check(`${label}: Create PDF scrolls fully into the window`, r.fits, r.detail);
    check(`${label}: Create PDF is what a tap there hits`, r.onTop, r.detail);
  }

  await browser.close();
  console.log(`\n${failures ? failures + ' FAILED' : 'all passed'}`);
  process.exit(failures ? 1 : 0);
})().catch(e => { console.error(e); process.exit(2); });
