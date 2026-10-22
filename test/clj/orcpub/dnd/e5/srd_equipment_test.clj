(ns orcpub.dnd.e5.srd-equipment-test
  "The app's SRD weapons and armor match the SRD 5.1 equipment tables as recorded in the served
   file resources/public/srd/2014/equipment.edn (costs, weights, ammunition flags), and a weapon
   with no damage dice (the Net) shows as dealing no damage."
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [clojure.string :as s]
            [clojure.walk :as walk]
            [orcpub.common :as common]
            [orcpub.dnd.e5.display :as disp]
            [orcpub.pdf-spec :as pdf]
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

(deftest weapon-categories-follow-the-srd-table
  (testing "thrown, yet listed under the ranged weapons"
    (let [by-key (into {} (map (juxt :key :category)) (:weapons (equipment-file)))]
      (is (= :simple-ranged (by-key :dart)))
      (is (= :martial-ranged (by-key :net)))
      (is (= :simple-melee (by-key :javelin)) "a thrown melee weapon stays melee"))))

(deftest ammunition-weapons-are-flagged
  (let [by-key (into {} (map (juxt :key identity)) weapons/weapons)]
    (doseq [{:keys [key properties]} (:weapons (equipment-file))
            :when (some #(= :ammunition (:key %)) properties)]
      (testing (name key)
        (is (::weapons/ammunition? (by-key key)) "an SRD ammunition weapon carries the app's ammunition flag")))))

(defn rules-text
  "The served 2014 rules as one string: every text node, spaces collapsed, so a sentence split by
   a link (\"is \" [:a ... \"restrained\"] \" until\") reads whole."
  []
  (let [strs (atom [])]
    (walk/postwalk #(do (when (string? %) (swap! strs conj %)) %)
                   (some-> (io/resource "public/srd/2014/rules.edn") slurp edn/read-string))
    (s/replace (s/join " " @strs) #"\s+" " ")))

(deftest a-weapon-without-damage-shows-none
  (let [net (weapons/weapons-map :net)
        sword (weapons/weapons-map :longsword)
        attack-text #(disp/attack-description (assoc % :attack-type :ranged :attack-modifier 5 :damage-modifier 2))
        pdf-damage #(pdf/damage-str (::weapons/damage-die %) (::weapons/damage-die-count %) 2 (::weapons/damage-type %) (:summary %))]
    (testing "the Net: no dice, so no damage and no modifier"
      (is (not (s/includes? (attack-text net) "d+")))
      (is (s/includes? (attack-text net) "no damage")))
    (testing "small spaces get the short summary"
      (is (<= (count (:summary net)) 12) "fits the table row and the PDF box")
      (is (s/includes? (attack-text net) (:summary net)))
      (is (= (str "None (" (:summary net) ")") (pdf-damage net))))
    (testing "the details get the SRD's own words"
      (is (seq (::weapons/description net)))
      (is (s/includes? (rules-text) (::weapons/description net)) "verbatim from the served SRD rules"))
    (testing "a weapon with dice is unchanged"
      (is (s/includes? (attack-text sword) "1d8+2 slashing damage"))
      (is (= "1d8+2 slashing" (pdf-damage sword))))
    (testing "a magic weapon's own damage type replaces the base one (the Sun Blade)"
      (let [sun-blade (assoc sword :orcpub.dnd.e5.magic-items/magical-damage-type :radiant)]
        (is (s/includes? (attack-text sun-blade) "1d8+2 radiant damage"))))))

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
