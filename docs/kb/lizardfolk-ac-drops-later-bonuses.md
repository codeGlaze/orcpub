# Lizardfolk AC drops bonuses applied after it

**Live defect, found 2026-10-06** while testing the Robe of the Archmagi fix (codeGlaze/orcpub PR #55).
Present on `integration` before and after that change.

## What happens

`:lizardfolk-ac` in `make-feat-modifiers` (`options.cljc`, near line 3466) sets
`?natural-ac-bonus` 3 and replaces `?armor-class-with-armor` with a function that takes the higher
of its own base and the previous `?armor-class-with-armor`. That previous function is the one that
stood when the lizardfolk modifier applied. A bonus added to `?ac-bonus-fns` after that point (a Ring
or Cloak of Protection, Bracers of Defense) is in the new list but not in the function the wrapper
kept, so it is dropped.

Measured with Dex 14 and a +1 ring (`robe_ac_test.clj` pins these):

| order | AC |
|---|---|
| ring applied, then lizardfolk | 16 (natural 15 + 1) |
| lizardfolk applied, then ring | 15 (ring dropped) |

The same held on `integration` with the ring in `?magical-ac-bonus`, so this predates PR #55.

## What is not known

Which order a real character gets. Modifier order comes from the dependency sort in `entity.cljc`
(`order-modifiers`; its docstring warns that another valid order can change a computed AC). Building
a lizardfolk character with a Ring of Protection in the app would answer it.

## The likely fix

Express lizardfolk's AC as a competing calculation (`mod5e/ac-formula`, as natural armor is on
`feature/grant-rows`) instead of wrapping `?armor-class-with-armor`. `:tortle-ac` wraps it the same
way and returns a flat 17 + shield, ignoring every bonus by design of the wrapper, not of the rules.
