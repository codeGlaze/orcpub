# Unified build — downloads Datomic Pro once, produces two targets:
#   transactor  — datomic transactor (dev storage protocol)
#   app         — orcpub uberjar runner
#
# Usage via docker-compose-build.yaml (each service sets its target).

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
COPY docker/transactor.properties.template /datomic/transactor.properties.template

RUN mkdir -p /data /log /backups

COPY deploy/start.sh /datomic/start.sh
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


# ── Target: app (orcpub uberjar) ─────────────────────────────
# Builds the uberjar, then copies it into a minimal JRE runner.
FROM clojure:temurin-21-lein-alpine AS app-builder

RUN apk add --no-cache bash maven

# Vendor jars (pdfbox snapshots) → Maven local repo
ADD ./lib/ /root/.m2/repository/

# Install Datomic Pro peer jar from the shared download
COPY --from=datomic-dist /datomic /tmp/datomic-pro
RUN cd /tmp/datomic-pro && bash bin/maven-install && rm -rf /tmp/datomic-pro

WORKDIR /orcpub
COPY project.clj /orcpub/
RUN lein deps

ADD ./ /orcpub

# Three-step build: CLJS, AOT compile, uberjar packaging.
#
# lein's compile task spawns a subprocess that hangs in no-TTY (Docker/CI)
# because non-daemon threads (Datomic Peer, core.async, etc.) started during
# namespace loading prevent JVM exit. Compilation FINISHES but the subprocess
# never exits. lein waits → jar never created.
#
# Fix: pre-compile in step 2 (timeout kills the hung subprocess after .class
# files are written). Step 3's uberjar-package uses ^:replace to remove
# "compile" from prep-tasks entirely — verified to survive the uberjar
# re-merge. Lein just packages the existing .class files without spawning
# a new compile subprocess. See docs/LEIN-UBERJAR-HANG.md

# Step 1: CLJS via figwheel-main (exits cleanly, no hang)
RUN lein run -m figwheel.main -- --build-once prod && \
    test -f resources/public/js/compiled/orcpub.js

# Step 2: AOT compile (compilation finishes, subprocess hangs, timeout kills)
RUN timeout 300 lein with-profile uberjar,uberjar-package compile || true; \
    test -f target/classes/orcpub/server__init.class || exit 1

# Step 3: garden CSS + jar packaging (no compile — .class files from step 2)
RUN timeout 600 lein with-profile uberjar,uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1

# Alpine runner — BusyBox wget handles healthchecks (use -q --spider, not GNU flags)
FROM eclipse-temurin:21-jre-alpine-3.22 AS app

# PDFBox requires fontconfig, fonts, and lcms2 for PDF character sheet generation
RUN apk add --no-cache fontconfig ttf-dejavu freetype lcms2

COPY --from=app-builder /orcpub/target/orcpub.jar /orcpub.jar

# Run as non-root. App only writes to /tmp (world-writable by default).
RUN addgroup -S app && adduser -S -G app app
USER app

ENTRYPOINT ["java", "-jar"]
CMD ["/orcpub.jar"]
