(ns orcpub.dnd.e5.srd-equipment-test
  "The app's SRD weapons and armor carry the costs and weights of the SRD 5.1 equipment tables,
   as recorded in the served file resources/public/srd/2014/equipment.edn."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [orcpub.common :as common]
            [orcpub.dnd.e5.weapons :as weapons]
            [orcpub.dnd.e5.armor :as armor]))

(defn equipment-file
  "The served 2014 equipment file, parsed, or nil when absent."
  []
  (some-> (io/resource "public/srd/2014/equipment.edn") slurp edn/read-string))

(defn pounds
  "A weight as the app writes it (\"2 lb.\", \"1/4 lb.\", \"—\", or a number) -> a number of pounds."
  [w]
  (cond
    (number? w) w
    (or (nil? w) (= "—" w)) 0
    :else (let [[_ a b] (re-find #"(\d+)(?:/(\d+))?" w)]
            (if b (/ (parse-long a) (parse-long b)) (parse-long a)))))

(defn by-name
  "Indexes `xs` by a name reduced to its letters, lower-cased, so \"Chain Shirt\" meets \"Chain shirt\"."
  [xs]
  (into {} (map (juxt #(s/replace (common/ascii-lower-case (:name %)) #"[^a-z]" "") identity)) xs))

(deftest weapons-carry-srd-cost-and-weight
  (let [srd (:weapons (equipment-file))
        app (by-name weapons/weapons)]
    (is (= 37 (count srd)))
    (doseq [{:keys [name cost weight]} srd
            :let [w (app (s/replace (common/ascii-lower-case name) #"[^a-z]" ""))]]
      (testing name
        (is (some? w) "the app has this SRD weapon")
        (is (= cost (:cost w)))
        (is (== weight (pounds (:weight w))))))))

(deftest armor-carries-srd-cost-and-weight
  (let [srd (by-name (map #(update % :name (fn [n] (s/replace n #"(?i) armor$" ""))) (:armor (equipment-file))))
        app (by-name armor/armor)]
    (is (= 13 (count srd)))
    (doseq [[k {:keys [name cost weight]}] srd
            :let [a (or (app k) (app (s/replace k #"leather$" "")))]]
      (testing name
        (is (some? a) "the app has this SRD armor")
        (is (= cost (:cost a)))
        (is (== weight (pounds (:weight a))))))))
