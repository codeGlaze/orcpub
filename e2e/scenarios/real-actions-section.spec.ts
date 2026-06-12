import { test, expect } from '@playwright/test';

/**
 * Run the REAL actions-section with the REAL Evasion trait map taken verbatim from
 * classes.cljc (no :name via temp-revert; only :page + :summary). The reverted
 * aloof-sort-by throws on the nil name; the wired-in auditor must isolate THAT
 * actual item by re-execution and surface its real data — with locate-hint showing
 * only the fields it truly has (here: just the page).
 */
test('real actions-section isolates the real nameless Evasion trait', async ({ page }) => {
  test.setTimeout(90000);
  await page.setViewportSize({ width: 1000, height: 900 });
  await page.goto('/');
  await page.waitForFunction(() => {
    const w = window as any;
    return !!(w.cljs && w.reagent && w.reagent.core && w.reagent.core.as_element && w.ReactDOM
      && w.orcpub && w.orcpub.dnd.e5.views && w.orcpub.dnd.e5.views.actions_section);
  }, { timeout: 30000 });

  const out = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, views = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    const kw = (s: string) => c.keyword.call(null, s);
    // the actual Evasion trait-cfg from classes.cljc, post temp-revert: no :name.
    const evasion = c.hash_map.call(null,
      kw('page'), 93,
      kw('summary'), 'When you are subjected to an effect, such as a red dragon’s fiery breath or a lightning bolt spell, that allows you to make a Dexterity saving throw to take only half damage, you instead take no damage if you succeed on the saving throw, and only half damage if you fail.');
    const whirlwind = c.hash_map.call(null,
      kw('name'), 'Whirlwind Attack', kw('page'), 93,
      kw('summary'), 'You can use your action to make a melee attack against any number of creatures within 5 feet of you.');
    const actions = c.vector.call(null, whirlwind, evasion);
    const tree = c.vector.call(null, views.actions_section, 'demo-id', 'Features, Traits, and Feats', 'vitruvian-man', actions);
    const div = document.createElement('div');
    div.id = 'real-section';
    (div as any).style = 'max-width:900px;margin:24px auto;color:#222;background:#fff;padding:12px;';
    document.body.innerHTML = '';
    document.body.appendChild(div);
    w.ReactDOM.render(rc.as_element.call(null, tree), div);
    return true;
  });
  expect(out).toBe(true);

  const sec = page.locator('#real-section');
  await expect(sec).toContainText('A feature in this section couldn’t be displayed'); // exactly ONE culprit
  await expect(sec).not.toContainText('2 features');            // Whirlwind must NOT be flagged
  await expect(sec).toContainText('Whirlwind Attack');          // it's a survivor, rendered normally
  await expect(sec).toContainText('red dragon');                // the real Evasion summary is the culprit
  const text = await sec.innerText();
  console.log('REAL_SECTION=' + JSON.stringify(text.slice(0, 700)));
  await page.screenshot({ path: 'test-results/real-actions-section.png' });
});
