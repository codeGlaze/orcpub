// Every state of every flow where an account is made, verified, entered or recovered.
//
//   ./scripts/e2e/run.sh auth-flows.js
//
// These flows had NO coverage, and no way to get any: the verification key and the reset
// key exist only inside an email, registration itself fails when no mail can be sent, and
// every other suite sidesteps all of it by starting from a user e2e-boot seeded straight
// into Datomic. So nothing here was ever exercised, and a reset form that refused every
// ordinary password in silence reached a merge.
//
// run.sh starts scripts/e2e/lib/mail-sink.js and points the app at it, which is what makes
// the keys readable. Without E2E_MAIL_PORT this suite skips rather than pretending to pass.
const { chromium } = require('playwright');
const { BASE, EXECUTABLE, newContext, checker } = require('./lib');
const { startMailSink } = require('./lib/mail-sink');

const PORT = Number(process.env.E2E_MAIL_PORT || 0);
const GOOD = 'brass lantern rope';
// 14 characters, passes every local rule, and the corpus has seen it thousands of
// times -- so ONLY the server can refuse it. It is the one case that proves the
// server's reason reaches the page.
const COMMON = 'letmeinletmein';
const SHORT = 'Sw0rdfish';

const uniq = () => 'e2e' + Date.now().toString(36) + Math.floor(Math.random() * 1e4);
const text = (page, sel) => page.locator(sel).innerText().catch(() => '');
const cardText = page => text(page, '.registration-content');
const heading = page => text(page, '.auth-heading');
const authInputs = page => page.locator('.auth-form input');
const press = (page, label) => page.locator('.auth-tail button', { hasText: label }).click();

// The link in a captured mail, with quoted-printable soft breaks undone.
const linkIn = (mail, re) => (mail.raw.replace(/=\r\n/g, '').match(re) || [])[0];

async function registerAndVerify(page, sink, user, email, password) {
  await page.goto(`${BASE}/pages/register-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  const b = authInputs(page);
  await b.nth(0).fill(user); await b.nth(1).fill(email); await b.nth(2).fill(email);
  await b.nth(3).fill(password); await b.nth(4).fill(password);
  await press(page, 'JOIN');
  const mail = await sink.waitFor(/verif/i, 20000);
  const key = (mail.raw.replace(/=\r\n/g, '').match(/verify\?key=([A-Za-z0-9-]+)/) || [])[1];
  await page.goto(`${BASE}/verify?key=${key}`, { waitUntil: 'networkidle' });
  await page.waitForTimeout(800);
  return key;
}

async function resetLinkFor(page, sink, email) {
  sink.clear();
  await page.goto(`${BASE}/pages/send-password-reset-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  await authInputs(page).first().fill(email);
  await press(page, 'SUBMIT');
  const mail = await sink.waitFor(/reset/i, 20000);
  return linkIn(mail, /https?:\/\/[^\s"'<>]*reset-password[^\s"'<>]*/);
}

(async () => {
  if (!PORT) {
    console.log('SKIP  auth-flows.js needs E2E_MAIL_PORT; run it through scripts/e2e/run.sh');
    process.exit(0);
  }
  const sink = await startMailSink({ port: PORT });
  const browser = await chromium.launch({ executablePath: EXECUTABLE });
  const ctx = await newContext(browser);
  const page = await ctx.newPage();
  const { check, failures } = checker();
  const errors = [];
  page.on('pageerror', e => errors.push(String(e)));

  const user = uniq(), email = `${user}@example.com`;

  console.log('registration');
  await registerAndVerify(page, sink, user, email, GOOD);
  check((await heading(page)).toLowerCase().includes('registration is complete'),
        'a new account registers and verifies', await heading(page));

  // The same address and name a second time must be refused, per field.
  await page.goto(`${BASE}/pages/register-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  let b = authInputs(page);
  await b.nth(0).fill(user); await b.nth(1).fill(email); await b.nth(2).fill(email);
  await b.nth(3).fill(GOOD); await b.nth(4).fill(GOOD);
  await press(page, 'JOIN');
  await page.waitForTimeout(2500);
  const taken = await cardText(page);
  check(/taken|already/i.test(taken), 'a username and email already in use are refused',
        taken.replace(/\n/g, ' | ').slice(0, 120));

  // Short password: the rules must say what is wrong, not just refuse.
  await page.goto(`${BASE}/pages/register-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  b = authInputs(page);
  await b.nth(0).fill(uniq()); const e2 = uniq() + '@example.com';
  await b.nth(1).fill(e2); await b.nth(2).fill(e2);
  await b.nth(3).fill(SHORT); await b.nth(4).fill(SHORT);
  await press(page, 'JOIN');
  await page.waitForTimeout(1200);
  check(/at least 12 characters/i.test(await cardText(page)),
        'a short password is refused WITH its reason');

  console.log('login');
  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill('not the password');
  await page.locator('button.form-button').click();
  await page.waitForTimeout(2500);
  const wrongPass = await cardText(page);

  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill('nobody' + uniq());
  await page.locator('input').nth(1).fill('not the password');
  await page.locator('button.form-button').click();
  await page.waitForTimeout(2500);
  const noSuchUser = await cardText(page);

  // The oracle: a wrong password and a username that does not exist must be
  // indistinguishable, or the login form is a membership test.
  check(wrongPass === noSuchUser,
        'a wrong password and an unknown username answer identically',
        wrongPass === noSuchUser ? '' : `"${wrongPass.slice(0,60)}" vs "${noSuchUser.slice(0,60)}"`);

  console.log('password reset');
  const link = await resetLinkFor(page, sink, email);
  check(!!link, 'a reset link is emailed for a known address', link || 'no link in the mail');

  // An address nobody registered must answer the same way and send nothing.
  sink.clear();
  await page.goto(`${BASE}/pages/send-password-reset-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  await authInputs(page).first().fill(uniq() + '@example.com');
  await press(page, 'SUBMIT');
  await page.waitForTimeout(2500);
  check((await heading(page)).toLowerCase().includes('check your email'),
        'an unknown address is answered the same way');
  check(sink.messages.length === 0, 'and no mail is sent for it',
        sink.messages.length ? `${sink.messages.length} sent` : '');

  await page.goto(link, { waitUntil: 'networkidle' });
  await page.waitForSelector('.auth-form input');
  check((await heading(page)).toLowerCase().includes('choose a new password'),
        'the link opens the reset form');
  check(await page.locator('.pw-slot').count() === 4,
        'the strength meter is on the reset form too');

  // Short: refused by a rule the browser knows, so it must say so without a round trip.
  let r = authInputs(page);
  await r.nth(0).fill(SHORT); await r.nth(1).fill(SHORT);
  await press(page, 'SUBMIT');
  await page.waitForTimeout(800);
  check(/at least 12 characters/i.test(await cardText(page)),
        'a short new password says what is wrong');

  // Common: passes every local rule, so ONLY the server can refuse it. This is the
  // case that was answering 400 with an empty body and a generic apology.
  r = authInputs(page);
  await r.nth(0).fill(COMMON); await r.nth(1).fill(COMMON);
  await press(page, 'SUBMIT');
  await page.waitForTimeout(3500);
  const common = await cardText(page);
  check(/too common/i.test(common),
        "the server's reason for a common password reaches the page",
        common.replace(/\n/g, ' | ').slice(0, 140));

  // Mismatch, while masked.
  r = authInputs(page);
  await r.nth(0).fill(GOOD + ' one'); await r.nth(1).fill(GOOD + ' two');
  await press(page, 'SUBMIT');
  await page.waitForTimeout(800);
  check(/do not match/i.test(await cardText(page)), 'a mismatched confirmation is caught');

  // And the whole point: a good password goes through and then LETS YOU IN.
  const fresh = GOOD + ' candle';
  r = authInputs(page);
  await r.nth(0).fill(fresh); await r.nth(1).fill(fresh);
  await press(page, 'SUBMIT');
  await page.waitForTimeout(3500);
  check(/password/i.test(await heading(page)) === false || /changed|reset/i.test(await cardText(page)),
        'the reset completes', (await heading(page)));

  await page.goto(`${BASE}/pages/login-page`, { waitUntil: 'networkidle' });
  await page.waitForSelector('input');
  await page.locator('input').nth(0).fill(user);
  await page.locator('input').nth(1).fill(fresh);
  await page.locator('button.form-button').click();
  await page.waitForTimeout(3500);
  check(!page.url().includes('login-page'), 'and the new password logs in', page.url().replace(BASE, ''));

  // The link is one use only.
  await page.goto(link, { waitUntil: 'networkidle' });
  await page.waitForTimeout(1500);
  const reused = (await cardText(page)).toLowerCase();
  check(/no longer works|already been used|expired/.test(reused),
        'a reset link cannot be used twice', reused.replace(/\n/g, ' | ').slice(0, 120));

  check(errors.length === 0, 'no page errors anywhere in the run', errors.slice(0, 3).join(' | '));

  await browser.close();
  await sink.stop();
  console.log(failures() ? `\n${failures()} FAILED` : '\nall passed');
  process.exit(failures() ? 1 : 0);
})();
