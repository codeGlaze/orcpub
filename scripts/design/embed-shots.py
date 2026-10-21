#!/usr/bin/env python3
"""Embeds every <img src="shots/..."> in a review page as a data: URI, so the page publishes as one
Artifact file. Usage: embed-shots.py <page.html> <out.html>. Paths are relative to the page."""
import base64, os, re, sys

page, out = sys.argv[1], sys.argv[2]
base = os.path.dirname(os.path.abspath(page))
html = open(page, encoding="utf-8").read()

def embed(m):
    path = os.path.join(base, m.group(1))
    kind = "jpeg" if path.lower().endswith((".jpg", ".jpeg")) else "png"
    return 'src="data:image/%s;base64,%s"' % (kind, base64.b64encode(open(path, "rb").read()).decode())

html = re.sub(r'src="(shots/[^"]+)"', embed, html)
open(out, "w", encoding="utf-8").write(html)
print("%s: %d KB (Artifact limit 16 MB)" % (out, len(html) // 1024))
