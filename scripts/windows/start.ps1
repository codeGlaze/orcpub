#Requires -Version 5.1
<#
.SYNOPSIS
    OrcPub Service Launcher (Windows)

.DESCRIPTION
    PowerShell mirror of scripts/start.sh. Start OrcPub development services.

    Targets:
      (none)    Start Datomic (background) + REPL with server (foreground)
      datomic   Start Datomic transactor only
      server    Start REPL with server (requires Datomic running)
      figwheel  Start Figwheel for ClojureScript hot-reload
      garden    Start Garden for CSS auto-compilation
      init-db   Initialize the database (requires Datomic running)
      prod      Build production uberjar + run it

.EXAMPLE
    .\start.ps1                     # Full dev stack
    .\start.ps1 datomic             # Just Datomic
    .\start.ps1 server              # Just REPL+server
    .\start.ps1 prod                # Build + run production jar
    .\start.ps1 prod -NoBuild       # Run existing production jar
    .\start.ps1 -Check              # Pre-flight validation
    .\start.ps1 datomic -Idempotent # Start or succeed if running
    .\start.ps1 datomic -Background # Run in background
#>

[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet("all", "datomic", "server", "figwheel", "garden", "init-db", "prod", "help")]
    [string]$Target = "all",

    [Alias("i")][switch]$Install,
    [Alias("b")][switch]$Background,
    [Alias("q")][switch]$Quiet,
    [Alias("c")][switch]$Check,
    [Alias("I")][switch]$Idempotent,
    [Alias("Skip")][switch]$NoBuild,
    [Alias("h")][switch]$Help
)

. "$PSScriptRoot\common.ps1"
if ($Quiet) { $script:QUIET = $true }

# -----------------------------------------------------------------------------
# Help / Install
# -----------------------------------------------------------------------------

if ($Help -or $Target -eq "help") {
    Get-Help $PSCommandPath -Detailed
    exit $script:EXIT_SUCCESS
}

if ($Install) {
    $postCreate = Join-Path $script:REPO_ROOT ".devcontainer\post-create.sh"
    $bashCmd = Get-Command bash -ErrorAction SilentlyContinue
    if ($bashCmd -and (Test-Path $postCreate)) {
        Write-LogInfo "Running Datomic installation via bash..."
        & bash $postCreate
        if ($LASTEXITCODE -ne 0) { Write-LogError "Installation failed"; exit $script:EXIT_RUNTIME }
        Write-LogInfo "Installation complete."
    }
    else {
        Write-LogError "Installation requires bash. Download Datomic manually to: $($script:DATOMIC_DIR)"
        exit $script:EXIT_PREREQ
    }
    exit $script:EXIT_SUCCESS
}

# -----------------------------------------------------------------------------
# Port Conflict Detection
# -----------------------------------------------------------------------------

function Test-PortAvailable {
    param([int]$Port, [string]$Service, [bool]$IsIdempotent = $false)

    $result = @{ Available = $true; AlreadyRunning = $false; Skip = $false }

    if (Test-PortInUse -Port $Port) {
        $pids = Find-PidsByPort -Port $Port
        $pidStr = if ($pids.Count -gt 0) { $pids -join ", " } else { "unknown" }

        if ($IsIdempotent) {
            Write-LogInfo "$Service already running on port $Port (PID: $pidStr)"
            $result.AlreadyRunning = $true
            return $result
        }

        if (-not (Test-Interactive)) {
            Write-LogError "Port $Port in use (non-interactive, exiting). PID: $pidStr"
            $result.Available = $false
            return $result
        }

        Write-LogWarn "Port $Port is already in use (PID: $pidStr)"
        $choice = Read-Host "$Service`: [s]kip, s[t]op existing, or [a]bort? [s/t/a]"
        switch -Regex ($choice) {
            '^[tTyY]' {
                Write-LogInfo "Stopping existing $Service..."
                & "$PSScriptRoot\stop.ps1" $Service -Yes -Quiet
                Start-Sleep -Seconds 1
                if (Test-PortInUse -Port $Port) {
                    Write-LogError "Failed to stop $Service on port $Port"
                    $result.Available = $false
                } else {
                    Write-LogInfo "Port $Port is now available"
                }
            }
            '^[sS]|^$' {
                Write-LogInfo "Skipping $Service (already running)"
                $result.Skip = $true
            }
            default {
                Write-LogError "Aborting."
                $result.Available = $false
            }
        }
    }
    return $result
}

# -----------------------------------------------------------------------------
# Datomic Readiness
# -----------------------------------------------------------------------------

function Wait-ForDatomic {
    param([int]$Timeout = $script:PORT_WAIT, [string]$LogFile = (Join-Path $script:LOG_DIR "datomic.log"))
    Write-LogInfo "Waiting for Datomic to be ready (port $($script:DATOMIC_PORT))..."
    if (Wait-ForPort -Port $script:DATOMIC_PORT -Timeout $Timeout) {
        Write-LogInfo "Datomic is ready"
        return $true
    }
    Show-StartupFailure -Name "datomic" -LogFile $LogFile -Port $script:DATOMIC_PORT
    return $false
}

# -----------------------------------------------------------------------------
# Pre-flight Checks
# -----------------------------------------------------------------------------

function Invoke-Checks {
    param([string]$CheckTarget)

    Write-LogInfo "Running pre-flight checks for target: $CheckTarget"
    Write-Host ""
    $failed = 0

    Write-Host -NoNewline "Java ($($script:JAVA_MIN_VERSION)+): "
    if (Test-JavaInstalled) { Write-Host "OK" -ForegroundColor Green }
    else { Write-Host "FAILED" -ForegroundColor Red; $failed++ }

    Write-Host -NoNewline "Leiningen: "
    if (Test-LeinInstalled) { Write-Host "OK" -ForegroundColor Green }
    else { Write-Host "FAILED" -ForegroundColor Red; $failed++ }

    if ($CheckTarget -in "all", "datomic") {
        Write-Host -NoNewline "Datomic installed: "
        if (Test-DatomicInstalled) { Write-Host "OK" -ForegroundColor Green }
        else { Write-Host "FAILED" -ForegroundColor Red; $failed++ }

        Write-Host -NoNewline "Datomic config: "
        if ((Test-Path $script:DATOMIC_CONFIG) -or (Test-Path $script:DATOMIC_CONFIG_TEMPLATE)) {
            Write-Host "OK" -ForegroundColor Green
        } else { Write-Host "FAILED" -ForegroundColor Red; $failed++ }

        Write-Host -NoNewline "Datomic port ($($script:DATOMIC_PORT)): "
        if (Test-PortInUse -Port $script:DATOMIC_PORT) {
            Write-Host "IN USE" -ForegroundColor Yellow
        } else { Write-Host "AVAILABLE" -ForegroundColor Green }
    }

    if ($CheckTarget -in "all", "server") {
        Write-Host -NoNewline "Server port ($($script:SERVER_PORT)): "
        if (Test-PortInUse -Port $script:SERVER_PORT) {
            Write-Host "IN USE" -ForegroundColor Yellow
        } else { Write-Host "AVAILABLE" -ForegroundColor Green }
    }

    if ($CheckTarget -eq "figwheel") {
        Write-Host -NoNewline "Figwheel port ($($script:FIGWHEEL_PORT)): "
        if (Test-PortInUse -Port $script:FIGWHEEL_PORT) {
            Write-Host "IN USE" -ForegroundColor Yellow
        } else { Write-Host "AVAILABLE" -ForegroundColor Green }
    }

    Write-Host ""
    if ($failed -gt 0) { Write-LogError "$failed check(s) failed"; return $false }
    Write-LogInfo "All checks passed"; return $true
}

if ($Check) {
    exit $(if (Invoke-Checks -CheckTarget $Target) { $script:EXIT_SUCCESS } else { $script:EXIT_PREREQ })
}

# -----------------------------------------------------------------------------
# Start a background service (shared helper)
# -----------------------------------------------------------------------------

function Start-BackgroundService {
    param(
        [string]$Name,
        [string]$Command,
        [string[]]$Arguments,
        [int]$Port = 0,
        [int]$StartupChecks = 1
    )

    Remove-StalePidFile -Name $Name

    $logFile = Join-Path $script:LOG_DIR "$Name.log"
    $errFile = Join-Path $script:LOG_DIR "$Name.err.log"

    $proc = Start-Process -FilePath $Command `
        -ArgumentList $Arguments `
        -WorkingDirectory $script:REPO_ROOT `
        -RedirectStandardOutput $logFile `
        -RedirectStandardError $errFile `
        -PassThru -NoNewWindow

    $proc.Id | Out-File (Join-Path $script:LOG_DIR "$Name.pid") -NoNewline
    Write-LogInfo "$Name started (PID $($proc.Id))"
    Write-LogInfo "Logs: $logFile"

    # Early verification
    Start-Sleep -Milliseconds 500
    if ($proc.HasExited) {
        Write-LogError "$Name process died immediately"
        Show-StartupFailure -Name $Name -LogFile $logFile -Port $Port
        exit $script:EXIT_RUNTIME
    }

    # Additional startup checks (for services without a port)
    if ($Port -eq 0 -and $StartupChecks -gt 0) {
        for ($i = 0; $i -lt $StartupChecks; $i++) {
            Start-Sleep -Seconds 1
            if ($proc.HasExited) {
                Write-LogError "$Name process died during startup"
                Show-StartupFailure -Name $Name -LogFile $logFile
                exit $script:EXIT_RUNTIME
            }
        }
    }

    return $proc
}

# -----------------------------------------------------------------------------
# Start Targets
# -----------------------------------------------------------------------------

function Start-Datomic {
    param([bool]$IsIdempotent = $false)

    if (-not (Test-DatomicInstalled)) { exit $script:EXIT_PREREQ }
    $check = Test-PortAvailable -Port $script:DATOMIC_PORT -Service "datomic" -IsIdempotent $IsIdempotent
    if (-not $check.Available) { exit $script:EXIT_RUNTIME }
    if ($check.AlreadyRunning -or $check.Skip) { exit $script:EXIT_SUCCESS }
    if (-not (Initialize-DatomicConfig)) { exit $script:EXIT_PREREQ }

    $transactorCmd = Get-TransactorCommand
    if (-not $transactorCmd) {
        Write-LogError "Datomic transactor not found in $($script:DATOMIC_DIR)\bin\"
        exit $script:EXIT_PREREQ
    }

    Write-LogInfo "Starting Datomic transactor ($($script:DATOMIC_TYPE) $($script:DATOMIC_VERSION))..."

    if ($Background) {
        $proc = Start-BackgroundService -Name "datomic" -Command $transactorCmd `
            -Arguments @($script:DATOMIC_CONFIG) -Port $script:DATOMIC_PORT

        if (-not (Wait-ForDatomic)) {
            Write-LogError "Failed to start Datomic."
            exit $script:EXIT_RUNTIME
        }
    }
    else {
        Write-LogInfo "Running in foreground. Press Ctrl+C to stop."
        & $transactorCmd $script:DATOMIC_CONFIG
    }
}

function Start-Server {
    param([bool]$IsIdempotent = $false)

    $check = Test-PortAvailable -Port $script:SERVER_PORT -Service "server" -IsIdempotent $IsIdempotent
    if (-not $check.Available) { exit $script:EXIT_RUNTIME }
    if ($check.AlreadyRunning -or $check.Skip) { exit $script:EXIT_SUCCESS }

    Set-Location $script:REPO_ROOT

    if ($Background) {
        Start-BackgroundService -Name "server" `
            -Command "lein" `
            -Arguments @("with-profile", "+dev,+start-server", "repl", ":headless") `
            -Port $script:SERVER_PORT
    }
    else {
        Write-LogInfo "Starting REPL with server (profile: +dev,+start-server)..."
        & lein with-profile "+dev,+start-server" repl
    }
}

function Start-Figwheel {
    param([bool]$IsIdempotent = $false)

    $check = Test-PortAvailable -Port $script:FIGWHEEL_PORT -Service "figwheel" -IsIdempotent $IsIdempotent
    if (-not $check.Available) { exit $script:EXIT_RUNTIME }
    if ($check.AlreadyRunning -or $check.Skip) { exit $script:EXIT_SUCCESS }

    Write-LogInfo "Starting Figwheel (ClojureScript hot-reload)..."
    Set-Location $script:REPO_ROOT

    $proc = Start-BackgroundService -Name "figwheel" `
        -Command "lein" -Arguments @("fig:watch") -Port $script:FIGWHEEL_PORT

    Write-LogInfo "Waiting for Figwheel to be ready (port $($script:FIGWHEEL_PORT))..."
    if (Wait-ForPortOrDie -Port $script:FIGWHEEL_PORT -ProcessId $proc.Id -Timeout $script:PORT_WAIT) {
        Write-LogInfo "Figwheel is ready"
    }
    else {
        try {
            Get-Process -Id $proc.Id -ErrorAction Stop | Out-Null
            Write-LogWarn "Figwheel still starting (first run may take minutes for CLJS compilation)"
        }
        catch {
            Show-StartupFailure -Name "figwheel" -LogFile (Join-Path $script:LOG_DIR "figwheel.log") -Port $script:FIGWHEEL_PORT
            exit $script:EXIT_RUNTIME
        }
    }
}

function Start-Garden {
    Write-LogInfo "Starting Garden (CSS auto-compilation)..."
    Set-Location $script:REPO_ROOT

    Start-BackgroundService -Name "garden" `
        -Command "lein" -Arguments @("garden", "auto") -StartupChecks 5

    Write-LogInfo "Garden is running"
}

function Initialize-Database {
    Write-LogInfo "Initializing database..."

    if (-not (Test-PortInUse -Port $script:DATOMIC_PORT)) {
        Write-LogError "Datomic is not running on port $($script:DATOMIC_PORT)"
        Write-LogInfo "Start Datomic first: .\start.ps1 datomic"
        exit $script:EXIT_PREREQ
    }

    Set-Location $script:REPO_ROOT
    & lein with-profile init-db run -m user init-db
    if ($LASTEXITCODE -eq 0) {
        Write-LogInfo "Database initialized successfully"
    }
    else {
        Write-LogError "Database initialization failed"
        exit $script:EXIT_RUNTIME
    }
}

function Start-All {
    param([bool]$IsIdempotent = $false)

    if (-not (Test-DatomicInstalled)) { exit $script:EXIT_PREREQ }

    # Datomic
    $dCheck = Test-PortAvailable -Port $script:DATOMIC_PORT -Service "datomic" -IsIdempotent $IsIdempotent
    if (-not $dCheck.Available) { exit $script:EXIT_RUNTIME }

    $datomicProc = $null
    if (-not $dCheck.AlreadyRunning -and -not $dCheck.Skip) {
        if (-not (Initialize-DatomicConfig)) { exit $script:EXIT_PREREQ }
        Remove-StalePidFile -Name "datomic"

        $transactorCmd = Get-TransactorCommand
        if (-not $transactorCmd) { Write-LogError "Transactor not found"; exit $script:EXIT_PREREQ }

        Write-LogInfo "Starting Datomic transactor (background)..."
        $datomicProc = Start-BackgroundService -Name "datomic" -Command $transactorCmd `
            -Arguments @($script:DATOMIC_CONFIG) -Port $script:DATOMIC_PORT

        if (-not (Wait-ForDatomic)) {
            Write-LogError "Failed to start Datomic."
            exit $script:EXIT_RUNTIME
        }
    }
    else {
        Write-LogInfo "Datomic already running, skipping startup"
    }

    # Server
    $sCheck = Test-PortAvailable -Port $script:SERVER_PORT -Service "server" -IsIdempotent $IsIdempotent
    if (-not $sCheck.Available) { exit $script:EXIT_RUNTIME }
    if ($sCheck.AlreadyRunning -or $sCheck.Skip) {
        Write-LogInfo "Server already running on port $($script:SERVER_PORT)"
        exit $script:EXIT_SUCCESS
    }

    Write-LogInfo "Starting REPL with server (profile: +dev,+start-server)..."
    if ($datomicProc) {
        Write-LogInfo "Note: Ctrl+C will stop the server. Stop Datomic separately with: .\stop.ps1 datomic"
    }
    Set-Location $script:REPO_ROOT

    try {
        & lein with-profile "+dev,+start-server" repl
    }
    finally {
        # Clean up Datomic if we started it
        if ($datomicProc -and -not $datomicProc.HasExited) {
            Write-LogInfo "Stopping Datomic (PID $($datomicProc.Id))..."
            Stop-ProcessGracefully -ProcessId $datomicProc.Id
            Remove-Item (Join-Path $script:LOG_DIR "datomic.pid") -Force -ErrorAction SilentlyContinue
        }
    }
}

function Start-Prod {
    param([bool]$SkipBuild = $false)

    $jar = Join-Path $script:REPO_ROOT "target\orcpub.jar"

    if ($SkipBuild) {
        if (Test-Path $jar) {
            Write-LogInfo "Skipping build (-NoBuild/-Skip)"
        }
        else {
            Write-LogError "No jar found at $jar - cannot skip build"
            exit $script:EXIT_PREREQ
        }
    }
    else {
        Write-LogInfo "Building production uberjar..."
        & "$PSScriptRoot\prod.ps1"
        if ($LASTEXITCODE -ne 0) { exit $script:EXIT_RUNTIME }
    }

    Write-LogInfo "Starting production server: java -jar $jar"
    Set-Location $script:REPO_ROOT
    & java -jar $jar
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

if (-not (Test-JavaInstalled)) { exit $script:EXIT_PREREQ }
if (-not (Test-LeinInstalled)) { exit $script:EXIT_PREREQ }

switch ($Target) {
    "all"      { Start-All -IsIdempotent $Idempotent.IsPresent }
    "datomic"  { Start-Datomic -IsIdempotent $Idempotent.IsPresent }
    "server"   { Start-Server -IsIdempotent $Idempotent.IsPresent }
    "figwheel" { Start-Figwheel -IsIdempotent $Idempotent.IsPresent }
    "garden"   { Start-Garden }
    "init-db"  { Initialize-Database }
    "prod"     { Start-Prod -SkipBuild $NoBuild.IsPresent }
}
