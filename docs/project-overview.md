# Project Overview

## What is Dungeon Master's Vault?

Dungeon Master's Vault (formerly OrcPub2) is a web-based D&D 5th Edition character sheet generator and management system. It provides players and dungeon masters with powerful tools to create, customize, and manage D&D characters through an intuitive web interface.

## Key Features

### 🎲 Character Creation & Management
- **Interactive Character Builder**: Step-by-step character creation with real-time validation
- **Complete D&D 5e Support**: Races, classes, backgrounds, spells, equipment, and more
- **Character Sheet PDF Export**: Generate professional character sheets for offline play
- **Cloud Storage**: Save and manage multiple characters online
- **Party Management**: Organize characters into adventuring parties

### 🎨 User Experience
- **Responsive Design**: Works seamlessly on desktop, tablet, and mobile devices
- **Real-time Updates**: Character changes are reflected immediately
- **Intuitive Interface**: Clean, user-friendly design that guides new players
- **Spell Management**: Organized spellbook with filtering and search capabilities

### 🛠 Technical Features
- **Plugin Architecture**: Extensible system for adding custom content (homebrew)
- **RESTful API**: Programmatic access to character data
- **Multi-user Support**: User accounts, authentication, and data isolation
- **Docker Deployment**: Easy self-hosting with containerization

## Project History

Dungeon Master's Vault is a community fork of OrcPub2, originally created by Larry Christensen. The project was forked in January 2019 to ensure continued development and community maintenance after the original project became inactive.

### Key Milestones
- **2019**: Community fork created from original OrcPub2 codebase
- **2019-2024**: Ongoing community development, bug fixes, and improvements
- **Present**: Active open-source project with Docker deployment support

## Architecture Overview

### High-Level Architecture

```
┌─────────────────┐    ┌──────────────────┐    ┌─────────────────┐
│   Web Browser   │◄──►│  Clojure Server  │◄──►│  Datomic DB     │
│  (ClojureScript)│    │   (Pedestal)     │    │  (Character     │
│   + Reagent     │    │                  │    │   Data)         │
└─────────────────┘    └──────────────────┘    └─────────────────┘
```

### Technology Stack

**Frontend (Client-side)**
- **ClojureScript**: Compiled to JavaScript, runs in browser
- **Reagent**: React wrapper for ClojureScript UI components
- **re-frame**: State management and event handling framework

**Backend (Server-side)**
- **Clojure**: JVM-based functional programming language
- **Pedestal**: Web application framework for HTTP services
- **Ring**: HTTP server abstraction layer

**Data & Storage**
- **Datomic**: Immutable database for character and game data
- **Transit**: Data serialization between client and server

## Core Concepts

### Entity-Modifier System

The application is built around a sophisticated **entity-modifier architecture** that models how D&D character building actually works:

**Entities** represent characters as collections of hierarchical choices:
```clojure
{:options {:race {:key :elf
                  :options {:subrace {:key :high-elf}}}}}
```

**Modifiers** apply effects from character options:
```clojure
{:modifiers [(modifier ?dex-bonus (+ ?dex-bonus 2))
             (modifier ?race "Elf")]}
```

**Built Characters** are the final result of applying all modifiers:
```clojure
{:race "Elf"
 :subrace "High Elf"
 :dex-bonus 2}
```

This architecture provides several advantages:
- **Traceability**: Always know which options created which bonuses
- **Extensibility**: Easy to add new content without changing core logic
- **Maintainability**: No central calculation functions that become unmaintainable
- **Flexibility**: Rules can override and modify other rules dynamically

### Plugin System

The modifier system enables a powerful plugin architecture where new content can be added as data rather than code changes. This allows for:
- **Official Content**: Support for published D&D books
- **Homebrew Content**: User-created races, classes, spells, etc.
- **House Rules**: Campaign-specific modifications and additions

## Project Goals

### Primary Objectives
1. **Accessibility**: Make D&D character creation accessible to new and experienced players
2. **Community**: Provide a self-hostable alternative to commercial tools
3. **Extensibility**: Support homebrew and custom content through data-driven architecture
4. **Education**: Serve as an example of functional programming in web applications

### Technical Goals
1. **Maintainability**: Clean, functional architecture that's easy to understand and modify
2. **Performance**: Efficient client-server communication and responsive UI
3. **Reliability**: Robust error handling and data persistence
4. **Scalability**: Support for multiple users and large amounts of game content

## Getting Involved

Whether you're interested in:
- Adding new D&D content or features
- Learning Clojure and functional programming
- Improving documentation and user experience
- Testing and reporting bugs

There are opportunities to contribute at all skill levels. See our [Contributing Guide](contributing.md) to learn more about getting involved in the project.

---

**Next Steps**: 
- Set up your development environment with our [Getting Started](getting-started.md) guide
- Learn about the technologies used in our [Technology Overview](technology-overview.md)