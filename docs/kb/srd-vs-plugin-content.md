# SRD vs Plugin Content — What's Hardcoded

## The Distinction

Only **SRD (System Reference Document)** content is hardcoded in the app.
Everything else — including most PHB content — comes from plugins (orcbrew files).

This matters for content reconciliation (detecting missing homebrew references)
and any feature that checks "is this content available?"

## What's Hardcoded (SRD)

| Type | Hardcoded | Source |
|------|-----------|--------|
| Classes | All 12 base classes | `classes.cljc` via `spell_subs.cljs:base-class-options` |
| Races | 9 races + subraces (dwarf, elf, halfling, human, dragonborn, gnome, half-elf, half-orc, tiefling) | `spell_subs.cljs:902-928` |
| Subclasses | 1 per class (Champion, Berserker, Lore, Life, Land, Open Hand, Devotion, Hunter, Thief, Draconic, Fiend, Evocation) | `classes.cljc` — non-SRD are `#_` discarded |
| Backgrounds | Acolyte only | `spell_subs.cljs:538` — `(cons acolyte-bg plugin-backgrounds)` |
| Feats | None | All from plugins |

## What Comes From Plugins

All non-SRD PHB content: Battle Master, Totem Warrior, College of Valor,
Knowledge Domain, Folk Hero, Sage, Soldier, etc.

The `plugin-*` subscriptions return only plugin content:
- `::classes5e/plugin-classes`
- `::classes5e/plugin-subclasses`
- `::races5e/plugin-races`
- `::races5e/plugin-subraces`
- `::bg5e/plugin-backgrounds`
- `::feats5e/plugin-feats`

The full subscriptions combine hardcoded + plugin:
- `::classes5e/classes` = base-class-options + plugin-classes
- `::races5e/races` = 9 hardcoded races + plugin-races
- `::bg5e/backgrounds` = acolyte + plugin-backgrounds

## Lesson Learned

An earlier version of content reconciliation had `builtin-backgrounds` with all
13 PHB backgrounds and `builtin-subclasses` with all PHB subclasses. This
silently suppressed warnings for non-SRD content like Folk Hero and Battle Master
that actually comes from plugins. When a user deleted their plugins, those items
weren't flagged as missing.

**Rule:** Only add truly hardcoded SRD content to builtin exclusion sets. If
unsure, check the source file — non-SRD options in `classes.cljc` are `#_`
reader-discarded and come from plugins.
