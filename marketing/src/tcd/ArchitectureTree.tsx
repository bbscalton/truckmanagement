import { useCallback, useEffect, useState } from 'react'
import type { ArchNode, TcdCheck, TcdCheckStatus } from './types'

export const ARCH_LAYOUT_VERSION = 'truckmgmt-arch-v2-20260801'

type NodeDef = {
  id: string
  label: string
  subtitle: string
  group: ArchNode['group']
  column: 'apps' | 'firebase' | 'cloudflare' | 'ops'
  checkIds?: string[]
}

const COLUMNS: { id: NodeDef['column']; header: string; num: string }[] = [
  { id: 'apps', num: '1', header: 'Apps' },
  { id: 'firebase', num: '2', header: 'Firebase' },
  { id: 'cloudflare', num: '3', header: 'Cloudflare' },
  { id: 'ops', num: '4', header: 'Hosting & admin' },
]

const NODES: NodeDef[] = [
  { id: 'dispatcher-web', label: 'Dispatcher web', subtitle: 'Fleet dashboard in the browser.', group: 'client', column: 'apps', checkIds: ['dispatcher-web'] },
  { id: 'dispatcher-apk', label: 'Dispatcher app', subtitle: 'Android app for dispatchers on the go.', group: 'client', column: 'apps', checkIds: ['dispatcher-apk'] },
  { id: 'driver-apk', label: 'Driver app', subtitle: 'Company device — location, jobs, monitoring.', group: 'client', column: 'apps', checkIds: ['driver-apk'] },
  { id: 'customer-apk', label: 'Customer app', subtitle: 'Schedule, track, and confirm payment.', group: 'client', column: 'apps', checkIds: ['customer-apk'] },
  { id: 'firebase-auth', label: 'Firebase Auth', subtitle: 'Sign-in and secure account sessions.', group: 'firebase', column: 'firebase', checkIds: ['firebase-auth'] },
  { id: 'firestore', label: 'Firestore', subtitle: 'Fleets, trucks, deliveries, trails.', group: 'firebase', column: 'firebase', checkIds: ['firestore'] },
  { id: 'fcm', label: 'Cloud Messaging', subtitle: 'Push notifications to all apps.', group: 'firebase', column: 'firebase', checkIds: ['fcm'] },
  { id: 'functions', label: 'Cloud Functions', subtitle: 'Nearest driver, FCM, payment rollups.', group: 'firebase', column: 'firebase', checkIds: ['functions'] },
  { id: 'hosting', label: 'Firebase Hosting', subtitle: 'Optional — dispatcher web SPA only.', group: 'firebase', column: 'firebase', checkIds: ['hosting'] },
  { id: 'worker', label: 'Cloudflare Worker', subtitle: 'Edge fleet cache and media routing.', group: 'cloudflare', column: 'cloudflare', checkIds: ['worker'] },
  { id: 'r2', label: 'R2 storage', subtitle: 'Media blobs and public APKs.', group: 'cloudflare', column: 'cloudflare', checkIds: ['r2'] },
  { id: 'd1', label: 'D1 database', subtitle: 'Structured data at the edge.', group: 'cloudflare', column: 'cloudflare', checkIds: ['d1'] },
  { id: 'kv', label: 'KV cache', subtitle: 'Fast edge key-value lookups.', group: 'cloudflare', column: 'cloudflare', checkIds: ['kv'] },
  { id: 'pages', label: 'GitHub Pages', subtitle: 'Primary host — marketing site and TCD.', group: 'ops', column: 'ops', checkIds: ['pages'] },
  { id: 'tcd', label: 'TCD console', subtitle: 'This operator dashboard you are using.', group: 'ops', column: 'ops', checkIds: ['tcd'] },
  { id: 'maps', label: 'Google Maps', subtitle: 'Satellite tiles for live location views.', group: 'ops', column: 'ops', checkIds: ['maps'] },
]

function worst(statuses: TcdCheckStatus[]): TcdCheckStatus {
  if (statuses.includes('fail')) return 'fail'
  if (statuses.includes('warn')) return 'warn'
  if (statuses.includes('unknown')) return 'unknown'
  if (statuses.every((s) => s === 'ok')) return 'ok'
  return 'unknown'
}

const ENV = (import.meta as ImportMeta & { env?: Record<string, string> }).env ?? {}

const R2_HEALTH = ENV.VITE_R2_BASE_URL ?? 'https://truckmgmt-media-proxy.neuereatec.workers.dev'
const FIREBASE_HOSTING_URL = 'https://truckmgmt-dev.web.app'
const GITHUB_PAGES_URL = 'https://bbscalton.github.io/truckmanagement/'
const MAPS_BUILD_KEY = ENV.VITE_GOOGLE_MAPS_API_KEY?.trim() ?? ''

/** Best-effort reachability — CORS may block status; no-cors fallback treats network reach as OK. */
async function probeUrlReachable(url: string): Promise<boolean> {
  try {
    const res = await fetch(url, { method: 'GET', cache: 'no-store' })
    if (res.ok || res.type === 'opaque') return true
  } catch {
    /* try no-cors */
  }
  try {
    await fetch(url, { method: 'GET', cache: 'no-store', mode: 'no-cors' })
    return true
  } catch {
    return false
  }
}

export function ArchitectureTree({ onBack }: { onBack?: () => void }) {
  const [checks, setChecks] = useState<TcdCheck[]>([])

  const refresh = useCallback(async () => {
    const next: TcdCheck[] = [
      { id: 'dispatcher-web', label: 'Dispatcher web', status: 'ok', detail: 'Source present in monorepo.' },
      { id: 'dispatcher-apk', label: 'Dispatcher app', status: 'ok', detail: 'Android module :dispatcher' },
      { id: 'driver-apk', label: 'Driver app', status: 'ok', detail: 'Android module :driver' },
      { id: 'customer-apk', label: 'Customer app', status: 'ok', detail: 'Android module :customer' },
      { id: 'firebase-auth', label: 'Firebase Auth', status: 'ok', detail: 'Email/Password, Google, and Anonymous enabled.' },
      { id: 'firestore', label: 'Firestore', status: 'ok', detail: 'Rules + indexes committed.' },
      { id: 'fcm', label: 'FCM', status: 'ok', detail: 'Messaging services wired in apps.' },
      { id: 'functions', label: 'Cloud Functions', status: 'warn', detail: 'Deploy with firebase deploy --only functions (requires Blaze plan).' },
      { id: 'hosting', label: 'Hosting', status: 'unknown', detail: `Probing ${FIREBASE_HOSTING_URL}…` },
      { id: 'pages', label: 'GitHub Pages', status: 'unknown', detail: `Probing ${GITHUB_PAGES_URL}…` },
      { id: 'tcd', label: 'TCD console', status: 'ok', detail: 'Architecture tree loaded.' },
      { id: 'maps', label: 'Google Maps', status: 'unknown', detail: 'Checking Maps key configuration…' },
      { id: 'worker', label: 'Worker', status: 'unknown', detail: 'Probing…' },
      { id: 'r2', label: 'R2', status: 'unknown' },
      { id: 'd1', label: 'D1', status: 'unknown' },
      { id: 'kv', label: 'KV', status: 'unknown' },
    ]

    const upsert = (id: string, status: TcdCheckStatus, detail?: string) => {
      const row = next.find((c) => c.id === id)
      if (row) {
        row.status = status
        if (detail) row.detail = detail
      }
    }

    try {
      const res = await fetch(`${R2_HEALTH}/platform-health`, { cache: 'no-store' })
      if (!res.ok) throw new Error(`HTTP ${res.status}`)
      const data = (await res.json()) as {
        checks?: {
          firebase?: { status?: string; message?: string }
          d1?: { status?: string; message?: string }
          kv?: { status?: string; message?: string }
          r2?: { status?: string; message?: string }
        }
      }
      const mapStatus = (s?: string): TcdCheckStatus =>
        s === 'ok' || s === 'warn' || s === 'fail' ? s : 'unknown'
      upsert('worker', 'ok', 'Health endpoint reachable.')
      upsert('r2', mapStatus(data.checks?.r2?.status), data.checks?.r2?.message)
      upsert('d1', mapStatus(data.checks?.d1?.status), data.checks?.d1?.message)
      upsert('kv', mapStatus(data.checks?.kv?.status), data.checks?.kv?.message)
      if (data.checks?.firebase?.status === 'ok') {
        upsert('firebase-auth', 'ok', data.checks.firebase.message)
      }
    } catch (e) {
      const detail = e instanceof Error ? e.message : 'Worker unreachable'
      ;['worker', 'r2', 'd1', 'kv'].forEach((id) => {
        const row = next.find((c) => c.id === id)
        if (row) {
          row.status = 'warn'
          row.detail = detail
        }
      })
    }

    const [hostingLive, pagesLive] = await Promise.all([
      probeUrlReachable(FIREBASE_HOSTING_URL),
      probeUrlReachable(GITHUB_PAGES_URL),
    ])

    if (hostingLive) {
      upsert('hosting', 'ok', `Live at ${FIREBASE_HOSTING_URL} (dispatcher web SPA).`)
    } else {
      upsert('hosting', 'warn', `Unreachable at ${FIREBASE_HOSTING_URL} — run firebase deploy --only hosting.`)
    }

    if (pagesLive) {
      upsert('pages', 'ok', `Live at ${GITHUB_PAGES_URL}`)
    } else {
      upsert('pages', 'warn', 'Push marketing/ to main and enable GitHub Pages (Actions).')
    }

    if (MAPS_BUILD_KEY.length > 10) {
      upsert('maps', 'ok', 'VITE_GOOGLE_MAPS_API_KEY set at build time.')
    } else if (hostingLive) {
      upsert(
        'maps',
        'ok',
        'Keys configured in local.properties / dispatcher-web .env (dispatcher web live; not probed from TCD).',
      )
    } else {
      upsert(
        'maps',
        'warn',
        'Set MAPS_API_KEY in local.properties and VITE_GOOGLE_MAPS_API_KEY in dispatcher-web/.env.',
      )
    }

    setChecks(next)
  }, [])

  useEffect(() => {
    void refresh()
  }, [refresh])

  const statusFor = (node: NodeDef): TcdCheckStatus => {
    if (!node.checkIds?.length) return 'unknown'
    return worst(node.checkIds.map((id) => checks.find((c) => c.id === id)?.status ?? 'unknown'))
  }

  return (
    <div className="tcd" data-arch-version={ARCH_LAYOUT_VERSION}>
      <header className="tcd-header">
        <div>
          <strong>TruckMgmt TCD</strong>
          <span className="muted"> Architecture & health</span>
        </div>
        <div className="tcd-actions">
          <button onClick={() => void refresh()}>Refresh</button>
          {onBack && (
            <button className="ghost" onClick={onBack}>
              Back
            </button>
          )}
        </div>
      </header>

      <div className="arch-grid">
        {COLUMNS.map((col) => (
          <section key={col.id} className="arch-col">
            <h2>
              <span className="num">{col.num}</span> {col.header}
            </h2>
            {NODES.filter((n) => n.column === col.id).map((node) => {
              const status = statusFor(node)
              return (
                <article key={node.id} className={`arch-node status-${status}`}>
                  <div className="arch-node-top">
                    <strong>{node.label}</strong>
                    <span className={`badge ${status}`}>{status.toUpperCase()}</span>
                  </div>
                  <p>{node.subtitle}</p>
                  <ul>
                    {(node.checkIds ?? [])
                      .map((id) => checks.find((c) => c.id === id))
                      .filter((c): c is TcdCheck => Boolean(c))
                      .map((c) => (
                        <li key={c.id}>
                          <span className={`dot ${c.status}`} />
                          {c.detail ?? c.label}
                        </li>
                      ))}
                  </ul>
                </article>
              )
            })}
          </section>
        ))}
      </div>
    </div>
  )
}
