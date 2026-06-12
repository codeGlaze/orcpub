import { test } from '@playwright/test';

test('debug: what is the Continue control + can we click it?', async ({ page }) => {
  test.setTimeout(90000);
  await page.goto('/');
  await page.waitForTimeout(3000);

  const html = await page.evaluate(() => {
    const els = Array.from(document.querySelectorAll('button, a, input[type=submit]'))
      .filter((e) => /continue/i.test((e as HTMLElement).innerText || (e as HTMLInputElement).value || ''));
    return els.map((e) => (e as HTMLElement).outerHTML).join('\n---\n');
  });
  console.log('CONTINUE_HTML=' + html.slice(0, 800));
  console.log('URL_BEFORE=' + page.url());

  // Try the most direct click.
  let clickErr = '';
  await page.getByText('Continue', { exact: true }).click({ timeout: 8000 }).catch((e) => { clickErr = e.message.slice(0, 120); });
  console.log('CLICK_ERR=' + clickErr);
  await page.waitForTimeout(5000);
  console.log('URL_AFTER=' + page.url());
  console.log('HAS_APP=' + (await page.locator('#app').count()));
});
