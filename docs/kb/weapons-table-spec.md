# Weapons table: the spec

The character sheet's Weapons section, redesigned. Every mockup and the build must meet every
"Agreed" line; check them one by one before showing anything. Decisions are the owner's,
2026-10-15. Background: [equipment-parity.md](equipment-parity.md) (the Net), [ux-guide.md](ux-guide.md).

## Agreed

| # | Rule |
|---|---|
| 1 | **Columns:** Weapon · To hit · Damage · (expand arrow). Headings stay. Headings and rows share one grid sized in px; each heading's centre is within 2px of its buttons' centre, measured. |
| 2 | **No Details or Proficient? column.** Proficiency shows only when it is missing: "Not proficient" in the weapon's grey line. |
| 3 | **Weapon cell:** the name in bold, then one muted line, SRD style: "Melee · Versatile (1d10)", "Ranged (150/600) · Heavy", "Thrown (5/15) · Special". It fits on one line at 390px. |
| 4 | **To hit:** "+4", not "1d20+4", in the app's `.roll-button`. |
| 5 | **Damage:** the app's `.roll-button` with the dice ("1d8+2") and the damage type under it ("slashing"). |
| 6 | **A weapon with no damage (the Net):** no damage button; a neutral `.chip` with its short summary, "Restrains". The full SRD text is in the opened row. The PDF reads "None (restrains)". |
| 7 | **Versatile is visible in the row** and does not make the row taller than its neighbours. No stacked "v 1d10+2" button. How: see Open. |
| 8 | **Only the app's own styles:** `.roll-button` as it is (amber gradient, white text, its text shadow), `table.striped` rows, `.p-10` cells, `.chip`, the amber active tint `rgba(240,161,0,0.28)`. No new colours, buttons or spacing. White on amber failing contrast (2.11:1) is a separate, app-wide ticket. |
| 9 | **Fits a 390px phone:** the table is no wider than its container; every cell is centred vertically in its row. |
| 10 | **Small spaces get the short summary, the opened row gets the full text** (UX guide, 2026-10-15). |
| 11 | **Review on a phone:** true-size phone screenshots of the real app, today beside the proposal. No design canvases for review. |

## Open

- **Versatile control:** split button (the damage button with a joined "2H" segment) or a "Two hands" chip in the grey line. Measured: split keeps the Longsword row at 47px but widens Damage to 132px, wrapping the other grey lines; the chip keeps every grey line on one line, Longsword row 57px.
- **Shield rule (proposed):** with a shield in the off hand, the two-handed option is greyed and the opened row says why. Two-handed weapons dimmed likewise. Not decided.
- **Desktop layout**, **200% text** (must wrap without clipping), **the opened row**, and **Armor in the same style**: not yet designed.

## Rejected: do not bring back

Dark text on amber (also rejected 2026-09-16 for the share button) · invented or darker amber
buttons · flat buttons · cards (option C) · stat-block lines (option B) · dotted underlines on many
words · a "Main hand / Off hand" strip · a separate two-handed line under the damage · dropping the
headings · labels inside the To hit button in place of headings · testing a change on today's table
instead of on this layout.

## How mockups are made

One way only: the real app (`lein e2e-server` or `scripts/e2e/run.sh`), the weapons table rewritten
in the page with the app's own classes, captured at 390px (and 1280px for desktop). Before any image
goes to the owner: measure rules 1 and 9, check every row of the Agreed table against the image,
and look at the image.
