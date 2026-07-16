(ns orcpub.tools.orcbrew-test
  "Tests for the orcbrew CLI tool error handling.
   Verifies that bad args throw ex-info instead of System/exit (REPL-safe).
   Test bodies capture stdout so CLI error/usage output doesn't pollute CI logs."
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.tools.orcbrew :as orcbrew]))

(deftest test-main-no-args-throws-not-exits
  (testing "-main with no args throws ex-info instead of killing the JVM"
    (try
      (with-out-str (orcbrew/-main))
      (is false "Should have thrown ex-info")
      (catch clojure.lang.ExceptionInfo e
        (is (= :usage-error (:type (ex-data e))))))))

(deftest test-main-missing-file-throws-not-exits
  (testing "-main with nonexistent file throws with file info"
    (try
      (with-out-str (orcbrew/-main "totally-nonexistent-file.orcbrew"))
      (is false "Should have thrown ex-info")
      (catch clojure.lang.ExceptionInfo e
        (is (= :file-not-found (:type (ex-data e))))
        (is (= "totally-nonexistent-file.orcbrew" (:filepath (ex-data e))))))))
