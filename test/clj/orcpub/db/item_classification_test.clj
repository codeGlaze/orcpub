(ns orcpub.db.item-classification-test
  "The backfill's whole reason to exist is that it can be pointed at a decade
   of user content without anyone having to hold their breath. These tests pin
   down the properties that make that true: additive only, idempotent, and
   silent where it isn't sure."
  (:require [clojure.test :refer [deftest testing is]]
            [datomic.api :as d]
            [datomock.core :as dm]
            [orcpub.db.schema :as schema]
            [orcpub.db.item-classification :as ic]
            [orcpub.dnd.e5.magic-items :as mi5e]))

(def ^:private wand
  {:db/id 1 ::mi5e/name "Wand of Sparks" ::mi5e/type :wand ::mi5e/owner "kaylee"})

(def ^:private rope
  ;; No rarity at all: mundane gear has none in 5e, and that absence is what
  ;; the mundane inference now requires.
  {:db/id 2 ::mi5e/name "Silk Rope" ::mi5e/type :other ::mi5e/owner "kaylee"})

(def ^:private common-trinket
  ;; A weapon with :common and nothing else. This used to be inferred mundane;
  ;; a Moon-Touched Sword has exactly this shape, so it goes to its owner now.
  {:db/id 5 ::mi5e/name "Glimmering Blade" ::mi5e/type :weapon
   ::mi5e/rarity :common ::mi5e/owner "kaylee"})

(def ^:private ambiguous
  {:db/id 3 ::mi5e/name "Old Trinket" ::mi5e/type :wondrous-item ::mi5e/rarity :common ::mi5e/owner "kaylee"})

(def ^:private already-classified
  {:db/id 4 ::mi5e/name "Settled" ::mi5e/type :wondrous-item ::mi5e/magical? false ::mi5e/owner "kaylee"})

(deftest tx-writes-only-assertions-of-the-new-attribute
  (let [tx (ic/classification-tx [wand rope])]
    (is (= 2 (count tx)))
    (testing "every datom is a plain assertion, never a retraction"
      (is (every? map? tx))
      (is (not-any? vector? tx)))
    (testing "each touches exactly the id and the new attribute — nothing else"
      (is (every? #(= #{:db/id ::mi5e/magical?} (set (keys %))) tx)))
    (testing "and carries the classification the item already had"
      (let [by-id (into {} (map (juxt :db/id ::mi5e/magical?)) tx)]
        (is (true? (get by-id 1)))
        (is (false? (get by-id 2)))))))

(deftest tx-never-second-guesses-a-stored-answer
  (testing "an item that already carries the flag contributes nothing"
    (is (empty? (ic/classification-tx [already-classified])))))

(deftest tx-stays-silent-on-items-it-cannot-place
  (testing "an unclassifiable legacy item is left alone rather than guessed at"
    (is (empty? (ic/classification-tx [ambiguous])))))

(deftest tx-skips-items-with-no-id
  (testing "nothing to assert against, so nothing is emitted"
    (is (empty? (ic/classification-tx [(dissoc wand :db/id)])))))

(deftest tx-is-idempotent
  (testing "re-running over items that now carry their flag is a no-op"
    (let [tx (ic/classification-tx [wand rope ambiguous])
          healed (map (fn [item]
                        (if-let [datom (first (filter #(= (:db/id item) (:db/id %)) tx))]
                          (merge item (select-keys datom [::mi5e/magical?]))
                          item))
                      [wand rope ambiguous])]
      (is (= 2 (count tx)))
      (is (empty? (ic/classification-tx healed))))))

(deftest report-counts-what-happened
  (let [report (ic/backfill-report [wand rope ambiguous already-classified])]
    (is (= 4 (:examined report)))
    (is (= 2 (:classified report)))
    (is (= 1 (:magical report)))
    (is (= 1 (:mundane report)))
    (testing "only the genuinely unplaceable item counts as left for review"
      ;; The already-classified item is untouched too, but it is settled, not
      ;; awaiting an answer — it must not be reported as outstanding work.
      (is (= 1 (:left-unreviewed report))))))

(deftest report-handles-an-empty-database
  (let [report (ic/backfill-report [])]
    (is (= 0 (:examined report)))
    (is (= 0 (:classified report)))
    (is (empty? (:tx report)))))

(deftest classification-matches-runtime-behaviour
  (testing "a backfilled item behaves identically to how it behaved before"
    ;; If these ever diverge, the backfill silently changes people's items.
    (doseq [item [wand rope ambiguous]]
      (let [datom (first (ic/classification-tx [item]))]
        (when datom
          (is (= (mi5e/magical? item) (::mi5e/magical? datom))
              (str (::mi5e/name item) " must keep behaving the same"))
          (is (= (mi5e/magical? item)
                 (mi5e/magical? (merge item (select-keys datom [::mi5e/magical?]))))))))))

(deftest a-common-item-with-no-evidence-is-left-for-its-owner
  (testing "the backfill does not resolve a rarity it cannot read"
    (is (empty? (ic/classification-tx [common-trinket])))
    (let [report (ic/backfill-report [common-trinket])]
      (is (= 0 (:classified report)))
      (is (= 1 (:left-unreviewed report))))))

(deftest backfill-swallows-an-error-not-just-an-exception
  (testing "an OutOfMemoryError cannot escape into component/start"
    ;; backfill! runs inside the Datomic component's start. Its own query
    ;; materialises every unclassified item at once, so on a large corpus the
    ;; realistic failure is an Error rather than an Exception -- and datomic/start
    ;; rethrows anything that escapes as :schema-initialization-failed, which
    ;; stops the server booting. An optional heal must never do that.
    (let [conn (dm/mock-conn)]
      @(d/transact conn schema/all-schemas)
      (with-redefs [ic/unclassified-items
                    (fn [_] (throw (OutOfMemoryError. "simulated heap exhaustion")))]
        (let [result (ic/backfill! conn)]
          (is (map? result) "it returns rather than throwing")
          (is (= "simulated heap exhaustion" (:error result))))))))

(deftest backfill-pulls-only-what-classification-reads
  (testing "the query does not drag descriptions and modifier bodies into heap"
    ;; A pull of [*] over every unclassified item is the thing most likely to
    ;; exhaust memory here, and nothing downstream reads those attributes.
    (let [pattern (-> #'ic/unclassified-items meta :doc)]
      (is (some? pattern)))
    (let [src (slurp "src/clj/orcpub/db/item_classification.clj")
          query-region (subs src
                             (.indexOf src "defn unclassified-items")
                             (.indexOf src "defn classification-tx"))]
      (is (not (.contains query-region "(pull ?e [*])"))
          "unclassified-items must not pull [*]")
      (is (.contains query-region "magic-items/rarity")
          "but must still pull what classify reads"))))
