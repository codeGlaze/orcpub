# Error Handling Guide

This document describes the error handling approach used throughout the OrcPub application.

## Overview

The application uses a consistent, DRY approach to error handling built on Clojure's `ex-info` for structured exceptions. All error handling utilities are centralized in the `orcpub.errors` namespace.

## Core Principles

1. **User-Friendly Messages**: All errors include clear, actionable messages for end users
2. **Structured Data**: Errors use `ex-info` with structured data for programmatic handling
3. **Logging**: All errors are logged with context for debugging
4. **Fail Fast**: Operations fail immediately with clear errors rather than silently continuing
5. **DRY**: Common error handling patterns use reusable macros and utilities

## Error Handling Utilities

### Database Operations

Use `with-db-error-handling` for all database transactions:

```clojure
(require '[orcpub.errors :as errors])

(defn save-user [conn user-data]
  (errors/with-db-error-handling :user-save-failed
    {:username (:username user-data)}
    "Unable to save user. Please try again."
    @(d/transact conn [user-data])))
```

**Benefits:**
- Automatically logs database errors with context
- Creates user-friendly error messages
- Includes structured error codes for programmatic handling
- Re-throws `ExceptionInfo` as-is (for already-handled errors)

### Email Operations

Use `with-email-error-handling` for sending emails:

```clojure
(defn send-welcome-email [user-email]
  (errors/with-email-error-handling :welcome-email-failed
    {:email user-email}
    "Unable to send welcome email. Please contact support."
    (postal/send-message config message)))
```

**Benefits:**
- Handles SMTP connection failures gracefully
- Logs email failures for ops monitoring
- Prevents application crashes when email server is down

### Validation & Parsing

Use `with-validation` for parsing user input:

```clojure
(defn parse-user-id [id-string]
  (errors/with-validation :invalid-user-id
    {:id-string id-string}
    "Invalid user ID format. Expected a number."
    (Long/parseLong id-string)))
```

**Benefits:**
- Handles `NumberFormatException` and other parsing errors
- Provides clear validation error messages
- Includes the invalid input in error data for debugging

## Error Data Structure

All errors created by the utilities include:

```clojure
{:error :error-code-keyword    ; Machine-readable error type
 ;; Additional context fields specific to the operation
 :user-id 123
 :operation-specific-data "..."}
```

Example exception:

```clojure
(ex-info "Unable to save user. Please try again."
         {:error :user-save-failed
          :username "alice"
          :message "Connection timeout"}
         original-exception)
```

## Error Codes

Error codes are defined as keywords in `orcpub.errors`:

### Database Errors
- `:party-creation-failed` - Failed to create a party
- `:party-update-failed` - Failed to update party data
- `:party-remove-character-failed` - Failed to remove character from party
- `:party-deletion-failed` - Failed to delete party
- `:verification-failed` - Failed to create verification record
- `:password-reset-failed` - Failed to initiate password reset
- `:password-update-failed` - Failed to update password
- `:entity-creation-failed` - Failed to create entity
- `:entity-update-failed` - Failed to update entity

### Email Errors
- `:verification-email-failed` - Failed to send verification email
- `:reset-email-failed` - Failed to send password reset email
- `:invalid-port` - Invalid email server port configuration

### Validation Errors
- `:invalid-character-id` - Invalid character ID format
- `:invalid-pdf-data` - Invalid PDF request data

### PDF Errors
- `:image-load-timeout` - Image loading timed out
- `:unknown-host` - Unknown host for image URL
- `:invalid-image-format` - Invalid or corrupt image
- `:image-load-failed` - Generic image loading failure
- `:jpeg-load-failed` - JPEG-specific loading failure

## Testing Error Handling

All error handling utilities are fully tested. See `test/clj/orcpub/errors_test.clj` for examples:

```clojure
(deftest test-with-db-error-handling
  (testing "wraps exceptions with proper context"
    (try
      (errors/with-db-error-handling :db-test-error
        {:user-id 789}
        "Unable to save to database"
        (throw (Exception. "Connection timeout")))
      (is false "Should have thrown exception")
      (catch clojure.lang.ExceptionInfo e
        (is (= "Unable to save to database" (.getMessage e)))
        (is (= :db-test-error (:error (ex-data e))))
        (is (= 789 (:user-id (ex-data e))))))))
```

## Migration Guide

### Before (Bespoke Error Handling)

```clojure
(defn create-party [conn party]
  (try
    (let [result @(d/transact conn [party])]
      {:status 200 :body result})
    (catch Exception e
      (println "ERROR: Failed to create party:" (.getMessage e))
      (throw (ex-info "Unable to create party. Please try again."
                      {:error :party-creation-failed}
                      e)))))
```

### After (DRY with Utilities)

```clojure
(defn create-party [conn party]
  (errors/with-db-error-handling :party-creation-failed
    {:party-data party}
    "Unable to create party. Please try again."
    (let [result @(d/transact conn [party])]
      {:status 200 :body result})))
```

**Benefits of migration:**
- 7 lines → 5 lines (30% reduction)
- Consistent error logging format
- No need to remember logging syntax
- Easier to read and maintain

## Best Practices

### DO:
- Use the provided macros for common operations
- Include relevant context in error data
- Write user-friendly error messages
- Test error handling paths

### DON'T:
- Catch exceptions without re-throwing
- Use generic error messages like "An error occurred"
- Log errors without context
- Silently swallow exceptions

## Client-Side API Response Handling

All API-calling re-frame subscriptions use the `handle-api-response` HOF from `events.cljs`:

```clojure
(require '[orcpub.dnd.e5.events :refer [handle-api-response]])

;; Basic usage — 401 routes to login, 500 shows generic error
(handle-api-response response
  #(dispatch [::set-data (:body response)])
  :context "fetch characters")

;; Custom overrides
(handle-api-response response
  #(dispatch [::set-data (:body response)])
  :on-401 #(when-not login-optional? (dispatch [:route-to-login]))
  :on-500 #(when required? (dispatch (show-generic-error)))
  :context "fetch user")
```

**Defaults:**
- 200: calls `on-success`
- 401: dispatches `:route-to-login` (override with `:on-401`)
- 500: dispatches `show-generic-error` (override with `:on-500`)
- Any other status: logs to console with `:context` string

This prevents the class of bug where a bare `case` with no default clause crashes on unexpected HTTP statuses.

## Future Improvements

Potential enhancements to consider:

1. **Retry Logic**: Add automatic retry for transient failures (network, db)
2. **Circuit Breakers**: Prevent cascading failures in external dependencies
3. **Error Monitoring**: Integration with error tracking services (Sentry, Rollbar)
4. **Rate Limiting**: Add rate limiting context to prevent abuse
5. **Internationalization**: Support multiple languages for error messages

## Related Files

- `src/cljc/orcpub/errors.cljc` - Error handling utilities (backend)
- `src/cljs/orcpub/dnd/e5/events.cljs` - `handle-api-response` HOF (client-side)
- `test/clj/orcpub/errors_test.clj` - Comprehensive test suite
- `src/clj/orcpub/email.clj` - Email operations with error handling
- `src/clj/orcpub/datomic.clj` - Database connection with error handling
- `src/clj/orcpub/routes.clj` - HTTP routes with error handling
- `src/clj/orcpub/routes/party.clj` - Party operations (demonstrates DRY refactoring)
- `src/clj/orcpub/pdf.clj` - PDF generation with timeout and error handling
