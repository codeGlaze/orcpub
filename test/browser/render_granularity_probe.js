// Measures re-render SCOPE while typing in a builder form.
//
// Counts DOM mutations under the whole page vs under the form container for N keystrokes. If the
// form's subscription deref registers in the enclosing page's reactive context, a keystroke
// re-renders the whole page and mutations appear well outside the form. If it registers in the
// form's own context, mutations stay local.
//
// Run:  node test/browser/render_granularity_probe.js
const fs = require('fs'); const path = require('path');
const { chromium } = require('playwright');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';
  try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();
  if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}}

(async () => {
  const browser = await chromium.launch({ executablePath: findChrome() });
  const page = await browser.newPage({ viewport: { width: 1400, height: 1200 } });
  await page.goto(`${BASE}/pages/dnd/5e/fighting-style-builder`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(2500);

  // Arm two observers: one on the whole app, one on the subtree that holds the form inputs.
  await page.evaluate(() => {
    window.__m = { all: 0, form: 0 };
    const app = document.body;
    const anInput = document.querySelector('input[type=number], input[type=text]');
    // climb to the container that holds several inputs — the form subtree
    let form = anInput;
    while (form && form.querySelectorAll('input, textarea, select').length < 3) form = form.parentElement;
    window.__formEl = form || app;
    new MutationObserver(r => { window.__m.all += r.length; })
      .observe(app, { subtree: true, childList: true, attributes: true, characterData: true });
    new MutationObserver(r => { window.__m.form += r.length; })
      .observe(window.__formEl, { subtree: true, childList: true, attributes: true, characterData: true });
  });

  const nameInput = await page.$('input[type=text]:not([type=hidden])');
  const inputs = await page.$$('input[type=text]');
  const target = inputs[1] || inputs[0];          // the builder's Name field
  await target.click();
  const t0 = Date.now();
  await target.type('Bulwarkiness', { delay: 40 });   // 12 keystrokes
  await page.waitForTimeout(1200);
  const ms = Date.now() - t0;

  const m = await page.evaluate(() => ({ ...window.__m,
    formShare: window.__m.all ? Math.round(100 * window.__m.form / window.__m.all) : 0,
    outsideForm: window.__m.all - window.__m.form }));
  console.log(`typed 12 chars in ${ms}ms`);
  console.log(`  mutations page-wide : ${m.all}`);
  console.log(`  mutations in form   : ${m.form}`);
  console.log(`  OUTSIDE the form    : ${m.outsideForm}  (${100 - m.formShare}% of all)`);
  await browser.close();
})();
