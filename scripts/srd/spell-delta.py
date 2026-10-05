#!/usr/bin/env python3
"""Spell delta between two SRD editions, derived from the PDFs' text alone.

No open5e, no 5etools: both editions come from the SRDs already in the repo's history,
so nothing third-party can leak into the result.

Two things the naive version got wrong, both recorded because they silently halve or
inflate the answer:
  - A spell header wraps. "Level 3 Evocation (Cleric, Druid, Paladin, Ranger," continues
    onto the next line, so the line above "Casting Time:" is often just "Ranger)". Missing
    that drops 38 of 339 entries in 5.2.1 and inflates the "removed in 5.2.1" column with
    spells that were simply unparsed.
  - 2024 renamed values without changing them: "1 action" became "Action". Comparing raw
    strings reports 250 of 280 shared spells as changed, which says nothing. Values are
    normalized before comparison and ritual is lifted out as its own flag.
"""
import re, json, sys

ORD = {"1st":1,"2nd":2,"3rd":3,"4th":4,"5th":5,"6th":6,"7th":7,"8th":8,"9th":9}
H_NEW = re.compile(r"^Level (\d) ([A-Z][a-z]+)\b"); C_NEW = re.compile(r"^([A-Z][a-z]+) Cantrip\b")
H_OLD = re.compile(r"^([0-9][a-z]{2})-level ([a-z]+)\b", re.I); C_OLD = re.compile(r"^([a-z]+) cantrip\b", re.I)
NAME  = re.compile(r"[A-Z][A-Za-z'’/\- ]{2,40}$")

def header(ln):
    for rx, f in ((H_NEW, lambda m:(int(m.group(1)), m.group(2))), (C_NEW, lambda m:(0, m.group(1))),
                  (H_OLD, lambda m:(ORD.get(m.group(1).lower()), m.group(2))), (C_OLD, lambda m:(0, m.group(1)))):
        m = rx.match(ln)
        if m: return f(m)
    return None

def norm_val(v):
    """2024 renamed values without changing them; compare meaning, not spelling."""
    s = v.lower().strip().rstrip(".")
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"^1 (action|bonus action|reaction)$", r"\1", s)
    s = re.sub(r"\bself\b.*", "self", s) if s.startswith("self") else s
    s = s.replace("concentration, up to", "concentration up to")
    return s

def parse(path):
    lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
    out = {}
    for i, ln in enumerate(lines):
        if not ln.startswith("Casting Time:"): continue
        j = i - 1
        while j > 0 and (not lines[j].strip() or lines[j].startswith("<<<PAGE")): j -= 1
        hv = header(lines[j]); back = 0
        while hv is None and back < 2:              # the header wraps; walk up
            j -= 1; back += 1
            while j > 0 and (not lines[j].strip() or lines[j].startswith("<<<PAGE")): j -= 1
            hv = header(lines[j])
        if hv is None: continue
        lvl, sch = hv
        k = j - 1
        while k > 0 and (not lines[k].strip() or lines[k].startswith("<<<PAGE")): k -= 1
        nm = lines[k].strip()
        if not NAME.fullmatch(nm): continue
        ct = ln.split(":", 1)[1].strip()
        ritual = bool(re.search(r"\britual\b", ct, re.I))
        rec = {"level": lvl, "school": sch.lower(), "ritual": ritual,
               "casting": norm_val(re.sub(r"\s*(or\s+)?ritual\s*$", "", ct, flags=re.I).rstrip(" ,"))}
        for off in range(1, 6):
            if i + off < len(lines):
                for f in ("Range:", "Components:", "Duration:"):
                    if lines[i+off].startswith(f):
                        rec[f.rstrip(":").lower()] = norm_val(lines[i+off].split(":", 1)[1])
        out.setdefault(nm.lower(), rec)
    return out

if __name__ == "__main__":
    old, new = parse(sys.argv[1]), parse(sys.argv[2])
    COMPARE = ("level", "school", "ritual", "casting", "range", "duration")
    changed = {}
    for n in sorted(set(old) & set(new)):
        d = {k: (old[n].get(k), new[n].get(k)) for k in COMPARE
             if old[n].get(k) is not None and new[n].get(k) is not None and old[n][k] != new[n][k]}
        if d: changed[n] = d
    res = {"added": sorted(set(new)-set(old)), "removed": sorted(set(old)-set(new)),
           "changed": changed, "identical": len(set(old)&set(new))-len(changed)}
    print(f"  parsed   5.1={len(old)}  5.2.1={len(new)}")
    print(f"  added    {len(res['added'])}")
    print(f"  removed  {len(res['removed'])}")
    print(f"  changed  {len(changed)}")
    print(f"  same     {res['identical']}")
    if len(sys.argv) > 3: json.dump(res, open(sys.argv[3], "w"), indent=1)
    print()
    for n, d in list(changed.items())[:8]: print(f"    {n:<24} {d}")
