import { test, expect } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady } from '../fixtures/test-utils';

async function passInterstitial(page: any) {
  const cont = page.getByText('Continue', { exact: true });
  await Promise.race([
    page.waitForSelector('#app', { timeout: 20000 }).catch(() => null),
    cont.first().waitFor({ state: 'visible', timeout: 20000 }).catch(() => null),
  ]);
  if (await cont.count().catch(() => 0)) { await cont.first().click().catch(() => {}); await page.waitForTimeout(4000); }
}
async function resolveConflictsIfAny(page: any) {
  const rename = page.getByText('RENAME ALL', { exact: false });
  if (await rename.count().catch(() => 0)) {
    await rename.first().click().catch(() => {});
    await page.waitForTimeout(2000);
    await page.getByText(/RESOLVE ALL/i).first().click().catch(() => {});
    await page.waitForTimeout(7000);
  }
}

test('probe: build Voidcaller (nameless trait) and inspect render wiring', async ({ page }) => {
  test.setTimeout(220000);
  const errs: string[] = [];
  page.on('pageerror', (e) => errs.push('PAGEERROR ' + String(e).slice(0, 300)));
  page.on('console', (m) => { if (m.type() === 'error') errs.push('CONSOLE ' + m.text().slice(0, 300)); });

  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('/');
  await passInterstitial(page);

  // import homebrew class with a level-1 trait that omits :name
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.locator('input[type="file"]').first()
    .setInputFiles(path.join(__dirname, '../fixtures/nameless-trait-class.orcbrew'));
  await page.waitForTimeout(6000);
  await resolveConflictsIfAny(page);

  // did the import land? search app-db for voidcaller
  const importInfo = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const db = c.deref(rf.db.app_db);
    const edn = c.pr_str(db);
    return {
      hasVoidcaller: /voidcaller/i.test(edn),
      voidContext: (edn.match(/.{0,80}voidcaller.{0,120}/i) || ['<none>'])[0],
      anyImportError: /invalid|error|missing|required/i.test((document.body.innerText || '').slice(0, 4000)) ? (document.body.innerText || '').slice(0, 500) : null,
    };
  });
  console.log('IMPORT=' + JSON.stringify(importInfo, null, 2));

  // build a character with it
  await page.goto('/pages/dnd/5e/character-builder');
  await waitForAppReady(page);
  await page.waitForTimeout(2000);
  const cookie = page.getByText('Got it', { exact: false });
  if (await cookie.count()) await cookie.first().click().catch(() => {});
  await page.getByText('Class / Level').first().click().catch(() => {});
  await page.waitForTimeout(1500);
  // pick the Voidcaller option (homebrew) in the class dropdown
  const dd = page.locator('select.builder-option-dropdown').first();
  const labels = await dd.locator('option').allTextContents();
  console.log('CLASS_LABELS=' + JSON.stringify(labels));
  const voidLabel = labels.find((l) => /voidcaller/i.test(l)) || 'Voidcaller';
  await dd.selectOption({ label: voidLabel }).catch(async () => {
    await dd.selectOption({ label: 'Voidcaller' }).catch(() => {});
  });
  await page.waitForTimeout(2500);
  console.log('SELECT_VALUE=' + JSON.stringify(await dd.inputValue().catch(() => '<err>')));
  console.log('SELECT_TEXT=' + JSON.stringify(await dd.locator('option:checked').textContent().catch(() => '<err>')));

  const probe = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame;
    const kw = (s: string) => c.keyword.call(null, s);
    const out: any = { steps: [] };
    try {
      const db = c.deref(rf.db.app_db);
      // top-level app-db keys
      out.dbKeys = c.pr_str(c.vec.call(null, c.keys.call(null, db))).slice(0, 600);
      // the working character + its db/id
      const character = c.get.call(null, db, kw('character'));
      const charEdn = c.pr_str(character);
      out.charLen = charEdn.length;
      out.charSample = charEdn.slice(0, 800);
      const idm = charEdn.match(/:db\/id (\d+)/);
      out.dbId = idm ? Number(idm[1]) : null;

      // CORRECT wiring: no-id traits sub -> :built-character (the builder's active char)
      try {
        const tsub = rf.core.subscribe.call(null, c.vector.call(null, kw('orcpub.dnd.e5.character/traits')));
        const tv = c.pr_str(c.deref(tsub));
        out.builderTraits = { len: tv.length, sample: tv.slice(0, 500), hasNoName: /\{[^}]*\}/.test(tv) && tv.includes(':level 1') };
      } catch (e: any) { out.builderTraitsErr = String(e).slice(0, 200); }

      // namespaces reachable?
      out.hasViews = !!(w.orcpub && w.orcpub.dnd && w.orcpub.dnd.e5 && w.orcpub.dnd.e5.views);
      out.hasReagentDom = !!(w.reagent && w.reagent.dom);
      const views = w.orcpub.dnd.e5.views;

      // Try to resolve the built character / traits subscription for a few candidate ids.
      const tryIds = [out.dbId, kw(':dnd/e5/builder'), kw('builder')].filter((x) => x !== null);
      out.subTries = [];
      const charNs = w.orcpub.dnd.e5.character;
      for (const id of tryIds) {
        try {
          const sub = rf.core.subscribe.call(null, c.vector.call(null, charNs && charNs.traits ? kw('orcpub.dnd.e5.character/traits') : kw('orcpub.dnd.e5.character/traits'), id));
          const v = c.deref(sub);
          const traitsEdn = c.pr_str(v);
          out.subTries.push({ id: c.pr_str(id), traitsLen: traitsEdn.length, hasNameless: /\{:level 1/.test(traitsEdn) && !/:name ""/.test(traitsEdn), sample: traitsEdn.slice(0, 300) });
        } catch (e: any) { out.subTries.push({ id: c.pr_str(id), err: String(e).slice(0, 150) }); }
      }

      // blank-feature-name? pure fn on nil/""/"x"
      if (views && views.blank_feature_name_QMARK_) {
        out.blankNil = views.blank_feature_name_QMARK_.call(null, null);
        out.blankEmpty = views.blank_feature_name_QMARK_.call(null, '');
        out.blankX = views.blank_feature_name_QMARK_.call(null, 'x');
      }
      // live aloof-sort-by over a nameless item (the exact crash path) — should NOT throw
      try {
        const coll = c.vector.call(null,
          c.hash_map.call(null, kw('name'), 'b'),
          c.hash_map.call(null, kw('level'), 1),            // no :name
          c.hash_map.call(null, kw('name'), 'a'));
        const sorted = w.orcpub.common.aloof_sort_by.call(null, kw('name'), coll);
        out.aloofSortOk = true;
        out.aloofSortLen = c.count.call(null, sorted);
      } catch (e: any) { out.aloofSortOk = false; out.aloofErr = String(e).slice(0, 200); }
    } catch (e: any) { out.fatal = String(e).slice(0, 400); }
    return out;
  });

  console.log('PROBE=' + JSON.stringify(probe, null, 2));
  console.log('ERRS=' + JSON.stringify(errs.slice(0, 20)));
  await page.screenshot({ path: 'test-results/black-screen-probe.png', fullPage: true }).catch(() => {});
});
