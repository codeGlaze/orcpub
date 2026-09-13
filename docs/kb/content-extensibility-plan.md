# Content Extensibility — Implementation Plan

> ⚠️ **SUPERSEDED / DEFLATED — see `docs/kb/content-extensibility-direction.md`.** This doc is *history*: the grand registry / catalog-grant framing was scaled back after a readability review. Read it for analysis/context, not as the plan.

**Purpose:** A step-by-step playbook to implement the content-extensibility redesign
safely. Written for agents with little context: follow it literally, in order, and stop
where it says stop. Do not improvise.

**Status:** Plan for **not-yet-started** work. The design is in
[content-extensibility.md](content-extensibility.md); the hard constraints are in
[content-extensibility-compatibility.md](content-extensibility-compatibility.md);
rationale in [content-extensibility-decisions.md](content-extensibility-decisions.md).

**Branch note:** references use the monolithic frontend layout; on `agents/develop`
views are split. Grep symbols to confirm exact locations.

---

## Golden rules (read before doing anything)

1. **Implement on a code branch** off the active code line (e.g. `feature/content-extensibility`).
   Do **not** write source on `agents/develop` (docs-only) or on the docs branch.
2. **One phase per branch, one phase per PR.** Do not start a phase until the previous
   phase is merged and green.
3. **Behavior-preserving until Phase 5.** Phases 0–4 must not change any built character
   or any loaded library. The Phase 0 golden test must stay green on every commit.
4. **Never change persisted or exported shapes. Never rename an existing key** — plugin
   key (`:orcpub.dnd.e5/…`), route keyword, selection key, option key, or localStorage
   key. If a step seems to require it → **STOP and ask a human.** (Why: compatibility
   doc §3 invariants.)
5. **Do not touch the modifier system (`mod5e/*`).** Out of scope.
6. **Run the full gate before every commit** (next section). Never commit a red gate.
7. **Keep the diff inside the files listed for the phase.** If the change spreads beyond
   them → **STOP** and reassess; the phase was misunderstood.
8. **If anything is ambiguous, STOP and ask.** Do not guess. A wrong guess here breaks
   users' saved characters.
9. **Never "fix forward" a behavior difference.** If the golden test changes, revert and
   reassess — do not patch until it passes.

## The verification gate (exact commands)

Run all, expect all green, before each commit:

```
lein lint        # clj-kondo; must pass
lein test        # clj + cljc tests
lein fig:test    # compiles and runs the cljs tests (incl. the golden test below)
```

The Phase 0 golden test is a cljs test, so it runs under `lein fig:test`.

---

## Phase 0 — Build the safety net (no production code)

**Goal:** a test that fails loudly if any later phase alters a built character or a
loaded library. Everything else depends on this existing first.

**Files:** new `test/cljs/orcpub/dnd/e5/extensibility_golden_test.cljs`; reuse an
existing fixture from `test/*.orcbrew` (or add a small one); committed golden EDN
snapshots under `test/`.

**Steps:**
1. Follow the existing test pattern in `test/cljs/orcpub/dnd/e5/subs_test.cljs`
   (`reset! app-db`, `rf/clear-subscription-cache!`, `rf/subscribe`, assert).
2. Set up representative cases that exercise the cross-links being migrated:
   - a Dwarf character with a homebrew subrace,
   - a Warlock character with a pact boon (and one invocation),
   - an existing `.orcbrew` fixture loaded into `(:plugins app-db)`.
3. For each case, build the character through the existing pipeline and capture the
   **strict** form (`char5e/to-strict`) as an EDN snapshot. Commit those snapshots.
4. The test asserts the freshly built character equals the committed snapshot.

**Gate:** the three commands pass; snapshots committed.
**Done when:** the golden test passes on unmodified `main` code.
**STOP if:** a build is nondeterministic (snapshots won't stabilize) — report it; do not
loosen the assertion to make it pass.

## Phase 1 — Generic option injector, proven on subraces

**Goal:** introduce one generic "group plugin options by a parent key" function and route
**subraces** through it with byte-identical output. This is the smallest possible Layer 2
step and it changes no behavior.

**Preconditions:** Phase 0 merged and green.

**Files:** new leaf ns `src/cljc/orcpub/dnd/e5/option_catalog.cljc`;
`src/cljs/orcpub/dnd/e5/spell_subs.cljs` (the `::races5e/plugin-subraces-map` and
`::races5e/races` subs, ~887 / ~893).

**Steps:**
1. In the new ns, write a pure function that reproduces today's grouping
   (`(group-by <key> options)`). No new behavior, no new data shape.
2. Re-point `::races5e/plugin-subraces-map` to call it. The value must be identical.
3. Run the gate. The golden test (Dwarf + homebrew subrace) must still pass.

**Constraints:** the new ns may require only data/spec namespaces — **no** requires on
`events`, `subs`, or `views` (keeps it a dependency leaf; compatibility doc and
decisions D7).
**Gate:** all green; diff limited to the two files.
**Done when:** subraces resolve through the generic function with identical builds.
**STOP if:** the golden subrace character changes at all → revert.

## Phase 2 — Migrate subclasses onto the injector

**Goal:** same as Phase 1, for `::classes5e/plugin-subclasses-map` (`group-by :class`,
~893). Proves the pattern generalizes.

**Files:** `option_catalog.cljc` (reuse the fn), `spell_subs.cljs` (the subclasses sub).
**Gate / Done / STOP:** identical to Phase 1, with a subclass golden case.

## Phase 3 — Boons and invocations onto grants (the risky migration)

**Goal:** replace the positional `boons`/`invocations` arguments threaded into the
warlock with a catalog/grant pull — **preserving the selection key and every option
key.** This is where compatibility can break; treat it carefully.

**Preconditions:** Phases 0–2 merged and green.

**Files:** `src/cljc/orcpub/dnd/e5/classes.cljc` (`warlock-option` ~2987,
`pact-boon-options` ~2629, the invocation options); `src/cljs/orcpub/dnd/e5/spell_subs.cljs`
(`base-class-options` ~932, `::classes5e/classes` ~945).

**Hard requirement:** the "Pact Boon" selection's key and each boon/invocation option key
(derived via `common/name-to-kw` of the same names) must be **unchanged**. Verify against
the golden Warlock-with-boon character — it must be byte-identical after the change.

**Steps (small, in order):**
1. Add a catalog read for `:pact-boon`, derived from the existing `::e5/boons` map
   (same data, no storage change).
2. Make the warlock pull boons from that catalog instead of the positional argument,
   producing the **same** selection and option keys as before.
3. Only after the catalog path is proven, remove `boons` from the positional signatures
   (`warlock-option`, `base-class-options`) and the `::classes5e/classes` inputs.
4. Repeat 1–3 for invocations.

**Gate:** all three commands green; golden test green, **especially** the Warlock+boon
case.
**STOP if:** the golden Warlock character changes in any way → revert. A changed selection
key is a compatibility break (compatibility doc §3 invariant 3, §5 risk 1).

## Phase 4 — Layer 1 content-type registry (independent track; micro-steps)

**Goal:** collapse the scattered registration into one `content-types` descriptor list,
reusing the **existing** factories (`reg-save-homebrew`, `reg-new-homebrew`,
`reg-edit-homebrew`, `reg-local-store-cofx`, `builder-page`) and the **existing** keys and
route keywords. No new content types here. This is compatibility-neutral (decisions D2).

**Do it one subsystem per PR**, each independently verifiable:
- **4a** subs: replace the per-type `::…/builder-item` passthrough subs with a loop.
- **4b** db: build the `default-value` builder-item slots and `reg-local-store-cofx`
  calls from the registry.
- **4c** events: generate `set-`/`reset-` events and the `reg-*-homebrew` calls from the
  registry.
- **4d** routes: derive the bidi tree, route sets, and `routes.clj` allowlist from the
  registry. Keep the `(def …-route :kw)` lines (decisions D6).
- **4e** core: build the `pages` map from the registry; resolve `:view` by keyword in
  `core.cljs` (which already requires `views`) — do not store view fns in the registry
  (decisions D7).

**Per micro-step:** add descriptors for the **existing** types only; convert that one
subsystem to a loop; confirm the registered routes/events/subs/keys are identical (the
app boots, the golden test passes, no route/event/sub/key name changed).
**Gate:** all green; golden test green.
**STOP if:** any route, event, subscription, or localStorage key name changes → revert.

## Phase 5 — New capability: dragonborn lineage (only after 1–4)

**Goal:** add one new content type end-to-end, as proof the architecture pays off.

**Steps:**
1. Add one descriptor to `content-types` with a **new** plugin key in the
   `orcpub.dnd.e5` namespace (compatibility doc §1a requires that namespace).
2. Write the builder form and the `homebrew-*` spec.
3. Declare a grant on dragonborn for the lineage/ancestry catalog. Convert
   `dragonborn-option-cfg` (`spell_subs.cljs` ~759) from a `def` to a function (or a
   catalog-reading sub) only as far as needed.

**Gate:** golden test green (existing characters and libraries unaffected) **plus** a new
test: a lineage from an imported `.orcbrew` appears under dragonborn.
**STOP if:** any existing golden case changes.

---

## Stop-and-ask triggers (summary)

Stop and get a human when:
- a step seems to require renaming or removing an existing key (plugin / route /
  selection / option / localStorage),
- the golden test changes and you can't explain why,
- the diff grows beyond the files listed for the phase,
- you'd need to loosen a spec to load existing data,
- you're unsure whether a change is additive.

## Two standing rules for the catalog/grant phases (3c onward)

- **Keys from stable ids, never display names.** Pass each catalog item's stored `:key`
  through to `option-cfg`/`selection-cfg`; never let identity re-derive from a `:name`
  that display code may manipulate (this orphaned saved characters once — see
  `feature/name-keyword-fix` and compatibility §1b). Keep display separate from identity.
- **Catalogs are layered, memoized subs.** A `grant-choice` references a catalog `reg-sub`;
  it must not rebuild a whole option list inside a hot subscription. Add a short guard
  comment at each catalog sub so the layering isn't collapsed later.

## Do NOT

- change persisted or exported data shapes,
- rename existing keys,
- re-derive identity keys from display names / call `name-to-kw` on manipulated names,
- recompute a whole catalog inside a hot subscription,
- touch `mod5e/*`,
- combine phases or skip the gate,
- commit a red gate,
- patch over a behavior difference instead of reverting.

## References

- [content-extensibility.md](content-extensibility.md) — design.
- [content-extensibility-compatibility.md](content-extensibility-compatibility.md) —
  invariants and risk surfaces (read §3 and §5 before Phase 3).
- [content-extensibility-decisions.md](content-extensibility-decisions.md) — why.
