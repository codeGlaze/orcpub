# Test Organization

## Directory Structure

Tests in this project are organized into dedicated subfolders based on their purpose:

```
test/
├── clj/                    # Clojure (JVM) tests
│   └── orcpub/
│       ├── dependencies/   # Dependency integration tests
│       ├── dnd/           # D&D game logic tests
│       ├── entity_spec_test.clj
│       ├── pdf_test.clj
│       ├── routes_test.clj
│       └── security_test.clj
└── cljc/                  # Clojure/ClojureScript shared tests
    └── orcpub/
        ├── dnd/e5/        # D&D 5e specific tests
        ├── entity/        # Entity-related tests
        └── ...
```

## Conventions

### Test File Placement

- **Use dedicated subfolders** for related tests (e.g., `dependencies/`, `dnd/`)
- **Namespace must match directory structure**: 
  - File: `test/clj/orcpub/dependencies/integration_test.clj`
  - Namespace: `(ns orcpub.dependencies.integration-test ...)`
- **Test file naming**: Use `_test.clj` suffix (e.g., `integration_test.clj`, `routes_test.clj`)

### When to Create a Subfolder

Create a dedicated subfolder when:
- Adding tests for a specific domain or feature area (e.g., dependencies, authentication)
- Multiple related test files will exist
- Following established patterns in the codebase

### Test Namespace Naming

- JVM tests: `orcpub.<folder>.<file-name>-test`
- Shared tests: `orcpub.<folder>.<file-name>-test`
- Example: `orcpub.dependencies.integration-test`

## Running Tests

```bash
# Run all tests
lein test

# Run specific namespace
lein test orcpub.dependencies.integration-test

# Run with auto-reload
lein test-refresh
```

## Notes for Contributors

- **Always organize tests in dedicated subfolders** to maintain clean structure
- Check existing folder structure before adding new tests
- Follow namespace naming conventions to ensure test discovery works correctly
- Add integration tests for dependency upgrades to validate runtime behavior

---

*This convention helps maintain organized test structure as the codebase grows and makes tests easier to discover and maintain.*
