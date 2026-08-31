(ns orcpub.starting-equipment-ledger-test
  "The override ledger's pure engine: derive a minimal diff of an edited starting-equipment
   form against an SRD base, and resolve base + diff back to the full form. The load-bearing
   property is the round-trip — resolve(base, derive(base, edited)) reproduces edited — over
   every kind of edit, plus fail-soft resolution and diff minimality."
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.dnd.e5.starting-equipment-ledger :as ledger]
            [orcpub.dnd.e5.srd-starting-equipment :as srd]))

;; A compact hand base (also exercised against a real SRD base below).
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

;; Normal form: order-insensitive (groups/options by name), and an empty fixed bucket is
;; the same as an absent one (the app drops blank equipment keys on save, so both mean "no
;; weapons"). Lets the round-trip compare on meaning, not incidental empties/order.
(defn- norm [eq]
  (as-> eq $
    (reduce (fn [m b] (if (empty? (get m b)) (dissoc m b) m)) $ [:weapons :armor :equipment])
    (cond-> $
      (:equipment-selections $)
      (update :equipment-selections
              (fn [groups]
                (->> groups
                     (sort-by :name)
                     (mapv (fn [g] (update g :options #(vec (sort-by :name %)))))))))))

(defn- round-trips [base edited]
  (let [{:keys [equipment warnings]} (ledger/resolve-ledger base (ledger/derive-ledger base edited))]
    (is (empty? warnings) "no warnings on a clean derive/resolve")
    (is (= (norm edited) (norm equipment)))))

(deftest identity-round-trip
  (testing "no edits -> empty ledger, base resolves to itself"
    (is (= [] (ledger/derive-ledger base base)))
    (is (= (norm base) (norm (:equipment (ledger/resolve-ledger base [])))))))

(deftest fixed-grant-edits
  (testing "add a fixed weapon"    (round-trips base (assoc-in base [:weapons :dagger] 2)))
  (testing "remove a fixed weapon" (round-trips base (update base :weapons dissoc :javelin)))
  (testing "change a fixed qty"    (round-trips base (assoc-in base [:weapons :javelin] 6)))
  (testing "remove the only item in a bucket drops the bucket"
    (let [edited (update base :equipment dissoc :explorers-pack)]
      (round-trips base edited)
      (is (not (contains? (:equipment (ledger/resolve-ledger base (ledger/derive-ledger base edited)))
                          :equipment))))))

(deftest group-edits
  (testing "rename a group"
    (round-trips base (assoc-in base [:equipment-selections 0 :name] "Body Armor")))
  (testing "add a group"
    (round-trips base (update base :equipment-selections conj
                              {:name "Pack" :options [{:name "Explorer's Pack"
                                                       :grants [{:kind :equipment :key :explorers-pack}]}]})))
  (testing "remove a group"
    (round-trips base (update base :equipment-selections (comp vec butlast)))))

(deftest option-edits
  (testing "add an option to a group"
    (round-trips base (update-in base [:equipment-selections 0 :options] conj
                                 {:name "Scale Mail" :grants [{:kind :armor :key :scale-mail}]})))
  (testing "remove an option"
    (round-trips base (update-in base [:equipment-selections 0 :options] (comp vec butlast))))
  (testing "replace an option's grants (incl. a quantity-only change)"
    (round-trips base (assoc-in base [:equipment-selections 0 :options 1 :grants]
                                [{:kind :armor :key :leather}
                                 {:kind :weapon :key :longbow}
                                 {:kind :equipment :key :arrow :qty 40}])))
  (testing "replace an option's sub-choice"
    (round-trips base (assoc-in base [:equipment-selections 1 :options 0 :choose]
                                [{:name "Simple Weapon" :from :simple}]))))

(deftest minimal-diff
  (testing "editing one option yields a tiny ledger, not a full copy"
    (let [edited (assoc-in base [:equipment-selections 0 :options 0 :name] "Chain Mail (heavy)")
          ops    (ledger/derive-ledger base edited)]
      ;; a rename changes the option's minted key: one option removed + one added
      (is (= 2 (count ops)) "just the two option ops")
      (is (every? #(= :option (get-in % [:path 2])) ops)))))

(deftest fail-soft-resolution
  (testing "an op targeting a group absent from the base is skipped and surfaced"
    (let [stale [{:op :replace :path [:group :ghost-group :name] :value "X"}]
          {:keys [equipment warnings]} (ledger/resolve-ledger base stale)]
      (is (= 1 (count warnings)) "one warning for the unresolvable op")
      (is (= (norm base) (norm equipment)) "base is preserved, nothing corrupted"))))

(deftest against-real-srd-base
  (testing "a real SRD base (fighter) round-trips after a tweak"
    (let [fighter (srd/builder-equipment :fighter)
          ;; swap in an extra fixed weapon and rename a group
          edited  (-> fighter
                      (assoc-in [:weapons :dagger] 1)
                      (assoc-in [:equipment-selections 0 :name] "Starting Armor"))]
      (round-trips fighter edited)
      (is (seq (ledger/derive-ledger fighter edited)) "a real edit produces a non-empty ledger")
      (is (< (count (ledger/derive-ledger fighter edited))
             (count (:equipment-selections fighter)))
          "the ledger is smaller than shipping the whole selections vector"))))
