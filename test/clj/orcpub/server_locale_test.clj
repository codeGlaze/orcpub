(ns orcpub.server-locale-test
  "The server must not take its behaviour from the operator's machine language.

   The JVM folds case and formats numbers with its default locale, so a Turkish server
   filed IVAN@x.com under ıvan@x.com and wrote 5,00 into PDF paths. -main pins it to
   Locale/ROOT before anything else runs. Not pinned in :jvm-opts on purpose: the
   locale CI job runs the suite under tr_TR and es_ES, and that would silence it."
  (:require [clojure.test :refer [deftest testing is]]
            [clojure.string :as str]
            [orcpub.server :as server])
  (:import (java.util Locale)))

(deftest pinning-overrides-a-foreign-machine-locale
  (let [saved (Locale/getDefault)]
    (try
      (Locale/setDefault (Locale/forLanguageTag "tr-TR"))
      (testing "the machine's locale does leak in before the pin -- or this proves nothing"
        (is (not= "ivan@x.com" (str/lower-case "IVAN@x.com")))
        (is (= "5,00" (format "%.2f" 5.0)))) ; locale-leak on purpose
      (server/pin-locale!)
      (testing "and after it, folding and formatting are the same on every machine"
        (is (= Locale/ROOT (Locale/getDefault)))
        (is (= "ivan@x.com" (str/lower-case "IVAN@x.com")))
        (is (= "5.00" (format "%.2f" 5.0)))) ; locale-leak on purpose
      (finally (Locale/setDefault saved)))))

(deftest every-entry-point-pins-before-it-starts-anything
  ;; Checked in the source: running either would start the whole system. Both matter:
  ;; Docker runs -main, scripts/start.sh -- what self-hosters use -- runs start-server.
  (doseq [[file start pin] [["src/clj/orcpub/server.clj" "(defn -main" "(pin-locale!)"]
                            ["dev/user.clj" "(defn start-server" "(server/pin-locale!)"]]]
    (testing (str file " pins ahead of the system start")
      (let [src (slurp file)
            body (subs src (str/index-of src start))
            p (str/index-of body pin)]
        (is (some? p) "the pin is there")
        (is (and p (< p (str/index-of body "component/start"))) "and runs first")))))
