// Drive a real .orcbrew import to completion, including the conflict-resolution modal.
//
// WHY THIS EXISTS: a real pack with overlapping keys makes the app open a conflict modal
// and WAIT. A probe that only polls app-db sees the plugin count stay put and concludes the
// import "failed" — three long runs were lost to exactly that, and CLAUDE.md warns about it
// in as many words ("a static-file server + dispatch_sync can't surface an import-conflict
// modal, and it misled a previous pass into a false conclusion").
//
// Races the two legitimate outcomes instead of assuming either: the import may land
// straight away, or it may park on the modal. A FIXED sleep before clicking is not enough —
// a bigger pack takes longer to parse, and the click then finds no button.
async function importPack(page, absPath, { timeout = 300000 } = {}) {
  const pluginCount = () => page.evaluate(() => {
    try { const c = window.cljs.core;
          const p = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'plugins'));
          return p ? c.count(p) : 0; } catch (e) { return 0; }
  });
  const before = await pluginCount();
  await page.setInputFiles('input[type=file]', absPath);

  const deadline = Date.now() + timeout;
  let clicked = false;
  while (Date.now() < deadline) {
    if (await pluginCount() > before) return { ok: true, viaModal: clicked };
    if (!clicked) {
      for (const label of ['Import', 'Confirm', 'Apply', 'OK']) {
        const b = page.locator(`button:has-text("${label}")`).last();
        if (await b.count().catch(() => 0)) {
          const visible = await b.isVisible().catch(() => false);
          if (visible) { try { await b.click({ timeout: 5000 }); clicked = true; break; } catch (e) {} }
        }
      }
    }
    await page.waitForTimeout(1000);
  }
  return { ok: false, viaModal: clicked, count: await pluginCount() };
}
module.exports = { importPack };
