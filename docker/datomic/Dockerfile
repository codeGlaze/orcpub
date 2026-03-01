# Unified build — downloads Datomic Pro once, produces two targets:
#   transactor  — datomic transactor (dev storage protocol)
#   app         — orcpub uberjar runner
#
# Usage: docker compose up --build (each service sets its target).

# ── Shared: download Datomic Pro distribution ─────────────────
FROM alpine:3.22 AS datomic-dist

ENV DATOMIC_VERSION=1.0.7482

RUN apk add --no-cache curl unzip

RUN curl --fail --location --silent --show-error \
      -o /tmp/datomic-pro.zip \
      "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip" \
 && unzip -q /tmp/datomic-pro.zip -d /tmp \
 && mv /tmp/datomic-pro-${DATOMIC_VERSION} /datomic \
 && rm /tmp/datomic-pro.zip


# ── Target: transactor ───────────────────────────────────────
# Runs the Datomic Pro transactor with dev storage protocol.
# Configuration: docker/transactor.properties.template (complete, pre-tuned).
# Secrets (passwords) are substituted at runtime by deploy/start.sh.
FROM eclipse-temurin:21-jre-alpine-3.22 AS transactor

# bash for start.sh, su-exec for privilege drop after fixing bind-mount ownership
RUN apk add --no-cache bash su-exec

COPY --from=datomic-dist /datomic /datomic
WORKDIR /datomic

# Template with ${VAR} placeholders for secrets only.
# All structural config (host, protocol, dirs, memory) is pre-set.
#COPY transactor.properties.template /datomic/transactor.properties

RUN mkdir -p /data /log /backups

COPY start.sh /datomic/start.sh
RUN chmod +x /datomic/start.sh

# Create non-root user for the transactor process.
# NOTE: No USER directive here — container starts as root so start.sh can
# chown bind-mounted volumes (/data, /log, /backups), then drops to datomic
# via su-exec. Build-time chown is useless for bind mounts because the host
# directory's ownership overrides it.
RUN addgroup -S datomic && adduser -S -G datomic datomic

VOLUME ["/data", "/log", "/backups"]
EXPOSE 4334 4335 4336
CMD ["/datomic/start.sh"]
