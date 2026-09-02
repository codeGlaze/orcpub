(ns orcpub.build.demo-emit-test
  "Golden test for the bundled demo-content pack. Regenerates the pack in memory
   from the recipe and compares it to the committed file, so a stale checked-in
   file fails the build. Re-runs the emitter's own verification so a recipe that
   would not survive a real import fails here too."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [orcpub.dnd.e5.demo-content :as demo]
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

(deftest emitted-pack-is-v2-with-a-content-version
  (testing "the emitted pack is a v2 envelope carrying its own content revision"
    (let [data (edn/read-string (demo-emit/rendered))]
      (is (= 2 (:orcbrew/format-version data)) "the pack stamps as format v2")
      (is (= demo/version (:orcbrew/content-version data))
          "and carries the demo pack's content version (Phase 3 provenance)"))))
