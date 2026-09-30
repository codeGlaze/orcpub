# Server and share-link safety notes

Why the server's request limits, boot page, image fetch, auth-token handling and share links are
shaped the way they are.

Related: `docs/CHARACTER-IMAGE-FETCH.md`, `docs/PDF-EXPORT-CAPACITY.md`; on `agents/develop` only:
`auth-state-in-app-db.md`, `share-links.md`, `secrets-in-boot-output.md`,
`plan-669-merge-verification.md`.

## Request body cap — `system.clj` `max-form-content-size`

- A 50MB POST to `/character.pdf` was neither rejected nor completed: the connection stayed open.
  The cap is 2MB, which is generous for a character spec.

## Boot rescue — `index.clj`

- `boot-rescue` sits below the fold during a normal boot, and is inline markup with its own
  `<script>` tag rather than part of the app bundle: every dependency it takes is one more thing
  that can be broken at the moment it is needed.

## Image fetch — `pdf.clj`

- A server that sends a byte just before each read timeout expires holds the connection for the
  timeout times the number of reads: 128 KB in 8 KB reads is 16 reads, 160 s, with an export slot
  held throughout. The rest of the guard's history (schemes, address ranges, rebinding) is
  in `docs/CHARACTER-IMAGE-FETCH.md` and `docs/TODO.md`.

## Auth token in app-db — `event_utils/get-auth-token`, `subs`

- `a0e20a8` fixed the wrong-key token guard in `::mi5e/custom-items` but missed the same guard in
  `::mi5e/remote-item`.
- The `:user` sub was kept bit-for-bit from its pre-HOF implementation.
- Pending: remove the dead storage at `db[:user]`, and fix the double nesting at
  `db[:user-data][:user-data]`.

## Share links — `share_url.cljs`, `events.cljs`

- **No hash or signature on the payload.** A hash would only prove the link was not corrupted in
  transit, not that its content is safe; with no server secret there is nothing to sign with. The
  decoder's own gates are the protection.
- **`build-share-payload` never drops content to fit.** A feat or trait *is* its description. Its
  `:full` / `:long` / `:file` tiers are not described in `share-links.md`.
- **Shared-content overlays force a rebuild once decoded** (`9db84754`): without it the character did
  not render on first load, the "paste-twice" bug.
