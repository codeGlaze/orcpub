(ns orcpub.dnd.e5.races
  (:require #?(:clj [clojure.spec.alpha :as spec])
            #?(:cljs [cljs.spec.alpha :as spec])
            [clojure.string :as str]
            [orcpub.common :as common]
            [orcpub.dnd.e5.builder-fields :as bf]))

(spec/def ::name (spec/and string? common/starts-with-letter?))
(spec/def ::key (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::option-pack string?)
(spec/def ::languages (spec/and set?
                                (spec/coll-of string?)))
(spec/def ::homebrew-race (spec/keys :req-un [::name ::key ::option-pack]
                                     :opt-un [::languages]))

(spec/def ::race (spec/and keyword? common/keyword-starts-with-letter?))
(spec/def ::homebrew-subrace (spec/keys :req-un [::name ::key ::race ::option-pack]))

;; The Draconic Ancestry builder's FIELD SCHEMA — the single source for both the form
;; (views/render-builder-field) and the save spec (below). The full breath weapon as data,
;; stored as the keywords the engine expects. Dimensions are conditional on the chosen shape.
(def draconic-ancestry-fields
  (let [damage-types [:acid :lightning :fire :poison :cold :thunder :force :radiant :necrotic :psychic]
        line? #(= :line (get-in % [:breath-weapon :area-type]))
        cone? #(= :cone (get-in % [:breath-weapon :area-type]))]
    [{:key [:breath-weapon :damage-type] :type :enum :required? true :label "Breath Weapon Damage Type"
      :options (mapv (fn [dt] {:value dt :title (str/capitalize (name dt))}) damage-types)}
     {:key [:breath-weapon :area-type] :type :enum :required? true :label "Breath Weapon Shape"
      :options [{:value :line :title "Line"} {:value :cone :title "Cone"}]}
     {:key [:breath-weapon :line-width] :type :number :label "Line Width (ft.)" :when line?}
     {:key [:breath-weapon :line-length] :type :number :label "Line Length (ft.)" :when line?}
     {:key [:breath-weapon :length] :type :number :label "Cone Length (ft.)" :when cone?}
     {:key [:breath-weapon :save] :type :enum :required? true :label "Breath Weapon Save"
      :options [{:value :orcpub.dnd.e5.character/dex :title "Dexterity"}
                {:value :orcpub.dnd.e5.character/con :title "Constitution"}]}]))

;; Generated from the field schema (optional-by-default; required name/key/option-pack +
;; the marked fields; enum values must be one of their options). Replaces the prior blunt
;; (spec/keys :req-un [::name ::key ::option-pack]) which never looked at the breath weapon.
(spec/def ::homebrew-draconic-ancestry (bf/fields->spec draconic-ancestry-fields))
