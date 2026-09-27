$ErrorActionPreference = 'Stop'
$serverDir = Split-Path -Parent $MyInvocation.MyCommand.Path
if (-not (Test-Path -LiteralPath (Join-Path $serverDir 'server-config.json'))) {
    throw 'Run server\setup.ps1 first'
}

function Resolve-NodeExecutable {
    $nodeCommand = Get-Command node.exe -ErrorAction SilentlyContinue
    if ($nodeCommand) {
        return $nodeCommand.Source
    }

    $candidates = @(
        (Join-Path $env:ProgramFiles 'nodejs\node.exe'),
        (Join-Path ${env:ProgramFiles(x86)} 'nodejs\node.exe'),
        (Join-Path $env:LOCALAPPDATA 'Programs\nodejs\node.exe'),
        (Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe')
    ) | Where-Object { $_ -and (Test-Path -LiteralPath $_) }

    if ($candidates.Count -gt 0) {
        return ($candidates | Select-Object -First 1)
    }

    $codexRuntimeRoot = Join-Path $env:USERPROFILE '.cache\codex-runtimes'
    if (Test-Path -LiteralPath $codexRuntimeRoot) {
        $bundledNode = Get-ChildItem -LiteralPath $codexRuntimeRoot -Filter node.exe -File -Recurse -ErrorAction SilentlyContinue |
            Sort-Object LastWriteTime -Descending |
            Select-Object -First 1
        if ($bundledNode) {
            return $bundledNode.FullName
        }
    }

    throw 'Node.js 18+ was not found. Install Node.js from https://nodejs.org/ or open the project in Codex once, then retry.'
}

$nodeExe = Resolve-NodeExecutable
$nodeVersion = (& $nodeExe --version).Trim()
$nodeMajor = [int](($nodeVersion -replace '^v', '') -split '\.')[0]
if ($nodeMajor -lt 18) {
    throw "Node.js 18+ is required; found $nodeVersion at $nodeExe"
}

Write-Host "Starting TopJournal AI with Node.js $nodeVersion"
Set-Location -LiteralPath $serverDir
& $nodeExe .\server.mjs
