(ns orcpub.dnd.e5.options-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.options :as opt]))

(deftest test-total-slots
  (is (= {1 2} (opt/total-slots 3 3)))
  (is (= {1 4
          2 3
          3 3
          4 1}
         (opt/total-slots 20 3))))
