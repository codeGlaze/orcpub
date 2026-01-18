import type {
  FullConfig,
  FullResult,
  Reporter,
  Suite,
  TestCase,
  TestResult,
} from '@playwright/test/reporter';
import * as fs from 'fs';
import * as path from 'path';

/**
 * Custom Playwright reporter that outputs structured JSON for Claude and other agents.
 *
 * Output format is designed for easy parsing and automated decision-making:
 * - Clear pass/fail status
 * - Console errors captured during tests
 * - Screenshots on failure
 * - Actionable recommendations
 */

/**
 * Known expected errors that should not cause test failure.
 * These are typically dev-mode artifacts that appear in production builds.
 */
const EXPECTED_ERROR_PATTERNS = [
  'figwheel-ws',           // Figwheel WebSocket (dev mode only)
  'ws://localhost:3449',   // Figwheel WebSocket URL
  'DevTools',              // DevTools warnings
];

function isExpectedError(text: string): boolean {
  return EXPECTED_ERROR_PATTERNS.some(pattern => text.includes(pattern));
}

interface ConsoleMessage {
  type: 'error' | 'warning' | 'log' | 'info';
  text: string;
  url?: string;
  lineNumber?: number;
}

interface TestResultSummary {
  name: string;
  scenario: string;
  status: 'passed' | 'failed' | 'skipped' | 'timedOut' | 'interrupted';
  duration: number;
  error?: string;
  consoleErrors: ConsoleMessage[];
  screenshots: string[];
}

interface AgentReport {
  timestamp: string;
  appUrl: string;
  patchContext: string | null;
  scenarios: string[];
  summary: {
    total: number;
    passed: number;
    failed: number;
    skipped: number;
    duration: number;
    overallStatus: 'passed' | 'failed';
  };
  consoleErrors: ConsoleMessage[];
  tests: TestResultSummary[];
  recommendations: string[];
  blockingIssues: string[];
}

class AgentReporter implements Reporter {
  private results: TestResultSummary[] = [];
  private allConsoleErrors: ConsoleMessage[] = [];
  private startTime: number = 0;
  private config: FullConfig | null = null;

  onBegin(config: FullConfig, suite: Suite) {
    this.config = config;
    this.startTime = Date.now();
    console.log(`\n🎭 OrcPub E2E Tests Starting...`);
    console.log(`   App URL: ${process.env.APP_URL || 'http://localhost:8890'}`);
    if (process.env.PATCH_CONTEXT) {
      console.log(`   Testing: ${process.env.PATCH_CONTEXT}`);
    }
  }

  onTestEnd(test: TestCase, result: TestResult) {
    const scenario = path.basename(test.parent.title || test.location.file);

    // Extract console errors from attachments or annotations
    const consoleErrors: ConsoleMessage[] = [];
    for (const attachment of result.attachments) {
      if (attachment.name === 'console-errors' && attachment.body) {
        try {
          const errors = JSON.parse(attachment.body.toString());
          consoleErrors.push(...errors);
        } catch {
          // Ignore parse errors
        }
      }
    }

    // Collect screenshots
    const screenshots = result.attachments
      .filter((a) => a.contentType?.startsWith('image/'))
      .map((a) => a.path || '')
      .filter(Boolean);

    const testSummary: TestResultSummary = {
      name: test.title,
      scenario,
      status: result.status,
      duration: result.duration,
      error: result.error?.message,
      consoleErrors,
      screenshots,
    };

    this.results.push(testSummary);
    this.allConsoleErrors.push(...consoleErrors);

    // Print progress
    const icon = result.status === 'passed' ? '✓' : result.status === 'failed' ? '✗' : '○';
    console.log(`   ${icon} ${test.title} (${result.duration}ms)`);
  }

  onEnd(result: FullResult) {
    const duration = Date.now() - this.startTime;
    const passed = this.results.filter((r) => r.status === 'passed').length;
    const failed = this.results.filter((r) => r.status === 'failed').length;
    const skipped = this.results.filter((r) => r.status === 'skipped').length;

    // Generate recommendations based on results
    const recommendations: string[] = [];
    const blockingIssues: string[] = [];

    // Filter out expected errors (like Figwheel WebSocket in production mode)
    const unexpectedErrors = this.allConsoleErrors.filter(
      (e) => e.type === 'error' && !isExpectedError(e.text)
    );

    if (unexpectedErrors.length > 0) {
      blockingIssues.push(`${unexpectedErrors.length} unexpected console error(s) detected`);
      recommendations.push('Review console errors - they may indicate runtime issues');
    }

    if (failed > 0) {
      const failedTests = this.results.filter((r) => r.status === 'failed');
      for (const test of failedTests) {
        blockingIssues.push(`Test "${test.name}" failed: ${test.error || 'Unknown error'}`);
      }
      recommendations.push('Fix failing tests before proceeding');
    }

    if (passed === this.results.length && unexpectedErrors.length === 0) {
      recommendations.push('All tests passed with no unexpected errors - ready to proceed');
    }

    // Build the report
    const report: AgentReport = {
      timestamp: new Date().toISOString(),
      appUrl: process.env.APP_URL || 'http://localhost:8890',
      patchContext: process.env.PATCH_CONTEXT || null,
      scenarios: [...new Set(this.results.map((r) => r.scenario))],
      summary: {
        total: this.results.length,
        passed,
        failed,
        skipped,
        duration,
        // Pass if no test failures and no unexpected console errors
        overallStatus: failed === 0 && unexpectedErrors.length === 0
          ? 'passed'
          : 'failed',
      },
      consoleErrors: this.allConsoleErrors,
      tests: this.results,
      recommendations,
      blockingIssues,
    };

    // Write to file
    const outputDir = path.join(process.cwd(), 'test-results');
    if (!fs.existsSync(outputDir)) {
      fs.mkdirSync(outputDir, { recursive: true });
    }

    const outputPath = path.join(outputDir, 'agent-report.json');
    fs.writeFileSync(outputPath, JSON.stringify(report, null, 2));

    // Print summary
    console.log(`\n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`);
    console.log(`  OrcPub E2E Test Results`);
    console.log(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`);
    console.log(`  Total: ${this.results.length} | Passed: ${passed} | Failed: ${failed}`);
    console.log(`  Console Errors: ${unexpectedErrors.length} (${this.allConsoleErrors.filter((e) => e.type === 'error').length} total, ${this.allConsoleErrors.filter((e) => e.type === 'error').length - unexpectedErrors.length} expected)`);
    console.log(`  Overall: ${report.summary.overallStatus.toUpperCase()}`);
    console.log(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━`);
    console.log(`  Agent report: ${outputPath}`);
    console.log(`━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\n`);
  }
}

export default AgentReporter;
