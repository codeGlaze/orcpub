(ns orcpub.csp
  "Content Security Policy (CSP) utilities for nonce-based strict mode.

   Generates per-request cryptographic nonces and builds CSP headers
   with 'strict-dynamic' for XSS protection."
  (:require [clojure.string :as str])
  (:import [java.security SecureRandom]
           [java.util Base64]))

(def ^:private ^SecureRandom secure-random
  "Thread-safe cryptographically secure random number generator."
  (SecureRandom.))

(defn generate-nonce
  "Generate a 128-bit (16 byte) cryptographically secure nonce, base64-encoded.

   Returns a new unique nonce string for each call. Nonces are used once
   per request to validate legitimate scripts in the CSP header."
  []
  (let [bytes (byte-array 16)]
    (.nextBytes secure-random bytes)
    (.encodeToString (Base64/getEncoder) bytes)))

(defn build-csp-header
  "Build a strict Content-Security-Policy header string for `nonce`.
   Options: :dev-mode? adds ws://localhost:3449 to connect-src; :extra-connect-src and
   :extra-frame-src are seqs of extra origins (from integrations).
   script-src is 'strict-dynamic' plus the nonce, so only nonced scripts execute. Styles and
   fonts also allow Google Fonts, images data: and https:; object-src is 'none'; everything
   else, including base-uri, frame-ancestors and form-action, is 'self'."
  [nonce & {:keys [dev-mode? extra-connect-src extra-frame-src]}]
  (str "default-src 'self'; "
       "script-src 'strict-dynamic' 'nonce-" nonce "'; "
       "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
       "font-src 'self' https://fonts.gstatic.com; "
       "img-src 'self' data: https:; "
       "connect-src 'self'"
       (when (seq extra-connect-src) (str " " (str/join " " extra-connect-src)))
       (when dev-mode? " ws://localhost:3449") "; "
       (when (seq extra-frame-src)
         (str "frame-src 'self' " (str/join " " extra-frame-src) "; "))
       "object-src 'none'; "
       "base-uri 'self'; "
       "frame-ancestors 'self'; "
       "form-action 'self'"))
