// Drive a real .orcbrew import to completion, including the conflict-resolution modal.
//
// WHY THIS EXISTS: a real pack with overlapping keys makes the app open a conflict modal
// and WAIT. A probe that only polls app-db sees the plugin count stay put and concludes the
// import "failed" — three long runs were lost to exactly that. A static-file server driven
// by dispatch_sync cannot surface an import-conflict modal at all, so it never sees this.
//
// Races the two legitimate outcomes instead of assuming either: the import may land
// straight away, or it may park on the modal. A FIXED sleep before clicking is not enough —
// a bigger pack takes longer to parse, and the click then finds no button.
// Preferred: suppress the banner before it ever renders. Set this on the CONTEXT (via
// addInitScript) before the first navigation and no test has to think about it again.
// cookies.js honours localStorage 'orcpub:no-cookie-banner' and ?no-cookie-banner=1.
async function suppressCookieBanner(context) {
  return context.addInitScript(() => {
    try { localStorage.setItem('orcpub:no-cookie-banner', '1'); } catch (e) {}
  });
}

// Fallback for a page that was already loaded without the flag.
async function dismissCookieBanner(page) {
  for (const label of ['Got it!', 'Got it', 'Accept', 'I agree']) {
    const b = page.locator(`button:has-text("${label}"), a:has-text("${label}")`).last();
    if (await b.count().catch(() => 0)) {
      try { await b.click({ timeout: 3000 }); await page.waitForTimeout(300); return true; } catch (e) {}
    }
  }
  // Fall back to removing it: some builds render it without a dismissable control.
  return page.evaluate(() => {
    const el = document.querySelector('#cookie-policy-popup, #poper');
    if (el) { el.remove(); return true; }
    return false;
  }).catch(() => false);
}

async function importPack(page, absPath, { timeout = 300000 } = {}) {
  // Progress signal that works on BOTH builds. Advanced compilation munges names, so
  // window.cljs.core / re_frame.db do not exist in a prod bundle and the app-db probe
  // silently reports 0 forever. The localStorage blob is build-independent, so fall back
  // to its size; either signal growing means the import landed.
  const pluginCount = () => page.evaluate(() => {
    try { const c = window.cljs.core;
          if (c && window.re_frame) {
            const p = c.get(window.re_frame.db.app_db.state, c.keyword(null, 'plugins'));
            if (p) return c.count(p);
          }
    } catch (e) {}
    try { return (localStorage.getItem('plugins') || '').length; } catch (e) { return 0; }
  });
  // The cookie-consent banner (#cookie-policy-popup) is fixed to the bottom of the page and
  // sits OVER the conflict modal's buttons: Playwright reports "subtree intercepts pointer
  // events" and even a forced click lands on the banner. Dismiss it first, exactly as a user
  // would. This cost several runs — the button was visible and enabled the whole time, which
  // is why "is it visible?" was the wrong question.
  await dismissCookieBanner(page);

  const before = await pluginCount();
  await page.setInputFiles('input[type=file]', absPath);

  const deadline = Date.now() + timeout;
  let clicked = false, lastClickErr = null;
  while (Date.now() < deadline) {
    if (await pluginCount() > before) return { ok: true, viaModal: clicked };
    if (!clicked) {
      for (const label of ['Import', 'Confirm', 'Apply', 'OK']) {
        const b = page.locator(`button:has-text("${label}")`).last();
        if (await b.count().catch(() => 0)) {
          const visible = await b.isVisible().catch(() => false);
          if (visible) {
            try { await b.click({ timeout: 5000 }); clicked = true; break; }
            catch (e) {
              lastClickErr = String(e).split('\n').slice(0, 12).join(' / ').slice(0, 700);
              // Playwright's actionability wait can stall on a modal that re-renders every
              // frame — the element never reads as "stable" even though a user could click
              // it fine. force skips the stability wait; it is still a real mouse click at
              // the element's box, not a synthetic DOM event.
              try { await b.click({ timeout: 5000, force: true }); clicked = true; break; }
              catch (e2) { lastClickErr += ' || FORCE: ' + String(e2).split('\n')[0].slice(0, 160); }
            }
          }
        }
      }
    }
    await page.waitForTimeout(1000);
  }
  // Failure must SAY what it saw. Guessing at why a click did not land has cost several
  // runs already; capture the page's own state instead.
  const diag = await page.evaluate(() => ({
    buttons: [...document.querySelectorAll('button')].map(b => ({
      t: b.innerText.trim().slice(0, 30),
      vis: !!(b.offsetWidth || b.offsetHeight || b.getClientRects().length)
    })).slice(0, 20),
    _placeholder: null,
    conflictText: (document.body.innerText.match(/conflict[\s\S]{0,160}/i) || [''])[0].replace(/\n+/g, ' | '),
    errText: (document.body.innerText.match(/(error|fail|invalid|could ?n.t)[\s\S]{0,140}/i) || [''])[0].replace(/\n+/g, ' | ')
  })).catch(e => ({ evalErr: String(e).slice(0, 100) }));
  return { ok: false, viaModal: clicked, count: await pluginCount(), diag, lastClickErr };
}
// The What's New panel opens over the page for any browser that has not seen the current
// release, and its backdrop swallows clicks -- it broke spell_help_laziness the moment the
// panel landed. Stamp it as seen before the app boots.
//
// The id must match the newest `:id` in src/cljc/orcpub/whats_new.cljc. A new release id
// makes the panel open again by design, which will break probes again; the probe runner is
// what catches that, and the fix is this one line.
const WHATS_NEW_RELEASE = 'summer-patch-2026';

async function suppressWhatsNew(context) {
  return context.addInitScript(id => {
    try { localStorage.setItem('whats-new-seen', JSON.stringify(id)); } catch (e) {}
  }, WHATS_NEW_RELEASE);
}

// Most probes want both. whats_new_e2e.js deliberately uses neither.
async function suppressOverlays(context) {
  await suppressCookieBanner(context);
  await suppressWhatsNew(context);
}

module.exports = { importPack, dismissCookieBanner, suppressCookieBanner,
                   suppressWhatsNew, suppressOverlays, WHATS_NEW_RELEASE };
