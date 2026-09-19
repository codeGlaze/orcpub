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
        seen (count (remove #(.isBefore % cutoff) (get before k)))]
    (< seen limit)))

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

;; Generous for a household or a game night, useless for bulk signup.
(def registrations-per-host-hourly 3)

(defn registration-allowed? [ip]
  (under-limit? [:register ip] registrations-per-host-hourly (hours 1)))
