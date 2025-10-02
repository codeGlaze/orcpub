# ClojureScript + Node.js + Java + useful tools for Codespaces
FROM mcr.microsoft.com/devcontainers/base:ubuntu-22.04

# Install Java (for Clojure/CLJS), Node.js (for ClojureScript tooling), and Clojure CLI
RUN apt-get update && \
    apt-get install -y openjdk-17-jdk curl npm git rlwrap && \
    curl -O https://download.clojure.org/install/linux-install-1.11.1.1413.sh && \
    chmod +x linux-install-1.11.1.1413.sh && \
    ./linux-install-1.11.1.1413.sh && \
    rm linux-install-1.11.1.1413.sh

# Install npx globally for convenience
RUN npm install -g npx

# Install shadow-cljs globally (if used)
RUN npm install -g shadow-cljs

# Set up useful environment variables
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
ENV PATH=$PATH:/usr/local/bin

# Set workdir
WORKDIR /workspaces/orcpub
