import { test, expect } from '@playwright/test';
import * as path from 'path';
import { waitForAppReady } from '../fixtures/test-utils';

/**
 * orcbrew validation console verification
 *
 * Verifies the combined fix for "validation errors render as munged garbage in
 * the dev console" (feature/fix-orcbrew-errors). Drives the real my-content UI
 * against the ADVANCED (prod) build so the original advanced-compilation
 * munging conditions are faithfully reproduced.
 *
 * The fix pre-formats every import/export validation message into a single
 * readable string. So the failure signature we assert against is a raw cljs
 * compiler form ("cljs.core/..."), or the bare mangled object dump that the
 * old code produced when a CLJS collection was passed straight to
 * console.error.
 */

const FIX = path.join(__dirname, '../fixtures');

interface Msg { type: string; text: string; }

function capture(page: import('@playwright/test').Page): Msg[] {
  const msgs: Msg[] = [];
  page.on('console', (m) => msgs.push({ type: m.type(), text: m.text() }));
  page.on('pageerror', (e) => msgs.push({ type: 'pageerror', text: e.message }));
  return msgs;
}

// Console noise unrelated to our validation paths.
function appNoise(text: string): boolean {
  return (
    text.includes('figwheel') ||
    text.includes('ws://localhost:3449') ||
    text.includes('Download the React DevTools') ||
    text.includes('[HMR]')
  );
}

// The bug signature: raw compiler forms or obviously-mangled object dumps.
function looksMunged(text: string): boolean {
  if (/cljs\.core\//.test(text)) return true;       // raw predicate forms leaked
  if (/clojure\.core\//.test(text)) return true;
  if (/#object\[/.test(text)) return true;          // raw js object dump
  // A lone 1-2 char "word" that is the whole message (the classic "M") with no
  // readable content around it.
  if (/^[A-Za-z]{1,2}$/.test(text.trim())) return true;
  return false;
}

async function gotoMyContent(page: import('@playwright/test').Page) {
  await page.goto('/dnd/5e/my-content');
  await waitForAppReady(page);
  await page.waitForSelector('input[type="file"]', { timeout: 20000 });
}

async function importFile(page: import('@playwright/test').Page, fixture: string) {
  const input = page.locator('input[type="file"]').first();
  await input.setInputFiles(path.join(FIX, fixture));
  // Give the FileReader + validate-import + re-frame a moment.
  await page.waitForTimeout(2500);
}

function report(label: string, msgs: Msg[]) {
  const relevant = msgs.filter((m) => !appNoise(m.text));
  console.log(`\n===== ${label}: ${relevant.length} console msg(s) =====`);
  for (const m of relevant) {
    console.log(`  [${m.type}] ${m.text.slice(0, 300).replace(/\n/g, '\\n')}`);
  }
  const munged = relevant.filter((m) => looksMunged(m.text));
  return { relevant, munged };
}

test.describe('orcbrew validation console', () => {
  test('import: malformed EDN -> readable parse error, no munge', async ({ page }) => {
    const msgs = capture(page);
    await gotoMyContent(page);
    await importFile(page, 'broken-parse.orcbrew');
    const { relevant, munged } = report('parse-error', msgs);
    expect(munged, `munged messages: ${JSON.stringify(munged)}`).toHaveLength(0);
    // Should surface a readable parse/validation message somewhere.
    expect(relevant.some((m) => /parse|error|invalid|validation/i.test(m.text))).toBeTruthy();
  });

  test('import: spec-invalid content -> humanized error, no raw cljs forms', async ({ page }) => {
    const msgs = capture(page);
    await gotoMyContent(page);
    await importFile(page, 'broken-spec.orcbrew');
    const { munged } = report('spec-invalid', msgs);
    expect(munged, `munged messages: ${JSON.stringify(munged)}`).toHaveLength(0);
  });

  test('import: real test-PAK (multi-plugin) -> clean readable console', async ({ page }) => {
    const msgs = capture(page);
    await gotoMyContent(page);
    await importFile(page, 'test-pak.orcbrew');
    const { munged } = report('real-test-pak', msgs);
    expect(munged, `munged messages: ${JSON.stringify(munged)}`).toHaveLength(0);
  });
  // The export-warning modal is exercised by the builder specs
  // (builder-missing-fields / builder-required-cues): import auto-cleans missing
  // fields, so import-then-export can't trigger it — only builder-created
  // incomplete content can.
});
