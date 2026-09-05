// Does an IMPORTED homebrew fighting style actually work on a character?
//
// The round-trip test proves storage: authored -> saved -> exported -> re-imported. It does NOT
// prove the imported content is usable. This one goes the last mile: import a style, build a
// Fighter, pick the style from the real selection list, and assert the character's AC moves by the
// amount the style declares.
//
// Prereqs:  lein fig:build && lein garden once && lein e2e-server
const fs = require('fs'); const path = require('path'); const os = require('os');
const { chromium } = require('playwright');
const BASE = process.env.E2E_BASE || 'http://localhost:8890';
const SHOTS = path.resolve(__dirname, '../../target/e2e-shots');
function findChrome(){const b=process.env.PLAYWRIGHT_BROWSERS_PATH||'/opt/pw-browsers';
  try{const d=fs.readdirSync(b).filter(x=>x.startsWith('chromium-')&&!x.includes('headless')).sort().pop();
  if(d){const p=path.join(b,d,'chrome-linux','chrome');if(fs.existsSync(p))return p;}}catch(_){}}
const results=[]; const check=(n,ok,d='')=>{results.push({n,ok});console.log(`${ok?'PASS':'FAIL'}  ${n}${d?'  — '+d:''}`);};
const dbAt=(p,q)=>p.evaluate(x=>{try{const v=window.cljs.core.get_in.call(null,
  window.cljs.core.deref.call(null,window.re_frame.db.app_db),window.cljs.reader.read_string.call(null,x));
  return window.cljs.core.pr_str.call(null,v);}catch(e){return 'ERR '+e.message;}},q);

// The .orcbrew we import. Two styles, because two things need proving:
//   :bulwark  — no :classes, so every fighting-style class may take it; +1 AC unconditionally,
//               so the sheet's number has to move (an armor-gated bonus would prove nothing on
//               an unarmored character, and the tag itself is pinned by the JVM AC tests).
//   :oathkeep — :classes #{:paladin}, so a FIGHTER must not be offered it. That is the divvying
//               rule from fighting-style-authoring.md, checked through the real UI.
const ORCBREW = `{"Usable Source" {:orcpub.dnd.e5/fighting-styles
  {:bulwark {:name "Bulwark" :key :bulwark :option-pack "Usable Source"
             :props {:ac-bonus {:bonus 1}}}
   :oathkeep {:name "Oathkeep" :key :oathkeep :option-pack "Usable Source"
              :classes #{:paladin}
              :props {:ac-bonus {:bonus 1}}}}}}`;

// The on-screen Armor Class, read from the builder's summary. Returns a number or null.
const acOnScreen = (page) => page.evaluate(() => {
  const vis = e => { const r = e.getBoundingClientRect(); return r.width > 0 && r.height > 0; };
  const label = [...document.querySelectorAll('div,span')]
    .filter(e => e.children.length === 0 && /^armor class$/i.test(e.textContent.trim()) && vis(e))[0];
  if (!label) return null;
  for (let n = label.parentElement, i = 0; n && i < 4; n = n.parentElement, i++) {
    const m = n.textContent.replace(/armor class/i, '').match(/\d+/);
    if (m) return Number(m[0]);
  }
  return null;
});

(async () => {
  fs.mkdirSync(SHOTS,{recursive:true});
  const file = path.join(os.tmpdir(), 'usable.orcbrew');
  fs.writeFileSync(file, ORCBREW);
  const browser = await chromium.launch({ executablePath: findChrome() });
  const ctx = await browser.newContext({ acceptDownloads:true, viewport:{width:1400,height:1200} });
  const page = await ctx.newPage();
  const errors=[]; page.on('pageerror',e=>errors.push(String(e)));
  page.on('console',m=>{if(m.type()==='error'&&!/ERR_(CONNECTION|NAME|INTERNET)/.test(m.text()))errors.push(m.text());});

  try {
    // import through the real file input
    await page.goto(`${BASE}/dnd/5e/my-content`, { waitUntil:'networkidle' });
    await page.waitForTimeout(2500);
    const fi = await page.$('input[type=file]');
    check('found the import file input', !!fi);
    await fi.setInputFiles(file);
    await page.waitForTimeout(2500);
    const plugins = await dbAt(page, '[:plugins "Usable Source" :orcpub.dnd.e5/fighting-styles]');
    check('imported styles are in :plugins',
          /bulwark/i.test(plugins) && /oathkeep/i.test(plugins), plugins.slice(0,140));

    // build a Fighter — fighting styles are a level-1 Fighter selection
    await page.goto(`${BASE}/pages/dnd/5e/character-builder`, { waitUntil:'networkidle' });
    await page.waitForTimeout(3500);
    await page.screenshot({ path: path.join(SHOTS,'usable-01-builder.png'), fullPage:true });

    // The builder opens on the RACE tab (and seeds a default class), so navigate to Class / Level
    // before looking for Fighter.
    const onClassTab = await page.evaluate(() => {
      const vis = e => { const r=e.getBoundingClientRect(); return r.width>0&&r.height>0; };
      const el = [...document.querySelectorAll('div,span,button')]
        .filter(e => e.children.length<=2 && /^class\s*\/\s*level$/i.test(e.textContent.trim()) && vis(e))
        .sort((a,b)=>a.textContent.length-b.textContent.length)[0];
      if (el) { el.click(); return true; } return false;
    });
    check('navigated to the Class / Level tab', onClassTab);
    await page.waitForTimeout(2500);

    // Class is a <select>, not clickable cards, and the builder seeds Barbarian by default.
    let pickedClass = false;
    for (const sel of await page.$$('select')) {
      const opts = await sel.evaluate(el => [...el.options].map(o => o.textContent.trim()));
      if (opts.some(o => /^fighter$/i.test(o))) {
        await sel.selectOption({ label: opts.find(o => /^fighter$/i.test(o)) });
        pickedClass = true; break;
      }
    }
    check('selected the Fighter class in the builder', pickedClass);
    await page.waitForTimeout(2500);
    await page.screenshot({ path: path.join(SHOTS,'usable-02-fighter.png'), fullPage:true });

    await page.waitForTimeout(1500);
    const body = await page.textContent('body');
    check('the Fighter Fighting Style selection renders with the SRD styles',
      /fighting style/i.test(body) && /archery/i.test(body) && /great weapon fighting/i.test(body));

    // CHARACTERIZATION of a real gap, not a passing feature.
    //
    // classes.cljc:1119 builds the Fighter's selection with opt5e/fighting-style-selection, which
    // reads the STATIC opt5e/fighting-style-options — the six SRD styles. The homebrew-inclusive
    // FLIPPED 2026-09-05 (E2). This block used to pin the gap: the class selection read the
    // static SRD list, so an imported style was saveable, exportable and importable but no
    // Fighter could pick it. fighting-style-selection now concatenates the homebrew pool,
    // divvied by :classes (absent = every fighting-style class) — see
    // docs/kb/fighting-style-authoring.md and fighting_style_class_eligibility_test.
    const offered = /bulwark/i.test(body);
    check('imported homebrew style IS offered by the Fighter class selection', offered,
      'the class selection now reads the homebrew-inclusive pool');
    check('but a style restricted to :paladin is NOT offered to a Fighter',
      !/oathkeep/i.test(body), 'the :classes divvying rule, through the real UI');
    await page.screenshot({ path: path.join(SHOTS,'usable-03-selection.png'), fullPage:true });

    {
      const before = await dbAt(page, '[:character :orcpub.entity/options :class]');
      const acBefore = await acOnScreen(page);
      // The option is a card: the text node is inside it, and the click handler is on an
      // ancestor. Clicking the text node itself does nothing — that is what made this check fail
      // once the style finally appeared.
      await page.locator('#app :text("Bulwark")').first().click();
      await page.waitForTimeout(2500);
      const after = await dbAt(page, '[:character :orcpub.entity/options :class]');
      check('selecting it records the choice on the character', after !== before && /bulwark/i.test(after),
            after.slice(0,160));
      // The last mile: the authored number reaches the sheet. Anything less proves only that the
      // option was clickable.
      const acAfter = await acOnScreen(page);
      check('and the authored +1 AC reaches the on-screen Armor Class',
            acBefore !== null && acAfter === acBefore + 1, `before ${acBefore} -> after ${acAfter}`);
      await page.screenshot({ path: path.join(SHOTS,'usable-03-selected.png'), fullPage:true });
    }
    check('no uncaught JS errors', errors.length===0, errors.slice(0,2).join(' | '));
  } catch (e) { check('ran to completion', false, e.message); }
  finally { await browser.close(); }
  const failed=results.filter(r=>!r.ok);
  console.log(`\nscreenshots: ${SHOTS}`);
  console.log(`${results.length-failed.length}/${results.length} checks passed`);
  process.exit(failed.length?1:0);
})();
