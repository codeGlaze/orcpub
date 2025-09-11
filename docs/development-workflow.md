# Development Workflow

This guide covers the daily development process, including building, testing, debugging, and common development tasks. It's designed for developers at all levels working on Dungeon Master's Vault.

## Daily Development Process

### 1. Starting Your Development Session

**Preparation Steps:**
```bash
# Navigate to your project directory
cd orcpub

# Pull latest changes from upstream
git fetch upstream
git checkout develop
git merge upstream/develop

# Start your feature branch
git checkout -b feature/your-feature-name
```

**Start Development Servers:**

*Terminal 1 - Database:*
```bash
# Start Datomic (keep running throughout session)
cd lib/datomic-free-0.9.5703
bin/transactor config/samples/free-transactor-template.properties
```

*Terminal 2 - Backend REPL:*
```bash
# Start Clojure REPL with server
lein with-profile +start-server repl
```
In the REPL:
```clojure
;; Initialize database (only needed once)
(init-database)

;; Start web server
(start-server)
```

*Terminal 3 - Frontend Development:*
```bash
# Start ClojureScript compiler and dev server
lein figwheel
```

**Verify Setup:**
- Database running: Check terminal 1 for "System started" message
- Backend running: Visit [http://localhost:8890/health](http://localhost:8890/health)
- Frontend running: Main app at [http://localhost:8890](http://localhost:8890)

### 2. Making Changes

#### Backend Development (Clojure)

**File Types and Locations:**
- **API endpoints**: `src/clj/orcpub/routes.clj`
- **Business logic**: `src/cljc/orcpub/dnd/e5/`
- **Database queries**: `src/clj/orcpub/datomic.clj`

**Development Flow:**
1. Edit Clojure files in your editor
2. Save changes
3. Reload namespace in REPL:
   ```clojure
   ;; Reload a specific namespace
   (require '[orcpub.routes :as routes] :reload)
   
   ;; Or reload all changed namespaces
   (refresh)
   ```

**Example: Adding a New API Endpoint**
```clojure
;; In src/clj/orcpub/routes.clj
(defn get-character-summary
  [{:keys [path-params] :as request}]
  (let [character-id (:character-id path-params)
        character (db/get-character character-id)]
    {:status 200
     :body {:name (:name character)
            :level (:level character)
            :class (:class character)}}))

;; Add route
["/character/:character-id/summary" :get get-character-summary]
```

#### Frontend Development (ClojureScript)

**File Types and Locations:**
- **UI components**: `src/cljs/orcpub/dnd/e5/views.cljs`
- **State management**: `src/cljs/orcpub/dnd/e5/events.cljs`, `src/cljs/orcpub/dnd/e5/subs.cljs`
- **Shared logic**: `src/cljc/orcpub/`

**Development Flow:**
1. Edit ClojureScript files
2. Save changes
3. **Changes appear automatically** in browser (hot-reload)
4. Check browser console for any errors

**Example: Adding a New UI Component**
```clojure
;; In src/cljs/orcpub/dnd/e5/views.cljs
(defn character-summary [character-id]
  (let [character @(subscribe [:character/by-id character-id])]
    [:div.character-summary
     [:h3 (:name character)]
     [:p "Level " (:level character) " " (:class character)]]))

;; Add event handler in events.cljs
(reg-event-fx
 :character/load-summary
 (fn [{:keys [db]} [_ character-id]]
   {:http-xhrio {:method :get
                 :uri (str "/api/character/" character-id "/summary")
                 :response-format (ajax/json-response-format)
                 :on-success [:character/summary-loaded]}}))

;; Add subscription in subs.cljs
(reg-sub
 :character/by-id
 (fn [db [_ character-id]]
   (get-in db [:characters character-id])))
```

#### Shared Code Development (CLJC)

Files with `.cljc` extension run on both client and server:

**Example: Adding D&D Game Logic**
```clojure
;; In src/cljc/orcpub/dnd/e5/character.cljc
(defn calculate-proficiency-bonus [level]
  (-> level
      (dec)
      (quot 4)
      (+ 2)))

(defn calculate-spell-attack-bonus [character]
  (let [level (:level character)
        ability-mod (:casting-ability-modifier character)
        prof-bonus (calculate-proficiency-bonus level)]
    (+ ability-mod prof-bonus)))
```

### 3. Testing Your Changes

#### Running Tests

**All Tests:**
```bash
lein test
```

**Specific Test Namespace:**
```bash
lein test orcpub.entity-spec
```

**Auto-running Tests (continuous):**
```bash
lein test-refresh
```

**Frontend Tests:**
```bash
# ClojureScript tests
lein doo phantom test once
```

#### Writing Tests

**Backend Test Example:**
```clojure
;; In test/clj/orcpub/character_test.clj
(ns orcpub.character-test
  (:require [clojure.test :refer :all]
            [orcpub.dnd.e5.character :as char]))

(deftest proficiency-bonus-test
  (testing "proficiency bonus calculation"
    (is (= 2 (char/calculate-proficiency-bonus 1)))
    (is (= 3 (char/calculate-proficiency-bonus 5)))
    (is (= 6 (char/calculate-proficiency-bonus 17)))))
```

**Frontend Test Example:**
```clojure
;; In test/cljs/orcpub/events_test.cljs
(ns orcpub.events-test
  (:require [cljs.test :refer [deftest is testing]]
            [orcpub.dnd.e5.events :as events]))

(deftest character-creation-test
  (testing "character creation event"
    (let [initial-db {}
          result (events/create-character initial-db [:character/create "Test Character"])]
      (is (contains? (:db result) :current-character))
      (is (= "Test Character" (get-in (:db result) [:current-character :name]))))))
```

#### Manual Testing

**Character Builder Testing Checklist:**
- [ ] Create a new character
- [ ] Select race and verify bonuses apply
- [ ] Select class and verify features appear
- [ ] Level up character and check spell progression
- [ ] Export PDF and verify formatting
- [ ] Save character and reload page

**Browser Testing:**
- Test in multiple browsers (Chrome, Firefox, Safari)
- Test responsive design on mobile screens
- Check browser developer console for errors

### 4. Code Quality and Style

#### Code Formatting

```bash
# Check formatting
lein cljfmt check

# Auto-fix formatting
lein cljfmt fix
```

#### Linting

```bash
# Run code analysis
lein kibit

# Check for common issues
lein eastwood
```

#### Style Guidelines

**Clojure/ClojureScript Style:**
- Use kebab-case for function and variable names
- Use meaningful names that describe purpose
- Keep functions small and focused
- Prefer pure functions over stateful ones

**Example of Good Style:**
```clojure
(defn calculate-armor-class
  "Calculates AC from base AC, dex modifier, and magical bonuses."
  [base-ac dex-modifier magical-bonuses]
  (+ base-ac 
     (min dex-modifier 2) ; Dex modifier capped at +2 for most armor
     (apply + magical-bonuses)))
```

**re-frame Style:**
- Use descriptive event names with namespaces
- Keep event handlers pure (no side effects)
- Use subscriptions for all data queries
- Group related events, subs, and views in same files

## Building and Deployment

### Development Builds

**ClojureScript Development:**
```bash
# Running via figwheel (recommended)
lein figwheel

# Manual compilation
lein cljsbuild once dev
```

**CSS Compilation:**
```bash
# Generate CSS from Garden
lein garden once
```

### Production Builds

**Create Production JAR:**
```bash
# Clean and build for production
lein clean
lein garden once
lein cljsbuild once prod
lein uberjar
```

**Docker Build:**
```bash
# Build Docker image
docker-compose build

# Run production containers
docker-compose up -d
```

### Performance Testing

**Load Testing:**
```bash
# Use Apache Bench for simple load testing
ab -n 1000 -c 10 http://localhost:8890/

# Or use more sophisticated tools like wrk
wrk -t12 -c400 -d30s http://localhost:8890/
```

## Debugging

### Backend Debugging

**REPL Debugging:**
```clojure
;; Add breakpoints with println
(defn problematic-function [input]
  (println "Debug: input is" input)
  (let [result (complex-calculation input)]
    (println "Debug: result is" result)
    result))

;; Inspect database state
(d/q '[:find ?e ?name
       :where [?e :character/name ?name]]
     (d/db conn))

;; Test functions interactively
(calculate-armor-class 12 3 [2 1])
```

**Exception Handling:**
```clojure
(try
  (risky-operation)
  (catch Exception e
    (log/error e "Operation failed")
    {:error (.getMessage e)}))
```

### Frontend Debugging

**Browser Developer Tools:**
- Use React DevTools for component inspection
- Check Network tab for HTTP requests
- Use Console for ClojureScript debugging

**re-frame-10x (Debugging Tool):**
```clojure
;; Add to dev dependencies in project.clj
[day8.re-frame/re-frame-10x "0.3.7"]

;; Enable in development builds
:closure-defines {"re_frame.trace.trace_enabled_QMARK_" true}
```

**Debug Subscriptions:**
```clojure
;; Add debug prints to subscriptions
(reg-sub
 :debug/character-state
 (fn [db _]
   (let [character (:current-character db)]
     (println "Debug: current character is" character)
     character)))
```

### Common Issues and Solutions

#### "Port already in use"
```bash
# Find process using port
lsof -i :8890
kill -9 [PID]

# Or change port in project.clj
:figwheel {:server-port 3450}
```

#### "Connection refused to database"
```bash
# Check if Datomic is running
ps aux | grep transactor

# Restart Datomic transactor
cd lib/datomic-free-0.9.5703
bin/transactor config/samples/free-transactor-template.properties
```

#### "Figwheel not connecting"
1. Check if port 3449 is available
2. Clear browser cache
3. Restart figwheel
4. Check firewall settings

#### "Cannot resolve symbol" in ClojureScript
1. Check namespace requires
2. Ensure symbols are properly exported
3. Check spelling and capitalization
4. Restart figwheel compilation

### Git Workflow

#### Feature Development
```bash
# Create feature branch from develop
git checkout develop
git pull upstream develop
git checkout -b feature/new-spell-system

# Make commits
git add .
git commit -m "Add spell slot calculation logic"

# Push to your fork
git push origin feature/new-spell-system

# Create pull request via GitHub
```

#### Code Review Process

**Before Submitting PR:**
- [ ] All tests pass: `lein test`
- [ ] Code is formatted: `lein cljfmt check`
- [ ] No linting issues: `lein kibit`
- [ ] Manual testing completed
- [ ] Documentation updated if needed

**PR Template:**
```markdown
## Description
Brief description of changes

## Testing
- [ ] Unit tests added/updated
- [ ] Manual testing completed
- [ ] Browser testing (Chrome, Firefox)

## Checklist
- [ ] Tests pass
- [ ] Code formatted
- [ ] Documentation updated
```

## Performance Optimization

### Frontend Performance

**ClojureScript Bundle Size:**
```bash
# Analyze bundle size
lein cljsbuild once prod
wc -c resources/public/js/compiled/orcpub.js
```

**React Performance:**
- Use `reagent.core/with-let` for expensive computations
- Implement `should-component-update` for large lists
- Use `re-frame` subscriptions efficiently

### Backend Performance

**Database Query Optimization:**
```clojure
;; Use specific queries instead of pulling whole entities
[:find ?name ?level
 :in $ ?character-id
 :where 
 [?e :character/id ?character-id]
 [?e :character/name ?name]
 [?e :character/level ?level]]
```

**Caching:**
```clojure
;; Cache expensive calculations
(def spell-list-cache (atom {}))

(defn get-spell-list [class level]
  (if-let [cached (@spell-list-cache [class level])]
    cached
    (let [spells (calculate-spell-list class level)]
      (swap! spell-list-cache assoc [class level] spells)
      spells)))
```

## Troubleshooting

### Build Issues

**Dependencies Not Downloading:**
```bash
# Clear local repository
rm -rf ~/.m2/repository
lein clean
lein deps
```

**ClojureScript Compilation Errors:**
1. Check for syntax errors in `.cljs` files
2. Verify all required namespaces are available
3. Clear compiled output: `lein clean`
4. Restart figwheel

**CSS Not Updating:**
```bash
# Recompile CSS
lein garden once

# Check if Garden is watching files
lein garden auto
```

### Runtime Issues

**Characters Not Saving:**
1. Check browser network tab for failed requests
2. Verify database connection
3. Check server logs for errors
4. Test with simple curl request

**UI Not Updating:**
1. Check browser console for JavaScript errors
2. Verify re-frame events are firing
3. Check subscription queries
4. Use re-frame-10x for debugging

---

**Next Steps:**
- Review our [Contributing Guide](contributing.md) before submitting changes
- Explore advanced topics in our [Technology Overview](technology-overview.md)
- Join the community discussion in GitHub Issues