// Default-on overlay suppression, injected by the probe runner via NODE_OPTIONS.
//
// Suppression used to be opt-in: each probe called suppressOverlays itself, so any probe
// that did not know to was silently vulnerable to the next overlay someone shipped. That is
// how the What's New panel broke spell_help_laziness -- its backdrop swallowed clicks, and
// the ten probes that survived did so by luck, not because they had opted in.
//
// This patches playwright's chromium.launch so every context a probe creates carries the
// suppression init scripts, whether the probe asks or not.
//
// OPT OUT with PROBE_SUPPRESS=0 -- for a probe whose whole point is that an overlay fires.
// The runner sets it from `suppress: false` in its probe list.

if (process.env.PROBE_SUPPRESS !== '0') {
  let pw;
  try { pw = require('playwright'); } catch (e) { pw = null; }
  if (pw && pw.chromium && typeof pw.chromium.launch === 'function') {
    const init = context => Promise.all([
      context.addInitScript(() => {
        try { localStorage.setItem('orcpub:no-cookie-banner', '1'); } catch (e) {}
      }),
      context.addInitScript(id => {
        try { localStorage.setItem('whats-new-seen', JSON.stringify(id)); } catch (e) {}
      }, process.env.PROBE_WHATS_NEW_RELEASE || 'summer-patch-2026'),
    ]);

    const wrap = browser => {
      const orig = browser.newContext.bind(browser);
      browser.newContext = async (...args) => {
        const ctx = await orig(...args);
        await init(ctx);
        return ctx;
      };
      // newPage() makes its own context internally, so patch it too.
      const origPage = browser.newPage.bind(browser);
      browser.newPage = async (...args) => {
        const page = await origPage(...args);
        await init(page.context());
        return page;
      };
      return browser;
    };

    const origLaunch = pw.chromium.launch.bind(pw.chromium);
    pw.chromium.launch = async (...args) => wrap(await origLaunch(...args));

    if (typeof pw.chromium.launchPersistentContext === 'function') {
      const origPersist = pw.chromium.launchPersistentContext.bind(pw.chromium);
      pw.chromium.launchPersistentContext = async (...args) => {
        const ctx = await origPersist(...args);
        await init(ctx);
        return ctx;
      };
    }
  }
}
