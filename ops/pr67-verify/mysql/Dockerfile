FROM maven:3.9.16-eclipse-temurin-21

RUN apt-get update \
    && apt-get install -y --no-install-recommends git python3 \
    && rm -rf /var/lib/apt/lists/*

ARG VERIFY_REF=feature/cito-platform-expansion-integrated
WORKDIR /verify

RUN git clone --branch "${VERIFY_REF}" https://github.com/lynelk/CPay.git repo \
    && cd repo \
    && echo "VERIFY_HEAD=$(git rev-parse HEAD)"

WORKDIR /verify/repo/InitializrSpringbootProjectFresh
RUN mvn -B dependency:go-offline --no-transfer-progress -DskipTests

ENV ACTUATOR_USERNAME=verify-actuator \
    ACTUATOR_PASSWORD=verify-actuator-password \
    ADMIN_API_USERNAME=verify-admin \
    ADMIN_API_PASSWORD=verify-admin-password \
    CALLBACK_SIGNING_SECRET=verify-callback-secret \
    MERCHANT_CHANNEL_ENCRYPTION_KEY=Y2ktb25seS1ub3QtYS1wcm9kdWN0aW9uLWtleQ== \
    CPAY_KEY_ENCRYPTION_KEY=Y2ktb25seS1ub3QtYS1wcm9kdWN0aW9uLWtleQ==

CMD ["sh", "-lc", "set -euo pipefail; mvn -B test --no-transfer-progress -Dtest=FlywayMigrationSmokeTest; mkdir -p /tmp/verify-http; printf 'PR 67 clean MySQL migration verification passed\\n' > /tmp/verify-http/index.html; cd /tmp/verify-http; python3 -m http.server ${PORT:-8080}"]
