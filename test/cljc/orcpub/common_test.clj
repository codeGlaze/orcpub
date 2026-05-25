(ns orcpub.common-test
  (:require [clojure.test :refer [is deftest]]
            [clojure.spec.test.alpha :as stest]
            [orcpub.common :as common]))

(deftest test-add-namespaces-to-keys
  (stest/instrument `common/add-namespaces-to-keys)
  (is (= {:db/id 88
          :a.b.c/x 1
          :a.b.c/y "sdlk"}
         (common/add-namespaces-to-keys "a.b.c" {:db/id 88
                                                 :x 1
                                                 :y "sdlk"})))
  (stest/instrument `common/add-namespaces-to-keys))

(deftest test-name-to-kw
  (is (= :wizard (common/name-to-kw "Wizard")))
  (is (= :my-class (common/name-to-kw "My Class")))
  (is (= :dr-johns-class (common/name-to-kw "Dr. John's Class")))
  (is (= :ns/wizard (common/name-to-kw "Wizard" "ns")))
  ;; Regression: (keyword "") prints as ":" and the EDN reader rejects it
  ;; with "A single colon is not a valid keyword" — a missing-homebrew
  ;; bug previously stranded the loading spinner. Return nil instead.
  (is (nil? (common/name-to-kw "")))
  (is (nil? (common/name-to-kw "'")))
  (is (nil? (common/name-to-kw nil)))
  (is (nil? (common/name-to-kw "" "ns"))))
