(ns orcpub.registration
  (:require [clojure.string :as s]))

(defn fails-match? [regex value]
  (or (nil? value)
      (nil? (re-matches regex value))))

(defn password-strength [password]
  (let [password-missing-special-character? (fails-match? #".*[!@#\$%\^&\*].*" password)
        password-missing-number? (fails-match? #".*[0-9].*" password)
        password-missing-uppercase? (fails-match? #".*[A-Z].*" password)
        password-missing-lowercase? (fails-match? #".*[a-z].*" password)
        password-too-short? (or (nil? password) (< (count password) 8))]
    (count (remove identity
                   [password-missing-special-character?
                    password-missing-number?
                    password-missing-uppercase?
                    password-missing-lowercase?
                    password-too-short?]))))

(def min-password-length
  "Eight is the floor both NIST SP 800-63B-4 and OWASP treat as the absolute
   minimum, and OWASP asks for twelve. Raising it further is a product call, so
   it lives here rather than being spelled out at each use."
  8)

(def ^:private keyboard-rows
  ["qwertyuiop" "asdfghjkl" "zxcvbnm" "1234567890"])

(defn- char-code
  "Clojure yields Characters when seq-ing a string, ClojureScript yields
   one-character strings, and (int \"a\") is 0 there. Ask for the code point."
  [c]
  #?(:clj (int c)
     :cljs (.charCodeAt c 0)))

(defn- windows
  "Every n-character run in s, lower-cased."
  [n s]
  (when (and s (>= (count s) n))
    (map #(apply str %) (partition n 1 (s/lower-case s)))))

(defn repeated-run?
  "Three or more of the same character in a row: aaa, 111, !!!."
  [password]
  (boolean (and password (re-find #"(.)\1{2,}" password))))

(defn sequential-run?
  "Four or more characters that step by one either way -- abcd, 4321 -- or that
   trace a keyboard row, which is the same idea one layout removed."
  [password]
  (let [codes (when password (map char-code (s/lower-case password)))
        steps-by-one? (fn [w] (let [d (map - (rest w) (butlast w))]
                                (or (every? #(= 1 %) d) (every? #(= -1 %) d))))]
    (boolean
     (or (some steps-by-one? (when (and codes (>= (count codes) 4))
                               (partition 4 1 codes)))
         (some (fn [w]
                 (some #(or (s/includes? % w)
                            (s/includes? % (s/reverse w)))
                       keyboard-rows))
               (windows 4 password))))))

(defn contains-identifier?
  "Whether the password carries the username or the local part of the email.
   Three characters is the shortest fragment worth objecting to."
  [password {:keys [username email]}]
  (let [low (s/lower-case (or password ""))
        parts (->> [username (first (s/split (or email "") #"@"))]
                   (remove s/blank?)
                   (map s/lower-case)
                   (filter #(>= (count %) 3)))]
    (boolean (and (seq low) (some #(s/includes? low %) parts)))))

(defn validate-password
  "Messages for everything wrong with a password, keyed :password.

   No composition rules: NIST SP 800-63B-4 says character-class requirements
   shall not be imposed, because they push people toward predictable shapes
   (Capital, then digit, then bang). Length plus refusing the obvious patterns
   is what the guidance asks for instead. Breach screening is separate and
   lives server-side, since it needs the network.

   The single-argument form is kept for callers that have no user context."
  ([password] (validate-password password nil))
  ([password context]
   (let [too-short? (or (nil? password) (< (count password) min-password-length))]
     (cond-> {}
       too-short?
       (update :password conj (str "Use at least " min-password-length " characters"))

       (repeated-run? password)
       (update :password conj "Avoid repeating the same character three times or more")

       (sequential-run? password)
       (update :password conj "Avoid runs like 1234, abcd or qwerty")

       (and context (contains-identifier? password context))
       (update :password conj "Leave your username and email out of your password")))))

(def email-format #"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,64}")

(defn bad-email? [email]
  (fails-match? email-format email))

(defn bad-username? [username]
  (fails-match? #"^[A-Za-z0-9]+$" username))

(defn bad-gmail? [email]
  (when (some? email)
    (let [[_ bad-host] (re-matches #".*(gmial|gmal|gmil|gmai|gmaal|gmiil|gmaail|gmaiil)\.(.*)" email)
          [_ bad-domain] (re-matches #".*gmail\.(cm|co|coom)$" email)]
      (cond
        bad-host (str "Invalid host '" bad-host "' did you mean 'gmail'?")
        bad-domain (str "Invalid host 'gmail." bad-domain "' did you mean 'gmail.com'?")
        :else false))))

(defn bad-hotmail? [email]
  (and email (second (re-matches #".*(hotmil|hotmall|htmail|htmial|hotmial).(.*)" email))))

(defn validate-registration [{:keys [email verify-email username password first-and-last-name]} email-taken? username-taken?]
  (let [bad-email-format? (bad-email? email)
        bad-gmail-domain (bad-gmail? email)
        bad-hotmail-domain (bad-hotmail? email)
        emails-dont-match (not= email verify-email)
        username-too-short? (or (nil? username) (< (count username) 3))
        username-email-format? (not (bad-email? email))
        bad-username-format? (bad-username? username)]
    (cond-> {}
      bad-gmail-domain (update :email conj bad-gmail-domain)
      bad-hotmail-domain (update :email conj (str "Invalid domain '" bad-hotmail-domain "' did you mean 'hotmail'?"))
      email-taken? (update :email conj "Email address is already associated with another account")
      emails-dont-match (update :verify-email conj "Email addresses don't match")
      bad-email-format? (update :email conj (if (s/blank? email)
                                              "Email is required"
                                              "Email is not a valid email format"))
      bad-username-format? (update :username conj "Username must be alphanumeric")
      username-taken? (update :username conj "Username is already taken by another user")
      username-too-short? (update :username conj (if (s/blank? username)
                                                   "Username is required"
                                                   "Username must be at least 3 characters"))
      true (merge (validate-password password {:username username :email email})))))
