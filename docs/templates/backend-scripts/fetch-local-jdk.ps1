#Requires -Version 5.1
<#
.SYNOPSIS
  Downloads Eclipse Temurin (pinned major) into backend/.jdk (parent of scripts/).
.NOTES
  Override major version: $env:ADOPTIUM_JAVA_VERSION = '17'
#>
$ErrorActionPreference = 'Stop'

$BackendRoot = Split-Path -Parent $PSScriptRoot
$TargetJdk = Join-Path $BackendRoot '.jdk'
$javaMajor = if ($env:ADOPTIUM_JAVA_VERSION) { $env:ADOPTIUM_JAVA_VERSION.Trim() } else { '21' }

function Get-AdoptiumPlatform {
  $ri = [System.Runtime.InteropServices.RuntimeInformation]
  $osp = [System.Runtime.InteropServices.OSPlatform]
  if ($ri::IsOSPlatform($osp::Windows)) {
    $os = 'windows'
    $cpu = $ri::OSArchitecture
    $arch = switch ($cpu.ToString()) {
      'Arm64' { 'aarch64' }
      'X64' { 'x64' }
      'X86' { 'x86' }
      default { throw "Unsupported Windows CPU architecture: $cpu" }
    }
    return @{ Os = $os; Arch = $arch; UseZip = $true }
  }
  if ($ri::IsOSPlatform($osp::OSX)) {
    $os = 'mac'
    $m = (& uname -m).Trim()
    $arch = if ($m -eq 'arm64') { 'aarch64' } elseif ($m -eq 'x86_64') { 'x64' } else { throw "Unsupported macOS machine: $m" }
    return @{ Os = $os; Arch = $arch; UseZip = $false }
  }
  if ($ri::IsOSPlatform($osp::Linux)) {
    $os = 'linux'
    $m = (& uname -m).Trim()
    $arch = switch ($m) {
      'aarch64' { 'aarch64' }
      'arm64' { 'aarch64' }
      'x86_64' { 'x64' }
      default { throw "Unsupported Linux machine: $m" }
    }
    return @{ Os = $os; Arch = $arch; UseZip = $false }
  }
  throw 'Unsupported OS (use fetch-local-jdk.ps1 on Windows / macOS / Linux with PowerShell Core).'
}

$p = Get-AdoptiumPlatform
$url = "https://api.adoptium.net/v3/binary/latest/$javaMajor/ga/$($p.Os)/$($p.Arch)/jdk/hotspot/normal/eclipse"
$tmp = Join-Path ([System.IO.Path]::GetTempPath()) ("temurin-" + [Guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory -Path $tmp -Force | Out-Null
try {
  $ext = if ($p.UseZip) { 'zip' } else { 'tar.gz' }
  $archive = Join-Path $tmp ("temurin.$ext")
  Write-Host "Downloading Temurin $javaMajor ($($p.Os)/$($p.Arch))…"
  Invoke-WebRequest -Uri $url -OutFile $archive -MaximumRedirection 10

  $unpack = Join-Path $tmp 'unpack'
  New-Item -ItemType Directory -Path $unpack -Force | Out-Null

  if ($p.UseZip) {
    Expand-Archive -LiteralPath $archive -DestinationPath $unpack -Force
  } else {
    tar -xzf $archive -C $unpack
  }

  $inner = Get-ChildItem -LiteralPath $unpack -Directory | Select-Object -First 1
  if (-not $inner) {
    throw "Unexpected archive layout: no directory under $unpack"
  }

  if (Test-Path -LiteralPath $TargetJdk) {
    Remove-Item -LiteralPath $TargetJdk -Recurse -Force
  }
  New-Item -ItemType Directory -Path $TargetJdk -Force | Out-Null
  Get-ChildItem -LiteralPath $inner.FullName -Force | ForEach-Object {
    Move-Item -LiteralPath $_.FullName -Destination $TargetJdk
  }

  Write-Host "Installed JDK to: $TargetJdk"
  Write-Host "Set JAVA_HOME to that path, then run .\mvnw.cmd or .\mvnw"
}
finally {
  Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue
}
