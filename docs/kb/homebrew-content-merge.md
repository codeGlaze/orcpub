# Homebrew content merge — and the `feat-options` trap

**Why this doc exists:** people (humans AND agents) keep concluding "homebrew feats aren't
supported" because they read `feat-options`, see it's nearly empty, and stop. **That conclusion
is wrong**, and the same mistake generalizes to other content types. Read this before claiming a
content type isn't homebrew-extensible.

---

## The trap, concretely (feats)

`opt5e/feat-options` (`options.cljc` ~1212) looks like dead/empty code — **nearly every built-in
feat is `#_`-commented**, leaving effectively just Grappler:

```clojure
(defn feat-options [spell-lists spells-map]
  [#_(feat-option {:name "Alert" …})     ; commented
   #_(feat-option {:name "Athlete" …})   ; commented
   … ])                                  ; only Grappler (the one SRD feat) is live
```

**This is intentional, not broken.** orcpub ships only SRD-licensed content; the rest of the
"official" feats/subclasses/etc. are commented out and are expected to come from user **orcbrew
packs** (homebrew plugins). `content_reconciliation.cljs:194` literally notes: *"Grappler is the
only SRD feat."*

**The real feat choice is assembled elsewhere** — `template.cljc:1540`:

```clojure
(opt5e/feat-selection-2
 {:options (concat
            (opt5e/feat-options spell-lists spells-map)              ; built-in half (Grappler)
            (map (partial opt5e/feat-option-from-cfg …) feats))})    ; ← HOMEBREW feats
```

where `feats` is the `::feats5e/feats` subscription (plugin feats). So the feat choice =
**built-in `feat-options`, then the homebrew feats concatenated in.** Homebrew feats **are**
available. The mistake is reading the static `feat-options` def and missing the concat at the
assembly point.

The flow: `::e5/feats` (in `:plugins`) → `::feats5e/plugin-feats` (`spell_subs.cljs:495`,
`(mapcat (comp vals ::e5/feats) plugin-vals)`) → `::feats5e/feats` (`spell_subs.cljs:1034`, adds
`:edit-event`) → passed as `feats` into `template-selections` (via `equipment_subs.cljs:301`) →
`concat`-ed into the feat selection's `:options` (`template.cljc:1540`).

---

## The general pattern (applies to most content types)

A top-level homebrew content type **X** is surfaced like this — and you must look at the **merge
point**, not the static `*-options` def:

1. **Storage:** items live in `:plugins` under a `::e5/X` key (e.g. `::e5/feats`, `::e5/races`).
2. **Plugin sub:** a subscription gathers them — `(mapcat (comp vals ::e5/X) plugin-vals)`
   (e.g. `::feats5e/plugin-feats`, `::races5e/plugin-races`).
3. **Merge point (THE thing to find):** at the choice's assembly site, the plugin entries are
   **`concat`-ed** with the built-in options, each mapped through the type's option constructor.
   This happens in `template-selections` (`template.cljc`) or the type's own sub
   (e.g. `::races5e/races` in `spell_subs.cljs`).

So the built-in `X-options` is only **half** the picture (the SRD built-ins, mostly commented).

---

## Verification recipe — "is homebrew X supported, and where does it merge?"

DON'T answer from the static `X-options` def. Instead:

1. `grep "::e5/X"` — is there a plugin content key + a `plugin-X` sub (`mapcat (comp vals ::e5/X)`)?
2. `grep` where that sub's value is **`concat`-ed into a selection's `:options`** (look in
   `template.cljc` `template-selections`, and the type's sub in `spell_subs.cljs`).
3. If you find the concat → homebrew **is** merged. If the static `X-options` is mostly
   `#_`-commented, that's SRD-minimal built-ins **by design**, not dead code.

**Known exception (a real gap, not the trap):** **fighting styles** genuinely have *no* plugin
path — no `::e5/fighting-styles` key, no plugin sub, no concat; `fighting-style-selection`
(`options.cljc:1793`) always returns the fixed 6 built-ins. So *that* one really isn't
homebrew-extensible (yet). The way to tell the difference is step 2: feats have the concat;
fighting styles don't.

---

## TL;DR
- Built-in `*-options` defs are **SRD-minimal and mostly `#_`-commented on purpose**; the rest
  comes from orcbrew plugins.
- Homebrew merge happens at the **assembly/`concat` point**, not in the static `*-options` def.
- **Feats ARE homebrew-extensible** (`template.cljc:1540`). Don't re-derive otherwise.
- Fighting styles are the genuine exception (no plugin path) — confirmed by the *absence* of a concat.
