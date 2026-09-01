(ns orcpub.build.demo-emit-test
  "Golden test for the bundled demo-content pack. Regenerates the pack in memory
   from the recipe and compares it to the committed file, so a stale checked-in
   file fails the build. Re-runs the emitter's own verification so a recipe that
   would not survive a real import fails here too."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [orcpub.build.demo-emit :as demo-emit]))

(deftest committed-file-matches-recipe
  (testing "the committed demo-content file is exactly what the recipe emits"
    (let [f (io/file demo-emit/output-path)]
      (is (.exists f)
          (str "Missing " demo-emit/output-path " — run `lein gen-demo`"))
      (when (.exists f)
        (is (= (demo-emit/rendered) (slurp f))
            (str "Committed demo pack is stale — run `lein gen-demo` and commit "
                 demo-emit/output-path))))))

(deftest recipe-passes-verification
  (testing "the recipe survives the load floor, save specs, and serializer round-trip"
    (is (some? (demo-emit/rendered)))))
