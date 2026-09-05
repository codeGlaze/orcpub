(ns orcpub.dnd.e5.builders
  "Schema-driven rebuilds of the builder forms, kept ALONGSIDE the hand-written originals in
  views.cljs so the two can be compared rather than swapped blind.

  Each entry here declares a type's form as data. A companion test asserts the schema collects the
  same fields the hand-written builder collects, so a conversion is provably equivalent before
  anything is switched over. Nothing here replaces its counterpart yet.

  Read docs/kb/builder-form-schemas.md first — it carries the node vocabulary, the HOW-TO recipes,
  and why triggers are sheet entries rather than conditions.

  WHY cljc and not cljs: a schema is data, so it can be checked on the JVM. The equivalence tests
  run in `lein test` rather than needing a browser.

  ── Conversion order (cheapest first, measured — see the doc's survey) ─────────────────────────
  language 21 · encounter 25 · background 46 · item 53 · feat 62 · selection 80 · spell 85 ·
  subclass 105 · subrace 129 · race 152 · monster 233 · class 268
  Class last: it is the only one with real conditional structure and bespoke selectors, so it is
  the case that tells us what the escape hatch actually needs."
  (:require [orcpub.dnd.e5.builder-fields :as bf]))

;; ── language ──────────────────────────────────────────────────────────────────────────────────
;; Original: views.cljs `language-builder`, 21 lines.
;; Collects exactly Name + Option Source + Description, which is precisely what
;; simple-content-builder already renders — so the schema adds NO fields of its own. The 21 lines
;; are pure boilerplate, and the conversion is a one-line call with an empty extra-fields.
;;
;; Kept as an explicit empty vector rather than nil so the equivalence test has something to assert
;; against, and so a later field has an obvious home.
(def language-fields
  [])

;; ── description key is NOT universal ──────────────────────────────────────────────────────────
;; simple-content-builder hardcodes :description. background-builder stores its prose in :help,
;; and it labels the field "Description" all the same. Any conversion of background must either
;; teach simple-content-builder which key to use, or migrate the data — neither is free, and
;; discovering it at conversion time is exactly why these are being rebuilt side by side.
(def ^:const description-key-exceptions
  {:background :help})
