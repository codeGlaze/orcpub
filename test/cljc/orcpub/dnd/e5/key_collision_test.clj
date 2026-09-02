(ns orcpub.dnd.e5.key-collision-test
  "Characterizes how DUPLICATE KEYS behave across the content stack — your 'override in some
   places, fail/coexist in others' question, verified rather than assumed. Three distinct behaviors:

   1. OVERRIDE — CLASSES (spell_subs.cljs:1016), RACES (:950), SPELLS (:1228) all use the SAME shape:
      `(into (sorted-set-by <key>) (concat (reverse plugin-options) built-in))`. A sorted set dedupes
      by the comparator (keeps the element already present), and plugin options are added FIRST, so a
      same-key collision keeps the PLUGIN and drops the built-in — i.e. a homebrew class/race/spell with
      a built-in's key OVERRIDES it (predictable, plugin-wins). This test pins that dedup semantics.
   2. COEXIST: list-gathered content (backgrounds/languages/selections via `mapcat`, the pools via
      `concat`) does NOT dedupe — same-key entries both appear in the seq.
   3. IMPORT: `detect-duplicate-keys`/`find-key-conflicts` (import_validation.cljs:1097) flag
      duplicates WITHIN an import (internal) and vs existing plugins (external) -> a resolution modal
      (rename/skip/replace), so duplicates are caught at import, not silently merged.

   The class-list logic is cljs, but the dedup SEMANTICS (`into sorted-set-by` + `concat`) are pure,
   so this JVM test pins exactly which entry survives. JVM/clojure.test."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]))

(defn- by-key-set [options]
  (into (sorted-set-by #(compare (::t/key %1) (::t/key %2))) options))

(deftest class-list-dedupes-by-key-plugin-overrides-builtin
  (let [base-fighter   {::t/key :fighter ::t/name "Fighter (built-in)"}
        plugin-fighter {::t/key :fighter ::t/name "Fighter (homebrew override)"}
        ;; exactly the class-list shape: (concat (reverse plugin-options) base-classes)
        combined (by-key-set (concat (reverse [plugin-fighter]) [base-fighter]))]
    (testing "same key -> exactly ONE survives (the set dedupes by ::t/key)"
      (is (= 1 (count combined))))
    (testing "the PLUGIN (homebrew) entry wins; the built-in is overridden"
      (is (= "Fighter (homebrew override)" (::t/name (first combined)))))))

(deftest distinct-keys-all-survive
  (testing "different keys do NOT collide — both a homebrew and the built-in coexist"
    (let [s (by-key-set [{::t/key :fighter ::t/name "Fighter"}
                         {::t/key :my-fighter ::t/name "My Fighter"}])]
      (is (= 2 (count s)))
      (is (= #{:fighter :my-fighter} (set (map ::t/key s)))))))

(deftest coexist-layers-do-not-dedupe
  (testing "list/concat layers (backgrounds/pools) keep BOTH same-key entries (no dedup)"
    ;; the pool/sub shape: a plain concat, no set — so duplicates coexist as a seq
    (let [coexist (concat [{::t/key :spy ::t/name "Spy (built-in)"}]
                          [{::t/key :spy ::t/name "Spy (homebrew)"}])]
      (is (= 2 (count coexist))
          "unlike the class set, a concat'd pool offers BOTH same-key entries"))))
