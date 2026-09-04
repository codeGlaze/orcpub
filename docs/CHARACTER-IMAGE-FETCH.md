# Character image fetch — how it works, and what to check when it breaks

A character's portrait and faction image are stored as **URLs**. When a sheet is
exported, the **server** fetches them and embeds the pixels in the PDF. That is
the only outbound request OrcPub makes to an address a visitor chose, on an
endpoint that needs no login, so it is deliberately hemmed in — and the hemming is
what usually breaks first.

Read this if portraits stop appearing in exported PDFs, or if a security review
asks what `/character.pdf` can be made to talk to.

- Code: `orcpub.pdf` — `validated-addresses`, `private-address?`,
  `pinned-connection-manager`, `proxied?`, `open-image-stream`,
  `read-bounded-bytes`, `within-pixel-budget?`, `safe-image-bytes`
- Tests: `test/clj/orcpub/pdf_image_test.clj` (which URLs are allowed),
  `test/clj/orcpub/pdf_image_fetch_test.clj` (what happens once one is)
- Background: `docs/kb/pdf-form-techniques.md`

## Symptoms and what they mean

Failures are logged by `orcpub.routes` as `pdf: failed adding …` or surface as
`Failed to load image from URL`. The `:error` key in the exception data is the
thing to grep for.

| What you see | `:error` | Cause |
|---|---|---|
| Portrait missing, host is internal or a bare IP | `:image-url-not-permitted` | The address is private or reserved. Working as intended — see the ranges below. |
| Portrait missing, public host | `:image-load-failed` with a `:status` | The host answered non-2xx. A `302` means it tried to redirect; redirects are refused on purpose. |
| Portrait missing, large image | `:image-too-large` | Over 128 KB, either declared or measured mid-stream. |
| Portrait missing, big dimensions | `:image-too-large-dimensions` | Over 2000×2000, refused from the header before decoding. |
| Portrait missing, slow host | `:image-transfer-timeout` | The body took more than 20s. |
| Portrait missing, unreadable file | `:invalid-image-format` | Not an image, or a format ImageIO cannot read. |
| **Every HTTPS image fails**, log says `not the pinned host` | — | **See "Egress proxies" below.** The DNS pin engaged where it should have stepped aside. |

That last row is the one to remember. It is not a per-image failure — it is every
image, everywhere, at once, and it means the proxy detection did not fire.

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
