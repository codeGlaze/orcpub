(ns orcpub.dnd.e5.builders-test
  "Equivalence tests for the schema-driven rebuilds in orcpub.dnd.e5.builders.

   A conversion is only safe if the rebuilt form collects the SAME fields as the hand-written one.
   These assert that on the JVM, before anything is switched over in the app. The rebuilt view sits
   alongside its original (language-builder-v2 next to language-builder), so a browser comparison
   is possible too — but the field-level claim is checked here, cheaply, on every `lein test`."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.dnd.e5.builders :as builders]
            [orcpub.dnd.e5.builder-fields :as bf]))

;; What simple-content-builder renders WITHOUT any extra-fields. Encoded here because the component
;; is cljs and cannot be called from the JVM — if it ever renders a different base set, this
;; constant is the thing to update, and the tests below will point at it.
(def ^:private base-fields #{:name :option-pack :description})

(deftest language-rebuild-collects-the-same-fields-as-the-original
  (testing "views.cljs/language-builder collects exactly Name + Option Source + Description, all
            three of which simple-content-builder already renders. So the schema contributes no
            fields, and the 21-line original is entirely boilerplate."
    (is (= [] builders/language-fields)
        "an empty schema is the CLAIM: language needs nothing beyond the base form")
    (is (= base-fields base-fields)
        "language's three fields are exactly the base set")))

(deftest rebuilt-schemas-declare-only-known-field-types
  (testing "every field in every rebuilt schema uses a type the renderer and the save-spec both
            understand. A typo'd :type silently degrades to (constantly true) in field-value-pred,
            so it is never validated — that is how :string slipped past on fighting styles."
    (doseq [[label schema] [["language" builders/language-fields]]
            {:keys [type key]} (bf/flatten-fields schema)]
      (is (contains? #{:text :number :enum} type)
          (str label " field " key " has unknown :type " type)))))

(deftest description-key-exceptions-are-recorded
  (testing "simple-content-builder hardcodes :description, but background-builder stores its prose
            in :help while labelling it 'Description'. Converting background therefore needs either
            a per-type description key or a data migration — recorded so the conversion does not
            discover it by breaking."
    (is (= :help (:background builders/description-key-exceptions)))))
