#Requires -Version 5.1
<#
.SYNOPSIS
    Create a user in the OrcPub database (Windows)

.DESCRIPTION
    PowerShell mirror of scripts/create_dummy_user.sh.
    Creates a user via the Leiningen CLI entrypoint. Requires Datomic running.
    Uses :init-db profile for fast startup (skips ClojureScript/Garden).

.PARAMETER Username
    The username to create

.PARAMETER Email
    The user's email address

.PARAMETER Password
    The user's password

.PARAMETER Verify
    Mark the user as verified (can log in immediately)

.EXAMPLE
    .\create_dummy_user.ps1 testuser test@example.com s3cret
    .\create_dummy_user.ps1 admin admin@example.com MyPass -Verify
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory, Position = 0)]
    [string]$Username,

    [Parameter(Mandatory, Position = 1)]
    [string]$Email,

    [Parameter(Mandatory, Position = 2)]
    [string]$Password,

    [switch]$Verify,
    [Alias("h")][switch]$Help
)

. "$PSScriptRoot\common.ps1"

if ($Help) { Get-Help $PSCommandPath -Detailed; exit $script:EXIT_SUCCESS }

# Log credentials to .test-users (gitignored)
$usersFile = Join-Path $script:REPO_ROOT ".test-users"
if (-not (Test-Path $usersFile)) {
    "# Test users created by dev tooling (gitignored)" | Out-File $usersFile -Encoding utf8
    "# username | email | password | status | created" | Out-File $usersFile -Append -Encoding utf8
}

$status = if ($Verify) { "verified" } else { "unverified" }
$timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
"$Username | $Email | $Password | $status | $timestamp" | Out-File $usersFile -Append -Encoding utf8

# Run lein to create the user
Set-Location $script:REPO_ROOT

if ($Verify) {
    & lein with-profile init-db run -m user create-user $Username $Email $Password verify
}
else {
    & lein with-profile init-db run -m user create-user $Username $Email $Password
}
