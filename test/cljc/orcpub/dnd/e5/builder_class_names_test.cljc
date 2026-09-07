(ns orcpub.dnd.e5.builder-class-names-test
  "Gate on CSS class names introduced by the builder-form framework.

   styles/core.clj is 3,000+ lines of utility classes, and a generic name silently inherits whatever
   the app already defines for it: naming the field wrapper `field` picked up a global
   `.field {margin-top:30px}`, which gave every declarative field 30px it never asked for and turned
   the two-toggle column into a 106px box holding two 16px rows. Nothing failed — the form rendered,
   it just spaced wrong.

   So: every class the framework renders must be prefixed, or listed here deliberately. This is a
   characterization gate, not a style rule — adding a name is one line, and the point is that it is
   a decision rather than an accident."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def ^:private prefixes
  #{"bf-"            ; the builder-form framework's own
    "opt-"           ; ported from option_menu_views (OMV)
    "select-menu"})  ; ditto

(def ^:private allowed
  "Generic names inside the block that are NOT prefixed. Each is here on purpose:

   - chip / tag families and the effect-row family are the framework's own generic names. They do
     not collide with anything today — checked against port/redesign-on-refactor too, which
     namespaces its own as opt-* and select-menu-*. They stay on this list as a standing reminder
     that they are the risky kind of name.
   - `field` and `input` are NOT definitions. They are REFERENCES, scoped inside `.opt-section`,
     neutralising the app-global `.field {margin-top:30px}` within a card — the same global that
     caused the collision this test exists for. Ported from OMV, which had to do exactly the same."
  #{"chip" "chip-toggle" "chip-row" "tag" "tags" "tag-label"
    "effect-row" "effect-row-body" "effect-row-header" "when-label" "row-lead-num"
    "form-head" "form-col"
    "field" "input"})

(defn- builder-classes
  "Class names the builder renderer emits, read out of the stylesheet block that defines them.
   Bounded by the BUILDER-FORM CSS sentinels in styles/core.clj so unrelated app CSS is not swept
   in. If they move, this test fails loudly rather than quietly checking nothing."
  []
  (let [src   (slurp (io/file "src/clj/orcpub/styles/core.clj"))
        start (str/index-of src "BUILDER-FORM CSS: START")
        end   (str/index-of src "BUILDER-FORM CSS: END")]
    (when (and start end)
      (->> (re-seq #"\[:\.([a-z][a-z0-9-]*)" (subs src start end))
           (map second)
           set))))

(deftest builder-css-classes-are-namespaced-or-listed
  (let [classes (builder-classes)]
    (is (some? classes)
        "could not locate the builder CSS block — its marker comments moved; fix this test's bounds")
    (doseq [c (sort classes)]
      (is (or (some #(str/starts-with? c %) prefixes)
              (contains? allowed c))
          (str "CSS class \"" c "\" is neither prefixed (" (str/join ", " (sort prefixes))
               ") nor on this test's allow-list. A generic name inherits whatever styles/core.clj "
               "already defines for it, and the failure is silent. Prefix it, or add it to `allowed` "
               "on purpose — see docs/kb/before-you-start.md.")))))

(deftest the-allow-list-does-not-rot
  (testing "a name kept on the allow-list should still be rendered; drop it when it is not"
    (let [classes (builder-classes)
          unused  (remove classes allowed)]
      (is (empty? unused)
          (str "on the allow-list but no longer in the builder CSS: " (pr-str (vec unused))
               " — remove them so the list keeps meaning something")))))
