# Portrait roadmap

What is left on the portrait system, and how each piece gets figured out.

**Working rule for every visual item:** each round of mock-ups shows three
columns on the artist's pack as delivered:
1. the version already approved, or the current build;
2. a careful change to it, one thing at a time;
3. any new method.

A new method replaces a working one only after it wins side by side. A result
that fails its own goal is fixed or reported as a failure, never sent as a
candidate.

---

## 1. Ship the strand files with the art

**Goal:** no visitor's browser and no server start ever works out a strand
field. They are made once, next to the art.

**Plan**
- A deploy step: `lein run -m orcpub.portrait-pack.strands <art-dir>` wherever
  the real art is unpacked, before the image is built or the volume mounted.
- Belt and braces: on start, the server checks for any hair piece without a
  `.strands.png` and logs one line per piece. It writes none itself, because
  the art directory may be read-only in Swarm.
- A browser probe that fails if any `.strands.png` request returns 404 on real
  art.

**Needs:** where the art lives in the Swarm deploy (a volume, or baked into
the image).

## 2. Open decisions (yours, no work until chosen)

| Item | Options | State |
|---|---|---|
| Style 1 far iris | your placement / my edit | developer toggle beside the face |
| Style 3 whites | place lower lids in the Loom, or not | Loom already supports it |
| Ombré tricks | under-layer on by default, light-direction dropped (my pick), or leave both | both off by default |

## 3. Split dye, reworked

**Goal:** each hair piece takes its own split or stripe, following its own
strands. The pieces' lines need not meet. The fringe can be split, striped,
flipped or left one colour.

**What we know**
- Per-piece lines traced up the strands worked on the spiky bangs.
- They failed on the long fringe: long overlapping strands confuse the
  direction read from the ink, and the traces wander.
- One line across every piece is wrong in principle. It is hidden outside
  Developer mode.

**Candidates, mocked together**
- **A, iterate the spiky-bangs method:** smooth the direction field more
  widely, weight it by how clearly the lines agree, and stop a trace that
  turns more than a set angle. Measure stripes across the strands, not down
  them. Spiky bangs must stay as good as they are now.
- **B, artist marks (new):** in the Loom, the artist clicks each hair piece's
  parting point and, where needed, draws two or three guide strokes along the
  strands. The split follows her strokes. That is about 11 hair pieces, a few
  clicks each.
- **C, per-piece controls:** a Line slider and a side (split, flip, none)
  for each hair piece, starting from A's placement.

**Order:** mock A and C on the long fringe, the spiky bangs and the long back
hair. Ask the artist for B only if A fails the fringe.

**Done when:** the long fringe takes a clean split and a clean stripe in all
three test portraits, and nothing regresses on the spiky bangs.

## 4. Glowing eyes

**Goal:** fiend, celestial and undead eyes that visibly light the skin round
them.

**What we know:** the first try was a pale iris with no halo. The second, a
two-radius bloom, read better but was still weak.

**Candidates**
- **Iterate the bloom:** a hot core (iris near white in its colour), a tight
  bloom and a wide soft bloom, screened, clipped to skin and lashes so it
  never tints hair. Strength and colour controls.
- **New:** light from the eyes added to the cast-shadow overlay. The
  surrounding skin is lit by distance from the iris, falling off with the
  square.

**Done when:** at thumbnail size the glow reads from across the room, and the
iris stays coloured, not white.

## 5. Mood light

**Goal:** torch, moon and arcane light from a chosen side.

**What we know:** a tint plus a blurred-edge rim read as a dirty outline on
dark hair and grey skin.

**Candidates**
- **Iterate:** colour cast only, per region (skin, hair, shirt), screened on
  the lit side and multiplied on the far side. No rim.
- **New, a form-aware rim:** estimate how each piece's surface turns from the
  distance to its own edge (a bevel). The rim lights only edges facing the
  light, never the ink.

**Done when:** each preset reads as light, not paint, on dark and light hair
and on four skin tones.

## 6. Skin form shading

**Goal:** the skin reads as round, not flat.

**Candidates**
- **Iterate the shadow overlay:** occlusion under the jaw onto the neck,
  under the hair line, and in the ear's bowl, using masks we already have.
- **New:** the bevel from item 5 on the head silhouette for soft cheek and
  jaw shading, plus a gentle light from the upper left.
- Tested on pale, deep brown, green, blue and red skin. Strength slider,
  default subtle.

**Done when:** shading reads at portrait size and no skin tone turns muddy.

## 7. Artist calibration in the Loom

**Goal:** new art can be set up without a developer, and with no more than a
few clicks per piece from the artist.

**Plan**
- The Loom gains per-piece marks:
  - hair: parting point and optional guide strokes (item 3);
  - casts a shadow: yes or no;
  - bangs default: roots, tipped or dyed.
- These export in the manifest. `orcpub.portrait-pack.import` writes them to
  `loom.edn`, reporting what changed as it already does.
- `pieces.edn` remains for our own overrides.

**Needs:** your go-ahead before anything is asked of the artist.

## 8. Release notes for changed portraits

Existing portraits change in two ways:
- every iris now takes light from below;
- "At an angle" ombrés are measured from the crown.

One line each in What's New.

---

## Suggested order

1. Item 1 (deploy) and item 8 (notes): small, no art judgement.
2. Item 3 (split): the item with the most back and forth.
3. Items 4 and 5 (glow, mood light), mocked together; they share the bloom
   and bevel work.
4. Item 6 (skin), reusing the bevel.
5. Item 7 (Loom): after 3 settles what marks the artist actually needs.
