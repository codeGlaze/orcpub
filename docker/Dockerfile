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
FROM eclipse-temurin:21-jre-alpine-3.22 AS transactor

RUN apk add --no-cache bash

COPY --from=datomic-dist /datomic /datomic
WORKDIR /datomic

# Configure dev storage protocol (datomic:dev://)
RUN cp config/samples/dev-transactor-template.properties transactor.properties

RUN mkdir -p /data /log
RUN sed -i "s/# data-dir=data/data-dir=\/data/" transactor.properties
RUN sed -i "s/# log-dir=log/log-dir=\/log/" transactor.properties

RUN sed -i "s/host=localhost/host=0.0.0.0/" transactor.properties
RUN sed -i "s/# storage-access=local/storage-access=remote/" transactor.properties

ADD deploy/start.sh /datomic/
RUN chmod +x /datomic/start.sh

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

# WORKAROUND: lein-cljsbuild hangs in no-TTY environments (Docker, CI).
# Three compounding issues: (1) cljsbuild I/O pump hang after compilation,
# (2) cljsbuild hooks fire on compile/jar tasks at plugin load time, and
# (3) non-daemon agent threads after AOT prevent JVM exit.
#
# Fix: cljsbuild config lives in the :cljsbuild-config profile (NOT at the
# top level of project.clj). The uberjar task re-merges from base + [:uberjar],
# so anything not in those profiles is invisible to hooks after re-merge.
# See: docs/LEIN-UBERJAR-HANG.md
#
# Step 1: CLJS only. cljsbuild-config provides build definitions.
#         uberjar-cljs wipes prep-tasks. timeout kills I/O pump hang.
# Step 2: garden CSS + AOT + jar. No cljsbuild config visible after
#         re-merge, so hooks are no-ops. timeout kills agent thread hang.
# NOTE: if a step fails, check whether the artifact exists before assuming
#       a code error. Missing artifact after timeout = build too slow (bump
#       timeout), not a compilation problem. See docs/LEIN-UBERJAR-HANG.md
RUN timeout 120 lein with-profile uberjar,uberjar-cljs,cljsbuild-config cljsbuild once prod; \
    test -f resources/public/js/compiled/orcpub.js || exit 1
RUN timeout 300 lein with-profile +uberjar-package uberjar; \
    test -f target/orcpub.jar || exit 1

# Alpine runner — BusyBox wget handles healthchecks (use -q --spider, not GNU flags)
FROM eclipse-temurin:21-jre-alpine-3.22 AS app

# PDFBox requires fontconfig, fonts, and lcms2 for PDF character sheet generation
RUN apk add --no-cache fontconfig ttf-dejavu freetype lcms2

COPY --from=app-builder /orcpub/target/orcpub.jar /orcpub.jar

ENTRYPOINT ["java", "-jar"]
CMD ["/orcpub.jar"]
