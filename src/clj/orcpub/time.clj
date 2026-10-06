(ns orcpub.time
  "Date/time helpers over java.time (clojure.java-time) with clj-time's readable threading
   syntax, e.g. (-> 1 minutes ago), (-> 24 hours from-now).
   Durations: seconds minutes hours millis days. Instants: now ago from-now instant.
   Comparison and arithmetic: before? after? plus minus. Server only; the client uses
   cljs-time. Migration details: UPGRADE_PLAN.md."
  (:require [java-time.api :as t]))

;; =============================================================================
;; Duration Constructors
;; =============================================================================
;; These return java.time.Duration objects for use with `ago` and `from-now`.
;; Designed for readable threading: (-> n unit ago)

(defn seconds
  "Returns a Duration of n seconds.
   Usage: (-> 30 seconds ago) or (-> 5 seconds from-now)"
  [n]
  (t/seconds n))

(defn minutes
  "Returns a Duration of n minutes.
   Usage: (-> 5 minutes ago) or (-> 10 minutes from-now)"
  [n]
  (t/minutes n))

(defn hours
  "Returns a Duration of n hours.
   Usage: (-> 24 hours ago) or (-> 1 hours from-now)"
  [n]
  (t/hours n))

(defn millis
  "Returns a Duration of n milliseconds.
   Usage: (-> 500 millis ago)"
  [n]
  (t/millis n))

(defn days
  "Returns a Duration of n days.
   Usage: (-> 7 days ago) or (-> 30 days from-now)"
  [n]
  (t/days n))

;; =============================================================================
;; Relative Time Functions
;; =============================================================================

(defn now
  "Returns the current instant (java.time.Instant).
   Equivalent to clj-time's (t/now)."
  []
  (t/instant))

(defn ago
  "Subtracts a duration from the current instant.
   Usage: (-> 1 minutes ago) returns an Instant 1 minute in the past.
   
   Can also be called directly: (ago (minutes 5))"
  [duration]
  (t/minus (t/instant) duration))

(defn from-now
  "Adds a duration to the current instant.
   Usage: (-> 1 hours from-now) returns an Instant 1 hour in the future.
   
   Can also be called directly: (from-now (hours 24))"
  [duration]
  (t/plus (t/instant) duration))

;; =============================================================================
;; Instant Coercion
;; =============================================================================

(defn instant
  "Coerces a value to a java.time.Instant. With no argument, the current instant (as `now`);
   a java.util.Date (what Datomic returns) or epoch-millis long is converted; an Instant is
   returned unchanged."
  ([]
   (t/instant))
  ([x]
   (t/instant x)))

;; =============================================================================
;; Comparison Functions
;; =============================================================================

(defn before?
  "Returns true if instant a is before instant b.
   Works with any instant-coercible values (Instant, Date, epoch millis)."
  [a b]
  (t/before? (t/instant a) (t/instant b)))

(defn after?
  "Returns true if instant a is after instant b.
   Works with any instant-coercible values (Instant, Date, epoch millis)."
  [a b]
  (t/after? (t/instant a) (t/instant b)))

;; =============================================================================
;; Duration Arithmetic (for advanced use)
;; =============================================================================

(defn plus
  "Adds a duration to an instant.
   Usage: (plus (now) (hours 24))"
  [instant duration]
  (t/plus instant duration))

(defn minus
  "Subtracts a duration from an instant.
   Usage: (minus (now) (hours 24))"
  [instant duration]
  (t/minus instant duration))
