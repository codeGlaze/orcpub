# orcbrew format versioning + the new file extension

**Status: open decision, HIGH PRIORITY.** The extension *name* is being polled with the
community and other developers. Everything else here is decided. Design-in-progress on
`feature/demo-content-tier`; surfaced by the demo tier but applies to **all** content.

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
