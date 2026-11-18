[CmdletBinding()]
param(
    [Parameter(Position = 0)]
    [ValidateSet('build', 'test', 'server', 'client', 'load')]
    [string]$Action = 'build',

    [string]$ServerHost = '127.0.0.1',

    [ValidateRange(1, 65535)]
    [int]$Port = 5000,

    [ValidateRange(1, [int]::MaxValue)]
    [int]$Clients = 20,

    [ValidateRange(1, [int]::MaxValue)]
    [int]$Rooms = 2,

    [ValidateRange(1, [int]::MaxValue)]
    [int]$MessagesPerSecond = 10,

    [ValidateRange(1, [int]::MaxValue)]
    [int]$Seconds = 10
)

Set-StrictMode -Version 3.0
$ErrorActionPreference = 'Stop'
# Inspect native exit codes ourselves in both Windows PowerShell and PowerShell.
$PSNativeCommandUseErrorActionPreference = $false
$classes = Join-Path $PSScriptRoot 'out'

function Find-JdkBin {
    if ($env:JAVA_HOME) {
        $homeBin = Join-Path $env:JAVA_HOME 'bin'
        if ((Test-Path -LiteralPath (Join-Path $homeBin 'javac.exe') -PathType Leaf) -and
            (Test-Path -LiteralPath (Join-Path $homeBin 'java.exe') -PathType Leaf)) {
            return $homeBin
        }
    }

    $compiler = Get-Command javac.exe -CommandType Application -ErrorAction SilentlyContinue |
        Select-Object -First 1
    if ($compiler) {
        $pathBin = Split-Path -Parent $compiler.Source
        if (Test-Path -LiteralPath (Join-Path $pathBin 'java.exe') -PathType Leaf) {
            return $pathBin
        }
    }

    throw 'JDK 21 is required. Set JAVA_HOME to the JDK directory or add its bin directory to PATH.'
}

$jdkBin = Find-JdkBin
$javac = Join-Path $jdkBin 'javac.exe'
$java = Join-Path $jdkBin 'java.exe'

function Build-Chat {
    $sources = @(
        foreach ($directory in @('src', 'test', 'tools')) {
            Get-ChildItem -LiteralPath (Join-Path $PSScriptRoot $directory) -Filter '*.java' -File |
                ForEach-Object { $_.FullName }
        }
    )
    New-Item -ItemType Directory -Path $classes -Force | Out-Null
    & $javac -encoding UTF-8 --release 21 -Xlint:all -d $classes @sources
    if ($LASTEXITCODE -ne 0) {
        throw "Compilation failed ($LASTEXITCODE). Use JDK 21 or later."
    }
}

function Invoke-ChatJava {
    param([string]$MainClass, [string[]]$Options = @())

    if (-not (Test-Path -LiteralPath (Join-Path $classes "$MainClass.class") -PathType Leaf)) {
        throw 'Classes are missing. Run .\chat.ps1 build first.'
    }
    & $java -ea -cp $classes $MainClass @Options
    if ($LASTEXITCODE -ne 0) {
        throw "$MainClass exited with code $LASTEXITCODE."
    }
}

switch ($Action) {
    'build' { Build-Chat }
    'test' {
        Build-Chat
        Invoke-ChatJava ChatServerTest
        Invoke-ChatJava CourseAcceptanceTest
    }
    'server' { Invoke-ChatJava ChatServer }
    'client' { Invoke-ChatJava ChatClient -Options @($ServerHost, $Port) }
    'load' {
        Invoke-ChatJava LoadTest -Options @($ServerHost, $Port, $Clients, $Rooms, $MessagesPerSecond, $Seconds)
    }
}
