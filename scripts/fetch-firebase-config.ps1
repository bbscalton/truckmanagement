# Downloads Firebase SDK configs for TruckMgmt apps and writes local files.
# Requires: firebase CLI logged in, apps registered in truckmgmt-dev.
$ErrorActionPreference = 'Stop'
$ProjectId = 'truckmgmt-dev'
$Root = Split-Path -Parent $PSScriptRoot

$AppIds = @{
    Dispatcher = '1:779631101524:android:66bc491e7bcff919184fc1'
    Driver     = '1:779631101524:android:136d8c41a7d0469d184fc1'
    Customer   = '1:779631101524:android:b0a4133c2683de83184fc1'
    Web        = '1:779631101524:web:5d87699af9c4c41a184fc1'
}

$PackageMap = @{
    Dispatcher = 'com.truckmgmt.dispatcher'
    Driver     = 'com.truckmgmt.driver'
    Customer   = 'com.truckmgmt.customer'
}

function Get-SdkConfigJson {
    param([string]$Platform, [string]$AppId)
    $raw = firebase apps:sdkconfig $Platform $AppId --project $ProjectId 2>&1
    if ($LASTEXITCODE -ne 0) { throw "firebase apps:sdkconfig failed: $raw" }
    return ($raw | Out-String).Trim() | ConvertFrom-Json
}

function Write-AndroidConfig {
    param([string]$Name, [string]$AppId, [string]$Package, [string]$OutPath)
    $full = Get-SdkConfigJson -Platform 'ANDROID' -AppId $AppId
    $client = $full.client | Where-Object { $_.client_info.android_client_info.package_name -eq $Package }
    if (-not $client) { throw "No client for package $Package in SDK config" }
    $out = @{
        project_info         = $full.project_info
        client               = @($client)
        configuration_version = '1'
    }
    $json = $out | ConvertTo-Json -Depth 10
    Set-Content -Path $OutPath -Value $json -Encoding UTF8
    Write-Host "  wrote $OutPath"
}

Write-Host "Fetching Android google-services.json files..."
Write-AndroidConfig -Name 'Dispatcher' -AppId $AppIds.Dispatcher -Package $PackageMap.Dispatcher `
    -OutPath (Join-Path $Root 'dispatcher\google-services.json')
Write-AndroidConfig -Name 'Driver' -AppId $AppIds.Driver -Package $PackageMap.Driver `
    -OutPath (Join-Path $Root 'driver\google-services.json')
Write-AndroidConfig -Name 'Customer' -AppId $AppIds.Customer -Package $PackageMap.Customer `
    -OutPath (Join-Path $Root 'customer\google-services.json')

Write-Host "Fetching web config for dispatcher-web/.env..."
$web = Get-SdkConfigJson -Platform 'WEB' -AppId $AppIds.Web
$envPath = Join-Path $Root 'dispatcher-web\.env'
$envExample = Join-Path $Root 'dispatcher-web\.env.example'

$lines = @()
if (Test-Path $envExample) {
    foreach ($line in Get-Content $envExample) {
        if ($line -match '^VITE_FIREBASE_API_KEY=') {
            $lines += "VITE_FIREBASE_API_KEY=$($web.apiKey)"
        } elseif ($line -match '^VITE_FIREBASE_AUTH_DOMAIN=') {
            $lines += "VITE_FIREBASE_AUTH_DOMAIN=$($web.authDomain)"
        } elseif ($line -match '^VITE_FIREBASE_PROJECT_ID=') {
            $lines += "VITE_FIREBASE_PROJECT_ID=$($web.projectId)"
        } elseif ($line -match '^VITE_FIREBASE_STORAGE_BUCKET=') {
            $lines += "VITE_FIREBASE_STORAGE_BUCKET=$($web.storageBucket)"
        } elseif ($line -match '^VITE_FIREBASE_MESSAGING_SENDER_ID=') {
            $lines += "VITE_FIREBASE_MESSAGING_SENDER_ID=$($web.messagingSenderId)"
        } elseif ($line -match '^VITE_FIREBASE_APP_ID=') {
            $lines += "VITE_FIREBASE_APP_ID=$($web.appId)"
        } else {
            $lines += $line
        }
    }
} else {
    $lines = @(
        "VITE_FIREBASE_API_KEY=$($web.apiKey)",
        "VITE_FIREBASE_AUTH_DOMAIN=$($web.authDomain)",
        "VITE_FIREBASE_PROJECT_ID=$($web.projectId)",
        "VITE_FIREBASE_STORAGE_BUCKET=$($web.storageBucket)",
        "VITE_FIREBASE_MESSAGING_SENDER_ID=$($web.messagingSenderId)",
        "VITE_FIREBASE_APP_ID=$($web.appId)",
        'VITE_GOOGLE_MAPS_API_KEY=',
        'VITE_R2_BASE_URL=https://truckmgmt-media-proxy.neuereatec.workers.dev'
    )
}

Set-Content -Path $envPath -Value ($lines -join "`n") -Encoding UTF8
Write-Host "  wrote $envPath (add VITE_GOOGLE_MAPS_API_KEY if missing)"
Write-Host "Done."
