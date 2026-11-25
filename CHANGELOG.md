# Changelog

All notable changes are documented here, newest release first. Format: [Keep a Changelog](https://keepachangelog.com).
House style and the branch → release fold: [`docs/branch-changelog.template.md`](docs/branch-changelog.template.md).

<!-- Editing this file: say what a change DID, once, in a line: not the commits it took. Sections:
     ### Added / ### Changed / ### Fixed / ### Security; each line opens with its area in bold
     (**Homebrew:**, **Printing:** ...). Fixed is for bugs that existed before, not fixes to work in the
     same release. No internal work (tests, CI, refactors). Cite the issue (#N) when there is one. -->

## [Long Rest Update] — 2026

### Highlights

Homebrew is repaired instead of lost, a character that used to blank the page now recovers, and characters can be shared with the homebrew they're built with and given a portrait from an illustrator's layered art; homebrew races, backgrounds, feats and subclasses can give ability score increases the player places. A new Rules tab holds the SRD's rules and conditions, with links that preview a spell, condition or rule as you read, and on the sheet a Weapons table rolls attacks and damage while charged items and a Rest box track charges and rests. Printing gains a spellbook, A4 paper and a full PDF Options panel, and accounts get new sign-in pages and stronger password rules.

### Added
- **Characters:** a new Weapons table: attack and damage rolls in one place, a sheet that chains a turn's attacks, extra damage you add as it applies, and main-hand and off-hand marks. (#722)
- **Characters:** roll attacks with advantage or disadvantage on any device: a switch in the Weapons heading, or a long press on an attack tile for one roll. (#722)
- **Characters:** every SRD magic weapon's extra damage and once-per-dawn powers, on the sheet and in the PDF. (#722)
- **Characters:** charges for the SRD's charged items, shown as pips to tap; staffs spend them on their powers. (#722)
- **Characters:** a Rest box on the Features tab, and a floating Rest button on the other tabs: short and long rests, dawn and dusk, recharging items by their roll or yours, with Undo. (#722)
- **Characters:** cast spells from charged items, priced in charges per level, from the item or from the Spells tab, and roll the spell's damage at the level cast. (#722)
- **Characters:** a portrait builder: pick or randomize layered art for hair, head, ears, eyes, nose, mouth and shirt, from the summary thumbnail, beside the Image URL field, or in a Portrait tab in the builder; Randomize can keep your colours. (#714)
- **Characters:** portrait colours and effects: a colour per piece with lighter or darker shades, hair ombré, streaks, shine and dyed bangs, slit or goat pupils and glowing eyes, blush and freckles, and mood light from a torch, moonlight or magic. (#714)
- **Characters:** the portrait's artist is credited under it, on the sheet, in shared link previews and in the picture itself, with a link to the artist's page. (#714)
- **Characters:** a character can use an image from the web as well as a built portrait; both are kept, and the picture editor switches which one shows on the sheet, the PDF and shares. (#714)
- **Characters:** unsaved changes left in a closed tab are offered once when you open that character again, with Undo; older ones wait in an "Unsaved drafts" list in the builder and on the character list for 30 days (a character never saved to an account is kept). A character open in another tab waits for "Edit here". (#704)
- **Characters:** lowering a level or changing a class, subclass or race keeps the choices that depend on it while you edit; change your mind and they come back by themselves, with Undo. (#718)
- **Characters:** the builder shows choices waiting on a higher level beside the level picker, and says when each comes back. (#718)
- **Characters:** with more than one class, a "1st" badge marks the starting class; tap another class's badge to make it the starting class, and choices that only apply to the starting class follow. (#718)
- **Characters:** a Choices list in the builder shows every choice a character stores, including ones whose homebrew isn't loaded in this browser, and lets those be cleared. (#718)
- **Characters:** a character that will not load opens a recovery panel instead of a blank page, and can be reported in one click. (#679, #685)
- **Characters:** the equipment lists filter as you type, with no limit on length. (#707)
- **Characters:** character pictures load from far more sites, with paste and upload when a site refuses. (#707)
- **Characters:** the character builder's Back and Next repeat at the bottom of each page, and open the next page at its top. (#728)
- **Builder:** Add a kit opens the item search on the equipment packs.
- **Builder:** a Developer mode switch hides the empty "select any number / complete" pick counts.
- **Character sheet:** kits on the Equipment tab, and a Manage inventory switch that adds items, changes quantities and coins on the sheet, and turns itself off when done. (#229)
- **Character sheet:** a backpack Worn | Off switch; set down, the backpack's contents show as not carried, still counted.
- **Character sheet:** the inventory list's search has a Packs filter that shows only what your kits hold.
- **Inventory:** equipment packs become kits: the pack's items are ordinary rows, two of the same pack make one kit with a count, and the kit keeps count of what runs out (torches, rations) with Restock, Re-bundle and Undo. Each pack has its own drawn icon, in a grid of framed cards. (#111)
- **Equipment:** weapons, armor, tools, musical instruments, gaming sets and equipment packs show their SRD price and weight. (#715)
- **Homebrew:** races, subraces, backgrounds and feats can give ability score increases the player places, such as +2 to one ability and +1 to another, or +1 to any martial ability; subclasses can too, in an optional section. (#717, #128)
- **Homebrew:** homebrew can give saving throw proficiencies, fixed or chosen, on their own or with an ability score increase; a file that uses these is refused by an older version of the site instead of loading wrong. (#717, #190)
- **Homebrew:** move or copy content between sources, one item or many at a time. (#705)
- **Homebrew:** turn all homebrew, a source or one section off without deleting it, and search inside a source. (#475)
- **Homebrew:** a library health card shows what needs fixing; one damaged entry is set aside with a repair panel instead of breaking its whole source. (#683)
- **Homebrew:** imports settle duplicate keys with safe defaults in one click, with a review panel for the detail. (#682)
- **Homebrew:** content missing required fields can be saved with placeholder names, and Fix & Restore names entries for you. (#690, #691)
- **Homebrew:** an option to show each class's homebrew source beside its name. (#671)
- **Homebrew:** export runs the same checks as import, so a library exported and imported again comes back the same. (#723)
- **Homebrew:** builder safety nets: a draft export, the work in progress restored after a refresh, and a dialog that fills in missing fields on export.
- **Homebrew:** homebrew classes can define starting equipment, or start from an SRD class and keep only what differs. (#706)
- **Homebrew:** custom items can have charges, a recharge time and the spells they cast. (#722)
- **Homebrew:** custom weapons can have switches the player turns on (like Flame Tongue) and extra damage or effects that apply under conditions you set, such as on a hit, against a creature type, or limited per turn or rest. (#722)
- **Homebrew:** an "export as markdown" button in My Content writes homebrew classes and subclasses as printable 5e-style text, with a level table and their features.
- **Homebrew:** the add buttons on homebrew builders repeat below a long list of traits or options.
- **Sharing:** share a character by a short link that brings the homebrew and magic items its sheet uses; renew or stop a link at any time, and unused links expire. (#692, #693)
- **Sharing:** a shared character link previews with the character's own portrait, and the preview updates when the portrait changes. (#714)
- **Rules:** a Rules tab with the full SRD 5.1 rules reference, by section, the 15 conditions, and clarification notes in our own words linked to where Wizards published the ruling. (#715)
- **Rules:** rules text, spells, magic items and monster stat blocks link to the spells, conditions and rules they name, with a preview on hover, or on a first tap on a phone. (#715)
- **Orcacle:** finds rules and conditions by name. (#715)
- **Reference:** each row on the Spells and Monsters lists shows its source's short tag (the one set in My Content), so same-named spells from different packs can be told apart; built-in entries read "SRD".
- **Printing:** spellbook pages: every printed spell by class, with save DC and attack on each page, as a full-text book, a ledger or a prep sheet. (#169)
- **Printing:** multiclass casters can print one class per spell column, with Pact Magic kept as its own pool. (#710)
- **Printing:** spell rows mark concentration, casting time and costly materials. (#710)
- **Printing:** magic item cards, an optional card-back logo, and black-and-white cards for home printers. (#684)
- **Printing:** the portrait prints on the exported character sheet, at full resolution. (#714)
- **Printing:** A4 as well as US Letter, for the character sheet, spell cards, magic item cards and the spellbook. (#719)
- **Printing:** when part of an export cannot be produced, or it holds more spells or magic items than the limit allows, the PDF opens with a page naming what was left out and why. (#719)
- **Printing:** a busy server answers a PDF export with a page that retries by itself instead of timing out. (#710)
- **Printing:** PDF Options lets players choose what the file name shows (player, character, class, subclass, level), where the level goes and how it is written (5, 05, L5); kept on the account. (#726)
- **Accounts:** new sign-up, sign-in and account-recovery pages. (#708)
- **Accounts:** password rules based on length, a live strength meter, and screening against known-breached passwords. (#708)
- **Accounts:** "Sign out everywhere", and an email when a password or email address changes. (#708)
- **Accounts:** a preferred name, used to greet you in site emails instead of "Hi there". (#714)
- **Accounts:** artists get an account with a welcome email, and choose on My Account how they are credited and what their public page shows. (#714)
- **Accounts:** sign-up, password reset and the other account emails are kept and retried until the mail server takes them, so signing up works while the mail server is down. (#716)
- **Accounts:** an optional Cloudflare Turnstile check on sign-up, password reset and resend verification, and on sign-in after a failed attempt; when it can't load, the form says so and suggests allowing challenges.cloudflare.com in an ad blocker. Off unless `TURNSTILE_SITE_KEY` and `TURNSTILE_SECRET_KEY` are set. (#725)
- **Site:** a What's New panel shows each release's highlights once, and thanks the patrons who keep the server running. (#711, #729)
- **Site:** optional Support the Server and Fuel the Dev links on the home page, in the site footer and in What's New, each shown only when the site sets it up. (#729)
- **Site:** a public page for each portrait artist, with their name, how many pieces they drew, three small watermarked example portraits and the links they listed, and a page listing every artist. (#714)
- **Site:** a "Dark Button Text" option for the yellow buttons. (#711)
- **Site:** pages and portrait images ask AI crawlers not to train on them. (#714)
- **Site:** a portrait artist's Twitch button shows when they are live, once the site sets Twitch keys.
- **Server:** a startup report of the server's settings, naming what is missing and what sign-up will do, never printing secrets, and ending with the settings new in this release that are not set yet. It is saved to `startup-report.txt` beside the crash files, `./run` prints it last, and it warns when a Patreon link is set but no patrons are listed. (#712)
- **Server:** a startup email to the admin on each new release saying what needs fixing and which new settings are worth setting, with one reminder a week later while something still needs fixing. (#724)
- **Server:** `./run --upgrade` and `./run --check` say what the release changes for your install, with the full notes in `logs/upgrade-report.txt`; installs that do not use `./run` find the same in the startup report. (#724)
- **Server:** database backup, restore and verify from the command line (`./run --backup`, `--restore`, `--verify-backup`), while the site runs, on the built-in storage and on Postgres, keeping the newest three monthly backups. (#724)
- **Server:** after the app runs out of memory or crashes, it restarts clean and the admin is emailed what happened, with the evidence kept in `/var/log/orcpub` on the host. (#716)
- **Server:** admin alerts by email: one email per problem with a count of repeats, a limit on how many are sent an hour, and nothing lost or slowed down when the mail server or the database is down. (#716)
- **Server:** sign-ins, failed sign-ins, sign-ups, password resets, email changes, deleted accounts and refused logins are written to the server log, without passwords or email addresses. (#716)
- **Server:** an optional `REDIS_URL` shares the sign-in and email limits across app containers. (#725)

### Changed
- **Characters:** the Missing Content warning also covers spells and languages, and ignores the built-in "Custom" option. (#524)
- **Characters:** on phones every weapon picker opens in a bottom sheet. (#722)
- **Character sheet:** the spell table's Attack column is gone; on a spell that makes a spell attack, its Mod. is the roll button, so the table is narrower and long names wrap less on phones.
- **Homebrew:** opening the app, importing and restoring are faster with a large library. (#723)
- **Homebrew:** a value in the wrong form whose meaning is plain (`"25"` for a speed, one trait where a list belongs) is corrected once, and the import report lists each field it fixed and each entry it sets aside for repair. (#723)
- **Homebrew:** content imported without a source lands in the real "Default Option Source".
- **Homebrew:** saving checks every required field, dropdowns and multi-selects included.
- **Content:** SRD weapon and armor names in Title Case, without the Wikipedia links.
- **Printing:** PDF Options opens as a full-window panel with grouped options, and Create PDF opens a tab that says the PDF is being made. (#726)
- **Printing:** spell cards have a new design: the name with ATK and DC, concentration and ritual as ribbons, the casting facts labelled, and more of the spell's text on the front. (#726)
- **Printing:** spell cards list spells by level, then name, or by name alone. (#520)
- **Printing:** each weapon is named once on the PDF, with its two-handed damage and extras as bullets beneath it. (#722)
- **Printing:** PDF exports are smaller and faster, card icons print sharp, and the site name prints on each page and card back. (#710)
- **Printing:** exported PDFs use one built-in font, so they look the same in every viewer and print names such as Łukasz. (#719, #726)
- **Accounts:** warnings about sign-in attempts on your account now follow up while attempts continue (at most every 6 hours, with a count), instead of once a day.
- **Site:** on phones the header, its seven tabs and menus, the builder's tab bar and the ability buttons fit the screen. (#711)
- **Site:** tick boxes are rounded and fill with the theme's accent when ticked, and the sign-up page's updates box matches the rest.
- **Site:** the site footer groups its links into labelled Help, Support and Legal rows, with the copyright, SRD notice, credits and version in a quieter band below. (#729)
- **Site:** returning visitors download the app again only when it has changed, and see a new release straight away.
- **Site:** What's New lists the release as New, Improved and Fixed, in plain player terms, and links to the portrait artist's page.
- **Server:** a deployed server will not start without a mail server unless `EMAIL_REQUIRED=false` says that is intended; `./run` sets it for installs that run without email. (#716)
- **Server:** admin alerts and artist notices fall back to `APP_SUPPORT_EMAIL` when `EMAIL_ERRORS_TO` and `EMAIL_ADMIN_TO` are unset. (#716)
- **Server:** the log no longer lists every web request, and Docker logs are capped so they cannot fill the disk. (#716)
- **Server:** links in account emails use `APP_URL` when it is set; the startup report warns when it is not.
- **Server:** blank settings count as unset, and Docker secrets work for the app's signing key. (#701)
- **Server:** runs on Pedestal 0.8.3 and Jetty 12, and the production build leaves out the developer hot-reload tool.
- **Docs:** the setup and operation guides read as step-by-step instructions, list every server setting, and cover backups and email troubleshooting. (#724)

### Fixed
- **Characters:** switching between Race and Class no longer freezes the builder, and an edit rebuilds the character once. (#621)
- **Characters:** the Features tab loads when a trait has no name. (#680)
- **Characters:** a part of the sheet that cannot be drawn shows a message instead of blanking the whole sheet.
- **Characters:** saving a character that was deleted in another tab no longer sends you to the login page with nothing said; your changes stay, and Save stores them as a new character.
- **Characters:** unsaved builder edits are no longer lost when a character's sheet or list saves, and builder tabs open on different characters keep their own unsaved changes. (#704)
- **Characters:** a change made on the character sheet while offline is saved when the connection returns, instead of waiting for another change.
- **Characters:** the Robe of the Archmagi sets your AC to 15 + Dex instead of adding 5, so it no longer stacks with Draconic Resilience or Unarmored Defense. (#720)
- **Characters:** a tortle's Ring of Protection, Bracers of Defense and similar bonuses now count toward its AC of 17; only armor is ignored. (#720)
- **Characters:** anyone who can open a character sees its owner's custom items. (#252)
- **Characters:** a choice that no longer applied, such as a skill picked for a level since lowered, stayed hidden on the character and still counted on its sheet.
- **Characters:** deleting or changing a character's first class no longer wipes the other classes' choices.
- **Characters:** a magic item's spell DC bonus no longer raises ki, Breath Weapon and other DCs that are not spell DCs. (#728)
- **Characters:** uses that come back once a day were never reset; they now reset at dawn. (#728)
- **Characters:** a monk with a low Strength no longer attacks at +0 with a weapon Martial Arts does not cover; the attack uses the real modifier. (#713)
- **Characters:** the loading spinner no longer stays up after a failed Random character; moving to another page always clears it.
- **Characters:** Bards regain Bardic Inspiration on a short rest from level 5 (Font of Inspiration). (#310)
- **Builder:** a pasted portrait picture fills its frame instead of being squashed.
- **Builder:** the class drop-down no longer disappears when the character's class isn't applied (for example, its homebrew source is turned off or couldn't load); the row stays and says why.
- **Character sheet:** magic armor is listed with the other magic items.
- **Character sheet:** scrolled to the end of the page, the Rest button no longer covers the footer's last lines.
- **Character sheet:** the spell table's DC and Mod. headings no longer run together on phones.
- **Character sheet:** the opened weapon panel keeps its coloured bottom strip.
- **Character sheet:** changing something on someone else's shared character shows the change without trying to save it, so there is no leave-page warning and no trip to sign-in.
- **Character sheet:** only spells that make a spell attack show an attack roll; Bless and Cure Wounds no longer offer 1d20.
- **Inventory:** the same gear from several sources adds up instead of one replacing the rest, such as a pack's rations plus rations picked by hand. (#138)
- **Inventory:** two of the same weapon or armor count as two. (#340)
- **Spells:** a spell can no longer be prepared once every preparation is used. (#728)
- **Warlock:** Mire the Mind's Slow is cast once per long rest using a warlock spell slot, not at will, and a Warlock's at-will and Mystic Arcanum spells stay at their own level on a packed PDF spell sheet.
- **Magic items:** the Rod of the Pact Keeper raises the spell save DC and spell attack of warlock spells only, not a multiclass character's other spells or a race's.
- **Items:** built-in items no longer show Edit and Delete to visitors who are not logged in.
- **Homebrew:** a custom element with a blank or symbol-only name no longer breaks the character. (#675)
- **Homebrew:** older spell names (Leomund's, Tasha's, Bigby's) match in imported packs. (#676)
- **Homebrew:** a homebrew class's source label no longer corrupts saved spell selections. (#670)
- **Homebrew:** on/off toggles no longer corrupt homebrew data. (#677)
- **Homebrew:** a save that runs out of browser storage warns and offers a backup, and unreadable storage is kept for recovery instead of deleted. (#678)
- **Homebrew:** homebrew names with non-English letters are recognised. (#681)
- **Homebrew:** import and export no longer lose renamed items, and their messages are readable. (#672)
- **Homebrew:** an import reports success only once it is saved, and shows its problems at import time. (#682)
- **Homebrew:** a class that names a missing spell shows it by name with an edit link instead of a blank card.
- **Homebrew:** exporting after a save, and autosaving an empty builder, no longer crash.
- **Homebrew:** New in a homebrew builder keeps the Option Source the box shows, so saving no longer asks for a source that looks filled in.
- **Homebrew:** saving homebrew in one tab no longer erases homebrew saved in another; the older tab's change is merged and a real conflict is reported. Characters are not covered. (#705)
- **Homebrew:** renamed items keep their links, and the characters using them. (#705)
- **Homebrew:** a homebrew subrace, subclass, pact boon or eldritch invocation that was renamed, or given a new key on import to settle a conflict, stays picked on the characters that chose it.
- **Homebrew:** a custom magic item saved with no type no longer breaks. (#694)
- **Homebrew:** a homebrew class's choices that share a name show in level order; levels 10 and 17 came before level 3. (#304)
- **Homebrew:** a homebrew subclass with a very short name, or none, is offered in the character builder; before, its class was there and the subclass was missing.
- **Homebrew:** exporting a class no longer drops the armor, weapon and tool proficiencies it gives even when multiclassing in.
- **Homebrew:** a homebrew entry that cannot be drawn costs only its own part of the page: one section of its builder, one list or source in My Content, one row of the monster list. Before, the whole page fell over.
- **Homebrew:** a broken homebrew subclass, invocation, boon or subrace, or one whose name cannot be read, is set aside for repair and named; before, it vanished without a word, or every built-in class disappeared from the builder.
- **Homebrew:** a library section stored as `true`, a shared monster with a broken name, size or type, a feat whose ability score increases were stored as a list, and a grant of a skill that does not exist no longer break the app, the monster list, the feat builder or the character builder.
- **Homebrew:** a homebrew source no longer shows as "couldn't load" with a blank count and "No entries to repair" after all its entries have loaded again.
- **Homebrew:** retrying a restore after one that could not be saved no longer adds the restored entry to the library twice.
- **Sharing:** link previews of shared characters work on Discord, Mastodon, Slack, Telegram and X, with the character's title and image; `robots.txt` blocked the crawlers and every preview was empty. (#727)
- **Reference:** spells and monsters that share a name (common across homebrew packs) each show as their own row; clearing a search no longer leaves duplicate rows, and opening one no longer opens the others.
- **Reference:** the Spells, Monsters and Items lists come back at once when a search is cleared, even with large homebrew libraries.
- **Monsters:** stat blocks show reactions, such as the Marilith's Parry, and the monster builder can add one; the dragons, Succubus/Incubus and Spy show all their skills, the Flying Snake its Flyby trait, and the Alchemy Jug that its liquid can be created once a day. (#715)
- **Printing:** some spells never printed, prepared ticks landed on the wrong rows, and styles 3 and 4 could not export a character with two casting classes. (#709)
- **Printing:** the hit dice box, the second-page name box and style 4's allies and backstory are filled, the features page no longer lands between spell pages, fields read the same in every PDF viewer, negative numbers print correctly, and a reaction's casting time prints as "1 React." on spell cards. (#709)
- **Printing:** a magic item or spell whose name the font cannot draw no longer costs the card sheet it is on, and every sheet after it. (#402)
- **Printing:** names and notes in Polish, Turkish, Czech, Russian, Greek and other scripts beyond Western European print as written instead of as question marks, and curly quotes, dashes, ellipses, bullets, œ and € print as themselves. (#726)
- **Printing:** a race's spell page (a Tiefling's Hellish Rebuke and Darkness) no longer shows the class's spell slots; each such spell says how it is cast, for example "2nd level, once/long rest". (#726)
- **Printing:** exported sheets reported "Adobe InDesign CS6" as their creator and had no title or author.
- **Accounts:** a failed verification email no longer leaves an account stuck. (#700)
- **Accounts:** the app signs you out when the server stops accepting your login. (#708)
- **Accounts:** follow buttons remember who you follow after the page reloads. (#721)
- **Accounts:** behind a proxy, sign-in, sign-up and password-reset limits now count each player separately; a few mistyped passwords could lock everyone out for a minute. Set `TRUST_CLOUDFLARE=true` when the site is behind Cloudflare. (#725)
- **Site:** blank icons show again. (#696)
- **Site:** bold text renders bold, and buttons use the site font. (#711)
- **Site:** dismissing the cookie notice now hides it on every page, not only the page where it was dismissed. (#729)
- **Site:** an action that fails now says so and changes nothing; before, nothing visible happened, and the actions queued after it were dropped. (#729)
- **Site:** notices such as Unsaved drafts show their thin amber ring in Firefox, instead of an amber wash that hid their text.
- **Site:** pages without a composed portrait (the builder, the character list, a sheet) no longer download the artists' credits.
- **Server:** sign-in, password reset and the character, party, folder and item lists stay fast as the site grows; the first start after upgrading builds the new indexes in the background, and until then an older account with a mixed-case email address signs in by username. (#721)
- **Server:** the same content gets the same keys on a Turkish or other non-English server, and spell-card school names and the sheet's ability labels print the right letter ("Illusion", not "İllusion"). (#699)
- **Server:** settings set in `.env` now reach the server under Docker Compose and Swarm; branding (`APP_*`) and most other settings were dropped, so the site ran on defaults. (#724)
- **Server:** `JAVA_OPTS` now reaches Java in Docker; memory settings set there were ignored, so the app ran with a quarter of its container's memory. (#724)
- **Server:** `DATOMIC_PASSWORD` now reaches a Postgres database address that already carries settings (`?user=...`). (#724)
- **Server:** Datomic starts on Docker Swarm from the stack `./run --swarm` writes, which `docker stack deploy` now accepts, and regenerating the stack keeps your own file and writes the new one beside it. (#724)
- **Server:** the setup scripts read `.env` the way Docker does, so a value with an apostrophe or spaces (`APP_NAME=Dungeon Master's Vault`) no longer breaks `./run` or the start scripts. (#724)
- **Server:** `./run --check` run without a terminal (CI, cron) only reports; it no longer starts setup or `--upgrade` by default. (#724)
- **Server:** the Windows start scripts find their ports. (#701)
- **Server:** uptime monitors and link previews that check a page with a HEAD request get its headers instead of "not found".

### Security
- **Accounts:** sign-up no longer says when an email is already in use; it answers as for a new address and emails the account's owner instead. (#725)
- **Accounts:** on a server whose JVM runs a non-English locale, an email address containing a capital I folded to a different account key, so sign-in, password reset, the unsubscribe link and email change could miss the account. (#699)
- **Accounts:** account emails can no longer be triggered in bulk from one form that had no limit.
- **Site:** pages no longer load fonts from Google, so visiting the site no longer sends your address to Google. (#721)
- **Site:** other sites a page links to are told only this site's domain, never the page's address.
- **Site:** the Patreon button and mark come from this site, so pages no longer send visitors' addresses to Patreon.
- **Server:** sign-in, password reset, share links, image fetching and PDF export are hardened against misuse, and secrets (including a Postgres `sslpassword`) are no longer printed at startup.
- **Server:** security updates to the web server (now Jetty 12, clearing its five open advisories), the JSON parser, the PostgreSQL driver, password hashing, the email library and Guava, and Datomic 1.0.7745 with its networking library; an unused PDF library removed.
- **Docker:** Java starts with a stopgap flag that blocks CVE-2026-114938 (Java2Shell) on the app and Datomic containers, until the images run a patched Java 21 (`JAVA2SHELL_STOPGAP`, true or false).

## [breaking/2026-stack-modernization]

### Infrastructure

- **2026 full-stack modernization** (`22823da`)
  Java 8 → 21, Datomic Free → Pro, Pedestal 0.5 → 0.7.0, React 15 → 18,
  Reagent 0.6 → 2.0, re-frame 0.x → 1.4.4, PDFBox 2 → 3, clj-time → java-time,
  figwheel-main, lambdaisland/garden, Jackson/Guava pinning.

- **Consolidate dev tooling** (`6249565`)
  Unified `user.clj` with lazy figwheel, nREPL helpers, lein aliases
  (`fig:dev`, `fig:watch`, `fig:build`, `fig:test`), operational scripts
  (`start.sh`, `stop.sh`, `menu`), `:dev`/`:uberjar`/`:lint`/`:init-db` profiles.

- **Merge develop** (`1d50782`)
  Integrate character folders, weapon builder (special/loading properties),
  docker-compose updates from `origin/develop` (24 commits).

### Bug Fixes

- **`:class-name` → `:class`** (`263f290`)
  Reagent 2.x overwrites hiccup tag classes with `:class-name`. Converted all
  UI uses to `:class`; 18 remaining `:class-name` are D&D data keys (correct).

- **Subscribe-outside-reactive-context — phase 1** (`c2290ca`)
  42 fixes across events.cljs, options.cljc, classes.cljc, core.cljs.
  Patterns: direct db read, plugin-data map, track! template cache, SSOT pure fns.

- **Subscribe-outside-reactive-context — phase 2** (`09d7e4c`)
  14 fixes across options.cljc, pdf_spec.cljc, equipment_subs.cljs, views.cljs.
  Patterns: plugin-data threading, reg-sub-raw, move to render scope.

- **Prereq subscribes → pure character fns** (`9cbc25a`)
  22 prereq-fn lambdas in options.cljc converted from `@(subscribe)` to pure
  `(fn [character] ...)` functions.

- **Multiclass/wizard prereqs** (`3249f88`)
  7 multiclass and spell-mastery prereqs in classes.cljc converted to pure fns.

- **`def` + `partial` → `defn`** (`f578cdb`)
  `option-language-proficiency-choice` captured subscribe at load time via
  `partial`. Converted to `defn` for proper reactive context.

### Cleanup

- **Remove 11 orphaned subscriptions** (`bb2400d`)
  4 static map wrappers deleted (superseded by homebrew-aware versions).
  7 unused subs reader-discarded (`#_`) with comments: `all-melee-weapons`,
  `item`, `base-spells-map`, `spell-option`, `spell-options`,
  `filtered-monster-names`, `has-prof?`. Pre-existing tech debt, not caused
  by subscribe refactor.

- **Fix 591 missing-else-branch lint warnings** (`29c9f28`, via `fix/lint-missing-else`)
  Mechanical `if→when`, `if-let→when-let`, `if-not→when-not` across 33 files.
  Scripted fix (`scripts/fix-missing-else.py`) with column-precise substitution.
  Also fixed 2 pre-existing bugs: `when` used instead of `if` for two-branch
  conditionals in classes.cljc:1808 and options.cljc:463.

- **Fix forward-reference lint error** (`792fe3c`)
  `show-generic-error` used before its `def` alias in events.cljs. Changed to
  fully-qualified `event-utils/show-generic-error`.

- **Consolidate lint config** (`7476f10`)
  All linter settings moved from project.clj `:lint` profile to
  `.clj-kondo/config.edn` (single source of truth for IDE + CLI). Lint scope
  expanded to cover `native/`, `test/`, `web/`. clj-kondo bumped to 2026.01.19.
  LSP false-positive suppression via `:exclude-when-defined-by` for re-frame.

- **Dead code cleanup — ~92 vars** (`6bbcd9a`, `b68b917`)
  `#_` reader-discard on dead defs across 10 source files: deprecated ua/scag
  refs, superseded template UI (ability roller, amazon frames), 17 never-dispatched
  event handlers, dead style defs, duplicate constants. Includes cascade cleanup
  (helpers that lost all callers). Each `#_` has a comment explaining why.

- **Redundant expression fixes** (in `6bbcd9a`, `b68b917`, `429152e`)
  Remove nested `(str (str ...))`, flatten `(and (and ...))`, remove duplicate
  destructuring param, remove unused refers, narrow test `:refer` lists,
  fix unreachable code in registration.cljc.

### Enhancements

- **Input debounce** (`d108134`)
  Moved debounce from component-level `input-field` to `debounced-build-sub`
  in subs.cljs (leading+trailing edge, 500ms). Eliminates per-keystroke
  entity/build recomputation.

- **Folder hardening** (`f28f58f`)
  `on-folder-failure` event re-fetches server state on HTTP error. Client +
  server blank-name validation. `check-folder-owner` wrapped with
  `interceptor/interceptor`, returns 404 for missing folders. Named tempid
  `"new-folder"` + `d/resolve-tempid`. `case` default clause in folders sub.
  CSS class fix (`builder-dropdown` → `builder-option-dropdown`).

- **UI polish** (`d163ca9`)
  Zero-warning dev/prod builds, dev-mode CSP nonce, favicon, custom
  `externs.js` for React 18 advanced compilation.

### Tests

- **CLJS test infrastructure** (`b96b1b6`)
  figwheel-main test build, `test_runner.cljs`, pure function tests for
  compute, entity, character accessors.

- **JVM tests for new code** (`6124d9f`)
  `compute-all-weapons-map`, feat-prereqs, pdf_spec pure functions, folder
  routes (CRUD + blank rejection + trimming).

- **Folder validation tests** (in `f28f58f`)
  Blank name → 400, whitespace trimming, nil defaults to "New Folder",
  name unchanged after rejected renames.

### Documentation

- **Migration docs** (`026b031`)
  MIGRATION-INDEX.md, JAVA-COMPATIBILITY.md, datomic-pro.md, pedestal-0.7.md,
  frontend-stack.md, library-upgrades.md, dev-tooling.md, ENVIRONMENT.md,
  testing.md.

- **STACK.md** (in `f28f58f`)
  Library/dependency onboarding guide: architecture diagram, all frameworks,
  build system, profiles, dependency pinning rationale.

### Current Status

- **174 JVM tests**, 444 assertions, 0 failures
- **0 CLJS errors**, 0 warnings (dev + advanced)
- **0 subscribe warnings** in browser console
- **0 linter errors**, 0 warnings

---

## [feature/error-handling-import-validation] (merged)

### New Features

#### Import Validation (`import_validation.cljs` -- new file)
- **Unicode normalization**: Converts smart quotes, em-dashes, non-breaking spaces, and 40+ other problematic Unicode characters to ASCII equivalents on import and homebrew save. Prevents copy-paste corruption from Word/Google Docs.
- **Required field detection & auto-fill**: On import, missing required fields (`:name`, `:hit-die`, `:speed`, etc.) are auto-filled with placeholder values like `[Missing Name]`. Content types covered: classes, subclasses, races, subraces, backgrounds, feats, spells, monsters, invocations, languages, encounters.
- **Trait validation**: Nested `:traits` arrays are checked for missing `:name` fields and auto-filled.
- **Option validation**: Empty options (`{}`) created by the UI are detected and auto-filled with unique default names ("Option 1", "Option 2", etc.).
- **Multi-plugin format detection**: Distinguishes single-plugin from multi-source orcbrew files for correct processing.

#### Export Validation
- **Pre-export warning modal**: Before exporting homebrew, all content is validated for missing required fields. If issues are found, a modal lists them with an "Export Anyway" option.
- **Specific save error messages**: `reg-save-homebrew` now extracts field names from spec failures and shows targeted messages instead of generic "You must specify a name" errors.

#### Content Reconciliation (`content_reconciliation.cljs` -- new file)
- **Missing content detection**: When a character references homebrew content that isn't loaded (e.g., deleted plugin), the system detects missing races, classes, and subclasses.
- **Fuzzy key matching**: Uses prefix matching and base-keyword similarity to suggest available content that resembles missing keys (top 5 matches with similarity scores).
- **Source inference**: Guesses which plugin pack a missing key likely came from based on key structure.

#### Missing Content Warning UI (`character_builder.cljs`)
- **Warning banner**: Orange expandable banner appears in character builder when content is missing, showing count and details.
- **Detail panel**: Lists each missing item with its content type, key, inferred source, and suggestions for similar available content.
- **DOM IDs for testability**: `#missing-content-warning`, `#missing-content-details`, `.missing-content-item` with `data-key` and `data-type` attributes.

#### Conflict Resolution Modal (`views/conflict_resolution.cljs`, `events.cljs`)
- **Duplicate key detection**: On import, detects keys that conflict with already-loaded homebrew (both internal duplicates within a file and external conflicts with existing content).
- **Resolution UI**: Modal presents each conflict with rename options. Key renaming updates internal references (subclass -> parent class mappings, etc.).
- **Color-coded radio options**: Rename (cyan), Keep (orange), Skip (purple) with left-border + tinted background. All styles in Garden CSS.

#### Import Log Panel (`views/import_log.cljs`)
- **Grouped collapsible sections**: Changes grouped into Key Renames, Field Fixes, Data Cleanup, and Advanced Details (collapsed by default). Empty sections hidden automatically.
- **Detailed field fix reporting**: Field Fixes section shows per-item breakdown — which item, content type, which fields were filled, how many traits/options were fixed.
- **Collapsible section component**: Reusable `collapsible-section` with configurable icon, colors, and default-expanded state.

#### OrcBrew CLI Debug Tool (`tools/orcbrew.clj` -- new file)
- `lein prettify-orcbrew <file>` -- Pretty-prints orcbrew EDN for readability.
- `lein prettify-orcbrew <file> --analyze` -- Reports potential issues: nil-nil patterns, problematic Unicode, disabled entries, missing trait names, file structure summary.

### Bug Fixes

#### nil nil Corruption (`events.cljs`)
- **Root cause fix**: `set-class-path-prop` was calling `assoc-in` with a nil path, producing `{nil nil}` entries in character data. Now guards against nil path before the second `assoc-in`.

#### Nil Character ID Crash (`views.cljs`)
- Character list page crashed with "Cannot form URI without a value given for :id parameter" when characters had nil `:db/id`. Added `(when id ...)` guard to skip rendering those entries.

#### Subclass Key Preservation (`options.cljc`, `spell_subs.cljs`)
- Subclass processing now uses explicit `:key` field if present (for renamed plugins), falling back to name-generated key. Prevents renamed keys from reverting.
- `plugin-subclasses` subscription preserves map keys and sets `:key` on subclass data correctly.

#### Plugin Data Robustness (`spell_subs.cljs`)
- `plugin-vals` subscription wrapped in try-catch to skip malformed plugin data instead of crashing.
- `level-modifier` handles unknown modifier types gracefully (logs warning, returns nil instead of throwing).
- `make-levels` filters out nil modifiers with `keep`.

#### Unhandled HTTP Status Crash (`subs.cljs`, `equipment_subs.cljs`)
- All 7 API-calling subscriptions used bare `case` on HTTP status with no default clause. Any unexpected status (e.g., 400) threw `No matching clause`. Replaced with `handle-api-response` HOF that logs unhandled statuses to console.

#### Import Log "Renamed key nil -> nil" (`events.cljs`, `import_validation.cljs`)
- Key rename change entries used `:old-key`/`:new-key` fields but display code expected `:from`/`:to`. Unified on `:from`/`:to` across creation, application, and display.

### Error Handling (Backend)

#### Database (`datomic.clj`)
- Startup wrapped in try-catch with structured errors: `:missing-db-uri`, `:db-connection-failed`, `:schema-initialization-failed`.

#### Email (`email.clj`)
- Email config parsing catches `NumberFormatException` for invalid port (`:invalid-port`).
- `send-verification-email` and `send-reset-email` check postal response and raise on failure (`:verification-email-failed`).

#### PDF Generation (`pdf.clj`, `pdf_spec.cljc`)
- Network timeouts (10s connect, 10s read) for image loading. Specific handling for `SocketTimeoutException` and `UnknownHostException`.
- Nil guards throughout `pdf_spec.cljc`: `total-length`, `trait-string`, `resistance-strings`, `profs-paragraph`, `keyword-vec-trait`, `damage-str`, spell name lookup. All use fallback strings like "(unknown)", "(Unknown Spell)", "(Unnamed Trait)".

#### Routes (`routes.clj`, `routes/party.clj`)
- All mutation endpoints wrapped with error handling: verification, password reset, entity CRUD, party operations. Each uses structured error codes (`:verification-failed`, `:entity-creation-failed`, `:party-creation-failed`, etc.).

#### System (`system.clj`)
- PORT environment variable parsing validates numeric input (`:invalid-port`).

#### Error Infrastructure (`errors.cljc` -- expanded)
- New error code constants for auth flows.
- `log-error`, `create-error` utility functions.
- `with-db-error-handling`, `with-email-error-handling`, `with-validation` macros for consistent patterns.

### Supporting Changes

#### Common Utilities (`common.cljc`)
- `kw-base`: Extracts keyword base before first dash (e.g., `:artificer-kibbles` -> `"artificer"`).
- `traverse-nested`: Higher-order function for recursively walking nested option structures.

#### Styles (`styles/core.clj`)
- `.bg-warning`, `.bg-warning-item` CSS classes for warning banner UI.
- `.conflict-*` Garden CSS classes for conflict resolution modal (backdrop, modal, header, footer, body, radio options with color-coded variants: cyan/rename, orange/keep, purple/skip).
- `.export-issue-*` Garden CSS classes for export warning modal.

#### App State (`db.cljs`)
- Added `import-log` and `conflict-resolution` state maps to re-frame db.

#### Subscriptions (`subs.cljs`, `equipment_subs.cljs`)
- Import log, conflict resolution, export warning, missing content report subscriptions.
- `handle-api-response` HOF (`event_utils.cljc`) — centralizes HTTP status dispatch with sensible defaults (401 → login, 500 → generic error) and catch-all logging for unhandled statuses. Replaces bare `case` statements across 7 API-calling subscriptions.

#### Entry Point (`core.cljs`)
- Dev version logging on startup.
- Import log overlay component mounted in main view wrapper.

#### Linter Configuration
- `.clj-kondo/config.edn`: Exclusions for `with-db` macro and user namespace functions.
- `.lsp/config.edn` (new): Explicit source-paths to prevent clojure-lsp from scanning compiled CLJS output in `resources/public/js/compiled/out/`.

### Design Principles

- **Import = permissive** (auto-fix and continue), **Export = strict** (warn user, let them decide)
- **Placeholder text convention**: `[Missing Name]` format (square brackets indicate auto-filled)
- **Modal pattern**: db state -> re-frame subscription -> event handlers -> component in `import-log-overlay`
