(ns orcpub.whats-new-test
  "Shape checks on the release highlights. The panel reads this data straight into
   the DOM, so a malformed entry is a broken panel with no other warning."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.whats-new :as whats-new]))

;; Icon names that render as an empty column: the app serves Font Awesome 5, and
;; these are the v4 spellings that were renamed in it.
(def ^:private renamed-in-fa5
  #{"fa-exchange" "fa-shield" "fa-picture-o" "fa-folder-open-o" "fa-file-text-o"
    "fa-star-o" "fa-heart-o" "fa-trash-o" "fa-pencil" "fa-refresh" "fa-remove"})

(deftest releases-are-well-formed
  (testing "there is a release to show"
    (is (seq whats-new/releases))
    (is (= (:id whats-new/current-release) whats-new/current-release-id)))

  (testing "ids are present and distinct — the id is what gates a second showing"
    (let [ids (map :id whats-new/releases)]
      (is (every? #(and (string? %) (not (str/blank? %))) ids))
      (is (= (count ids) (count (set ids))))))

  (testing "every release carries a title and at least one highlight"
    (doseq [{:keys [id title items]} whats-new/releases]
      (is (not (str/blank? title)) (str id " has no title"))
      (is (seq items) (str id " has no highlights")))))

(deftest highlights-are-renderable
  (doseq [{release :id items :items} whats-new/releases
          {:keys [icon headline detail]} items]
    (testing (str release " / " headline)
      (is (not (str/blank? headline)))
      (is (not (str/blank? detail)))
      (is (str/starts-with? (str icon) "fa-") "icons are Font Awesome class names")
      (is (not (contains? renamed-in-fa5 icon))
          (str icon " is a Font Awesome 4 name and renders blank")))))

(deftest groups-render-as-one-heading-each
  (doseq [{release :id :as r} whats-new/releases]
    (testing release
      (let [groups (map first (whats-new/grouped-items r))]
        (testing "a group is contiguous, so it gets one heading rather than several"
          (is (= (count groups) (count (distinct groups)))
              (str "regrouped: " (pr-str groups))))
        (testing "grouping is all-or-nothing within a release"
          (is (or (every? some? groups) (every? nil? groups))))))))

(deftest unseen-gates-on-the-current-id
  (testing "a browser with no stamp is shown the release"
    (is (whats-new/unseen? nil)))
  (testing "an older stamp is shown the release"
    (is (whats-new/unseen? "some-earlier-release")))
  (testing "the current stamp is not shown it again"
    (is (not (whats-new/unseen? whats-new/current-release-id)))))

(deftest patron-credits-parse-from-pasted-markdown
  (testing "headings start tiers; names keep their order; list markers and blank lines drop"
    (is (= [{:tier "Archmage" :names ["Mira Ashgrove" "Tobin Reed"]}
            {:tier "Adventurer" :names ["Kessa"]}]
           (whats-new/parse-credits "## Archmage\n- Mira Ashgrove\n* Tobin Reed\n\n## Adventurer\r\nKessa\n"))))
  (testing "names before any heading are one list with no tier"
    (is (= [{:tier nil :names ["Kessa" "Nyx"]}] (whats-new/parse-credits "Kessa\nNyx"))))
  (testing "a tier with no names, and an empty file, show nothing"
    (is (= [{:tier "Adventurer" :names ["Kessa"]}] (whats-new/parse-credits "# Archmage\n# Adventurer\nKessa")))
    (is (= [] (whats-new/parse-credits "")))
    (is (= [] (whats-new/parse-credits nil)))))

(deftest a-long-last-group-shows-a-sample-and-the-rest-alphabetically
  (let [many (mapv #(str "Patron " (char (- 90 (mod % 26))) %) (range 40))
        [top last-group] (whats-new/credits-display [{:tier "Archmage" :names ["Zed" "Amy"]}
                                                    {:tier "Adventurer" :names many}]
                                                   identity)]
    (testing "groups before the last show in full, in file order"
      (is (= {:tier "Archmage" :names ["Zed" "Amy"]} top)))
    (testing "the last group shows credits-sample-size names, then all of them alphabetically"
      (is (= (take whats-new/credits-sample-size many) (:sample last-group)))
      (is (= (sort-by str/lower-case many) (:all last-group)))))
  (testing "a short last group, and a single list, show in full with no sample"
    (is (= [{:tier nil :names ["Kessa"]}] (whats-new/credits-display [{:tier nil :names ["Kessa"]}] identity)))
    (is (= [] (whats-new/credits-display [] identity)))))
