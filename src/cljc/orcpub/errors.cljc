(ns orcpub.errors
  "Error handling utilities and error code constants.

  This namespace provides:
  - Error code constants for application-level errors
  - Reusable error handling utilities for common operations
  - Consistent error logging and exception creation")

;; Error code constants
(def bad-credentials :bad-credentials)
(def unverified :unverified)
(def unverified-expired :unverified-expired)
(def no-account :no-account)
(def username-required :username-required)
(def password-required :password-required)
(def too-many-attempts :too-many-attempts)

;; Error handling utilities

(def ^:dynamic *error-prefix*
  "Prefix prepended to log-error output. Rebind to \"TEST_ERROR:\" in
   tests so CI logs can distinguish expected error-path output from
   real failures."
  nil)

(defn log-error
  "Prints `prefix` (e.g. \"ERROR:\") and `message`, then `context` (an optional map) on its
  own line when non-empty. Returns nil.
  When *error-prefix* is bound, it replaces the caller-supplied prefix."
  ([prefix message]
   (println (or *error-prefix* prefix) message))
  ([prefix message context]
   (println (or *error-prefix* prefix) message)
   (when (seq context)
     (println "  Context:" context))))

(defn create-error
  "An ExceptionInfo with message `user-msg` (user-friendly) and ex-data `context` plus
  `:error error-code`, wrapping the optional underlying exception `cause`."
  ([user-msg error-code context]
   (ex-info user-msg (assoc context :error error-code)))
  ([user-msg error-code context cause]
   (ex-info user-msg (assoc context :error error-code) cause)))

(defn with-error-handling*
  "Calls zero-arg `operation-fn` and returns its result; prefer the macros over calling this.
  An ExceptionInfo is re-thrown as-is. Any other exception is logged with `:operation-name`,
  passed to the optional `:on-error` fn, and re-thrown via `create-error` with
  `:user-message`, `:error-code` and `:context` from the opts map."
  [operation-fn {:keys [operation-name user-message error-code context on-error]}]
  (try
    (operation-fn)
    (catch #?(:clj clojure.lang.ExceptionInfo :cljs ExceptionInfo) e
      ;; Re-throw ExceptionInfo as-is (already structured)
      (throw e))
    (catch #?(:clj Exception :cljs :default) e
      (log-error "ERROR:" (str "Failed " operation-name ":")
                 (merge context {:message #?(:clj (.getMessage e) :cljs (.-message e))}))
      (when on-error
        (on-error e))
      (throw (create-error user-message error-code context e)))))

#?(:clj
   (defmacro with-db-error-handling
     "Runs `body` as a \"database operation\" under `with-error-handling*`: a failure is
     logged and re-thrown as an ExceptionInfo carrying `user-message`, with `context` plus
     `:error error-code` as its ex-data. Examples: docs/ERROR_HANDLING.md."
     [error-code context user-message & body]
     `(with-error-handling*
        (fn [] ~@body)
        {:operation-name "database operation"
         :user-message ~user-message
         :error-code ~error-code
         :context ~context})))

#?(:clj
   (defmacro with-email-error-handling
     "Runs `body` as an \"email operation\" under `with-error-handling*`: a failure is logged
     and re-thrown as an ExceptionInfo carrying `user-message`, with `context` plus
     `:error error-code` as its ex-data. Examples: docs/ERROR_HANDLING.md."
     [error-code context user-message & body]
     `(with-error-handling*
        (fn [] ~@body)
        {:operation-name "email operation"
         :user-message ~user-message
         :error-code ~error-code
         :context ~context})))

#?(:clj
   (defmacro with-validation
     "Runs parsing/validation `body`. An ExceptionInfo is re-thrown as-is; any other exception
     (NumberFormatException included) is logged with `context` and re-thrown as an
     ExceptionInfo carrying `user-message`, with `context` plus `:error error-code` as its
     ex-data. Examples: docs/ERROR_HANDLING.md."
     [error-code context user-message & body]
     `(try
        ~@body
        (catch NumberFormatException e#
          (log-error "ERROR:" "Validation failed:" ~context)
          (throw (create-error ~user-message ~error-code ~context e#)))
        (catch clojure.lang.ExceptionInfo e#
          (throw e#))
        (catch Exception e#
          (log-error "ERROR:" "Validation failed:" ~context)
          (throw (create-error ~user-message ~error-code ~context e#))))))