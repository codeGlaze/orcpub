// The character data page (/pages/dnd/5e/characters/<id>/data): it lists a saved character's
// stored choices with the app bundle BLOCKED, so it works when the app cannot open the character.
//
// A level-4 wizard is built by clicks with a homebrew race, feat and spell ("Tide Pak", the
// orphan-clear.js setup), saved, and the pack is switched off in My Content, the measured
// trapped-picks case (character-rescue.md). Then, with every request for orcpub.js aborted:
//   the owner sees the three choices on /data and on /repair, and Copy for support copies them;
//   another account and a logged-out visitor see "Only the owner can open this";
//   an unknown id says so; this browser's draft lists the same choices;
//   Remove takes a row out unsaved, Undo puts it back, a parent's Remove takes its children;
//   removing a class asks first, and Cancel keeps it;
//   a Save that finds the character changed since the page opened writes nothing and offers to
//   reload with the pending removals kept;
//   Save writes through the app's own save route: the feat is gone from the saved copy, the rest
//   and the summary stay, and the app then opens the character without listing the feat as
//   missing; on the draft page, Save writes the browser draft.
// Every change is a click, a select or the file input; app-db is never written.
//
// Needs the seeded server (kaylee / serenity99, zoe / washburne7) and both bundles:
//   lein fig:build && lein fig:ledger && lein garden once
//   DATOMIC_URL=datomic:mem://orcpub-e2e ORCPUB_ENV=dev SIGNATURE=e2e-test-signature PORT=8890 \
//   CSP_POLICY=none lein with-profile init-db run -m e2e-boot
//   NODE_PATH=/opt/node22/lib/node_modules node test/e2e/character-data.js
const { chromium } = require('playwright');
const { BASE, findChrome, checker, dbAt, dismissWhatsNew } = require('./lib');

const { PAK, wait, log, importPak, build, myContent, logIn } = require('./rescue-lib');

// A page that may not load the app: every request for orcpub.js is aborted and counted.
async function noAppPage(context) {
  const page = await context.newPage();
  page.appRequests = 0;
  await page.route('**/js/compiled/orcpub.js', r => { page.appRequests++; return r.abort(); });
  page.on('pageerror', e => log('   pageerror:', String(e).slice(0, 160)));
  return page;
}

// The data page's table as [section, choice, stored key] rows, once it has rendered.
async function dataRows(page, url) {
  await page.goto(url, { waitUntil: 'load' });
  await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
  return page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')]
    .map(tr => [...tr.children].slice(1, 4).map(td => td.textContent.trim())));
}
const listed = (rows, section, choice) => rows.some(r => r[0] === section && r[1] === choice);
const ledgerText = page => page.evaluate(() => document.getElementById('ledger').textContent);

(async () => {
  const { check, report } = checker();
  const browser = await chromium.launch({ executablePath: findChrome() });
  try {
    const owner = await browser.newContext({ viewport: { width: 1280, height: 1000 } });
    await owner.grantPermissions(['clipboard-read', 'clipboard-write'], { origin: BASE });
    const app = await owner.newPage();
    await logIn(app, 'kaylee', 'serenity99');
    await importPak(app);
    const id = await build(app);
    check('the character was saved', /^\d+$/.test(id), id);
    await myContent(app);
    await app.locator('.item-list-item .dev-mode-switch:not(.compact)').first().click();
    await wait(2000);
    check('the pack is switched off', /true/.test(await dbAt(app, `[:plugins "${PAK}" :disabled?]`)));

    const page = await noAppPage(owner);
    for (const route of ['data', 'repair']) {
      const rows = await dataRows(page, `${BASE}/pages/dnd/5e/characters/${id}/${route}`);
      check(`/${route} lists the homebrew race`, listed(rows, 'Race', 'Tidefolk'));
      check(`/${route} lists the homebrew feat`, listed(rows, 'Feats', 'Tidebreaker'));
      check(`/${route} lists the homebrew spell`, listed(rows, 'Wizard Spells Known', 'Brine Lash'));
      check(`/${route} lists the class's levels under it`, listed(rows, 'Levels', 'Level 4'));
    }
    await page.getByRole('button', { name: 'Copy for support' }).click();
    await wait(500);
    const copied = await page.evaluate(() => navigator.clipboard.readText());
    check('Copy for support names the character', copied.startsWith(`Character ${id}`), copied.split('\n')[0]);
    check('Copy for support lists the feat by its path', copied.split('\n').includes('Feats › Tidebreaker'));

    await page.goto(`${BASE}/pages/dnd/5e/characters/999999/data`, { waitUntil: 'load' });
    await page.waitForFunction(() => !/^Loading/.test(document.getElementById('ledger').textContent));
    check('an unknown id says so', /no saved character with this number/.test(await ledgerText(page)));

    const draftRows = await dataRows(page, `${BASE}/pages/dnd/5e/character-data`);
    check("this browser's draft lists the same feat", listed(draftRows, 'Feats', 'Tidebreaker'));
    check('the app bundle was never requested', page.appRequests === 0,
          `${page.appRequests} request(s) for orcpub.js, each aborted`);
    const html = await (await page.request.get(`${BASE}/pages/dnd/5e/characters/${id}/data`)).text();
    check('the page itself does not ask for the app bundle', !/orcpub\.js/.test(html));

    // ---- Remove, Undo, Save (saved character). The app tab leaves the builder first: an open
    // builder's autosave could write the removed pick back (character-rescue.md, Limits).
    await app.goto('about:blank');
    const dataUrl = `${BASE}/pages/dnd/5e/characters/${id}/data`;
    await dataRows(page, dataUrl);
    const rowButton = (choice) => page.locator('#ledger tbody tr', { has: page.locator('td', { hasText: new RegExp(`^${choice}$`) }) }).locator('button');
    await rowButton('Tidebreaker').click();
    let rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check('Remove takes the row out', !rows.includes('Tidebreaker'));
    check('the removal is listed as not saved yet', /Not saved yet: 1 removed/.test(await ledgerText(page)));
    await page.getByRole('button', { name: 'Undo' }).click();
    rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check('Undo puts it back', rows.includes('Tidebreaker') && !/Not saved yet/.test(await ledgerText(page)));
    const classButton = rowButton('Wizard');
    const classLabel = (await classButton.innerText()).trim();
    check("a parent's Remove says what goes with it", /^Remove with \d+ below$/.test(classLabel), classLabel);
    let asked = '';
    page.once('dialog', d => { asked = d.message(); d.dismiss(); });
    await classButton.click();
    await wait(300);
    check('removing a class asks first, saying what goes', /^Remove Wizard and the \d+ choices under it\? The builder will ask for a class again\.$/.test(asked), asked);
    rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check('Cancel keeps the class', rows.includes('Wizard') && rows.includes('Brine Lash'));
    page.once('dialog', d => d.accept());
    await classButton.click();
    await wait(300);
    rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check("OK removes the class with its children", !rows.includes('Brine Lash') && !rows.includes('Level 4'));
    await page.getByRole('button', { name: 'Undo' }).click();

    // ---- A save that finds the character changed since the page opened.
    await rowButton('Tidebreaker').click();
    const other = await noAppPage(owner);
    await dataRows(other, dataUrl);
    await other.locator('#ledger tbody tr', { has: other.locator('td', { hasText: /^Brine Lash$/ }) }).locator('button').click();
    await other.getByRole('button', { name: 'Save' }).click();
    await other.waitForFunction(() => /Saved\./.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    await other.close();
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForFunction(() => /changed since you opened/.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    const copy = await page.evaluate(async id => (await fetch(`/dnd/5e/characters/${id}`, { headers: { Accept: 'application/edn' } })).text(), id);
    check('a stale Save writes nothing', /:tidebreaker/.test(copy) && !/:brine-lash/.test(copy), 'the other tab\'s removal stands; this one is not written');
    check('and offers to reload keeping the removals', await page.getByRole('button', { name: 'Reload and keep my removals' }).isVisible());
    await page.getByRole('button', { name: 'Reload and keep my removals' }).click();
    rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check('the reload shows the other tab\'s change with this removal still pending',
          !rows.includes('Brine Lash') && !rows.includes('Tidebreaker') && /Not saved yet: 1 removed/.test(await ledgerText(page)));
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForFunction(() => /Saved\./.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    rows = await page.evaluate(() => [...document.querySelectorAll('#ledger tbody tr')].map(tr => tr.children[2].textContent.trim()));
    check('after Save the feat is gone and the rest stays', !rows.includes('Tidebreaker') && rows.includes('Tidefolk') && rows.includes('Wizard'));
    check('Save kept the same character', page.url() === dataUrl, page.url());
    const savedCopy = await page.evaluate(async id => (await fetch(`/dnd/5e/characters/${id}`, { headers: { Accept: 'application/edn' } })).text(), id);
    check('the saved copy has neither removal', !/:tidebreaker/.test(savedCopy) && !/:brine-lash/.test(savedCopy));
    check('the saved copy keeps the race', /:tidefolk/.test(savedCopy));
    check('the saved copy keeps its summary', /orcpub\.entity\.strict\/summary/.test(savedCopy));

    await app.goto(`${BASE}/pages/dnd/5e/characters/${id}`, { waitUntil: 'load' });
    await wait(4000);
    await dismissWhatsNew(app);
    await app.getByText('Edit', { exact: true }).first().click();
    await wait(3500);
    const warn = app.locator('#missing-content-warning');
    let missingText = '';
    if (await warn.count()) {
      await warn.locator('div.flex.align-items-c.pointer').first().click();
      await wait(600);
      missingText = await app.locator('#missing-content-details').innerText().catch(() => '');
    }
    check('the app opens the character and still lists the race as missing', /tidefolk/i.test(missingText), missingText.replace(/\n+/g, ' | ').slice(0, 160));
    check('the app no longer lists the feat as missing', !/tidebreaker/i.test(missingText));
    await app.goto('about:blank');

    // ---- Save on the draft page writes the browser draft.
    await dataRows(page, `${BASE}/pages/dnd/5e/character-data`);
    page.once('dialog', d => d.accept());
    await rowButton('Tidefolk').click();
    await page.getByRole('button', { name: 'Save' }).click();
    await page.waitForFunction(() => /Saved\./.test(document.getElementById('ledger').textContent), null, { timeout: 15000 });
    const draft = await page.evaluate(() => localStorage.getItem('character') || '');
    check('Save on the draft page writes the draft without the race', !/:tidefolk/.test(draft) && /:wizard/.test(draft));

    const zoe = await browser.newContext();
    const zoeApp = await zoe.newPage();
    await logIn(zoeApp, 'zoe', 'washburne7');
    const zoePage = await noAppPage(zoe);
    const zoeRows = await dataRows(zoePage, `${BASE}/pages/dnd/5e/characters/${id}/data`);
    check('another account sees no rows', zoeRows.length === 0, `${zoeRows.length} rows`);
    check('another account is told only the owner can open it', /Only the owner can open this/.test(await ledgerText(zoePage)));

    const anon = await browser.newContext();
    const anonPage = await noAppPage(anon);
    const anonRows = await dataRows(anonPage, `${BASE}/pages/dnd/5e/characters/${id}/data`);
    check('a logged-out visitor sees no rows', anonRows.length === 0);
    check('a logged-out visitor is offered Log in', await anonPage.getByRole('link', { name: 'Log in' }).isVisible());
  } catch (e) {
    check('the run finished', false, e.message.split('\n')[0]);
  } finally {
    await browser.close();
  }
  process.exit(report() ? 1 : 0);
})();
