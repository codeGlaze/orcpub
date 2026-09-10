;; ============================================================================
;; NAMESPACE COLLISION — READ THIS BEFORE "DISCOVERING" IT AGAIN
;; ----------------------------------------------------------------------------
;; This file AND `common_test.cljc` (same dir) both declare `orcpub.common-test`.
;; Clojure resolves `.clj` before `.cljc`, so under `lein test` (clj/cljc) ONLY
;; THIS FILE loads — the .cljc's ~14 deftests (aloof-sort-by, name-to-kw guard,
;; sanitize-edn-colons, cljs.reader round-trips) are SHADOWED and DO NOT RUN
;; here. That is why `lein test orcpub.common-test` reports just 1 test.
;;
;; The .cljc suite runs under the CLJS test runner (`test_runner.cljs` requires
;; `orcpub.common-test`; in cljs there is no .clj to shadow it) via `lein fig:test`.
;; So both suites DO get exercised — just by different runners. This is expected,
;; not a bug. To make `lein test` cover the .cljc too, fold these into the .cljc
;; and delete this file. Until then: don't re-file this as a mystery.
;; ============================================================================
(ns orcpub.common-test
  (:require [clojure.test :refer [is deftest testing]]
            [clojure.spec.test.alpha :as stest]
            [orcpub.common :as common]))

(deftest test-add-namespaces-to-keys
  (stest/instrument `common/add-namespaces-to-keys)
  (is (= {:db/id 88
          :a.b.c/x 1
          :a.b.c/y "sdlk"}
         (common/add-namespaces-to-keys "a.b.c" {:db/id 88
                                                 :x 1
                                                 :y "sdlk"})))
  (stest/instrument `common/add-namespaces-to-keys))

;; Added here rather than in the .cljc because of the namespace collision noted
;; above: only this file loads under `lein test`.

(deftest name-to-kw-drops-a-trailing-separator
  (testing "a dangling separator carries no meaning and is dropped"
    (is (= :eladrin-cha (common/name-to-kw "Eladrin (Cha)")))
    (is (= :samurai (common/name-to-kw "Samurai ")))
    (is (= :fire-bolt (common/name-to-kw "Fire Bolt!"))))

  (testing "a LEADING separator is kept -- it is how junk names are detected"
    ;; Sanitising these would let them pass keyword-starts-with-letter? and defeat
    ;; the keyword-trap machinery. See name-to-kw-does-not-sanitise-leading-non-letter.
    (is (= :-baz (common/name-to-kw "  Baz")))
    (is (= :-plus (common/name-to-kw "++Plus")))
    (is (= :-foo (common/name-to-kw "-Foo"))))

  (testing "a name that is ALL separators keeps its dash rather than becoming a placeholder"
    ;; "@@@" reduces to "-"; stripping that would empty it, trip the blank-name
    ;; placeholder, and yield :unnamed-<hash>, which starts with a letter and so
    ;; would PASS the trap check.
    (is (= :- (common/name-to-kw "@@@")))
    (is (not (common/keyword-starts-with-letter? (common/name-to-kw "@@@")))))

  (testing "interior structure untouched"
    (is (= :artificer (common/name-to-kw "Artificer")))
    (is (= :mages-hand (common/name-to-kw "Mage's Hand")))))

(deftest canonical-key-matches-old-and-new-forms
  (testing "a key stored before the trim reduces to the form derived after it"
    (is (= :dark-elf-drow (common/canonical-key :dark-elf-drow-)))
    (is (= (common/canonical-key :dark-elf-drow-)
           (common/canonical-key (common/name-to-kw "Dark Elf (Drow)")))))

  (testing "it removes ONLY the trailing separator"
    ;; Removing more would match content that was never related.
    (is (= :fire-bolt (common/canonical-key :fire-bolt)))
    (is (not= (common/canonical-key :fire-bolt) (common/canonical-key :firebolt)))
    (is (= :-baz (common/canonical-key :-baz))))

  (testing "an all-separator key is left alone, so they do not all collapse together"
    (is (= :- (common/canonical-key :-))))

  (testing "namespace is preserved"
    (is (= :a/b (common/canonical-key :a/b-))))

  (testing "nil for a non-keyword"
    (is (nil? (common/canonical-key "not-a-keyword")))))
