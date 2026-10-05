#!/usr/bin/env python3
"""Emit the SRD 5.2.1 spell delta as orcpub .cljc data."""
import re, json, sys
import os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) or ".")
from spelldelta import parse, norm_val   # same parser, same anchors

S = os.path.dirname(os.path.abspath(__file__)) or "."
SRC_TAG = ":srd-2024"          # one line to change if the tag is named differently

def kw(name):
    k = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")
    return ":" + k

def components(raw):
    """'V, S, M (a tiny ball of bat guano and sulfur)' -> the orcpub map."""
    if not raw: return None
    mat = re.search(r"\bM\b\s*\(([^)]*)\)", raw)
    parts = {"verbal": bool(re.search(r"\bV\b", raw)),
             "somatic": bool(re.search(r"\bS\b", raw)),
             "material": bool(re.search(r"\bM\b", raw))}
    out = " ".join(f":{k} true" for k, v in parts.items() if v)
    if mat: out += ' :material-component "%s"' % mat.group(1).replace('"', "'").strip()
    return "{" + out + "}"

def descriptions(path):
    """Body text, from a spell's Duration line to the next spell's NAME line.

    The earlier version ended each body at `next Casting Time - 3`, assuming the
    next spell's name sits three lines above its anchor. Headers wrap, so it is
    often four, and bodies were cut mid-sentence. Finding the name lines first and
    using them as the boundary removes the guess."""
    lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
    anchors = [i for i, l in enumerate(lines) if l.startswith("Casting Time:")]
    SKIP = re.compile(r"^(Level \d|[A-Z][a-z]+ Cantrip|Casting|Range|Components|Duration)")
    found = []                       # (name_idx, name, anchor_idx)
    for i in anchors:
        k, hops = i - 1, 0
        while k > 0 and hops < 5:
            nm = lines[k].strip()
            if re.fullmatch(r"[A-Z][A-Za-z'\u2019/\- ]{2,40}", nm) and not SKIP.match(nm):
                found.append((k, nm, i)); break
            k -= 1; hops += 1
    out = {}
    for n, (nidx, nm, anchor) in enumerate(found):
        j = anchor
        while j < len(lines) and not lines[j].startswith("Duration:"):
            j += 1
            if j - anchor > 6: break
        end = found[n + 1][0] if n + 1 < len(found) else min(j + 60, len(lines))
        # Running furniture repeats on every page and lands mid-sentence in a body:
        # the footer "System Reference Document 5.2.1" (often fused to its page
        # number), bare page numbers, and the CC licence line.
        FURNITURE = re.compile(r"^(System Reference Document|System Reference|\d{1,4}$"
                               r"|This work includes material|Creative Commons|CC BY)")
        body = [l for l in lines[j + 1:end]
                if l.strip() and not l.startswith("<<<PAGE") and not FURNITURE.match(l.strip())]
        out.setdefault(nm.lower(), " ".join(body).strip())
    return out

old = parse(f"{S}/srd/SRD-OGL_V5.1.norm.txt")
new = parse(f"{S}/srd/SRD-5.2.1.norm.txt")
desc = descriptions(f"{S}/srd/SRD-5.2.1.norm.txt")
delta = json.load(open(f"{S}/spell-delta.json"))
names = sorted(set(delta["added"]) | set(delta["changed"]))

rows = []
for n in names:
    r = new.get(n)
    if not r: continue
    title = " ".join(w if w.isupper() else w.capitalize() for w in n.split())
    f = [f'    :name "{title}"', f"    :key {kw(n)}", f'    :school "{r["school"]}"',
         f'    :level {r["level"]}', f"    :source {SRC_TAG}"]
    if r.get("casting"): f.append(f'    :casting-time "{r["casting"].title()}"')
    if r.get("range"):   f.append(f'    :range "{r["range"]}"')
    if r.get("duration"):f.append(f'    :duration "{r["duration"]}"')
    c = components(r.get("components"))
    if c: f.append(f"    :components {c}")
    if r.get("ritual"):  f.append("    :ritual true")
    d = desc.get(n)
    # No length cap: an earlier 1200-char slice cut the eleven longest spells
    # mid-word, and the cut was then misdiagnosed as a PDF column-flow problem.
    if d: f.append('    :description "%s"' % d.replace('\\','').replace('"', "'"))
    rows.append("   {\n" + "\n".join(f) + "}")

hdr = f'''(ns orcpub.dnd.e55.spells
  "SRD 5.2.1 spell delta: the spells that are NEW or MECHANICALLY CHANGED versus
   SRD 5.1. Spells identical in both editions are NOT repeated here -- they are
   inherited from orcpub.dnd.e5.spells, which stays the base.

   GENERATED from resources/public/dnld/SRD-5.2.1.pdf by scripts/srd/emit-e55.py;
   do not hand-edit. Every entry is derived from the SRD text itself, not from a
   third-party aggregator, so nothing outside the SRD can reach this file.

   SRD 5.2.1 content (c) Wizards of the Coast LLC, licensed under CC BY 4.0.
   https://creativecommons.org/licenses/by/4.0/")

(def source
  "The version tag every entry carries, for the edition filter."
  {SRC_TAG})

(def spells
  [
{chr(10).join(rows)}])
'''
open(f"{S}/e55_spells.cljc", "w", encoding="utf-8").write(hdr)
print(f"  emitted {len(rows)} spells -> {S}/e55_spells.cljc ({len(hdr)} bytes)")
print(f"    added:   {len(delta['added'])}")
print(f"    changed: {len(delta['changed'])}")
