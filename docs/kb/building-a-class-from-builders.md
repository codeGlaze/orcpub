# Building a class from the builders — verified capability + gaps (Artificer worked example)

What a homebrew **class** can actually be assembled from today, traced from code (file:line cited), using
Artificer (non-SRD — cannot ship, must be user-built; see D28) as the stress case. This is verified
*mechanics* reference, not design. Companion to `class-feature-catalogue.md` (feature shapes),
`spell-slot-progression.md` (the slot gap), and the content-extensibility track (pools/grants).

Markers: **VERIFIED** = read from code, file:line cited. **GAP** = a capability a full Artificer needs
that the builders don't expose yet.

## The capability witness — VERIFIED
`src/cljc/orcpub/dnd/e5/templates/ua_artificer.cljc` is a complete (older UA) Artificer expressed in the
**existing primitives** — `opt5e/class-option` cfg, a `:spellcasting` block, multiselect selections,
`level-val` scaling, tool profs, subclasses. It is **entirely `#_`-commented** (dead): proof the engine
*can* express the class shape, but NOT shippable (non-SRD, same rule as Maneuvers/Mariner). So the gap is
the **authoring layer**, not the engine (matches D18).

## What a homebrew class/subclass can already express — VERIFIED
- **Spec is minimal**: `::homebrew-class` requires only `name`/`key`/`option-pack` (`classes.cljc:21`);
  everything else is the option cfg.
- **`subclass-option` accepts the full shape** (`options.cljc:2558`): `:profs`, `:selections`,
  `:spellcasting`, `:modifiers`, `:level-modifiers`, `:traits`, `:prereqs`, and `:levels {N {:modifiers
  :selections}}` (level-gated features + choices). A homebrew class has the same richness.
- **Spellcasting is data**: `spellcasting-template` (`options.cljc:702`) turns a `:spellcasting`
  `{:level-factor :cantrips-known :spells-known :known-mode :ability :spell-list}` into full/half/third
  casting with real spell choices and an **optional custom spell list** assoc'd under the class key.
- **Homebrew builders already exist** for class, subclass, **invocation**, **boon**, and a generic
  **selection** (pool) — `content_types.cljc:82/90/111/141/149`.
- Homebrew builder classes get **ASI/hit-points automatically** (`options.cljc:2787`).

## Infusions ≈ the invocation/boon pool pattern — VERIFIED
Infuse Item is the load-bearing Artificer piece, and it's structurally the **Eldritch Invocations**
pattern, already in the codebase:
- `eldritch-invocation-selection` (`options.cljc:3109`) is a `selection-cfg` with `:multiselect? true`
  and a `:ref`, options supplied by a **plugin arg** (warlock-option takes `invocations`/`boons`).
- The (dead) `eldritch-invocation-option` macro (`options.cljc:3160`) shows each option = `prereqs` +
  `modifiers` + a typed trait (action/bonus-action/reaction/dependent-trait).
- The (dead) `wonderous-invention-selection` (`ua_artificer.cljc:13`) is the same multiselect pool whose
  options grant **magic items** via `mod5e/magic-item` — i.e. an infusion pool **reuses the magic-item
  homebrew system**.
So "Infusions" = a multiselect pool of item/effect-granting options + a scaling count + replace-on-rest.
The substrate (multiselect pool, plugin-fed options, magic-item grant) exists; it is **not yet a
first-class, named, author-declarable pool** (the open lever — see the content-extensibility track and the
D29 grant question).

## Feature-to-mechanism map (what's data entry vs a real gap)
Data entry into existing builders: chassis/profs/ASI/saves/equipment; text traits (Magical Tinkering, Right
Tool, Magic Item Adept/Savant/Master, Soul of Artifice); Tool Expertise (`?tool-expertise` /
`mod5e/tool-expertise`, used in `ua_artificer.cljc:142`); subclass features (non-companion).

- **GAP — spell-slot progression.** Artificer half-casts from level 1; `:level-factor` can't express it
  (overloaded — see `spell-slot-progression.md` / D27). Needs the bucket-of-tables + declared multiclass
  rule.
- **GAP — Infusions as a first-class scaling/swappable pool** (the keystone tool; the pattern exists, the
  authoring affordance doesn't).
- **GAP — ability-derived frequency** (Flash of Genius = Int-mod uses/long-rest). The catalogue's
  non-level use-count source (B3); without it the feature degrades to text.
- **GAP — companion link** (Battle Smith's Steel Defender, Artillerist's cannon). The monster builder
  exists; a feature→companion **link** does not. The only Artificer piece with no existing analog.

## Validation rule (D28)
Expressiveness is proven with a **synthetic stand-in** class (a made-up "Tinkerer": a scaling item-granting
pool + a half-from-1 slot table + an Int-mod reaction), **never** the real Artificer as a fixture — a
copyrighted fixture in the repo would itself be shipping the content. (Built-in PHB classes are SRD and fine
as fixtures; Artificer is not.)
