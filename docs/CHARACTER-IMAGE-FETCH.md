# Character image fetch — how it works, and what to check when it breaks

A character's portrait and faction image are stored as **URLs**. Three ways the
pixels reach the PDF, tried in that order:

1. **The browser reads them and sends the bytes with the export.** The normal
   path. Hosts that block hotlinking judge the Referer and the datacenter IP,
   neither of which describes the visitor's own browser.
2. **The server fetches the URL itself.** For a picture the browser was refused.
   The builder asks about it up front — `POST /image-probe`, see below — so it
   knows the answer before anyone clicks Export. This is the only outbound request
   OrcPub makes to an address a visitor chose, on an endpoint that needs no login,
   so it is deliberately hemmed in.
3. **Paste, or a file.** Local to the machine, so no host has a say. This is the
   way in for a host that refuses everyone.

The user is told nothing until step 2 has answered. An offer at the moment the
browser gives up would ask for a paste of a picture the server was about to fetch.

Read this if portraits stop appearing in exported PDFs, or if a security review
asks what `/character.pdf` can be made to talk to.

- Code, browser side: `orcpub.image-capture` — `capture`, `capture-file`,
  `normalize`, `read-drawn`
- Code, server side: `orcpub.pdf` — `decode-image-bytes` (path 1),
  and `validated-addresses`, `private-address?`, `pinned-connection-manager`,
  `proxied?`, `open-image-stream`, `read-bounded-bytes`,
  `within-pixel-budget?`, `safe-image-bytes` (path 2)
- Tests: `test/clj/orcpub/pdf_supplied_image_test.clj` (bytes the browser sent),
  `test/clj/orcpub/pdf_image_test.clj` (which URLs are allowed),
  `test/clj/orcpub/pdf_image_fetch_test.clj` (what happens once one is),
  `test/browser/character_image_capture_e2e.js` (both paths through the real app)
- Background: `docs/kb/pdf-form-techniques.md`

## Measured against real hosts (2026-09-06)

With a browser that can reach the real internet (see `test/browser/README.md` for
the TLS flag that takes), against **real** URLs rather than invented ones:

| host | browser reads it | server fetches it |
|---|---|---|
| `i.imgur.com` | yes | yes |
| `cdn.discordapp.com` | yes | yes |
| `upload.wikimedia.org` | yes (42 KB after fitting) | yes |
| `i.pinimg.com` | no | **yes** (393 KB, fitted) |
| `www.dndbeyond.com` | no | **yes** (148 KB, fitted) |

Pinterest serves this server a 200 and 393 KB of JPEG; a D&D Beyond avatar is a
200 and 148 KB. Neither ever blocked us. What
refused it was our own 128 KB ceiling, applied to the download rather than to the
document — now split in two, so a heavy picture is fitted instead of dropped. A
Pinterest portrait reaches the sheet with nothing asked of the user.

## Which hosts allow the browser to read (measured 2026-09-05)

The browser can only read a picture whose host sends `Access-Control-Allow-Origin`.
Sampled with a browser-shaped request; ACAO is set at the edge, so it holds for the
host rather than the object.

| Allows the read | Does not |
|---|---|
| `i.imgur.com` `*` | `i.pinimg.com` |
| `cdn.discordapp.com` `*` | `www.dndbeyond.com` |
| `static.wikia.nocookie.net` (Fandom) `*` | `i.postimg.cc` |
| `upload.wikimedia.org` `*` | `i.ibb.co` |
| `cdna.artstation.com` `*` | `live.staticflickr.com` |
| `images-wixmp-…` (DeviantArt), echoes Origin | `www.dropbox.com` |
| `lh3.googleusercontent.com`, `64.media.tumblr.com`, `raw.githubusercontent.com` `*` | `i.redd.it` |

The two lists are largely complementary rather than overlapping: most of the
right-hand column allows hotlinking, so the server fetches those perfectly well.

Do NOT read the right-hand column as "these block us". It records only that the
host sends no `Access-Control-Allow-Origin`. An earlier version of this table
claimed Pinterest and D&D Beyond refused the server too, on the strength of a 403
-- but those URLs were invented, and both hosts sit behind S3, which answers
`AccessDenied` for a key that does not exist. Whether a given host will serve THIS
server is a question about a real URL, and the answer belongs to the runtime, not
to a table.

Re-run the probe politely -- a request or two per host, spaced -- rather than in a
loop; these are other people's servers.

## What is known before anything is fetched

`orcpub.image-url/advise` reads the address alone -- no request, no waiting -- and
is where most real mistakes are caught, because most of them are visible in the
string:

- **A page's address instead of the picture's.** The commonest paste there is:
  a Pinterest pin page, an Imgur gallery, a Reddit post, a Flickr or DeviantArt or
  ArtStation page. A fetch can only report these as a puzzle; the string says
  plainly what they are.
- **A login wall.** Instagram and Facebook links cannot work for anyone.
- **A malformed address.** No scheme, a scheme that is not the web, a space in the
  middle from a half-copied link.
- **`http://`,** which the page's own CSP will not display whatever the host does.
  This one is not offered but VERIFIED and applied: the browser loads the https
  address itself -- a plain `<img>`, no server, and the same request the thumbnail
  was about to make -- and the field is changed only once that succeeds, with a
  note saying so. A host that serves no https is told, and its address is left
  exactly as typed.
- **Viewer links with a known direct form** -- Dropbox `?dl=0`, Google Drive
  `/file/d/<id>/view` -- which are offered as a correction to take or leave.

A correction is only ever offered where it is mechanical. Nothing guesses a
picture's address from a page's; it says what the page is and how to get the real
one. The advice is debounced, because the field commits on every keystroke and
advice that objects to `htt` on the way to `https://` teaches people to ignore it.

Advice, not enforcement. The rules that must hold are enforced where they cannot
be argued with -- address validation on the server, CORS in the browser -- so this
is free to be occasionally wrong, and its weakest rule (an unknown host with no
file name) is only a note.

## What the builder tells a person, and why

`/image-probe` answers a REASON, not a boolean, and the builder turns it into
wording. The server never sends a sentence: keeping the words on the client means
nothing the server says can reach a person unedited.

| reason | shown as | what it points at |
|---|---|---|
| `ok` | nothing | the server can fetch it |
| `blocked-address` | that address cannot be fetched, check the host name and that it is a public image | the link |
| `not-found` | the host says there is nothing there | the link |
| `redirect` | the link redirects rather than being the picture | the link |
| `not-an-image` | not a PNG or JPEG | the link |
| `unreachable` | that host could not be reached | the link |
| `refused` | the host refused to serve it to us | supply the picture |
| `too-large` | larger than 2 MB | supply the picture |
| `too-many-pixels` | larger than 2000x2000 | supply the picture |
| `timeout` | the host took too long | supply the picture |
| `host-error` | the host had an error of its own | supply the picture |
| `rate-limited` | the host is asking us to slow down | try again shortly |
| `unknown` | could not be fetched | supply the picture |

Split by what is worth fixing. Telling someone to copy a picture when they have
mistyped a link is not help, and telling someone to check a link the host is
refusing outright sends them round in circles.

Every address refusal collapses into `blocked-address` on purpose — a private
range, a reserved range, a name that does not resolve, a DNS answer that did not
survive the pin. Separating them would make an endpoint that needs no login into a
map of what this server's network can reach, one request per answer. The wording
covers both cases instead.

## The pre-flight probe

`POST /image-probe` with `{:url "..."}` answers `"true"` or `"false"`: whether
THIS server can fetch THAT picture. The builder asks as soon as a browser read
fails, not at export time — the export posts a form into a new tab and never sees
the response, and an await between the click and the submit would spend the user
activation that keeps the tab from being blocked.

- The bytes are kept (`probed-images`, 10 minutes, 64 entries) so the export that
  follows costs the host no second request. A negative answer is cached too.
- It answers a boolean and never the picture. Handing back fetched bytes would
  make an endpoint that needs no login into a general-purpose proxy for anything
  inside the size limits.
- Every address rule that guards the export guards this: private and reserved
  ranges, scheme, redirects. Otherwise it would be a port scanner returning one
  boolean per request. `test/clj/orcpub/image_probe_test.clj` holds that line.

## Which path a picture took

Both ceilings — 128 KB and 2000×2000 — apply to both paths. Bytes from the
browser arrive base64 in `:image-data` / `:faction-image-data` and are decoded by
`decode-image-bytes`, which applies those ceilings and reads the format from the
bytes rather than from the mime type the client claimed. When they are present the
server does not fetch at all, so none of the failures in the table below can occur.

When the browser cannot read a picture the export goes out with the address and
the server tries instead. **Nothing is said to the user at that point** — an offer
there would ask for an upload of what the server was about to fetch. The upload is
offered only after an export has gone out with the picture unread, and worded
conditionally, because this page never sees the export's response: the form posts
into a new tab.

Exporting is held while a read is in flight, so the browser's bytes win the race
rather than losing it to a click. A read always ends — `capture` carries its own
deadline — so the hold is bounded.

For a host that lets nobody read its pictures -- refusing the page and the server
alike -- the way in is PASTE. The browser's own "Copy image" puts the decoded
picture on the clipboard, and a paste into the Image URL field is read locally, so
none of the host's rules apply. The offer names that first and the file picker
second. Nothing here can be done by opening the picture in another tab: that tab
is a different origin and the opener cannot read it, and a service worker fetching
it no-cors gets an opaque response whose bytes it cannot read either.

A refused read logs a CORS error in the browser console. That is the browser
reporting the host's rule and cannot be suppressed by the page.

A picture drawn off a canvas is re-encoded to JPEG at up to 1000px on the long
edge, which is past 300dpi for the 2.35 × 3.15 inch box it prints in. An uploaded
file already inside both ceilings is carried untouched.

## Symptoms and what they mean

Failures are logged by `orcpub.routes` as `pdf: failed adding …` or surface as
`Failed to load image from URL`. The `:error` key in the exception data is the
thing to grep for.

| What you see | `:error` | Cause |
|---|---|---|
| Portrait missing, host is internal or a bare IP | `:image-url-not-permitted` | The address is private or reserved. Working as intended — see the ranges below. |
| Portrait missing, public host | `:image-load-failed` with a `:status` | The host answered non-2xx. A `302` means it tried to redirect; redirects are refused on purpose. |
| Portrait missing, large image | `:image-too-large` | Over the 2 MB **download** ceiling. The 128 KB ceiling is separate and applies to what goes INTO the PDF: a picture between the two is scaled and re-encoded to fit, not refused. |
| Portrait missing, big dimensions | `:image-too-large-dimensions` | Over 2000×2000, refused from the header before decoding. |
| Portrait missing, slow host | `:image-transfer-timeout` | The body took more than 20s. |
| Portrait missing, unreadable file | `:invalid-image-format` | Not an image, or a format ImageIO cannot read. |
| **Every** image fails, log shouts `EVERY character image will fail to load` | `:image-pin-mismatch` | Something is routing the connection elsewhere while the JVM reports no proxy, so the pin engaged where it should have stepped aside. **See "Egress proxies" below.** |

That last row is the one to remember. It is not a per-image failure — it is every
image, everywhere, at once. It is printed **once** per process, in full, naming the
host it was routed to and pointing back here, so it cannot drown in its own repeats
on a busy server.

## The line to look for at boot

Every start prints which of the two egress paths is live, before Jetty comes up:

```
pdf/image-fetch: DNS pinning ACTIVE (no proxy configured for external https)
```
```
pdf/image-fetch: DNS pinning OFF -- HTTP @ proxy.internal:3128 will resolve and fetch; it is the egress control point
```

**If that says OFF and you configured no proxy, or ACTIVE on a host that has one,
stop there** — the mismatch is the cause of whatever comes next. The same thing is
readable as data: `(orcpub.pdf/image-egress-status)` returns
`{:pinning? bool :proxy str-or-nil}`.

## The limits, in one place

| Rule | Value | Where |
|---|---|---|
| Schemes | `http`, `https` only | `validated-addresses` |
| Addresses | private/reserved refused | `private-address?` |
| Redirects | not followed | `open-image-stream` |
| Size | 128 KB | `max-image-bytes` |
| Dimensions | 2000 × 2000 | `max-image-pixels` |
| Connect | 10s | `open-image-stream` |
| Transfer | 20s total | `image-transfer-deadline-ms` |

Refused address ranges: loopback, `0.0.0.0/8`, `10/8`, `100.64/10` (carrier-grade
NAT), `169.254/16` (cloud metadata), `172.16/12`, `192.0.0/24`, `192.168/16`,
`198.18/15`, `240/4`, multicast; and for IPv6 `::1`, `fe80::/10`, `fec0::/10`,
**`fc00::/7`**, plus the IPv4 addresses carried inside `64:ff9b::/96` (NAT64) and
`2002::/16` (6to4).

The last three exist because `InetAddress` has no predicate for them and each was
verified reachable before it was added. `fc00::/7` is the one to notice:
`isSiteLocalAddress` knows only the deprecated `fec0::/10`, so a guard built on it
alone lets every real private IPv6 address through.

## Resolve once, connect to that answer

The host is resolved **once**, by `validated-addresses`, and those exact addresses
are handed to the connection.

This matters because the obvious implementation is wrong. If the guard resolves
the name to check it, and the connection then resolves the name again, a DNS
server the attacker controls can answer a public address the first time and
`127.0.0.1` the second. The address that was validated is not the address that was
talked to. That is DNS rebinding.

`pinned-connection-manager` gives the HTTP client a `DnsResolver` that returns the
validated addresses for that host and throws for anything else.

**Two constraints on the shape, both verified against the Apache javadoc:**

- The resolver must go on the **connection manager**, not on `HttpClientBuilder`.
  Its javadoc for `setDnsResolver` says: *"Please note this value can be
  overridden by the `setConnectionManager(HttpClientConnectionManager)` method."*
  clj-http always sets a connection manager, so pinning on the builder compiles,
  runs, and silently does nothing.
  ([javadoc](https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/apidocs/org/apache/http/impl/client/HttpClientBuilder.html))
- The hostname must stay in the URL. Addressing the resolved IP directly with a
  `Host:` header is the tempting shortcut, and it breaks certificate validation;
  repairing that means overriding hostname verification, which is a bigger hole
  than rebinding. Pinning the resolver keeps the default socket factories doing
  ordinary certificate and hostname checks.

`BasicHttpClientConnectionManager` is used with the four-argument constructor
`(socketFactoryRegistry, connFactory, schemePortResolver, dnsResolver)`. It
"maintains only one active connection" and "ought to be used by one execution
thread only", which is exactly the shape here: one manager per fetch, closed in a
`finally`.
([javadoc](https://hc.apache.org/httpcomponents-client-4.5.x/current/httpclient/apidocs/org/apache/http/impl/conn/BasicHttpClientConnectionManager.html))

## Deployment

| Setup | Effect | To configure |
|---|---|---|
| **nginx** (`deploy/nginx.conf.template`) | None. It is an inbound reverse proxy; this fetch is outbound and never passes through it. | Nothing |
| **Docker Compose** | Works. The one resolution uses the container's DNS, so Docker's embedded resolver serves it normally. | Nothing |
| **Docker Swarm** (`./run --swarm`, overlay networks) | Same as Compose. Overlay addressing is internal and irrelevant to an external image URL; task egress NATs out normally. Each replica fetches independently — nothing is shared. | Nothing |
| **Egress proxy** | The pin is **skipped**, deliberately. | Nothing, if the proxy is set via the JVM's proxy properties |

### Why an egress proxy changes things

Behind a proxy the client connects to the **proxy**, so the resolver is asked for
the *proxy's* hostname — which the pin refuses, failing every HTTPS fetch with
`not the pinned host`. It is also pointless there: the proxy does the name
resolution, and the proxy is the egress control point.

`proxied?` reads the same `ProxySelector` that clj-http's `SystemDefaultRoutePlanner`
uses, so the two cannot disagree, and steps around the pin when one applies.

**Note that this only detects proxies the JVM knows about** — `https.proxyHost`,
`http.proxyHost` and friends. A transparent proxy or an egress gateway enforced at
the network layer is invisible to the JVM: the fetch still works, but the guarantee
is then your network's rather than this code's.

Neither Compose nor Swarm sets a proxy today, so **the pinned path is the default
in production.**

### Consequence worth knowing

Because private ranges are refused, a character image URL **cannot** point at
another container on your Compose or Swarm network. That is deliberate — it is the
same rule that stops it reaching the transactor or a cloud metadata endpoint — but
it does mean there is no way to serve portraits from an internal host today. There
is no allowlist or exception mechanism.

## Diagnosing a specific URL

```
lein repl
(require '[orcpub.pdf :as pdf])
(pdf/image-egress-status)                                  ;; which path is live?
(pdf/safe-image-url? "https://example.com/portrait.png")   ;; may we fetch it?
(count (pdf/safe-image-bytes "https://example.com/portrait.png"))  ;; try it
```

`safe-image-url?` returns false rather than throwing, so a rejected URL is
indistinguishable from one that failed to load — by design, since the sheet
already handles a missing image. `safe-image-bytes` throws with the `:error` key
from the table above, which is the faster way to find out *why*.

## What has not been verified

- **Not run inside a container.** The deployment rows above are read from
  `docker-compose.yaml`, `docker/Dockerfile` and `deploy/nginx.conf.template`,
  not from a container run. If images break after a deployment change, this is
  the first assumption to re-test.
- **The pinned path has not been exercised against a real external host.** It is
  covered against a local server through a resolved hostname, and a real HTTPS
  fetch was verified end-to-end — but through the *proxied* path, because the
  development sandbox requires a proxy. Production runs the pinned path.
- Whether a fetched image is re-encoded or downscaled to its drawn size. It is
  capped at 128 KB, so the exposure is bounded either way.
