<#
.SYNOPSIS
    Spike for feature/code-sandbox: builds the sandbox image and runs the sample programs in it
    with the limits the runner will use, then reports exit code, time and leftover containers.

.DESCRIPTION
    Needs Docker running and tar.exe (included in Windows 10 1803+). Nothing is mounted from the host:
    each sample is streamed into the container as a tar archive on stdin.
    Results are written to a temp folder; the script prints its path.

.EXAMPLE
    .\run-spike.ps1
    .\run-spike.ps1 -Sample infinite_loop,fork_bomb -ShowOutput
    .\run-spike.ps1 -SkipBuild -Sample ok_hello
#>
[CmdletBinding()]
param(
    [string[]]$Sample = @(),
    [switch]$SkipBuild,
    [switch]$ShowOutput,
    [int]$OuterTimeoutSeconds = 40,
    [string]$Image = 'jla-sandbox:spike'
)

$ErrorActionPreference = 'Continue'
# `powershell -File` passes "a,b,c" as one string; split it so lists work both ways.
$Sample = @($Sample | ForEach-Object { $_ -split ',' } | Where-Object { $_ })
$sandboxDir = Split-Path -Parent $PSScriptRoot
$samplesDir = Join-Path $PSScriptRoot 'samples'
$workDir = Join-Path ([IO.Path]::GetTempPath()) ('jla-spike-' + [guid]::NewGuid().ToString('N').Substring(0, 8))
New-Item -ItemType Directory -Path $workDir | Out-Null

if (-not $SkipBuild) {
    Write-Host "Building $Image ..." -ForegroundColor Cyan
    docker build --tag $Image $sandboxDir
    if ($LASTEXITCODE -ne 0) { throw 'docker build failed' }
}

# The limits the runner will apply to every submission. Keep in sync with docs when they change.
# The container starts as root but keeps only the three capabilities the launcher needs to hand the
# student's program to an unprivileged uid; see launcher.sh.
$limits = @(
    '--network none',
    '--read-only',
    '--cap-drop ALL',
    '--cap-add SETUID',
    '--cap-add SETGID',
    '--cap-add KILL',
    '--security-opt no-new-privileges',
    '--user 0:0',
    '--memory 512m',
    '--memory-swap 512m',
    '--cpus 1',
    '--pids-limit 128',
    '--ulimit nofile=256:256',
    '--tmpfs /work:rw,nosuid,nodev,noexec,size=32m,mode=0711',
    '--tmpfs /build:rw,nosuid,nodev,noexec,size=16m,uid=10003,gid=10003,mode=0755',
    '--tmpfs /tmp:rw,nosuid,nodev,noexec,size=16m,mode=1777',
    '-e COMPILE_TIMEOUT_S=10',
    '-e RUN_TIMEOUT_S=5',
    '-e MAX_OUTPUT_BYTES=65536'
) -join ' '

$names = Get-ChildItem -Directory $samplesDir | ForEach-Object { $_.Name }
if ($Sample.Count -gt 0) { $names = $names | Where-Object { $Sample -contains $_ } }

$rows = @()
foreach ($name in $names) {
    $dir = Join-Path $samplesDir $name
    $container = 'jla-spike-' + [guid]::NewGuid().ToString('N').Substring(0, 8)
    $outFile = Join-Path $workDir "$name.out.bin"
    $errFile = Join-Path $workDir "$name.err.txt"
    $cmdFile = Join-Path $workDir "$name.cmd"

    # A .cmd file keeps the tar | docker pipe binary-safe (the PowerShell 5.1 pipeline would mangle bytes).
    $line = "tar --format ustar -cf - -C `"$dir`" . | docker run --rm -i --name $container --label jla-sandbox=spike $limits $Image > `"$outFile`" 2> `"$errFile`""
    Set-Content -Path $cmdFile -Value "@echo off`r`n$line" -Encoding ASCII

    Write-Host ""
    Write-Host "=== $name" -ForegroundColor Cyan
    $sw = [Diagnostics.Stopwatch]::StartNew()
    $p = Start-Process -FilePath cmd.exe -ArgumentList @('/c', "`"$cmdFile`"") -NoNewWindow -PassThru
    $null = $p.Handle   # PowerShell 5.1 needs this to be able to read ExitCode afterwards
    $finished = $p.WaitForExit($OuterTimeoutSeconds * 1000)
    if (-not $finished) {
        Write-Host "outer timeout after ${OuterTimeoutSeconds}s - killing container" -ForegroundColor Red
        docker rm -f $container | Out-Null
        $p.Kill()
    }
    $p.WaitForExit()
    $sw.Stop()

    $leftover = docker ps -aq --filter "name=$container"
    $outBytes = 0
    if (Test-Path $outFile) { $outBytes = (Get-Item $outFile).Length }

    if (Test-Path $errFile) { Get-Content $errFile | ForEach-Object { Write-Host "  $_" } }
    if ($ShowOutput -and (Test-Path $outFile)) {
        Write-Host "  --- frames ---"
        [Text.Encoding]::UTF8.GetString([IO.File]::ReadAllBytes($outFile)) | Write-Host
    }

    $rows += [pscustomobject]@{
        Sample     = $name
        DockerExit = $p.ExitCode
        Seconds    = [math]::Round($sw.Elapsed.TotalSeconds, 2)
        OutBytes   = $outBytes
        Leftover   = if ($leftover) { 'YES' } else { 'no' }
        OuterKill  = if ($finished) { 'no' } else { 'YES' }
    }
}

Write-Host ""
Write-Host "=== Summary" -ForegroundColor Cyan
$rows | Format-Table -AutoSize

# Safety net: nothing labelled by this spike may survive.
$stray = docker ps -aq --filter "label=jla-sandbox=spike"
if ($stray) {
    Write-Host "Removing stray containers: $stray" -ForegroundColor Red
    docker rm -f $stray | Out-Null
}
Write-Host "Raw results: $workDir"
