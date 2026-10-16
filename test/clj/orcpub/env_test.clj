(ns orcpub.env-test
  "Pins the one rule orcpub.env exists to enforce: A BLANK VALUE IS ABSENT.

   Worth pinning rather than trusting, because the wrong version reads correctly.
   (or (env :k) default) looks like it applies the default when the variable is
   not set, and does not: environ returns \"\" for an exported-but-empty variable
   and \"\" is truthy in Clojure, so the default is never reached. That shape was
   written independently at five sites in this codebase.

   Each test below fails against the naive implementation. That is the point --
   a test that passes either way would not have caught any of the five."
  (:require [clojure.test :refer [deftest testing is]]
            [environ.core]
            [orcpub.env :as env]))

(defn- with-env
  "Run f with environ/env redefined to m. Redefining environ rather than the
   process environment because Java cannot set its own env vars."
  [m f]
  (with-redefs [environ.core/env m] (f)))

(deftest blank-counts-as-absent
  (testing "unset"
    (with-env {} #(is (nil? (env/value :missing)))))

  (testing "empty string -- the case (or (env :k) d) gets wrong"
    (with-env {:k ""} #(is (nil? (env/value :k)))))

  (testing "whitespace only, matching config/read-secret's trim"
    (with-env {:k "   "} #(is (nil? (env/value :k))))
    (with-env {:k "\t\n"} #(is (nil? (env/value :k))))))

(deftest real-values-survive
  (testing "a value is returned unchanged"
    (with-env {:k "hunter2"} #(is (= "hunter2" (env/value :k)))))

  (testing "surrounding whitespace is trimmed, inner whitespace is not"
    (with-env {:k "  a b  "} #(is (= "a b" (env/value :k)))))

  (testing "a value that merely looks empty is still a value"
    (with-env {:k "0"} #(is (= "0" (env/value :k))))
    (with-env {:k "false"} #(is (= "false" (env/value :k))))))

(deftest default-applies-to-blank-not-just-unset
  (testing "unset takes the default"
    (with-env {} #(is (= "8890" (env/value :port "8890")))))

  (testing "EMPTY takes the default too -- the whole reason this namespace exists"
    (with-env {:port ""} #(is (= "8890" (env/value :port "8890"))))
    (with-env {:port "  "} #(is (= "8890" (env/value :port "8890")))))

  (testing "a real value beats the default"
    (with-env {:port "9000"} #(is (= "9000" (env/value :port "8890"))))))

(deftest flag-matches-the-servers-own-comparison
  (testing "only the literal true, any case"
    (doseq [v ["true" "TRUE" "True" " true "]]
      (with-env {:f v} #(is (true? (env/flag? :f)) (str "expected true for " (pr-str v))))))

  (testing "every other value is false, including the truthy-LOOKING ones"
    (doseq [v ["yes" "1" "on" "tru" "false" "" "   "]]
      (with-env {:f v} #(is (false? (env/flag? :f)) (str "expected false for " (pr-str v))))))

  (testing "unset is false"
    (with-env {} #(is (false? (env/flag? :f))))))

(deftest turkish-locale-does-not-change-the-answer
  ;; The branch this lands on exists because of locale-dependent case folding.
  ;; flag? uses equalsIgnoreCase, which compares per character rather than by
  ;; locale casing rules, so "TRUE" must not fold to "tr�e" or similar under tr-TR.
  (let [saved (java.util.Locale/getDefault)]
    (try
      (java.util.Locale/setDefault (java.util.Locale/forLanguageTag "tr-TR"))
      (with-env {:f "TRUE"} #(is (true? (env/flag? :f))
                                 "TRUE must still read as true on a Turkish JVM"))
      (with-env {:k "  STRICT  "} #(is (= "STRICT" (env/value :k))
                                       "value trims but must not case-fold at all"))
      (finally (java.util.Locale/setDefault saved)))))
