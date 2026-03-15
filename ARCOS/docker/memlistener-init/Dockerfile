FROM ollama/ollama:latest AS ollama-bin

FROM debian:bookworm-slim

COPY --from=ollama-bin /bin/ollama /usr/local/bin/ollama

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/*

COPY provision.sh /provision.sh
RUN chmod +x /provision.sh

ENTRYPOINT ["/provision.sh"]
