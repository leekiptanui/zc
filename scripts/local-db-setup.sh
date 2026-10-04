#!/bin/sh
# Creates the local zimasa-zcare-service database, its schema-owner role and the zc_app login role.
# Reads DB_URL, DB_USERNAME, DB_PASSWORD, DB_MIGRATION_USERNAME, DB_MIGRATION_PASSWORD,
# DB_ADMIN_USERNAME and DB_ADMIN_PASSWORD from the environment or from .env at the repository root.
# Safe to re-run: existing roles get their passwords reset, an existing database is left alone.
set -eu

ROOT=$(cd "$(dirname "$0")/.." && pwd)
if [ -f "$ROOT/.env" ]; then
  set -a
  . "$ROOT/.env"
  set +a
fi

: "${DB_URL:?set DB_URL (see .env.example)}"
: "${DB_PASSWORD:?set DB_PASSWORD}"
: "${DB_MIGRATION_USERNAME:?set DB_MIGRATION_USERNAME}"
: "${DB_MIGRATION_PASSWORD:?set DB_MIGRATION_PASSWORD}"
: "${DB_ADMIN_USERNAME:?set DB_ADMIN_USERNAME}"
: "${DB_ADMIN_PASSWORD:?set DB_ADMIN_PASSWORD}"
if [ "${DB_USERNAME:-zc_app}" != "zc_app" ]; then
  echo "DB_USERNAME must be zc_app: the schema grants the application's privileges to that role." >&2
  exit 1
fi

# jdbc:postgresql://host[:port]/name[?params]
rest=${DB_URL#jdbc:postgresql://}
hostport=${rest%%/*}
DB_NAME=${rest#*/}
DB_NAME=${DB_NAME%%\?*}
DB_HOST=${hostport%%:*}
DB_PORT=5432
if [ "$hostport" != "$DB_HOST" ]; then
  DB_PORT=${hostport#*:}
fi

PGPASSWORD="$DB_ADMIN_PASSWORD" psql -X -q -v ON_ERROR_STOP=1 \
  -h "$DB_HOST" -p "$DB_PORT" -U "$DB_ADMIN_USERNAME" -d postgres \
  -v owner="$DB_MIGRATION_USERNAME" -v owner_password="$DB_MIGRATION_PASSWORD" \
  -v app_password="$DB_PASSWORD" -v db="$DB_NAME" <<'SQL'
SELECT format('CREATE ROLE %I LOGIN CREATEROLE', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('ALTER ROLE %I LOGIN CREATEROLE PASSWORD %L', :'owner', :'owner_password') \gexec
SELECT 'CREATE ROLE zc_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zc_app') \gexec
SELECT format('ALTER ROLE zc_app LOGIN PASSWORD %L', :'app_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
SQL

echo "Database $DB_NAME is ready on $DB_HOST:$DB_PORT."
echo "Next: ./mvnw liquibase:update"
