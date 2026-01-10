# Upgrade Dependencies & Compatibility Notes

This document tracks all dependency changes, compatibility tweaks, and required workarounds as the project is upgraded (Java, Datomic, Pedestal, etc).

## Java 9+/21 & Servlet API
- **Issue:** `javax.servlet.http.HttpServletRequest` is not included in Java 9+.
- **Solution:** Add `[javax.servlet/javax.servlet-api "4.0.1"]` to `project.clj`.
- **Reference:** See Pedestal and Ring issues for details.

## Datomic Pro
- **Upgrade:** Migrated from Datomic Free to Datomic Pro for Java 21 support.
- **Install:** Peer JAR must be placed in `lib/com/datomic/datomic-pro/<version>/`.
- **Reference:** See `AGENTS.md` and `docker/datomic/README.md`.

## Other Notable Upgrades
- **Guava:** Upgraded to `32.1.2-jre` for Java 21 compatibility.
- **Jackson:** Upgraded to `2.15.2` for security and compatibility.
- **Pedestal:** Using `0.7.2` for modern Ring middleware support.

## How to Use This Document
- Add a new section for each upgrade, dependency change, or workaround.
- Link to this file from README.md and reference in code comments as needed.
- Use for onboarding and troubleshooting during upgrades.

---
_Last updated: January 2026_
