# Handoff: building the Weapons table redesign

For the agent building `feature/weapons-table`. Everything decided in the design chat (2026-10-15 to
2026-10-17) is here or linked from here. Read it all before writing code; ask the owner about
anything it does not settle.

## Where things are

| What | Where |
|---|---|
| Branch | `feature/weapons-table`, cut from `integration` at `427d16aa` (2026-10-17), pushed to origin. The owner approved this name; make no other branch |
| Worktree | `/home/codeglaze/projects/orcpub-weapons` (agent tooling armed by `agent-setup.sh`) |
| Design package (v5) | `/home/codeglaze/projects/weapons-design-v5/handoff_weapons_v5/` (also the zip beside it). Kept outside every repo on purpose: design-handoff folders are gitignored after they polluted a fork once |
| Spec | [weapons-table-spec.md](weapons-table-spec.md) |
| Earlier design rounds | [weapons-extra-damage-handoff.md](weapons-extra-damage-handoff.md), [weapons-extra-damage-design.md](weapons-extra-damage-design.md): history; v5 supersedes them |
| Base weapon fields and their traps | [weapon-data-model.md](weapon-data-model.md) (flags absent rather than false; `::thrown`, `::versatile`, `::reach` have no `?`) |
| Screenshot script | `scripts/design/weapons-table-shots.js` on this branch (agents/develop); run from a code worktree with `SHOTS=<dir> ./scripts/e2e/run.sh <path>` |

**Precedence when files disagree:** `Weapons Current Design.dc.html` wins, then `Weapons Handoff.md`
(both in the v5 package), then the spec. Start with `Weapons Handoff.md` and `Weapons Decisions.dc.html`
(D1-D10, one picture per decision). Do not build from `archive/`. Sample data in the drawings
(bonuses, names) is not a rule. The `.dc.html` files are design canvases: render them to PNG with
Playwright to read them (they are unreadable on a phone, and the owner reviews on a phone).

## Decisions the v5 handoff does not carry

- **Weapon Mastery is built now, inactive** (owner, 2026-10-17). The band, the opened-row card and the
  Graze / Vex / Cleave buttons are built; they show only when the weapon has a mastery and the
  character is trained in it. Nothing on `integration` supplies either, so they never show there.
  The 2024 data (which weapon has which mastery, who is trained) arrives later from the 5.5 line
  (`srd52/develop`). Test them with a made-up weapon and character, and prove the "only when
  trained" gate fails when broken.
- **Account settings** (owner, 2026-10-17): a change applies instantly and is saved in the browser,
  so it survives until site data is wiped. Signed in, it also saves to the account in the
  background. On login the account's settings replace the browser's, except a first login on an
  account with no saved settings, which uploads the browser's. Store one EDN map in one new field
  (`:orcpub.user/display-settings`), values only, never CSS; the server keeps known keys and allowed
  values, drops the rest, caps the size. Fold `:orcpub.user/dark-button-text?` into the map and keep
  reading the old field for accounts that set it. Follow its existing path (below).
- **Two good options become a preference:** a default plus an account setting, not one forced winner.
  Contrast numbers are reported as facts, not used as a veto.
- **The shield rules are in** (owner, 2026-10-17: "the shield rules are great"): spec rules 14-17, which
  v5 neither draws nor drops. With a shield or second weapon in the off hand the 1H | 2H switch is greyed
  and the opened row says why; two-handed weapons keep rolling but their tiles become amber outlines, the
  opened row says "Needs both hands" and the result adds "(needs both hands)"; one gold "Shield in off
  hand" pill by the heading; the switch pulses (10s, one minute, none under reduced motion) while the off
  hand is free. Grip comes from what is equipped (main/off-hand slots, the equipped shield), never a setting.
- **The PDF's extras are not decided:** a trial (extras as "<weapon>: <extras>" lines after the attacks
  text) pushed two lines onto the server's overflow page, a whole page for two lines. The attacks box
  holds about 8 lines and versatile weapons already spend two each. Owner shown the PDF 2026-10-17.
  Owner: names must not repeat ("wasting a lot of precious room repeating the names"); proposed format
  is the name once, then indented bullets per variant ("• versatile 1d10+2", "• +2d6 fire while Ablaze").
  Trials (patches, not committed): inline after a semicolon fits but shrinks the box text; name plus
  bullets reads best but a rule that sends every weapon with a variant to the text leaves boxes empty
  (owner caught it). Next proposal: boxes take the first three weapons as today, their variants listed
  under the boxes, later weapons as name plus bullets. Render it before building.
- **PDF bullets printed as "?"** (fixed, `5215f590`): `pdf.clj` `normalize-text` replaced everything
  above U+00FF, including U+2022, which Helvetica's WinAnsi encoding has. SRD item text with bullets
  (Gem of Brightness) printed "?" on magic item cards. U+2192 (arrow) really is missing from the font.
- **The overflow check spills early** (not yet investigated): `spill-overflow!` (`pdf.clj` about 1941,
  `max-lines`) sent a line to the overflow page while the Attacks box visibly had room for about three
  more. To trace and fix with a failing test first.
- **Rendering a PDF to PNG here:** no poppler; use the app's PDFBox (`PDFRenderer`) with `lein classpath`.
- **The settings panel is a second PR from the same branch,** after the table ships with defaults.
- **Crit rules are a setting** (owner, 2026-10-16, from
  [weapons-extra-damage-design.md](weapons-extra-damage-design.md)): tables play crits differently, so the
  rule is an account default, overridable per character, and the rule in use is named wherever crit
  damage is shown (the + menu's Critical damage row, the crit result). Every extra part, Vicious
  included, follows the rule set. v5's "all dice doubled" is the default rule, not the only one. Which
  rules to offer is not drawn: ask.
- **The scope is intentional** (owner, 2026-10-16): charges, once-per-rest, on-hit effects and the
  stress-test rows exist so homebrew and non-SRD content fit without a redesign. Build the data model
  for all of them even where the SRD has no example.
- **The PDF keeps working.** `pdf_spec.cljc` prints each weapon's damage; the new damage-parts model
  must not break it. How extra parts and the no-roll case print is not drawn: ask before changing the
  PDF's output. Two existing bugs were noted against this area and not yet investigated: the PDF's
  two-handed (versatile) damage line, and today's table overflowing a phone screen. Check both against
  `integration`; the overflow should vanish with the new table, the PDF one may not.

## Build order

1. **The table:** a damage-parts data model with the SRD magic weapons filled in (below); rows,
   tiles, chips, the lines under the row, the inactive mastery band, the + menu with its keyboard
   path, the no-prof and no-roll tiles, desktop with the Details column, Garden styles from the
   palette as CSS variables (so the settings panel only changes variables).
2. **Rolling:** the mobile bottom sheet, the damage result (one line per part, then the total), the
   attack chain, the desktop result card replacing the `.message` bar; the mastery sheet buttons.
3. **The advantage switch** in the Weapons header; ctrl/shift-click stays.
4. **The settings panel** (second PR).

### Progress

- **Stage 1 step 1, the damage model: done, reshaped 2026-10-17** with the owner into flat fields
  (`::mi/extra-damage`, `::mi/effects`, `::mi/toggles`, `::mi/crit-from`, `::mi/charges`) and a dawn
  reset that a long rest also clears. The first draft (`db9e6115`, `damage_parts.cljc`, `::dp/when`) is
  replaced: its mixed-type `when` could not be stored for custom items. Fields, reasons, traps:
  [weapon-damage-model.md](weapon-damage-model.md). Human doc: `docs/MAGIC-WEAPON-DAMAGE.md` on the
  branch. Owner direction for later steps: one shared formatter in `display.cljc` (the pattern
  `attack-description` already follows for feature attacks) feeds both the table and the PDF.
- **2026-10-17, later:** the PDF's two-handed line and Sun Blade's type are fixed (owner: fix bugs as we
  go). All 16 SRD weapons with extra damage are filled in, adding `not-against`, `attack-type` and a flat
  `damage-bonus`. Sun Blade stays on every sword. **Crit rules: per account and per character**, at
  least two common variants (owner); which ones is still to agree.
- **The shared formatter: done** (2026-10-17): `display.cljc` writes every weapon's damage, extras and
  saves as text (`weapon-extras-str` gives one line per weapon). Not called yet; the rows and the PDF
  will call it. Where the PDF prints extras is still the owner's call.
- **Stage 1 step 2, the rows: built 2026-10-17** on `feature/weapons-table`. `weapons-section-2` is now
  `weapon-row`s in a CSS grid (`minmax(0,1fr) 62px 106px`), styles in `src/clj/orcpub/styles/weapons.clj`
  (every colour a CSS variable on `.weapons-section`, light theme overrides them), row logic in
  `display.cljc` `weapon-row-lines`. Toggles follow v5 section 10 (neutral grey; a damage toggle takes its
  type's ribbon), not the blue drawn in section 1. Browser test: `test/browser/weapons_table_e2e.js`
  (27 checks, dark and light). Screenshots: `scripts/design/weapons-rows-shots.js`. Not yet: the + menu,
  mastery band, desktop Details column, shield states (owner question open), charge counters.
- **Today's table overflowing a phone:** measured 2026-10-17. On a real phone (touch, phone user agent)
  it fits; in a narrow *desktop* window every button gains the ctrl/shift `.tooltip` wrapper and the
  table is 404px in a 370px section. The new rows fit both. A screenshot taken with a desktop user agent
  at 390px is not a phone: set `isMobile`, `hasTouch` and a phone user agent.
- **The Net shows "Special"** in its no-roll tile: it has no `:summary` until PR #62 lands ("restrains").
- **Port 8890 is shared** by every worktree's `run.sh`; another session's server answering there makes
  `server.sh` refuse to start. Set `E2E_PORT` to a free port.
- **Found while building, not fixed:**
  - Vicious Weapon's SRD trigger is "roll a 20": modelled as `::mi/min-roll 20`, apart from
    `::mi/on :crit`, which follows the crit range.

**Not drawn, so ask:** how the result card relates to a roll log; resistance per damage part; charge
recharge and the zero-charges state; whether the app knows the attacks-per-turn count (fallback: the
chain counter only counts up). Armor in the same style is not in scope.

## Today's code (measured 2026-10-17 on `427d16aa`)

- **The table** is `weapons-section-2` in `src/cljs/orcpub/dnd/e5/views.cljs` (about line 3808),
  mounted at about 4264 and 4349. A local `r/atom` keyed by weapon key tracks opened rows; clicking a
  `tr.weapon.pointer` row toggles it. Columns today: Name, Proficient? (desktop only), Details,
  Atk/Attack, Dmg/Damage, caret. Rows with `::weapon/type :ammunition` are skipped. Versatile gets a
  second damage button ("v 1d10+2"). `weapon-details` (about 3645) is the opened row.
- **Phone or desktop is decided in ClojureScript,** not CSS: `@(subscribe [:device-type])` (subs.cljs
  about 859) combines the user agent and `:narrow-screen?` (`(max-width: 767px)`, user_agent.cljs).
- **Subscriptions it reads:** `::char/all-weapons`, `::char/weapon-profs`,
  `::char/best-weapon-attack-modifier-fn`, `::char/best-weapon-damage-modifier-fn`,
  `::char/has-weapon-prof`, `:device-type`, `::mi/all-weapons-map`.
- **Rolls:** `roll-button` (views.cljs about 2828) stops propagation so a roll never toggles the row.
  `button-roll-fn` (about 2818): ctrl/meta-click dispatches `[:show-message-2 "... w/ Advantage: r1 | r2"]`,
  shift-click the same with Disadvantage. **It shows both rolls and picks neither;** the new result
  picks one and strikes out the other. The `.tooltiptext` hint is hidden on mobile user agents.
- **The result bar:** `:show-message-2` (events.cljs about 3643) never auto-closes. It renders in the
  `header` component through `notifications/message` (`views/notifications.cljs` about 49); a click
  or a route change hides it.
- **Magic weapons:** `::mi/magical-attack-bonus`, `::mi/magical-damage-bonus`; Sun Blade alone has
  `::mi/magical-damage-type :radiant`. **There is no structured extra damage anywhere:** Flame
  Tongue's +2d6 fire, Frost Brand's +1d6 cold, Sun Blade's +1d8 vs undead live only in description
  prose (`magic_items.cljc`). The data model is new work.
- **The Net** has no damage keys; `integration`'s table shows "d" plus a modifier for it. PR #62
  (`fix/srd-2014-data-defects`, unmerged) adds `:summary "restrains"` and the PDF's "None
  (restrains)". The new table shows the no-roll tile for any weapon without dice, so it is right
  either way.
- **`display.cljc` `attack-description`** reads an unqualified `damage-type`, so a weapon's
  `::weapon/damage-type` is never printed (#62 fixes this too).
- **Styles** (`src/clj/orcpub/styles/core.clj`): `.roll-button` (about 2177, amber `#f1a20f -> #dbab50`,
  white, 16px 700), `table.striped` (about 1973; light theme about 2601), `.message` (about 1162),
  `.app.dark-button-text:not(.light-theme)` (about 1808). The root `div.app` carries the theme class
  and `dark-button-text` (views.cljs about 2163).
- **The dark-text setting's path, to copy for the settings map:** schema `schema.clj` about 129; client
  `[:user-data :dark-button-text?]`; `:toggle-dark-button-text` (events.cljs about 2246) flips the db
  and PUTs when signed in; `update-user-preferences` (routes.clj about 755); `user-body` (about 359)
  returns it at login; `:login-success` (events.cljs about 3112) lets the account value win. Test:
  `test/clj/orcpub/routes_test.clj` about 363.
- **Tests guarding the table today:** only `test/browser/srd_equipment_e2e.js` (opens the Longsword row,
  reads price and weight). Nothing tests `weapons-section-2`, `attack-description`, the modifier fns or
  `:show-message-2`. Every new test is shown to fail first.

## Rules data, checked against SRD 5.2

**Weapon Mastery** (open5e `srd-2024/WeaponProperty.json`, the SRD's own text): Topple (on hit, Con
save DC 8 + the attack's ability modifier + proficiency bonus, prone); Push (on hit, Large or smaller,
10 ft); Slow (on hit, needs damage dealt, -10 ft speed, does not stack); Sap (on hit, no save, the
target has disadvantage on its next attack roll); Vex (on hit with damage dealt, advantage on your next
attack against that creature before the end of your next turn); Cleave (on hit, once per turn, a melee
attack roll against a second creature within 5 ft of the first and within your reach, damage without
the ability modifier unless it is negative); Graze (on miss, damage equal to the attack's ability
modifier, the weapon's type); Nick (the Light extra attack becomes part of the Attack action, once per
turn). Assignments checked: Greataxe Cleave, Greatsword Graze, Maul Topple, Scimitar Nick, Shortsword
Vex, **Warhammer Push** (v4 had it as Topple).

**SRD 5.1 extra damage** to put in the data model: Frost Brand +1d6 cold (always); Flame Tongue +2d6
fire (while ablaze, a switch); Sun Blade radiant instead of slashing, +1d8 radiant vs undead; Holy
Avenger +2d10 radiant vs fiends and undead; Mace of Disruption +2d6 radiant vs fiends and undead;
Giant Slayer +2d6 of the weapon's type vs giants; Dragon Slayer +3d6 of the weapon's type vs dragons;
Vicious Weapon +2d6 of the weapon's type on a critical hit. Read each from the SRD text before writing
it; SRD wording is never paraphrased into data.

## The v5 palette, verified

Measured 2026-10-17 with WCAG contrast and OKLab distance: white on every ribbon 5.86-10.88:1; every
dot against both strip colours (`#1a1e28`, `#363636`) at least 3.97:1 (slashing on `#363636` is the
lowest); no two types closer than 0.06, none within 0.07 of a reserved colour. The handoff's "all 13
pass" is true.

## How the owner works with you

- **Stop rules first,** in every working reply (AGENTS.md). One attempt per risky step.
- **Visual review is on a phone:** true-size 390px screenshots, dark and light, today's table beside
  the new one, sent as image files after each step. Never a design canvas or a link to one.
- **Real app styles only:** the app's amber `.roll-button` with white text, `table.striped`, its spacing.
  No invented colours, no dark text on amber by default, no flat buttons, no dropped headings. One px
  grid for headings and rows, measured. The spec's "Rejected" list is binding.
- **Before any image goes out:** measure (fits 390px, headings within 2px of their buttons, nothing
  clipped), check it against the handoff, look at it yourself.
- **Tests prove their fail state** before commit; put the failing output in the PR.
- **Commits** as codeGlaze, signed, no agent trailers; `lein lint` on changed files, `lein test`,
  `lein fig:build` after CLJS changes. Push this branch routinely; PRs into `integration` need the
  owner's OK.
- **Docs:** findings go to agents/develop `docs/kb/`, indexed in its README; the branch carries code
  and tests only. Update this page as stages land.
