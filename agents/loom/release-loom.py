#!/usr/bin/env python3
"""Package the Silhouette Loom for sending. Run before every delivery.

Refuses to release when:
  - the newest About entry is not the version in LOOM_VERSION;
  - the page's contents changed but the version did not (the ledger keeps
    the hash each released version had).
Bundles the working placements (the app's resources/portrait-pack/loom.edn)
into the page, stamps the build hash, and writes release/silhouette-loom-v2.html.

The file name stays silhouette-loom-v2.html on purpose: a new copy replaces
the old one and a refresh picks it up. The exact version and build show
inside the page (badge, title, About).
"""
import hashlib, json, os, re, sys

# The working placements live on the code branches, not here: point LOOM_EDN
# at a checkout of one, or run from a tree that has the file.
LOOM_EDN = os.environ.get('LOOM_EDN') or os.path.join(
    os.path.dirname(os.path.abspath(__file__)), '..', '..', 'resources', 'portrait-pack', 'loom.edn')

def edn_to_py(text):
    """Just enough EDN for loom.edn: maps, vectors, strings, keywords,
    numbers, true/false/nil, comments."""
    toks = re.findall(r';[^\n]*|"(?:[^"\\]|\\.)*"|[{}\[\]]|[^\s,{}\[\]]+', text)
    toks = [t for t in toks if not t.startswith(';')]
    pos = 0
    def val():
        nonlocal pos
        t = toks[pos]; pos += 1
        if t == '{':
            m = {}
            while toks[pos] != '}':
                k = val(); m[k] = val()
            pos += 1; return m
        if t == '[':
            v = []
            while toks[pos] != ']': v.append(val())
            pos += 1; return v
        if t.startswith('"'): return json.loads(t)
        if t.startswith(':'): return t[1:]
        if t == 'true': return True
        if t == 'false': return False
        if t == 'nil': return None
        return float(t) if any(c in t for c in '.eE') else int(t)
    return val()
here = os.path.dirname(os.path.abspath(__file__))
src = os.path.join(here, 'silhouette-loom-v2.html')
ledger_path = os.path.join(here, 'releases.json')
s = open(src).read()
ver = re.search(r"const LOOM_VERSION = '(v[\d.]+)';", s).group(1)
newest = re.search(r'<b>(v[\d.]+)</b>', s).group(1)
if newest != ver:
    sys.exit(f'REFUSED: LOOM_VERSION is {ver} but the newest About entry is {newest}')
if not os.path.exists(LOOM_EDN):
    sys.exit(f'No loom.edn at {LOOM_EDN}. Set LOOM_EDN to resources/portrait-pack/loom.edn in a code-branch checkout.')
placements = edn_to_py(open(LOOM_EDN, encoding='utf-8').read())
count = sum(len(v) for v in placements.values())
edn_text = open(LOOM_EDN, encoding='utf-8').read()
pack = re.search(r'(?m)^;; Placed in pack (\S+)\.$', edn_text)
bundle = {'source': 'loom.edn ' + hashlib.sha256(edn_text.encode()).hexdigest()[:8],
          'packVersion': pack.group(1) if pack else None,
          'count': count, 'layers': placements}
s = re.sub(r"const BUNDLED_MANIFEST = [^\n]*;( // bundled by release-loom.py)?",
           lambda m: "const BUNDLED_MANIFEST = " + json.dumps(bundle, separators=(',', ':')) + "; // bundled by release-loom.py", s, count=1)
body = re.sub(r"const LOOM_BUILD = '[^']*';", "const LOOM_BUILD = '';", s)
h = hashlib.sha256(body.encode()).hexdigest()[:8]
ledger = json.load(open(ledger_path)) if os.path.exists(ledger_path) else {}
if ver in ledger and ledger[ver] != h:
    sys.exit(f'REFUSED: {ver} was already released as build {ledger[ver]} and the page has changed since (now {h}). Bump the version and add an About entry.')
out = re.sub(r"const LOOM_BUILD = '[^']*';", f"const LOOM_BUILD = '{h}';", s)
out = re.sub(r'<title>Silhouette Loom v[\d.]+</title>', f'<title>Silhouette Loom {ver}</title>', out)
out = re.sub(r'<span id="ver-badge">v[\d.]+</span>', f'<span id="ver-badge">{ver}</span>', out)
os.makedirs(os.path.join(here, 'release'), exist_ok=True)
dst = os.path.join(here, 'release', 'silhouette-loom-v2.html')
open(dst, 'w').write(out)
ledger[ver] = h
json.dump(ledger, open(ledger_path, 'w'), indent=1, sort_keys=True)
print(f'released {ver} build {h} -> release/{os.path.basename(dst)} with {count} pieces of placements from {bundle["source"]}, pack {bundle["packVersion"]}')
