# Auth libraries: is buddy safe, and what would replace it

*Checked 2026-11-19 on `upgrade/pedestal-jetty12` (with #89 merged in): GitHub's advisory
database, the buddy jars we ship, `lein deps :why`, and Maven Central poms. Re-check before acting.*

## What buddy does for us

| Part | Version | Job | Where |
|---|---|---|---|
| buddy-auth | 3.0.323 | reads the sign-in token on each request | `routes.clj` `backend` (JWS backend) |
| buddy-sign | 3.6.1-359 | signs and checks sign-in tokens (JWT, HS256) | `auth/create-token`, unsubscribe links in `routes.clj` |
| buddy-hashers | 2.0.167 | hashes and checks passwords | `routes/auth.clj` (`hashers/encrypt`, `hashers/check`) |
| buddy-core | 1.12.0-430 | the crypto layer under the others | (transitive, declared) |

## Is it safe

- **No advisories** in GitHub's database for any of the four packages.
- **The crypto is not buddy's own.** buddy-core wraps Bouncy Castle and the JDK. Bouncy Castle is
  where the 3 critical alerts were; #89 pins it to 1.87.
- **Our use is sound.** Tokens are HMAC-SHA256 with a server secret; the checker accepts only the
  algorithm it is given, which blocks the "token names another algorithm" forgery. A blank secret is
  refused (`auth.clj` `jwt-secret`, `signature-or-throw`). Tokens carry `:exp`, and the
  token-withdrawal register cancels older tokens after a password change.
- **Passwords:** buddy-hashers' default, `bcrypt+sha512` at cost 12 (`buddy/hashers.clj` in the jar).
  The SHA-512 pre-hash avoids bcrypt's 72-byte truncation.
- **The real weakness is pace:** buddy-sign's last release was 2024-07, buddy-auth's 2022-01. Slow
  releases mean a slow fix *if* a flaw is found, not that one exists.
- `security_test/a-token-issued-before-a-library-update-still-signs-in` pins a token issued by
  buddy-sign 3.4.333 (the version every live token came from), so a library update that breaks
  existing sign-ins fails a test.

## What buddy brings into the jar

Only buddy brings these (`lein deps :why`):

- Bouncy Castle bcprov, bcpkix, bcutil (about 9.5 MB), through buddy-core
- cheshire with Jackson's CBOR and Smile formats, and tigris, through buddy-core
- clojurewerkz/scrypt and com.lambdaworks/scrypt, through buddy-hashers
- clout and instaparse, through buddy-auth

The app's own JSON moved to charred on `upgrade/pedestal-jetty12`; cheshire stays only for buddy.

## The replacement, if it is ever needed

- **Tokens:** nimbus-jose-jwt (10.10, 843 KB). Its pom has no required runtime dependency; Bouncy
  Castle and Tink are optional. HMAC runs on the JDK's own crypto.
- **Passwords:** Password4j (1.8.4, 90 KB), depending only on slf4j-api.
- **buddy-auth's job** becomes a short interceptor of our own.
- **What it removes:** everything in the list above, including Bouncy Castle.
- **What it costs:** a rewrite of sign-in and password checking. Stored hashes are buddy's
  `bcrypt+sha512$...` format, which Password4j does not read, so the old check must be reproduced
  (SHA-512, then bcrypt with the stored salt) and each password rehashed at its next sign-in. Live
  tokens either stay valid through a period accepting both, or every player signs in again once.

**Recommendation (2026-11-19):** keep buddy, updated. Replace it only if buddy-sign or buddy-hashers
gets an advisory with no fix, or if dropping Bouncy Castle becomes worth a rewrite on its own.

## argon2 inside buddy (not done)

buddy-hashers already supports `argon2id` (`hashers.clj`, defaults 64 MiB memory). New passwords
could use it and old ones be upgraded at sign-in, with no new library. The trap: the server runs with
`-XX:+ExitOnOutOfMemoryError` (`docker-compose.yaml`) and the JVM's default heap, so 64 MiB per
sign-in multiplied by a burst of sign-ins could exit the server. Use a smaller memory setting (OWASP's
floor is 19 MiB with 2 passes) and measure a burst before shipping it.

**Recommendation (owner, 2026-11-19):** not now. bcrypt at cost 12 is acceptable; argon2 is a
far-future review, tracked in `docs/TODO.md`.
