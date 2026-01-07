FROM clojure:openjdk-11-lein

# Install useful dev tools (including unzip for Datomic Pro)
RUN apt-get update \
 && apt-get install -y --no-install-recommends rlwrap git curl tmux make git-lfs lsof iproute2 unzip \
    libfreetype6 fontconfig fonts-dejavu-core libxrender1 libxext6 libxi6 libxrandr2 \
 && rm -rf /var/lib/apt/lists/*

# Ensure git-lfs is configured system-wide so LFS files are available in Codespaces
RUN git lfs install --system || true

# Install Leiningen so `lein` is available for LSP and project tasks
RUN curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && /usr/local/bin/lein --version || true

WORKDIR /workspace

# Copy project.clj first (needed to know version)
COPY project.clj /workspace/

# Copy existing lib/ directory first (preserves pdfbox and other vendor deps)
# Create lib/ directory if it doesn't exist, then copy contents
RUN mkdir -p /workspace/lib/
COPY lib/ /workspace/lib/ || true

# Download and install Datomic Pro to lib/ using existing file:lib repository pattern
# Latest version: https://docs.datomic.com/releases-pro.html
ARG DATOMIC_VERSION=1.0.7482
RUN echo "[DOCKER BUILD] Starting Datomic Pro ${DATOMIC_VERSION} installation..." && \
    mkdir -p lib/com/datomic/datomic-pro/${DATOMIC_VERSION} && \
    echo "[DOCKER BUILD] Downloading Datomic Pro zip..." && \
    curl -L -o /tmp/datomic-pro-${DATOMIC_VERSION}.zip \
    "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip" && \
    echo "[DOCKER BUILD] Extracting zip..." && \
    unzip -q /tmp/datomic-pro-${DATOMIC_VERSION}.zip -d /tmp/datomic-extract && \
    DATOMIC_JAR=$(find /tmp/datomic-extract -name "datomic-pro-*.jar" -type f | head -1) && \
    echo "[DOCKER BUILD] Found JAR: ${DATOMIC_JAR}" && \
    cp "${DATOMIC_JAR}" "lib/com/datomic/datomic-pro/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.jar" && \
    echo "[DOCKER BUILD] Verifying JAR placement..." && \
    ls -lh "lib/com/datomic/datomic-pro/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.jar" && \
    rm -rf /tmp/datomic-pro-${DATOMIC_VERSION}.zip /tmp/datomic-extract && \
    echo "[DOCKER BUILD] ✅ Datomic Pro installed to lib/ (file:lib repository pattern)"

# Copy the rest of the source
COPY . /workspace

# Default shell to bash for VS Code terminal
SHELL ["/bin/bash", "-c"]
