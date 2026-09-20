(ns orcpub.registration
  (:require [clojure.string :as s]))

(defn fails-match? [regex value]
  (or (nil? value)
      (nil? (re-matches regex value))))

(def min-password-length
  "NIST SP 800-63B-4 sets fifteen for a site with no second factor, and eight
   only where one exists. Twelve is a deliberate shortfall for a site whose
   worst case is a character sheet, chosen as a product call rather than a
   reading of the standard."
  12)

(def min-distinct-characters
  "Five. Repeats and runs both look at neighbours, so they never see a password
   that is two characters taking turns -- ababababab has no triple and no
   sequence. Counting how many DIFFERENT characters appear catches that whole
   family at once, and five is low enough that a real passphrase never notices:
   \"tomato potato\" already has six."
  5)

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

(defn- undecorated
  "The password with spaces and punctuation taken out. Both run checks below
   look at neighbours, so anything wedged between two characters hides the
   pattern from them: \"5 5 5 5 5 5\" has no three in a row and \"a-b-c-d\" does
   not step by one, while both are exactly what they look like."
  [password]
  ;; Whitespace and ASCII punctuation, spelled out as ranges. \p{L} and \p{N}
  ;; are Java-only -- JavaScript needs the u flag for them and a ClojureScript
  ;; regex literal does not carry one, so the JVM stripped separators and the
  ;; browser silently did not. Naming the separators keeps letters of every
  ;; script, which "not a letter" would have thrown away in the browser.
  (s/replace (or password "") #"[\s!-/:-@\[-`{-~]" ""))

(defn repeated-run?
  "Three or more of the same character in a row: aaa, 111, !!! -- and the same
   once separators are removed, so spacing them out does not get past it."
  [password]
  (let [three-in-a-row? #(boolean (re-find #"(.)\1{2,}" %))]
    (boolean (and password
                  (or (three-in-a-row? password)
                      (three-in-a-row? (undecorated password)))))))

(defn too-few-distinct?
  "Whether the password draws on fewer than min-distinct-characters different
   characters. Separators do not count toward the variety they are hiding."
  [password]
  (let [bare (undecorated password)]
    (and (seq bare) (< (count (set (s/lower-case bare))) min-distinct-characters))))

(defn sequential-run?
  "Four or more characters that step by one either way -- abcd, 4321 -- or that
   trace a keyboard row, which is the same idea one layout removed."
  [password]
  (let [password (when password (undecorated password))
        codes (when (seq password) (map char-code (s/lower-case password)))
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

       (and (not too-short?) (too-few-distinct? password))
       (update :password conj "Mostly the same character over and over. Try a few different words")

       (and context (contains-identifier? password context))
       (update :password conj "Leave your username and email out of your password")))))

(def strength-rungs
  "Character counts at which each of the meter's four slots fills. Length is all
   the meter measures, because it is all that reliably buys anything: a digit
   and a symbol are worth less than four more letters, which is why the
   character-class scoring this replaces was misleading in both directions."
  [12 16 22 28])

(def ^:private slot-edges (into [0] strength-rungs))

(defn- slot-fills
  "How full each of the four slots is, 0 to 100, at this length."
  [length]
  (mapv (fn [[lo hi]]
          (-> (/ (double (- length lo)) (- hi lo))
              (max 0.0)
              (min 1.0)
              (* 100)))
        (partition 2 1 slot-edges)))

(defn password-strength
  "What the meter should show: {:rung r :fills [a b c d]}.

   :rung is nil for an empty field, -1 when a rule is broken, and 0 to 3 for the
   highest slot filled. :fills reports the real length even at -1: the fill is
   the truth about the string and the colour is the verdict on it, so a failing
   password must never look like it has earned a whole slot.

   The verdict comes from validate-password rather than a second set of rules.
   The two used to be written separately and drifted -- this scored Dragon7!
   five out of five while the server refused it, and called a twenty-five
   character passphrase a two. Deriving it makes disagreeing impossible."
  ([password] (password-strength password nil))
  ([password context]
   (let [length (count (or password ""))
         fills (slot-fills length)]
     (cond
       (s/blank? password)
       {:rung nil :fills (slot-fills 0)}

       (seq (:password (validate-password password context)))
       {:rung -1 :fills fills}

       :else
       {:rung (->> strength-rungs
                   (keep-indexed (fn [i rung] (when (>= length rung) i)))
                   last)
        :fills fills}))))

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
