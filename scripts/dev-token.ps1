# Mints an HS256 bearer token for LOCAL runs of zimasa-zcare-service (SPRING_PROFILES_ACTIVE=local).
# The service accepts it only when ZCARE_JWT_HMAC_SECRET is set to the same secret; deployed
# environments verify Keycloak tokens instead and never set that variable.
#
#   .\scripts\dev-token.ps1 -Subject cm-001 -Role care_manager -Tenant acme-health
#   .\scripts\dev-token.ps1 -Subject pc-001 -Role provider_coordinator -Tenant acme-health -Org 3
param(
    [Parameter(Mandatory = $true)][string] $Subject,
    [Parameter(Mandatory = $true)][string] $Role,
    [Parameter(Mandatory = $true)][string] $Tenant,
    [long] $Org = 0,
    [int] $Minutes = 60,
    [string] $Audience = 'zimasa-zcare-service',
    [string] $Secret = $env:ZCARE_JWT_HMAC_SECRET
)

if (-not $Secret) {
    Write-Error 'Set ZCARE_JWT_HMAC_SECRET (or pass -Secret) to the secret the service uses.'
    exit 1
}

function ConvertTo-Base64Url([byte[]] $bytes) {
    [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
}

$now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
$claims = [ordered]@{
    sub          = $Subject
    aud          = $Audience
    iat          = $now
    exp          = $now + ($Minutes * 60)
    tenant       = $Tenant
    realm_access = @{ roles = @($Role) }
}
if ($Org -gt 0) { $claims.org = $Org }

$header = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes('{"alg":"HS256","typ":"JWT"}'))
$payload = ConvertTo-Base64Url ([Text.Encoding]::UTF8.GetBytes(($claims | ConvertTo-Json -Compress -Depth 4)))
$hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes($Secret))
$signature = ConvertTo-Base64Url ($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$header.$payload")))
"$header.$payload.$signature"
