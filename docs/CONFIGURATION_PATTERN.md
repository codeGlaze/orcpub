# Configuration Pattern - Lessons Learned

## Single Source of Truth: docker-compose.yaml

**The Pattern:**
Environment variables are defined ONCE in `docker-compose.yaml` and accessed directly via `(env :key-name)` throughout the codebase.

**DO NOT:**
- ❌ Create wrapper namespaces around `environ/env`
- ❌ Scatter configuration across multiple files
- ❌ Add abstraction layers that hide the source of truth

**DO:**
- ✅ Define all env vars in `docker-compose.yaml`
- ✅ Use `(env :key-name)` directly where needed
- ✅ Document env vars inline with comments
- ✅ Keep `env.sh.example` as optional local dev convenience

## Existing Pattern Examples

```clojure
;; routes.clj:56 - Direct usage
(def backend (backends/jws {:secret (env :signature)}))

;; email.clj:31-36 - Multiple direct calls
(defn email-cfg []
  {:user (env :email-access-key)
   :pass (env :email-secret-key)
   :host (env :email-server-url)
   ...})

;; system.clj:37 - Conditional with direct usage
(if-let [datomic-url (env :datomic-url)]
  (str datomic-url)
  ...)
```

## Google Drive Integration Implementation

### Backend: index.clj (single location)
```clojure
;; Inject Client ID if configured
(when-let [client-id (env :google-client-id)]
  (when-not (str/blank? client-id)
    (list
     [:script (str "window.googleClientId = \"" client-id "\";")]
     [:script {:src "https://apis.google.com/js/api.js"}]
     [:script {:src "https://accounts.google.com/gsi/client"}])))
```

### Frontend: Direct access
```clojure
;; In ClojureScript, just read from window
(defn google-drive-enabled? []
  (some? (.-googleClientId js/window)))
```

### Configuration: docker-compose.yaml
```yaml
environment:
  GOOGLE_CLIENT_ID: ''  # Leave empty to disable, set to enable
```

## Why This Matters

**One place to look** for configuration → easier debugging
**One place to change** configuration → fewer errors
**No indirection** → clear data flow
**Follows existing patterns** → maintainable

## File Structure

```
docker-compose.yaml          ← SINGLE SOURCE OF TRUTH
env.sh.example               ← Optional local dev template
.gitignore                   ← Protects .env* files
README.md                    ← Documents env vars

src/clj/orcpub/
  ├── routes.clj             ← Uses (env :signature)
  ├── email.clj              ← Uses (env :email-*)
  ├── system.clj             ← Uses (env :datomic-url)
  └── index.clj              ← Uses (env :google-client-id)
```

## Key Insight

If you're tempted to create a "config namespace" - **stop and ask:**
- Does this add real value or just indirection?
- Is the existing pattern (`(env :key)`) insufficient?
- Am I following the codebase's established patterns?

**The answer is usually: use `(env :key)` directly.**
