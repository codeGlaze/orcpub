#Requires -Version 5.1
<#
.SYNOPSIS
    OrcPub Development Menu (Windows)

.DESCRIPTION
    Interactive menu for managing OrcPub development services on Windows.
    PowerShell equivalent of the ./menu script.

    Provides a simple numbered menu to start/stop services, run setup,
    check status, and manage users.
#>

[CmdletBinding()]
param(
    [Alias("h")][switch]$Help
)

$ErrorActionPreference = "Stop"
$ScriptsDir = Join-Path $PSScriptRoot "scripts\windows"

# Verify the PowerShell scripts exist
if (-not (Test-Path (Join-Path $ScriptsDir "common.ps1"))) {
    Write-Host "[ERROR] PowerShell scripts not found at: $ScriptsDir" -ForegroundColor Red
    Write-Host "Expected: scripts\windows\common.ps1, start.ps1, stop.ps1, etc."
    exit 1
}

# Source common for status info
. "$ScriptsDir\common.ps1"

function Show-Menu {
    Clear-Host
    Write-Host ""
    Write-Host "  OrcPub Development Menu" -ForegroundColor Cyan
    Write-Host "  =======================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "  Start Services" -ForegroundColor White
    Write-Host "    1)  Start All (Datomic + Server)"
    Write-Host "    2)  Start Datomic transactor"
    Write-Host "    3)  Start REPL + Server"
    Write-Host "    4)  Start Figwheel (ClojureScript)"
    Write-Host "    5)  Start Garden (CSS)"
    Write-Host ""
    Write-Host "  Stop Services" -ForegroundColor White
    Write-Host "    6)  Stop all services"
    Write-Host "    7)  Stop specific service"
    Write-Host ""
    Write-Host "  Setup & Tools" -ForegroundColor White
    Write-Host "    8)  First-time dev setup"
    Write-Host "    9)  Initialize database"
    Write-Host "   10)  Create test user"
    Write-Host "   11)  Pre-flight checks"
    Write-Host ""
    Write-Host "  Info" -ForegroundColor White
    Write-Host "   12)  Show service status"
    Write-Host ""
    Write-Host "    q)  Quit"
    Write-Host ""
}

function Show-ServiceStopMenu {
    Write-Host ""
    Write-Host "  Stop which service?" -ForegroundColor White
    Write-Host "    1) Datomic"
    Write-Host "    2) Server"
    Write-Host "    3) nREPL"
    Write-Host "    4) Figwheel"
    Write-Host "    5) Garden"
    Write-Host "    6) All"
    Write-Host "    b) Back"
    Write-Host ""

    $choice = Read-Host "  Select [1-6/b]"
    switch ($choice) {
        "1" { & "$ScriptsDir\stop.ps1" datomic -Yes }
        "2" { & "$ScriptsDir\stop.ps1" server -Yes }
        "3" { & "$ScriptsDir\stop.ps1" repl -Yes }
        "4" { & "$ScriptsDir\stop.ps1" figwheel -Yes }
        "5" { & "$ScriptsDir\stop.ps1" garden -Yes }
        "6" { & "$ScriptsDir\stop.ps1" -Yes }
        "b" { return }
        default { Write-Host "Invalid choice." -ForegroundColor Yellow }
    }
}

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit 0
}

# Main loop
while ($true) {
    Show-Menu

    $choice = Read-Host "  Select [1-12/q]"

    switch ($choice) {
        "1"  { & "$ScriptsDir\start.ps1" all }
        "2"  { & "$ScriptsDir\start.ps1" datomic -Background }
        "3"  { & "$ScriptsDir\start.ps1" server }
        "4"  { & "$ScriptsDir\start.ps1" figwheel }
        "5"  { & "$ScriptsDir\start.ps1" garden }
        "6"  { & "$ScriptsDir\stop.ps1" -Yes }
        "7"  { Show-ServiceStopMenu }
        "8"  { & "$ScriptsDir\dev-setup.ps1" }
        "9"  { & "$ScriptsDir\start.ps1" init-db }
        "10" {
            $user = Read-Host "Username"
            $email = Read-Host "Email"
            $pass = Read-Host "Password"
            $verify = Read-Host "Verify immediately? [y/N]"
            if ($verify -match '^[yY]') {
                & "$ScriptsDir\create_dummy_user.ps1" $user $email $pass -Verify
            } else {
                & "$ScriptsDir\create_dummy_user.ps1" $user $email $pass
            }
        }
        "11" { & "$ScriptsDir\start.ps1" -Check }
        "12" { & "$ScriptsDir\stop.ps1" -DryRun }
        "q"  { Write-Host ""; Write-Host "Goodbye." -ForegroundColor Cyan; exit 0 }
        "Q"  { Write-Host ""; Write-Host "Goodbye." -ForegroundColor Cyan; exit 0 }
        default { Write-Host "Invalid choice." -ForegroundColor Yellow }
    }

    Write-Host ""
    Write-Host "Press Enter to continue..." -ForegroundColor DarkGray
    Read-Host | Out-Null
}
