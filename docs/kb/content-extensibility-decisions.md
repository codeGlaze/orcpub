# Content Extensibility — Decisions & Audit

**Purpose:** Record *how* the content-extensibility direction was reached — the
pivots, the dead-ends, and why we changed our minds — plus the crisp decisions it
produced. For both humans and agents picking this up cold. Design record, not
verified source behavior. See [content-extensibility.md](content-extensibility.md).

**Date opened:** 2026-06-13. **Stage:** design; no production code changed.

---

## Part 1 — How the thinking evolved (audit)

Read this to understand what we were thinking at each step, not just where we landed.

1. **Start: "8 files to add a minor option — is there a better way?"**
   First instinct was a single content-type *registry* to kill the scattered
   registration boilerplate. Plausible, but it was reasoning from the symptom.

2. **Pushback: "we might be talking past each other."**
   Prompted to look at a real change — the Pact Boon builder (commit `6029fd0`) — and
   at a hypothetical dragonborn lineage builder, instead of theorizing.

3. **Evidence changed the framing.** The boon diff touched 10 files, but split into
   two unrelated costs: *registration* (mechanical, scattered) and *injection*
   (wiring boons into the warlock via positional function args). The registry idea
   only addressed the mechanical half — and the cheaper half. → **D1.**

4. **First Layer-2 idea: parent-keyed "slots."** Attach a boon to
   `[:class :warlock :pact-boon]`. Looked tidy.

5. **Caveat that broke it: 5e is expansion-driven.** Feats grant spells, ASIs, even
   pact boons; backgrounds grant feats; homebrew adds more later. Asked directly:
   does the proposal make cross-tapping easier or harder? Honest answer: rigid slots
   make it *harder*, because one option granted by several parents needs several
   attachment declarations. → pivoted to **type-addressed catalogs + grants** (D3),
   and split "grant a fixed thing" (keep modifiers, D4) from "grant a choice from a
   set" (new).

6. **Pseudocode requested** to make the intent concrete — confirmed the catalog/grant
   shape reads cleanly and that subraces already do exactly this by parent key.

7. **Documentation requested; KB location clarified.** The canonical agent KB lives
   on `agents/develop`, which already has overlapping docs
   (`spa-routing-architecture.md` covers the registration side) and an issue cluster
   (`homebrew-builders.md`: #58, #57/#209, #172/#170, #210/#107, #280, #173, #128)
   that are real instances of this problem. Restructured the docs to that KB's
   conventions and separated verified-from-code facts from the proposal. → **D8.**

8. **Compatibility raised as a hard constraint, audited before planning.** Users with
   existing orcbrew libraries and built characters can't be broken. Decided to audit
   the *current* persisted formats first (orcbrew/plugins map, strict-entity
   characters, localStorage), derive invariants, and constrain the design to be
   additive. The audit showed the design can be zero-migration *if* catalogs derive
   over existing storage (as subraces already do) and selection/option keys are
   preserved. → **D9.** See
   [content-extensibility-compatibility.md](content-extensibility-compatibility.md).

The throughline: each pivot came from concrete evidence (a real diff), a domain
constraint (5e's cross-pollination), or a user-data constraint (existing orcbrew and
characters) — not from preference. The registry survived; the slot idea did not.

## Part 2 — Decision summary

**D1 — Treat the "8-file" cost as two problems.** Registration boilerplate and
parent-injection are different shapes with different fixes. *Rejected:* one
"scaffolding" fix — it misses the fragile injection half where bugs hide.

**D2 — Layer 1 is data + existing factories, not a macro.** A `content-types`
descriptor list feeding loops that call the existing `reg-*-homebrew` factories.
*Rejected:* a `defcontent` macro — opaque expansion, buys nothing when factories exist.

**D3 — Layer 2 addresses options by type, not by parent slot.** Children declare a
type; consumers pull from a type catalog with an optional filter. *Rejected:* fixed
parent-keyed slots — they break when one option is granted by several parents.

**D4 — Keep the modifier system for fixed grants.** `mod5e/*` handles "grant this
specific thing"; leave it. Catalogs/grants are only for "choose from a set."

**D5 — Migrate subraces first.** They already use the target bucket-by-key pattern, so
step one is a behavior-preserving refactor and an easy review. *Rejected:* starting
with boons (touches the fragile class sub) or lineages (new capability, not refactor).

**D6 — Keep route-keyword `def`s; generate downstream.** They're referenced by symbol
at compile time; generate the bidi tree, route sets, pages, events, and subs.

**D7 — Registry namespace stays a dependency leaf.** Requires only specs and
`route-map`; views referenced by keyword and resolved in `core.cljs`, to avoid the
circular dep the code already works around (`events.cljs` ~204).

**D8 — Document first; no code until the subrace spike is reviewed.**

**D9 — Backward compatibility is a non-negotiable constraint; target zero-migration.**
Existing orcbrew libraries and saved characters must keep working with no user-facing
migration. The design stays additive: catalogs derive over the existing plugin
storage (don't reformat it), existing plugin keys and the `orcpub.dnd.e5` namespace are
preserved, and selection/option keys that characters may have chosen are not renamed.
Each migration step is guarded by an orcbrew + saved-character fixture. *Rejected:*
designing the storage model first and auditing compatibility afterward — the audit
would too late to reshape it. Full analysis:
[content-extensibility-compatibility.md](content-extensibility-compatibility.md).
