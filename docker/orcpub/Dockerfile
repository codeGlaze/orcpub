# ── Target: app (orcpub uberjar) ─────────────────────────────
# Builds the uberjar, then copies it into a minimal JRE runner.
FROM clojure:temurin-21-lein-alpine AS app-builder

RUN apk add --no-cache bash maven

# Vendor jars (pdfbox snapshots) → Maven local repo
#ADD /root/DMV/lib/ /root/.m2/repository/

# Install Datomic Pro peer jar from the shared download
#COPY --from=datomic-dist /datomic /tmp/datomic-pro
#RUN cd /tmp/datomic-pro && bash bin/maven-install && rm -rf /tmp/datomic-pro

WORKDIR /orcpub
COPY ../../project.clj /orcpub/
RUN lein deps

ADD ../../ /orcpub

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
RUN timeout 900 lein with-profile uberjar,uberjar-package compile || true; \
    test -f target/classes/orcpub/server__init.class || exit 1

# Step 3: garden CSS + jar packaging (no compile — .class files from step 2)
RUN timeout 900 lein with-profile uberjar,uberjar-package uberjar; \
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
