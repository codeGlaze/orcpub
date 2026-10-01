(ns orcpub.dnd.e5.language-key-test
  (:require [clojure.test :refer [deftest is]]
            [orcpub.dnd.e5.options :as opt5e]))

(deftest a-race-language-is-found-by-its-name
  (let [language-map {:tidetongue-tp {:name "Tidetongue" :key :tidetongue-tp}
                      :elvish {:name "Elvish" :key :elvish}}]
    (is (= :tidetongue-tp (opt5e/language-key language-map "Tidetongue"))
        "a homebrew language whose key carries its source's tag")
    (is (= :elvish (opt5e/language-key language-map "Elvish")))
    (is (= :giant (opt5e/language-key language-map "Giant")) "no such name: the derived key, as before")))
