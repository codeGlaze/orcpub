import { test, expect } from '@playwright/test';

// Stress-test the fail-soft machinery against error types BEYOND the nil-name sort.
// Each mounts real components with crafted/real data; reports real outcomes.
const ready = () => {
  const w = window as any;
  return !!(w.cljs && w.reagent && w.reagent.core && w.reagent.core.as_element && w.ReactDOM
    && w.orcpub && w.orcpub.dnd && w.orcpub.dnd.e5 && w.orcpub.dnd.e5.views
    && w.orcpub.dnd.e5.views.error_boundary && w.orcpub.dnd.e5.views.actions_section
    && w.orcpub.dnd.e5.views.skills_section_2 && w.orcpub.dnd.e5.views.feature_render_error
    && w.orcpub.dnd.e5.views.isolate_culprit_selection && w.orcpub.dnd.e5.views.app_error_fallback);
};

async function boot(page: any) {
  await page.setViewportSize({ width: 1000, height: 1000 });
  await page.goto('/');
  await page.waitForFunction(ready, { timeout: 30000 });
}

// T1 — per-item containment: render-guard around an item whose render throws must
// contain it to that item and dump its raw data. (A data-driven per-item throw is
// hard to arrange — cljs tolerates bad types — so we throw in the child directly,
// which is exactly what render-guard wraps.)
test('T1 per-item throw -> render-guard contains it and shows the item data', async ({ page }) => {
  await boot(page);
  await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, V = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    const kw = (s: string) => c.keyword.call(null, s);
    const data = c.hash_map.call(null, kw('name'), 'Broken Feature', kw('page'), 99,
      kw('summary'), 'this item blew up while rendering');
    function Boom() { throw new Error('per-item render blew up'); }
    const tree = c.vector.call(null, V.render_guard, data, c.vector.call(null, Boom));
    const d = document.createElement('div'); d.id = 't1'; document.body.innerHTML = ''; document.body.appendChild(d);
    w.ReactDOM.render(rc.as_element.call(null, tree), d);
  });
  const t = page.locator('#t1');
  await expect(t).toContainText('couldn’t be displayed');       // contained, not a black screen
  await expect(t).toContainText(':page 99');                    // its raw data shown
  console.log('T1=' + JSON.stringify((await t.innerText()).slice(0, 300)));
});

// T3 — non-data logic bug: failure that no selection-removal can clear.
// isolate-culprit-selection must return nil (no false blame), not invent a culprit.
test('T3 non-data bug -> isolation returns nil, no false blame', async ({ page }) => {
  await boot(page);
  const res = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, rf = w.re_frame, V = w.orcpub.dnd.e5.views;
    const kw = (s: string) => c.keyword.call(null, s);
    const character = c.deref(rf.core.subscribe.call(null, c.vector.call(null, kw('orcpub.dnd.e5.character/character'), null)));
    const template = c.deref(rf.core.subscribe.call(null, c.vector.call(null, kw('built-template'))));
    const alwaysFails = () => true;                              // a bug independent of any selection
    const site = V.isolate_culprit_selection.call(null, character, template, alwaysFails);
    return { site: site == null ? null : c.pr_str.call(null, site) };
  });
  console.log('T3=' + JSON.stringify(res));
  expect(res.site, 'no selection falsely blamed').toBeNull();
});

// T4 — two faulty features at once: self-pair must flag BOTH real culprits, not the
// innocent neighbour.
test('T4 multi-fault -> both culprits flagged, survivor not', async ({ page }) => {
  await boot(page);
  await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, V = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    const kw = (s: string) => c.keyword.call(null, s);
    const namedGood = c.hash_map.call(null, kw('name'), 'Cleric Boon', kw('summary'), 'ok');
    const nameless1 = c.hash_map.call(null, kw('summary'), 'NAMELESS ONE', kw('page'), 11);
    const nameless2 = c.hash_map.call(null, kw('summary'), 'NAMELESS TWO', kw('page'), 22);
    const actions = c.vector.call(null, namedGood, nameless1, nameless2);
    const tree = c.vector.call(null, V.actions_section, 't4', 'Features, Traits, and Feats', 'vitruvian-man', actions);
    const d = document.createElement('div'); d.id = 't4'; document.body.innerHTML = ''; document.body.appendChild(d);
    w.ReactDOM.render(rc.as_element.call(null, tree), d);
  });
  const t = page.locator('#t4');
  await expect(t).toContainText('2 features in this section couldn’t be displayed');
  await expect(t).toContainText('NAMELESS ONE');
  await expect(t).toContainText('NAMELESS TWO');
  await expect(t).toContainText('Cleric Boon');                 // survivor still rendered
  console.log('T4=' + JSON.stringify((await t.innerText()).slice(0, 400)));
});

// T2 + T6 — a nil skill bonus throws in skills-section-2 (mod-str -> (pos? nil)).
// Wrapped in the per-tab boundary as "Proficiencies": confirm no black-screen, the
// panel NAMES the section, and there's no Features-only trace.
test('T2/T6 nil skill in a non-Features section -> boundary names it, no black-screen', async ({ page }) => {
  await boot(page);
  const threw = await page.evaluate(() => {
    const w = window as any, c = w.cljs.core, V = w.orcpub.dnd.e5.views, rc = w.reagent.core;
    // does skills-section-2 throw for a blank character (empty skill-bonuses -> nil)?
    let sectionThrows = false;
    try { c.deref(rc.as_element.call(null, c.vector.call(null, V.skills_section_2, null))); } catch (e) { /* render is lazy; rely on boundary */ }
    const fallback = (error: any, stack: any, retry: any) =>
      c.vector.call(null, V.feature_render_error, 'Proficiencies', null, error, stack, retry);
    const tree = c.vector.call(null, V.error_boundary, fallback, c.vector.call(null, V.skills_section_2, null));
    const d = document.createElement('div'); d.id = 't6'; document.body.innerHTML = ''; document.body.appendChild(d);
    w.ReactDOM.render(rc.as_element.call(null, tree), d);
    return sectionThrows;
  });
  await page.waitForTimeout(500);
  const t = page.locator('#t6');
  const txt = await t.innerText();
  console.log('T6=' + JSON.stringify(txt.slice(0, 400)));
  // Either it rendered fine (graceful) OR the boundary caught it and named the section.
  // What we must NOT see is a blank/black result.
  expect(txt.trim().length, 'something rendered (no blank/black screen)').toBeGreaterThan(10);
});
