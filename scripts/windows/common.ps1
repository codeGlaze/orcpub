#Requires -Version 5.1
# =============================================================================
# common.ps1 - Shared utilities for OrcPub PowerShell scripts
# =============================================================================
# Dot-source this file in start.ps1, stop.ps1, etc.:
#   . "$PSScriptRoot\common.ps1"
#
# PowerShell mirror of scripts/common.sh — same conventions, same .env,
# same ports. Reads scripts/services.json for service definitions.
# =============================================================================

# Prevent double-sourcing
if ($script:_ORCPUB_COMMON_LOADED) { return }
$script:_ORCPUB_COMMON_LOADED = $true

# -----------------------------------------------------------------------------
# Path Setup
# -----------------------------------------------------------------------------

$script:COMMON_DIR = $PSScriptRoot
$script:SCRIPTS_DIR = (Resolve-Path (Join-Path $script:COMMON_DIR "..")).Path
$script:REPO_ROOT = (Resolve-Path (Join-Path $script:SCRIPTS_DIR "..")).Path

# -----------------------------------------------------------------------------
# Service Manifest
# -----------------------------------------------------------------------------

$script:MANIFEST_PATH = Join-Path $script:SCRIPTS_DIR "services.json"

function Get-ServiceManifest {
    <#
    .SYNOPSIS
    Load and cache the shared service manifest (scripts/services.json).
    #>
    if (-not $script:_MANIFEST_CACHE) {
        if (-not (Test-Path $script:MANIFEST_PATH)) {
            Write-LogError "Service manifest not found: $($script:MANIFEST_PATH)"
            exit 1
        }
        $script:_MANIFEST_CACHE = Get-Content $script:MANIFEST_PATH -Raw | ConvertFrom-Json
    }
    return $script:_MANIFEST_CACHE
}

function Get-ServiceDef {
    <#
    .SYNOPSIS
    Get the definition for a named service from the manifest.
    #>
    param([string]$Name)
    $manifest = Get-ServiceManifest
    return $manifest.services.$Name
}

function Get-ServicePort {
    <#
    .SYNOPSIS
    Get the effective port for a service (env var override or manifest default).
    #>
    param([string]$Name)
    $svc = Get-ServiceDef -Name $Name
    if (-not $svc) { return 0 }

    $envVal = [Environment]::GetEnvironmentVariable($svc.port_env, 'Process')
    if ($envVal) { return [int]$envVal }
    return [int]$svc.port_default
}

function Get-ServicePattern {
    <#
    .SYNOPSIS
    Get the process search pattern for a service.
    #>
    param([string]$Name)
    $svc = Get-ServiceDef -Name $Name
    if ($svc) { return $svc.process_pattern }
    return $Name
}

function Get-ServiceStartCommand {
    <#
    .SYNOPSIS
    Get the start command for a service, resolved for the current platform.
    #>
    param(
        [string]$Name,
        [switch]$Headless
    )
    $svc = Get-ServiceDef -Name $Name

    $cmdObj = if ($Headless -and $svc.start_command_headless) {
        $svc.start_command_headless
    } else {
        $svc.start_command
    }

    if (-not $cmdObj) { return $null }

    # Prefer platform-specific, fall back to "both"
    if ($cmdObj.windows) { return $cmdObj.windows }
    if ($cmdObj.both) { return $cmdObj.both }
    return $null
}

# -----------------------------------------------------------------------------
# Environment Configuration
# -----------------------------------------------------------------------------

function Import-DotEnv {
    <#
    .SYNOPSIS
    Load variables from a .env file into the current process environment.
    Supports KEY=VALUE, KEY="VALUE", and KEY='VALUE' formats.
    Lines starting with # and blank lines are skipped.
    #>
    param([string]$Path)

    if (-not (Test-Path $Path)) { return }

    Get-Content $Path | ForEach-Object {
        $line = $_.Trim()
        if ($line -eq '' -or $line.StartsWith('#')) { return }
        if ($line -match '^\s*([^=]+?)\s*=\s*(.*)$') {
            $key = $Matches[1]
            $val = $Matches[2].Trim()
            # Strip surrounding quotes
            if (($val.StartsWith('"') -and $val.EndsWith('"')) -or
                ($val.StartsWith("'") -and $val.EndsWith("'"))) {
                $val = $val.Substring(1, $val.Length - 2)
            }
            [Environment]::SetEnvironmentVariable($key, $val, 'Process')
        }
    }
}

# Source .env if present (authoritative config)
$script:EnvFile = Join-Path $script:REPO_ROOT ".env"
if (Test-Path $script:EnvFile) {
    Import-DotEnv -Path $script:EnvFile
}

# Load defaults from manifest
$script:_Manifest = Get-ServiceManifest
$script:DATOMIC_VERSION  = if ($env:DATOMIC_VERSION)  { $env:DATOMIC_VERSION }  else { $script:_Manifest.defaults.datomic_version }
$script:DATOMIC_TYPE     = if ($env:DATOMIC_TYPE)     { $env:DATOMIC_TYPE }     else { $script:_Manifest.defaults.datomic_type }
$script:JAVA_MIN_VERSION = if ($env:JAVA_MIN_VERSION) { [int]$env:JAVA_MIN_VERSION } else { [int]$script:_Manifest.defaults.java_min_version }
$script:LOG_DIR          = if ($env:LOG_DIR)           { $env:LOG_DIR }           else { Join-Path $script:REPO_ROOT $script:_Manifest.defaults.log_dir }
$script:KILL_WAIT        = if ($env:KILL_WAIT)         { [int]$env:KILL_WAIT }    else { [int]$script:_Manifest.defaults.kill_wait }
$script:PORT_WAIT        = if ($env:PORT_WAIT)         { [int]$env:PORT_WAIT }    else { [int]$script:_Manifest.defaults.port_wait }

# Port configuration (from manifest + env overrides)
$script:DATOMIC_PORT  = Get-ServicePort -Name "datomic"
$script:SERVER_PORT   = Get-ServicePort -Name "server"
$script:NREPL_PORT    = Get-ServicePort -Name "nrepl"
$script:FIGWHEEL_PORT = Get-ServicePort -Name "figwheel"
$script:GARDEN_PORT   = Get-ServicePort -Name "garden"

# Derived paths
$script:DATOMIC_DIR = Join-Path $script:REPO_ROOT "lib\com\datomic\datomic-$($script:DATOMIC_TYPE)\$($script:DATOMIC_VERSION)"
$script:DATOMIC_CONFIG = Join-Path $script:DATOMIC_DIR "config\working-transactor.properties"
$script:DATOMIC_CONFIG_TEMPLATE = Join-Path $script:DATOMIC_DIR "config\samples\dev-transactor-template.properties"

# Exit codes (from manifest)
$script:EXIT_SUCCESS = [int]$script:_Manifest.exit_codes.success
$script:EXIT_USAGE   = [int]$script:_Manifest.exit_codes.usage
$script:EXIT_PREREQ  = [int]$script:_Manifest.exit_codes.prereq
$script:EXIT_RUNTIME = [int]$script:_Manifest.exit_codes.runtime

# Ensure logs directory exists
if (-not (Test-Path $script:LOG_DIR)) {
    New-Item -ItemType Directory -Path $script:LOG_DIR -Force | Out-Null
}

# -----------------------------------------------------------------------------
# Quiet Mode
# -----------------------------------------------------------------------------

$script:QUIET = if ($env:QUIET -eq 'true') { $true } else { $false }

# -----------------------------------------------------------------------------
# Interactive Detection
# -----------------------------------------------------------------------------

function Test-Interactive {
    return [Environment]::UserInteractive -and ($Host.Name -ne 'Default Host')
}

# -----------------------------------------------------------------------------
# Logging
# -----------------------------------------------------------------------------

function Write-LogInfo {
    param([string]$Message)
    if ($script:QUIET) { return }
    Write-Host "[INFO] $Message" -ForegroundColor Green
}

function Write-LogWarn {
    param([string]$Message)
    if ($script:QUIET) { return }
    Write-Host "[WARN] $Message" -ForegroundColor Yellow
}

function Write-LogError {
    param([string]$Message)
    Write-Host "[ERROR] $Message" -ForegroundColor Red
}

# -----------------------------------------------------------------------------
# Port Utilities
# -----------------------------------------------------------------------------

function Test-PortInUse {
    <#
    .SYNOPSIS
    Check if a TCP port is in use (listening).
    #>
    param([int]$Port)

    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        return ($null -ne $connections -and @($connections).Count -gt 0)
    }
    catch {
        # Fallback: try to connect
        try {
            $tcp = New-Object System.Net.Sockets.TcpClient
            $tcp.Connect("127.0.0.1", $Port)
            $tcp.Close()
            return $true
        }
        catch {
            return $false
        }
    }
}

function Wait-ForPort {
    param([int]$Port, [int]$Timeout = 30)
    $elapsed = 0
    while ($elapsed -lt $Timeout) {
        if (Test-PortInUse -Port $Port) { return $true }
        Start-Sleep -Seconds 1
        $elapsed++
    }
    return $false
}

function Wait-ForPortOrDie {
    param([int]$Port, [int]$ProcessId, [int]$Timeout = 60)
    $elapsed = 0
    while ($elapsed -lt $Timeout) {
        try {
            $proc = Get-Process -Id $ProcessId -ErrorAction Stop
            if ($proc.HasExited) {
                Write-LogError "Process $ProcessId died while waiting for port $Port"
                return $false
            }
        }
        catch {
            Write-LogError "Process $ProcessId died while waiting for port $Port"
            return $false
        }
        if (Test-PortInUse -Port $Port) { return $true }
        Start-Sleep -Seconds 1
        $elapsed++
    }
    Write-LogError "Timeout waiting for port $Port (process $ProcessId still running)"
    return $false
}

function Wait-ForPortFree {
    param([int]$Port, [int]$Timeout = 10)
    $elapsed = 0
    while ($elapsed -lt $Timeout) {
        if (-not (Test-PortInUse -Port $Port)) { return $true }
        Start-Sleep -Seconds 1
        $elapsed++
    }
    return $false
}

function Find-PidsByPort {
    param([int]$Port)
    try {
        $connections = Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue
        if ($connections) {
            return @($connections | Select-Object -ExpandProperty OwningProcess -Unique)
        }
    }
    catch {
        # Fallback: parse netstat
        $output = netstat -ano 2>$null | Select-String ":$Port\s" | ForEach-Object {
            if ($_ -match '\s+LISTENING\s+(\d+)') { [int]$Matches[1] }
        }
        if ($output) { return @($output | Sort-Object -Unique) }
    }
    return @()
}

function Find-PidsByName {
    param([string]$Pattern)
    $selfPid = $PID
    try {
        # Get-CimInstance gives us CommandLine on Windows
        $procs = Get-CimInstance Win32_Process -ErrorAction SilentlyContinue |
            Where-Object {
                ($_.Name -match $Pattern) -or
                ($_.CommandLine -and $_.CommandLine -match $Pattern)
            } |
            Where-Object { $_.ProcessId -ne $selfPid }

        if ($procs) {
            return @($procs | Select-Object -ExpandProperty ProcessId -Unique)
        }
    }
    catch {
        # Simpler fallback
        $procs = Get-Process | Where-Object {
            $_.ProcessName -match $Pattern
        } | Where-Object { $_.Id -ne $selfPid }
        if ($procs) {
            return @($procs | Select-Object -ExpandProperty Id -Unique)
        }
    }
    return @()
}

function Get-ProcessInfoString {
    param([int]$ProcessId)
    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction Stop
        $cmd = if ($proc.Path) { $proc.Path } else { $proc.ProcessName }
        return "PID: $ProcessId  Cmd: $cmd"
    }
    catch {
        return "PID: $ProcessId (info unavailable)"
    }
}

function Get-ProcessUptime {
    param([int]$ProcessId)
    try {
        $proc = Get-Process -Id $ProcessId -ErrorAction Stop
        $elapsed = (Get-Date) - $proc.StartTime
        if ($elapsed.TotalHours -ge 1) {
            return "{0}h {1}m" -f [int]$elapsed.TotalHours, $elapsed.Minutes
        }
        elseif ($elapsed.TotalMinutes -ge 1) {
            return "{0}m {1}s" -f [int]$elapsed.TotalMinutes, $elapsed.Seconds
        }
        else {
            return "{0}s" -f [int]$elapsed.TotalSeconds
        }
    }
    catch { return "unknown" }
}

# -----------------------------------------------------------------------------
# Prerequisite Checks
# -----------------------------------------------------------------------------

function Test-JavaInstalled {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if (-not $javaCmd) {
        Write-LogError "Java not found. Please install Java $($script:JAVA_MIN_VERSION) or higher."
        return $false
    }
    $versionOutput = & java -version 2>&1 | Select-Object -First 1
    if ($versionOutput -match '"(\d+)') {
        $majorVersion = [int]$Matches[1]
        if ($majorVersion -lt $script:JAVA_MIN_VERSION) {
            Write-LogError "Java $($script:JAVA_MIN_VERSION)+ required (found Java $majorVersion)."
            return $false
        }
        Write-LogInfo "Java $majorVersion detected (minimum: $($script:JAVA_MIN_VERSION))"
        return $true
    }
    Write-LogError "Could not parse Java version from: $versionOutput"
    return $false
}

function Test-LeinInstalled {
    if (Get-Command lein -ErrorAction SilentlyContinue) { return $true }
    if (Get-Command lein.bat -ErrorAction SilentlyContinue) { return $true }
    Write-LogError "Leiningen not found. Please install Leiningen."
    return $false
}

function Test-DatomicInstalled {
    if (-not (Test-Path $script:DATOMIC_DIR)) {
        Write-LogError "Datomic $($script:DATOMIC_TYPE) $($script:DATOMIC_VERSION) not found at: $($script:DATOMIC_DIR)"
        return $false
    }
    $transactorBat = Join-Path $script:DATOMIC_DIR "bin\transactor.bat"
    $transactorSh = Join-Path $script:DATOMIC_DIR "bin\transactor"
    if (-not (Test-Path $transactorBat) -and -not (Test-Path $transactorSh)) {
        Write-LogError "Datomic transactor not found. Installation may be incomplete."
        return $false
    }
    return $true
}

# -----------------------------------------------------------------------------
# Process Management
# -----------------------------------------------------------------------------

function Stop-ProcessGracefully {
    param([int]$ProcessId, [int]$WaitSeconds = $script:KILL_WAIT)
    try { $proc = Get-Process -Id $ProcessId -ErrorAction Stop } catch { return }
    try { $proc.CloseMainWindow() | Out-Null } catch { }
    # Also try taskkill for console processes
    taskkill /PID $ProcessId 2>$null | Out-Null
    $waited = 0
    while ($waited -lt $WaitSeconds) {
        try {
            $proc = Get-Process -Id $ProcessId -ErrorAction Stop
            if ($proc.HasExited) { return }
        }
        catch { return }
        Start-Sleep -Seconds 1
        $waited++
    }
    Write-LogWarn "Process $ProcessId didn't stop gracefully, force killing"
    try { Stop-Process -Id $ProcessId -Force -ErrorAction Stop } catch { }
}

function Remove-StalePidFile {
    param([string]$Name)
    $pidFile = Join-Path $script:LOG_DIR "$Name.pid"
    if (Test-Path $pidFile) {
        $oldPid = Get-Content $pidFile -ErrorAction SilentlyContinue
        if ($oldPid) {
            try {
                Get-Process -Id ([int]$oldPid) -ErrorAction Stop | Out-Null
            }
            catch {
                Remove-Item $pidFile -Force -ErrorAction SilentlyContinue
                Write-LogInfo "Cleaned up stale PID file for $Name"
            }
        }
    }
}

function Find-ServicePids {
    param([string]$Name, [int]$Port, [string]$Pattern)

    # PID file first
    $pidFile = Join-Path $script:LOG_DIR "$Name.pid"
    if (Test-Path $pidFile) {
        $filePid = Get-Content $pidFile -ErrorAction SilentlyContinue
        if ($filePid) {
            try {
                Get-Process -Id ([int]$filePid) -ErrorAction Stop | Out-Null
                return @([int]$filePid)
            }
            catch { }
        }
    }

    # Fallback: port + pattern
    $portPids = Find-PidsByPort -Port $Port
    $namePids = Find-PidsByName -Pattern $Pattern
    return @(@($portPids) + @($namePids) | Sort-Object -Unique)
}

# -----------------------------------------------------------------------------
# Failure Diagnostics
# -----------------------------------------------------------------------------

function Show-StartupFailure {
    param([string]$Name, [string]$LogFile, [int]$Port = 0)

    Write-LogError "Service '$Name' failed to start. Diagnostics:"
    Write-Host ("=" * 60)
    if ($LogFile -and (Test-Path $LogFile)) {
        Write-Host "Last 30 lines of ${LogFile}:"
        Get-Content $LogFile -Tail 30
    }
    else {
        Write-Host "Log file: (not available)"
    }
    Write-Host ("=" * 60)
    if ($Port -gt 0) {
        Write-Host "Processes on port ${Port}:"
        $pids = Find-PidsByPort -Port $Port
        if ($pids.Count -gt 0) {
            foreach ($pid in $pids) { Write-Host "  $(Get-ProcessInfoString -ProcessId $pid)" }
        }
        else { Write-Host "  (none)" }
    }
    Write-Host ("=" * 60)
}

# -----------------------------------------------------------------------------
# Datomic Config Helpers
# -----------------------------------------------------------------------------

function Get-DatomicPortFromConfig {
    param([string]$ConfigPath)
    if (Test-Path $ConfigPath) {
        $portLine = Get-Content $ConfigPath | Where-Object { $_ -match '^port=' }
        if ($portLine -match '^port=(\d+)') { return [int]$Matches[1] }
    }
    return $script:DATOMIC_PORT
}

function Get-TransactorCommand {
    $batPath = Join-Path $script:DATOMIC_DIR "bin\transactor.bat"
    $shPath = Join-Path $script:DATOMIC_DIR "bin\transactor"
    if (Test-Path $batPath) { return $batPath }
    if (Test-Path $shPath) { return $shPath }
    return $null
}

function Initialize-DatomicConfig {
    if (-not (Test-Path $script:DATOMIC_CONFIG)) {
        if (Test-Path $script:DATOMIC_CONFIG_TEMPLATE) {
            Copy-Item $script:DATOMIC_CONFIG_TEMPLATE $script:DATOMIC_CONFIG
            Write-LogInfo "Created transactor config from template"
        }
        else {
            Write-LogError "Datomic config template not found at: $($script:DATOMIC_CONFIG_TEMPLATE)"
            return $false
        }
    }
    return $true
}
