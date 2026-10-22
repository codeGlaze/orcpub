# Every per-address limit sees the proxy, not the player (found 2026-10-22, not fixed)

**What this is for:** why sign-in, sign-up and reset limits treat all players as one visitor on a
proxied deployment, the evidence, and what a fix has to decide.

## The finding

Every per-address check uses Pedestal's `:remote-addr`, the address the TCP connection came from:
`security/multiple-account-access?` (login spray), `add-failed-login-attempt!`,
`registration-allowed?`, `reset-email-allowed?`, `image-probe-allowed?` (`routes.clj`). Nothing
reads `X-Forwarded-For`, `X-Real-IP` or `CF-Connecting-IP`, and nginx (`deploy/nginx.conf.template`)
sets `X-Real-IP $remote_addr` without the `real_ip` module, so behind Cloudflare even that is
Cloudflare's edge.

Evidence: a live error email (owner, 2026-10-22) shows `:remote-addr "10.0.38.49"` (the Swarm overlay
proxy), `x-real-ip "104.23.160.222"` (a Cloudflare edge) and `x-forwarded-for "97.126.172.119,
104.23.160.222"` (the player first).

## What it does on the live site

- **Login spray check:** five distinct usernames failing "from one address" inside a minute
  refuses sign-ins. With every player on the proxy's address, five players mistyping in the same
  minute lock everyone out for that minute.
- **Sign-up and reset limits** are per address per hour: effectively site-wide caps.
- `multiple-ip-attempts-to-same-account?` (the "sign-in attempts from several places" notice) never
  sees more than one address.

## What a fix must decide (owner)

Which header to trust, and only from which hop. `X-Forwarded-For` is client-controlled except for
the entries the trusted proxies appended. Options: trust `CF-Connecting-IP` when the request came
from a Cloudflare range; or have nginx resolve the real address (`set_real_ip_from` Cloudflare
ranges + `real_ip_header CF-Connecting-IP`) and pass it in `X-Real-IP`, then trust that header only
from the proxy network. Deployments without Cloudflare (self-hosters) need the plain nginx case.
The security log (`security/audit!`, below) records both `ip=` (`:remote-addr`) and `fwd=`
(`X-Forwarded-For`) meanwhile.
