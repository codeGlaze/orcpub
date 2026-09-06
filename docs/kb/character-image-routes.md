# Getting a character's picture into a sheet

Everything here was measured against real hosts with real URLs, in September 2026.
The runbook for operating the feature is `docs/CHARACTER-IMAGE-FETCH.md`; this file
is what was learned getting there, including the wrong turns, because two of them
cost a day and both are easy to repeat.

## The rule that decides everything

**CORS is a browser rule and has no bearing on the server; hotlink blocking is a
server-side rule and has no bearing on the browser.** They are separate decisions
made by different people for different reasons, so the hosts they catch overlap
far less than one would guess. That is the whole reason a two-tier design works:

1. The browser reads the picture and the export carries the bytes. Needs
   `Access-Control-Allow-Origin`.
2. The server fetches the URL. Needs the host to serve a datacenter IP.
3. Neither — copy or upload, which needs nobody's permission.

## Measured, with real URLs

| host | browser may read | server may fetch |
|---|---|---|
| `i.imgur.com` | yes (`*`) | yes |
| `cdn.discordapp.com` | yes (`*`) | yes |
| `upload.wikimedia.org` | yes (`*`) | yes |
| `static.wikia.nocookie.net` | yes (`*`) | — |
| `cdna.artstation.com` | yes (`*`) | — |
| `images-wixmp-…` (DeviantArt) | yes (echoes Origin) | — |
| `lh3.googleusercontent.com`, `64.media.tumblr.com`, `raw.githubusercontent.com` | yes (`*`) | — |
| `i.pinimg.com` | **no** | **yes** (200, 393 KB) |
| `www.dndbeyond.com` | **no** | **yes** (200, 148 KB) |
| `i.postimg.cc`, `i.ibb.co`, `live.staticflickr.com`, `www.dropbox.com`, `i.redd.it` | no | untested |

Nothing has been shown to block this server. `/image-probe` logs the host whenever
it answers that a picture cannot be had, so the genuinely unreachable set is
measured from real traffic rather than guessed at.

## Trap 1: an invented URL proves nothing

Pinterest and D&D Beyond were both written off as blocking this server on the
strength of a 403. The URLs had been made up, and **both hosts sit behind S3, which
answers `AccessDenied` for a key that does not exist** — `i.pinimg.com/` itself does
it. A second conclusion, that User-Agent and Referer tuning did not help, rested on
the same fabricated URLs and was equally worthless.

Get a real URL. With browser egress working (below) that is one page visit:

    await page.goto('https://www.pinterest.com/');
    const url = [...document.images].map(i => i.currentSrc)
      .find(s => /i\.pinimg\.com\/.+\.(jpg|png)/i.test(s));

## Trap 2: the ceiling was ours

Pinterest and D&D Beyond serve this server a clean 200. What refused them was a
single 128 KB constant used as BOTH the download ceiling and the ceiling on what
may go into the PDF. A 393 KB portrait the host handed over without complaint was
dropped for weight.

They are separate questions and are now separate numbers — 2 MB down, 128 KB into
the document, with `pdf/fit-for-sheet` scaling and re-encoding in between. Neither
bound that actually matters moved: the pixel budget caps the decode and the
transfer deadline caps the time. **The byte count was never what made a large image
dangerous.**

## Reaching the real internet from a browser test

Chromium in the agent container cannot complete a **TLS 1.3** handshake through the
egress relay: the tunnel closes mid-exchange and the page sees
`ERR_CONNECTION_RESET`, while curl and Playwright's own Node stack work fine. The
proxy's `recentRelayFailures` shows ~1,700 bytes out, 39 back, closed after 6s.

    chromium.launch({
      args: ['--ssl-version-max=tls1.2'],
      proxy: { server: process.env.HTTPS_PROXY, bypass: 'localhost,127.0.0.1' },
    })

Not post-quantum key share (disabling `PostQuantumKyber` / `X25519MLKEM768` changes
nothing) and not the CA. Isolate by comparing stacks: if `context.request.get` works
and `page.goto` does not, it is Chrome's TLS, not the proxy.

## What the browser cannot be made to do

Tested, not assumed:

- **A page-initiated copy of a cross-origin image yields its MARKUP, not its
  pixels.** `execCommand('copy')` over a selection holding the image reports
  success and puts `text/html` on the clipboard; `navigator.clipboard.read()` finds
  no `image/*`. If a page could copy pixels it could read any image anywhere, so no
  amount of cleverness gets round this. It is also the difference between a page and
  an **extension**, which is granted cross-origin read at install time.
- **A tab or iframe showing the picture is a different origin.** The opener cannot
  read its DOM or canvas.
- **A service worker fetching it `no-cors`** gets an opaque response whose bytes it
  cannot read either.

What DOES work is the viewer copying it themselves: the browser's own "Copy image"
puts the decoded picture on the clipboard, and a `paste` — or
`navigator.clipboard.read()` behind a user gesture — hands the page a real `File`
with no origin attached. User mediation is the sanctioned route, not a loophole.

- **Cache-mode poisoning is not a thing here.** A plain `<img>` load followed by a
  `crossOrigin` one for the same URL returns `READABLE`: Chrome served the second
  from cache and still checked it against the cached ACAO header. Do not go hunting
  for this bug; it was hunted.

## Ordering constraints that bite

- **The read must not run ahead of the thumbnail.** Both request the same URL; if
  the `crossOrigin` one goes first on a host with no ACAO, its failure takes the
  plain one down with it and the picture stops displaying at all. The read is
  therefore started from the thumbnail's `on-load` and nowhere earlier.
- **`image-error` in the builder dispatches when CALLED, at render**, rather than
  returning a handler. Anything that reads the failed flag at handler-build time
  reads it before the browser has tried. Fixed, but the shape is worth recognising.
- **The export is a synchronous form POST into a new tab**, so nothing in the page
  ever sees its response, and an `await` between the click and `.submit()` spends
  the transient user activation that keeps the tab from being blocked. Anything that
  must be known before exporting has to be known BEFORE the click — hence the
  eager probe.

## Interface rules these fields follow

Arrived at by looking at every state in one frame, which is the only way the
problem was visible:

- **One notice, one sentence, at most one action.** Four blocks could appear at
  once for a single unreachable picture. Whatever is most actionable is the only
  thing shown; everything else waits behind one disclosure.
- **No form controls inside a notice.** A message that grows a button, a hint and a
  file input stops reading as a message. Asserted in
  `test/browser/character_image_capture_e2e.js`.
- **Advice is debounced; failures are not.** The field commits on every keystroke,
  so unbounced advice objects to `htt` on the way to `https://`. Clear the previous
  advice the moment the address changes: left up during the debounce, its offered
  correction is both stale and clickable.
- **A correction may be applied automatically only once verified.** http → https is
  checked with a plain `<img>` load first — free, and the same request the
  thumbnail was about to make. An unverified rewrite that fails leaves someone
  debugging an address they never typed.
