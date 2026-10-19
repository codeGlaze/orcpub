(ns orcpub.dnd.e5.armor
  (:require [orcpub.dnd.e5.srd-equipment :as srd]))

(def armor-types
  [:light :medium :heavy])

(def app-fields
  "The app's own fields on SRD armor, merged onto the generated entries by :key: the short names
   (equipment-parity.md)."
  {:padded {:name "Padded"}
   :leather {:name "Leather"}
   :studded {:name "Studded"}
   :hide {:name "Hide"}
   :splint {:name "Splint"}
   :plate {:name "Plate"}})

(def community-armor
  "Armor outside the SRD, kept by hand."
  [{:name "Spiked Armor"
    :type :medium
    :base-ac 14
    :max-dex-mod 2
    :weight 45
    :stealth-disadvantage? true
    :key :spiked-armor}])

(def armor-order
  "Every armor's :key, in the order lists show them."
  [:shield :padded :leather :studded :hide :chain-shirt :scale-mail :breastplate :half-plate :ring-mail :chain-mail :splint :plate :spiked-armor])

(def armor
  "Every armor and the shield: the generated SRD entries with app-fields merged in, then
   community-armor, in armor-order."
  (let [by-key (into {} (map (juxt :key identity))
                     (concat (map #(merge % (app-fields (:key %))) srd/armor) community-armor))]
    (mapv by-key armor-order)))

(def armor-map
  (zipmap (map :key armor) armor))

(defn non-shields [armor]
  (filter #(not= :shield (:type %)) armor))

(defn shields [armor]
  (filter #(= :shield (:type %)) armor))
