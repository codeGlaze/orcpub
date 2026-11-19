# Other game systems: Nimble, Daggerheart, and a shared base

*Researched 2026-10-19. Code references are against `agents/develop` at `94e5abcf`. Licence and
competitor facts were read from the publishers' own pages on that date; they move, so re-read before
acting on them. Nothing here is built. The plan sections are a PROPOSAL, not a decision.*

**The question (owner):** can the app grow beyond D&D 5e/5.5e, Nimble and Daggerheart being the
candidates, and what would "truly common" functions need, so each product is built on a shared base
rather than a fork?

**Short answer:** yes, Nimble first. Its licence plainly covers a free web tool, and its rules
are close enough to 5e to reuse our machinery. Daggerheart has a real SRD, but its licence does not
name web apps, and its rules share only our generic engine. Most of the work is not per-system: it
is separating the app shell from 5e. **That split exists only for other games.** 2014/2024 does not
need it: a 2014 or 2024 character is one D&D 5e character in one app (see Corrections).

## Licensing — the constraint you cannot see from the code

| | Nimble | Daggerheart |
|---|---|---|
| Reference document | **No formal SRD.** The *3rd Party Creator License* v2.0 (2025-12-09) covers all published Nimble text | **SRD 2.0** (2026-08-25), under the Darrington Press Community Gaming License (**DPCGL**) 2.0 (2026-08-25) |
| Web apps and tools | **Named and allowed:** "For free online tools/wikis/apps/VTT implementations, you may use any and all published Nimble text, provided it is completely free to use (optional donations to defray development/hosting costs permitted)" and their notice is shown "prominently (banner, sidebar, welcome message, etc.)" | **Not named.** The licence speaks of "Permitted Formats" without defining them on its page; VTTs were added in 2025 (allowlist: Roll20, Demiplane, Foundry, Alchemy, Fantasy Grounds). Character builders and apps are not mentioned |
| Main risk | A paywall or premium tier leaves the free-tool clause. Ads are not addressed | That a web builder is not a permitted format. In practice several run openly under the DPCGL (Heart of Daggers, HeartSmith, daggerheartsrd.com) — tolerated, not confirmed |
| Before building | Check our donation model fits "completely free"; show the notice | **Email Darrington Press** and get it in writing |

Both licences:
- forbid their logos and art
- forbid looking official or endorsed

Two points for the code:
- **Licensed text stays in its own data files, under its own notice.** Our code is EPL-2.0
  (`LICENSE`). The DPCGL says SRD-derived content remains Darrington's, so it must not be presented
  as EPL. This is the same separation 5e SRD text already has (`resources/public/srd/2014/`).
- **Nimble's notice is a UI element, not a footer credit.** It has to sit somewhere visible on the
  Nimble pages.

Sources:
- [Nimble creators page](https://nimblerpg.com/pages/creators)
- [Nimble licence update post](https://nimblerpg.com/blogs/news/nimble-3rd-party-creator-license-update)
- [Daggerheart SRD](https://www.daggerheart.com/srd/)
- [DPCGL](https://darringtonpress.com/license/)
- [Daggerheart FAQ](https://www.daggerheart.com/faq/)

## How well the rules fit our engine

*Rules summaries are from general knowledge, not checked against either rulebook.*

- **Nimble** is a 5e derivative:
  - four stats
  - attacks hit unless you roll a natural 1
  - armor reduces damage rather than setting a target number
  - three actions a turn, and wounds

  Class / ancestry / feature choices map onto `option-cfg` / `selection-cfg`. Derived values
  differ, so it needs its own `character.cljc` equivalent, which can start as a copy of e5's.
- **Daggerheart** shares the shape but not the maths:
  - classes, domains (domain cards), ancestries, communities
  - the Duality roll (2d12, Hope/Fear)
  - damage thresholds instead of HP-by-number
  - no 5e ability modifiers

  The generic choice-and-build engine fits. Every 5e rules layer above it does not.

## What is already shared (verified)

**The generic core is about 1.2k lines with no e5 requires** (checked by reading the `ns` forms):

- **`entity.cljc`, `entity_spec.cljc`, `modifiers.cljc`, `template.cljc` (in `src/cljc/orcpub/`).**
  The model is "a template of choices plus a base, built into deferred values"
  ([content-to-character-pipeline.md](content-to-character-pipeline.md)), and it contains no 5e.
  One leak: `selection-cfg` defaults `::source` to `:phb` (`template.cljc:41`).
- **`dice.cljc`.** It rolls `NdS±mod` for `dice-sides [4 6 8 10 12 20 100]` (`dice.cljc:5`). That
  is enough for Nimble; Daggerheart's Duality roll is new code.
- **Infrastructure:** auth, email, config, security, the Datomic wrapper, and `tools/orcbrew.clj`.

**A game tag on saved characters already exists:**

- **Schema:** `::se/game` and `::se/game-version` (`db/schema.clj:209-212`).
- **Writer:** `add-dnd-5e-character-tags` stamps `:dnd` / `:e5` on every save (`routes.clj:1344`).
- **Disabled check:** the validation that would enforce the tag is switched off with `#_`:

  ```clojure
  problems [] #_(dnd-e5-char-type-problems …)   ; routes.clj:1354, :1598, :1611
  ```

  Dispatch by system therefore does not need designing. The check needs turning back on and using.

## What is hard-wired to 5e (verified)

| where | what | why it matters |
|---|---|---|
| `src/cljs/orcpub/dnd/e5/views.cljs`, `web/cljs/orcpub/core.cljs` | The app shell lives in e5: nav, character list, parties, My Content, splash. `core.cljs` binds `dnd-e5-*` routes | A second system has no shell to plug into |
| `route_map.cljc:157-158` | Every route sits under `"dnd/" {"5e/" …}`. Default route is the e5 builder (`db.cljs:80`) | URLs need a `/:game/:version/` level |
| `src/clj/orcpub/routes.clj` | Auth and user handlers share a file with character and item endpoints | Splitting auth out is the server half of the shell split |
| `db.cljs:35-46` | localStorage keys carry no prefix (`"character"`, `"spell"`, `"boon"`…) | A second system collides. This is also the root of the two-tab contamination ([multi-tab-character-contamination.md](multi-tab-character-contamination.md)), so one storage reshape can fix both |
| `src/cljs/orcpub/dnd/e5.cljc:14` | The plugin spec accepts only keywords in namespace `"orcpub.dnd.e5"` | Homebrew for another system is rejected |
| `db/schema.clj` | Character attributes are `:orcpub.dnd.e5.character/*`; parties and folders are filed under e5 | Collides with nothing: a new system adds its own namespace. Parties and folders should move to shared |
| `pdf.clj`, `pdf_spec.cljc` | Server-side PDFBox filling fixed 5e AcroForm sheets | Each system needs its own sheet. Start new ones as HTML/print, which also fits the pure-SPA goal ([srd-2024-roadmap.md](srd-2024-roadmap.md)) |

**Inside `dnd/e5/` (about 60k lines of cljc), roughly:**
- **~15% machinery:** `content_types.cljc`, `grant_pools.cljc`, `selections`, `requirements`,
  `orcbrew_format.cljc`, `share_bundle.cljc`.
- **~20% engine shape filled with 5e rules:** `options.cljc`, `character.cljc`, e5 `modifiers.cljc`.
- **~65% pure data.**

These are whole-file line counts. They were not checked with `clj-grep.py`, so discarded forms
are counted too.

## Competitor: Heart of Daggers (Daggerheart)

[heartofdaggers.com](https://heartofdaggers.com) is the most complete Daggerheart site found. What
it has:
- a marketplace (85% creator share)
- a character creator and a Homebrew Vault
- encounter and campaign tools, a GM screen, a rules compendium, forums

It is free with Ko-fi donations, uses SRD 1.0 under the DPCGL, and runs on WordPress
(`wp-login.php`) behind CloudFront.

Its character creator, as seen logged out on 2026-10-19:

- **Account required to try it:** "Sign in to create Daggerheart Characters!" Nothing can be built
  first.
- **No export or import found:** no JSON, PDF, print, share link or VTT button, no `wp-json`, no open
  source. A Homebrew Vault "one-click export to VTTs" was announced as coming; it was not found shipped.
- **Homebrew is site-internal:**
  - custom items on a sheet, kept per character or saved to the Vault
  - custom trackers that reset on rests, for homebrew classes, subclasses, ancestries and
    communities
- **Ahead of us on:**
  - a guided level-up (experience, traits, then abilities, with the full card shown before a pick)
  - rest-reset trackers, which are worth having in the shared layer, since 5e and Nimble need them
    too

Not checked: anything inside the creator once logged in, and whether characters get a public URL.

**What it means:**
- **No data partnership yet.** There is nothing to import until they ship an export. Re-check if
  the VTT export lands.
- **Our differentiators are real:**
  - try without an account (below)
  - portable files (`.orcbrew`, share bundles)
  - homebrew on the same build path as SRD content ([homebrew-content-merge.md](homebrew-content-merge.md))
  - static data with offline as the goal

## Product principle: try before you sign up

**Owner, 2026-10-19:** forcing an account just to try a site is not friendly. This is where Heart of
Daggers loses us.

The app already works this way, verified:
- **The builder needs no login.** Its route is served as a plain page (`routes.clj:1810`), and the
  draft lives in localStorage (`db.cljs:35`).
- **Only server-side features check auth.** The saved-characters list, user and items routes sit
  behind `check-auth` (`routes.clj:1925-1945`).

**Any new system keeps this:** build, roll and print with no account; an account adds sync, sharing
and saved lists.

## Proposed shared-base plan (PROPOSAL)

Ordered. **Which steps pay off without a second system:**

| step | pays off with no second system? |
|---|---|
| 1, 2, 5 | **No.** Only another game needs them |
| 3 | **Yes:** keying drafts by character id fixes the two-tab bug |
| 4 | **Yes:** the edition-neutral helpers serve 5e |

So 3 and 4 can be done on their own merits; 1, 2 and 5 wait on a decision to build another game.

1. **Split the shell from the system.**
   - Move nav, character list, parties, folders, My Content and auth into an `orcpub.app.*` layer.
   - Each system registers its builder, sheet, routes and content types with it.
   - `content_types.cljc` already does this registration inside e5.
2. **Make the game tag drive dispatch.**
   - Re-enable the `entity-type-problems` check.
   - Route saved characters, the plugin format and URLs (`/:game/:version/…`) by `game` /
     `game-version`.
   - **This is not the 2014/2024 mechanism.** Editions are a version filter on pick lists inside
     one game ([srd-2024-roadmap.md](srd-2024-roadmap.md)). `game-version` stays `:e5` for both.
3. **Namespace the localStorage keys and widen the plugin spec per system.** Do it together with
   the multi-tab fix (key drafts by character id).
4. **Lift the edition-neutral helpers out of e5:**
   - grant pools, selections, requirements, `picks.cljc`
   - the homebrew registrar HOF ([content-extensibility-direction.md](content-extensibility-direction.md))
   - rest-reset resource trackers

   5e vocabulary (abilities, slots, AC) stays.
5. **Sheets per system as a plug-in.** 5e keeps its PDF; new systems start with HTML/print.
6. **A thin Nimble proof of concept before generalising further.** Apply the KB's own rule: an
   abstraction must be thicker than what it hides. The second system is the test of which shared
   layers are real.

**Order of systems:** Nimble first. Daggerheart after Darrington Press answers in writing. It would
then exercise only the generic engine, which makes it a good second test of the shared base.

## Open decisions (owner)

- Whether to pursue Nimble at all, and whether our donation model meets "completely free".
- Whether to email Darrington Press now, ahead of any Daggerheart work.
- Where the shell split lives in the branch tree (`refactor/` leaf vs its own track).

## Corrections

**2026-10-19: the shell split is not something the 5.5e work needs.** The first version of this
doc made three claims that are wrong:
- separating the app shell from 5e is something "the 5.5e work needs anyway"
- 2014 vs 2024 is "the same mechanism" as dispatching saved characters by `game` / `game-version`
- every shared-base step "pays off even if a second system never ships"

The owner's recorded decisions in [srd-2024-roadmap.md](srd-2024-roadmap.md) (its decision table)
say otherwise:
- **2024 is data only.** It lives in `orcpub.dnd.e55.*` with "no templates, no views, no edition
  dispatch".
- **Two dials, not one edition switch:** content and rules, because "users want to mix".
- **The content dial is a version filter.** It removes entries from pick lists like switching off a
  source. Untagged content is shared, and only the 2024 side is tagged.

So a 2014 or 2024 character is one D&D 5e character in one app. Only a different game (Nimble,
Daggerheart) needs the shell split or game dispatch.

**2024 does bring its own mechanics, and none of them needs dispatch.** They arrive in two ways:

- **With the content.** A 2024 feat, species or feature carries its grants and is compiled when it
  is picked, like any option. A filtered-out entry brings nothing. Data-only entries have their
  mechanics joined by `:key` from code, the same split magic items use.
- **The rules dial.** These are rules where the engine computes differently: 2024 Exhaustion is a
  formula (minus 2 per level on D20 Tests, minus 5 ft speed per level) where 2014 is a six-row
  table. Grappled gains mechanics. That is a per-rule switch inside one engine, not a different
  app.

The list of rules that changed is not compiled yet: 71 prose records need a person to read them
([srd-2024-implementation-notes.md](srd-2024-implementation-notes.md)). How the dial is stored is
not designed yet.

Caught by the agent orchestrating the 2014/2024 work. The cause: inferring from a schema field
named `game-version` that editions would use it, without reading the edition decisions first.
