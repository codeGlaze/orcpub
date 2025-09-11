# Source File Guide

This guide provides a comprehensive overview of the orcpub codebase structure, explaining the purpose and organization of major files and directories. Understanding this structure will help you navigate the code and contribute effectively.

## Project Structure Overview

```
orcpub/
├── src/
│   ├── clj/        # Server-side Clojure code
│   ├── cljc/       # Shared code (client & server)
│   └── cljs/       # Client-side ClojureScript code
├── web/cljs/       # Additional frontend code
├── resources/      # Static assets, config files
├── test/           # Test files
├── dev/            # Development utilities
└── docs/           # Project documentation
```

## Code Organization Principles

### By File Extension
- **`.clj`** - Server-only Clojure code (JVM)
- **`.cljs`** - Client-only ClojureScript code (Browser)  
- **`.cljc`** - Cross-platform code (both JVM and Browser)

### By Functionality
- **Infrastructure** - Web servers, database, routing, authentication
- **Game Logic** - D&D rules, character building, calculations
- **UI Components** - User interface, forms, displays
- **Data Models** - Character templates, rules definitions

## Directory Deep Dive

### `src/clj/orcpub/` - Server-Side Code

The server-side code handles HTTP requests, database operations, authentication, and business logic that shouldn't run in the browser.

#### Core Infrastructure Files

**`server.clj`** - Application entry point
```clojure
(defn -main []
  (component/start (s/system :prod)))
```
- Application bootstrap and main entry point
- Starts the web server and all components

**`system.clj`** - Component system configuration
- Defines application components (database, web server, etc.)
- Manages component lifecycle and dependencies
- Uses Stuart Sierra's Component library

**`pedestal.clj`** - HTTP service configuration
- Configures Pedestal web framework
- Defines interceptors for request processing
- Sets up routes and middleware

**`routes.clj`** - HTTP route definitions
- API endpoints for character operations
- Authentication routes
- Static file serving

#### Database & Persistence

**`datomic.clj`** - Database connection and utilities
- Datomic database connection management
- Query helpers and database utilities
- Transaction functions

**`db/schema.clj`** - Database schema definitions
- Datomic schema for characters, users, etc.
- Entity relationships and constraints
- Migration functions

#### Business Logic

**`pdf.clj`** - PDF generation
- Character sheet PDF creation
- Uses Apache PDFBox for PDF manipulation
- Renders character data into printable forms

**`email.clj`** - Email utilities
- User registration emails
- Password reset functionality
- Error notification emails

#### Security & Authentication

**`oauth.clj`** - OAuth authentication
- Third-party login integration
- Token management
- User session handling

**`security.clj`** - Security middleware
- Authentication checks
- Authorization logic
- CSRF protection

**`privacy.clj`** - Privacy and terms handling
- Privacy policy management
- Terms of service
- User consent tracking

#### UI Server-Side Rendering

**`index.clj`** - Main HTML page generation
- Generates the initial HTML page
- Includes ClojureScript application
- Sets up client-side configuration

**`styles/core.clj`** - CSS generation
- Uses Garden library for CSS
- Programmatic stylesheet generation
- Component-specific styling

### `src/cljc/orcpub/` - Shared Code

Code that runs on both client and server, primarily game logic and data structures.

#### Core Entity System

**`entity.cljc`** - Core entity-building engine
```clojure
(defn build-entity [raw-entity template]
  ;; Applies modifiers to create computed character
  )
```
- The heart of the character building system
- Applies modifiers to create final character state
- Handles dependency resolution between modifiers

**`entity_spec.cljc`** - Entity validation
- Clojure specs for entity data structures
- Validation of character data
- Type checking and constraints

**`modifiers.cljc`** - Modifier system implementation
- Defines how modifiers work
- Modifier application logic
- Dependency graph resolution

**`template.cljc`** - Character option templates
- Template parsing and processing
- Option selection validation
- Template merging and inheritance

#### D&D 5e Game Rules

**`dnd/e5/character.cljc`** - Core character logic
- Character creation and advancement
- Ability score calculations
- Level-up mechanics

**`dnd/e5/races.cljc`** - Race definitions
```clojure
{:name "Elf"
 :key :elf
 :modifiers [(modifier ?dex-bonus (+ ?dex-bonus 2))]
 :selections [...]}
```
- All D&D races with their bonuses and features
- Subrace options and modifiers
- Racial spell lists and proficiencies

**`dnd/e5/classes.cljc`** - Class definitions  
- All D&D classes and subclasses
- Class features by level
- Spell progressions and abilities

**`dnd/e5/backgrounds.cljc`** - Background definitions
- Character backgrounds with skills and equipment
- Background features and roleplay hooks

**`dnd/e5/spells.cljc`** - Spell definitions
- Complete D&D spell list
- Spell descriptions, components, and mechanics

**`dnd/e5/equipment.cljc`** - Equipment and items
- Weapons, armor, and adventuring gear
- Equipment stats and properties

**`dnd/e5/feats.cljc`** - Feat definitions
- Optional feats with prerequisites
- Feat bonuses and special abilities

#### UI Components & Utilities

**`components.cljc`** - Reusable UI components
- Form inputs, buttons, modals
- Shared component logic
- Style utilities

**`common.cljc`** - Common utilities
- Helper functions used throughout the app
- Data transformation utilities
- Validation helpers

**`dice.cljc`** - Dice rolling mechanics
- Dice notation parsing ("2d6+3")
- Random number generation
- Dice roll statistics

### `src/cljs/orcpub/` - Client-Side Code

ClojureScript code that runs in the browser, handling UI, user interactions, and client-side state.

#### Main Application

**`dnd/e5.cljc`** - Main D&D 5e application entry
- Application initialization
- Route configuration
- Global event handlers

**`character_builder.cljs`** - Character builder UI
- Step-by-step character creation interface
- Option selection forms
- Real-time character preview

#### re-frame Architecture

**`dnd/e5/events.cljs`** - Event handlers
```clojure
(reg-event-db
 :character/update-race
 (fn [db [_ race-key]]
   (assoc-in db [:current-character :race] race-key)))
```
- All application events
- State update logic
- Side effect coordination

**`dnd/e5/subs.cljs`** - Subscriptions (queries)
```clojure
(reg-sub
 :character/current-level
 (fn [db _]
   (get-in db [:current-character :level])))
```
- Data queries for UI components
- Computed properties and derived state
- Performance optimizations

**`dnd/e5/db.cljs`** - Database schema
- Client-side application state structure
- Initial state definition
- State validation schemas

**`dnd/e5/views.cljs`** - UI components
- Main character sheet display
- Form inputs and controls
- Navigation and layout components

#### Specialized Subscriptions

**`dnd/e5/equipment_subs.cljs`** - Equipment-specific queries
- Equipment lists and filtering
- Inventory management
- Equipment stat calculations

**`dnd/e5/spell_subs.cljs`** - Spell-specific queries
- Spell lists by class and level
- Known spells and spell slots
- Spell filtering and search

#### Development Tools

**`dnd/e5/autosave_fx.cljs`** - Autosave functionality
- Automatic character saving
- Debounced save operations
- Error handling for save failures

**`user_agent.cljs`** - Browser detection
- User agent parsing
- Browser capability detection
- Mobile/desktop detection

## Key Architectural Patterns

### Entity-Modifier Pattern

The core of orcpub's architecture is the entity-modifier system:

1. **Templates** define available options (races, classes, etc.)
2. **Entities** represent character choices as hierarchical selections
3. **Modifiers** from selected options are applied to create final character
4. **Built Characters** are the computed result with all bonuses applied

### re-frame Pattern (Client-Side)

The frontend follows re-frame's unidirectional data flow:

1. **Events** represent user actions or system events
2. **Event Handlers** update application state
3. **Subscriptions** query state for UI display
4. **Views** render UI based on subscription data

### Component System (Server-Side)

The server uses Stuart Sierra's Component pattern:

1. **Components** have start/stop lifecycle
2. **Dependencies** are injected at startup
3. **System** orchestrates component initialization
4. **Reloaded workflow** enables REPL-driven development

## Data Flow Examples

### Character Creation Flow

1. User selects race in UI (`views.cljs`)
2. Dispatches `:character/select-race` event (`events.cljs`)
3. Event handler updates application state (`db.cljs`)
4. Subscription queries updated character data (`subs.cljs`)
5. UI re-renders with new race bonuses (`views.cljs`)
6. Entity system computes final stats (`entity.cljc`)

### Character Save Flow

1. Auto-save triggers save event (`autosave_fx.cljs`)
2. Character data serialized to Transit format
3. HTTP request sent to server (`routes.cljs`)
4. Server validates and stores in Datomic (`datomic.clj`)
5. Success/failure response returned to client
6. UI shows save status indicator

## Common Development Tasks

### Adding a New Race

1. **Define race data** in `dnd/e5/races.cljc`
2. **Add modifiers** for racial bonuses
3. **Update templates** if needed
4. **Test with character builder**

### Adding a New UI Component

1. **Create component function** in appropriate views file
2. **Add subscriptions** if needing app state
3. **Add event handlers** for user interactions  
4. **Style with Garden CSS** in `styles/core.clj`

### Adding a New API Endpoint

1. **Define route** in `routes.clj`
2. **Create handler function** with business logic
3. **Add database queries** if needed
4. **Update client-side HTTP calls**

## Testing Strategy

### Test Organization
- `test/clj/` - Server-side tests
- `test/cljc/` - Shared logic tests  
- `test/cljs/` - Client-side tests

### Key Test Areas
- **Entity building** - Core character logic
- **Modifier application** - Rules engine
- **HTTP APIs** - Server endpoints
- **UI components** - User interface

## Development Tools Integration

### REPL Integration
- **Server REPL** - `lein with-profile +start-server repl`
- **Client REPL** - Available through Figwheel
- **Component reloading** - Modify running system without restart

### Hot Reloading
- **Server code** - Requires REPL reload
- **Client code** - Automatic via Figwheel
- **CSS** - Live updates via Garden

---

**Next Steps:**
- Learn the daily development workflow in [Development Workflow](development-workflow.md)
- Understand how to contribute in [Contributing Guide](contributing.md)
- Practice with the [Getting Started](getting-started.md) setup guide