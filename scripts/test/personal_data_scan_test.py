#!/usr/bin/env python3
"""Table test for scripts/personal-data-scan.py's IP rule: which dotted numbers it flags.

Run: python3 -I scripts/test/personal_data_scan_test.py   (exit 0 pass, 1 fail). CI runs it before the scan.
"""
import importlib.util
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
spec = importlib.util.spec_from_file_location('scan', os.path.join(HERE, '..', 'personal-data-scan.py'))
scan = importlib.util.module_from_spec(spec)
spec.loader.exec_module(scan)

# A real public address, joined at run time so this file does not trip the scan it tests.
PUBLIC = '.'.join(['8', '8', '4', '4'])

EMPTY = {'email': set(), 'email-domain': set(), 'ip': set(), 'author': set(), 'path': []}

# (line, IPs the scan must flag)
CASES = [
    ('ua = "Mozilla/5.0 (Linux) AppleWebKit/537.36 Chrome/126.0.0.0 Mobile Safari/537.36"', []),
    ("userAgent: 'Mozilla/5.0 Chrome/120.0.0.0 Mobile'", []),
    ('Firefox/115.10.2.4 on Android', []),
    (f'server at {PUBLIC} answered', [PUBLIC]),
    (f'fetch("http://{PUBLIC}/x")', [PUBLIC]),
    (f'host={PUBLIC}', [PUBLIC]),
    (f'see /{PUBLIC} in the log', [PUBLIC]),
    ('version=2.5.0.27', []),
    ('private 10.0.0.5 and loopback 127.0.0.1', []),
]


def flagged(line):
    """Returns the IP values scan_line reports for `line` with an empty allow list."""
    return [v for kind, v in scan.scan_line(line, EMPTY) if kind == 'IP address']


def main():
    """Runs every case; prints each mismatch and returns the process exit code (0 pass, 1 fail)."""
    bad = [(line, want, flagged(line)) for line, want in CASES if flagged(line) != want]
    for line, want, got in bad:
        print(f'FAIL {line!r}: wanted {want}, got {got}')
    print(f'personal-data scan test: {len(CASES) - len(bad)}/{len(CASES)} cases pass')
    return 1 if bad else 0


if __name__ == '__main__':
    sys.exit(main())
