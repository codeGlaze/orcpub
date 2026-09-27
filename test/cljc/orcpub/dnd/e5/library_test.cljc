(ns orcpub.dnd.e5.library-test
  (:require [clojure.test :refer [deftest testing is]]
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
