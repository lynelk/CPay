#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

required=(MYSQL_HOST MYSQL_USER MYSQL_PASSWORD MYSQL_DATABASE S3_BUCKET S3_REGION S3_ENDPOINT AWS_ACCESS_KEY_ID AWS_SECRET_ACCESS_KEY)
for name in "${required[@]}"; do
  if [[ -z "${!name:-}" ]]; then
    echo "Required variable is missing: ${name}" >&2
    exit 1
  fi
done

MYSQL_PORT="${MYSQL_PORT:-3306}"
BACKUP_PREFIX="${BACKUP_PREFIX:-mysql/daily}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
MODE="${MODE:-backup}"
workdir="$(mktemp -d)"
defaults_file="${workdir}/client.cnf"
trap 'rm -rf "${workdir}"' EXIT

cat >"${defaults_file}" <<EOF
[client]
host=${MYSQL_HOST}
port=${MYSQL_PORT}
user=${MYSQL_USER}
password=${MYSQL_PASSWORD}
protocol=TCP
EOF

s3=(aws --endpoint-url "${S3_ENDPOINT}" --region "${S3_REGION}")

run_backup() {
  local timestamp object dump checksum local_size remote_size
  timestamp="$(date -u +%Y-%m-%dT%H-%M-%SZ)"
  object="${BACKUP_PREFIX}/cpay-mysql-${timestamp}.sql.gz"
  dump="${workdir}/backup.sql.gz"
  checksum="${dump}.sha256"

  dump_options=(
    --defaults-extra-file="${defaults_file}"
    --single-transaction --quick --routines --triggers --events --hex-blob
    --no-tablespaces --skip-lock-tables --databases "${MYSQL_DATABASE}"
  )
  if mysqldump --help 2>/dev/null | grep -q -- '--set-gtid-purged'; then
    dump_options+=(--set-gtid-purged=OFF)
  fi

  echo "Creating transaction-consistent backup ${object}"
  mysqldump "${dump_options[@]}" | gzip -9 >"${dump}"
  gzip -t "${dump}"
  [[ -s "${dump}" ]]
  sha256sum "${dump}" | sed "s#${dump}#$(basename "${dump}")#" >"${checksum}"

  "${s3[@]}" s3 cp "${dump}" "s3://${S3_BUCKET}/${object}" --only-show-errors
  "${s3[@]}" s3 cp "${checksum}" "s3://${S3_BUCKET}/${object}.sha256" --only-show-errors

  local_size="$(stat -c %s "${dump}")"
  remote_size="$("${s3[@]}" s3api head-object --bucket "${S3_BUCKET}" --key "${object}" --query ContentLength --output text)"
  [[ "${local_size}" = "${remote_size}" && "${remote_size}" -gt 0 ]]

  RETENTION_CUTOFF="$(date -u -d "${RETENTION_DAYS} days ago" +%Y-%m-%dT%H:%M:%SZ)" \
  S3_ARGS="$(printf '%q ' "${s3[@]}")" S3_BUCKET_VALUE="${S3_BUCKET}" BACKUP_PREFIX_VALUE="${BACKUP_PREFIX}" \
  python3 - <<'PY'
import json, os, shlex, subprocess
cmd = shlex.split(os.environ['S3_ARGS']) + [
    's3api', 'list-objects-v2', '--bucket', os.environ['S3_BUCKET_VALUE'],
    '--prefix', os.environ['BACKUP_PREFIX_VALUE'] + '/', '--output', 'json'
]
objects = json.loads(subprocess.check_output(cmd, text=True)).get('Contents', [])
cutoff = os.environ['RETENTION_CUTOFF']
for item in objects:
    if item['LastModified'] < cutoff:
        subprocess.run(shlex.split(os.environ['S3_ARGS']) + [
            's3api', 'delete-object', '--bucket', os.environ['S3_BUCKET_VALUE'], '--key', item['Key']
        ], check=True)
PY

  echo "BACKUP_OK key=${object} size=${remote_size} sha256=$(cut -d' ' -f1 "${checksum}")"
}

run_restore_validation() {
  local object dump expected actual table_count flyway_version
  object="${RESTORE_OBJECT_KEY:-}"
  if [[ -z "${object}" ]]; then
    object="$("${s3[@]}" s3api list-objects-v2 --bucket "${S3_BUCKET}" --prefix "${BACKUP_PREFIX}/" \
      --query 'reverse(sort_by(Contents,&LastModified))[?ends_with(Key,`.sql.gz`)]|[0].Key' --output text)"
  fi
  [[ -n "${object}" && "${object}" != "None" ]]
  dump="${workdir}/restore.sql.gz"
  "${s3[@]}" s3 cp "s3://${S3_BUCKET}/${object}" "${dump}" --only-show-errors
  "${s3[@]}" s3 cp "s3://${S3_BUCKET}/${object}.sha256" "${dump}.sha256" --only-show-errors
  expected="$(cut -d' ' -f1 "${dump}.sha256")"
  actual="$(sha256sum "${dump}" | cut -d' ' -f1)"
  [[ "${expected}" = "${actual}" ]]
  gzip -t "${dump}"
  gzip -dc "${dump}" | mysql --defaults-extra-file="${defaults_file}"

  table_count="$(mysql --defaults-extra-file="${defaults_file}" --batch --skip-column-names \
    -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}'")"
  flyway_version="$(mysql --defaults-extra-file="${defaults_file}" --batch --skip-column-names "${MYSQL_DATABASE}" \
    -e "SELECT MAX(CAST(version AS UNSIGNED)) FROM flyway_schema_history WHERE success=1")"
  mysql --defaults-extra-file="${defaults_file}" "${MYSQL_DATABASE}" -e \
    "SELECT COUNT(*) AS flyway_rows FROM flyway_schema_history; SELECT COUNT(*) AS transaction_rows FROM merchant_transactions_log; SELECT COUNT(*) AS merchant_rows FROM merchants;"
  [[ "${table_count}" -gt 0 && "${flyway_version}" -ge 82 ]]
  echo "RESTORE_OK key=${object} tables=${table_count} flyway_version=${flyway_version} sha256=${actual}"
}

case "${MODE}" in
  backup) run_backup ;;
  restore-validate) run_restore_validation ;;
  *) echo "Unsupported MODE: ${MODE}" >&2; exit 2 ;;
esac
