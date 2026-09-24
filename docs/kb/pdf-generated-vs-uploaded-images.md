# A generated picture is not an uploaded one: the 128k ceiling in the PDF export

`pdf/decode-image-bytes` **refuses** anything over `max-embedded-bytes` (128 KiB). That is
correct for a picture the browser captured, and wrong for one the app generated. Getting
this wrong costs the picture silently: the export succeeds, the sheet prints, and the
portrait is simply not on it.

Found 2026-09-20 merging `integration` into the paper-doll portrait branch, where both sides
had independently built a "browser supplies the bytes" path.

## The collision

`integration` reworked the PDF image path while the portrait branch was out:

- `image-data` / `faction-image-data` fields carry bytes the browser read
- `pdf/decode-image-bytes` decodes them
- `probed-outcome` reuses a cached probe instead of fetching twice
- `well-formed-image-url?` replaces an inline regex

The portrait branch had a private version of the same idea — `portrait-png` plus a
`decode-portrait-png` with its own 2 MB cap — because a composed portrait has no URL that
could produce it: the client bakes CSS-mask layers to a PNG.

Collapsing the private one onto the general one is right, and `decode-image-bytes` is
strictly better than what it replaced: it also checks `within-pixel-budget?`, which reads
the header before decoding, so a 69-byte PNG declaring 25000×25000 cannot make ImageIO
allocate 2.5 GB. The portrait-specific decoder had no such check.

## Why the merge then dropped the portrait

`decode-image-bytes` enforces the ceiling the builder advertises next to the Image URL
field, and it enforces it by **refusal**:

```clj
(when (and (string? b64) (not (s/blank? b64))
           (<= (count b64) max-image-base64))     ; 128 KiB, encoded
  ...
  (when (and (pos? (alength data))
             (<= (alength data) max-embedded-bytes)
             (within-pixel-budget? data))
    {:data data :jpg? (jpeg-bytes? data)}))
```

The refusal is sound *because the browser is expected to have fitted the image already* —
`orcpub.image-capture` measures against the same print edge before it sends, and the
comment on `print-edge` says the two "have to agree or one of them is wasting bytes the
other would have kept."

A composed portrait has no such stage. It is rasterized at the frame's own size (600×750)
and posted as-is. Measured: **247,215 bytes against a 131,072 ceiling — 1.9×**, with flat
silhouette art. Real line art is heavier.

The symptom is not an error. It is:

```
PDF embeds an image XObject                       FAIL
PDF without the portrait is materially smaller    FAIL  with=261089 without=261089
```

A sheet byte-identical with and without a portrait. The only reason this was caught is that
`portrait_pdf_export_e2e.js` asserts a *differential* — it exports twice, once with the
payload stripped, and requires the two to differ. An "embeds an image XObject" check alone
would have passed on the template's own artwork.

## The fix, and the alternative that was rejected

`pdf/fit-for-sheet` already exists for exactly this and its docstring states the principle:

> The ceiling belongs on what goes INTO the document, not on what may be fetched.

It scales and re-encodes through `fit-attempts` until the result fits, rather than refusing.
That is what an oversized *fetched* image already gets. So the portrait gets it too, via
`pdf/decode-artwork-bytes` — same pre-decode guards (encoded length bounds the allocation,
header must declare a sane canvas), then `fit-for-sheet` instead of a hard refusal.

**Rejected: raising `max-embedded-bytes`, or giving the portrait a private exemption.** The
128k is the number the UI advertises to users; moving it to accommodate one internal
producer changes a promise for everyone. A second decoder with a looser cap is how the
duplicate arose in the first place.

**Also rejected: fitting in the browser**, the way `image-capture` does. Defensible, and it
would keep the server path uniform — but it puts the print-edge arithmetic in a third place,
and the portrait rasterizer would have to re-encode as JPEG to hit the budget, losing the
alpha the og:image path needs.

## A latent bug this uncovered

The pre-merge code read:

```clj
composed (when portrait-png (delay (decode-portrait-png portrait-png)))
portrait (or composed (some-> (wanted image-url image-url-failed) ...))
```

`composed` is a `Delay` object, so it is truthy even when it derefs to `nil`. A portrait
that failed to decode therefore **suppressed the pasted `image-url` that should have taken
over** — the user would lose both pictures, not fall back to one. Decoding eagerly (it is
local CPU with no network in it) and branching on the value fixes it:

```clj
composed (pdf/decode-artwork-bytes portrait-png)
portrait (if composed (delay composed) (image image-data image-url image-url-failed))
```

## If you add another generated-image path

Ask which side of the line it is on. Bytes that came from *outside* — a fetch, an upload, a
paste — get `decode-image-bytes` and its refusal. Bytes this application produced, at a size
this application chose, get `decode-artwork-bytes` and get fitted. The guards before the
decode are the same either way; only the response to "too heavy" differs.
