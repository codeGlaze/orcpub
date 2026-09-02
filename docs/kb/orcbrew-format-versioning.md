# orcbrew format versioning + the new file extension

**Status: in-file mechanism IMPLEMENTED (on `feature/demo-content-tier`); the user-facing
EXTENSION is the only part still blocked on the community name poll.**

Implemented model — the version is a **compatibility class of the content**, not a build date:

- **v1 = fully backward compatible.** Ships as a **plain** file (no tag, no wrapper); old
  builds read it unchanged.
- **v2 = contains any non-backward-compatible feature.** Wrapped in an envelope
  `{:orcbrew/format-version 2 :orcbrew/requires [...features...] :orcbrew/content <plugin map>}`.
  The wrapper's keyword keys break an old build's "every top-level key is a source name" parse,
  so old builds bounce off it — the intended gate for incompatible content. A file's version is
  **classified from its content** by `orcbrew-format/detect-incompatible-features` (conservative:
  over-tag rather than under-tag), so nothing has to be tagged by hand.

Wiring (all in `orcpub.dnd.e5.orcbrew-format`): `stamp` (export — wraps v2, no-op for v1),
`compat-check` + `unwrap` (import — refuses a version beyond `supported-format-version`,
otherwise unwraps). Hooked into `save-orcbrew-blob!` + the pretty-print export, `validate-import`
(covers file import, share, and the demo boot-load), and the demo-pack emitter. Verified by
`orcbrew_format_test` + the real-app boot-load of the (now v2) demo pack.

**Still blocked (the extension NAME):** a distinct file extension for v2 files, to keep them out
of old builds' file *pickers*. Until it's named, v2 files keep `.orcbrew` — the envelope still
makes an old build *fail to load* rather than corrupt. When the name lands it's a one-line swap
on `orcbrew-format/new-extension`.

---

Original design notes (the extension *name* is being polled with the community and other
developers; everything else here is decided and now implemented per above):

## Why this exists

The content-extensibility refactor adds features (declarative field-schemas, the pool/grant
primitive, ASI spreads, …) whose exported content **older builds cannot read**. Old builds
in the wild also **fail opaquely** — they won't tell a user *why* an import failed. So we
need to stop incompatible files from reaching an old importer at all, and let new builds
check compatibility explicitly.

## The mechanism (three parts)

1. **A new file extension for the new format.** A distinct extension keeps new-format files
   out of an old build's file picker (its `accept=".orcbrew"` filter won't offer them) and
   reads clearly to a human. It's a *soft* gate — a user can force-pick "all files" — so it
   pairs with the in-file compat tag below. New builds read **both** the old `.orcbrew` and
   the new extension; only old builds are gated out of new files.

   **Extension NAME — OPEN (community poll).** Candidates so far:
   - `.orcbrewx` — keeps "orcbrew" intact, `x` = next-gen. *Current leading candidate / placeholder.*
   - `.orcbrewed` — a real word, "the finished brew."
   - `.orcgrog` — fully in-world, but drops the "brew" brand.

   Constraints on whatever wins: lowercase ASCII, no `+`/unicode/umlaut (breaks pickers and
   OSes), distinct enough from `.orcbrew` that old pickers won't grab it. **Reserve the
   extension bump for major, incompatible breaks only** — finer versioning rides the in-file
   tag, so we don't end up at `.orcbrew5`.

   **Placeholder until the poll resolves:** treat the extension as a single token
   `<NEW_EXT>` (standing in as `.orcbrewx`). Reference it from one constant so the final name
   is a one-place swap.

2. **In-file format-version tag** — `:orcbrew/format-version` at the top of the file. New
   builds read it first and refuse with a clear "needs app version X" message before touching
   content. The version number lives **here, inside the file**, NOT in the extension.

3. **Compatibility tag** — `:orcbrew/requires` (the features / minimum build the content
   needs). Checked by new builds on import; the precise "why" the extension can't give on old
   builds.

## Still open (besides the name)

- **Conversion tag** — undecided between: a *down-convert on export* (a new build also emits a
  reduced plain `.orcbrew` by stripping incompatible features, so old-build users get a
  usable-if-lesser file) vs a *migration/provenance marker* (records an old→new upgrade). See
  the discussion in `demo-content-tier.md`.
- Whether `format-version` + `requires` are per-source metadata (they should be — compat
  guards every export, not just the demo pack).

## Related

- `demo-content-tier.md` — the demo pack is the first `<NEW_EXT>` file and the forcing function.
