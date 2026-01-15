# Configuration Pattern - Lessons Learned

## Single Source of Truth: Environment Variables

**The Pattern:**
Environment variables are accessed directly via `(env :key-name)` throughout the codebase. How you SET them depends on your deployment method.

**Most Common Use Case: Local Development (Bare Metal)**
- Developers run the app directly via `lein repl` and `lein figwheel`
- Environment variables are set in `profiles.clj` (gitignored, Leiningen standard)
- This is the PRIMARY method for local dev

**Deployment Use Case: Docker**
- Docker deployments define env vars in `docker-compose.yaml`
- This is SECONDARY - used when deploying, not actively developing

**DO NOT:**
- ❌ Create wrapper namespaces around `environ/env`
- ❌ Scatter configuration across multiple files
- ❌ Add abstraction layers that hide the source of truth

**DO:**
- ✅ Define all env vars in `docker-compose.yaml`
- ✅ Use `(env :key-name)` directly where needed
- ✅ Document env vars inline with comments
- ✅ Keep `env.sh.example` as optional local dev convenience

## How to Set Environment Variables

### Method 1: profiles.clj (PRIMARY - Local Development)

**Most common for developers** - Create `profiles.clj` in project root (already gitignored):

```clojure
{:dev {:env {:signature "my-local-dev-secret-20chars"
             :datomic-url "datomic:free://localhost:4334/orcpub"
             :google-client-id "123456789.apps.googleusercontent.com"
             :email-server-url "smtp.gmail.com"
             :email-access-key "your-email@gmail.com"
             :email-secret-key "your-app-password"}}}
```

Then just run: `lein repl`, `lein figwheel` - variables automatically available.

### Method 2: Shell Export (Alternative - Local Development)

```bash
# Option A: One-time export
export GOOGLE_CLIENT_ID="123456.apps.googleusercontent.com"
lein repl

# Option B: Use env.sh (see env.sh.example)
cp env.sh.example env.sh
# Edit env.sh with your values
source env.sh
lein repl
```

### Method 3: docker-compose.yaml (SECONDARY - Deployment Only)

```yaml
# For Docker deployments, not local development
environment:
  GOOGLE_CLIENT_ID: ''
  SIGNATURE: '<change me>'
  DATOMIC_URL: 'datomic:free://datomic:4334/orcpub'
```

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

**Key point**: Same `(env :key)` code works regardless of how you set the variables.

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
# Configuration Sources (pick one based on use case)
profiles.clj                 ← PRIMARY: Local dev (gitignored, use this!)
env.sh.example               ← Alternative: Template for shell export method
docker-compose.yaml          ← SECONDARY: Docker deployment only

# Protection
.gitignore                   ← Protects profiles.clj, .env*, env.sh

# Documentation
README.md                    ← Documents env vars for all methods
docs/CONFIGURATION_PATTERN.md ← This file

# Code (reads from env vars regardless of source)
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
