# Plan: custom magic items on a shared character sheet

How to make a character's CUSTOM magic items / weapons / armor apply on a shared
sheet (a link opened by a different session), extending the homebrew-embed share
feature. Output of a dedicated research spike. file:line against the
integration/colon-keyword tree.

## Headline finding (dissolves the hard part)

The assumption that custom items are referenced "by DB id" is WRONG for
rendering. A character references a custom item by a **name-derived keyword** —
the same shape as a homebrew plugin key — not by `:db/id`. `:db/id` matters only
to the library editor (edit/delete), never to sheet resolution. So this is
essentially the SAME problem already solved for `:shared-plugins`, and no
id-remapping is needed. Tractability: HIGH.

Evidence:
- `template.cljc:1248-1260` magic-item-selection stores each equipped item as
  `{::entity/key <item-key> ::entity/value cfg}` in `::entity/options`.
- `equipment_subs.cljs:141-164` magic-item-options: `item-key (or key (keyword
  (str "id-" id)))` — and for custom items `key` is always the name-derived key.
- `magic_items.cljc:2953-2956` add-key -> `(common/name-to-kw (name-key item))`;
  expand-weapon (2992-3018) / expand-armor (3039-3065) key by `(name-to-kw name)`.
- So `::entity/key` of a custom item == `(common/name-to-kw its-name)`. The
  `:id-<n>` fallback is effectively dead and wouldn't resolve anyway.

## Resolution path (where injected defs must live)

Sheet render resolves an equipped item via:
1. built-character inventory subs `::char5e/magic-weapons` (subs.cljs:733),
   `::char5e/magic-armor` (721), `::char5e/magic-items` (738) -> `{item-kw cfg}`.
2. item-kw looked up in by-key maps `::mi5e/all-weapons-map`
   (equipment_subs.cljs:227-233), `::mi5e/all-armor-map` (203-209),
   `::mi5e/all-magic-items-map` (235-243).
3. those merge SRD with custom maps built via map-by-key-or-id from
   `::char5e/sorted-items` (72-77) = `expanded-custom-items ++ static`.

The single seam is **`::mi5e/expanded-custom-items` -> the `::mi5e/custom-items`
app-db key**. Injecting there also makes the derived STATS apply (not just the
tooltip): the template options magic-weapon-options/etc. (equipment_subs.cljs:166-179)
feed `::char5e/template-selections` (289-326) and also derive from sorted-items;
if the def is absent when the template builds, the selected `::entity/key`
produces NO modifier and the item silently does nothing. Feeding the overlay in
fixes render + stats at once.

## The one gotcha (hardest part)

Two modifier-condition sites read `::mi/custom-items` DIRECTLY from
`re-frame.db/app-db`, bypassing subs: `options.cljc:1218` and `options.cljc:1796`
(both `mi/compute-all-weapons-map (get @app-db ::mi/custom-items)`). A separate
`:shared-custom-items` key is invisible to these. Fix: introduce
`mi/effective-custom-items` = `(concat (::mi/custom-items @app-db)
(:shared-custom-items @app-db))` and use it at both sites. If missed, shared
weapons' conditional modifiers (dual-wield, AC type) silently ignore the item
while the tooltip looks fine.

## Plan (mirrors the homebrew flow)

1. WIRE FORMAT: custom items are NOT one of the 13 content-types, so
   whitelist-bundle (share_bundle.cljc) would reject them. Carry them in a
   SEPARATE top-level bundle section: `{:plugins {...} :custom-items [raw-item ...]}`.
   Extend the codec to serialize/parse both.
2. EXTRACT (share time, integrations.cljs:164-181, after sb/extract-bundle):
   reuse `share_bundle/selected-keys` (all ::entity/keys), index the sharer's
   `::mi5e/expanded-custom-items` by `:key` (common/map-by-key — NOT
   custom-item-map, which is by :db/id), `(select-keys idx selected-keys)`,
   serialize the RAW server form (from ::mi5e/custom-items pre-expansion) for
   those keys, strip :db/id. Only used items, not the whole library.
3. DECODE (untrusted): decode-shared layers 1-4 already cover the new section.
   Add a layer-5 item shape gate: `:custom-items` must be a vector of maps each
   valid against `::mi5e/magic-item` (magic_items.cljc:41-51); drop the rest,
   fail-closed, report drop-count.
4. SANITIZE (layer 6): custom items aren't a content-type so
   valid-item-for-load?/salvage don't apply directly; add
   `(filter #(spec/valid? ::mi5e/magic-item %) items)` + name sanitize. Mandatory
   (item defs are as untrusted as homebrew).
5. APPLY: extend `::e5/apply-shared-content` (events.cljs) to also
   `(assoc db :shared-custom-items items)`; clear it where `:shared-plugins` is
   cleared (the :route handler char-page branch).
6. SEAM: change `::mi5e/expanded-custom-items` (equipment_subs.cljs:53-57) to
   subscribe `[::mi5e/custom-items]` AND a new `[::mi5e/shared-custom-items]`
   (reg-sub over :shared-custom-items), concatenating raw lists before
   expand-magic-items. Propagates to sorted-items, all by-key maps, template
   options/modifiers. Plus the two options.cljc direct-read patches above.
7. UI (optional): fold the item count into the existing shared-content banner.
   "Keep in my library" for items is a LARGER follow-up (needs the item-save
   API to persist to a logged-in user's server library) — defer; view-only
   resolution is the core win.

## Security

Item defs are fully untrusted (attacker controls the URL) — same threat model as
homebrew. `::mi5e/modifiers` are DATA (mod-cfg maps interpreted by the builder),
not code, and safe-read-edn forbids eval/unknown tags, so residual risk is
malformed/oversized content — covered by the size caps + the ::mi5e/magic-item
spec gate. Name collisions resolve last-wins via merge order, same as homebrew.

Net edits: one bundle section + item whitelist/spec gate (mirrors
whitelist-bundle), one expanded-custom-items sub change, one new
:shared-custom-items sub + db key, two options.cljc one-liners, reuse of the
existing decode/apply/clear/banner machinery. See
[[share-with-embedded-content]] and share-bundle-dependency-extraction.md.
