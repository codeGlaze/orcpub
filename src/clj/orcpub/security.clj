(ns orcpub.security
  (:require [java-time.api :as t]))

;; =============================================================================
;; Date/Time Helper Functions
;; =============================================================================
;;
;; These helpers preserve clj-time's readable threading syntax after migrating
;; to clojure.java-time. They allow expressions like:
;;
;;   (-> 1 minutes ago)   ; returns Instant 1 minute in the past
;;   (-> 30 seconds ago)  ; returns Instant 30 seconds in the past
;;   (-> 24 hours ago)    ; returns Instant 24 hours in the past
;;
;; How it works:
;;   1. (minutes 1) returns a java.time.Duration of 1 minute
;;   2. (ago duration) subtracts that duration from the current instant
;;   3. The threading macro (-> 1 minutes ago) chains them together
;;
;; Migration note (2026-01):
;;   Migrated from clj-time 0.15.0 to clojure.java-time 1.4.2.
;;   clj-time provided these functions natively; we recreate them here.
;;   See UPGRADE_PLAN.md for full migration details.
;; =============================================================================

(defn seconds
  "Returns a Duration of n seconds. Use with `ago`: (-> 30 seconds ago)"
  [n] (t/seconds n))

(defn minutes
  "Returns a Duration of n minutes. Use with `ago`: (-> 5 minutes ago)"
  [n] (t/minutes n))

(defn hours
  "Returns a Duration of n hours. Use with `ago`: (-> 24 hours ago)"
  [n] (t/hours n))

(defn millis
  "Returns a Duration of n milliseconds. Use with `ago`: (-> 500 millis ago)"
  [n] (t/millis n))

(defn ago
  "Subtracts a duration from the current instant.
   Usage: (-> 1 minutes ago) returns an Instant 1 minute in the past."
  [duration] (t/minus (t/instant) duration))

;; =============================================================================

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
                 :date (t/instant)}]
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

(defn multiple-ip-attempts-to-same-account-aux [username attempts-by-username]
  (some-> username
          attempts-by-username
          ;; Filter attempts newer than 1 minute ago.
          ;; Uses {:date ...} map because sorted-set compares by :date field.
          (subseq > {:date (-> 1 minutes ago)})
          ips-for-attempts
          count
          (>= 3)))

(defn multiple-ip-attempts-to-same-account? [username]
  (multiple-ip-attempts-to-same-account-aux
  username
  @failed-login-attempts-by-username))
