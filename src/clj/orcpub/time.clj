(ns orcpub.time
  "Date/Time utilities providing clj-time-compatible readable syntax.
   
   =============================================================================
   MIGRATION NOTES
   =============================================================================
   
   This namespace provides helper functions to preserve clj-time's readable
   threading syntax after migrating from clj-time 0.15.0 to clojure.java-time
   1.4.2 (January 2026).
   
   clj-time (built on Joda-Time) is no longer maintained. clojure.java-time
   wraps java.time (JSR-310), the modern Java date/time API.
   
   WHAT THIS NAMESPACE PROVIDES:
   
   Duration constructors (for use with threading macros):
     - (seconds n)  → java.time.Duration
     - (minutes n)  → java.time.Duration  
     - (hours n)    → java.time.Duration
     - (millis n)   → java.time.Duration
     - (days n)     → java.time.Duration
   
   Relative time functions:
     - (ago duration)      → java.time.Instant (past)
     - (from-now duration) → java.time.Instant (future)
     - (now)               → java.time.Instant (current)
   
   Comparison functions:
     - (before? a b) → boolean
     - (after? a b)  → boolean
   
   USAGE EXAMPLES:
   
     ;; Get an instant 1 minute in the past
     (-> 1 minutes ago)
     
     ;; Get an instant 24 hours in the future
     (-> 24 hours from-now)
     
     ;; Get an instant 30 seconds ago
     (-> 30 seconds ago)
     
     ;; Current instant
     (now)
     
     ;; Check if timestamp is older than 24 hours
     (before? (instant some-date) (-> 24 hours ago))
   
   HOW IT WORKS:
   
     1. (minutes 1) returns a java.time.Duration of 1 minute
     2. (ago duration) subtracts that duration from the current instant
     3. The threading macro (-> 1 minutes ago) chains: 1 → (minutes 1) → (ago ...)
   
   FUTURE CONSIDERATION:
   
   If cross-platform (CLJ + CLJS) date/time code is needed, consider migrating
   to cljc.java-time or juxt/tick, which work in both environments. Currently:
     - Server (src/clj): Uses this namespace (clojure.java-time)
     - Client (src/cljs): Uses cljs-time (stale but functional)
   
   See UPGRADE_PLAN.md for full migration details.
   =============================================================================
   "
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
  "Coerces a value to a java.time.Instant.
   - If no argument, returns current instant (same as `now`)
   - If given a java.util.Date, converts it
   - If given an Instant, returns it unchanged
   - If given a long (epoch millis), converts it
   
   Useful for comparing dates from Datomic (which returns java.util.Date)."
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
