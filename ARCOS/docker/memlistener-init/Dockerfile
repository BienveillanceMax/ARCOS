FROM python:3.12-slim

RUN apt-get update && \
    apt-get install -y --no-install-recommends curl && \
    rm -rf /var/lib/apt/lists/* && \
    pip install --no-cache-dir huggingface_hub requests

COPY provision.sh /provision.sh
RUN chmod +x /provision.sh

ENTRYPOINT ["/provision.sh"]
