(ns orcpub.dnd.e5.armor)

(def armor-types
  [:light :medium :heavy])

(def armor
  [{:name "Shield" :cost {:num 10 :type :gp} :weight 6
    :type :shield
    :key :shield}
   {:name "Padded" :cost {:num 5 :type :gp},
    :type :light,
    :base-ac 11,
    :stealth-disadvantage? true,
    :weight 8,
    :key :padded}
   {:name "Leather" :cost {:num 10 :type :gp},
    :type :light,
    :base-ac 11,
    :weight 10,
    :key :leather}
   {:name "Studded" :cost {:num 45 :type :gp},
    :type :light,
    :base-ac 12,
    :weight 13,
    :key :studded}
   {:name "Hide" :cost {:num 10 :type :gp},
    :type :medium,
    :base-ac 12,
    :max-dex-mod 2,
    :weight 12,
    :key :hide}
   {:name "Chain Shirt" :cost {:num 50 :type :gp},
    :type :medium,
    :base-ac 13,
    :max-dex-mod 2,
    :weight 20,
    :key :chain-shirt}
   {:name "Scale mail" :cost {:num 50 :type :gp},
    :type :medium,
    :base-ac 14,
    :max-dex-mod 2,
    :stealth-disadvantage? true,
    :weight 45,
    :key :scale-mail}
   {:name "Breastplate" :cost {:num 400 :type :gp},
    :type :medium,
    :base-ac 14,
    :max-dex-mod 2,
    :weight 20,
    :key :breastplate}
   {:name "Half plate" :cost {:num 750 :type :gp},
    :type :medium,
    :base-ac 15,
    :max-dex-mod 2,
    :stealth-disadvantage? true,
    :weight 40,
    :key :half-plate}
   {:name "Ring mail" :cost {:num 30 :type :gp},
    :type :heavy,
    :base-ac 14,
    :max-dex-mod 0,
    :stealth-disadvantage? true,
    :weight 40,
    :key :ring-mail}
   {:name "Chain mail" :cost {:num 75 :type :gp},
    :type :heavy,
    :base-ac 16,
    :max-dex-mod 0,
    :min-str 13,
    :stealth-disadvantage? true,
    :weight 55,
    :key :chain-mail}
   {:name "Splint" :cost {:num 200 :type :gp},
    :type :heavy,
    :base-ac 17,
    :max-dex-mod 0,
    :min-str 15,
    :stealth-disadvantage? true,
    :weight 60,
    :key :splint}
   {:name "Plate" :cost {:num 1500 :type :gp},
    :type :heavy,
    :base-ac 18,
    :max-dex-mod 0,
    :min-str 15,
    :stealth-disadvantage? true,
    :weight 65,
    :key :plate}
   {:name "Spiked Armor"
    :type :medium
    :base-ac 14
    :max-dex-mod 2
    :weight 45
    :stealth-disadvantage? true
    :key :spiked-armor}])

(def armor-map
  (zipmap (map :key armor) armor))

(defn non-shields [armor]
  (filter #(not= :shield (:type %)) armor))

(defn shields [armor]
  (filter #(= :shield (:type %)) armor))
