(ns orcpub.dnd.e5.library-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.library :as library]))

(def ^:private library
  {"Classes" {:orcpub.dnd.e5/classes {:warden {:key :warden :name "Warden" :option-pack "Classes"}}}
   "Domains" {:orcpub.dnd.e5/subclasses {:tides {:key :tides :name "Oath of Tides" :class :warden
                                                 :option-pack "Domains"}}}})

(deftest commit-files-each-item-under-the-key-and-source-it-is-stored-at
  (let [stale (assoc-in library ["Classes" :orcpub.dnd.e5/classes :warden]
                        {:key :old-warden :name "Warden" :option-pack "Elsewhere"})
        {:keys [plugins]} (library/commit library stale {})]
    (is (= :warden (get-in plugins ["Classes" :orcpub.dnd.e5/classes :warden :key])))
    (is (= "Classes" (get-in plugins ["Classes" :orcpub.dnd.e5/classes :warden :option-pack])))))

(deftest commit-refuses-a-write-that-strands-a-link
  (let [gone (update library "Classes" dissoc :orcpub.dnd.e5/classes)]
    (testing "an accidental removal is refused and says what it would strand"
      (let [{:keys [refused plugins]} (library/commit library gone {})]
        (is (nil? plugins))
        (is (= [{:source "Domains" :type :orcpub.dnd.e5/subclasses :key :tides :name "Oath of Tides"
                 :target :warden :target-type :orcpub.dnd.e5/classes}]
               (:broken refused)))))
    (testing "a deliberate delete goes through and reports what now points at nothing"
      (let [{:keys [plugins broken]} (library/commit library gone {:deleting? true})]
        (is (some? plugins))
        (is (= [:tides] (map :key broken)))))
    (testing "links being moved elsewhere are not counted"
      (is (some? (:plugins (library/commit library gone
                                           {:retargeting [[:orcpub.dnd.e5/classes :warden]]})))))))

(deftest a-link-that-was-already-broken-does-not-block-a-write
  (let [orphaned (update library "Classes" dissoc :orcpub.dnd.e5/classes)]
    (is (some? (:plugins (library/commit orphaned (assoc-in orphaned ["Domains" :x] 1) {}))))))

(deftest commit-refuses-a-new-key-the-loader-would-set-aside
  (let [bad (assoc-in library ["Classes" :orcpub.dnd.e5/classes :9-lives] {:name "9 Lives"})]
    (is (= [["Classes" :orcpub.dnd.e5/classes :9-lives]]
           (:invalid (:refused (library/commit library bad {})))))
    (is (some? (:plugins (library/commit bad bad {})))
        "a key already stored is the loader's to set aside, not a reason to refuse every write")))

(deftest overwritten-names-the-entries-a-re-import-changed
  (let [reimport (-> library
                     (assoc-in ["Classes" :orcpub.dnd.e5/classes :warden :name] "Warden (2nd printing)")
                     (assoc-in ["Classes" :orcpub.dnd.e5/classes :new-one] {:name "New One"}))]
    (is (= [{:source "Classes" :type :orcpub.dnd.e5/classes :key :warden :name "Warden (2nd printing)"}]
           (library/overwritten library reimport))
        "a changed entry is named; a new one and an unchanged one are not")))

;; ── Two tabs ────────────────────────────────────────────────────────────────

(deftest three-way-keeps-both-tabs-work
  (let [base library
        mine (assoc-in base ["Classes" :orcpub.dnd.e5/classes :mine] {:name "Mine"})
        theirs (-> base
                   (assoc-in ["Classes" :orcpub.dnd.e5/classes :theirs] {:name "Theirs"})
                   (assoc "New Pak" {}))
        {:keys [plugins conflicts]} (library/three-way base mine theirs)]
    (is (empty? conflicts))
    (is (= #{:warden :mine :theirs} (set (keys (get-in plugins ["Classes" :orcpub.dnd.e5/classes]))))
        "an item added in each tab survives")
    (is (contains? plugins "New Pak") "an empty source the other tab added survives")))

(deftest three-way-applies-a-delete-without-undoing-the-other-tab
  (let [base library
        mine (update-in base ["Classes" :orcpub.dnd.e5/classes] dissoc :warden)
        theirs (assoc-in base ["Domains" :orcpub.dnd.e5/subclasses :tides :name] "Oath of the Tides")
        {:keys [plugins conflicts]} (library/three-way base mine theirs)]
    (is (empty? conflicts))
    (is (nil? (get-in plugins ["Classes" :orcpub.dnd.e5/classes :warden])) "this tab's delete applies")
    (is (= "Oath of the Tides" (get-in plugins ["Domains" :orcpub.dnd.e5/subclasses :tides :name]))
        "and the other tab's edit stands")))

(deftest three-way-reports-an-item-both-tabs-changed
  (let [base library
        mine (assoc-in base ["Classes" :orcpub.dnd.e5/classes :warden :name] "Warden A")
        theirs (assoc-in base ["Classes" :orcpub.dnd.e5/classes :warden :name] "Warden B")]
    (is (= [["Classes" :orcpub.dnd.e5/classes :warden]] (:conflicts (library/three-way base mine theirs))))
    (is (empty? (:conflicts (library/three-way base mine mine))) "the same change in both is no conflict")))

(deftest three-way-conflicts-when-a-deleted-source-gained-an-item-elsewhere
  (let [base {"Pak" {:orcpub.dnd.e5/languages {:cant {:key :cant :name "Cant" :option-pack "Pak"}}
                     :abbreviation "PK"}}
        mine (dissoc base "Pak")
        theirs (assoc-in base ["Pak" :orcpub.dnd.e5/languages :sea] {:key :sea :name "Sea" :option-pack "Pak"})
        {:keys [conflicts]} (library/three-way base mine theirs)]
    (is (seq conflicts) "the new item under a source this tab deleted is a conflict, not a silent merge")))

;; ── Links to nothing ────────────────────────────────────────────────────────

(deftest dangling-finds-links-nothing-answers
  (let [plugins {"Pak" {:orcpub.dnd.e5/subclasses {:lost {:name "Lost" :class :gone}
                                                   :srd  {:name "Srd" :class :cleric}
                                                   :home {:name "Home" :class :warden}}
                        :orcpub.dnd.e5/classes {:warden {:name "Warden"}}
                        :orcpub.dnd.e5/races {:folk {:name "Folk" :languages #{"Tidetongue" "Nope"}}}
                        :orcpub.dnd.e5/languages {:tidetongue-tp {:name "Tidetongue"}}}}
        out (library/dangling plugins {:orcpub.dnd.e5/classes #{:cleric}})]
    (is (= [{:link :subclass->class :to :orcpub.dnd.e5/classes :target :gone}]
           (get out ["Pak" :orcpub.dnd.e5/subclasses :lost])))
    (is (nil? (get out ["Pak" :orcpub.dnd.e5/subclasses :srd])) "built-in content answers")
    (is (nil? (get out ["Pak" :orcpub.dnd.e5/subclasses :home])) "a library item answers")
    (is (= [{:link :language-by-name :to :orcpub.dnd.e5/languages :target "Nope"}]
           (get out ["Pak" :orcpub.dnd.e5/races :folk]))
        "a language named by name is found by name; one nothing has that name is reported")
    (is (= {} (library/dangling plugins nil)) "nothing is reported before the builder's list exists")))

(deftest a-built-in-of-another-type-does-not-answer-a-link
  ;; The built-in LANGUAGE Orc is :orc; a subrace naming race :orc still points at nothing.
  (let [plugins {"Pak" {:orcpub.dnd.e5/subraces {:grey {:name "Grey" :race :orc}}}}]
    (is (= [:orc] (map :target (get (library/dangling plugins {:orcpub.dnd.e5/languages #{:orc}})
                                    ["Pak" :orcpub.dnd.e5/subraces :grey]))))
    (is (empty? (library/dangling plugins {:orcpub.dnd.e5/races #{:orc}})))))

(deftest a-level-selection-is-answered-only-by-a-selection
  ;; The builder names a class's generated choice after the selection it asks for, so the flat
  ;; list of offered keys held the link's own target.
  (let [plugins {"Pak" {:orcpub.dnd.e5/classes {:mage {:name "Mage" :level-selections [{:type :tricks}]}}}}]
    (is (= [:tricks] (map :target (get (library/dangling plugins {:orcpub.dnd.e5/classes #{:tricks}})
                                       ["Pak" :orcpub.dnd.e5/classes :mage]))))))

(deftest offered-by-type-reads-classes-and-races-from-the-template
  (let [template {::t/selections [(t/selection-cfg {:name "Race" :key :race
                                                    :options [(t/option-cfg {:name "Elf" :key :elf})]})
                                  (t/selection-cfg {:name "Class" :key :class
                                                    :options [(t/option-cfg {:name "Wizard" :key :wizard})]})
                                  (t/selection-cfg {:name "Feats" :key :feats
                                                    :options [(t/option-cfg {:name "Elf" :key :orc})]})]}
        offered (library/offered-by-type template)]
    (is (= #{:elf} (:orcpub.dnd.e5/races offered)))
    (is (= #{:wizard} (:orcpub.dnd.e5/classes offered)))
    (is (contains? (:orcpub.dnd.e5/languages offered) :orc) "built-in languages by their keys")
    (is (contains? (:orcpub.dnd.e5/spells offered) :fireball))
    (is (not (contains? (:orcpub.dnd.e5/races offered) :orc)) "a feat's key is no race")))

(deftest repoint-offer-finds-links-in-other-packs-that-do-not-hold-their-own-copy
  (let [plugins {"Classes" {:orcpub.dnd.e5/classes {:warden-x {:name "Warden X"}}}
                 "Domains" {:orcpub.dnd.e5/subclasses {:tides {:name "Oath of Tides" :class :warden}}}
                 "Twin"    {:orcpub.dnd.e5/classes {:warden {:name "Warden"}}
                            :orcpub.dnd.e5/subclasses {:own {:name "Own" :class :warden}}}}
        offer (library/repoint-offer plugins :orcpub.dnd.e5/classes :warden :warden-x "Classes")]
    (is (= [["Domains" :tides :warden :warden-x]] (map (juxt :source :key :target :to-key) offer))
        "a pack holding its own :warden means its own")
    (is (= :warden-x (get-in (library/apply-repairs plugins offer)
                             ["Domains" :orcpub.dnd.e5/subclasses :tides :class])))
    (is (= plugins (library/apply-repairs plugins [(assoc (first offer) :key :gone)]))
        "a repair for an item that is gone changes nothing")))

(deftest linking-finds-what-still-names-a-key-outside-some-sources
  (is (= [{:source "Domains" :type :orcpub.dnd.e5/subclasses :key :tides :name "Oath of Tides"}]
         (library/linking library :orcpub.dnd.e5/classes :warden #{"Classes"})))
  (is (empty? (library/linking library :orcpub.dnd.e5/classes :warden #{"Domains"}))))

;; ── Damage a past rename left ───────────────────────────────────────────────

(deftest suggested-repairs-finds-what-a-past-rename-left-behind
  (let [plugins {"Classes" {:orcpub.dnd.e5/classes {:warden-x {:name "Warden X" :former-keys [:warden]}
                                                    :other    {:name "Other"}}}
                 "Domains" {:orcpub.dnd.e5/subclasses {:tides {:name "Oath of Tides" :class :warden}
                                                       :lost  {:name "Lost" :class :gone}}}
                 "Library" {:orcpub.dnd.e5/selections {:tricks {:name "Library Tricks"}}}
                 "Import"  {:orcpub.dnd.e5/selections {:tricks-imp {:name "Import Tricks" :former-keys [:tricks]}}
                            :orcpub.dnd.e5/classes {:mage {:name "Mage" :level-selections [{:type :tricks}]}}}}
        repairs (library/suggested-repairs plugins {})]
    (is (= [[:tides :missing :warden-x]] (map (juxt :key :reason :to-key) repairs))
        "a renamed target; a link another pack's copy answers is left to mean that copy, and a
         link to nothing with no rename behind it is not guessed at")
    (is (= :warden-x (get-in (library/apply-repairs plugins repairs)
                             ["Domains" :orcpub.dnd.e5/subclasses :tides :class])))
    (is (empty? (library/suggested-repairs plugins {:orcpub.dnd.e5/classes #{:warden}}))
        "built-in content of the link's type answers the old key")
    (is (= 1 (count (library/suggested-repairs plugins {:orcpub.dnd.e5/languages #{:warden}})))
        "built-in content of another type does not")
    (is (not-any? #(= :tides (:key %))
                  (library/suggested-repairs
                   (assoc-in plugins ["Classes" :orcpub.dnd.e5/classes :other :former-keys] [:warden]) {}))
        "two items claiming the old key: no suggestion, since it would be a guess")))

(deftest renaming-a-language-carries-the-new-name-into-races
  (let [old {"Pak" {:orcpub.dnd.e5/languages {:tidetongue-tp {:name "Tidetongue"}}
                    :orcpub.dnd.e5/races {:tidefolk {:name "Tidefolk" :languages #{"Tidetongue" "Common"}}}}}
        new (assoc-in old ["Pak" :orcpub.dnd.e5/languages :tidetongue-tp :name] "Tide-tongue")]
    (is (= #{"Tide-tongue" "Common"}
           (get-in (:plugins (library/commit old new {})) ["Pak" :orcpub.dnd.e5/races :tidefolk :languages])))))

(deftest a-renamed-language-is-not-followed-while-its-old-name-still-answers
  (let [old {"Pak" {:orcpub.dnd.e5/languages {:elvish-tp {:name "Elvish"}}}
             "Folk" {:orcpub.dnd.e5/races {:wood {:name "Wood" :languages #{"Elvish"}}}}}
        renamed (assoc-in old ["Pak" :orcpub.dnd.e5/languages :elvish-tp :name] "Elvish (Old)")]
    (is (= #{"Elvish"} (get-in (:plugins (library/commit old renamed {})) ["Folk" :orcpub.dnd.e5/races :wood :languages]))
        "built-in Elvish still answers, so the race keeps meaning it"))
  (let [old {"Pak" {:orcpub.dnd.e5/languages {:cant-a {:name "Cant"}}}
             "Other" {:orcpub.dnd.e5/languages {:cant-b {:name "Cant"}}}
             "Folk" {:orcpub.dnd.e5/races {:thief {:name "Thief" :languages #{"Cant"}}}}}
        renamed (assoc-in old ["Pak" :orcpub.dnd.e5/languages :cant-a :name] "Old Cant")]
    (is (= #{"Cant"} (get-in (:plugins (library/commit old renamed {})) ["Folk" :orcpub.dnd.e5/races :thief :languages]))
        "another library language still answers to the old name")))

(deftest restoring-lets-a-key-the-loader-sets-aside-through
  (let [bad (assoc-in library ["Classes" :orcpub.dnd.e5/classes :9-lives] {:name "9 Lives"})]
    (is (some? (:plugins (library/commit library bad {:deleting? true :restoring? true}))))))

(deftest empty-library-is-a-library-with-no-items
  (is (library/empty-library? {}))
  (is (library/empty-library? {"Default Option Source" {}}))
  (is (library/empty-library? {"Pak" {:orcpub.dnd.e5/classes {}}}))
  (is (not (library/empty-library? library))))

(deftest dropped-lists-items-the-second-library-lacks
  (is (= [["Classes" :orcpub.dnd.e5/classes :warden]]
         (library/dropped library (update library "Classes" dissoc :orcpub.dnd.e5/classes)))))

(deftest a-corrected-key-is-kept-as-a-former-key
  ;; Characters picked the item under the :key it carried; filing it under its storage key must
  ;; leave them a way back.
  (let [stale {"Pak" {:orcpub.dnd.e5/feats {:tough-kt {:name "Tough" :key :tough}}}}
        item (get-in (library/normalize stale) ["Pak" :orcpub.dnd.e5/feats :tough-kt])]
    (is (= :tough-kt (:key item)))
    (is (= [:tough] (:former-keys item)))
    (is (= (library/normalize stale) (library/normalize (library/normalize stale))) "once only")))

(deftest a-pick-is-only-what-its-selection-can-hold
  (let [both [:orcpub.dnd.e5/feats :orcpub.dnd.e5/languages]]
    (is (= [:orcpub.dnd.e5/feats] (library/pick-types :feats both)) "a feat pick is not a language")
    (is (= both (library/pick-types :some-other-choice both)) "a feat can be picked under other choices")
    (is (= [:orcpub.dnd.e5/languages]
           (library/pick-types :languages [:orcpub.dnd.e5/races :orcpub.dnd.e5/languages]))
        "a race is only ever picked under :race")))
