#Requires -Version 5.1
<#
.SYNOPSIS
    OrcPub Process Management - Stop/Status (Windows)

.DESCRIPTION
    PowerShell mirror of scripts/stop.sh. Stop OrcPub development services.

    Targets:
      (none)          Stop all OrcPub services
      repl            Stop nREPL processes
      server          Stop OrcPub server
      datomic         Stop Datomic transactor
      figwheel        Stop Figwheel
      garden          Stop Garden CSS watcher
      port <number>   Stop process on specific port
      name <pattern>  Stop processes matching pattern

.EXAMPLE
    .\stop.ps1                      # Stop all (interactive)
    .\stop.ps1 -DryRun              # Show what's running
    .\stop.ps1 -Yes                 # Stop all without prompting
    .\stop.ps1 datomic -Yes         # Stop Datomic only
    .\stop.ps1 port 8890 -Force     # Force kill port 8890
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [string]$Target = "all",

    [Parameter(Position = 1)]
    [string]$TargetArg = "",

    [switch]$DryRun,
    [Alias("y")][switch]$Yes,
    [Alias("f")][switch]$Force,
    [Alias("q")][switch]$Quiet,
    [Alias("h")][switch]$Help
)

. "$PSScriptRoot\common.ps1"

if ($Quiet) { $script:QUIET = $true; $Yes = [switch]::new($true) }

if ($Help) { Get-Help $PSCommandPath -Detailed; exit $script:EXIT_SUCCESS }

# -----------------------------------------------------------------------------
# Status Display
# -----------------------------------------------------------------------------

function Show-Status {
    if ($script:QUIET) {
        $running = 0
        foreach ($port in @($script:DATOMIC_PORT, $script:SERVER_PORT, $script:NREPL_PORT)) {
            if (Test-PortInUse -Port $port) { $running++ }
        }
        Write-Output $running
        return
    }

    Write-Host ""
    Write-Host "OrcPub Service Status" -ForegroundColor White
    Write-Host ("=" * 65)
    Write-Host ("{0,-16} {1,-8} {2,-10} {3,-10} {4}" -f "Service", "Port", "Status", "PID", "Uptime")
    Write-Host ("-" * 65)

    # Read service list from manifest
    $manifest = Get-ServiceManifest
    $serviceNames = @("datomic", "server", "nrepl", "figwheel", "garden")

    foreach ($name in $serviceNames) {
        $svc = $manifest.services.$name
        $port = Get-ServicePort -Name $name
        $pids = Find-PidsByPort -Port $port

        if ($pids.Count -gt 0) {
            $pid = $pids[0]
            $uptime = Get-ProcessUptime -ProcessId $pid
            Write-Host ("{0,-16} {1,-8} " -f $svc.description, $port) -NoNewline
            Write-Host ("{0,-10}" -f "running") -ForegroundColor Green -NoNewline
            Write-Host (" {0,-10} {1}" -f $pid, $uptime)
        }
        else {
            Write-Host ("{0,-16} {1,-8} " -f $svc.description, $port) -NoNewline
            Write-Host ("{0,-10}" -f "stopped") -ForegroundColor Yellow -NoNewline
            Write-Host (" {0,-10} {1}" -f "-", "-")
        }
    }

    Write-Host ("=" * 65)
    Write-Host ""
}

if ($DryRun) { Show-Status; exit $script:EXIT_SUCCESS }

# -----------------------------------------------------------------------------
# Kill Functions
# -----------------------------------------------------------------------------

function Confirm-Kill {
    param([int[]]$Pids, [string]$Description, [bool]$SkipConfirm = $false)

    if ($Pids.Count -eq 0) {
        if (-not $script:QUIET) { Write-LogWarn "No processes found for: $Description" }
        return $false
    }

    if (-not $script:QUIET) {
        Write-Host ""
        Write-Host "Found processes to stop ($Description):"
        Write-Host ("-" * 40)
        foreach ($pid in $Pids) { Write-Host "  $(Get-ProcessInfoString -ProcessId $pid)" }
        Write-Host ("-" * 40)
    }

    if ($SkipConfirm) { return $true }

    if (-not (Test-Interactive)) {
        Write-LogError "Cannot prompt (non-interactive). Use -Yes to skip."
        return $false
    }

    $reply = Read-Host "Stop these processes? [y/N]"
    return ($reply -match '^[yY]')
}

function Stop-Pids {
    param([int[]]$Pids, [bool]$UseForce = $false)

    if ($Pids.Count -eq 0) { return $true }
    if (-not $script:QUIET) { Write-LogInfo "Stopping PIDs: $($Pids -join ', ')" }

    foreach ($pid in $Pids) {
        try {
            $proc = Get-Process -Id $pid -ErrorAction Stop
            $proc.CloseMainWindow() | Out-Null
        } catch { }
        taskkill /PID $pid 2>$null | Out-Null
    }

    Start-Sleep -Seconds 3

    $remaining = @()
    foreach ($pid in $Pids) {
        try {
            $proc = Get-Process -Id $pid -ErrorAction Stop
            if (-not $proc.HasExited) { $remaining += $pid }
        } catch { }
    }

    if ($remaining.Count -gt 0) {
        if ($UseForce) {
            if (-not $script:QUIET) { Write-LogWarn "Force killing: $($remaining -join ', ')" }
            foreach ($pid in $remaining) {
                try { Stop-Process -Id $pid -Force -ErrorAction Stop } catch { }
            }
            Start-Sleep -Seconds 1
        }
        else {
            if (-not $script:QUIET) {
                Write-LogWarn "Still running: $($remaining -join ', '). Use -Force."
            }
            return $false
        }
    }

    if (-not $script:QUIET) { Write-LogInfo "All processes terminated" }
    return $true
}

# -----------------------------------------------------------------------------
# Stop Targets
# -----------------------------------------------------------------------------

function Stop-ServiceTarget {
    param([string]$ServiceName, [bool]$SkipConfirm, [bool]$UseForce)

    $port = Get-ServicePort -Name $ServiceName
    $pattern = Get-ServicePattern -Name $ServiceName
    $svc = Get-ServiceDef -Name $ServiceName
    $desc = if ($svc) { "$($svc.description) (port $port)" } else { "$ServiceName (port $port)" }

    $pids = Find-ServicePids -Name $ServiceName -Port $port -Pattern $pattern
    if (Confirm-Kill -Pids $pids -Description $desc -SkipConfirm $SkipConfirm) {
        Stop-Pids -Pids $pids -UseForce $UseForce
        Remove-Item (Join-Path $script:LOG_DIR "$ServiceName.pid") -Force -ErrorAction SilentlyContinue
    }
}

function Stop-AllServices {
    param([bool]$SkipConfirm, [bool]$UseForce)

    $allPids = @()
    foreach ($name in @("datomic", "server", "nrepl", "figwheel", "garden")) {
        $port = Get-ServicePort -Name $name
        $pattern = Get-ServicePattern -Name $name
        $pids = Find-ServicePids -Name $name -Port $port -Pattern $pattern
        $allPids += $pids
    }
    $allPids = @($allPids | Sort-Object -Unique)

    if (Confirm-Kill -Pids $allPids -Description "all OrcPub services" -SkipConfirm $SkipConfirm) {
        Stop-Pids -Pids $allPids -UseForce $UseForce
        Get-ChildItem (Join-Path $script:LOG_DIR "*.pid") -ErrorAction SilentlyContinue | Remove-Item -Force
    }
}

function Stop-ByPort {
    param([string]$PortStr, [bool]$SkipConfirm, [bool]$UseForce)
    if (-not $PortStr -or $PortStr -notmatch '^\d+$') {
        Write-LogError "Usage: .\stop.ps1 port <number>"; exit $script:EXIT_USAGE
    }
    $pids = Find-PidsByPort -Port ([int]$PortStr)
    if (Confirm-Kill -Pids $pids -Description "port $PortStr" -SkipConfirm $SkipConfirm) {
        Stop-Pids -Pids $pids -UseForce $UseForce
    }
}

function Stop-ByName {
    param([string]$Pattern, [bool]$SkipConfirm, [bool]$UseForce)
    if (-not $Pattern) { Write-LogError "Usage: .\stop.ps1 name <pattern>"; exit $script:EXIT_USAGE }
    if ($Pattern.Length -lt 4) {
        Write-LogWarn "Pattern '$Pattern' is very broad"
        if (-not (Test-Interactive)) {
            Write-LogError "Refusing broad pattern in non-interactive mode"; exit $script:EXIT_USAGE
        }
        $reply = Read-Host "May match many processes. Continue? [y/N]"
        if ($reply -notmatch '^[yY]') { exit $script:EXIT_SUCCESS }
    }
    $pids = Find-PidsByName -Pattern $Pattern
    if (Confirm-Kill -Pids $pids -Description "pattern '$Pattern'" -SkipConfirm $SkipConfirm) {
        Stop-Pids -Pids $pids -UseForce $UseForce
    }
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

$skipConfirm = $Yes.IsPresent
$useForce = $Force.IsPresent

switch ($Target) {
    "all"      { Stop-AllServices -SkipConfirm $skipConfirm -UseForce $useForce }
    "repl"     { Stop-ServiceTarget -ServiceName "nrepl"    -SkipConfirm $skipConfirm -UseForce $useForce }
    "server"   { Stop-ServiceTarget -ServiceName "server"   -SkipConfirm $skipConfirm -UseForce $useForce }
    "datomic"  { Stop-ServiceTarget -ServiceName "datomic"  -SkipConfirm $skipConfirm -UseForce $useForce }
    "figwheel" { Stop-ServiceTarget -ServiceName "figwheel" -SkipConfirm $skipConfirm -UseForce $useForce }
    "garden"   { Stop-ServiceTarget -ServiceName "garden"   -SkipConfirm $skipConfirm -UseForce $useForce }
    "port"     { Stop-ByPort -PortStr $TargetArg -SkipConfirm $skipConfirm -UseForce $useForce }
    "name"     { Stop-ByName -Pattern $TargetArg -SkipConfirm $skipConfirm -UseForce $useForce }
    default    { Write-LogError "Unknown target: $Target"; exit $script:EXIT_USAGE }
}
