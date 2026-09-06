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
- `POST /image-probe`, which the builder asks as soon as a browser read fails:
  can THIS server fetch THAT picture? The bytes are kept for ten minutes, so the
  export that follows costs the host no second request, and a negative answer is
  remembered too. It answers a boolean and never the picture — the endpoint needs
  no login, and returning fetched bytes would make it a general-purpose proxy —
  and every address rule that guards the export guards it.
- A "Use copied image" button beside the field, so the last resort is one click
  rather than an instruction. It reads a picture the VIEWER has copied; it cannot
  do the copying, because a page-initiated copy of a cross-origin image puts its
  markup on the clipboard and not its pixels -- the same rule that taints the
  canvas, and the reason extensions can do this and pages cannot.
- Paste, for a host that lets nobody read its pictures. The clipboard carries the
  DECODED image -- the browser's own "Copy image" put it there -- so none of the
  host's rules reach it. Two clicks, and no download-and-upload round trip. This
  is the answer for Pinterest and anything else that refuses page and server
  alike.
- An upload under the Image URL field for a host that allows no read. It runs the
  same ceilings, and falls back to the image loader when `createImageBitmap`
  refuses a file the loader renders — it is the stricter decoder of the two.
- `test/browser/character_image_capture_e2e.js` drives both routes through the
  real app. The server refuses loopback addresses, so an image reaching a PDF from
  the test origin can only have arrived as bytes the browser read.

## Added

- `orcpub.image-url/advise`, which reads the address alone and catches most real
  mistakes before a request is made: a PAGE's address pasted instead of the
  picture's (Pinterest pin, Imgur gallery, Reddit post, Flickr, DeviantArt,
  ArtStation), a login wall (Instagram, Facebook), a missing or non-web scheme, a
  space from a half-copied link, and `http://`, which this page's CSP will not
  display whatever the host does. Dropbox `?dl=0` and Google Drive
  `/file/d/<id>/view` are offered as corrections to take or leave.

  Advice, never enforcement, and a correction only where it is mechanical --
  nothing guesses a picture's address from a page's. Debounced, because the field
  commits on every keystroke and advice that objects to `htt` on the way to
  `https://` teaches people to ignore advice.

## Changed

- **One line under a field, never four blocks.** A single unreachable picture
  could raise a scheme warning, a suggested correction, a fetch failure and a
  panel of controls at once -- six lines of prose and three controls to say that
  one picture could not be had. Only the most actionable of them now shows: a
  mechanical correction first, then what the address itself gives away, then what
  the server found, then that it simply did not load. The other ways in wait
  behind one disclosure, and most people never open it.
- Controls live outside notices. A notice says what is wrong; a red panel holding
  a button, a sentence and a file picker is a control surface wearing an error's
  colours, and both halves get harder to read. A correction is now a question --
  "Did you mean https://...?" -- under its notice, and supplying a picture by
  hand is its own labelled block.
- Field notices are a component rather than a red line. `.field-notice` carries a
  severity accent, a panel ground and two parts with two jobs: the fault in the
  severity colour, and the instruction -- the only part anyone acts on -- brighter
  and heavier beneath it. Run together in one colour and one weight, the vaguer
  half reads first and the useful half is skipped.
- **`.red` was unreadable on the app's own background.** `#9a031e` sits at about
  9:1 on white and about 2:1 on near-black, less than half the readable minimum,
  and the app is dark by default. The dark theme now takes a lighter red and the
  light theme keeps the deep one; the same colour cannot serve both. This reaches
  every use of `.red`, not just these fields.
- Inline rather than a hover tooltip, deliberately: these notices carry buttons,
  and `.tooltiptext` disappears when the pointer moves toward it, is fixed at
  130px, and is absent on mobile.


- The builder says nothing about pasting or uploading until BOTH routes are known
  to be shut: the browser refused, and the server's own answer came back no. Most
  hosts that refuse the browser serve the server perfectly well, so speaking up
  earlier asked people to supply a picture that was about to arrive. Measured: of
  sixteen common portrait hosts, nine let the browser read (Imgur, Discord,
  Fandom, Wikimedia, ArtStation, DeviantArt, Google, Tumblr, githubusercontent)
  and most of the rest allow the server.
- Exporting is held while a picture is still being read, so the browser's bytes
  win that race instead of falling through to the server. `capture` carries a
  deadline, so a read always ends and the hold is bounded.
- An oversized picture gives up SIZE before quality, down to what the sheet can
  actually show -- the portrait box is 2.35 x 3.15 inches, so 945px on the long
  edge at 300dpi, against a 200x100 thumbnail on screen. Pixels past that cost
  nothing visible; quality costs something immediately. A picture already smaller
  than that is never scaled, only re-compressed, and going below it happens last.
  Measured: a 5.8 MB noise PNG leaves the browser at 92 KB and full quality, where
  spending quality first had produced 37 KB and a worse picture.


- Pictures are read when the thumbnail loads and when the export panel mounts,
  never on the export click: the export is a synchronous form submit into a new
  tab, and an await in between spends the user activation that keeps that tab from
  being blocked. Bytes are held in app-db keyed by URL, outside the character
  entity — that entity is what gets persisted, and localStorage has a ceiling.
- `docs/CHARACTER-IMAGE-FETCH.md` leads with the browser path; the server fetch is
  documented as the fallback it now is.

## Changed

- `/image-probe` answers a REASON rather than a boolean, and the builder turns it
  into wording split by what is worth fixing: the link (`not-found`, `redirect`,
  `not-an-image`, `unreachable`, `blocked-address`), the picture (`refused`,
  `too-large`, `too-many-pixels`, `timeout`, `host-error`), or simply waiting
  (`rate-limited`). Telling someone to copy a picture when they have mistyped a
  link is not help. The server never sends a sentence, so nothing it says reaches
  a person unedited, and every address refusal collapses to one code so the
  endpoint cannot be read as a map of what this network can reach.

## Fixed

- **A picture the host served happily was refused for weight.** The ceiling on
  what the server would DOWNLOAD and the ceiling on what may go INTO the PDF were
  the same 128 KB, so a Pinterest portrait (393 KB, served with a 200) and a
  Wikimedia one (224 KB) were dropped although nothing had blocked them. The two
  are now separate — 2 MB down, 128 KB into the document — and a heavy picture is
  scaled to the printed size and re-encoded to fit, exactly as the browser does.
  What bounds the danger was never this number: the pixel budget caps the decode
  and the transfer deadline caps the time, and both are unchanged. A Pinterest
  portrait now reaches the sheet with nothing asked of the user.


- The builder flashed "Image failed to load" at pictures that were fine.
  `image-error` dispatched when it was CALLED, at render time, rather than
  returning a handler -- so every fresh URL was marked failed before the browser
  had tried it, and only the load took the mark back.

- A picture whose host allows no read stopped displaying in the builder. The
  builder marks a URL failed optimistically as soon as the thumbnail renders and
  relies on the load to take that back; the load handler had captured the flag at
  the moment it was built, when it was still clear, so the mark was never
  withdrawn. The clear no longer reads the flag, and is a no-op when there is
  nothing set, so an ordinary load does not count as an edit.

## Removed

- `create-monsters-pdf`, which was private with zero callers, and the
  `draw-text-from-top` helper, `HELVETICA_OBLIQUE` font and
  `orcpub.dnd.e5.monsters` require that it was the only user of.
