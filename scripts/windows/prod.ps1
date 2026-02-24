#Requires -Version 5.1
<#
.SYNOPSIS
    Build OrcPub Production Uberjar (Windows)

.DESCRIPTION
    PowerShell mirror of scripts/prod.sh. Builds the full production artifact:
    CLJS + CSS + AOT-compiled uberjar.

    Uses a three-step build to work around lein's compile subprocess hang
    (see docs/LEIN-UBERJAR-HANG.md):
      1. CLJS via figwheel-main (exits cleanly)
      2. AOT compile with timeout (hangs but .class files are written)
      3. Package uberjar (no re-compile, no clean)

.PARAMETER SkipCljs
    Skip CLJS compilation (reuse existing JS output).

.EXAMPLE
    .\prod.ps1              # Full production build
    .\prod.ps1 -SkipCljs    # Skip CLJS, rebuild backend only
#>

[CmdletBinding()]
param(
    [switch]$SkipCljs,
    [Alias("h")][switch]$Help
)

. "$PSScriptRoot\common.ps1"

# Build configuration
$CompileTimeout = if ($env:COMPILE_TIMEOUT) { [int]$env:COMPILE_TIMEOUT } else { 300 }
$UberjarTimeout = if ($env:UBERJAR_TIMEOUT) { [int]$env:UBERJAR_TIMEOUT } else { 600 }
$CljsOutput = Join-Path $script:REPO_ROOT "resources\public\js\compiled\orcpub.js"
$AotMarker = Join-Path $script:REPO_ROOT "target\classes\orcpub\server__init.class"
$JarOutput = Join-Path $script:REPO_ROOT "target\orcpub.jar"

if ($Help) {
    Get-Help $PSCommandPath -Detailed
    exit $script:EXIT_SUCCESS
}

# -----------------------------------------------------------------------------
# Timeout helper
# -----------------------------------------------------------------------------

function Invoke-WithTimeout {
    <#
    .SYNOPSIS
    Run a command with a timeout. Returns $true if the command finished, $false if timed out.
    #>
    param(
        [int]$Seconds,
        [string]$Command,
        [string[]]$Arguments
    )

    $proc = Start-Process -FilePath $Command `
        -ArgumentList $Arguments `
        -WorkingDirectory $script:REPO_ROOT `
        -PassThru -NoNewWindow

    if ($proc.WaitForExit($Seconds * 1000)) {
        return $true
    }
    else {
        Stop-ProcessGracefully -ProcessId $proc.Id
        return $false
    }
}

# -----------------------------------------------------------------------------
# Build Steps
# -----------------------------------------------------------------------------

function Build-Cljs {
    Write-LogInfo "Step 1/3: Building production CLJS (advanced optimizations)..."
    Set-Location $script:REPO_ROOT

    & lein fig:prod
    if ($LASTEXITCODE -ne 0) {
        Write-LogError "CLJS build failed"
        exit $script:EXIT_RUNTIME
    }

    if (Test-Path $CljsOutput) {
        Write-LogInfo "CLJS build complete: $CljsOutput"
    }
    else {
        Write-LogError "CLJS build succeeded but output not found: $CljsOutput"
        exit $script:EXIT_RUNTIME
    }
}

function Invoke-AotCompile {
    Write-LogInfo "Step 2/3: AOT compiling (timeout: ${CompileTimeout}s)..."
    Set-Location $script:REPO_ROOT

    # The compile subprocess hangs after writing all .class files due to
    # non-daemon threads. We use timeout to kill it, then verify the output.
    Invoke-WithTimeout -Seconds $CompileTimeout `
        -Command "lein" `
        -Arguments @("with-profile", "uberjar,uberjar-package", "compile") | Out-Null

    if (Test-Path $AotMarker) {
        Write-LogInfo "AOT compile complete (classes written)"
    }
    else {
        Write-LogError "AOT compile failed - marker class not found: $AotMarker"
        exit $script:EXIT_RUNTIME
    }
}

function Build-Uberjar {
    Write-LogInfo "Step 3/3: Packaging uberjar (Garden CSS + jar)..."
    Set-Location $script:REPO_ROOT

    # uberjar-package profile: auto-clean false, prep-tasks ^:replace [["garden" "once"]]
    Invoke-WithTimeout -Seconds $UberjarTimeout `
        -Command "lein" `
        -Arguments @("with-profile", "uberjar,uberjar-package", "uberjar") | Out-Null

    if (Test-Path $JarOutput) {
        $size = (Get-Item $JarOutput).Length / 1MB
        Write-LogInfo ("Build complete: $JarOutput ({0:N1} MB)" -f $size)
    }
    else {
        Write-LogError "Uberjar packaging failed - jar not found: $JarOutput"
        exit $script:EXIT_RUNTIME
    }
}

# -----------------------------------------------------------------------------
# Main
# -----------------------------------------------------------------------------

if (-not (Test-JavaInstalled)) { exit $script:EXIT_PREREQ }
if (-not (Test-LeinInstalled)) { exit $script:EXIT_PREREQ }

Write-LogInfo "Building production uberjar..."
Write-Host ""

# Step 1: CLJS
if ($SkipCljs) {
    if (Test-Path $CljsOutput) {
        Write-LogInfo "Step 1/3: Skipping CLJS (-SkipCljs, using existing)"
    }
    else {
        Write-LogWarn "Step 1/3: -SkipCljs but no existing CLJS output found"
        Write-LogWarn "Building CLJS anyway..."
        Build-Cljs
    }
}
else {
    Build-Cljs
}

# Step 2: AOT compile
Invoke-AotCompile

# Step 3: Package
Build-Uberjar

Write-Host ""
Write-LogInfo "Production build successful!"
Write-LogInfo "Run with: java -jar $JarOutput"
