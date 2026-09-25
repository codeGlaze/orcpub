(ns orcpub.config-test
  "Pins the locale-independence of the CSP config tokens.

   CSP_POLICY and DEV_MODE are ASCII protocol tokens, not prose. Folding their
   case with the default locale means a Turkish machine reads STRICT as
   \"strıct\" (dotless i), matches no branch, and silently falls through to the
   PERMISSIVE policy -- a security downgrade nobody asked for and nothing
   reports. These tests run under a Turkish locale on purpose."
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [orcpub.config :as config]
            [environ.core])
  (:import [java.util Locale]))

(def ^:private turkish (Locale/forLanguageTag "tr-TR"))

(defn- with-locale
  "Run f with the JVM default locale set to loc, then restore it. Restoring
   matters: a leaked default would silently change how every later test in the
   same JVM folds case."
  [^Locale loc f]
  (let [saved (Locale/getDefault)]
    (try (Locale/setDefault loc) (f)
         (finally (Locale/setDefault saved)))))

(use-fixtures :once (fn [t] (let [saved (Locale/getDefault)]
                              (try (t) (finally (Locale/setDefault saved))))))

(deftest turkish-locale-does-not-break-csp-policy
  (testing "an uppercase CSP_POLICY still resolves to strict under tr-TR"
    (with-locale turkish
      (fn []
        (with-redefs [environ.core/env {:csp-policy "STRICT"}]
          (is (= "strict" (config/get-csp-policy))
              "STRICT lowercased with the Turkish locale yields \"strıct\" (dotless i)")
          (is (true? (config/strict-csp?))
              "a mis-folded token falls through to the PERMISSIVE policy"))))))

(deftest turkish-locale-does-not-break-dev-mode
  (testing "DEV_MODE comparison is case-insensitive and locale-independent"
    (with-locale turkish
      (fn []
        (doseq [v ["true" "TRUE" "True"]]
          (with-redefs [environ.core/env {:dev-mode v}]
            (is (true? (config/dev-mode?)) (str "DEV_MODE=" v " should be true"))))
        (doseq [v ["false" "FALSE" "" "yes" "1"]]
          (with-redefs [environ.core/env {:dev-mode v}]
            (is (false? (config/dev-mode?)) (str "DEV_MODE=" v " should be false"))))))))

(deftest dev-mode-is-false-when-unset
  (testing "an unset DEV_MODE is false, and does not throw"
    (with-redefs [environ.core/env {}]
      (is (false? (config/dev-mode?))))))

(deftest csp-policy-defaults-to-strict
  (testing "no CSP_POLICY set means strict"
    (with-redefs [environ.core/env {}]
      (is (= "strict" (config/get-csp-policy))))))
