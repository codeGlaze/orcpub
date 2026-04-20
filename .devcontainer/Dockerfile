FROM clojure:temurin-21-lein

# Install useful dev tools (including unzip for Datomic Pro)
RUN apt-get update \
 && apt-get install -y --no-install-recommends rlwrap git curl tmux make git-lfs lsof iproute2 unzip rsync maven \
    libfreetype6 fontconfig fonts-dejavu-core fonts-liberation libxrender1 libxext6 libxi6 libxrandr2 \
 && rm -rf /var/lib/apt/lists/*

# Ensure git-lfs is configured system-wide so LFS files are available in Codespaces
RUN git lfs install --system || true

# Install Leiningen so `lein` is available for LSP and project tasks
RUN curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && /usr/local/bin/lein --version || true

WORKDIR /workspace

# Copy project source (lib/ includes pdfbox vendor deps from host)
COPY . /workspace

# Pre-cache Datomic Pro zip so post-create.sh skips the download.
# The actual install (extract, flatten, maven-install) is done by post-create.sh
# because the volume mount overwrites /workspace during container creation.
ARG DATOMIC_VERSION=1.0.7482
RUN curl --fail -L -o "/tmp/datomic-pro-${DATOMIC_VERSION}.zip" \
      "https://datomic-pro-downloads.s3.amazonaws.com/${DATOMIC_VERSION}/datomic-pro-${DATOMIC_VERSION}.zip"

# Default shell to bash for VS Code terminal
SHELL ["/bin/bash", "-c"]
