#!/usr/bin/env python3
"""
OrcPub Test Results Generator
Generates structured JSON results from test runs for agent consumption
"""

import json
import subprocess
import sys
import os
from datetime import datetime
from pathlib import Path

def get_git_info(repo_dir):
    """Get current git branch and commit hash"""
    try:
        branch = subprocess.check_output(
            ['git', 'rev-parse', '--abbrev-ref', 'HEAD'],
            cwd=repo_dir,
            stderr=subprocess.DEVNULL
        ).decode().strip()
    except:
        branch = "unknown"

    try:
        commit = subprocess.check_output(
            ['git', 'rev-parse', '--short', 'HEAD'],
            cwd=repo_dir,
            stderr=subprocess.DEVNULL
        ).decode().strip()
    except:
        commit = "unknown"

    return branch, commit

def run_backend_tests(project_dir):
    """Run backend tests and capture results"""
    try:
        result = subprocess.run(
            ['lein', 'test'],
            cwd=project_dir,
            capture_output=True,
            text=True,
            timeout=300
        )

        output = result.stdout + result.stderr
        status = "passed" if result.returncode == 0 else "failed"

        # Try to parse test counts from output
        passed = 0
        failed = 0
        errors = 0

        for line in output.split('\n'):
            if 'Ran' in line and 'tests' in line:
                parts = line.split()
                if len(parts) >= 2:
                    try:
                        passed = int(parts[1])
                    except:
                        pass
            if 'FAIL' in line or 'ERROR' in line:
                if 'FAIL' in line:
                    failed += 1
                if 'ERROR' in line:
                    errors += 1

        return {
            "status": status,
            "total": passed,
            "passed": passed,
            "failed": failed,
            "error": errors,
            "output": output
        }
    except subprocess.TimeoutExpired:
        return {
            "status": "error",
            "total": 0,
            "passed": 0,
            "failed": 0,
            "error": 1,
            "output": "Test execution timed out"
        }
    except Exception as e:
        return {
            "status": "error",
            "total": 0,
            "passed": 0,
            "failed": 0,
            "error": 1,
            "output": f"Error running tests: {str(e)}"
        }

def check_frontend_build(project_dir):
    """Check frontend build artifacts"""
    js_file = project_dir / "resources" / "public" / "js" / "compiled" / "orcpub.js"
    css_file = project_dir / "resources" / "public" / "css" / "compiled" / "styles.css"

    result = {
        "main_js": {
            "exists": False,
            "size_bytes": 0,
            "last_modified": None
        },
        "css": {
            "exists": False,
            "size_bytes": 0,
            "last_modified": None
        }
    }

    if js_file.exists():
        stat = js_file.stat()
        result["main_js"] = {
            "exists": True,
            "size_bytes": stat.st_size,
            "last_modified": datetime.fromtimestamp(stat.st_mtime).isoformat() + "Z"
        }

    if css_file.exists():
        stat = css_file.stat()
        result["css"] = {
            "exists": True,
            "size_bytes": stat.st_size,
            "last_modified": datetime.fromtimestamp(stat.st_mtime).isoformat() + "Z"
        }

    return result

def generate_results(project_dir, test_focus="all", custom_tests=""):
    """Generate complete test results JSON"""
    timestamp = datetime.utcnow().isoformat() + "Z"
    branch, commit = get_git_info(project_dir)
    run_id = f"{timestamp}-{commit}"

    # Run tests
    backend_results = run_backend_tests(project_dir)
    backend_duration = backend_results.pop("output", "")  # Extract output

    # Check frontend build
    build_artifacts = check_frontend_build(project_dir)

    # Determine overall status
    all_tests_passed = backend_results["status"] == "passed"
    overall_status = "passed" if all_tests_passed else "failed"

    blocking_issues = []
    if backend_results["status"] == "failed":
        blocking_issues.append("Backend tests failed")
    if not build_artifacts["main_js"]["exists"]:
        blocking_issues.append("Frontend JavaScript build missing - run 'lein cljsbuild once main'")

    results = {
        "timestamp": timestamp,
        "branch": branch,
        "commit": commit,
        "test_run_id": run_id,
        "test_focus": {
            "target": test_focus,
            "custom_tests": custom_tests.split(",") if custom_tests else []
        },
        "backend_tests": {
            "status": backend_results["status"],
            "total": backend_results["total"],
            "passed": backend_results["passed"],
            "failed": backend_results["failed"],
            "error": backend_results["error"],
            "output_summary": backend_duration[:500] if backend_duration else ""
        },
        "build_artifacts": build_artifacts,
        "frontend_console": {
            "status": "not_captured_yet",
            "note": "Requires browser inspection - configure Figwheel to capture errors"
        },
        "targeted_ui_tests": {
            "status": "not_run",
            "note": "Patch-specific UI tests can be added with --custom-tests"
        },
        "summary": {
            "overall_status": overall_status,
            "all_tests_passed": all_tests_passed,
            "blocking_issues": blocking_issues,
            "recommendations": [
                "Review backend test output if failed",
                "Run 'lein figwheel' to start dev server",
                "Open http://localhost:8890 in browser",
                "Check browser console for frontend errors"
            ]
        }
    }

    return results

def main():
    project_dir = Path(__file__).parent.parent
    test_focus = "all"
    custom_tests = ""

    # Parse arguments
    for i, arg in enumerate(sys.argv[1:]):
        if arg == "--focus" and i+1 < len(sys.argv)-1:
            test_focus = sys.argv[i+2]
        elif arg == "--custom-tests" and i+1 < len(sys.argv)-1:
            custom_tests = sys.argv[i+2]

    # Generate results
    results = generate_results(project_dir, test_focus, custom_tests)

    # Write results
    output_file = project_dir / ".claude" / "test-results.json"
    output_file.parent.mkdir(parents=True, exist_ok=True)

    with open(output_file, "w") as f:
        json.dump(results, f, indent=2)

    print(json.dumps(results, indent=2))

    return 0 if results["summary"]["overall_status"] == "passed" else 1

if __name__ == "__main__":
    sys.exit(main())
