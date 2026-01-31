param(
  [string]$BackendDir = "${PSScriptRoot}\..",
  [string]$GradleVersion = '8.10.2',
  [string]$JdkHome = 'C:\Program Files\Eclipse Adoptium\jdk-17.0.17.10-hotspot'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$BackendDir = (Resolve-Path $BackendDir).Path

if (!(Test-Path $JdkHome)) {
  if ($env:JAVA_HOME -and (Test-Path $env:JAVA_HOME)) {
    $JdkHome = $env:JAVA_HOME
  } else {
    $javaCmd = Get-Command java -ErrorAction SilentlyContinue
    if ($null -ne $javaCmd) {
      $javaPath = $javaCmd.Source
      $candidate = Split-Path -Parent (Split-Path -Parent $javaPath)
      if (Test-Path $candidate) {
        $JdkHome = $candidate
      }
    }
  }
}

if (!(Test-Path $JdkHome)) {
  throw "JDK not found at: $JdkHome. Set JAVA_HOME or install JDK 17."
}

$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"

$wrapperJar = Join-Path $BackendDir 'gradle\wrapper\gradle-wrapper.jar'

if (Test-Path $wrapperJar) {
  Write-Host "Gradle wrapper OK: $wrapperJar"
  exit 0
}

$localDir = Join-Path $BackendDir '.gradle-local'
New-Item -ItemType Directory -Force -Path $localDir | Out-Null

$zipPath = Join-Path $localDir "gradle-$GradleVersion-bin.zip"
$distUrl = "https://services.gradle.org/distributions/gradle-$GradleVersion-bin.zip"

if (!(Test-Path $zipPath)) {
  Write-Host "Downloading $distUrl"
  Invoke-WebRequest -Uri $distUrl -OutFile $zipPath
}

$gradleHome = Join-Path $localDir "gradle-$GradleVersion"
if (!(Test-Path $gradleHome)) {
  Write-Host "Extracting $zipPath"
  Expand-Archive -Force -Path $zipPath -DestinationPath $localDir
}

$gradleBat = Join-Path $gradleHome 'bin\gradle.bat'
if (!(Test-Path $gradleBat)) {
  throw "Gradle not found at: $gradleBat"
}

Write-Host "Generating wrapper using $gradleBat"
& $gradleBat -p $BackendDir wrapper --gradle-version $GradleVersion --distribution-type bin

if (!(Test-Path $wrapperJar)) {
  throw "Wrapper jar still missing: $wrapperJar"
}

Write-Host "Gradle wrapper generated: $wrapperJar"
