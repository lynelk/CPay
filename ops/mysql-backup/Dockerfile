FROM debian:bookworm-slim

# Operational certification marker: rebuild after the 2026-09-03 production recovery.
RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       awscli ca-certificates default-mysql-client gzip coreutils python3 \
    && rm -rf /var/lib/apt/lists/*

COPY backup.sh /usr/local/bin/cpay-backup
RUN chmod 0755 /usr/local/bin/cpay-backup

ENTRYPOINT ["/usr/local/bin/cpay-backup"]
