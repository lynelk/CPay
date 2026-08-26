#!/bin/sh
set -eu

: "${PORT:=8080}"
: "${BACKEND_UPSTREAM:=cpay.railway.internal:8080}"
export PORT BACKEND_UPSTREAM

envsubst '${PORT} ${BACKEND_UPSTREAM}' \
  < /etc/nginx/templates/default.conf.template \
  > /etc/nginx/conf.d/default.conf

nginx -t
exec nginx -g 'daemon off;'
