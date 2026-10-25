(ns orcpub.hardened-helpers-test
  "The helpers every option, choice and modifier passes through skip a value of the wrong shape
   instead of throwing (docs/HOMEBREW_GUARD.md, 5)."
  (:require #?(:clj  [clojure.test :refer [deftest testing is]]
               :cljs [cljs.test :refer-macros [deftest testing is]])
            [orcpub.common :as common]
            [orcpub.dnd.e5.skills :as skills]
            [orcpub.dnd.e5.options :as opt5e]))

(def ^:private junk [42 "x" true :kw [] [42] [{}] {:a 1} #{42} 1.5])

(deftest usable-gives-back-what-fits-and-the-fallback-otherwise
  (is (= {:a true} (common/usable map? {:a true} {} "x")))
  (is (= {} (common/usable map? nil {} "x")))
  (is (= {} (common/usable map? 42 {} "x"))))

(deftest the-helpers-never-throw-on-junk
  (doseq [v junk]
    (testing (pr-str v)
      (is (seq? (doall (opt5e/collect-map-modifiers v identity))))
      (is (sequential? (doall (opt5e/plugin-modifiers v :k))))
      (is (sequential? (doall (opt5e/feat-modifiers :k "F" "d" {} v))))
      (is (or (nil? (common/safe-capitalize-kw v)) (string? (common/safe-capitalize-kw v)))))))

(deftest a-skill-key-that-names-no-skill-shows-as-its-text
  (is (= "Athletics" (skills/skill-name :athletics)))
  (is (= "Juggling" (skills/skill-name :juggling)))
  (is (nil? (skills/skill-name 42))))
