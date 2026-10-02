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

## 3. Split dye: parked

Hidden outside Developer mode. Every automatic split tried so far regressed
somewhere, or left artifacts and hairlines that make no sense:
- one line across every piece;
- a line per piece traced up the strands (good on the spiky bangs only);
- the trace with wider smoothing and a turn limit (broke the spiky bangs);
- a straight line per piece.

Reading the hair's shape alone has not been enough. Don't restart from any of
these. The open route is the artist marking each piece's parting, with a few
guide strokes, in the Loom (item 7), if you decide to ask her.

## 4. Glowing eyes: built

A hot core in the iris and a wide halo onto the skin, never onto the hair over
the face. Five presets, a custom colour and a strength slider, in the Eyes
panel.

## 5. Mood light: built

Torch, moon and arcane, each in three styles, under the colour strip:
- **Edge:** a lit edge along the side facing the light.
- **Cast:** the lit side warmed or cooled and the far side shaded.
- **Rim:** Cast plus a rim on the outer edge of the whole portrait. Worked out
  per piece, it lit inside edges and drew the head's outline and a hairline
  across the face.

The light can come from the left, right, above or below. Each mood starts
from its own side.

## 6. Skin

- **Built: Shade.** A soft dimming of the skin near the hair over it, as a
  slider, off by default.
- **To do: a skin-tone render.** What was actually wanted is skin painted with
  tone: warm and cool variation, flush where blood sits, light on the planes
  that catch it. It needs its own mock-up rounds.

## 7. Artist calibration in the Loom

**Goal:** new art can be set up without a developer, and with no more than a
few clicks per piece from the artist.

**Plan**
- The Loom gains per-piece marks:
  - hair: parting point and optional guide strokes (for item 3, if revived);
  - casts a shadow: yes or no;
  - bangs default: roots, tipped or dyed.
- These export in the manifest. `orcpub.portrait-pack.import` writes them to
  `loom.edn`, reporting what changed as it already does.
- `pieces.edn` remains for our own overrides.

**Needs:** your go-ahead before anything is asked of the artist.

## 8. What's New

The portrait builder has not shipped yet, so no saved portraits change. When
it ships it needs one What's New entry for the builder as a whole.

---

## Suggested order

1. Item 1, the deploy step (yours).
2. Item 6, the skin-tone render: mock-up rounds.
3. Item 7, the Loom: only if you decide to ask the artist for marks.
