(ns orcpub.pdf-spec-test
  "Tests for pure PDF spec helper functions.
   pdf_spec.cljc no longer has any re-frame subscribes,
   so all helpers are testable on JVM."
  (:require [clojure.test :refer [deftest testing is are]]
            [orcpub.pdf-spec :as pdf]
            [orcpub.dnd.e5.magic-items :as mi]))

;; -- class-string --

(deftest class-string-single-class
  (testing "single class without subclass"
    (is (= "Fighter (5)"
           (pdf/class-string
            [:fighter]
            {:fighter {:class-name "Fighter"
                       :class-level 5
                       :subclass-name ""}})))))

(deftest class-string-single-class-with-subclass
  (testing "single class with subclass shows brackets"
    (is (= "Wizard [Evocation] (10)"
           (pdf/class-string
            [:wizard]
            {:wizard {:class-name "Wizard"
                      :class-level 10
                      :subclass-name "Evocation"}})))))

(deftest class-string-multiclass
  (testing "multiclass joins with slash"
    (is (= "Fighter (5) / Wizard [Evocation] (3)"
           (pdf/class-string
            [:fighter :wizard]
            {:fighter {:class-name "Fighter"
                       :class-level 5
                       :subclass-name ""}
             :wizard {:class-name "Wizard"
                      :class-level 3
                      :subclass-name "Evocation"}})))))

(deftest class-string-empty-classes
  (testing "empty class list returns empty string"
    (is (= "" (pdf/class-string [] {})))))

;; -- entity-vals --

(deftest entity-vals-keyword-keys
  (testing "keyword keys use same key for to and from"
    ;; entity-val does (entity k), so built-char is a flat map
    (let [built-char {:str 18 :dex 14}
          result (pdf/entity-vals built-char [:str :dex])]
      (is (= 18 (:str result)))
      (is (= 14 (:dex result))))))

(deftest entity-vals-vector-keys
  (testing "vector [to from] renames the output key"
    (let [built-char {:strength 16}
          result (pdf/entity-vals built-char [[:str :strength]])]
      (is (= 16 (:str result)))
      (is (nil? (:strength result))))))

(deftest entity-vals-missing-key
  (testing "missing keys return nil"
    (let [built-char {}
          result (pdf/entity-vals built-char [:nonexistent])]
      (is (nil? (:nonexistent result))))))

;; -- trait-string nil/blank handling (homebrew data hardening) --

(deftest trait-string-normal
  (testing "Normal case: name and description both present"
    (let [result (pdf/trait-string "Darkvision" "you can see in the dark.")]
      (is (string? result))
      (is (.contains result "Darkvision"))
      ;; sentensize capitalizes first char and ensures trailing period
      (is (.contains result "You can see in the dark.")))))

(deftest trait-string-nil-desc
  (testing "Nil description doesn't crash (was NPE on subs/count before fix)"
    (let [result (pdf/trait-string "Darkvision" nil)]
      (is (string? result))
      (is (.contains result "Darkvision")))))

(deftest trait-string-nil-name-and-desc
  (testing "Both nil — worst case from totally empty homebrew trait"
    (let [result (pdf/trait-string nil nil)]
      (is (string? result))
      (is (.contains result "(Unnamed Trait)")))))

(deftest trait-string-nil-name-falls-back-to-desc
  (testing "Nil name uses first 30 chars of description as display name"
    (let [desc "You can see in the dark up to 60 feet including dim light"
          result (pdf/trait-string nil desc)]
      (is (string? result))
      ;; Should truncate to 30 chars + "..."
      (is (.contains result "...")))))

(deftest trait-string-blank-and-whitespace-desc
  (testing "Blank and whitespace-only descriptions treated as absent"
    (let [blank-result (pdf/trait-string "Darkvision" "")
          ws-result (pdf/trait-string "Darkvision" "   ")]
      ;; Neither should crash, both should use the name
      (is (.contains blank-result "Darkvision"))
      (is (.contains ws-result "Darkvision")))))

(deftest trait-string-wrong-type-desc
  (testing "Non-string description from corrupted data doesn't crash"
    (let [result (pdf/trait-string "Darkvision" 42)]
      (is (string? result))
      (is (.contains result "Darkvision")))))

;; -- captured-bytes --

(def ^:private captured
  "The private helper that turns what the browser managed to read into what the
   export actually carries."
  #'pdf/captured-bytes)

(deftest bytes-the-browser-read-are-carried
  (is (= "AAAA"
         (captured {"http://host/portrait.png" {:mime "image/png" :data "AAAA"}}
                   "http://host/portrait.png"))))

(deftest a-capture-that-produced-no-bytes-carries-nothing
  (testing "still running"
    (is (nil? (captured {"u" :pending} "u"))))
  (testing "refused by the host"
    ;; Both are states rather than payloads, and both have to read as nil so the
    ;; server falls back to fetching the address itself.
    (is (nil? (captured {"u" :unavailable} "u")))))

(deftest a-url-nobody-read-carries-nothing
  (is (nil? (captured {} "u")))
  (is (nil? (captured nil "u")))
  (is (nil? (captured {"other" {:data "AAAA"}} "u"))))

;; -- weapon-entries --

(defn entry
  "The one PDF entry for the weapon with this key."
  [k attack-bonus damage-modifier]
  (first (pdf/weapon-entries (mi/all-weapons-map k) attack-bonus damage-modifier)))

(deftest weapon-entries-carry-versatile-as-a-bullet
  (is (= {:name "Longsword" :attack-bonus 5 :damage "1d8+3 slashing" :bullets ["versatile 1d10+3"]}
         (entry :longsword 5 3))))

(deftest weapon-entries-without-variants-have-no-bullets
  (is (= [] (:bullets (entry :greatsword 5 3)))))

(deftest weapon-entries-use-the-magic-damage-type
  (is (= "1d8+4 radiant" (:damage (entry :sun-blade-longsword 6 4)))))

(deftest weapon-entries-list-each-extra-as-a-bullet
  (is (= ["versatile 1d10+5" "+2d10 radiant vs fiends and undead"]
         (:bullets (entry :holy-avenger-longsword 7 5))))
  (is (= ["+2d6 slashing vs giants" "DC 15 Str or prone, vs giants"]
         (:bullets (entry :giant-slayer-greataxe 6 4)))))

;; -- sheet-weapon-names --

(deftest magic-weapons-print-their-own-name
  (is (= ["Flame Tongue" "Holy Avenger" "Longsword"]
         (pdf/sheet-weapon-names (map mi/all-weapons-map [:flame-tongue-longsword :holy-avenger-longsword :longsword])))))

(deftest two-of-the-same-item-keep-their-full-names
  (is (= ["Flame Tongue, Longsword" "Flame Tongue, Greatsword" "Frost Brand"]
         (pdf/sheet-weapon-names (map mi/all-weapons-map [:flame-tongue-longsword :flame-tongue-greatsword
                                                          :frost-brand-rapier])))))

;; -- weapon-sheet-fields --

(deftest the-first-three-fill-the-rows-and-their-variants-follow
  (let [e (fn [nm & bullets] {:name nm :attack-bonus 4 :damage "1d8+2 slashing" :bullets (vec bullets)})
        {:keys [rows lines]} (pdf/weapon-sheet-fields
                              [(e "Dagger") (e "Longsword" "versatile 1d10+2")
                               (e "Flame Tongue" "versatile 1d10+2" "+2d6 fire while Ablaze")
                               (e "Holy Avenger" "+2d10 radiant vs fiends and undead") (e "Club")])]
    (is (= ["Dagger" "Longsword" "Flame Tongue"] (map :name rows)))
    (is (= ["Longsword"
            "  \u2022 versatile 1d10+2"
            "Flame Tongue"
            "  \u2022 versatile 1d10+2"
            "  \u2022 +2d6 fire while Ablaze"
            "Holy Avenger. +4, 1d8+2 slashing"
            "  \u2022 +2d10 radiant vs fiends and undead"
            "Club. +4, 1d8+2 slashing"]
           lines))))


;; -- file-name --

(def ^:private two-classes
  {:wizard {:class-name "Wizard" :class-level 5 :subclass-name "School of Evocation"}
   :cleric {:class-name "Cleric" :class-level 3 :subclass-name ""}})

(deftest file-name-follows-the-players-choices
  (testing "defaults: every part, level after its class, plain"
    (is (= "Pat - Hero - Wizard [School of Evocation] 5 + Cleric 3"
           (pdf/file-name "Pat" "Hero" [:wizard :cleric] two-classes {}))))
  (testing "level first, padded or prefixed, subclass off"
    (is (= "Hero - 05 Wizard + 03 Cleric"
           (pdf/file-name "Pat" "Hero" [:wizard :cleric] two-classes
                          {:player? false :subclass? false :level-first? true :level-style :padded})))
    (is (= "Hero - Wizard L5 + Cleric L3"
           (pdf/file-name "Pat" "Hero" [:wizard :cleric] two-classes
                          {:player? false :subclass? false :level-style :prefixed}))))
  (testing "classes off: the total level alone"
    (is (= "Hero - L8" (pdf/file-name "Pat" "Hero" [:wizard :cleric] two-classes
                                      {:player? false :class? false :level-style :prefixed}))))
  (testing "level off, and everything off"
    (is (= "Pat - Wizard [School of Evocation] + Cleric"
           (pdf/file-name "Pat" nil [:wizard :cleric] two-classes {:level? false})))
    (is (= "" (pdf/file-name "Pat" "Hero" [:wizard] two-classes
                             {:player? false :character? false :class? false :level? false})))))

(deftest clean-file-name-format-keeps-only-known-choices
  (is (= {:player? false :level-style :padded}
         (pdf/clean-file-name-format {:player? false :level-style :padded :x 1 :class? "no"})))
  (is (= {} (pdf/clean-file-name-format {:level-style :huge})))
  (is (= {} (pdf/clean-file-name-format "not a map"))))

(def ^:private cantrip-casters
  "spells-known for a Cleric/Wizard with a High Elf cantrip: cantrips from two classes and a race,
   and levelled spells from both classes."
  {0 {"g" {:key :guidance :class "Cleric" :ability :orcpub.dnd.e5.character/wis}
      "l" {:key :light :class "Wizard" :ability :orcpub.dnd.e5.character/int}
      "f" {:key :fire-bolt :class "High Elf" :ability :orcpub.dnd.e5.character/int}}
   1 {"b" {:key :bless :class "Cleric" :ability :orcpub.dnd.e5.character/wis}
      "m" {:key :magic-missile :class "Wizard" :ability :orcpub.dnd.e5.character/int}}})

(defn- spell-fields
  "spell-page-fields for `known` with slot table `slots`, per-class layout, style 1. Returns the field map."
  [known slots]
  (pdf/spell-page-fields known slots (constantly 13) (constantly 5) false #{} {} {} {} 1))

(defn- slot-fields
  "The spell-slots-* entries of field map `fields`, as a map."
  [fields]
  (into {} (filter #(re-matches #"spell-slots-.*" (name (key %)))) fields))

(deftest cantrips-carry-no-slot-total
  (testing "casters: no cantrip slots field on any page; levelled slots stay"
    (let [slots (slot-fields (spell-fields cantrip-casters {1 4}))]
      (is (empty? (filter #(re-matches #"spell-slots-0-.*" (name %)) (keys slots))) (str slots))
      (is (= 4 (:spell-slots-1-1 slots)) (str slots))))
  (testing "a non-caster whose only spell is a racial cantrip"
    (let [known {0 {"f" {:key :fire-bolt :class "High Elf" :ability :orcpub.dnd.e5.character/int}}}
          slots (slot-fields (spell-fields known {}))]
      (is (empty? (filter #(re-matches #"spell-slots-0-.*" (name %)) (keys slots))) (str slots)))))

(def ^:private tiefling-wizard
  "spells-known for a Tiefling Wizard: Wizard spells cast from slots, the Tiefling's Hellish Rebuke
   and Darkness free once per long rest, no slots (SRD 5.1 Infernal Legacy)."
  (let [lr {:units :orcpub.dnd.e5.units/long-rest :amount 1}
        cha :orcpub.dnd.e5.character/cha
        int :orcpub.dnd.e5.character/int]
    {1 {"m" {:key :magic-missile :class "Wizard" :ability int}
        "h" {:key :hellish-rebuke :class "Tiefling" :ability cha
             :casting {:free (assoc lr :level 2) :slots :none}}}
     2 {"s" {:key :shatter :class "Wizard" :ability int}
        "d" {:key :darkness :class "Tiefling" :ability cha :casting {:free lr :slots :none}}}}))

(def ^:private spell-names
  "Names for the spells these tests print, as spells-map holds them."
  (into {} (map (fn [[k n]] [k {:name n}]))
        {:magic-missile "Magic Missile" :hellish-rebuke "Hellish Rebuke" :shatter "Shatter"
         :darkness "Darkness" :hex "Hex" :mage-armor "Mage Armor" :eyebite "Eyebite"}))

(defn- page-of
  "The page suffix (\"-1\") whose spellcasting-class field reads `source` in field map `fields`."
  [fields source]
  (some (fn [[k v]] (when (= source v) (subs (name k) (count "spellcasting-class"))))
        fields))

(deftest race-and-feat-free-casts-carry-no-class-slots
  (let [fields (pdf/spell-page-fields tiefling-wizard {1 4 2 3} (constantly 13) (constantly 5)
                                     false #{} {} spell-names {} 1)
        wizard (page-of fields "Wizard")
        tiefling (page-of fields "Tiefling")]
    (testing "the Wizard page keeps its slots"
      (is (= 4 (get fields (keyword (str "spell-slots-1" wizard)))) (str fields))
      (is (= 3 (get fields (keyword (str "spell-slots-2" wizard)))) (str fields)))
    (testing "the Tiefling page shows how its spells are cast, and no slots"
      (is (empty? (filter #(and (re-matches #"spell-slots-.*" (name %))
                                (.endsWith (name %) tiefling))
                          (keys fields)))
          (str fields))
      (is (= "Hellish Rebuke (2nd level, once/long rest)"
             (get fields (keyword (str "spells-1-1" tiefling)))) (str fields))))
  (testing "packed: the Tiefling group carries no slots, the Wizard group the shared ones"
    (let [groups (into {} (map (juxt :class identity))
                       (pdf/packing-classes (into {} (map (fn [[l m]] [l (into {} (map (fn [[_ c]] [(:key c) c])) m)]))
                                                  tiefling-wizard)
                                            spell-names {1 4 2 3} {} (constantly 13) (constantly 5)))]
      (is (= {1 4 2 3} (get-in groups ["Wizard" :slots])))
      (is (= {} (get-in groups ["Tiefling" :slots])))
      (is (= ["Hellish Rebuke (2nd level, once/long rest)"] (get-in groups ["Tiefling" :levels 1]))))))

(deftest a-warlocks-free-casts-stay-off-its-pact-level
  (let [cha :orcpub.dnd.e5.character/cha
        known {1 {:hex {:key :hex :class "Warlock" :ability cha}
                  :mage-armor {:key :mage-armor :class "Warlock" :ability cha
                               :casting {:free {:at-will true} :slots :none}}}
               6 {:eyebite {:key :eyebite :class "Warlock" :ability cha :qualifier "uses Mystic Arcanum"
                            :casting {:free {:units :orcpub.dnd.e5.units/long-rest :amount 1} :slots :none}}}}
        [warlock] (pdf/packing-classes known spell-names {} {5 3} (constantly 15) (constantly 7))]
    (is (:pact? warlock))
    (is (= ["Hex"] (get-in warlock [:levels 5])) (str warlock))
    (is (= ["Mage Armor (at will)"] (get-in warlock [:levels 1])) (str warlock))
    (is (= ["Eyebite (uses Mystic Arcanum, once/long rest)"] (get-in warlock [:levels 6])) (str warlock))))

(deftest file-safe-names-open-on-every-system
  (testing "the PDF Options preview and the server share one rule"
    (are [in out] (= out (pdf/file-safe in))
      "Zoë D’Artagnan" "Zoë D’Artagnan"
      "Кира 龍 🙂"      "Кира 龍"
      "Zoe\u0308"      "Zo\u00EB"
      ". hidden ."     "hidden"
      "a\u200Bb"       "ab"
      nil              "")))
