# Creates the local zimasa-zcare-service database, its schema-owner role and the zc_app login role.
# Reads DB_URL, DB_USERNAME, DB_PASSWORD, DB_MIGRATION_USERNAME, DB_MIGRATION_PASSWORD,
# DB_ADMIN_USERNAME and DB_ADMIN_PASSWORD from the environment or from .env at the repository root.
# Safe to re-run: existing roles get their passwords reset, an existing database is left alone.
$ErrorActionPreference = 'Stop'

$root = Split-Path -Parent $PSScriptRoot
$envFile = Join-Path $root '.env'
if (Test-Path $envFile) {
    foreach ($line in Get-Content $envFile) {
        if ($line -match '^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)$') {
            [Environment]::SetEnvironmentVariable($Matches[1], $Matches[2].Trim(), 'Process')
        }
    }
}

foreach ($name in 'DB_URL', 'DB_PASSWORD', 'DB_MIGRATION_USERNAME', 'DB_MIGRATION_PASSWORD', 'DB_ADMIN_USERNAME', 'DB_ADMIN_PASSWORD') {
    if (-not [Environment]::GetEnvironmentVariable($name)) { throw "Set $name (see .env.example)." }
}
if ($env:DB_USERNAME -and $env:DB_USERNAME -ne 'zc_app') {
    throw 'DB_USERNAME must be zc_app: the schema grants the application''s privileges to that role.'
}

# jdbc:postgresql://host[:port]/name[?params]
if ($env:DB_URL -notmatch '^jdbc:postgresql://([^/:]+)(?::(\d+))?/([^?]+)') { throw "DB_URL is not a PostgreSQL JDBC URL: $env:DB_URL" }
$dbHost = $Matches[1]
$dbPort = if ($Matches[2]) { $Matches[2] } else { '5432' }
$dbName = $Matches[3]

$sql = @'
SELECT format('CREATE ROLE %I LOGIN CREATEROLE', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'owner') \gexec
SELECT format('ALTER ROLE %I LOGIN CREATEROLE PASSWORD %L', :'owner', :'owner_password') \gexec
SELECT 'CREATE ROLE zc_app LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS NOINHERIT'
 WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'zc_app') \gexec
SELECT format('ALTER ROLE zc_app LOGIN PASSWORD %L', :'app_password') \gexec
SELECT format('CREATE DATABASE %I OWNER %I', :'db', :'owner')
 WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'db') \gexec
'@

$env:PGPASSWORD = $env:DB_ADMIN_PASSWORD
try {
    $sql | & psql -X -q -v ON_ERROR_STOP=1 -h $dbHost -p $dbPort -U $env:DB_ADMIN_USERNAME -d postgres `
        -v "owner=$env:DB_MIGRATION_USERNAME" -v "owner_password=$env:DB_MIGRATION_PASSWORD" `
        -v "app_password=$env:DB_PASSWORD" -v "db=$dbName"
    if ($LASTEXITCODE -ne 0) { throw "psql failed with exit code $LASTEXITCODE" }
}
finally {
    Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue
}

Write-Host "Database $dbName is ready on ${dbHost}:$dbPort."
Write-Host 'Next: .\mvnw.cmd liquibase:update'
