# Makefile - helper targets for dev
.PHONY: dev-setup dev-start dev-stop dev-monitor

dev-setup:
	bash ./scripts/dev-setup.sh --no-start

dev-start:
	bash ./scripts/dev-setup.sh --start

# Open a tmux monitor session (requires tmux in container)
dev-monitor:
	bash ./scripts/dev-monitor.sh

# Start local bundled Datomic (no Docker)
datomic-start:
	bash ./scripts/start-datomic-local.sh || true

datomic-stop:
	bash ./scripts/stop-datomic-local.sh || true

dev-stop:
	docker-compose down || true
