FROM mysql:8.4

USER root
RUN if command -v microdnf >/dev/null 2>&1; then \
        microdnf install -y python3 && microdnf clean all; \
    elif command -v apt-get >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends python3 && rm -rf /var/lib/apt/lists/*; \
    else \
        echo "A Python package manager is required for the verification health endpoint" >&2; exit 1; \
    fi

COPY entrypoint.sh /usr/local/bin/cpay-mysql-verify-entrypoint.sh
RUN chmod +x /usr/local/bin/cpay-mysql-verify-entrypoint.sh

# This service is intentionally ephemeral. Every deployment initializes a brand-new MySQL 8.4
# datadir under /tmp, then exposes an HTTP health file only after MySQL is accepting TCP clients.
# V28 creates append-only audit triggers, so the supported trigger-creation capability is enabled
# at server start without mutating the historical Flyway migration.
ENV PORT=8080
EXPOSE 3306 8080
CMD ["/usr/local/bin/cpay-mysql-verify-entrypoint.sh"]
