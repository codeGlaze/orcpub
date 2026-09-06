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

(deftest unseen-gates-on-the-current-id
  (testing "a browser with no stamp is shown the release"
    (is (whats-new/unseen? nil)))
  (testing "an older stamp is shown the release"
    (is (whats-new/unseen? "some-earlier-release")))
  (testing "the current stamp is not shown it again"
    (is (not (whats-new/unseen? whats-new/current-release-id)))))
