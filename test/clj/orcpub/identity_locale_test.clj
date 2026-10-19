(ns orcpub.identity-locale-test
  "An account identifier must fold the same on every machine.

   clojure.string/lower-case uses the JVM's default locale, so on a Turkish or
   Azerbaijani server \"IVAN@example.com\" becomes \"ıvan@example.com\" -- a different
   account key for sign-in, password reset, the unsubscribe token and email change.
   docs/kb/locale-safety.md section 3 names exactly this; these files are held to it."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [orcpub.common :as common])
  (:import (java.util Locale)))

(def ^:private identity-files
  "Files that fold an email, an account handle or another identifier. Not the whole
   tree: content and prose elsewhere still case-fold with the machine's locale, which
   is an open audit rather than a claim that it is correct."
  ["src/clj/orcpub/routes.clj"
   "src/clj/orcpub/artist_accounts.clj"])

(def ^:private bare-fold-re
  #"\((?:s|str|string|clojure\.string)/(?:lower-case|upper-case|capitalize)\s")

(deftest identity-files-do-not-fold-with-the-machine-locale
  (testing "ascii-lower-case / ascii-upper-case / ascii-capitalize, not the locale ones"
    (let [offenders (for [f identity-files
                          [i line] (map-indexed vector (str/split-lines (slurp (io/file f))))
                          :when (re-find bare-fold-re line)]
                      (str f ":" (inc i) " " (str/trim line)))]
      (is (empty? offenders)
          (str "use orcpub.common/ascii-*:\n" (str/join "\n" offenders))))))

(deftest an-email-folds-the-same-under-any-locale
  (testing "the Turkish dotless i is the whole problem"
    (let [saved (Locale/getDefault)]
      (try
        (Locale/setDefault (Locale/forLanguageTag "tr-TR"))
        (is (= "ivan@example.com" (common/ascii-lower-case "IVAN@example.com")))
        (is (not= (str/lower-case "IVAN@example.com")
                  (common/ascii-lower-case "IVAN@example.com"))
            "if these ever agree under tr_TR the guard above has stopped meaning anything")
        (finally (Locale/setDefault saved))))))
