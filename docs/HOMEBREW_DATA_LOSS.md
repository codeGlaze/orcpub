# Homebrew Data Loss — Findings & Decisions

> **STATUS: implemented.** This is the root-cause record that started the work;
> the design directions here (§6–§10) all shipped — resilient loader (B1),
> quarantine-for-repair (B2), quota surfacing (B3), the shared spec registry
> (B4), and the builder escape hatches. See `docs/HOMEBREW_REMEDIATION_ROADMAP.md`
> for current state. Kept as the "why/how we got here" analysis so it isn't
> re-derived.
>
> **Purpose.** Capture the root-cause analysis, verified mechanisms, corrected
> assumptions, and agreed design direction for the "custom class / source
> disappears" class of bugs.
>
> Investigated on branch `claude/custom-class-source-error-2k5ykd` (off
> `develop`). File:line references are against that branch and may have drifted.

---

## 1. The triggering symptom

A user creates a custom class under a new source ("Eberron: Forge of the
Artificer"). On clicking the post-save "click **here** to export" banner they get:

```
Export validation failed for "Eberron: Forge of the Artificer":
Validation errors found:
  • at root: Failed validation: cljs.core/map?
    Got: {:orcpub.dnd.e5/classes {:artificer {:key :art...
```

Follow-on reports: the class is **not** present in the file produced by the
"My Content" cloud-export button, and a fresh tab / refresh shows **no** homebrew
content at all.

---

## 2. Root cause of the banner error (FIXED on this branch)

`reg-save-homebrew`'s success banner and `save-selection`'s banner dispatched
the export with the plugin wrapped in `(str …)`:

```clojure
;; events.cljs:673 and :790 (BEFORE)
{:on-click #(dispatch [::e5/export-plugin option-pack (str (new-plugins option-pack))])}
```

`::e5/export-plugin` → `validate-and-show-modal-or-export` (`events.cljs:3772`)
runs `validate-before-export` → `(spec/valid? ::e5/plugin <arg>)`. `::e5/plugin`
is a `map-of`, so `map?` is the outermost predicate. A **string** fails `map?`
at the root — hence the error, with `Got:` showing the string rendering of the
map. `save-orcbrew-blob!` (`events.cljs:3755`) already serializes internally
(`(str data)` / pprint), and every other call site (e.g. `views.cljs:8193`)
passes the **raw map**, so the `str` was simply wrong.

**Fix:** drop the `str` wrapper at both sites (now `(new-plugins option-pack)`).

### Why it stayed hidden for ~8 years
- `str` was present from the original 2017 banner (Larry, `6b3c7640`,
  "add big scary warning message about homebrew saving").
- 2025-08-24 (`ab8d4cf8`, "FIXED saving from the popdown banner") changed
  `(plugins …)` → `(new-plugins …)` but left `str` intact.
- 2026-06-13 (`e512dc45`, "orcbrew import/export events + homebrew
  save-validation") added `validate-before-export`. For 8 years export did no
  validation, so a pre-stringified value was harmless (`save-orcbrew-blob!`
  re-`str`s, which is idempotent on a string). The new spec gate turned a
  long-dormant wart into a hard failure.

This `str` line is **identical in upstream `orcpub/orcpub` develop**, which is
what dungeonmastersvault.com builds from — so it is an upstream bug, not a
codeGlaze regression. The deployed error text (`"Validation errors found:"`
with no count; `"at root: Failed validation: cljs.core/map?"`) is an *older*
form of the current humanized formatter, confirming the live build predates
recent validation refactors.

---

## 3. Verified mechanisms (the data-loss layers)

| # | Mechanism | Location | Effect |
|---|-----------|----------|--------|
| A | Loader is **all-or-nothing** | `db.cljs:252-270` (`reg-local-store-cofx`); plugins cofx `db.cljs:307-310` | If **any** stored plugin fails `::e5/plugins`, the loader returns `nil` and the **entire** homebrew library is dropped from memory on load (fresh tab / refresh shows nothing). It logs `"Invalid stored item, ignoring: plugins"` + humanized errors. It does **not** `removeItem`, so the raw data survives in `localStorage` even though it isn't displayed. |
| B | `set-item` **swallows** write failures | `db.cljs:162-165` | `try/catch` that only `prn`s `"FAILED SETTING LOCALSTORAGE ITEM"`. A quota-exceeded write (large library vs ~5 MB cap) fails silently → data becomes memory-only with no user signal → a refresh then truly loses it. Identical on `develop`. |
| C | Progressive import **silently skips** invalid items | `events.cljs` `import-progressive` (`:skipped-items`) | Re-importing a file drops any item that fails the spec; logs `"Skipped N invalid item(s)"` to console only. |

`set-plugins` (`events.cljs:3733`) persists via `plugins->local-store-interceptor`
(`events.cljs:140,198`) → `plugins->local-store` (`db.cljs:238-240`) →
`set-item "plugins" (str plugins)`. So saves/toggles do write to `localStorage`.

---

## 4. Corrected assumptions (do NOT re-derive these the wrong way)

These were asserted earlier in analysis and then **falsified** by reading code:

1. **"Export silently filters/drops invalid items."** FALSE. The export-warning
   modal path keeps everything: `:export-with-auto-fix` (`events.cljs:3857`)
   fills dummies; `:export-as-is` (`events.cljs:3907`) passes through unchanged.
   The only *lossy* export path is the hard `:else` spec-failure branch in
   `validate-and-show-modal-or-export`, which errors and exports **nothing**.
   → "class missing from the exported file" is **not** explained by export code;
   it needs the user's actual `:plugins` EDN to diagnose. (Most likely the file
   *does* contain it, or a different source was exported, or the item never
   committed to `:plugins` — unverified.)

2. **"Save-time vs load-time spec is a trap door for the class (missing
   `:option-pack`)."** FALSE for classes. Save spec `::class5e/homebrew-class`
   (`classes.cljc:21`) = `(keys :req-un [::name ::key ::option-pack])` is
   **stricter** than load's `::homebrew-item` (`e5.cljc:18`) =
   `(keys :req-un [::option-pack])`. So a saved class cannot fail load on those
   fields. The trap-door *principle* still holds in general (see §6.4), but the
   direction for classes is save ⊇ load, which is safe.

3. **Confidence map.** VERIFIED: the `str` bug (§2), loader all-or-nothing (3A),
   silent `set-item` (3B), import skip (3C), export-keeps-not-drops (4.1).
   UNVERIFIED / needs user data: why the class is absent from their exported
   file; the exact spec field (if any) their class violates.

---

## 5. Emergency recovery (no redeploy needed)

The live copy of unsaved/unloadable content is in the running app and/or raw
`localStorage`. Order of effort:

1. **Read raw localStorage** (covers mechanism 3A — data present but not
   displayed): in the affected tab's console,
   `copy(localStorage.getItem('plugins'))`, save to a file. Check it contains
   the class: `/artificer/i.test(localStorage.getItem('plugins')||'')`.
2. **If absent (memory-only, mechanism 3B):** hook `setItem` to capture the
   in-memory map even if the write fails, then trigger a `set-plugins` via a
   non-destructive **enable/disable toggle** of the source in My Content
   (`::e5/toggle-plugin`, `events.cljs:4004`, persists the *whole* map without
   re-validating items):
   ```js
   window.__grab={}; const _s=localStorage.setItem.bind(localStorage);
   localStorage.setItem=(k,v)=>{window.__grab[k]=v; try{return _s(k,v)}catch(e){}};
   // toggle the source off/on in My Content, then:
   copy(window.__grab.plugins);
   ```
**Do not refresh/close** until captured. The cloud export is not a safe backup
here (it omits the class).

---

## 6. Agreed design direction

Principle: **validation may gate, but must never be the only exit, and must never
cause data loss.**

### 6.1 Raw escape hatch on export failure  *(priority 1)*
The `:else` spec-failure branch must offer an unvalidated raw dump
(`(str (:plugins db))` → blob) so a validation failure can never strand data.
Add an `::e5/emergency-export-raw` event and surface it on every error surface
(export failure, load rejection, import skip). Keep the existing auto-fill/modal
behavior — the user's stated intent (block-missing or auto-fill with dummies) is
correct and already implemented; we only add the escape hatch.

### 6.2 Resilient loader + backups  *(priority 1)*
Replace the all-or-nothing `reg-local-store-cofx` with per-plugin validation:
keep valid plugins, quarantine invalid ones under `plugins:rejected` (don't
discard). *(A `plugins:lkg` last-known-good snapshot was also proposed here but
later dropped — see §10 for why the full-copy snapshot didn't scale.)*

### 6.3 Surface storage failures + headroom  *(priority 2)*
`set-item` should surface failures (warning + trigger raw-export prompt) instead
of swallowing. To stay under the ~5 MB cap, compress the plugins blob
(e.g. LZ-string) before considering a storage-engine change. IndexedDB is **not**
deprecated (that's WebSQL), but raw IndexedDB is verbose/async — prefer a thin
wrapper (localForage) only if compression isn't enough.

### 6.4 Unify save/load specs + drift test  *(priority 2)*
- **Rule:** load/import validation must be a **subset** of save validation —
  never require at load what isn't enforced at save.
- **Mechanism:** a single `content-type → spec` registry, dereferenced by **both**
  save and load (load becomes content-type-aware instead of the generic
  `::homebrew-item`). One source of truth ⇒ no drift. NOTE: this makes load
  stricter, so it must ship **with** the resilient loader (6.2) to avoid
  quarantining old loose-but-real data.
- **Enforcement:** a `clojure.test.check` property per content type —
  `spec/gen` the save spec, assert each sample satisfies the load spec — fails
  CI with a shrunk counterexample the moment the two diverge. Caveat: custom
  predicates (`keyword-starts-with-letter?`) need `spec/with-gen` generators.

---

## 7. Testing (shipped)

The plan below became the backend-free Playwright harness in `e2e/` (see
`e2e/README.md` for how to run it and the authoring gotchas). Every planned case
landed as a scenario in `e2e/scenarios/`:
- Resilient loader — `resilient-loader.spec.ts`, `boot-resilience.spec.ts`
  (valid survive, invalid → `plugins:rejected`).
- Emergency raw export — `emergency-export.spec.ts`.
- Quota path — `quota-failure.spec.ts` (mock `QuotaExceededError` → rescue prompt).
- Spec drift — the generative `save ⊆ load` property (`content_specs_test`, JVM).
- Quarantine repair, keyword-trap import, round-trip-and-USE, broken-content
  survival, and the builder escape hatches each have their own scenario too.

---

## 8. Open questions

1. Why is the class absent from the user's **exported file**? Needs their raw
   `:plugins` EDN (export code keeps items; this is unexplained).
2. Does their class actually fail any spec, or was the only real bug the `str`
   wrapper (§2)? The screenshot error is fully explained by `str` alone.
3. Did the user hit the localStorage quota (mechanism 3B)? The "afraid it'll be
   GC'd on refresh" suggests memory-only data, which points at 3B.

---

## 9. Feasibility & prior art (verified)

### Load validation is a GATE, not part of "applying" content
`:initialize-db` (`events.cljs:208-228`) injects the `::e5/plugins` cofx and
stores the result verbatim: `(cond-> default-value plugins (assoc :plugins
plugins) …)`. The spec only decides keep-all-vs-`nil`; it never transforms the
data, and all downstream consumers (templates, builder, subs) read `db :plugins`
directly regardless of which spec admitted it. **Therefore the resilient loader
(§6.2) is safe** — returning the valid *subset* is stored exactly as the whole
map is today; nothing downstream changes.

Caveat for §6.4 (per-type registry): the generic loose `::homebrew-item` (only
`:option-pack`) is permissive *on purpose* so heterogeneous content — including
content types with no dedicated per-type spec — passes. That looseness is not a
functional necessity but it prevents over-rejection. A per-type registry must
therefore (a) fall back to the loose check for unregistered content types and
(b) ship together with the resilient loader, or it trades "drop everything" for
"quarantine usable content."

### State of generative / drift testing on `develop`
- `test.check` is a dependency (`project.clj:24`).
- Generative tests exist for *other* domains only: character speeds
  (`character_test.cljc:25`, `defspec`), modifier sampling
  (`template_test.clj:14`, `spec/gen`).
- Plugin spec tests are **example-based**, not generative:
  `e5_test.clj:16-20` (`test-specs`) asserts specific maps validate against
  `::e5/plugins`/`::e5/plugin`.
- **The save ⊆ load generative drift test (§6.4) does NOT exist yet.**

### Related prior branches (adjacent, not duplicates)
- `claude/fix-custom-items-disappearing-DW8rb` — custom **equipment/magic items**
  vanishing via subscriptions/derivation (`equipment_subs`, `api_subs`, `subs`;
  `docs/CUSTOM_ITEMS_INVESTIGATION.md`; `e2e/scenarios/custom-items.spec.ts`).
  Same symptom *family* ("custom thing disappears") but a different mechanism;
  does **not** touch `db.cljs` (the plugins loader).
- `claude/fix-orcbrew-errors-bLkFJ` — export-warning/validation modal work
  (see `docs/orcbrew-validation-followups.md`).
Neither contains the resilient plugins loader or the drift test — both remain
net-new.

---

## 10. Implementation status

Landed on `claude/custom-class-source-error-2k5ykd`:

| Item | Status | Where |
|------|--------|-------|
| Export `str` bug fix (§2) | ✅ done | `events.cljs:673,790` |
| Pure `salvage-plugins` helper (§6.2) | ✅ done | `e5.cljc`; JVM tests `e5_test.clj` |
| Resilient loader + `plugins:rejected` (§3A/§6.2) | ✅ done | `db.cljs` (`::e5/plugins` cofx) |
| `::e5/emergency-export-raw` + export `:else` escape hatch (§6.1) | ✅ done | `events.cljs` (`select-emergency-export`); cljs tests `events_test.cljs` |

New localStorage keys: `plugins:rejected` (quarantined invalid sources),
`<key>:corrupt` (unreadable blobs preserved for recovery).

**Test execution note:** the frontend builds and tests *without* the backend
(the only heavy backend dep, `com.datomic/peer`, resolves from Maven Central;
the cljs build pulls no datomic namespaces). Verified in-session:
- `lein test orcpub.dnd.e5-test` → **7 tests, 19 assertions, 0 failures/0 errors**
  (the pure `salvage-plugins` logic, executed on the JVM).
- `lein fig:test` → **cljs test build compiles clean** (`db.cljs`, `events.cljs`,
  `e5.cljc`); only pre-existing third-party `goog.math.Long` warnings in
  test.check remain.

Still pending real execution: the re-frame/DOM cljs tests (`events_test.cljs`
etc.) need a headless browser — run them via the `testing/develop` Playwright
harness (no browser is cached in a fresh web-session container).

Since done (see roadmap): the rejected-repair **UI** (B2 quarantine panel),
`set-item` quota surfacing (B3), the per-type spec registry + generative drift
test (B4), and the full Playwright e2e (`e2e/`). Blob compression stays
deferred (an optimization, not a data-loss fix).

The `plugins:lkg` last-known-good snapshot (originally proposed in §6.2) was
**dropped**: it stored a full copy of the plugins blob every boot, which at
realistic 3–4 MB import sizes doubles the footprint and blows the ~5 MB
localStorage cap — so the snapshot write fails silently exactly when it would
matter. With the defensive loader (never discards) and the emergency raw export
(always recoverable) doing the real work, the redundant snapshot bought nothing.
Larger-quota storage (IndexedDB/localForage) and compression are tracked in
`docs/TODO.md`.
