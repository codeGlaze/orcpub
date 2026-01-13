# OrcPub Development Container
# Requires: Java 8, Leiningen, Node.js (for ClojureScript tooling)

FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Avoid prompts during package installation
ENV DEBIAN_FRONTEND=noninteractive

# Install OpenJDK 8 and other dependencies
RUN apt-get update && apt-get install -y \
    openjdk-8-jdk \
    openjdk-8-jdk-headless \
    curl \
    wget \
    unzip \
    git \
    rlwrap \
    nodejs \
    npm \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

# Set Java 8 as default
ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Verify Java version
RUN java -version

# Install Leiningen
RUN mkdir -p /usr/local/bin \
    && curl -fsSL https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -o /usr/local/bin/lein \
    && chmod +x /usr/local/bin/lein

# Pre-fetch leiningen dependencies as root (will be cached)
RUN lein --version

# Create workspace directory
WORKDIR /workspaces/orcpub

# Switch to vscode user for development
USER vscode

# Ensure lein works for vscode user (will download to user home)
RUN lein --version
