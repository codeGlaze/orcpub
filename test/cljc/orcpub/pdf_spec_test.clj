(ns orcpub.pdf-spec-test
  "Tests for pure PDF spec helper functions.
   pdf_spec.cljc no longer has any re-frame subscribes,
   so all helpers are testable on JVM."
  (:require [clojure.test :refer [deftest testing is]]
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
