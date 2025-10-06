FROM clojure:openjdk-11-lein

# Install useful dev tools
RUN apt-get update && apt-get install -y rlwrap git curl

WORKDIR /workspace

# Copy only project.clj first for dependency download layer
COPY project.clj /workspace/
RUN lein deps

# Copy the rest of the source
COPY . /workspace

# Default shell to bash for VS Code terminal
SHELL ["/bin/bash", "-c"]
