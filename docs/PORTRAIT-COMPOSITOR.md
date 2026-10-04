# Portrait compositor: why the code is the way it is

The paper-doll portrait is drawn by three renderers, credited on every surface it reaches,
and stored as client-supplied EDN. Most of its code guards against something that went wrong
once. The docstrings say what each function does; this page keeps the reasons and the
measurements, so a change can be checked against them. Tinting has its own page,
[PORTRAIT-TINTING.md](PORTRAIT-TINTING.md).

## Three renderers, one geometry

The drawer stacks tinted divs in the DOM, the PDF export bakes a canvas in the browser, and
the share card is composed in Java2D because a crawler runs no JavaScript. They cannot share
drawing code, so `portrait_layout.cljc` holds every number they share and each renderer asks
it rather than deriving its own. Before it existed, each difference was a bug or a visible
mismatch:

- Both rasterizers stretched each layer to fill the frame while the DOM composites with
  `mask-size: contain`, so a shared portrait and a printed sheet came out 10% wider than the
  face in the drawer (8:11 art in a 4:5 frame). `contain-rect` is the DOM's behaviour,
  because the DOM is what a person looks at while choosing.
- The credit was sized with `round` in the browser and `int` on the server: at a 750px frame
  it was 20px with a 2px halo in a PDF and 19px with a 1px halo on a share card. The layout
  rounds, as the browser did.

## Rendering on the server

`portrait_render.clj` exists for the share card: a crawler reads `og:image` from the page
HTML and fetches it, with no browser to bake the layers. It needs no new dependency. Vector
assets reuse `pdf/svg-path-ops`, whose output maps directly onto a Java2D `Path2D`, and
raster assets are read off the classpath and tinted by `multiply!`.

- **Every failure means no portrait, never an exception.** A share card without a picture is
  a state the page already handles; a throw would cost the character their page.
- **`asset-source` reads only data URIs and site-absolute paths.** An off-site URL returns
  nil, so rendering a share card can never make the server fetch an arbitrary URL. It reads
  both forms because the registry moved from inline data URIs to files under
  `resources/public` when the illustrator's inventory arrived.
- **The credit faces are Vollkorn files shipped with the app**, not
  `java.awt.Font/SANS_SERIF`. That is a logical family resolved through the host's
  fontconfig, so a slim container renders whatever it has, or boxes.

- **The artist pages' examples are rendered here once and kept**, small, flattened and
  watermarked, so those pages never send layer files to the browser. See
  `docs/design/artist-profiles/PLAN.md`, "the examples are rendered once".

## The baked credit and the site mark

The page and the sheet can each carry a credit beside the portrait, but the composed image
is what people pass around: saved off a share card, lifted out of a PDF. It arrives detached
from both, so the credit is drawn into the pixels and travels with it.

- **Dark fill under a light outline.** The PNG is transparent, so it may land on a white
  page or a dark chat client; one of the two always reads.
- **It is a credit, not a watermark.** A crop removes it. It survives being right-click-saved.
- **The site mark runs up the right edge, away from the credit.** Two marks in one caption
  band would give anyone who wants the advertising gone a reason to crop the artist's name
  off with it. On opposite edges the cheap crop takes the site mark and leaves the artist.
- **The PDF prints no separate credit line.** The credit is already in the baked PNG; a
  second line landed 0.12in under the first and said the same thing.
- **The PDF's document info is overwritten** (`stamp-document-info!`). The sheet templates
  are third-party InDesign files, so a fresh export claimed to be made by "Adobe InDesign CS6
  (Macintosh)" with an empty author, which is untrue and is what an asset tool or a search
  index reads. It also puts the credit somewhere besides the picture. This is provenance,
  not protection: metadata strips in seconds.
- **The credit is stamped only when the composed portrait decoded.** A `portrait-png` that
  fails to decode falls back to the pasted image URL, and crediting the illustrator for
  somebody else's photograph is the one thing this feature must not do.

## Who the credit names

- **Order is by `layer-credit-weight`, then piece count, then registry order.** Weight comes
  first because count misleads: someone who drew the ears, nose and hair bits has three
  pieces, someone who drew the head and shirt has two, and the second drew the face. The
  weights are fixed numbers rather than measured pixel area, so the browser and the server
  agree without loading an image, the numbers can be read and tuned, and a large mostly
  transparent asset cannot climb the list. Registry order last means the credit never
  reshuffles between two renders of the same portrait.
- **Link marks flank the name only when they balance**: an even count, at most four. One
  link, three, or more than four go on a centred line below. The credit's broken rule is
  symmetric, and a lone mark on one side of it reads as a mistake. Two a side fits the
  247px drawer column beside a long name.
- **The lead artist keeps the marks**, because the lead is who most people looking at the
  picture want to find. Everyone else goes on one "with A, B and C" line, so the lockup
  stays three or four lines high however many artists there are.
- **The character page links each name** (`linked-credit`). A share link, a PDF and a baked
  PNG can only carry the name as text, so the character page is the one surface after the
  builder that can send someone to the artist.
- **An artist with no `:artist/name` gets no byline.** The drawer says "artist credit
  pending" rather than showing an empty slot or inventing a name; `credit-line` returns nil.
- **The drawer's credit sits directly under the art.** It used to sit in the footer beside
  Save Portrait, which put a credit in a row of actions and made the drawer disagree with
  the character summary, where it sits under the thumbnail.

The credit's design was settled from captures stashed at `docs/design/portrait-credit` on
`agents/develop`.

## Deployment overrides

The registry in `portrait_assets.cljc` holds the structure, which artist drew which asset,
and that has to be public because the app cannot resolve an asset without it. How a credit
is presented is a deployment concern: a fork may ship the same art under a different
arrangement with the artist, or add artists its own users contributed. So `:artist/name`,
`:artist/link`, `:artist/links`, `:artist/license` and `:artist/profile?` are overridable,
and nothing else is. A fork can credit its own contributors without editing shared code, and
cannot silently re-attribute someone's art to a different pack.

- **`PORTRAIT_ARTISTS` that does not parse is ignored with a warning.** A broken credit
  should cost the credit, not the site.
- **Both runtimes read the same overrides**: the server from env at startup, the client
  from the config the page injects. The drawer and the PDF render in the browser and the
  share card on the server, and a credit that disagreed between them would be worse than
  none.
- **`:artist/profile?` sits with the credit fields** so whatever owns the artist's choice,
  a deployment's config now and the artist's account setting later, can flip it without
  touching code.

## Saved portraits are untrusted

A portrait is client-supplied EDN, stored as a string because `::se/values` is a Datomic
component ref and no attribute in it can hold a nested map.

- **`sane-portrait` checks the containers, not just the outer map.** A stored
  `{:layers "abc"}` is valid EDN and passed an outer-map check. The non-map `:layers` then
  reached `credit-line`, which walks it: a string, a vector and a number all threw, and
  `character-page` calls `credit-line` unguarded, so a crafted save took the public
  character page down with a 500. Entry-level junk is already harmless, since
  `(:asset/id 42)` is nil.
- **Artists are resolved through the registry by asset id first**, and only then from the
  `:artist/id` the saved portrait names. Trusting the saved key meant deleting it detached
  the credit from art plainly in the registry: on the page, on the sheet and in the picture.
- **`drawable?`, not `(seq :layers)`, decides whether a portrait exists.** A selection naming
  only assets this deployment lacks is non-empty and draws nothing. The share card pointed
  at `/portrait.png`, the renderer returned 404, and the link previewed broken even when the
  character had a usable image URL to fall back on.

## Artwork in the PDF

- **Composed artwork may weigh 320k in the PDF, not the 128k upload limit.** The 128k is
  what the builder advertises beside the Image URL field, a promise about what a user may
  upload. A composed portrait is generated by the app at a size chosen to print well.
  Fitted to 128k, a 600x750 portrait came back 367x459: 156 dpi in the 2.35in box the sheet
  prints it at, under the 200 dpi the assets are built for. 320k keeps the full raster and
  still bounds the document.
- **`decode-artwork-bytes` fits rather than refuses.** `decode-image-bytes` refuses anything
  over the ceiling because the browser fits an uploaded image before sending it. A composed
  portrait has no such stage and lands at about twice the ceiling, so refusing it would drop
  the picture for being the size the app chose to make it.
- **`fit-artwork` keeps alpha; `fit-for-sheet` does not.** `fit-for-sheet` scales into
  `TYPE_INT_RGB` and re-encodes as JPEG, right for a photograph. A composed portrait put
  through it came back with its transparent ground black, a black box around the
  character. Artwork gives up pixels instead, stays PNG, and steps its longest edge down 15%
  a try, so the first attempt always shrinks; a fixed ladder starting above the image's size
  re-encoded it unchanged.
- **The composed portrait is decoded eagerly, not in a `delay`.** It is local CPU, and a
  delay that derefs to nil is still truthy, so a portrait that failed to decode would have
  suppressed the pasted image URL that should have taken over.

## Caching the share-card PNG

- **`artwork-epoch` is mixed into the ETag.** The validator hashes what the character chose,
  which means the same selections but not the same pixels: the layer files and the registry
  are classpath resources and change with a deployment, not with the database. A restart
  invalidates every portrait validator, which is when the pixels can have changed.
- **The ETag is route-local.** The global etag interceptor derives a validator from the
  response body and has no method for a stream; teaching it one would hash every exported
  PDF, which shares that body type. Here the key is the stored EDN, in hand before anything
  is rendered, so a 304 costs a pull and a hash instead of a render.
- **The `If-None-Match` wildcard is the whole header or nothing.** RFC 7232 s3.2 gives the
  grammar as `"*" / 1#entity-tag`, so `*` is never a list member. Splitting on commas could
  turn a quoted tag containing a comma into a bare `*` and answer 304 to a client holding a
  stale picture. Tags are matched out, not split; comparison is weak, as the RFC specifies,
  and a `--gzip` suffix is dropped as the global interceptor drops it.
- **A wildcard request renders first.** `*` asks whether a representation exists, and a
  stored portrait selecting nothing drawable has none and 404s. Answering 304 there would
  call a cache current when there is no current copy. The saving is lost on wildcard
  requests and kept on every request that names a tag.

## The draft and the character on screen

The portrait draft lives at the top of app-db, beside the character rather than in it.

- **`:set-character` drops the draft; `:character-updated` keeps it.** Three review rounds
  went into guards that tried to work out after the fact whether a replacement was really a
  switch, and each was wrong in a way the next found: a first save looks like a new id, and
  two never-saved characters share a nil one. The information is in which event the caller
  meant, so callers choose. Re-opening the same saved character keeps the draft as a
  courtesy; two different characters cannot share a non-nil id, so that comparison cannot
  be wrong, and nothing about a save reaches it.
- **`:character-epoch` counts replacements.** A save is dispatched at one epoch and answered
  at another, which is the only way the answer can tell whether its character is still on
  screen. An id cannot: a first save is where the id appears.
- **A save replaces the character on screen only when it was about it.** It used to replace
  it with whatever came back. The autosave queue is throttled by 7.5s and every sheet
  control feeds it, so adjusting hit points on one character and opening another inside
  that window swapped the builder out from under you, taking the unsaved edits in db
  `:character` with it. The portrait draft was the loudest symptom. The manual save passes
  the epoch it was dispatched at; the autosave posts a character by id, so it passes that
  id. Neither given answers false, which updates the character map and leaves the screen
  alone.
- **`:character-save-success` takes the response last.** The `:http` effect conjoins the
  response onto the caller's `:on-success` vector. Reading the arguments the other way
  round bound the save context as the response, so every successful save silently failed to
  install the character it got back, including a first save's new `:db/id`.

## Opting out of AI training

The page carries `<meta name="robots" content="noai, noimageai">`, the convention DeviantArt
started: only the `noai` tokens, which do not imply noindex. The portrait PNG carries the
same tokens as `X-Robots-Tag`, because a crawler fetching the image directly never parses
the HTML. Both are voluntary, honoured by some crawlers and ignored by others, so they sit
beside robots.txt and the art licence rather than replacing either.

## The drawer's chrome

- **The empty portrait slot shows the site logo, debossed**: masked to 5% white over a
  shallow top-down gradient, with a one-pixel dark shadow, so it reads as a detail in the
  wall rather than a picture. It replaced a cool radial spotlight, which belonged to some
  other screen: the app's panels are flat or top-down and its accents are amber. The mark
  comes from `branding/logo-path`, so a fork gets its own. Only the empty states carry it,
  so a portrait never shows a logo through its transparent parts.
- **The drawer sets the theme class on its own root.** It is mounted as a sibling of
  `content-page`, and `.app`, which carries the theme, is inside `content-page`. The
  Portrait tab has its own `.pl-root` too, so the stylesheet has one theme hook, not two.
- **The drawer's stylesheet mounts whether the drawer is open or not.** It also styles the
  launcher button and the Portrait tab, which are on screen when the drawer is not.
