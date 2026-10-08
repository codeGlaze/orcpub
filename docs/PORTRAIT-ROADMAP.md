# Portrait roadmap

Where the portrait system stands, what a deployment needs, and what comes in later updates.

**Working rule for every visual item:** judge it on whole portraits rendered by the app, on
light to dark skin, at the size the builder shows them, beside the version already
approved. A new method replaces a working one only after it wins there. Single-piece views
and amplified checks find problems; they do not decide them.

---

## Shipping in this branch

- The portrait builder: layered pieces, Randomize, per-character colours with per-piece
  overrides, and the credit on every surface the picture reaches.
- Hair: ombré with streaks along the drawn strands, shine, dyed bangs, shadows on the face.
- Eyes: pupils, a second colour, brightness, glowing eyes, painted-in whites for styles drawn
  without them.
- Skin: blush, freckles, shading under the hair.
- Mood light: torch, moon and arcane; edge, cast or rim; from any side.
- Artist accounts, preferred names, and artist-editable credit.
- Art prepared in the Silhouette Loom (`agents/develop`, `agents/loom/`): eye placements,
  the see-through fill, patches for gaps between pieces.

The generated scalp piece is retired: the art now meets (hair front 02 was patched to reach
bangs 03 in pack v1.775).

## Deploying

1. Unpack the art pack (currently **v1.775**, exported from the Loom with "Fill see-through
   insides" ticked) over `resources/public/image/portraits/`, or the volume that serves it.
2. Nothing else for the art: the image build writes the strand files beside it (the Dockerfile
   runs `orcpub.portrait-pack.strands`, which only fills in missing or stale ones), so the image
   carries them. Running from source without that step still works: the server then works a
   missing one out on first request.
3. Configure, as needed: `PORTRAIT_ARTISTS` (credits and artist accounts), `PROFILE_ENCRYPTION_KEYS`
   (preferred names), `APP_URL` (links in artist emails), `EMAIL_ADMIN_TO`.
4. A What's New entry for the builder, in whichever release ships it. Draft:
   - **Group:** Characters · **Icon:** `fa-user-circle`
   - **Headline:** Build a portrait for your character
   - **Detail:** Pick or randomize layered art by a real illustrator, colour hair, skin, eyes and
     clothing, and add an ombré, freckles, glowing eyes or a torch-lit edge. The portrait shows
     on the sheet, the summary and shared links, credited to the artist.

## Rolling enhancements

Each of these works today or is hidden; none blocks shipping.

### Art and look

- **Mood light details.** Torch edge, moonlight and arcane each want small tweaks, judged on
  dark skin first: the reflected-light base with a long soft taper is the direction chosen.
- **Skin-tone rendering.** Warm and cool variation, flush where blood sits, light on the planes
  that catch it. Needs its own mock-up rounds.
- **Split dye** (Developer mode only). Every automatic split tried so far regressed somewhere.
  The open route is the artist marking each piece's parting in the Loom.
- **Ombré under-layer** and the **alternative far iris** (Developer mode): decisions pending.
- **Bangs 01 hairline slivers.** Skin shows between its strand tips at the hairline; it reads as
  a fringe. If wanted closed, it is a Patch gaps job in the Loom.
- **Further healing of the art** (closing holes the fill does not reach). Parked with what was
  learned in `agents/loom/README.md`: the artist draws some marks with transparency, and no
  automatic rule tried so far tells them from holes.

### Strand files

- **One file per layer, per artist** instead of one per piece: people click through every option
  in a category, so one request per category serves them all, and an artist's files never change
  when another artist's pack is added (it also suits filtering by artist later). Today's files
  are internal (saved portraits never store their addresses), so switching costs no data.

### Loom

- Rebuilding the tint picker, bundled placements ignoring the layer, a separate message area,
  remembering commission-kit fields, zip dates and UTF-8 names, keyboard access, memory use on
  large packs.

### Code

- **Comment debt.** 87 long docstrings and comments from this branch are recorded in
  `test/comment-baseline.edn` (the house check fails on new ones). Shorten each to spec, move
  its history to `docs/kb/`, then prune the baseline.

### Before or at release

- **Art licence:** `ARTISTS.md`, a `LICENSE` in the portraits directory, and a carve-out from the
  root EPL-2.0, once the commission paperwork is checked and the artist agrees.
