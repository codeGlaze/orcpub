(ns orcpub.fork.auth
  "Fork-specific auth and session configuration.
   Public/community edition: short sessions, no login tracking."
  (:require [clojure.string :as s]
            [orcpub.fork.branding :as branding]))

;; ─── Session ────────────────────────────────────────────────────────

(def token-lifetime-hours
  "JWT token lifetime in hours."
  24)

(def track-last-login?
  "Whether to record last-login timestamp on each login."
  false)

(def record-last-login-at-registration?
  "Whether to set initial last-login when a user registers."
  false)

;; ─── Display ────────────────────────────────────────────────────────

(def verification-display-name
  "Name shown in verification and password-reset emails."
  "User")
