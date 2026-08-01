# TruckMgmt

Fleet operations product (Phase 1): dispatcher, driver (company device), and customer apps —
modeled on the SareChild monorepo pattern.

## Modules

| Module | ID / host | Role |
|--------|-----------|------|
| `dispatcher/` | `com.truckmgmt.dispatcher` | Dispatcher Android (drawer + satellite live map) |
| `driver/` | `com.truckmgmt.driver` | Company driver device — pairing, FGS monitoring, jobs |
| `customer/` | `com.truckmgmt.customer` | Schedule, track truck after accept, enter payment |
| `shared/` | `com.truckmgmt.shared` | Constants, geo helpers, models |
| `dispatcher-web/` | Optional Firebase Hosting | Dispatcher dashboard (sidebar menus) |
| `marketing/` | **GitHub Pages** | Landing + TCD architecture console |
| `functions/` | Cloud Functions | FCM, nearest-driver, payment rollups, trail purge |
| `r2-proxy/` | Cloudflare Worker | **Primary** media/APK storage, edge fleet cache, health |

## Storage

| Layer | Role | Status |
|-------|------|--------|
| **Cloudflare R2** (`truckmgmt-uploads` via `r2-proxy/`) | Media blobs, APKs, edge uploads | **Canonical** — use Worker `/upload/`, `/media/`, `/downloads/` |
| Firebase Storage | Legacy optional | Soft-deprecated; `storage.rules` kept but **not required** for first run |

See [`r2-proxy/README.md`](r2-proxy/README.md) for Worker endpoints and setup.

## Hosting

| Site | Host | Deploy |
|------|------|--------|
| Marketing + TCD | **GitHub Pages** (`/<repo>/`) | Push to `main` → `.github/workflows/deploy-marketing.yml` |
| Dispatcher web SPA | Optional Firebase Hosting | `firebase deploy --only hosting` or `.github/workflows/deploy-dispatcher-web.yml` (needs `FIREBASE_SERVICE_ACCOUNT` secret) |

Enable GitHub Pages: repo **Settings → Pages → Build and deployment → GitHub Actions**.

## First run

Firebase project **`truckmgmt-dev`** is configured in `.firebaserc`. After cloning:

```bat
scripts\setup-firebase.bat
scripts\setup-cloudflare.bat
```

`setup-firebase.bat` links the project, deploys Firestore rules/indexes, fetches `google-services.json` into each Android module, and writes `dispatcher-web/.env`. It does **not** block on Firebase Storage.

### One-time console steps

| Step | URL / action |
|------|----------------|
| Auth: Email/Password + Google + Anonymous | [Authentication providers](https://console.firebase.google.com/project/truckmgmt-dev/authentication/providers) — already enabled on Spark |
| Blaze plan (for Cloud Functions only) | [Usage and billing](https://console.firebase.google.com/project/truckmgmt-dev/usage/details) |
| GitHub Pages | Repo **Settings → Pages → GitHub Actions** |
| Cloudflare R2/D1/KV + Worker | `scripts\setup-cloudflare.bat` (or see `r2-proxy/README.md`) |

**Optional later:** Firebase Storage — [Storage console](https://console.firebase.google.com/project/truckmgmt-dev/storage) → Get Started → `firebase deploy --only storage`. Not needed for R2-backed media.

### Local secrets (gitignored)

| File | Source |
|------|--------|
| `dispatcher/google-services.json` | `firebase apps:sdkconfig` or `scripts/fetch-firebase-config.ps1` |
| `driver/google-services.json` | same |
| `customer/google-services.json` | same |
| `dispatcher-web/.env` | copy from `.env.example`; script fills Firebase keys + `VITE_R2_BASE_URL` |
| `local.properties` | `MAPS_API_KEY=` for Android Maps SDK |

Example templates: `*/google-services.json.example`, `dispatcher-web/.env.example`.

### Registered Firebase apps

| App | Package / type | App ID |
|-----|----------------|--------|
| Dispatcher | `com.truckmgmt.dispatcher` | `1:779631101524:android:66bc491e7bcff919184fc1` |
| Driver | `com.truckmgmt.driver` | `1:779631101524:android:136d8c41a7d0469d184fc1` |
| Customer | `com.truckmgmt.customer` | `1:779631101524:android:b0a4133c2683de83184fc1` |
| Dispatcher Web | Web | `1:779631101524:web:5d87699af9c4c41a184fc1` |

**Already deployed:** Firestore `(default)` database, `firestore.rules`, `firestore.indexes.json`.

**Still manual:** Cloud Functions (Blaze), Google Maps key restrictions, GitHub Pages enablement, optional Firebase Hosting for dispatcher-web.

## Quick start

1. Run `scripts\setup-firebase.bat` and `scripts\setup-cloudflare.bat`.
2. Enable GitHub Pages (GitHub Actions source) in repo settings.
3. Set `MAPS_API_KEY` in `local.properties` (see `gradle.properties.example`).
4. Copy/fill `dispatcher-web/.env` — Maps key: `VITE_GOOGLE_MAPS_API_KEY`; R2: `VITE_R2_BASE_URL` (Worker URL).
5. Android Studio: open this folder, sync Gradle, run apps.
6. Web: `cd dispatcher-web && npm i && npm run dev`
7. Functions: `cd functions && npm i && npm run build && firebase deploy --only functions`
8. Marketing preview: `cd marketing && npm i && GITHUB_PAGES=true npm run build`

## Delivery flow

Customer schedules → `deliveryRequests` (dispatcher_review) → dispatcher assigns **or** scheduled Function `assignNearestDriverScheduled` after timeout → driver accepts → customer sees truck on satellite map → arrived / delivered → customer enters amount → driver accepts payment → dispatcher sees payment + trip totals / playback.

## Maps

All maps default to **satellite** (`MAP_TYPE_SATELLITE` / `MapTypeId.SATELLITE`).

## Driver soft lockdown (Phase 1)

One-time permissions hub, foreground service for location/heartbeats/commands, overt monitoring label, remote lock/ring overlays. Device Owner / Lock Task deferred.
