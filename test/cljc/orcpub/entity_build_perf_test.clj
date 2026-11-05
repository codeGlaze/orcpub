(ns orcpub.entity-build-perf-test
  "Characterizes the topological sort inside `entity/build`, and pins the one property a
   rewrite of it must not break: the EXACT node order.

   Why the order is load-bearing: `apply-options` feeds `kahn-sort`'s result to
   `order-modifiers` (entity.cljc:419), which sorts the modifiers by their key's position
   in it. Two modifiers writing the same key are applied in that order, so a different —
   still valid — topological order can change a computed value. A perf change that reorders
   here is a behaviour change, not a speedup.

   The guard is `reference-kahn-sort` below: a verbatim copy of the implementation as it
   stood before the rewrite (entity.cljc @ c5f8b7a). The production `entity/kahn-sort` must
   return exactly `=` to it — vectors, so order-sensitive — on the real dependency graph a
   character build produces, on randomized DAGs, on cyclic graphs (both must give nil), and
   on the degenerate shapes.

   THIS FILE IS ONLY HALF THE PIN. It runs on the JVM, where a set's iteration order is a
   pure function of its contents. In ClojureScript a set of <= 8 elements is
   PersistentArrayMap-backed and iterates in INSERTION order — and the sort reads its
   frontier with (first s). A draft of the rewrite passed everything here and diverged on
   159 of 808 graphs in the browser. The cljs half is
   test/browser/kahn_sort_order_equivalence_e2e.js; re-run it whenever kahn-sort changes."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.template :as t5e]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.classes :as classes5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]
            [orcpub.dnd.e5.weapons :as weapons5e]
            [orcpub.common :as common]
            [clojure.set :refer [union difference intersection]]))

;; ---------------------------------------------------------------------------
;; The reference implementation — the pre-rewrite `kahn-sort`, kept verbatim.
;; Do not "clean up": its value is being an independent second opinion.
;; ---------------------------------------------------------------------------

(defn- ref-without [s x] (difference s #{x}))

(defn- ref-no-incoming [g]
  (let [nodes (set (keys g))
        have-incoming (apply union (vals g))]
    (difference nodes have-incoming)))

(defn- ref-normalize [g]
  (let [have-incoming (apply union (vals g))]
    (reduce #(if (get % %2) % (assoc % %2 #{})) g have-incoming)))

(defn reference-kahn-sort
  ([g] (reference-kahn-sort (ref-normalize g) [] (ref-no-incoming g)))
  ([g l s]
   (if (empty? s)
     (when (every? empty? (vals g)) l)
     (let [item (first s)
           s' (ref-without s item)
           m (g item)
           g' (reduce #(update-in % [item] ref-without %2) g m)]
       (recur g' (conj l item) (union s' (intersection (ref-no-incoming g') m)))))))

;; ---------------------------------------------------------------------------
;; The real graph: exactly what `apply-options` hands to `kahn-sort` for a real
;; multiclass character built from the SRD template. Built here rather than borrowed
;; from another test namespace so this file stands on its own.
;; ---------------------------------------------------------------------------

(def language-map (common/map-by-key [{:name "Common" :key :common}]))

(defn- class-opt [opt-fn]
  (opt-fn sl5e/spell-lists spells5e/spell-map {} language-map weapons5e/weapons-map))

(def test-template
  (t5e/template
   (t5e/template-selections
    nil nil nil weapons5e/weapons-map weapons5e/weapons
    sl5e/spell-lists spells5e/spell-map
    [] []
    [(class-opt classes5e/monk-option)
     (class-opt classes5e/barbarian-option)
     (class-opt classes5e/fighter-option)]
    [] language-map)))

(def abilities
  {:orcpub.dnd.e5.character/str 10 :orcpub.dnd.e5.character/dex 14
   :orcpub.dnd.e5.character/con 16 :orcpub.dnd.e5.character/int 10
   :orcpub.dnd.e5.character/wis 16 :orcpub.dnd.e5.character/cha 10})

(defn- level-1 [class-key]
  {::entity/key class-key
   ::entity/options {:levels [{::entity/key :level-1
                               ::entity/options
                               {:hit-points {::entity/key :average ::entity/value 4}}}]}})

(def test-entity
  {::entity/options
   {:ability-scores {::entity/key :standard-roll ::entity/value abilities}
    :class (mapv level-1 [:barbarian :monk])}})

(defn all-deps-for
  "Rebuilds `apply-options`' `all-deps` for a character — the graph kahn-sort receives."
  [raw-entity template]
  (let [options   (#'entity/flatten-options (::entity/options raw-entity))
        modifiers (sort-by :orcpub.modifiers/order
                           (#'entity/collect-modifiers-2 raw-entity options template))
        deps      (reduce (fn [m {:keys [:orcpub.modifiers/key :orcpub.modifiers/deps]}]
                            (if (seq deps) (update m key union deps) m))
                          {} modifiers)
        base      (merge (:orcpub.template/base template) (::entity/values raw-entity))]
    (merge-with union deps (:orcpub.entity-spec/deps base))))

(def real-graph (all-deps-for test-entity test-template))

;; ---------------------------------------------------------------------------
;; Randomized graphs. Seeded, so a failure is reproducible.
;; ---------------------------------------------------------------------------

(defn- rand-dag [n density seed]
  (let [r (java.util.Random. seed)
        nodes (mapv #(keyword (str "n" %)) (range n))]
    (into {} (for [i (range n)]
               [(nodes i)
                (into #{} (for [j (range (inc i) n) :when (< (.nextDouble r) density)]
                            (nodes j)))]))))

(defn- rand-cyclic [n seed]
  (let [r (java.util.Random. seed)
        nodes (mapv #(keyword (str "c" %)) (range n))]
    (into {} (for [i (range n)] [(nodes i) #{(nodes (.nextInt r n))}]))))

(def degenerate-graphs
  [{}                                   ; empty
   {:a #{}}                             ; one node, no edges
   {:a #{:b}}                           ; target absent from keys — normalize adds it
   {:a #{:a}}                           ; self-edge: cyclic
   {:a #{:b} :b #{:a}}                  ; 2-cycle
   {:a #{:b :c} :b #{:c}}               ; diamond-ish
   {:a #{:b} :c #{:d}}                  ; disconnected
   {:a #{:b :c :d} :b #{:d} :c #{:d} :d #{}}])

;; ---------------------------------------------------------------------------

(deftest kahn-sort-order-is-unchanged-on-the-real-graph
  (testing "the production sort returns the pre-rewrite order, node for node"
    (let [expected (reference-kahn-sort real-graph)
          actual   (entity/kahn-sort real-graph)]
      (println (format "\n[KAHN] real build graph: %d nodes, %d edges -> %d sorted nodes (hash %d)"
                       (count real-graph)
                       (reduce + (map count (vals real-graph)))
                       (count actual)
                       (hash actual)))
      (is (vector? actual) "the result is an ordered vector; order-modifiers indexes into it")
      (is (= expected actual)
          "kahn-sort must return the EXACT pre-rewrite order — order-modifiers turns this
           sequence into modifier application order, so any reordering is a behaviour change"))))

(deftest kahn-sort-order-is-unchanged-on-randomized-dags
  (testing "500 seeded DAGs of varying size and density"
    (let [mismatches (for [seed (range 500)
                           :let [g (rand-dag (+ 2 (mod seed 40))
                                             (/ (double (inc (mod seed 5))) 12.0)
                                             seed)]
                           :when (not= (reference-kahn-sort g) (entity/kahn-sort g))]
                       seed)]
      (is (empty? mismatches)
          (str "seeds whose sort order diverged from the reference: " (vec (take 10 mismatches)))))))

(deftest cyclic-graphs-still-return-nil
  (testing "a cycle makes kahn-sort return nil — the caller relies on that, not on a partial order"
    (is (nil? (entity/kahn-sort {:a #{:a}})) "self-edge")
    (is (nil? (entity/kahn-sort {:a #{:b} :b #{:a}})) "2-cycle")
    (let [mismatches (for [seed (range 300)
                           :let [g (rand-cyclic (+ 2 (mod seed 30)) seed)]
                           :when (not= (reference-kahn-sort g) (entity/kahn-sort g))]
                       seed)]
      (is (empty? mismatches)
          (str "seeds where cycle handling diverged: " (vec (take 10 mismatches)))))))

(deftest kahn-sort-matches-the-reference-on-degenerate-shapes
  (testing "empty, single node, absent target, self-edge, disconnected"
    (doseq [g degenerate-graphs]
      (is (= (reference-kahn-sort g) (entity/kahn-sort g)) (pr-str g)))))

(deftest sorted-order-is-a-valid-topological-order
  (testing "an independent check that does not lean on the reference: every edge points forward"
    (let [order (entity/kahn-sort real-graph)
          pos   (zipmap order (range))]
      (is (= (count order) (count (#'entity/normalize real-graph)))
          "every node in the normalized graph appears exactly once")
      (is (empty? (for [[from tos] real-graph, to tos
                        :when (>= (pos from) (pos to))]
                    [from to]))
          "u -> v implies u comes before v"))))
