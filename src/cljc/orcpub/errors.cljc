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
  "Logs an error message with optional context data.

  Args:
    prefix - A prefix string (e.g., 'ERROR:', 'WARNING:')
    message - The error message
    context - Optional map of context data to log

  When *error-prefix* is bound, it replaces the caller-supplied prefix.

  Example:
    (log-error \"ERROR\" \"Failed to save\" {:user-id 123})"
  ([prefix message]
   (println (or *error-prefix* prefix) message))
  ([prefix message context]
   (println (or *error-prefix* prefix) message)
   (when (seq context)
     (println "  Context:" context))))

(defn create-error
  "Creates a structured exception with ex-info.

  Args:
    user-msg - User-friendly error message
    error-code - Keyword identifying the error type
    context - Map of additional context data
    cause - Optional underlying exception

  Returns:
    An ExceptionInfo with structured data

  Example:
    (create-error \"Unable to save\" :save-failed {:id 123} original-exception)"
  ([user-msg error-code context]
   (ex-info user-msg (assoc context :error error-code)))
  ([user-msg error-code context cause]
   (ex-info user-msg (assoc context :error error-code) cause)))

(defn with-error-handling*
  "Core function for wrapping operations with error handling.

  This is typically not called directly - use the macro versions instead.

  Args:
    operation-fn - Zero-arg function to execute
    opts - Map with keys:
           :operation-name - Name for logging (e.g., 'database transaction')
           :user-message - Message shown to users on failure
           :error-code - Keyword for the error type
           :context - Additional context data
           :on-error - Optional function called with exception

  Returns:
    Result of operation-fn, or re-throws with structured error"
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
     "Wraps database operations with consistent error handling.

     Automatically logs errors and creates user-friendly exceptions.

     Args:
       error-code - Keyword identifying the error type
       context - Map of context data (will be in error's ex-data)
       user-message - User-friendly error message
       & body - Code to execute

     Example:
       (with-db-error-handling :user-creation-failed
         {:username \"alice\"}
         \"Unable to create user. Please try again.\"
         @(d/transact conn [{:db/id \"temp\" :user/name \"alice\"}]))"
     [error-code context user-message & body]
     `(with-error-handling*
        (fn [] ~@body)
        {:operation-name "database operation"
         :user-message ~user-message
         :error-code ~error-code
         :context ~context})))

#?(:clj
   (defmacro with-email-error-handling
     "Wraps email operations with consistent error handling.

     Automatically logs errors and creates user-friendly exceptions.

     Args:
       error-code - Keyword identifying the error type
       context - Map of context data (e.g., {:email \"user@example.com\"})
       user-message - User-friendly error message
       & body - Code to execute

     Example:
       (with-email-error-handling :verification-email-failed
         {:email user-email :username username}
         \"Unable to send verification email.\"
         (postal/send-message config message))"
     [error-code context user-message & body]
     `(with-error-handling*
        (fn [] ~@body)
        {:operation-name "email operation"
         :user-message ~user-message
         :error-code ~error-code
         :context ~context})))

#?(:clj
   (defmacro with-validation
     "Wraps parsing/validation operations with error handling.

     Specifically handles NumberFormatException and other parsing errors.

     Args:
       error-code - Keyword identifying the error type
       context - Map of context data
       user-message - User-friendly error message
       & body - Code to execute

     Example:
       (with-validation :invalid-id
         {:id-string \"abc\"}
         \"Invalid ID format.\"
         (Long/parseLong id-string))"
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