(ns orcpub.starting-equipment-ledger-test
  "The lean starting-equipment delta: derive a small diff of an edited class against an SRD
   base (item-level fixed grants; touched choice groups stored whole), and resolve base +
   delta back to the full plain form. The load-bearing property is the round-trip; the other
   tests pin down that everyday tweaks produce a TINY delta, not a full copy."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.starting-equipment-ledger :as ledger]
            [orcpub.dnd.e5.srd-starting-equipment :as srd]))

(def base
  {:weapons {:javelin 4}
   :equipment {:explorers-pack 1}
   :equipment-selections
   [{:name "Armor"
     :options [{:name "Chain Mail" :grants [{:kind :armor :key :chain-mail}]}
               {:name "Leather + Longbow" :grants [{:kind :armor :key :leather}
                                                    {:kind :weapon :key :longbow}
                                                    {:kind :equipment :key :arrow :qty 20}]}]}
    {:name "Weapon"
     :options [{:name "A martial weapon and a shield"
                :grants [{:kind :armor :key :shield}]
                :choose [{:name "Martial Weapon" :from :martial}]}]}]})

;; Normal form: order-insensitive on groups; empty fixed bucket == absent (the app drops
;; blank equipment keys on save, so both mean "no weapons").
(defn- norm [eq]
  (as-> eq $
    (reduce (fn [m b] (if (empty? (get m b)) (dissoc m b) m)) $ [:weapons :armor :equipment])
    (cond-> $
      (:equipment-selections $)
      (update :equipment-selections #(vec (sort-by :name %))))))

(defn- round-trips
  "Deriving a delta from `original` to `edited` and resolving it back gives `edited`."
  [original edited]
  (is (= (norm edited)
         (norm (ledger/resolve-delta original (ledger/derive-delta original edited))))))

(deftest identity-round-trip
  (is (= {} (ledger/derive-delta base base)) "no edits -> empty delta")
  (is (= (norm base) (norm (ledger/resolve-delta base {})))))

(deftest fixed-grant-edits
  (testing "add a fixed weapon"    (round-trips base (assoc-in base [:weapons :dagger] 2)))
  (testing "remove a fixed weapon" (round-trips base (update base :weapons dissoc :javelin)))
  (testing "change a fixed qty"    (round-trips base (assoc-in base [:weapons :javelin] 6)))
  (testing "add to a brand-new bucket"
    (round-trips base (assoc base :armor {:shield 1})))
  (testing "remove the only item in a bucket drops the bucket"
    (let [edited (update base :equipment dissoc :explorers-pack)]
      (round-trips base edited)
      (is (not (contains? (ledger/resolve-delta base (ledger/derive-delta base edited)) :equipment))))))

(deftest group-edits
  (testing "change an option inside a group"
    (round-trips base (assoc-in base [:equipment-selections 0 :options 1 :grants]
                                [{:kind :armor :key :leather}
                                 {:kind :weapon :key :longbow}
                                 {:kind :equipment :key :arrow :qty 40}])))
  (testing "add an option to a group"
    (round-trips base (update-in base [:equipment-selections 0 :options] conj
                                 {:name "Scale Mail" :grants [{:kind :armor :key :scale-mail}]})))
  (testing "remove an option"
    (round-trips base (update-in base [:equipment-selections 0 :options] (comp vec butlast))))
  (testing "rename a group"
    (round-trips base (assoc-in base [:equipment-selections 0 :name] "Body Armor")))
  (testing "add a whole group"
    (round-trips base (update base :equipment-selections conj
                              {:name "Pack" :options [{:name "Explorer's Pack"
                                                       :grants [{:kind :equipment :key :explorers-pack}]}]})))
  (testing "remove a group"
    (round-trips base (update base :equipment-selections (comp vec butlast)))))

(deftest delta-is-small
  (testing "adding one item stores just that item — no groups, no copy"
    (let [delta (ledger/derive-delta base (assoc-in base [:weapons :dagger] 1))]
      (is (= {:fixed {:set {:weapons {:dagger 1}}}} delta))))
  (testing "editing one group stores only that group; the untouched group is absent"
    (let [edited (assoc-in base [:equipment-selections 0 :name] "Body Armor")
          delta  (ledger/derive-delta base edited)]
      (is (= #{"Body Armor"} (set (keys (get-in delta [:groups :set])))) "only the touched group")
      (is (nil? (:fixed delta)) "no fixed churn")
      (is (= #{"Armor"} (get-in delta [:groups :del])) "the renamed-away group removed"))))

(deftest against-real-srd-base
  (testing "a real SRD base (fighter) round-trips after a tweak, with a small delta"
    (let [fighter (srd/builder-equipment :fighter)
          edited  (-> fighter
                      (assoc-in [:weapons :dagger] 1)
                      (assoc-in [:equipment-selections 0 :name] "Starting Armor"))
          delta   (ledger/derive-delta fighter edited)]
      (round-trips fighter edited)
      (is (seq delta) "a real edit produces a non-empty delta")
      ;; the delta touches one added weapon + one renamed group, not the whole selections vector
      (is (= {:dagger 1} (get-in delta [:fixed :set :weapons]))))))

;; ---------------------------------------------------------------------------
;; Whole-class serialization boundary: collapse on export, expand on import.
;; ---------------------------------------------------------------------------

(deftest class-collapse-expand-round-trip
  (testing "a filled+edited class collapses to base+delta and expands back to the full form"
    (let [fighter (srd/builder-equipment :fighter)
          class   (merge {:name "Battle Sage" :key :battle-sage :hit-die 10
                          :starting-equipment-base :fighter}
                         fighter
                         {:weapons (assoc (:weapons fighter) :dagger 1)}) ; one tweak
          collapsed (ledger/collapse-class class)
          expanded  (ledger/expand-class collapsed)]
      ;; exported form: compact delta + base, none of the full equipment keys, no internal marker
      (is (= :fighter (get-in collapsed [:starting-equipment :base])))
      (is (nil? (:equipment-selections collapsed)) "full selections not written to the file")
      (is (nil? (:starting-equipment-base collapsed)) "internal marker not leaked to the file")
      (is (contains? (get-in collapsed [:starting-equipment :fixed :set :weapons]) :dagger))
      ;; expand restores the full equipment + base marker; unrelated fields survive both ways
      (is (= (norm (select-keys class [:weapons :armor :equipment :equipment-selections]))
             (norm (select-keys expanded [:weapons :armor :equipment :equipment-selections]))))
      (is (= :fighter (:starting-equipment-base expanded)))
      (is (= "Battle Sage" (:name expanded)) "unrelated fields preserved")
      (is (nil? (:starting-equipment expanded)) "delta key consumed on expand"))))

(deftest collapse-expand-noops
  (testing "no base marker -> collapse is a no-op"
    (is (= {:name "X" :weapons {:club 1}}
           (ledger/collapse-class {:name "X" :weapons {:club 1}}))))
  (testing "no :starting-equipment key -> expand is a no-op"
    (is (= {:name "X" :weapons {:club 1}}
           (ledger/expand-class {:name "X" :weapons {:club 1}}))))
  (testing "unknown base -> expand leaves the class untouched, never drops equipment"
    (let [c {:name "X" :starting-equipment {:base :not-a-class :fixed {:set {:weapons {:club 1}}}}}]
      (is (= c (ledger/expand-class c))))))

(deftest collapse-skips-legacy-shorthand
  (testing "a class still using legacy :*-choices is left in full form (delta doesn't model it)"
    (let [out (ledger/collapse-class {:name "L" :starting-equipment-base :fighter
                                      :weapon-choices [{:name "W" :options {:club 1}}]})]
      (is (nil? (:starting-equipment out)) "not collapsed")
      (is (= [{:name "W" :options {:club 1}}] (:weapon-choices out)) "legacy shorthand kept intact")
      (is (nil? (:starting-equipment-base out)) "internal marker dropped"))))
