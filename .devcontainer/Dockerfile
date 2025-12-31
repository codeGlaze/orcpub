FROM clojure:openjdk-11-lein

# Install useful dev tools
RUN apt-get update \
 && apt-get install -y --no-install-recommends rlwrap git curl \
 && rm -rf /var/lib/apt/lists/*

# Install Leiningen so `lein` is available for LSP and project tasks
RUN curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein \
    && /usr/local/bin/lein --version || true

WORKDIR /workspace

# Copy project.clj and local libs (if present) for dependency download layer
COPY project.clj /workspace/
COPY lib/ /workspace/lib/
RUN lein deps

# Copy the rest of the source
COPY . /workspace

# Default shell to bash for VS Code terminal
SHELL ["/bin/bash", "-c"]
