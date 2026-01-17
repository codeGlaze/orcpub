# OrcPub Automated Testing System

This document describes the automated testing infrastructure designed to speed up feature development in Codespaces by automating test execution and returning structured results to Claude and other agents.

## Problem Solved

**Before:** Manually reload browser, click through UI, copy/paste errors from console, transcribe issues

**Now:** Automated pipeline captures:
- Backend test results
- Frontend build status
- Console errors
- Structured JSON output that Claude can parse and act on

## Architecture

```
Code Change
    ↓
Test Runner (./.claude/test.sh)
    ↓
Results JSON (./.claude/test-results.json)
    ↓
Claude Reads & Acts (auto-fix, retry, etc)
```

The system uses **bidirectional file communication**:
- Tests write results to `.claude/test-results.json`
- Claude reads results, makes decisions, optionally writes feedback

## Quick Start

### Manual Testing

Run tests on-demand:

```bash
# Test everything
./.claude/test.sh

# Focus on specific feature (modals, spell-selection, etc)
./.claude/test.sh --focus modals

# Add custom test descriptions for this patch
./.claude/test.sh --focus modals --custom-tests "test_modal_display,test_modal_close"
```

### Automatic Testing on Commit

Tests run automatically before each commit (non-blocking):

```bash
git commit -m "Fix modal styling"
# → Pre-commit hook runs tests
# → Results saved to .claude/test-results.json
# → Summary displayed
# → Commit proceeds (even if tests fail)
```

## Test Results JSON

Results are written to `.claude/test-results.json` in this format:

```json
{
  "timestamp": "2026-01-17T05:57:33Z",
  "branch": "claude/feature-x",
  "commit": "abc123",
  "test_focus": {
    "target": "modals",
    "custom_tests": ["test_modal_display"]
  },
  "backend_tests": {
    "status": "passed",
    "passed": 42,
    "failed": 0,
    "error": 0
  },
  "build_artifacts": {
    "main_js": { "exists": true, "size_bytes": 1024000 },
    "css": { "exists": true, "size_bytes": 51200 }
  },
  "summary": {
    "overall_status": "passed",
    "all_tests_passed": true,
    "blocking_issues": [],
    "recommendations": ["Run 'lein figwheel' to test UI"]
  }
}
```

Claude can parse this and:
- ✅ Detect if tests passed
- ✅ Identify blocking issues
- ✅ Act automatically (retry, fix, or escalate)
- ✅ Track what's been tested for this feature

## What Gets Tested

### Backend Tests (Always)

```bash
lein test
```

Runs all Clojure/ClojureScript unit tests in `test/` directories.

**Reports:**
- Pass/fail counts
- Error details
- Test output

### Frontend Build (Always)

Checks if these files exist and are recent:
- `resources/public/js/compiled/orcpub.js` (compiled ClojureScript)
- `resources/public/css/compiled/styles.css` (compiled CSS)

### Frontend Console Errors (Manual Browser)

**Not automated yet** - requires app running in browser. To capture:

1. Start dev server: `lein figwheel`
2. Open browser console
3. Look for errors/warnings

(This can be automated with Selenium/Playwright in future)

### Custom UI Tests (Per Patch)

Specify what to focus on for your current patch:

```bash
# For a modal fix
./.claude/test.sh --focus modals --custom-tests "test_modal_display,test_modal_close,test_escape_key"

# For spell selection
./.claude/test.sh --focus spell-selection --custom-tests "test_spell_list_renders,test_spell_search,test_spell_select"
```

The test runner **records what was tested** in the JSON, helping track coverage.

## Usage in Development Workflow

### Scenario 1: Fix a Bug

1. **Make code change**
   ```bash
   # Edit some files
   ```

2. **Run tests immediately**
   ```bash
   ./.claude/test.sh --focus bug-area
   ```

3. **Claude reviews results**
   ```
   I see the modal test is failing at line X.
   Let me fix that and try again...
   ```

4. **Repeat until passing**

### Scenario 2: Add a Feature

1. **Write feature code**

2. **Commit for auto-testing**
   ```bash
   git commit -m "Add character export feature"
   # → Pre-commit hook runs tests
   # → Results in .claude/test-results.json
   ```

3. **Claude checks results** (.claude/test-results.json)
   ```
   Backend tests: ✓ passed
   Build status: ✓ OK

   Next: Manual UI testing in browser for export flow
   ```

4. **Manual browser testing** (if needed)
   ```bash
   lein figwheel
   # Open http://localhost:8890
   # Check browser console for errors
   ```

### Scenario 3: Complex Multi-Component Fix

1. **Make changes to 3 components**

2. **Run targeted tests for each**
   ```bash
   ./.claude/test.sh --focus component-a
   ./.claude/test.sh --focus component-b
   ./.claude/test.sh --focus component-c
   ```

3. **Claude tracks all 3 test runs**
   - Can compare results
   - Identify which component still has issues
   - Suggest fixes

## How Claude Uses This

### Automatic Decision Making

```
Results show backend tests failed
  → Claude reads .claude/test-results.json
  → Sees which tests failed
  → Fixes the issue
  → Runs tests again
  → Success → continues
```

### With User Intervention

```
Results show frontend console errors
  → Claude reads .claude/test-results.json
  → Can't auto-fix UI rendering
  → Reports issue to user
  → User confirms in browser
  → Claude makes targeted fix based on feedback
```

### Tracking Test History

```
Each test run includes:
- timestamp
- branch
- commit hash
- test_focus (what was tested)
- results

Claude can track: "Has this component been tested yet?"
```

## Pre-commit Hook Behavior

The `.git/hooks/pre-commit` hook runs before each commit:

```bash
$ git commit -m "Fix modal"

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  OrcPub Pre-commit Test
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Test Focus: all
Backend Tests: PASSED (42/42 passed)
Build Status: ✓ OK
Overall: PASSED

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
Full results: .claude/test-results.json
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

**Note:** Even if tests fail, the commit proceeds (hook doesn't block). Results are still saved for review.

## Future Enhancements

### Phase 1 (Current)
- ✅ Backend test automation
- ✅ Build artifact checking
- ✅ Structured JSON results
- ✅ Pre-commit hook

### Phase 2 (Planned)
- [ ] Browser console error capture (Selenium/Playwright)
- [ ] Screenshot diffing for UI changes
- [ ] Performance metrics (build time, render time)
- [ ] Component-specific test framework
- [ ] Test coverage reporting

### Phase 3 (Future)
- [ ] E2E testing (full user flows)
- [ ] Visual regression testing
- [ ] Accessibility testing
- [ ] Mobile responsive testing

## Troubleshooting

### "lein" not found

The test runner needs Leiningen installed. In Codespaces:

```bash
curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein
chmod +x /usr/local/bin/lein
```

Or use the devcontainer setup from `testing/develop` branch which includes this.

### No test results file created

Check if `.claude/` directory exists:

```bash
mkdir -p .claude
chmod +x .claude/test.sh
```

### Tests pass but file is missing

Results are created at `.claude/test-results.json`. Verify the directory:

```bash
ls -la .claude/test-results.json
```

## See Also

- `.claude/test-generator.py` - Python script that generates JSON
- `.claude/test.sh` - Wrapper script to run tests
- `.git/hooks/pre-commit` - Automatic testing on commits
- `testing/develop` branch - Devcontainer setup with tools pre-installed
