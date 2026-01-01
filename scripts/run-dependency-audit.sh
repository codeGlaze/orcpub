#!/usr/bin/env bash
# Run a dependency & test audit for this project and save outputs to ./audit/
# Usage: chmod +x scripts/run-dependency-audit.sh && ./scripts/run-dependency-audit.sh

set -u

OUT_DIR="audit"
mkdir -p "$OUT_DIR"

echo "Dependency audit started at $(date)" > "$OUT_DIR/README.txt"

# Environment
{
  echo "## Environment"
  echo
  echo "java:" && java -version 2>&1 | sed -n '1,5p' || true
  echo
  echo "lein:" && lein -v 2>&1 | sed -n '1,5p' || true
  echo
  echo "node:" && node -v 2>&1 || true
  echo "npm:" && npm -v 2>&1 || true
  echo
  echo "## Git"
  git rev-parse --abbrev-ref HEAD 2>/dev/null || echo "(git not available)"
  echo
  git status --porcelain 2>/dev/null || true
} > "$OUT_DIR/env.txt" || true

# Dependency tree
echo "Running: lein deps :tree (this can be slow)"
lein deps :tree > "$OUT_DIR/deps-tree.txt" 2>&1 || true

# Tests
echo "Running: lein test"
lein test > "$OUT_DIR/test-results.txt" 2>&1 || true

# Lint (use alias if available - falls back if not)
echo "Running: lein lint"
# Try the alias; if it fails, record a helpful message
lein lint > "$OUT_DIR/lint-results.txt" 2>&1 || echo "lein lint failed or is not configured; try 'lein with-profile lint run -m clj-kondo.main --lint src'" > "$OUT_DIR/lint-results.txt" || true

# NPM outdated
echo "Running: npm outdated --json"
npm outdated --json > "$OUT_DIR/npm-outdated.json" 2>/dev/null || true

# npm-check-updates suggestions
echo "Running: npx npm-check-updates"
npx npm-check-updates --packageFile package.json --jsonUpgraded > "$OUT_DIR/ncu.json" 2>/dev/null || true

# Summarize outputs
{
  echo "Audit completed: $(date)"
  echo
  echo "Files in $OUT_DIR:"
  ls -la "$OUT_DIR" || true
} > "$OUT_DIR/summary.txt"

cat "$OUT_DIR/summary.txt"

echo "Audit files are in $OUT_DIR/. Commit or upload them as artifacts for PR review."