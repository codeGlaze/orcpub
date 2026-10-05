#!/usr/bin/env python3
"""Classify every item name in a content file against the extracted SRD text.

Imported content is trusted far more readily than it should be: the open5e import of SRD 5.2
claimed in its own docstring to validate against the SRD PDFs and never did, and roughly a
fifth of its magic items turned out to be third-party content that is in no SRD at all. A
name-level check is cheap and catches that class; scan-licensed-content.sh cannot, because it
looks for WotC trademarks and third-party names are not trademarked.

Verdicts:
  exact      the name (or its de-parenthesised base) appears in the target SRD
  expansion  a shortened form appears -- "Demon Breastplate" from SRD "Demon Armor"
  stale      absent from the target SRD but present in an older one
  non-srd    in no SRD text supplied. The ones to look at.
  malformed  unbalanced parentheses in the name, i.e. a broken import, not a naming difference

Usage:
  validate-names.py --srd 5.2.1=SRD-5.2.1.norm.txt [--srd 5.1=...] FILE.cljc [FILE...]
  ... --manifest out.tsv     also write a per-name manifest

The FIRST --srd is the target; later ones are only consulted to tell stale from non-srd.
Exits 1 when any name lands in non-srd or malformed, 0 otherwise.
"""
import argparse, re, sys, unicodedata, csv

ARMOR = (r"(breastplate|chain-?\s?mail|chain-?\s?shirt|half-?\s?plate|hide|leather|padded"
         r"|plate|ring mail|scale mail|splint|studded leather|shield|armor)")
# Attribution and licence lines sit in :name position in some generated files.
META = re.compile(r"^(5th edition|system reference|creative commons|wizards of the coast)", re.I)


def norm(s):
    s = unicodedata.normalize("NFKD", s).replace("’", "'").replace("‘", "'")
    return re.sub(r"\s+", " ", re.sub(r"[^a-z0-9' ]+", " ", s.lower())).strip()


def candidates(name):
    """Forms the SRD might use for the same item, longest first."""
    base = re.sub(r"\s*\([^)]*\)\s*$", "", name)
    words = base.split()
    return ([name, base]
            + [" ".join(words[:k]) for k in range(len(words) - 1, 0, -1)]
            + [re.sub(ARMOR, "armor", base, flags=re.I)])


def classify(name, texts, target):
    if name.count("(") != name.count(")"):
        return "malformed", ""
    base = re.sub(r"\s*\([^)]*\)\s*$", "", name)
    if norm(name) in texts[target] or norm(base) in texts[target]:
        return "exact", target
    if any(norm(c) and norm(c) in texts[target] for c in candidates(name)):
        return "expansion", target
    for ed, txt in texts.items():
        if ed != target and any(norm(c) and norm(c) in txt for c in candidates(name)):
            return "stale", ed
    return "non-srd", ""


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--srd", action="append", required=True, metavar="EDITION=PATH")
    ap.add_argument("--manifest")
    ap.add_argument("files", nargs="+")
    a = ap.parse_args()

    texts, order = {}, []
    for spec in a.srd:
        ed, path = spec.split("=", 1)
        texts[ed] = norm(open(path, encoding="utf-8", errors="replace").read())
        order.append(ed)
    target = order[0]

    rows, bad = [], 0
    for path in a.files:
        src = open(path, encoding="utf-8", errors="replace").read()
        names = [n for n in dict.fromkeys(re.findall(r':name\s+"([^"]{2,60})"', src))
                 if not META.match(n)]
        counts = {}
        for n in names:
            verdict, found = classify(n, texts, target)
            counts[verdict] = counts.get(verdict, 0) + 1
            rows.append((path, n, verdict, found))
            if verdict in ("non-srd", "malformed"):
                bad += 1
        total = len(names) or 1
        print(f"  {path}  ({len(names)} names)")
        for v in ("exact", "expansion", "stale", "non-srd", "malformed"):
            if counts.get(v):
                print(f"      {v:<11}{counts[v]:>6}  {counts[v] * 100 // total:>3}%")

    if a.manifest:
        with open(a.manifest, "w", newline="", encoding="utf-8") as f:
            w = csv.writer(f, delimiter="\t")
            w.writerow(["file", "name", "verdict", "found_in"])
            w.writerows(rows)
        print(f"\n  manifest: {a.manifest} ({len(rows)} rows)")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
