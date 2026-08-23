FROM maven:3.9.16-eclipse-temurin-21 AS verify

RUN apt-get update \
    && apt-get install -y --no-install-recommends git python3 python3-yaml rsync \
    && rm -rf /var/lib/apt/lists/*

COPY . /snapshot
WORKDIR /verify
RUN git clone --branch main --single-branch https://github.com/lynelk/CPay.git repo \
    && rsync -a --delete --exclude='.git' /snapshot/ /verify/repo/ \
    && cd /verify/repo \
    && git config user.email 'verification@cito.local' \
    && git config user.name 'Cito Release Verification' \
    && git add -A \
    && git commit -m 'PR 67 verification snapshot' \
    && echo "VERIFY_TREE=$(git rev-parse HEAD^{tree})" \
    && echo "VERIFY_MAIN=$(git rev-parse origin/main)"

WORKDIR /verify/repo/InitializrSpringbootProjectFresh

ENV DB_URL="jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1" \
    DB_USERNAME=sa \
    DB_PASSWORD="" \
    MAIL_USERNAME=noreply@example.com \
    MAIL_PASSWORD="" \
    LOCK_FILE_DIR=/tmp/cpay/locks \
    ACTUATOR_USERNAME=verify-actuator \
    ACTUATOR_PASSWORD=verify-actuator-password \
    ADMIN_API_USERNAME=verify-admin \
    ADMIN_API_PASSWORD=verify-admin-password \
    CALLBACK_SIGNING_SECRET=verify-callback-secret

RUN mvn -B verify --no-transfer-progress -Dspring.flyway.enabled=false
RUN mvn -B org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom --no-transfer-progress -DskipTests
RUN mvn -B org.owasp:dependency-check-maven:check --no-transfer-progress -DfailBuildOnCVSS=9 -Dformat=HTML

WORKDIR /verify/repo
RUN set -euo pipefail; \
    files=$(find InitializrSpringbootProjectFresh/src/main/resources/db/migration -type f -name 'V*.sql' -printf '%f\n' | sort); \
    versions=$(printf '%s\n' "$files" | sed -E 's/^V([0-9]+)__.*/\1/'); \
    dupes=$(printf '%s\n' "$versions" | sort | uniq -d); \
    test -z "$dupes" || { echo "Duplicate Flyway versions found: $dupes" >&2; exit 1; }; \
    test -s Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/native/payments/collect' Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/payments/payout' Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/payment-links' Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/channels' Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/payments/{reference}' Docs/Api/cpay-v2-openapi.yaml; \
    grep -q '/api/v2/balances' Docs/Api/cpay-v2-openapi.yaml; \
    for required in \
      Docs/Readiness/Market-readiness-gates.md \
      Docs/Runbooks/Production-incident-response.md \
      Docs/Runbooks/Provider-certification-checklist.md \
      Docs/Runbooks/Operations-alerts.md \
      Docs/Runbooks/Callback-security-and-requeue.md \
      Docs/Runbooks/Reconciliation-finance-daily-close.md \
      Docs/Architecture/Overview.md \
      Docs/Production-code-controls.md \
      Docs/Readiness/Market-readiness-tracker.md; do test -s "$required"; done; \
    python3 - <<'PY'
import yaml
with open('Docs/Api/cpay-v2-openapi.yaml', encoding='utf-8') as fh:
    spec = yaml.safe_load(fh)
assert spec.get('openapi')
assert '/api/v2/native/payments/collect' in spec['paths']
PY
RUN printf 'verified\n' > /verify/pr67-backend-verified.txt

FROM nginx:1.27-alpine
COPY --from=verify /verify/pr67-backend-verified.txt /usr/share/nginx/html/verification.txt
RUN printf 'PR 67 backend verification passed\n' > /usr/share/nginx/html/index.html
EXPOSE 80
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=3 CMD wget -q --spider http://localhost/ || exit 1
CMD ["nginx", "-g", "daemon off;"]
