# Branch changelog — `feature/browser-side-character-images`

## Why this branch exists

A character's portrait was stored as a URL and fetched by the SERVER at export
time. Hosts that block hotlinking judge the Referer and the datacenter IP, so the
browser's request succeeds where the server's does not: the thumbnail showed in the
builder and the PDF came out blank. That fetch was also the only outbound request
the app makes to a caller-supplied address.

## Highlights

The browser reads the picture and the export carries the bytes. The server's fetch
is now the fallback for a picture the browser was refused, and when neither is
allowed the builder says so and offers an upload — which no host has a say in.

## Added

- `orcpub.image-capture` reads a character's picture in the browser: a
  CORS-attributed `<img>` drawn to a canvas, scaled to the size the sheet prints
  and encoded until it fits the 128 KB ceiling. Only the canvas route exists —
  the app's CSP is `connect-src 'self'`, so `fetch` to an image host is blocked
  and attempting it would log a violation on every export, while `img-src` allows
  `https:`.
- `pdf/decode-image-bytes` takes those bytes on the server. The same 128 KB and
  2000×2000 ceilings as `safe-image-bytes`, checked against the ENCODED length
  first so an oversized image never becomes a byte array, and the format read from
  the bytes rather than from the mime type the client claimed.
- The export spec carries `:image-data` and `:faction-image-data`; when they are
  present `/character.pdf` does not fetch at all.
- An upload under the Image URL field for a host that allows no read. It runs the
  same ceilings, and falls back to the image loader when `createImageBitmap`
  refuses a file the loader renders — it is the stricter decoder of the two.
- `test/browser/character_image_capture_e2e.js` drives both routes through the
  real app. The server refuses loopback addresses, so an image reaching a PDF from
  the test origin can only have arrived as bytes the browser read.

## Changed

- Pictures are read when the thumbnail loads and when the export panel mounts,
  never on the export click: the export is a synchronous form submit into a new
  tab, and an await in between spends the user activation that keeps that tab from
  being blocked. Bytes are held in app-db keyed by URL, outside the character
  entity — that entity is what gets persisted, and localStorage has a ceiling.
- `docs/CHARACTER-IMAGE-FETCH.md` leads with the browser path; the server fetch is
  documented as the fallback it now is.

## Removed

- `create-monsters-pdf`, which was private with zero callers, and the
  `draw-text-from-top` helper, `HELVETICA_OBLIQUE` font and
  `orcpub.dnd.e5.monsters` require that it was the only user of.
