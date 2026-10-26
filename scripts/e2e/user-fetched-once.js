// Needs: dev bundle (reads the app's internals, which a production bundle compiles away).
// Is the account fetched once per page load, and does it keep who you follow?
//
//   ./scripts/e2e/run.sh user-fetched-once.js
//
// GET /user looks the account up on the server, which reads the database each time. The app asks for
// it once at start (:verify-user-session) and keeps the answer. A view showing another account's
// name subscribes to :following-users, and that must read the kept answer, not ask again: five
// fresh subscriptions (what five page mounts do) send no request. The kept answer carries the
// accounts you follow, so a follow is still shown after a reload. plan-lookup-indexes.md.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, newContext, login, checker } = require('./lib');

const { check, failures } = checker();

// The usernames in app-db's :user :following.
const following = page => page.evaluate(() => {
  const c = window.cljs.core;
  const f = c.get_in(window.re_frame.db.app_db.state,
    c.PersistentVector.fromArray([c.keyword(null, 'user'), c.keyword(null, 'following')], true));
  return f == null ? [] : c.clj__GT_js(c.vec(f));
});

// Subscribes to :following-users `n` times, each a fresh subscription as a newly mounted view makes.
const mountFollowing = (page, n) => page.evaluate(n => {
  const c = window.cljs.core, rf = window.re_frame.core;
  for (let i = 0; i < n; i++) {
    rf.clear_subscription_cache_BANG_();
    c.deref(rf.subscribe(c.PersistentVector.fromArray([c.keyword(null, 'following-users')], true)));
  }
}, n);

(async () => {
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await newContext(browser);
  const page = await ctx.newPage();
  const userGets = [];
  page.on('request', r => {
    if (r.method() === 'GET' && new URL(r.url()).pathname === '/user') userGets.push(r.url());
  });

  await login(page, 'kaylee', 'serenity99');
  const token = await page.evaluate(() => {
    const c = window.cljs.core;
    return c.get_in(window.re_frame.db.app_db.state,
      c.PersistentVector.fromArray([c.keyword(null, 'user-data'), c.keyword(null, 'token')], true));
  });
  check(!!token, 'signed in as kaylee');
  const followed = await page.evaluate(async ({ url, token }) =>
    (await fetch(url, { method: 'POST', headers: { Authorization: `Token ${token}` } })).status,
    { url: `${BASE}/following/users/zoe`, token });
  check(followed === 200, 'kaylee follows zoe', `status ${followed}`);

  userGets.length = 0;
  await page.goto(`${BASE}/pages/dnd/5e/characters`, { waitUntil: 'domcontentloaded' });
  await page.waitForFunction(() => window.re_frame && window.cljs, null, { timeout: 60000 });
  await page.waitForTimeout(3000);
  check(userGets.length === 1, 'a page load fetches the account once', `${userGets.length} GET /user`);
  check((await following(page)).includes('zoe'), 'after a reload the app still knows kaylee follows zoe',
    JSON.stringify(await following(page)));

  userGets.length = 0;
  await mountFollowing(page, 5);
  await page.waitForTimeout(2000);
  check(userGets.length === 0, 'five views that show other accounts ask for nothing more',
    `${userGets.length} GET /user`);

  await page.evaluate(async ({ url, token }) =>
    fetch(url, { method: 'DELETE', headers: { Authorization: `Token ${token}` } }),
    { url: `${BASE}/following/users/zoe`, token });
  await browser.close();
  console.log(failures() ? `\n${failures()} FAILED` : '\nALL PASSED');
  process.exit(failures() ? 1 : 0);
})().catch(e => { console.error(e); process.exit(1); });
