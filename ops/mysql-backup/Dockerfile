FROM debian:bookworm-slim

RUN apt-get update \
    && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends \
       awscli ca-certificates default-mysql-client gzip coreutils python3 \
    && rm -rf /var/lib/apt/lists/*

COPY backup.sh /usr/local/bin/cpay-backup
RUN chmod 0755 /usr/local/bin/cpay-backup

ENTRYPOINT ["/usr/local/bin/cpay-backup"]
