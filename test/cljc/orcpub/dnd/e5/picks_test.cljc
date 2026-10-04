(ns orcpub.dnd.e5.picks-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.entity :as entity]
            [orcpub.dnd.e5.picks :as picks]))

(def ranger
  "A ranger 7 / fighter 1 with a race, a multi-pick skill list and a single pick at level 7."
  {:db/id 7
   ::entity/options
   {:race {::entity/key :elf ::entity/options {:subrace {::entity/key :wood-elf}}}
    :class [{::entity/key :ranger
             ::entity/options
             {:levels [{::entity/key :level-7
                        ::entity/options {:defensive-tactics {::entity/key :steel-will}}}]
              :skill-proficiency (with-meta [{::entity/key :athletics}
                                             {::entity/key :insight}
                                             {::entity/key :stealth}]
                                   {:kept true})}}
            {::entity/key :fighter
             ::entity/options {:fighting-style [{::entity/key :defense}]}}]}})

(def steel-will [:class :ranger :levels :level-7 :defensive-tactics :steel-will])
(def insight [:class :ranger :skill-proficiency :insight])

(deftest relinking-a-race-leaves-a-subrace-with-the-same-key-alone
  (let [character {:db/id 7
                   ::entity/options {:race {::entity/key :aarakocra
                                            ::entity/options {:subrace {::entity/key :aarakocra}}}}}
        {:keys [character rewrote]} (picks/relink character :orcpub.dnd.e5/races :aarakocra :aarakocra-2)]
    (is (= :aarakocra-2 (get-in character [::entity/options :race ::entity/key])))
    (is (= :aarakocra (get-in character [::entity/options :race ::entity/options :subrace ::entity/key])))
    (is (= 1 (count rewrote))))
  (let [character {::entity/options {:race {::entity/key :bird
                                             ::entity/options {:subrace {::entity/key :aarakocra}}}}}]
    (is (= #{:aarakocra} (picks/keys-of character :orcpub.dnd.e5/subraces)))
    (is (= #{:bird} (picks/keys-of character :orcpub.dnd.e5/races))
        "a subrace pick does not make the question about the race")))

(deftest walk-visits-every-pick-with-its-selection-path
  (let [seen (atom [])]
    (picks/walk (::entity/options ranger) (fn [path e] (swap! seen conj [path (::entity/key e)]) e))
    (is (= #{[[:race] :elf] [[:race :subrace] :wood-elf] [[:class] :ranger] [[:class] :fighter]
             [[:class :levels] :level-7] [[:class :levels :defensive-tactics] :steel-will]
             [[:class :skill-proficiency] :athletics] [[:class :skill-proficiency] :insight]
             [[:class :skill-proficiency] :stealth] [[:class :fighting-style] :defense]}
           (set @seen)))
    (is (= 10 (count @seen)) "each pick once")))

(deftest remove-at-takes-out-exactly-one-pick
  (testing "a single pick: its selection goes"
    (let [{:keys [character removed]} (picks/remove-at ranger steel-will)]
      (is (= {:address steel-will :entry {::entity/key :steel-will} :multi? false} removed))
      (is (not (contains? (get-in character [::entity/options :class 0 ::entity/options :levels 0
                                             ::entity/options])
                          :defensive-tactics)))))
  (testing "a multi-pick: by key, the others keep their order and the vector its metadata"
    (let [{:keys [character removed]} (picks/remove-at ranger insight)
          v (get-in character [::entity/options :class 0 ::entity/options :skill-proficiency])]
      (is (= [:athletics :stealth] (map ::entity/key v)))
      (is (= {:kept true} (meta v)))
      (is (true? (:multi? removed)))))
  (testing "the same key in another class is a different address"
    (let [{:keys [removed]} (picks/remove-at ranger [:class :fighter :fighting-style :defense])]
      (is (= {::entity/key :defense} (:entry removed)))))
  (testing "nothing stored there: the character is unchanged"
    (doseq [address [[:class :ranger :skill-proficiency :perception]
                     [:class :wizard :skill-proficiency :insight]
                     [:feats :alert]]]
      (let [{:keys [character removed]} (picks/remove-at ranger address)]
        (is (nil? removed) (str address))
        (is (= ranger character) (str address)))))
  (testing "everything else is untouched"
    (let [{:keys [character]} (picks/remove-at ranger steel-will)]
      (is (= (disj (picks/keys-of ranger nil) :steel-will) (picks/keys-of character nil))))))

(deftest put-at-reverses-remove-at
  (testing "a single pick comes back as it was"
    (let [{:keys [character removed]} (picks/remove-at ranger steel-will)]
      (is (= ranger (picks/put-at character removed)))))
  (testing "a multi-pick comes back at the end, metadata kept"
    (let [{:keys [character removed]} (picks/remove-at ranger insight)
          back (picks/put-at character removed)
          v (get-in back [::entity/options :class 0 ::entity/options :skill-proficiency])]
      (is (= [:athletics :stealth :insight] (map ::entity/key v)))
      (is (= {:kept true} (meta v)))))
  (testing "into an emptied multi-pick"
    (let [{:keys [character removed]} (picks/remove-at ranger [:class :fighter :fighting-style :defense])]
      (is (= [] (get-in character [::entity/options :class 1 ::entity/options :fighting-style])))
      (is (= [{::entity/key :defense}]
             (get-in (picks/put-at character removed)
                     [::entity/options :class 1 ::entity/options :fighting-style])))))
  (testing "nowhere to go: nil"
    (let [{:keys [removed]} (picks/remove-at ranger steel-will)
          no-ranger (assoc-in ranger [::entity/options :class] [(get-in ranger [::entity/options :class 1])])]
      (is (nil? (picks/put-at no-ranger removed))))))
