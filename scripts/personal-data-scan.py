#!/usr/bin/env python3
"""Personal-data scan: blocks real IP addresses, email addresses, keys, tokens and password hashes.

Usage:
  scripts/personal-data-scan.py --range <base>..<head>   added lines and commit authors in a range
  scripts/personal-data-scan.py --pushed <remote> <ref>  commits on <ref> not yet on any <remote> branch
  scripts/personal-data-scan.py --tree <ref>             every file at <ref>
Exceptions live in .personal-data-allow (format in that file); a line ending in the marker
`personal-data: ok` is skipped. Exit 0 clean, 1 findings, 2 usage error. docs/kb/personal-data-scan.md
"""
import ipaddress
import os
import re
import subprocess
import sys

ALLOW_FILE = '.personal-data-allow'
MARKER = 'personal-data: ok'
DOC_NETS = [ipaddress.ip_network(n) for n in ('192.0.2.0/24', '198.51.100.0/24', '203.0.113.0/24')]
IP = re.compile(r'(?<![\w.-])(\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3})(?!\w|\.\d|/\d)')  # not 1.2.3.4.5 or a CIDR
EMAIL = re.compile(r'(?<![\w.%+-])[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:\.[A-Za-z0-9-]+)*\.[A-Za-z]{2,}')
SECRETS = [
    ('private key', re.compile(r'-----BEGIN (?:[A-Z]+ )?PRIVATE KEY-----')),
    ('AWS key', re.compile(r'\b(?:AKIA|ASIA)[0-9A-Z]{16}\b')),
    ('GitHub token', re.compile(r'\b(?:gh[pousr]_[A-Za-z0-9]{36}|github_pat_[A-Za-z0-9_]{40,})')),
    ('API key', re.compile(r'\b(?:sk-(?:ant-|proj-)?[A-Za-z0-9_-]{24,}|xox[abprs]-[A-Za-z0-9-]{10,}|AIza[0-9A-Za-z_-]{35})')),
    ('password hash', re.compile(r'\$2[aby]\$\d\d\$[./A-Za-z0-9]{53}|\bbcrypt\+sha512\$[0-9a-f]{20,}')),
    ('JWT', re.compile(r'\beyJ[A-Za-z0-9_-]{10,}\.eyJ[A-Za-z0-9_-]{10,}\.[A-Za-z0-9_-]{10,}')),
]
BINARY_PATH = re.compile(r'\.(svg|png|jpe?g|gif|webp|woff2?|ttf|otf|ico|pdf|map|jar|zip|gz)$|(^|/)compiled/|package-lock\.json$')
ASSET = re.compile(r'\.(png|jpe?g|gif|svg|webp)$')  # icon@2x.png, roll@2.png: no TLD is an image type
VERSION_BEFORE = re.compile(r'(?:version|release|tag)\s*[:=]?\s*["\']?$', re.I)  # version=2.5.0.27
PRODUCT_BEFORE = re.compile(r'\b[A-Z][A-Za-z]*/$')  # a user-agent product token: Chrome/126.0.0.0


def load_allow(root):
    """Reads .personal-data-allow at `root`. Returns a dict of kind -> list of values or regexes.

    Kinds: email, email-domain, ip, author, path (a regex on the file path)."""
    allow = {'email': set(), 'email-domain': set(), 'ip': set(), 'author': set(), 'path': []}
    path = os.path.join(root, ALLOW_FILE)
    if not os.path.exists(path):
        return allow
    for raw in open(path, encoding='utf-8'):
        line = raw.split('#', 1)[0].strip()
        if not line or ':' not in line:
            continue
        kind, value = (s.strip() for s in line.split(':', 1))
        if kind == 'path':
            allow['path'].append(re.compile(value))
        elif kind in allow:
            allow[kind].add(value.lower())
    return allow


def email_allowed(addr, allow):
    """True when `addr` is on the allow list by address or by domain (a domain covers its subdomains)."""
    addr = addr.lower()
    domain = addr.split('@', 1)[1]
    if ASSET.search(domain) or addr in allow['email']:
        return True
    return any(domain == d or domain.endswith('.' + d) for d in allow['email-domain'])


def ip_flagged(text, before, allow):
    """True when `text` is a public IPv4 address not on the allow list.

    Private, loopback, reserved and documentation ranges pass; so does a dotted number whose
    preceding text `before` ends in a version label (version=2.5.0.27, release 2.5.0.26) or a
    capitalised product token (Chrome/126.0.0.0); a URL's // or a lower-case path still flags."""
    try:
        ip = ipaddress.ip_address(text)
    except ValueError:
        return False
    if not ip.is_global or any(ip in n for n in DOC_NETS) or text in allow['ip']:
        return False
    parts = text.split('.')
    if len(set(parts)) == 1 or text.startswith('1.2.3.') or text == '4.3.2.1':
        return False  # an obvious placeholder (9.9.9.9, 1.2.3.4)
    return not (VERSION_BEFORE.search(before) or PRODUCT_BEFORE.search(before))


def scan_line(line, allow):
    """Returns (kind, value) findings for one line of text; empty when the line carries the marker."""
    if MARKER in line:
        return []
    found = [(kind, m.group(0)[:6] + '... (hidden)') for kind, rx in SECRETS for m in rx.finditer(line)]
    found += [('IP address', m.group(1)) for m in IP.finditer(line) if ip_flagged(m.group(1), line[:m.start()], allow)]
    found += [('email', m.group(0)) for m in EMAIL.finditer(line) if not email_allowed(m.group(0), allow)]
    return found


def path_skipped(path, allow):
    """True for binary or generated files, the allow list itself, and paths it exempts."""
    return path == ALLOW_FILE or bool(BINARY_PATH.search(path)) or any(rx.search(path) for rx in allow['path'])


def git(*args):
    """Runs git with `args`; returns stdout as text (raises on failure)."""
    return subprocess.run(['git', *args], check=True, capture_output=True, text=True,
                          encoding='utf-8', errors='replace').stdout


def scan_commits(revs, allow):
    """Scans the commits `revs` (a rev-list argument list): authors, committers and added lines.

    A merge's added lines are those its conflict resolution wrote (--remerge-diff), not the merged
    branch's. Returns a list of (where, kind, value)."""
    hits = []
    merges = set(git('rev-list', '--merges', *revs).split())
    commits = git('rev-list', *revs).split()
    for c in commits:
        meta = git('show', '-s', '--format=%ae%n%ce', c).split()
        for who in sorted(set(meta)):
            if who.lower() not in allow['author'] and not who.lower().endswith('@users.noreply.github.com'):
                hits.append((f'commit {c[:9]}', 'author email', who))
        path, in_header = '', False
        diff = ['--remerge-diff'] if c in merges else []
        for line in git('-c', 'core.quotepath=false', 'show', *diff, '--format=', '--no-color', '-U0', c).split('\n'):
            if line.startswith('diff --git '):
                path, in_header = '', True
            elif line.startswith('@@'):
                in_header = False
            elif in_header:
                if line.startswith('+++ '):
                    path = header_path(line[4:])
            elif line.startswith('+') and path and not path_skipped(path, allow):
                hits += [(f'{path} ({c[:9]})', k, v) for k, v in scan_line(line[1:], allow)]
    return hits


def header_path(name):
    """The file path from a diff's `+++ ` header text `name` (b/x, "b/x" quoted, /dev/null).

    Returns '' for /dev/null (a deleted file)."""
    if name.startswith('"') and name.endswith('"'):
        name = name[1:-1].replace('\\"', '"').replace('\\\\', '\\')
    return name[2:] if name.startswith('b/') else ''


def scan_tree(ref, allow):
    """Scans every text file at `ref`. Returns a list of (where, kind, value)."""
    hits = []
    for entry in git('ls-tree', '-r', '-z', ref).split('\0'):
        if not entry:
            continue
        meta, path = entry.split('\t', 1)
        _mode, kind, oid = meta.split()
        if kind != 'blob' or path_skipped(path, allow):
            continue
        blob = subprocess.run(['git', 'cat-file', 'blob', oid], check=True, capture_output=True).stdout
        if b'\0' in blob[:8000]:
            continue
        for n, line in enumerate(blob.decode('utf-8', 'replace').split('\n'), 1):
            hits += [(f'{path}:{n}', k, v) for k, v in scan_line(line, allow)]
    return hits


def main(argv):
    """Parses `argv`, runs the scan, prints findings. Returns the exit code."""
    root = git('rev-parse', '--show-toplevel').strip()
    allow = load_allow(root)
    if len(argv) == 2 and argv[0] == '--range':
        hits = scan_commits([argv[1]], allow)
    elif len(argv) == 3 and argv[0] == '--pushed':
        hits = scan_commits([argv[2], '--not', f'--remotes={argv[1]}'], allow)
    elif len(argv) == 2 and argv[0] == '--tree':
        hits = scan_tree(argv[1], allow)
    else:
        print(__doc__, file=sys.stderr)
        return 2
    for where, kind, value in hits:
        print(f'  {kind}: {value}  ({where})')
    if hits:
        print(f'personal-data scan: {len(hits)} finding(s). Remove them, or if one is safe to publish, '
              f'add it to {ALLOW_FILE} or end the line with "{MARKER}".', file=sys.stderr)
        return 1
    print('personal-data scan: clean')
    return 0


if __name__ == '__main__':
    try:
        sys.exit(main(sys.argv[1:]))
    except subprocess.CalledProcessError as e:
        print(f'personal-data scan: could not run git {" ".join(e.cmd[1:])}: {e.stderr.strip()}', file=sys.stderr)
        sys.exit(2)
