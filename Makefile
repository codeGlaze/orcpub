# Makefile - helper targets for dev
.PHONY: dev-setup dev-start dev-stop

dev-setup:
	bash ./scripts/dev-setup.sh --no-start

dev-start:
	bash ./scripts/dev-setup.sh --start

dev-stop:
	docker-compose down || true
