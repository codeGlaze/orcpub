#Requires -Version 5.1
<#
.SYNOPSIS
    OrcPub first-time dev environment setup (Windows)

.DESCRIPTION
    PowerShell mirror of scripts/dev-setup.sh.
    Orchestrates initial development environment setup:
      1. Start Datomic (if not skipped)
      2. Run lein deps
      3. Initialize database + apply schema
      4. Create a verified test user (unless -NoTestUser)
      5. Optionally start server/figwheel

.PARAMETER NoStart
    Only perform setup steps, do not start servers (default behavior)

.PARAMETER SkipDatomic
    Don't attempt to start Datomic

.PARAMETER NoTestUser
    Skip creating the default test user

.PARAMETER Start
    After setup, start the backend and figwheel in background

.EXAMPLE
    .\dev-setup.ps1                 # Standard setup
    .\dev-setup.ps1 -SkipDatomic    # Skip Datomic startup
    .\dev-setup.ps1 -Start          # Setup + start services
#>

[CmdletBinding()]
param(
    [switch]$NoStart,
    [switch]$SkipDatomic,
    [switch]$NoTestUser,
    [switch]$Start,
    [Alias("h")][switch]$Help
)

. "$PSScriptRoot\common.ps1"

if ($Help) { Get-Help $PSCommandPath -Detailed; exit $script:EXIT_SUCCESS }

Write-Host "Dev setup: NoStart=$NoStart SkipDatomic=$SkipDatomic NoTestUser=$NoTestUser Start=$Start"

# Step 1: Start Datomic
if (-not $SkipDatomic) {
    Write-Host "Starting Datomic transactor..."
    & "$PSScriptRoot\start.ps1" datomic -Quiet -Idempotent -Background
    if ($LASTEXITCODE -ne 0) {
        Write-Host "Datomic start failed; continuing but DB init may be skipped." -ForegroundColor Yellow
    }
}
else {
    Write-Host "Skipping Datomic startup as requested."
}

# Step 2: Dependencies
Write-Host "Running lein deps..."
Set-Location $script:REPO_ROOT
& lein deps

# Step 3: Database initialization
Write-Host "Initializing database (idempotent)..."

if (Test-PortInUse -Port $script:DATOMIC_PORT) {
    & lein with-profile init-db run -m user init-db
    if ($LASTEXITCODE -eq 0) {
        Write-Host "DB init succeeded."

        # Step 4: Test user
        if (-not $NoTestUser) {
            Write-Host "Creating test user (test / test@test.com / testpass)..."

            $usersFile = Join-Path $script:REPO_ROOT ".test-users"
            if (-not (Test-Path $usersFile)) {
                "# Test users created by dev tooling (gitignored)" | Out-File $usersFile -Encoding utf8
                "# username | email | password | status | created" | Out-File $usersFile -Append -Encoding utf8
            }
            $timestamp = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ")
            "test | test@test.com | testpass | verified | $timestamp" | Out-File $usersFile -Append -Encoding utf8

            & lein with-profile init-db run -m user create-user test "test@test.com" testpass verify
            if ($LASTEXITCODE -eq 0) {
                Write-Host "Test user created and verified."
            }
            else {
                Write-Host "Test user creation failed (may already exist)." -ForegroundColor Yellow
            }
        }
    }
    else {
        Write-Host "DB init failed but continuing (non-fatal)." -ForegroundColor Yellow
    }
}
else {
    Write-Host "Datomic not reachable on port $($script:DATOMIC_PORT); skipping DB init."
}

# Step 5: Optionally start services
if ($Start -and -not $NoStart) {
    Write-Host "Starting backend and figwheel in background..."
    & "$PSScriptRoot\start.ps1" server -Background -Quiet 2>$null
    & "$PSScriptRoot\start.ps1" figwheel -Background -Quiet 2>$null
    Write-Host "Started server & figwheel (logs in $($script:LOG_DIR))"
}
else {
    Write-Host ""
    Write-Host "Setup complete. To start services:"
    Write-Host "  .\scripts\windows\start.ps1 server"
    Write-Host "  .\scripts\windows\start.ps1 figwheel"
    Write-Host "Or use the menu:"
    Write-Host "  .\menu.ps1"
}

exit 0
