(ns orcpub.dnd.e5.extensibility-golden-test
  "Phase 0 safety net for the content-extensibility work
   (see docs/kb/content-extensibility-plan.md).

   These tests lock down the backward-compatibility invariants that the upcoming
   registry / catalog refactors must not break (docs/kb/content-extensibility-compatibility.md):

     1. Homebrew/content keys derive from names via `common/name-to-kw`, and that
        derivation is stable. Every saved character and every .orcbrew entry
        references content by these keys, so a change here orphans user data.

     2. A saved character (strict entity) survives a load -> save round-trip with
        its chosen selection/option keys intact.

   Pure JVM test (clojure.test) so it runs under the enforced `lein test` gate.
   No plugin/template/re-frame context is required — `from-strict`/`to-strict`
   are pure structural transforms."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.walk :as walk]
            [orcpub.common :as common]
            [orcpub.dnd.e5.character :as char5e]))

;; ---------------------------------------------------------------------------
;; Invariant 1 — key derivation is stable (the linchpin of all compatibility)
;; ---------------------------------------------------------------------------

(deftest homebrew-key-derivation-is-stable
  (testing "name-to-kw maps content names to the keys saved data references"
    (is (= :pact-boon            (common/name-to-kw "Pact Boon")))
    (is (= :shadow-dwarf         (common/name-to-kw "Shadow Dwarf")))
    (is (= :mountain-dwarf       (common/name-to-kw "Mountain Dwarf")))
    (is (= :pact-of-the-undying  (common/name-to-kw "Pact of the Undying")))
    (is (= :draconic-ancestry    (common/name-to-kw "Draconic Ancestry"))))
  (testing "apostrophes are stripped and runs of non-word chars collapse to one dash"
    (is (= :mariners-armor       (common/name-to-kw "Mariner's Armor")))
    (is (= :book-of-secrets      (common/name-to-kw "Book   of  Secrets")))))

;; ---------------------------------------------------------------------------
;; Invariant 2 — a saved character round-trips with its keys intact
;; ---------------------------------------------------------------------------

;; A representative saved character (strict entity, the on-disk / localStorage /
;; DB shape) that references content via selection/option keys: a Dwarf with a
;; (homebrew) subrace, and a Warlock who chose a (homebrew) pact boon. These are
;; exactly the cross-links the refactor touches.
(def saved-character
  #:orcpub.entity.strict
  {:selections
   [#:orcpub.entity.strict
    {:key :race
     :option #:orcpub.entity.strict
              {:key :dwarf
               :selections [#:orcpub.entity.strict
                            {:key :subrace
                             :option #:orcpub.entity.strict{:key :shadow-dwarf}}]}}
    #:orcpub.entity.strict
    {:key :class
     :options [#:orcpub.entity.strict
               {:key :warlock
                :selections [#:orcpub.entity.strict
                             {:key :pact-boon
                              :option #:orcpub.entity.strict{:key :pact-of-the-undying}}]}]}]})

(defn- strict-keys
  "Every ::strict/key appearing anywhere in a strict entity."
  [strict]
  (let [ks (atom #{})]
    (walk/postwalk
     (fn [x]
       (when (and (map-entry? x) (= :orcpub.entity.strict/key (key x)))
         (swap! ks conj (val x)))
       x)
     strict)
    @ks))

(deftest saved-character-round-trip-is-stable
  (let [once  (-> saved-character char5e/from-strict char5e/to-strict)
        twice (-> once char5e/from-strict char5e/to-strict)]
    (testing "load -> save is idempotent (serialization is stable)"
      (is (= once twice)))
    (testing "every chosen selection/option key survives the round-trip"
      (let [survived (strict-keys once)]
        (doseq [k [:race :dwarf :subrace :shadow-dwarf
                   :class :warlock :pact-boon :pact-of-the-undying]]
          (is (contains? survived k)
              (str "key " k " must survive load/save")))))))
