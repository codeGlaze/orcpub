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

(def srd-key
  "The served file's key -> the app's key, where they differ (equipment-parity.md)."
  {:studded-leather :studded})

(defn dice-pair
  "Damage dice as [count die]: \"1d8\" is [1 8], a flat \"1\" is [1 1], nil or \"0\" is nil."
  [d]
  (if-let [[_ c n] (re-matches #"(\d+)d(\d+)" (or d ""))]
    [(parse-long c) (parse-long n)]
    (when-let [n (some-> (re-matches #"[1-9]\d*" (or d "")) parse-long)] [n 1])))

(def flag-fields
  "SRD property key -> the app's weapon flag field."
  {:finesse ::weapons/finesse? :heavy ::weapons/heavy? :light ::weapons/light? :loading ::weapons/loading?
   :reach ::weapons/reach :special ::weapons/special? :two-handed ::weapons/two-handed?
   :thrown ::weapons/thrown :ammunition ::weapons/ammunition?})

(defn srd-mechanics
  "A served SRD weapon's mechanics in the app's terms: a map of comparable fields."
  [{:keys [category damage properties]}]
  (let [props (into {} (map (juxt :key #(or (:detail %) true))) properties)
        category-name (name category)
        [_ lo hi] (re-find #"range (\d+)/(\d+)" (str (or (props :ammunition) (props :thrown) "")))]
    (merge {:type (keyword (first (s/split category-name #"-"))) :melee? (s/ends-with? category-name "melee")
            :ranged? (s/ends-with? category-name "ranged") :dice (dice-pair (:dice damage))
            :damage-type (when (dice-pair (:dice damage)) (:type damage))
            :range (when lo [(parse-long lo) (parse-long hi)])
            :versatile (dice-pair (props :versatile))}
           (into {} (for [[p _] flag-fields] [p (contains? props p)])))))

(defn app-mechanics
  "An app weapon's mechanics, field for field with srd-mechanics."
  [w]
  (merge {:type (::weapons/type w) :melee? (boolean (::weapons/melee? w)) :ranged? (boolean (::weapons/ranged? w))
          :dice (when (::weapons/damage-die w) [(::weapons/damage-die-count w) (::weapons/damage-die w)])
          :damage-type (when (::weapons/damage-die w) (::weapons/damage-type w))
          :range (some-> (::weapons/range w) ((juxt ::weapons/min ::weapons/max)))
          :versatile (some-> (::weapons/versatile w) ((juxt ::weapons/damage-die-count ::weapons/damage-die)))}
         (into {} (for [[p f] flag-fields] [p (boolean (get w f))]))))

(deftest weapon-mechanics-match-the-srd
  (let [app (into {} (map (juxt :key identity)) weapons/weapons)]
    (doseq [{:keys [key] :as e} (:weapons (equipment-file))]
      (testing (name key)
        (is (= (srd-mechanics e) (some-> (app (srd-key key key)) app-mechanics)))))))

(deftest armor-mechanics-match-the-srd
  (let [app (into {} (map (juxt :key identity)) armor/armor)]
    (doseq [{:keys [key category ac strength] :as e} (:armor (equipment-file))
            :let [a (app (srd-key key key))]]
      (testing (name key)
        (is (= category (:type a)))
        (is (= (:base ac) (:base-ac a)))
        (is (= (cond (nil? (:base ac)) nil (not (:dex? ac)) 0 :else (:max-dex ac)) (:max-dex-mod a)))
        (is (= strength (:min-str a)))
        (is (= (boolean (:stealth-disadvantage? e)) (boolean (:stealth-disadvantage? a))))))))

(deftest every-key-stays-in-its-order
  (testing "saved characters and lists depend on these keys, in this order"
    (is (= [:crossbow-light :dart :shortbow :sling :club :dagger :greatclub :handaxe :javelin
            :light-hammer :mace :quarterstaff :sickle :spear :battleaxe :flail :glaive :greataxe
            :greatsword :halberd :lance :longsword :maul :morningstar :pike :rapier :scimitar
            :shortsword :trident :war-pick :warhammer :whip :blowgun :crossbow-hand :crossbow-heavy
            :longbow :net :firearm-hand :firearm-burst :firearm-long]
           (mapv :key weapons/weapons)))
    (is (= [:shield :padded :leather :studded :hide :chain-shirt :scale-mail :breastplate :half-plate
            :ring-mail :chain-mail :splint :plate :spiked-armor]
           (mapv :key armor/armor)))))

(deftest the-apps-own-fields-stay
  (let [w (into {} (map (juxt :key identity)) weapons/weapons)
        a (into {} (map (juxt :key identity)) armor/armor)]
    (testing "subtype tags"
      (is (= {:greataxe :axe :greatsword :sword :longsword :sword :quarterstaff :staff
              :rapier :sword :scimitar :sword :shortsword :sword}
             (into {} (for [[k e] w :when (::weapons/subtype e)] [k (::weapons/subtype e)])))))
    (testing "the Net's short summary"
      (is (= "restrains" (:summary (w :net)))))
    (testing "the six armors keep their short names until the owner decides"
      (is (= ["Padded" "Leather" "Studded" "Hide" "Splint" "Plate"]
             (mapv (comp :name a) [:padded :leather :studded :hide :splint :plate]))))
    (testing "non-SRD entries stay"
      (is (every? w [:firearm-hand :firearm-burst :firearm-long]))
      (is (a :spiked-armor)))))
