(ns orcpub.dnd.e5.subclass-key-test
  "A homebrew subclass is offered under its stored key, whatever its name is."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.template :as t]
            [orcpub.common :as common]
            [orcpub.dnd.e5.options :as opt5e]
            [orcpub.dnd.e5.spells :as spells5e]
            [orcpub.dnd.e5.spell-lists :as sl5e]))

(defn- offered-key
  "The key under which `subclass-option` offers `subclass` of the fighter class."
  [subclass]
  (::t/key (opt5e/subclass-option sl5e/spell-lists spells5e/spell-map
                                  (common/map-by-key [{:name "Common" :key :common}])
                                  {:key :fighter} subclass)))

(deftest subclass-is-offered-under-its-stored-key
  (testing "a name that does not make the stored key"
    (is (= :my-oath (offered-key {:key :my-oath :name "x"}))))
  (testing "no name at all"
    (is (= :nameless (offered-key {:key :nameless}))))
  (testing "no stored key: the name's key, as before"
    (is (= :champion (offered-key {:name "Champion"})))))
