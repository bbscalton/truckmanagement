# Provisions Cloudflare R2, D1, KV for TruckMgmt r2-proxy and deploys the Worker.
# Requires: npx wrangler logged in (wrangler login)
$ErrorActionPreference = 'Stop'
$Root = Split-Path -Parent $PSScriptRoot
$ProxyDir = Join-Path $Root 'r2-proxy'
$WranglerJson = Join-Path $ProxyDir 'wrangler.jsonc'

Push-Location $ProxyDir
try {
    Write-Host '=== TruckMgmt Cloudflare setup ==='
    Write-Host ''

    $whoami = npx wrangler whoami 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host 'ERROR: Not logged in to Cloudflare. Run: npx wrangler login'
        exit 1
    }

    $bucketList = npx wrangler r2 bucket list 2>&1 | Out-String
    if ($bucketList -notmatch 'truckmgmt-uploads') {
        Write-Host 'Creating R2 bucket truckmgmt-uploads...'
        npx wrangler r2 bucket create truckmgmt-uploads
        if ($LASTEXITCODE -ne 0) { throw 'R2 bucket create failed' }
    } else {
        Write-Host 'R2 bucket truckmgmt-uploads already exists.'
    }

    $d1List = npx wrangler d1 list 2>&1 | Out-String
    if ($d1List -notmatch 'truckmgmt-ops') {
        Write-Host 'Creating D1 database truckmgmt-ops...'
        $createOut = npx wrangler d1 create truckmgmt-ops 2>&1 | Out-String
        Write-Host $createOut
        if ($createOut -match 'database_id":\s*"([a-f0-9-]+)"') {
            $dbId = $Matches[1]
            Write-Host "Copy database_id $dbId into r2-proxy/wrangler.jsonc if placeholders remain."
        }
    } else {
        Write-Host 'D1 database truckmgmt-ops already exists.'
    }

    $kvList = npx wrangler kv namespace list 2>&1 | Out-String
    if ($kvList -notmatch 'TRUCKMGMT_EDGE_CACHE') {
        Write-Host 'Creating KV namespace TRUCKMGMT_EDGE_CACHE...'
        $kvOut = npx wrangler kv namespace create TRUCKMGMT_EDGE_CACHE 2>&1 | Out-String
        Write-Host $kvOut
        if ($kvOut -match '"id":\s*"([a-f0-9]+)"') {
            $kvId = $Matches[1]
            Write-Host "Copy KV id $kvId into r2-proxy/wrangler.jsonc if placeholders remain."
        }
    } else {
        Write-Host 'KV namespace TRUCKMGMT_EDGE_CACHE already exists.'
    }

    if ((Get-Content $WranglerJson -Raw) -match '00000000-0000-0000-0000-000000000001|00000000000000000000000000000000') {
        Write-Host ''
        Write-Host 'WARNING: wrangler.jsonc still has placeholder D1/KV ids.'
        Write-Host 'Run: npx wrangler d1 list'
        Write-Host 'Run: npx wrangler kv namespace list'
        Write-Host 'Update r2-proxy/wrangler.jsonc, then re-run this script.'
        exit 1
    }

    Write-Host ''
    Write-Host 'Applying D1 migrations (remote)...'
    npx wrangler d1 migrations apply truckmgmt-ops --remote
    if ($LASTEXITCODE -ne 0) { throw 'D1 migration failed' }

    Write-Host ''
    Write-Host 'Deploying Worker...'
    npx wrangler deploy
    if ($LASTEXITCODE -ne 0) { throw 'Worker deploy failed' }

    Write-Host ''
    Write-Host 'Done. Set VITE_R2_BASE_URL / R2_MEDIA_PROXY_BASE_URL to the Worker URL printed above.'
} finally {
    Pop-Location
}
