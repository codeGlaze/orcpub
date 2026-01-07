FROM clojure:openjdk-11-lein

# Install useful dev tools
RUN apt-get update \
 && apt-get install -y --no-install-recommends rlwrap git curl tmux make git-lfs lsof iproute2 \
    libfreetype6 fontconfig fonts-dejavu-core libxrender1 libxext6 libxi6 libxrandr2 \
 && rm -rf /var/lib/apt/lists/*

# Ensure git-lfs is configured system-wide so LFS files are available in Codespaces
RUN git lfs install --system || true

# Install Leiningen so `lein` is available for LSP and project tasks
RUN curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && /usr/local/bin/lein --version || true

WORKDIR /workspace

# Copy project.clj and local libs (if present)
# Note: lein deps is run in postCreateCommand where secrets are available
COPY project.clj /workspace/
COPY lib/ /workspace/lib/

# Copy the rest of the source
COPY . /workspace

# Default shell to bash for VS Code terminal
SHELL ["/bin/bash", "-c"]
