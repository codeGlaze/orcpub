(ns orcpub.security
  "Login attempt tracking and rate limiting.
   See orcpub.time for date/time utilities."
  (:require [orcpub.time :as time :refer [minutes hours ago]]))

(defn compare-dates [attempt-1 attempt-2]
  (compare (:date attempt-1) (:date attempt-2)))

(def failed-login-attempts-by-username
  (atom {}))

(def failed-login-attempts-by-ip
  (atom {}))

(defn threshold []
  (-> 1 minutes ago))

(defn remove-old [attempts threshold-date]
  (reduce
   (fn [as [k v]]
     (let [past-threshold (into (sorted-set-by compare-dates) (subseq v > {:date threshold-date}))]
       (if (seq past-threshold)
         (assoc as k past-threshold)
         as)))
   {}
   attempts))

(defn add-and-remove-old [key attempt attempts threshold-date]
  (-> attempts
      (remove-old threshold-date)
      (update key #(conj (or % (sorted-set-by compare-dates)) attempt))))

(defn add-failed-login-attempt! [username ip]
  (let [attempt {:user username
                 :ip ip
                 :date (time/now)}]
    (swap! failed-login-attempts-by-username
           #(add-and-remove-old username attempt % (threshold)))
    (swap! failed-login-attempts-by-ip
           #(add-and-remove-old ip attempt % (threshold)))))

(defn threshold-attempts [attempts time]
  (let [thresholded (subseq attempts > {:date time})]
    thresholded))

(defn too-many-attempts-for-username-aux [username attempts-by-username]
  (some-> username
          attempts-by-username
          (threshold-attempts (threshold))
          count
          (>= 5)))

(defn too-many-attempts-for-username? [username]
  (too-many-attempts-for-username-aux
   username
   @failed-login-attempts-by-username))

(defn usernames-for-attempts [attempts]
  (set (map :user attempts)))

(defn ips-for-attempts [attempts]
  (set (map :ip attempts)))

(defn multiple-account-access-aux [ip attempts-by-ip]
  (some-> ip
          attempts-by-ip
          ;; Filter attempts newer than 1 minute ago.
          ;; Uses {:date ...} map because sorted-set compares by :date field.
          (subseq > {:date (-> 1 minutes ago)})
          usernames-for-attempts
          count
          (>= 5)))

(defn multiple-account-access? [ip]
  (multiple-account-access-aux
   ip
   @failed-login-attempts-by-ip))

(defn multiple-ip-attempts-to-same-account-aux
  "True when `username` has failed logins from 3+ distinct IPs in the last minute.
   Returns nil, not false, when the username has no recorded attempts."
  [username attempts-by-username]
  (some-> username
          attempts-by-username
          ;; Filter attempts newer than 1 minute ago.
          ;; Uses {:date ...} map because sorted-set compares by :date field.
          (subseq > {:date (-> 1 minutes ago)})
          ips-for-attempts
          count
          (>= 3)))

;; NOTE unused: nothing calls this. Its sibling too-many-attempts-for-username?
;; is wired into the login handler in routes.clj; this one is not, so a fault in
;; it fails open silently and no test of the wiring would catch it.
(defn multiple-ip-attempts-to-same-account? [username]
  (multiple-ip-attempts-to-same-account-aux
   username
   @failed-login-attempts-by-username))


;; ---------------------------------------------------------------------------
;; Windowed limits for the endpoints that cost something to serve
;;
;; Login has been throttled since the brute-force work. The two endpoints that
;; SEND MAIL never were, and they are the ones a stranger can make expensive:
;; each call spends our sending reputation on a message we did not choose to
;; send. Neither limit may change a response -- an endpoint that answers
;; differently once throttled is an oracle for whether a limit was reached, and
;; on password reset that is the membership test we just closed.
;; ---------------------------------------------------------------------------

;; A limit nobody counts is a limit nobody can tune. A number chosen against a
;; guess about real behaviour -- ten signups an hour from one household -- is
;; only as good as the guess, and the only way to find out it was wrong is to
;; see how often it bites. Steady refusals on the household limits means the
;; number is too low and real people are hitting it; a sudden spike on one host
;; means it is doing its job.
(def ^:private refusals (atom {}))

(defn note-refusal!
  "Records a refusal against a limit, and WHO it was. The count alone cannot
   tell one host refused twenty times from twenty hosts refused once each --
   the first is somebody hammering, the second is a spread, and only the second
   suggests a pool of addresses working through the same list."
  ([what] (note-refusal! what nil))
  ([what who]
   (swap! refusals update what
          (fn [{:keys [n who-set] :or {n 0 who-set #{}}}]
            {:n (inc n) :who-set (cond-> who-set who (conj who))}))
   nil))

(defn take-refusals! []
  (first (reset-vals! refusals {})))

(defn refusal-summary
  "A line for the log, or nil on an hour when nothing was turned away. Each
   limit reports how many refusals and across how many distinct sources, which
   is the difference between one bad actor and a pool of them."
  [counts]
  (when (seq counts)
    (str "limits: "
         (->> counts
              (sort-by (comp - :n val))
              (map (fn [[what {:keys [n who-set]}]]
                     (str (name what) " " n
                          (when (> (count who-set) 1)
                            (str " from " (count who-set) " sources")))))
              (interpose ", ")
              (apply str)))))

(def ^:private sightings (atom {}))

(defn- prune [m cutoff]
  (into {}
        (keep (fn [[k ts]]
                (let [live (remove #(.isBefore % cutoff) ts)]
                  (when (seq live) [k (vec live)]))))
        m))

(defn under-limit?
  "True while `k` has been seen fewer than `limit` times inside `window`, and
   false once it has hit the limit. The sighting is recorded in the same swap
   that answers, so simultaneous callers cannot both be told yes on the last
   slot. `k` is any value -- callers namespace their own, e.g. [:reset email]."
  [k limit window]
  (let [cutoff (ago window)
        [before _] (swap-vals! sightings
                               (fn [m]
                                 (let [m (prune m cutoff)]
                                   (update m k #(conj (vec %) (time/now))))))
        seen (count (remove #(.isBefore % cutoff) (get before k)))
        allowed (< seen limit)]
    ;; The key's first element names the limit, so every caller is counted
    ;; without having to remember to say so.
    (when-not allowed
      (note-refusal! (if (vector? k) (first k) k)
                     (when (vector? k) (second k))))
    allowed))

(defn claim-once?
  "True the first time `k` is claimed inside `window`, false until it lapses."
  [k window]
  (under-limit? k 1 window))

;; One sign-in notice per account per day. An attacker who can trigger the
;; condition can trigger it on a loop, and without this the warning would be a
;; way to mail-bomb any account whose username is public.
(def notice-cooldown-hours 24)

(defn claim-sign-in-notice! [username]
  (claim-once? [:sign-in-notice username] (hours notice-cooldown-hours)))

;; Three reset mails an hour to one address leaves room for "it never arrived,
;; send it again" and takes the bombing ceiling off unlimited. The per-address
;; limit is the one that protects a PERSON; the per-host limit is the one that
;; stops somebody walking a list of addresses through the same endpoint.
(def reset-per-address-hourly 3)
(def reset-per-host-hourly 10)

(defn reset-email-allowed? [email ip]
  (and (under-limit? [:reset-address email] reset-per-address-hourly (hours 1))
       (under-limit? [:reset-host ip] reset-per-host-hourly (hours 1))))

;; What this defends is NOT the account table. An unverified account cannot log
;; in, so bulk signups sit there inert. What signup can be made to do is send
;; mail to an address the person filling the form does not have to own -- the
;; same cannon the reset endpoint was, pointed at whoever you name.
;;
;; So the number is set by the table it must not break, not by the attack. Eight
;; people signing up at one game night, plus retries and a typo or two, has to
;; fit. A bulk run wants thousands and is stopped by any of these numbers.
(def registrations-per-host-hourly 10)

(defn registration-allowed? [ip]
  (under-limit? [:register ip] registrations-per-host-hourly (hours 1)))

(defn busiest
  "The heaviest single user of each limit in the window, whether or not it was
   ever refused. A host that stops at ten signups an hour, every hour, never
   trips anything and so never appears in the refusals at all -- this is the
   only place it shows up."
  []
  (->> @sightings
       (reduce (fn [acc [k ts]]
                 (let [limit (if (vector? k) (first k) k)]
                   (update acc limit (fnil max 0) (count ts))))
               {})))

(defn busiest-summary
  "A line naming any limit whose heaviest user is at least at `floor` of it --
   the quiet version of abuse, which sits just inside every number it is given."
  [highs floor]
  (let [caps {:register registrations-per-host-hourly
              :reset-address reset-per-address-hourly
              :reset-host reset-per-host-hourly}
        near (for [[what n] highs
                   :let [cap (get caps what)]
                   :when (and cap (>= n (* floor cap)))]
               (str (name what) " " n "/" cap))]
    (when (seq near)
      (str "limits, busiest source: " (apply str (interpose ", " near))))))

(defn summary-job
  "Heartbeat job: at most two lines an hour, and silent when there is nothing
   to say. The first is what was turned away and how widely; the second is the
   heaviest user of any limit that is sitting at 80% or more of it."
  [_conn]
  (let [highs (busiest)]
    (when-let [line (refusal-summary (take-refusals!))]
      (println line))
    (when-let [line (busiest-summary highs 0.8)]
      (println line))))
