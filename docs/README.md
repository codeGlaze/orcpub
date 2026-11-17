# Documentation

Guides for running your own copy of the site, and for working on the code. Start with the section
that matches what you are doing.

## Install, run and maintain the site

| Page | Read it to |
|------|------------|
| [../README.md](../README.md) | Install with Docker or run locally, upgrade, back up, load homebrew |
| [DOCKER.md](DOCKER.md) | Install, upgrade and troubleshoot with Docker Compose or Swarm; secrets; logs and crash files; backups |
| [ENVIRONMENT.md](ENVIRONMENT.md) | Look up any setting in `.env`: what it does, its default, an example |
| [docker-user-management.md](docker-user-management.md) | Create, verify, list and check user accounts with `docker-user.sh` |
| [email-system.md](email-system.md) | Understand which emails the site sends, how mail failures are handled, and what to do when email does not arrive |
| [BRANDING-AND-INTEGRATIONS.md](BRANDING-AND-INTEGRATIONS.md) | Set the site's name, logos, footer, contact details and social links |
| [DOCKER-SECURITY.md](DOCKER-SECURITY.md) | See how the containers are locked down and why |
| [PDF-EXPORT-CAPACITY.md](PDF-EXPORT-CAPACITY.md) | Size the server for character sheet exports |
| [CHARACTER-IMAGE-FETCH.md](CHARACTER-IMAGE-FETCH.md) | Find out why a portrait is missing from a PDF, and how proxies and firewalls affect it |
| [JAVA-COMPATIBILITY.md](JAVA-COMPATIBILITY.md) | Check which Java version to run |
| [../ARTISTS.md](../ARTISTS.md) | Credit portrait artists and set up artist accounts |

## Set up a development machine

| Page | Read it to |
|------|------------|
| [GETTING-STARTED.md](GETTING-STARTED.md) | Go from a fresh clone to a running dev server |
| [STACK.md](STACK.md) | See the technology stack, build commands and pinned dependencies |
| [MIGRATION-INDEX.md](MIGRATION-INDEX.md) | Find the notes on the Java, Datomic, Pedestal and React upgrades |
| [migration/](migration/) | Read the per-topic upgrade notes, including [moving data from Datomic Free](migration/datomic-data-migration.md) |
| [LEIN-UBERJAR-HANG.md](LEIN-UBERJAR-HANG.md) | Understand why the Docker build runs `lein uberjar` in three steps |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Follow the branching, changelog and commit conventions |

## Work on the code

| Page | Read it to |
|------|------------|
| [ERROR_HANDLING.md](ERROR_HANDLING.md) | Use the error handling macros and utilities |
| [clojurescript-type-tolerance.md](clojurescript-type-tolerance.md) | See what ClojureScript tolerates and what crashes the page |
| [ORCBREW_FILE_VALIDATION.md](ORCBREW_FILE_VALIDATION.md) | Understand how `.orcbrew` files are validated on import and export |
| [CONFLICT_RESOLUTION.md](CONFLICT_RESOLUTION.md) | Understand how duplicate keys are handled on import |
| [CONTENT_RECONCILIATION.md](CONTENT_RECONCILIATION.md) | Understand how missing homebrew content is detected and matched |
| [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md) | Look up the required fields for each homebrew content type |
| [MAGIC-WEAPON-DAMAGE.md](MAGIC-WEAPON-DAMAGE.md) | Write the extra damage, crits, charges and resets of a magic weapon as data |
| [PORTRAIT-SERVING.md](PORTRAIT-SERVING.md) | See how the server spends CPU and memory on portrait images |
| [PORTRAIT-COMPOSITOR.md](PORTRAIT-COMPOSITOR.md) | Understand the shape of the portrait drawing code |
| [PORTRAIT-TINTING.md](PORTRAIT-TINTING.md) | Understand how character colours reach the line art |
| [PORTRAIT-PACK.md](PORTRAIT-PACK.md) | Calibrate portrait art pieces |
| [PORTRAIT-STYLES.md](PORTRAIT-STYLES.md) | Change the portrait drawer's styles |

## Common homebrew tasks (for players)

- **Create homebrew:** build it in the app, export it, fix any required-field warnings in the browser console, and export again.
- **Import content:** import the file, resolve any conflicts the dialog lists (rename, skip or replace), then check for missing-content warnings.
- **Fix an import that fails:** open the browser console (F12) and read the validation errors. Valid items are imported and invalid ones are skipped, so you keep what can be recovered.
- **Fix a character with "(not loaded)" content:** import the missing plugin, or use the alternative the page suggests.

## Known limits

- Not every required field is checked on import. Some missing fields silently break a feature. See [HOMEBREW_REQUIRED_FIELDS.md](HOMEBREW_REQUIRED_FIELDS.md).
- You can import one file at a time.

## Agent knowledge base

- [KB index](kb/README.md): verified findings from investigations (agents/develop only).
