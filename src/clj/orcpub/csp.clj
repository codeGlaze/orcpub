(ns orcpub.csp
  "Content Security Policy (CSP) utilities for nonce-based strict mode.

   Generates per-request cryptographic nonces and builds CSP headers
   with 'strict-dynamic' for XSS protection."
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
  "Build a Content-Security-Policy header string with strict-dynamic and nonce.

   Options:
     :dev-mode? - When true, adds ws://localhost:3449 to connect-src for
                  Figwheel hot-reload WebSocket support.

   The resulting CSP:
     - Uses 'strict-dynamic' for script-src (only nonced scripts execute)
     - Allows Google Fonts for styles and fonts
     - Restricts all other sources to 'self'
     - Blocks object embeds, restricts base-uri, frame-ancestors, and form-action"
  [nonce & {:keys [dev-mode?]}]
  (str "default-src 'self'; "
       "script-src 'strict-dynamic' 'nonce-" nonce "'; "
       "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
       "font-src 'self' https://fonts.gstatic.com; "
       "img-src 'self' data: https:; "
       "connect-src 'self'" (when dev-mode? " ws://localhost:3449") "; "
       "object-src 'none'; "
       "base-uri 'self'; "
       "frame-ancestors 'self'; "
       "form-action 'self'"))
