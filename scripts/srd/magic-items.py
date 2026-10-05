#!/usr/bin/env python3
"""Magic items from an SRD text, name + type + rarity + attunement + body.

Anchor is the TYPE line ("Armor (Any Medium or Heavy...), Uncommon"); the name is
the line above it and the body runs to the next item's name. 5.1 lower-cases the
rarity and keeps it on one line; 5.2.1 title-cases it and often wraps it onto the
next line, so rarity is read from either. No length caps and no guessed offsets --
both of those produced silently wrong output on the spell pass.
"""
import re, sys, json

TYPE = (r"^(Wondrous Item|Wondrous item|Armor|Weapon|Potion|Ring|Rod|Scroll|Staff|Wand|"
        r"Ammunition)\b[^\n]*")
RARITY = re.compile(r"\b(Common|Uncommon|Rare|Very Rare|Legendary|Artifact)\b", re.I)
ATTUNE = re.compile(r"requires? attunement([^)]*)", re.I)
NAME   = re.compile(r"[A-Z][A-Za-z'’/\-+ 0-9]{2,44}$")
FURNITURE = re.compile(r"^(System Reference Document|\d{1,4}$|This work includes|Creative Commons)")

def parse(path):
    lines = open(path, encoding="utf-8", errors="replace").read().split("\n")
    anchors = []
    for i, l in enumerate(lines):
        if not re.match(TYPE, l):
            continue
        head = l
        if not RARITY.search(head) and i + 1 < len(lines):      # 5.2.1 wraps rarity
            head = l + " " + lines[i + 1]
        if not RARITY.search(head):
            continue
        j = i - 1
        while j > 0 and (not lines[j].strip() or lines[j].startswith("<<<PAGE")
                         or FURNITURE.match(lines[j].strip())):
            j -= 1
        nm = lines[j].strip()
        if NAME.fullmatch(nm):
            anchors.append((j, nm, i, head))
    out = {}
    for n, (nidx, nm, i, head) in enumerate(anchors):
        end = anchors[n + 1][0] if n + 1 < len(anchors) else min(i + 80, len(lines))
        start = i + (2 if head != lines[i] else 1)
        body = [l for l in lines[start:end]
                if l.strip() and not l.startswith("<<<PAGE") and not FURNITURE.match(l.strip())]
        rar = RARITY.search(head)
        # "(requires attunement)" WRAPS -- 5.1 breaks it as "(requires" / "attunement)".
        # Searching only the header missed every wrapped one and invented 16 edition
        # "changes" that were really detection failures. Join a window instead.
        att = ATTUNE.search(" ".join(lines[i:i + 4]))
        out.setdefault(nm.lower(), {
            "name": nm,
            "type": re.sub(r"[,;]\s*$", "", re.split(RARITY, head)[0]).strip().rstrip(","),
            "rarity": rar.group(1).title() if rar else None,
            "attunement": bool(att),
            "body": " ".join(body).strip()})
    return out

if __name__ == "__main__":
    a, b = parse(sys.argv[1]), parse(sys.argv[2])
    print(f"  5.1 parsed:   {len(a)}")
    print(f"  5.2.1 parsed: {len(b)}")
    added = sorted(set(b) - set(a)); removed = sorted(set(a) - set(b))
    changed = [n for n in sorted(set(a) & set(b))
               if (a[n]["rarity"], a[n]["attunement"]) != (b[n]["rarity"], b[n]["attunement"])]
    print(f"    added in 5.2.1:   {len(added)}")
    print(f"    removed:          {len(removed)}")
    print(f"    rarity/attune changed: {len(changed)}")
    print(f"    same:             {len(set(a)&set(b))-len(changed)}")
    print(f"\n    sample added: {added[:6]}")
    if len(sys.argv) > 3: json.dump({"items": b, "added": added, "changed": changed}, open(sys.argv[3], "w"), indent=1)
