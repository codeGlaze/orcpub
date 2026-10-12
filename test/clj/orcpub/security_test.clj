(ns orcpub.security-test
  (:require [clojure.test :refer [deftest is testing]]
            [orcpub.time :as time :refer [seconds minutes hours millis ago now]]
            [orcpub.security :as s]
            [clojure.set :as sets]))

;; Convenience wrappers for tests - these take n and return an Instant n units ago
(defn seconds-ago [n] (-> n seconds ago))
(defn minutes-ago [n] (-> n minutes ago))
(defn hours-ago [n] (-> n hours ago))
(defn millis-ago [n] (-> n millis ago))

(defn attempts-set [attempts]
  (into
   (sorted-set-by s/compare-dates)
   attempts))

(defn attempts-for-dates [dates]
  (into
   (sorted-set-by s/compare-dates)
   (map
    (fn [date]
      {:date date})
    dates)))

(deftest test-compare-dates
  (is (= 1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (seconds-ago 11)})))
  (is (= 1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (minutes-ago 11)})))
  (is (= -1 (s/compare-dates
            {:date (seconds-ago 10)}
            {:date (seconds-ago 9)})))
  (is (= -1 (s/compare-dates
            {:date (minutes-ago 10)}
            {:date (seconds-ago 9)}))))

(deftest test-too-many-attempts-for-username?
  (let [attempts {"larry" (attempts-for-dates (map seconds-ago (range 5)))
                  "larry-2" (attempts-for-dates (map seconds-ago (range 6)))
                  "redorc" (attempts-for-dates (map seconds-ago (range 4)))
                  "redorc-2" (sets/union
                              (attempts-for-dates (map seconds-ago (range 1 5)))
                              (attempts-for-dates (map hours-ago (range 1 6))))}]
    (is (s/too-many-attempts-for-username-aux "larry" attempts))
    (is (s/too-many-attempts-for-username-aux "larry-2" attempts))
    (is (not (s/too-many-attempts-for-username-aux "redorc" attempts)))
    (is (= 5 (-> "redorc-2" attempts (subseq < {:date (minutes-ago 1)}) count)))
    (is (= 4 (-> "redorc-2" attempts (subseq > {:date (minutes-ago 1)}) count)))
    (is (not (s/too-many-attempts-for-username-aux "redorc-2" attempts)))
    (is (not (s/too-many-attempts-for-username-aux "larry-3" attempts)))))

(deftest test-multiple-account-access?
  (let [attempts {"1.2.3.4" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 5)))
                  "1.2.3.5" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 6)))
                  "1.2.3.6" (attempts-set
                             (map
                              (fn [i]
                                {:date (seconds-ago i)
                                 :user (str "user-" i)})
                              (range 4)))}
        attempts-2 (update
                    attempts
                    "1.2.3.4"
                    sets/union
                    (attempts-set
                     (map
                      (fn [i]
                        {:date (hours-ago i)
                         :user (str "user-" i)})
                      (range 1 10))))]
    (is (s/multiple-account-access-aux "1.2.3.4" attempts))
    (is (s/multiple-account-access-aux "1.2.3.5" attempts))
    (is (not (s/multiple-account-access-aux "1.2.3.6" attempts)))
    (is (not (s/multiple-account-access-aux "1.2.3.6" attempts-2)))
    (is (not (s/multiple-account-access-aux "1.2.3.8" attempts-2)))))

;; Was disabled with "TODO: Fix / remove test" because the function it covers
;; never called its own aux -- it returned the attempts atom, which is truthy
;; even when empty, so every assertion expecting false failed. The function is
;; fixed, so the test is live again.
(deftest test-multiple-ip-attempts-to-same-account?
  (let [attempts {"user-1" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 3)))
                  "user-2" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 4)))
                  "user-3" (attempts-set
                            (map
                             (fn [i]
                               {:date (millis-ago i)
                                :ip (str i)})
                             (range 2)))}]
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-1"
         attempts))
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-1"
         (update
          attempts
          "user-1"
          (fn [as]
            (sets/union
             as
             (attempts-set
              (map
               (fn [s]
                 (assoc s :ip "0"))
               as)))))))
    (is (not
         (s/multiple-ip-attempts-to-same-account-aux
          "user-3"
          (update
           attempts
           "user-3"
           (fn [as]
            (sets/union
             as
             (attempts-set
              (map
               (fn [s]
                 (assoc s :ip "0"))
               as))))))))
    (is (s/multiple-ip-attempts-to-same-account-aux
         "user-2"
         attempts))
    (is (not (s/multiple-ip-attempts-to-same-account-aux
              "user-3"
              attempts)))
    (is (not (s/multiple-ip-attempts-to-same-account-aux
              "user-4"
              attempts)))))

(deftest test-add-and-remove-old
  (let [attempts {"user-1" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-1"
                                :date (minutes-ago i)})
                             (range 1 10)))
                  "user-2" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-2"
                                :date (minutes-ago (+ i 10))})
                             (range 1 10)))
                  "user-3" (attempts-set
                            (map
                             (fn [i]
                               {:ip (str i)
                                :user "user-3"
                                :date (minutes-ago i)})
                             (range 1 20)))}
        result (s/add-and-remove-old
                "user-1"
                {:ip "x"
                 :user "user-1"
                 :date (now)}
                attempts
                (minutes-ago 10))]
    (is (-> "user-2" result nil?))
    (is (= 10 (-> "user-1" result count)))
    (is (= 9 (-> "user-3" result count)))))

(deftest multiple-ip-attempts-answers-no-for-an-untouched-account
  ;; The precise shape of the bug: the function returned the attempts map
  ;; instead of calling its aux, and an empty map is truthy in Clojure, so it
  ;; answered "yes" for an account with no attempts at all.
  (is (not (s/multiple-ip-attempts-to-same-account-aux "nobody" {})))
  (is (not (s/multiple-ip-attempts-to-same-account? "nobody"))
      "and through the public predicate, which is what callers use"))

(deftest attempts-sharing-an-instant-collapse
  ;; A property of the production structure, not a test artifact: attempts live
  ;; in a sorted-set-by compare-dates, which orders on :date alone, so two
  ;; attempts recorded in the same instant are treated as one element and one
  ;; is dropped. Worth knowing before trusting these counts as exact.
  (let [instant (now)
        two-ips (into (sorted-set-by s/compare-dates)
                      [{:user "u" :ip "1.1.1.1" :date instant}
                       {:user "u" :ip "2.2.2.2" :date instant}])]
    (is (= 1 (count two-ips))
        "two attempts, one instant, one surviving entry")))


(deftest refusals-are-counted-so-the-numbers-can-be-tuned
  (s/take-refusals!)
  (is (nil? (s/refusal-summary {})) "a quiet hour says nothing")
  ;; Eleven signups from one host inside the hour: the first ten pass.
  (dotimes [_ 11] (s/registration-allowed? "9.9.9.9"))
  (let [counts (s/take-refusals!)]
    (is (= 1 (:n (:register counts))))
    (is (= "limits: register 1" (s/refusal-summary counts))))
  (is (= {} (s/take-refusals!)) "taking them resets, so each hour is its own period"))

(deftest a-limit-turns-away-only-what-is-past-it
  (s/take-refusals!)
  (let [host (str "10.0.0." (rand-int 250))]
    (is (every? true? (repeatedly 10 #(s/registration-allowed? host))))
    (is (false? (s/registration-allowed? host)))
    (is (true? (s/registration-allowed? (str host ".other")))
        "a different host has its own allowance")))


(deftest one-host-hammering-reads-differently-from-a-spread
  ;; The count alone cannot tell these apart, and they are not the same event:
  ;; twenty refusals from twenty addresses is a pool working through a list.
  (s/take-refusals!)
  (dotimes [_ 4] (s/note-refusal! :register "5.5.5.5"))
  (is (= "limits: register 4" (s/refusal-summary (s/take-refusals!)))
      "one source is not worth naming as a spread")
  (doseq [n (range 6)] (s/note-refusal! :register (str "5.5.5." n)))
  (is (= "limits: register 6 from 6 sources" (s/refusal-summary (s/take-refusals!)))))

(deftest a-host-that-stops-just-short-is-still-visible
  ;; The case refusals cannot see at all: a host taking nine of its ten signups
  ;; an hour, every hour, trips nothing and is never counted. busiest-summary
  ;; is pure, so it is tested on its own rather than through the shared atom.
  (is (= "limits, busiest source: register 9/10" (s/busiest-summary {:register 9} 0.8)))
  (is (= "limits, busiest source: register 10/10" (s/busiest-summary {:register 10} 0.8)))
  (is (nil? (s/busiest-summary {:register 2} 0.8)) "an ordinary hour says nothing")
  (is (nil? (s/busiest-summary {:unknown-limit 99} 0.8)) "a limit with no cap is not guessed at"))

(deftest busiest-reads-the-live-window
  (let [host (str "7.7.7." (rand-int 250))]
    (dotimes [_ 9] (s/registration-allowed? host))
    ;; Other tests share this atom, so the floor is what matters, not the value.
    (is (>= (:register (s/busiest)) 9))))


(deftest a-password-change-withdraws-the-tokens-that-came-before-it
  (s/restore-password-changes! {})
  (let [before 1000 after 3000]
    (s/note-password-changed! "kaylee" 2000)
    (testing "a token minted before the change is no longer good"
      (is (true? (s/token-withdrawn? "kaylee" before))))
    (testing "one minted after it is"
      (is (false? (s/token-withdrawn? "kaylee" after))))
    (testing "a token with no minted stamp predates the mechanism, so it goes"
      (is (true? (s/token-withdrawn? "kaylee" nil))))
    (testing "nobody else is signed out -- this is not a limit on logins"
      (is (false? (s/token-withdrawn? "wash" before)))
      (is (false? (s/token-withdrawn? "wash" nil))))))

(deftest the-register-forgets-what-it-can-no-longer-refuse
  (s/restore-password-changes! {})
  (s/note-password-changed! "old" 1000)
  (s/note-password-changed! "recent" 9000)
  (s/forget-password-changes-before! 5000)
  (is (= #{"recent"} (set (keys (s/password-changes-snapshot))))
      "an entry older than the token lifetime can only refuse tokens that have expired anyway")
  (is (false? (s/token-withdrawn? "old" 1))
      "and once forgotten it refuses nothing"))

(deftest the-register-can-be-restored-because-a-restart-empties-it
  ;; The failure this guards is silent: after a restart an unrestored register
  ;; refuses nothing and looks exactly like one that has nothing to refuse.
  (s/restore-password-changes! {})
  (is (false? (s/token-withdrawn? "kaylee" 1)) "empty register refuses nothing")
  (s/restore-password-changes! [["kaylee" 2000]])
  (is (true? (s/token-withdrawn? "kaylee" 1)) "restored register refuses again")
  (s/restore-password-changes! {}))

(deftest a-refresh-from-an-older-snapshot-keeps-what-was-noted-since
  ;; Greptile, PR #34: the hourly refresh read the database, a reset committed and
  ;; noted itself, then the refresh replaced the map with its older read.
  (s/restore-password-changes! {})
  (s/note-password-changed! "kaylee" 9000)
  (s/absorb-password-changes! {} 0)
  (is (true? (s/token-withdrawn? "kaylee" 8000)) "a snapshot that predates the note keeps it")
  (s/absorb-password-changes! {"kaylee" 3000} 0)
  (is (true? (s/token-withdrawn? "kaylee" 8000)) "an older instant from the database does not win")
  (s/absorb-password-changes! {"wash" 7000} 0)
  (is (true? (s/token-withdrawn? "wash" 6000)) "what the database knows is added")
  (s/absorb-password-changes! {} 8000)
  (is (false? (s/token-withdrawn? "wash" 6000)) "and expired entries still drop out")
  (s/restore-password-changes! {}))
