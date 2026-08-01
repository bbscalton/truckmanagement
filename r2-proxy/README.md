# TruckMgmt media proxy (Cloudflare Worker)

Canonical storage and edge ops layer for TruckMgmt. **R2** holds media blobs and public APKs; **D1** stores fleet/device heartbeats; **KV** caches fleet snapshots.

Firebase Storage is **not** used for new uploads — point apps and scripts at this Worker instead.

## Bindings (`wrangler.jsonc`)

| Binding | Resource | Name |
|---------|----------|------|
| `MEDIA_BUCKET` | R2 | `truckmgmt-uploads` |
| `DB` | D1 | `truckmgmt-ops` |
| `EDGE_CACHE` | KV | `TRUCKMGMT_EDGE_CACHE` |

## Setup

From repo root (requires [Wrangler](https://developers.cloudflare.com/workers/wrangler/) logged in):

```bat
scripts\setup-cloudflare.bat
```

Or manually:

```bash
cd r2-proxy
npx wrangler r2 bucket create truckmgmt-uploads   # skip if exists
npx wrangler d1 create truckmgmt-ops              # skip if exists; copy database_id into wrangler.jsonc
npx wrangler kv namespace create TRUCKMGMT_EDGE_CACHE  # skip if exists; copy id into wrangler.jsonc
npx wrangler d1 migrations apply truckmgmt-ops --remote
npx wrangler deploy
```

After deploy, set `VITE_R2_BASE_URL` (web) and `R2_MEDIA_PROXY_BASE_URL` (Android `TruckMgmtConstants`) to your Worker URL, e.g. `https://truckmgmt-media-proxy.<account>.workers.dev`.

## HTTP API

Base URL: Worker deploy URL (see above). All responses include CORS headers for browser clients.

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/health`, `/platform-health` | Platform health (Firebase probe, D1, KV, R2) |
| `GET` | `/edge/fleet/{fleetId}` | Fleet snapshot + device heartbeats (KV → D1 fallback) |
| `POST` | `/edge/sync/device` | Upsert device heartbeat; refresh fleet snapshot |
| `PUT` | `/upload/{key}` | Upload media/blob (`Content-Type` header optional) |
| `GET` | `/media/{key}` | Stream uploaded object |
| `GET` | `/downloads/{path}` | Download APK or binary (`Content-Disposition: attachment`) |

### Upload example

```bash
curl -X PUT "https://truckmgmt-media-proxy.example.workers.dev/upload/fleets/demo/proof.jpg" \
  -H "Content-Type: image/jpeg" \
  --data-binary @proof.jpg
```

### Public media URL

```
https://truckmgmt-media-proxy.example.workers.dev/media/fleets/demo/proof.jpg
```

### APK download path

Upload via `PUT /upload/downloads/dispatcher.apk`, then link:

```
https://truckmgmt-media-proxy.example.workers.dev/downloads/dispatcher.apk
```

## Clients in this repo

| Client | Config | Usage |
|--------|--------|-------|
| `shared/TruckMgmtConstants.kt` | `R2_MEDIA_PROXY_BASE_URL` | Driver edge heartbeat sync |
| `dispatcher-web/.env` | `VITE_R2_BASE_URL` | Health checks, future media uploads |
| `marketing` TCD | `VITE_R2_BASE_URL` | Architecture health probe |
