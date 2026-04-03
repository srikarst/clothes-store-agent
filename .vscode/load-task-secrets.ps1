param(
    [string]$SecretsFile = (Join-Path $PSScriptRoot "tasks.secrets.json"),
    [string[]]$RequiredKeys = @()
)

Set-StrictMode -Version Latest
$ErrorActionPreference = "Stop"

$secretsPath = $SecretsFile

if (-not (Test-Path -LiteralPath $secretsPath)) {
    throw "Missing $secretsPath. Copy .vscode/tasks.secrets.example.json to .vscode/tasks.secrets.json and fill in your values."
}

$rawJson = Get-Content -LiteralPath $secretsPath -Raw
if ([string]::IsNullOrWhiteSpace($rawJson)) {
    throw "The file $secretsPath is empty."
}

try {
    $secrets = $rawJson | ConvertFrom-Json -ErrorAction Stop
}
catch {
    throw "Invalid JSON in $secretsPath. $($_.Exception.Message)"
}

foreach ($key in $RequiredKeys) {
    if ([string]::IsNullOrWhiteSpace($key)) {
        continue
    }
    if (-not ($secrets.PSObject.Properties.Name -contains $key)) {
        throw "Missing required key '$key' in $secretsPath."
    }
    $value = [string]$secrets.$key
    if ([string]::IsNullOrWhiteSpace($value)) {
        throw "Required key '$key' in $secretsPath is empty."
    }
}

foreach ($property in $secrets.PSObject.Properties) {
    if (-not [string]::IsNullOrWhiteSpace([string]$property.Value)) {
        Set-Item -Path "env:$($property.Name)" -Value ([string]$property.Value)
    }
}
