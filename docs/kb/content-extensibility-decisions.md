# Content Extensibility — Decisions

**Purpose:** Record the decisions behind the content-extensibility direction and the
options we rejected, so they aren't re-litigated. Design record, not verified source
behavior. See [content-extensibility.md](content-extensibility.md) for the design.

**Date opened:** 2026-06-13. **Stage:** design; no production code changed.

---

**D1 — Treat the "8-file" cost as two problems.** Registration boilerplate and
parent-injection are different shapes with different fixes. Solving only registration
(the cheaper half) misses where the bugs are. *Rejected:* one "scaffolding" fix.

**D2 — Layer 1 is data + existing factories, not a macro.** A `content-types`
descriptor list feeding loops that call the existing `reg-*-homebrew` factories.
*Rejected:* a `defcontent` macro — cljc macro plumbing and opaque expansion buy
nothing over data when the factories already exist.

**D3 — Layer 2 addresses options by type, not by parent slot.** A child declares its
type; consumers pull from a type catalog with an optional filter. *Rejected:* fixed
parent-keyed slots like `[:class :warlock :pact-boon]` — they break when one option
(e.g. a pact boon) is granted by several parents, which 5e and homebrew require.

**D4 — Keep the modifier system for fixed grants.** `mod5e/*` already handles "grant
this specific thing" well; leave it. Catalogs/grants are only for "choose from a set."
Both feed the same entity build.

**D5 — Migrate subraces first.** They already use the target bucket-by-key pattern, so
the first step is a behavior-preserving refactor and an easy review. *Rejected:*
starting with boons (touches the fragile class sub first) or lineages (that's new
capability, not a refactor).

**D6 — Keep route-keyword `def`s; generate downstream.** They're referenced by symbol
at compile time. Generate the bidi tree, route sets, pages map, events, and subs from
the registry instead.

**D7 — Registry namespace stays a dependency leaf.** It may require only spec
namespaces and `route-map`. Views are referenced by keyword and resolved in
`core.cljs`, to avoid the circular dependency the code already works around
(`events.cljs` ~204).

**D8 — Document first; no code until the subrace spike is reviewed.** Original request
was a plan, not action.
