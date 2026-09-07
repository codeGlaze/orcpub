# Feat builder — widget map, gaps, and why feat is LAST

Audit of `feat-builder` (`views.cljs:5877`) against what the compiler actually accepts. Written
because a previous pass sorted the widgets by *shape* and called that a map; shape alone misses the
four things that matter — the verb split and the built-but-unwired hook that dissolves it, the AC
vocabulary the builder never got, the third grant vocabulary in the Custom Feat path, and the dead
control.

Method: every widget the builder renders → the `:props` key it writes → the arm in
`make-feat-modifiers` / `make-feat-selections` (`options.cljc:3766` / `:3682`) that reads it.

## 1. The verb split is PHYSICAL — both halves already exist, wired to opposite silos

`declarative-grant-vocabulary.md` names two verbs: **grant** (creator picks the members) and
**select** (user picks at build time, creator picks the count). Both are built. Neither builder has
both.

| pool | grant widget | used by | select widget | used by |
|---|---|---|---|---|
| languages | `option-languages` (`:5178`) | race, subrace, monster | `feat-languages` (`:5546`) | **feat only** |
| skills | `option-skill-proficiency` (`:5162`) | race, subrace | `feat-skill-proficiency` (`:5407`) | **feat only** |
| weapons | `option-weapon-proficiency` (`:5641`) | race, subrace | `feat-weapon-proficiency` (`:5424`) | **feat only** |

So **a feat cannot grant a specific language; a race cannot offer a language choice.** Not a missing
feature — the widget exists, it is wired to the other silo. `options.cljc:3676` says the same thing
from the compiler side: `make-feat-selections` is feat-only, and "making this reachable from every
silo is the prime cross-silo target."

This is why feat was planned LAST. Every other silo has to be opened up to cross-pool granting
first; feat is where the two halves meet.

## 1b. The hook that dissolves the split is ALREADY BUILT — it is wired to one silo, one pool

`grant-selection` (`options.cljc:3922`) is the generic cross-pool grant, and its own docstring says
so: *"Pool-agnostic AND owner-agnostic — one hook serves feat/background/race/subrace/class/subclass."*
Four modes, of which two ARE the two verbs:

| mode | meaning | verb |
|---|---|---|
| `{:from p :choose n}` | n from the whole pool | **select** (user picks) |
| `{:from p :filter #{…}}` | n from a subset | select, narrowed |
| `{:from p :key :k}` | one forced entry | **grant** (creator picks) |

So the verb split is not a missing abstraction. It is a wiring gap. Three wires, none of them the
hook:

1. **The registry holds one pool.** `template.cljc:1563` passes
   `{:fighting-styles {:name "Fighting Style" :options fighting-style-pool}}`. Languages, skills,
   tools, weapons and armor are not registered — though `content-pools/pool` is the generic
   constructor for exactly that (built-in ++ homebrew, order-stable).
2. **One call site.** `feat-option-from-cfg` is the only assembly fn that takes `grantable-pools`.
3. **No builder writes `:grant`.** No widget in any builder emits the key.

The three feat-only select props are instances of the generic key, and the grant widgets are its
`:key` mode:

| today | as a grant | who has it today |
|---|---|---|
| `{:language-choice 2}` | `{:grant {:from :languages :choose 2}}` | feat only |
| `{:skill-tool-choice 3}` | `{:grant {:from :skills-and-tools :choose 3}}` | feat only |
| `{:weapon-prof-choice 4}` | `{:grant {:from :weapons :choose 4}}` | feat only |
| `{:language {:elvish true}}` | `{:grant {:from :languages :key :elvish}}` | race/subrace/monster only |

Old keys stay as D9 read-shims — nothing saved moves. The same registry entry that lets a feat grant
a specific language lets a race offer a language *choice*, because the hook does not know who owns
it. **This is the years-old asymmetry the pool/grant initiative was started to fix**
(`content-extensibility-direction.md`), and the prototype is one pool short of demonstrating it.

## 2. The AC work landed a general vocabulary. The feat builder never got it.

`armor-class-refactor.md:342` generalised the medium-armor cap into a per-type
`{:armor-dex-cap {:medium 3}}`, keeping `:medium-armor-max-dex-3` as a D9 key that compiles to it.
The same refactor produced `:ac`, `:ac-bonus`, `:armor-gives-no-ac`, and the conditional
`:attack-bonus` / `:damage-bonus` tag maps.

**The compiler accepts all six. The feat builder offers none of them** — only the two hardcoded
legacy checkboxes it has had since before the refactor.

| feat builder offers | compiler also accepts | an author today CANNOT say |
|---|---|---|
| "medium armor, +3 Dex if Dex 16+" | `:armor-dex-cap {:heavy 2}` | +2 Dex cap on heavy armor |
| "+1 AC wielding two melee weapons" | `:ac-bonus {:bonus N :armor? b :shield? b}` | +1 AC while wearing a shield |
| — | `:attack-bonus {:bonus 2 :ranged? true}` | Archery, as a feat |
| — | `:ac {:ac 13 :abilities [:dex]}` | any unarmored-defense feat |
| — | `:flying-speed`, `:swimming-speed` | any movement beyond walking |

The general vocabulary is authorable in **exactly one builder** — fighting style, via
`bf/effect-rows` — and `views.cljs:7893` already says in its own comment that the fragment "can be
dropped into any other builder's extra-fields unchanged." Adding it to feat is a one-line change to
`feat-builder`'s `extra-fields`.

**One exception that is NOT a shim.** `:two-weapon-ac-1` compiles to `dual-wield-ac-mod`
(`options.cljc:1437`), which writes the *same* `?ac-bonus-fns` channel as `:ac-bonus` but conditions
on a wielding predicate. The `:ac-bonus` tag vocabulary is `:armor?` / `:shield?` only. Expressing
this generally needs a **new tag** (`:dual-wield?`), not a re-spelling of the old key.

## 3. Dead control

`:improvised-weapons-prof` (`feat-weapon-proficiency`, `:5430`) has **zero consumers** — no arm in
`make-feat-modifiers`, no reader anywhere in `src/`. An author checks it, it saves, it compiles to
nothing. Either wire it (`modifiers/weapon-proficiency :improvised` exists, `options.cljc:1813`) or
remove the control; do not carry it forward silently.

Checked and NOT dead, contrary to first appearance: `:damage-vulnerability` and
`:condition-immunity` have no `:props` arm either, but their widgets are monster-only and monsters
have a separate compile path (`spell_subs.cljs:1354`).

## 4. Hardcoded UI ranges the compiler does not impose

`:speed` (5/10/15/… as checkboxes), `:initiative` (1–5), `:max-hp-bonus` (1–2),
`:skill-tool-choice` (1–3), `:language-choice` (1–3), `:weapon-prof-choice` (3–4) are all **scalars**
written by `toggle-feat-value-prop` (`events.cljs:3519`) — a radio group with deselect: picking the
same value again `dissoc`s the key. The ranges live only in the `(range …)` calls in the view; the
compiler takes any number. Converting these to `:number` or an `:enum` is a capability increase, not
a like-for-like port.

⚠️ **The one real conversion hazard.** These are mutually exclusive. An `:enum` with a nil option
preserves that exactly. A `:multi-enum` would write `{1 true, 2 true}` where the compiler expects a
number. That is the one thing worth a unit test.

## 5. Full widget map

| widget | key(s) | shape | compiler | note |
|---|---|---|---|---|
| `feat-prereqs` | `:prereqs`, `:path-prereqs` | — | `feat-prereqs` | one-off, limited vocab |
| `feat-ability-increase-options` | `:ability-increases` | not `:props` | `feat-modifiers` | one-off |
| `feat-skill-proficiency` | `:skill-tool-choice` | scalar | selections | **select verb** |
| `feat-languages` | `:language-choice` | scalar | selections | **select verb** |
| `feat-weapon-proficiency` | `:weapon-prof-choice` | scalar | selections | **select verb** |
| | `:improvised-weapons-prof` | bool | **none** | ☠️ dead |
| `feat-armor-proficiency` | `:armor-prof` | map-of-flags | modifiers | **grant verb** |
| | `:medium-armor-stealth` | bool | modifiers | ok |
| | `:medium-armor-max-dex-3` | bool | modifiers (D9→`:armor-dex-cap`) | legacy-only |
| `feat-hps` | `:max-hp-bonus` | scalar | modifiers | UI-capped |
| `feat-damage-resistance` | `:damage-resistance` | map-of-flags | modifiers | **grant verb** |
| `feat-speed-bonuses` | `:speed` | scalar | modifiers | UI-capped |
| `feat-initiative-bonuses` | `:initiative` | scalar | modifiers | UI-capped |
| `feat-misc-modifiers` | `:two-weapon-ac-1` | bool | modifiers (hardcoded) | needs `:dual-wield?` tag |
| | `:two-weapon-any-one-handed` | bool | modifiers | ok |
| | `:saving-throw-advantage-traps` | bool | modifiers (hardcoded) | `:saving-throw-advantage` is general |
| | `:passive-perception-5`, `:passive-investigation-5` | bool | modifiers (hardcoded 5) | no general form exists |
| `feat-spellcasting` | `:magic-novice`, `:ritual-casting`, `:attack-spell` | bool | selections | 3 fixed templates |
| `option-skill-proficiency-or-expertise` | `:skill-prof-or-expertise` | map-of-flags | modifiers | **grant verb** |
| `option-tool-proficiency-or-expertise` | `:tool-prof-or-expertise` | map-of-flags | modifiers | **grant verb** |

## 6. What this changes about sequencing

Feat is not a conversion. It is three jobs that happen to meet in one builder, in this order:

1. **Register a second pool and thread `grantable-pools` to one more silo.** The smallest change
   that proves the bridge prototype generalises, and the load-bearing one: it is what makes the
   grant/select verbs reachable from anywhere. Registry entry + one more assembly-fn arg; the hook
   itself is untouched (§1b).
2. **Extend** — drop `(bf/effect-rows)` into feat's `extra-fields` and the six AC/weapon props
   become authorable. Already written, already tested in fighting style. One line, independent of
   step 1, and worth doing whenever (§2).
3. **Convert** — the field-schema port. Cheapest, and least useful first: a generated form over the
   un-opened data path renders the same gaps in a nicer grid.

`declarative-grant-vocabulary.md` §Sequencing already said this ("make the data path uniform first
— THEN let the registry generate the builder form"). Feat being last in the original plan is the
same conclusion arrived at from the other end.

## 7. A THIRD grant vocabulary — the Custom Feat option list

`decision-vocabulary.md` compares vocabularies A (`:props`, flat, cljc) and B (`:level-modifiers`,
level-gated, cljs). There is a third, unlisted: the **Custom Feat** "Feat Modifiers" multiselect
built by `custom-option-builder` (`options.cljc:1470`, list at `:1855-1960`). It is the in-character
custom feat, not the feat builder page, and it spells the same effects a third time as ~20 hardcoded
`option-cfg`s — "Speed +10", "Initiative +5", "Passive Perception +5", "Medium Armor: Max DEX Bonus
of 3", "Three Skills or Tools" — each a fixed instance of a prop the compiler already parameterises.

Two defects in it, found while chasing `:improvised-weapons-prof`:

- **`"Passive Investigation +5"` grants passive PERCEPTION** (`options.cljc:1901`).
  `modifiers/passive-investigation` exists (`modifiers.cljc:445`) and the `:props` path uses it
  correctly; this is a copy-paste from the entry directly above. ⚠️ Fixing it changes the sheet of
  any saved character that selected it — a behaviour change, not a silent tidy-up.
- **`"Improvised Weapons Proficiency"`** (`:1923`) has `:name` and `:help` and no `:modifiers`. A
  player selects it, it persists in the character entity, the box stays checked — and it grants
  nothing and shows no trait. `modifiers/weapon-proficiency :improvised` exists and Tavern Brawler
  uses it (`:1813`).

So `:improvised-weapons-prof` is inert in **both** authoring surfaces, in two different ways: no
compiler arm in the feat builder (§3), no `:modifiers` in the Custom Feat list.

## Corrections
- **A previous pass reported feat as "14 widgets → 3 shapes + 3 one-offs" and recommended feat as
  the next conversion.** The shape sort was right and is preserved in §5, but the recommendation was
  wrong: it counted controls and never diffed the builder against the compiler, so it missed the
  verb split (§1), the entire AC vocabulary gap (§2), and the dead control (§3). Sorting widgets by
  the control they render is not a map of a builder; the map is builder-key → compiler-arm.
- **§6 originally led with the `bf/effect-rows` extension.** Reordered once `grant-selection` was
  found: extending feat is a one-line win but silo-local, while registering a second pool is what
  actually unblocks every other builder. The extension keeps its place as an independent step.
