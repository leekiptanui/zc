#!/bin/sh
# Mints an HS256 bearer token for LOCAL runs of zimasa-zcare-service (SPRING_PROFILES_ACTIVE=local).
# The service accepts it only when ZCARE_JWT_HMAC_SECRET is set to the same secret; deployed
# environments verify Keycloak tokens instead and never set that variable.
#
#   scripts/dev-token.sh <subject> <role> <tenant> [org-id]
#   scripts/dev-token.sh cm-001 care_manager acme-health
set -eu

if [ $# -lt 3 ]; then
  echo "usage: $0 <subject> <role> <tenant> [org-id]" >&2
  exit 1
fi
: "${ZCARE_JWT_HMAC_SECRET:?set ZCARE_JWT_HMAC_SECRET to the secret the service uses}"

subject=$1
role=$2
tenant=$3
org=${4:-}
now=$(date +%s)
exp=$((now + 3600))

b64url() { openssl base64 -A | tr '+/' '-_' | tr -d '='; }

org_claim=""
if [ -n "$org" ]; then
  org_claim=",\"org\":$org"
fi
header=$(printf '%s' '{"alg":"HS256","typ":"JWT"}' | b64url)
payload=$(printf '{"sub":"%s","aud":"zimasa-zcare-service","iat":%s,"exp":%s,"tenant":"%s","realm_access":{"roles":["%s"]}%s}' \
  "$subject" "$now" "$exp" "$tenant" "$role" "$org_claim" | b64url)
signature=$(printf '%s' "$header.$payload" | openssl dgst -binary -sha256 -hmac "$ZCARE_JWT_HMAC_SECRET" | b64url)
echo "$header.$payload.$signature"
