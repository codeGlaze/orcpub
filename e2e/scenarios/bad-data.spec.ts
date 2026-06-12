import { test, expect } from '@playwright/test';
async function boot(page: any) {
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as any;
    return !!(w.cljs && w.reagent?.core?.as_element && w.ReactDOM && w.orcpub?.dnd?.e5?.views?.actions_section);
  }, { timeout: 30000 });
}
function mountActions(page: any, items: any) {
  return page.evaluate((spec: any) => {
    const w = window as any, c = w.cljs.core, V = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    const kw = (s: string) => c.keyword.call(null, s);
    const mk = (o: any) => { let m = c.hash_map.call(null); for (const k in o) { const v = o[k]; m = c.assoc.call(null, m, kw(k), v); } return m; };
    const actions = c.apply.call(null, c.vector, (spec as any[]).map(mk));
    const tree = c.vector.call(null, V.actions_section, 'bd', 'Features, Traits, and Feats', 'vitruvian-man', actions);
    const d = document.createElement('div'); d.id = 'bd'; document.body.innerHTML = ''; document.body.appendChild(d);
    w.ReactDOM.render(rc.as_element.call(null, tree), d);
  }, items);
}

// number where a NAME (string) is expected -> the sort's lower-case crashes -> caught + isolated
test('number-as-name -> crashes the sort, auditor isolates it', async ({ page }) => {
  await boot(page);
  await mountActions(page, [{ name: 'Good Trait', summary: 'fine' }, { name: 42, summary: 'name is a number' }]);
  const t = page.locator('#bd');
  await expect(t).toContainText('couldn’t be displayed');   // caught, not black-screen
  await expect(t).toContainText('name is a number');        // the offending item surfaced
  await expect(t).toContainText('Good Trait');              // survivor rendered
  console.log('NUM_NAME=' + JSON.stringify((await t.innerText()).slice(0, 240)));
});

// wrong types in NON-string-op fields (level/page as strings) -> tolerated, renders fine
test('string where number expected (level/page) -> tolerated, no crash', async ({ page }) => {
  await boot(page);
  await mountActions(page, [{ name: 'Odd Types', level: 'three', page: 'ninety-two', summary: 'weird but harmless' }]);
  const t = page.locator('#bd');
  await expect(t).toContainText('Odd Types');               // rendered normally
  await expect(t).not.toContainText('couldn’t be displayed');
  console.log('ODD_TYPES=' + JSON.stringify((await t.innerText()).slice(0, 200)));
});
