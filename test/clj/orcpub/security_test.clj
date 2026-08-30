(ns orcpub.security-test
  (:require [clojure.test :refer [deftest testing is]]
            [orcpub.security :as security]
            [orcpub.time :as time]))

(deftest multiple-ip-attempts-answers-no-when-there-are-none
  (testing "an untouched account is not flagged"
    ;; This returned the attempts atom itself -- truthy even when empty -- so
    ;; it answered "yes" for every account, always.
    (is (not (security/multiple-ip-attempts-to-same-account-aux "nobody" {})))))

(deftest multiple-ip-attempts-answers-yes-past-the-threshold
  ;; Distinct timestamps on purpose. compare-dates orders by :date alone, so a
  ;; sorted-set-by treats two attempts sharing an instant as the same element
  ;; and silently keeps one -- which is a property of the production structure,
  ;; not just this fixture.
  (let [base (time/now)
        attempts (into (sorted-set-by security/compare-dates)
                       (map-indexed
                        (fn [i ip]
                          {:user "target" :ip ip
                           :date (.plusMillis base (inc i))})
                        ["1.1.1.1" "2.2.2.2" "3.3.3.3" "4.4.4.4"]))]
    (is (security/multiple-ip-attempts-to-same-account-aux
         "target" {"target" attempts})
        "four distinct IPs against one account is over the threshold of three")))

(deftest a-single-ip-is-not-flagged
  (let [base (time/now)
        attempts (into (sorted-set-by security/compare-dates)
                       (for [i (range 5)]
                         {:user "target" :ip "1.1.1.1"
                          :date (.plusMillis base (inc i))}))]
    (is (not (security/multiple-ip-attempts-to-same-account-aux
              "target" {"target" attempts}))
        "five attempts from one IP is a different signal, not this one")))
