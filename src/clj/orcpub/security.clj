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


;; One notice per account per window. An attacker who can trigger the condition
;; can trigger it repeatedly, and without this the defence would be a way to
;; mail-bomb any account whose username you know.
(def notice-cooldown-hours 24)

(def ^:private notices-sent (atom {}))

(defn claim-sign-in-notice!
  "True the first time it is asked about a username inside the cooldown window,
   false every time after. The slot is claimed in the same swap that answers, so
   two simultaneous failures cannot both come away with a yes and send twice."
  [username]
  (let [cutoff (-> notice-cooldown-hours hours ago)
        [before _] (swap-vals! notices-sent
                               (fn [sent]
                                 (-> (into {}
                                           (remove (fn [[_ at]] (.isBefore at cutoff)))
                                           sent)
                                     (assoc username (time/now)))))
        previous (get before username)]
    (or (nil? previous) (.isBefore previous cutoff))))
