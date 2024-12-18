FROM fedora:latest

RUN dnf update -y && dnf install -y curl unzip zip

RUN curl -s "https://get.sdkman.io" | bash \
    && source "/root/.sdkman/bin/sdkman-init.sh" \
    && sdk install java && sdk install sbt

RUN dnf install -y git
RUN mkdir -p /workspace
RUN dnf install -y verilator clangd

WORKDIR /workspace
