import { defineConfig, devices } from '@playwright/test';

/**
 * OrcPub Playwright Configuration
 *
 * Environment variables:
 *   APP_URL - Base URL of the app (default: http://localhost:8890)
 *   TEST_SCENARIOS - Comma-separated list of scenarios to run
 *   PATCH_CONTEXT - Description of what patch/feature is being tested
 *   HEADLESS - Run in headless mode (default: true)
 */

const baseURL = process.env.APP_URL || 'http://localhost:8890';

export default defineConfig({
  testDir: './scenarios',
  fullyParallel: false, // Run sequentially to avoid state conflicts
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1,
  reporter: [
    ['html', { open: 'never' }],
    ['json', { outputFile: 'test-results/results.json' }],
    ['./reporters/agent-reporter.ts'], // Custom reporter for Claude/agents
  ],

  use: {
    baseURL,
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',

    // Capture console messages
    contextOptions: {
      logger: {
        isEnabled: () => true,
        log: (name, severity, message) => {
          // Logged via page.on('console') in tests
        },
      },
    },
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
    {
      name: 'mobile',
      use: { ...devices['iPhone 13'] },
    },
  ],

  // Don't start a server - assume app is already running (Codespace or local)
  // webServer: undefined,

  // Timeout settings
  timeout: 30000,
  expect: {
    timeout: 10000,
  },
});
