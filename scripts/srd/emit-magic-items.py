#!/usr/bin/env python3
"""Emit the SRD 5.2.1 magic-item delta as orcpub .cljc data (convention B)."""
import re, os, sys, json
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)) or ".")
from mitems import parse
S = os.path.dirname(os.path.abspath(__file__)) or "."
SRC_TAG = ":srd-2024"

TYPE_KW = {"wondrous item":":wondrous-item","armor":":armor","weapon":":weapon","potion":":potion",
           "ring":":ring","rod":":rod","scroll":":scroll","staff":":staff","wand":":wand",
           "ammunition":":ammunition"}
def type_kw(t):
    head = re.split(r"[ (]", (t or "").strip().lower())[0]
    for k, v in TYPE_KW.items():
        if (t or "").strip().lower().startswith(k): return v
    return TYPE_KW.get(head, ":wondrous-item")
def rarity_kw(r): return ":" + r.lower().replace(" ", "-") if r else ":uncommon"
def esc(s): return s.replace("\\", "").replace('"', "'")

a, b = parse(f"{S}/srd/SRD-OGL_V5.1.norm.txt"), parse(f"{S}/srd/SRD-5.2.1.norm.txt")
added   = sorted(set(b) - set(a))
changed = [n for n in sorted(set(a) & set(b))
           if (a[n]["rarity"], a[n]["attunement"]) != (b[n]["rarity"], b[n]["attunement"])]
rows = []
for n in added + changed:
    it = b[n]
    f = [f'    ::mi/name "{esc(it["name"])}"',
         f'    ::mi/type {type_kw(it["type"])}',
         f'    ::mi/rarity {rarity_kw(it["rarity"])}',
         f"    :source {SRC_TAG}"]
    if it["attunement"]: f.append("    ::mi/attunement true")
    if it["body"]:       f.append(f'    ::mi/description "{esc(it["body"])}"')
    rows.append("   {\n" + "\n".join(f) + "}")

hdr = f'''(ns orcpub.dnd.e55.magic-items
  "SRD 5.2.1 magic-item delta: items that are NEW in 5.2.1, or whose rarity or
   attunement changed versus SRD 5.1. Items identical in both editions are NOT
   repeated -- they stay in orcpub.dnd.e5.magic-items, which remains the base.

   GENERATED from resources/public/dnld/SRD-5.2.1.pdf by scripts/srd/emit-magic-items.py;
   do not hand-edit. Derived from the SRD text, never from a third-party aggregator.

   Keys are alias-qualified to e5.magic-items on purpose. A bare ::name here would
   auto-resolve to orcpub.dnd.e55.magic-items/name -- a DIFFERENT keyword that no
   consumer reads, which compiles cleanly and leaves the data inert.

   SRD 5.2.1 content (c) Wizards of the Coast LLC, licensed under CC BY 4.0.
   https://creativecommons.org/licenses/by/4.0/"
  (:require [orcpub.dnd.e5.magic-items :as mi]))

(def source
  "The version tag every entry carries, for the edition filter."
  {SRC_TAG})

(def magic-items
  [
{chr(10).join(rows)}])
'''
open(f"{S}/e55_magic_items.cljc", "w", encoding="utf-8").write(hdr)
print(f"  emitted {len(rows)} items ({len(added)} added, {len(changed)} changed) -> {len(hdr)} bytes")
