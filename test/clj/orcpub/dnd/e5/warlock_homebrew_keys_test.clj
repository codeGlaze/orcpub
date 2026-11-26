(ns orcpub.dnd.e5.warlock-homebrew-keys-test
  "Homebrew boons and invocations are offered under their stored :key, not one made from the name.
   Picks of the name's key heal (content-reconciliation, items-with-formers)."
  (:require [clojure.test :refer [deftest is]]
            [orcpub.template :as t]
            [orcpub.dnd.e5.classes :as classes]))

(deftest a-homebrew-boon-is-offered-under-its-stored-key
  (is (= :pact-of-ash-pk
         (::t/key (first (classes/pact-boon-options [{:name "Pact of Ash" :key :pact-of-ash-pk}] {} {}))))))

(deftest a-homebrew-invocation-is-offered-under-its-stored-key
  (is (= :ember-sight-2
         (::t/key (first (classes/eldritch-invocation-options [{:name "Ember Sight" :key :ember-sight-2}]
                                                              {} {}))))))
