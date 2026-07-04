import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: './scenarios',
  timeout: 90000,
  expect: { timeout: 10000 },
  reporter: [['list']],
  use: { headless: true, baseURL: 'http://localhost:8899' },
});
