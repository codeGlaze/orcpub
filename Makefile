# Makefile - helper targets for dev
.PHONY: dev-setup dev-start dev-stop dev-monitor

dev-setup:
	bash ./scripts/dev-setup.sh --no-start

dev-start:
	bash ./scripts/dev-setup.sh --start

# Open a tmux monitor session (requires tmux in container)
dev-monitor:
	bash ./scripts/dev-monitor.sh

# Interactive dev menu (runs without needing executable bit)
dev-menu:
	bash ./scripts/dev-menu.sh

# Start local bundled Datomic (no Docker)
datomic-start:
	bash ./scripts/start-datomic-auto.sh || true

datomic-stop:
	bash ./scripts/stop-datomic-local.sh || true

# Print datomic listeners for quick diagnosis
datomic-list:
	bash -lc 'bash ./scripts/dev-menu.sh -c datomic_listeners'

# Init the DB (idempotent)
init-db:
	lein run -m orcpub.dev-init

dev-stop:
	docker-compose down || true
