# Branch changelog — `claude/character-portrait-generator-hOutO`

## Why this branch exists

Characters could only have a portrait by pasting an image URL. This adds a paper-doll
compositor: layered line art contributed by an illustrator, stacked in fixed z-order and
tinted from the character's own colours, with a seeded Randomize.

It is **not** AI image generation. Every asset is pre-authored art, and attribution is a
first-class part of the feature rather than a footnote.

Scope notes for review:

- `resources/public/image/portraits/` currently holds **silhouettes** — the anonymised
  48px alpha shapes from the illustrator's own tooling, un-squashed back to the source
  proportions and written to the paths the finished art will occupy. Real filenames, real
  counts, real registration; no line detail. A production run overwrites the same paths and
  no code changes.
- `:artist/name` is **deliberately nil** until the illustrator says how she wants to be
  credited. Every credit surface skips an unnamed artist, so they all correctly show
  nothing today and all light up together when it is filled in.
- `gap-inventory` is empty for the same reason: the manifest these silhouettes came from
  predates gap recording. Empty means "nothing marked pending", not "the set is finished".
- The art still needs a licence decision (`ARTISTS.md`, a directory `LICENSE`, and a
  carve-out from the root EPL-2.0). Not in this branch.

## Highlights

Characters can now be given a portrait built from layered art rather than a pasted URL —
pick or randomize each of ten layers, then colour hair, skin, eyes and clothing per
character, down to shading an individual piece. The result follows the character
everywhere its picture goes: the summary, the exported sheet, and the card a shared link
unfurls into, each carrying the artist's credit with it.

## Added

- Paper-doll portrait compositor: ten z-ordered layers, per-artist asset registry, seeded
  Randomize, and a colour model with four slots plus per-piece shade and override
  (`9e15b45`, `efce42e`).
- Three ways in, because the picture and the place you edit it were far apart: a launcher
  beside the Image URL field, a pencil on the summary thumbnail, and a Portrait builder tab
  that renders the compositor in place (`9e15b45`, `63eddd2`, `a1081f1`).
- Composed portraits print on the exported character sheet. The browser bakes its CSS-mask
  layers to a PNG because no URL can produce one, and the server embeds the bytes
  (`89ce91d`).
- Composed portraits render server-side for `og:image`, so a shared link unfurls with the
  character's own picture. A share crawler runs no JavaScript, so those pixels are made in
  Java2D rather than a browser (`150b78f`).
- Artist credit on every surface the picture reaches: under the summary thumbnail, in the
  share card's description, in the PDF's metadata, and burned into the composed image
  itself — the image is what people actually pass around, and it used to travel bare
  (`5450740`, `3f679fd`).
- Optional site mark up the right edge of shared images, read from `branding/app-url` and
  omitted when unset. Deliberately on the opposite edge from the artist credit, so trimming
  the advertising cannot take her name with it (`793fbd7`).
- Asset pipeline: `scripts/build-portrait-assets.py` resamples the illustrator's originals
  at full float precision and emits a manifest, and `scripts/import-silhouettes.py` unpacks
  the anonymised silhouettes to the same paths (`62f0646`, `3b887f9`).
- Pieces marked not-yet-drawn (her `no <name>.txt` convention) show as inert "soon"
  swatches, so a thin category reads as in progress rather than finished (`7e98065`).
- Light theme for the compositor, and `noai`/`noimageai` opt-outs on the pages, the
  portrait PNGs and `robots.txt` (`a0a8605`, `c403f0d`).

## Fixed

- Link-preview crawlers were blocked by a blanket `Disallow: /`, so every shared character
  link failed to unfurl on Discord, Mastodon, Slack, Telegram and X — all of which honour
  robots.txt. Facebook ignores it, which is why some previews worked and hid the problem
  (`850ce75`).
- `character-summary-for-id` returned only the `::se/summary` submap while `character-page`
  destructured that key back out of it, so **every** shared character link had an empty
  `og:title` and `og:image`. Pre-existing (`150b78f`).
- Reopening the compositor discarded a saved portrait: the draft was seeded through a
  getter that resolves against a *built* character while `db :character` holds the raw
  entity, so it silently yielded nil. Missed by 457 unit tests and caught by the first
  browser probe (`d0ba22f`).
- The exported sheet inherited its metadata from the third-party InDesign templates —
  Creator "Adobe InDesign CS6", empty Title and Author — so every file misreported its own
  provenance to anything that reads it (`6e12820`).
- Both rasterizers stretched each layer to fill the frame while the browser composites with
  `mask-size: contain`, so a shared portrait and a printed sheet came out 10% wider than
  the face the compositor showed (`3f679fd`).
- The artist was read from the `:artist/id` stored in the saved portrait, which is
  client-supplied EDN — deleting that one key detached the credit from art plainly in the
  registry, on every surface at once. Resolved through the registry by asset id now
  (`3f679fd`).
- Generated artwork was fitted for the PDF by a path that re-encodes as JPEG, which has no
  alpha, so the portrait printed inside a black rectangle (`a674342e`).
- The in-progress portrait crossed from one character to another: it lives at the top of
  app-db, and the drawer and the Portrait tab both seed it only when it is missing, so a
  switch from A to B left A's portrait to be saved onto B (`8fda24bc`).
- Pressing Save character before Save portrait then discarded those edits. The fix above
  compared `:db/id`, and a first save is exactly where a new character is given one, so the
  save's own re-set of the character read as a switch. The callers that continue a character
  now say so, which also tells two never-saved characters apart — both have a nil id, the
  one case the comparison could never see. New builds the blank character without going
  through `:set-character` at all, so it drops the draft in its own interceptor.
- A save that outlived the screen it started on then carried the wrong portrait. Saving is
  asynchronous and the autosave queue is throttled, so a save answers routinely after the
  builder has moved on -- toggle a prepared spell on one character from a list and the
  queued save for it lands while another is open. The response was treated as the character
  on screen either way, so the draft went with it. Nothing in the response can settle that,
  so each save now says what it is about: the manual one the epoch it was dispatched at
  (`:character-epoch` counts replacements, which an id cannot, since a first save is where
  the id appears), the autosave the id it posted.
- `node test/e2e/cljs-harness.js` depended on an untracked runner page under `target/`, so
  the command this branch is verified with failed on a clean checkout with a bare Playwright
  navigation error. The harness writes its own page now, and says which build step is
  missing instead of timing out.

## Changed

- `::char5e/portrait` is stored as one EDN string. `::se/values` is a Datomic component
  ref, so every key in it must be a registered attribute and none can hold a nested map;
  the first save would otherwise have failed the transaction (`efce42e`).
- Vollkorn moves to `resources/public/fonts/` so one copy serves both consumers — PDFBox
  reads it off the classpath, and the browser fetches it for the credit baked into the
  picture that ends up inside that same PDF. A 26 KB subset covers the credit line; the
  full face remains the fallback (`32e8eb7`, `0e0e140`).
- Generated artwork has its own weight ceiling. The 128k limit is what the builder
  advertises beside the Image URL field — a promise about uploads — and holding the app's
  own raster to it cost print resolution (`a674342e`).
- The builder tab bar wraps. A fourth tab ran the labels together at phone width
  (`a1081f1`).
