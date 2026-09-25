(ns orcpub.registration-test
  (:require [clojure.test :refer [deftest testing is are]]
            [orcpub.registration :as reg]))

(defn- messages [password & [context]]
  (:password (reg/validate-password password context)))

(deftest length-floor
  (testing "below the floor is rejected, at the floor is not"
    ;; Reads the constant rather than the number, so raising the floor does not
    ;; mean editing an assertion that was never about 8 in particular.
    (is (some #(re-find (re-pattern (str "at least " reg/min-password-length)) %)
              (messages "Sh0rt!")))
    (is (nil? (messages "vault of seven ravens")))
    (is (some? (messages nil)))
    (is (some? (messages ""))))
  (testing "spaces around it do not count, because every path stores it trimmed"
    ;; Greptile, PR #34: 13 characters as typed, 11 as saved.
    (is (some? (messages " pomegranate ")))
    (is (some? (messages "           ")))))

(deftest repeated-characters
  (is (reg/repeated-run? "aaa"))
  (is (reg/repeated-run? "passsword"))
  (is (reg/repeated-run? "hello!!!there"))
  (testing "two in a row is ordinary English and must pass"
    (is (not (reg/repeated-run? "kettle")))
    (is (not (reg/repeated-run? "aa")))
    (is (not (reg/repeated-run? nil)))))

(deftest sequential-runs
  (testing "counting up or down"
    (is (reg/sequential-run? "abcd"))
    (is (reg/sequential-run? "4321"))
    (is (reg/sequential-run? "vault1234")))
  (testing "keyboard rows, either direction"
    (is (reg/sequential-run? "qwerty"))
    (is (reg/sequential-run? "ytrewq"))
    (is (reg/sequential-run? "asdfgh")))
  (testing "ordinary words are not runs"
    (is (not (reg/sequential-run? "raven")))
    (is (not (reg/sequential-run? "vault")))
    (is (not (reg/sequential-run? "abc")))
    (is (not (reg/sequential-run? nil)))))

(deftest identifier-in-password
  (let [ctx {:username "thornwood" :email "ana@example.com"}]
    (is (reg/contains-identifier? "thornwood99" ctx))
    (is (reg/contains-identifier? "myTHORNWOODpass" ctx))
    (testing "the local part of the email counts"
      (is (reg/contains-identifier? "ana-is-here" ctx)))
    (testing "unrelated passwords pass, and short fragments are ignored"
      (is (not (reg/contains-identifier? "seven gilded ravens" ctx)))
      (is (not (reg/contains-identifier? "anything" {:username "an"}))))
    (testing "only checked when context is supplied"
      (is (nil? (messages "thornwood thornwood"))))))

(deftest no-composition-rules
  (testing "NIST 800-63B-4: character-class requirements shall not be imposed"
    (is (nil? (messages "correct horse battery staple")))
    (is (nil? (messages "ALLUPPERCASELETTERS")))
    (is (nil? (messages "917462083512")))))

(deftest registration-threads-context-through
  (let [errs (reg/validate-registration
              {:email "ana@example.com" :verify-email "ana@example.com"
               :username "thornwood" :password "thornwood1"}
              false false)]
    (testing "the identifier rule fires through the registration path"
      (is (some #(re-find #"username and email" %) (:password errs))))))

(deftest separators-do-not-hide-a-pattern
  ;; Both run checks look at NEIGHBOURS, so anything wedged between two
  ;; characters used to hide the pattern from them. "55 5 5 5 5 5" has no three
  ;; in a row and passed every rule at thirteen characters.
  (are [password] (seq (:password (reg/validate-password password)))
    "55 5 5 5 5 5"
    "5 5 5 5 5 5 5"
    "55 55 55 55 55 5 5 5 55"
    "a-b-c-d-e-f-g"
    "tot tot tot tot"
    "abababababab"))

(deftest a-real-passphrase-is-left-alone
  (are [password] (empty? (:password (reg/validate-password password)))
    "correcthorsebatterystaple"
    "purple-lantern-quiet-road"
    "tomato potato soup"
    "a lantern two quiet roads"))

(deftest the-variety-floor-is-low-enough-not-to-bite
  (is (reg/too-few-distinct? "ababababab"))
  (is (reg/too-few-distinct? "5 5 5 5 5 5"))
  (is (not (reg/too-few-distinct? "tomato potato"))
      "six distinct characters, and nothing about it is contrived")
  (is (not (reg/too-few-distinct? "correcthorsebatterystaple"))))

(deftest the-minimum-is-twelve
  ;; NIST SP 800-63B-4 asks fifteen without a second factor; twelve is the
  ;; product call, and the test exists so the number cannot drift silently.
  (is (= 12 reg/min-password-length))
  (is (seq (:password (reg/validate-password "elevenchar1"))))
  (is (empty? (:password (reg/validate-password "twelvechars1")))))


(deftest the-meter-cannot-disagree-with-the-gate
  ;; The property that matters, and the one nothing checked before: a rung of -1
  ;; and a rejection are the same event. The two used to be written separately
  ;; and drifted -- the meter scored Dragon7! five of five while the server
  ;; refused it, and called a twenty-five character passphrase a two.
  (doseq [password ["" "short" "Dragon7!" "elevenchar1" "twelvechars1"
                    "tomato potato soup" "purple-lantern-quiet-road"
                    "a lantern two quiet roads home" "55 5 5 5 5 5"
                    "abababababab" "a-b-c-d-e-f-g" "correcthorsebatterystaple"]]
    (let [{:keys [rung]} (reg/password-strength password)
          rejected? (boolean (seq (:password (reg/validate-password password))))]
      (is (= rejected? (not (and rung (not (neg? rung)))))
          (str "meter and gate disagree about " (pr-str password))))))

(deftest the-meter-reports-real-length-even-when-failing
  ;; The fill is the truth about the string, the colour is the verdict on it.
  ;; A failing password must not look like it has earned a whole slot.
  (is (= [0.0 0.0 0.0 0.0] (:fills (reg/password-strength ""))))
  (let [{:keys [rung fills]} (reg/password-strength "aaa")]
    (is (= -1 rung))
    (is (= 25.0 (first fills)) "three of twelve, not an empty bar and not a full one"))
  (let [{:keys [rung fills]} (reg/password-strength "55 5 5 5 5 5")]
    (is (= -1 rung))
    (is (= 100.0 (first fills)) "twelve characters is twelve characters, even when refused")))

(deftest the-rungs-are-all-reachable
  ;; A bar with a rung nothing can reach is the bug that produced the last
  ;; three revisions of this widget.
  (are [expected password] (= expected (:rung (reg/password-strength password)))
    0 "twelvechars1"
    1 "sixteen chars ok"
    2 "twenty two characters!"
    3 "twenty eight characters long"))


(deftest a-typo-is-offered-a-correction
  ;; Only for a domain CLOSE to a common one. An unknown domain is somebody's
  ;; own mail server, and suggesting they meant gmail would be an insult and a
  ;; wrong answer at once.
  (are [expected address] (= expected (reg/suggest-email-domain address))
    "kaylee@gmail.com"   "kaylee@gmial.com"
    "kaylee@gmail.com"   "kaylee@gmai.com"
    "kaylee@gmail.com"   "kaylee@gmail.con"
    "kaylee@hotmail.com" "kaylee@hotmial.com"
    "kaylee@yahoo.co.uk" "kaylee@yahooo.co.uk"
    nil                  "kaylee@gmail.com"
    nil                  "kaylee@aol.com"
    nil                  "kaylee@serenity.example"
    nil                  "kaylee@my-own-mailserver.dev"
    nil                  "kaylee@"
    nil                  "kaylee"
    nil                  ""
    nil                  nil))

(deftest edit-distance-counts-what-it-says
  (are [n a b] (= n (reg/edit-distance a b))
    0 "gmail.com" "gmail.com"
    1 "gmai.com"  "gmail.com"
    1 "gmail.con" "gmail.com"
    ;; Three, not two: this is plain Levenshtein, where a transposition costs
    ;; two edits rather than one. So "gmial.com" (one transposition) is offered
    ;; a correction and "gmial.con" (transposition AND a wrong letter) is not --
    ;; two independent typos in one domain is where guessing starts being wrong.
    3 "gmial.con" "gmail.com"
    2 "gmial.com" "gmail.com"
    0 "" ""
    3 "abc" ""))

(deftest a-password-and-its-confirmation-are-judged-together
  (testing "a mismatch is its own field's fault"
    (is (= ["Passwords do not match"]
           (:verify-password (reg/password-pair-faults "brass lantern rope" "brass lantern rop" false nil)))))
  (testing "nothing to mismatch while the password is blank"
    (is (nil? (:verify-password (reg/password-pair-faults "" "anything" false nil)))))
  (testing "revealed, the confirmation is not asked for, so it cannot be wrong"
    (is (nil? (:verify-password (reg/password-pair-faults "brass lantern rope" "" true nil)))))
  (testing "the password's own rules still apply either way"
    (is (seq (:password (reg/password-pair-faults "short" "short" true nil)))))
  (testing "context reaches the password rules -- this is the check the reset page lacked"
    (is (some #(re-find #"(?i)username" %)
              (:password (reg/password-pair-faults "kayleekaylee12" "kayleekaylee12" false
                                                   {:username "kaylee"}))))
    (is (empty? (:password (reg/password-pair-faults "kayleekaylee12" "kayleekaylee12" false nil)))
        "and without it the same password passes, which is the gap")))
