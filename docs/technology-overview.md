# Technology Overview

This document provides an introduction to the technologies used in Dungeon Master's Vault, with special focus on helping developers new to the Clojure ecosystem understand the stack.

## Technology Stack Overview

### Architecture Layers

```
┌─────────────────────────────────────┐
│         Browser (Client)            │
│  ┌─────────────┐ ┌─────────────────┐ │
│  │   Reagent   │ │   re-frame      │ │
│  │ (React UI)  │ │ (State Mgmt)    │ │
│  └─────────────┘ └─────────────────┘ │
│  ┌─────────────────────────────────┐ │
│  │      ClojureScript              │ │
│  │   (Functional Frontend)         │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
                    │
                HTTP/Transit
                    │
┌─────────────────────────────────────┐
│          Server (JVM)               │
│  ┌─────────────┐ ┌─────────────────┐ │
│  │  Pedestal   │ │   Ring          │ │
│  │  (Web App)  │ │ (HTTP Server)   │ │
│  └─────────────┘ └─────────────────┘ │
│  ┌─────────────────────────────────┐ │
│  │         Clojure                 │ │
│  │   (Functional Backend)          │ │
│  └─────────────────────────────────┘ │
└─────────────────────────────────────┘
                    │
                Database
                    │
┌─────────────────────────────────────┐
│           Datomic                   │
│      (Immutable Database)           │
└─────────────────────────────────────┘
```

## Core Technologies

### 1. Clojure

**What is Clojure?**
Clojure is a functional programming language that runs on the Java Virtual Machine (JVM). It emphasizes immutability, functional composition, and interactive development.

**Key Characteristics:**
- **Functional Programming**: Functions are first-class citizens, emphasis on pure functions
- **Immutable Data**: Data structures don't change, preventing many classes of bugs
- **Lisp Syntax**: Code is data (homoiconicity), enabling powerful macros
- **JVM Integration**: Access to Java libraries and mature JVM ecosystem
- **REPL-driven Development**: Interactive programming for rapid feedback

**Example Clojure Code:**
```clojure
;; Define a function to calculate ability modifier
(defn ability-modifier [ability-score]
  (-> ability-score
      (- 10)
      (/ 2)
      (Math/floor)
      (int)))

;; Use the function
(ability-modifier 16) ;; => 3

;; Data transformation with threading macro
(-> {:strength 16, :dexterity 14}
    (update :strength ability-modifier)
    (update :dexterity ability-modifier))
;; => {:strength 3, :dexterity 2}
```

**Why Clojure for orcpub?**
- **Immutable data** prevents character state bugs
- **Functional approach** makes complex D&D rules easier to compose
- **Interactive development** enables rapid iteration on game mechanics
- **Java ecosystem** provides access to PDF generation, web servers, etc.

**Learning Resources:**
- [Clojure Official Guide](https://clojure.org/guides/getting_started)
- [Clojure for the Brave and True](https://www.braveclojure.com/)
- [Living Clojure](https://www.oreilly.com/library/view/living-clojure/9781491909270/)

### 2. ClojureScript

**What is ClojureScript?**
ClojureScript compiles Clojure code to JavaScript, allowing you to use Clojure's syntax and functional programming on the frontend.

**Key Benefits:**
- **Same Language**: Frontend and backend use the same syntax and concepts
- **Google Closure Compiler**: Advanced optimizations and dead code elimination
- **React Integration**: Seamless integration with React ecosystem
- **Functional UI**: Immutable state makes UI behavior predictable

**Example ClojureScript Code:**
```clojure
;; Component definition with Reagent
(defn character-name-input [character]
  [:div.form-group
   [:label "Character Name:"]
   [:input {:type "text"
            :value (:name character)
            :on-change #(dispatch [:character/update-name 
                                  (-> % .-target .-value)])}]])

;; Event handling with re-frame
(reg-event-db
  :character/update-name
  (fn [db [_ new-name]]
    (assoc-in db [:current-character :name] new-name)))
```

**Compilation Process:**
1. ClojureScript source → Google Closure Compiler
2. Dead code elimination and optimization
3. Single JavaScript bundle or advanced compilation

**Learning Resources:**
- [ClojureScript Quick Start](https://clojurescript.org/guides/quick-start)
- [ClojureScript Unraveled](https://funcool.github.io/clojurescript-unraveled/)

### 3. Reagent

**What is Reagent?**
Reagent is a ClojureScript wrapper around React that provides a simpler, more functional approach to building user interfaces.

**Key Features:**
- **Hiccup Syntax**: HTML represented as Clojure data structures
- **Reactive Components**: Automatically re-render when data changes
- **Atoms**: Simple state management with reactive updates
- **React Ecosystem**: Can use React components and libraries

**Component Examples:**
```clojure
;; Simple component
(defn welcome-message [name]
  [:h1 "Welcome, " name "!"])

;; Component with local state
(defn counter []
  (let [count (r/atom 0)]
    (fn []
      [:div
       [:p "Count: " @count]
       [:button {:on-click #(swap! count inc)} "Increment"]])))

;; Using components
(defn app []
  [:div
   [welcome-message "Adventurer"]
   [counter]])
```

**Hiccup Syntax:**
```clojure
;; Clojure data structure
[:div.character-sheet
  [:h2 "Character Details"]
  [:ul
    [:li "Name: " (:name character)]
    [:li "Level: " (:level character)]]]

;; Compiles to HTML
<div class="character-sheet">
  <h2>Character Details</h2>
  <ul>
    <li>Name: Thorin</li>
    <li>Level: 5</li>
  </ul>
</div>
```

**Why Reagent?**
- **Functional**: Components are just functions
- **Declarative**: Describe what the UI should look like
- **Efficient**: Only re-renders when data actually changes
- **Simple**: Less boilerplate than raw React

**Learning Resources:**
- [Reagent Documentation](https://reagent-project.github.io/)
- [Reagent Cookbook](https://github.com/reagent-project/reagent-cookbook)

### 4. re-frame

**What is re-frame?**
re-frame is a state management framework for ClojureScript applications that implements a unidirectional data flow pattern.

**Core Concepts:**

**Events** - Describe what happened:
```clojure
(dispatch [:character/level-up])
(dispatch [:spell/add-to-spellbook spell-id])
```

**Event Handlers** - Update application state:
```clojure
(reg-event-db
  :character/level-up
  (fn [db [_ character-id]]
    (update-in db [:characters character-id :level] inc)))
```

**Subscriptions** - Query application state:
```clojure
(reg-sub
  :character/current-level
  (fn [db [_ character-id]]
    (get-in db [:characters character-id :level])))
```

**Views** - Render UI based on subscriptions:
```clojure
(defn character-level-display [character-id]
  (let [level @(subscribe [:character/current-level character-id])]
    [:div "Level: " level]))
```

**Data Flow Diagram:**
```
Views ────────► Events
  ▲                │
  │                ▼
Subscriptions ◄ Event Handlers
  ▲                │
  │                ▼
  └─── App State ◄─┘
```

**Why re-frame?**
- **Predictable**: Unidirectional data flow makes state changes traceable
- **Testable**: Pure functions are easy to test
- **Debuggable**: Time-travel debugging with re-frame-10x
- **Scalable**: Handles complex application state elegantly

**Learning Resources:**
- [re-frame Documentation](https://day8.github.io/re-frame/)
- [re-frame Tutorial](https://github.com/Day8/re-frame/blob/master/docs/README.md)

### 5. Pedestal

**What is Pedestal?**
Pedestal is a web application framework for Clojure that provides high-performance HTTP services with a focus on interceptors and async processing.

**Key Features:**
- **Interceptors**: Composable middleware system
- **Async Support**: Non-blocking I/O for scalability
- **HTTP/2 Support**: Modern web protocols
- **Development Tools**: Built-in development server and debugging

**Example Route Definition:**
```clojure
(def routes
  [[["/" ^:interceptors [(body-params/body-params)
                         http/html-body]
     ["/characters" ^:interceptors [auth/authenticated]
      {:get  [:character/list get-characters]
       :post [:character/create create-character]}
      ["/:character-id" ^:interceptors [(load-character)]
       {:get    [:character/show show-character]
        :put    [:character/update update-character]
        :delete [:character/delete delete-character]}]]]]])
```

**Interceptor Example:**
```clojure
(def load-character
  {:name ::load-character
   :enter (fn [context]
            (let [character-id (get-in context [:request :path-params :character-id])
                  character (db/get-character character-id)]
              (assoc-in context [:request :character] character)))})
```

**Learning Resources:**
- [Pedestal Documentation](https://pedestal.io/)
- [Pedestal Tutorial](https://pedestal.io/guides/)

### 6. Datomic

**What is Datomic?**
Datomic is an immutable database that stores data as facts with time-based versioning. All data is kept forever, providing complete audit trails.

**Key Concepts:**
- **Immutable**: Data is never updated, only new facts are added
- **Time-based**: Query any point in time
- **Datalog**: Powerful query language based on logic programming
- **ACID**: Full transaction support with consistency guarantees

**Example Queries:**
```clojure
;; Find all characters with level > 5
[:find ?character ?name ?level
 :where
 [?character :character/name ?name]
 [?character :character/level ?level]
 [(> ?level 5)]]

;; Find character's spell list
[:find ?spell-name
 :in $ ?character-id
 :where
 [?character :character/id ?character-id]
 [?character :character/spells ?spell]
 [?spell :spell/name ?spell-name]]
```

**Transaction Example:**
```clojure
;; Add a new character
@(d/transact conn
  [{:character/id (uuid)
    :character/name "Thorin Oakenshield"
    :character/race :dwarf
    :character/class :fighter
    :character/level 1}])
```

**Why Datomic for orcpub?**
- **Audit Trail**: Complete history of character changes
- **Consistency**: ACID properties prevent data corruption
- **Flexibility**: Schema evolution without migrations
- **Query Power**: Complex queries for character statistics

**Learning Resources:**
- [Datomic Documentation](https://docs.datomic.com/)
- [Learn Datalog Today](http://www.learndatalogtoday.org/)

## Supporting Technologies

### Ring
HTTP server abstraction for Clojure web applications. Provides a simple, composable way to handle HTTP requests and responses.

### Transit
Data serialization format for communication between ClojureScript frontend and Clojure backend. More efficient than JSON for Clojure data structures.

### Garden
CSS generation library that allows writing stylesheets in Clojure. Provides composable, programmatic styling.

### Figwheel
Development tool for ClojureScript that provides:
- Hot code reloading
- Browser-connected REPL
- Compile error reporting
- CSS reloading

## Development Tools

### REPL (Read-Eval-Print Loop)
Interactive programming environment that allows:
- Testing functions immediately
- Exploring data structures
- Modifying running programs
- Learning through experimentation

### Leiningen
Build automation tool for Clojure that handles:
- Dependency management
- Project configuration
- Task automation (compile, test, deploy)
- Plugin ecosystem

## Functional Programming Concepts

### Immutability
Data structures never change; operations return new versions:
```clojure
(def character {:name "Aragorn" :level 1})
(def leveled-up (assoc character :level 2))
;; character is unchanged, leveled-up is a new map
```

### Pure Functions
Functions that always return the same output for the same input and have no side effects:
```clojure
(defn calculate-hp [level constitution-modifier]
  (+ level (* level constitution-modifier)))
;; Always returns same result for same inputs
```

### Higher-Order Functions
Functions that take or return other functions:
```clojure
(map ability-modifier [16 14 13 12 15 10])
;; Applies ability-modifier to each value
```

### Function Composition
Combining simple functions to build complex behavior:
```clojure
(def process-character
  (comp calculate-stats
        apply-racial-bonuses
        validate-choices))
```

---

**Next Steps:**
- Practice with the [Getting Started](getting-started.md) guide
- Explore the codebase with our [Source File Guide](source-guide.md)
- Learn the development process in [Development Workflow](development-workflow.md)