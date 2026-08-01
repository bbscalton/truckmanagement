import { useEffect, useMemo, useRef, useState } from 'react'
import {
  addDoc,
  collection,
  doc,
  getDoc,
  getDocs,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  setDoc,
  updateDoc,
  where,
  limit,
  type Unsubscribe,
} from 'firebase/firestore'
import { signOut } from 'firebase/auth'
import { useAuth } from '../AuthContext'
import { auth, COL, db } from '../firebase'
import { initFleetMap, type FleetMapHandle, type LatLng } from '../lib/fleetMap'
import { IconCopy, IconLogout, IconMenu, NavIcon, type NavSection } from '../components/icons'

export type Section = NavSection

const NAV: { group: string; items: { id: Section; label: string }[] }[] = [
  {
    group: 'Fleet',
    items: [
      { id: 'live_map', label: 'Live map' },
      { id: 'trucks', label: 'Trucks' },
      { id: 'drivers', label: 'Drivers' },
      { id: 'pair', label: 'Pair device' },
    ],
  },
  {
    group: 'Operations',
    items: [
      { id: 'requests', label: 'Requests' },
      { id: 'deliveries', label: 'Active deliveries' },
      { id: 'playback', label: 'Trip playback' },
      { id: 'stops', label: 'Stops' },
    ],
  },
  {
    group: 'Money',
    items: [
      { id: 'payments', label: 'Payments' },
      { id: 'totals', label: 'Trip totals' },
    ],
  },
  {
    group: 'Comms',
    items: [
      { id: 'chat', label: 'Fleet chat' },
      { id: 'activity', label: 'Monitored activity' },
    ],
  },
  {
    group: 'Account',
    items: [{ id: 'settings', label: 'Settings' }],
  },
]

type ViewState =
  | { kind: 'loading' }
  | { kind: 'empty'; title: string; hint: string }
  | { kind: 'summary'; text: string }
  | {
      kind: 'table'
      columns: { key: string; label: string; mono?: boolean }[]
      rows: Record<string, string>[]
    }
  | { kind: 'stats'; trips: number; revenue: number }
  | {
      kind: 'chat'
      messages: { role: string; text: string }[]
    }
  | { kind: 'settings'; fleetId: string; email: string; name: string }
  | { kind: 'pair' }

function statusChip(status: string) {
  const s = status.toLowerCase()
  let cls = 'chip-neutral'
  if (['online', 'active', 'assigned', 'completed', 'accepted'].some((k) => s.includes(k))) cls = 'chip-success'
  else if (['idle', 'requested', 'pending', 'review'].some((k) => s.includes(k))) cls = 'chip-warning'
  else if (['offline', 'cancelled', 'failed'].some((k) => s.includes(k))) cls = 'chip-danger'
  else if (['auto_nearest', 'dispatcher_review', 'in_transit'].some((k) => s.includes(k))) cls = 'chip-info'
  return <span className={`chip ${cls}`}>{status}</span>
}

function EmptyBlock({ title, hint }: { title: string; hint: string }) {
  return (
    <div className="empty-state">
      <svg className="empty-state-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5">
        <rect x="3" y="3" width="18" height="18" rx="3" />
        <path d="M8 12h8" />
      </svg>
      <h3>{title}</h3>
      <p>{hint}</p>
    </div>
  )
}

export function DashboardPage() {
  const { user, fleetId, profile } = useAuth()
  const [section, setSection] = useState<Section>('live_map')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [view, setView] = useState<ViewState>({ kind: 'loading' })
  const [statusLine, setStatusLine] = useState('Connecting to fleet…')
  const [chatInput, setChatInput] = useState('')
  const [pairName, setPairName] = useState('Driver')
  const [pairCode, setPairCode] = useState('')
  const [truckLabel, setTruckLabel] = useState('')
  const [mapError, setMapError] = useState<string | null>(null)
  const [mapReady, setMapReady] = useState(0)
  const [copied, setCopied] = useState(false)
  const mapRef = useRef<HTMLDivElement>(null)
  const mapObj = useRef<FleetMapHandle | null>(null)

  const showMap = section === 'live_map' || section === 'playback' || section === 'stops'
  const mapPrimary = section === 'live_map'

  const title = useMemo(() => {
    for (const g of NAV) {
      const hit = g.items.find((i) => i.id === section)
      if (hit) return hit.label
    }
    return 'Dashboard'
  }, [section])

  useEffect(() => {
    if (!showMap || !mapRef.current) return
    setMapError(null)
    try {
      mapObj.current?.destroy()
      mapObj.current = initFleetMap(mapRef.current)
      requestAnimationFrame(() => {
        mapObj.current?.map.invalidateSize()
        setMapReady((n) => n + 1)
      })
    } catch (e) {
      setMapError(e instanceof Error ? e.message : 'Map failed to load.')
    }
    return () => {
      mapObj.current?.destroy()
      mapObj.current = null
    }
  }, [showMap])

  useEffect(() => {
    if (!fleetId) {
      setView({ kind: 'empty', title: 'No fleet linked', hint: 'Contact support or register a new fleet account.' })
      setStatusLine('No fleet assigned to this account')
      return
    }
    let unsub: Unsubscribe | undefined

    const clearOverlays = () => {
      mapObj.current?.clearLayers()
    }

    const run = async () => {
      setView({ kind: 'loading' })

      if (section === 'live_map') {
        unsub = onSnapshot(collection(db, COL.fleets, fleetId, COL.drivers), (snap) => {
          const fleetMap = mapObj.current
          const online = snap.docs.filter((d) => d.get('online')).length
          setStatusLine(`${online} of ${snap.size} drivers online · Satellite live tracking`)
          setView({ kind: 'summary', text: 'All fleet drivers shown on satellite map. Green markers = online.' })

          if (!fleetMap) return
          const drivers = snap.docs.flatMap((d) => {
            const lat = d.get('lastLat') as number | undefined
            const lng = d.get('lastLng') as number | undefined
            if (lat == null || lng == null) return []
            return [
              {
                lat,
                lng,
                title: (d.get('displayName') as string) || 'Driver',
                online: Boolean(d.get('online')),
              },
            ]
          })
          fleetMap.setMarkers(drivers)
          const first = snap.docs.find((d) => d.get('lastLat') != null)
          if (first) {
            fleetMap.setCenter(first.get('lastLat') as number, first.get('lastLng') as number)
          }
        })
        return
      }

      if (section === 'trucks') {
        const snap = await getDocs(collection(db, COL.fleets, fleetId, COL.trucks))
        setStatusLine(`${snap.size} truck${snap.size === 1 ? '' : 's'} registered`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No trucks yet', hint: 'Add a truck from Pair device → truck label field.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'label', label: 'Label' },
            { key: 'plate', label: 'Plate' },
            { key: 'status', label: 'Status' },
          ],
          rows: snap.docs.map((d) => ({
            label: String(d.get('label') ?? '—'),
            plate: String(d.get('plate') ?? '—'),
            status: String(d.get('status') ?? 'unknown'),
          })),
        })
        return
      }

      if (section === 'drivers') {
        const snap = await getDocs(collection(db, COL.fleets, fleetId, COL.drivers))
        const online = snap.docs.filter((d) => d.get('online')).length
        setStatusLine(`${online} online · ${snap.size} total drivers`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No drivers paired', hint: 'Create a pairing code under Pair device.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'name', label: 'Driver' },
            { key: 'status', label: 'Status' },
            { key: 'heartbeat', label: 'Last heartbeat', mono: true },
          ],
          rows: snap.docs.map((d) => ({
            name: String(d.get('displayName') ?? 'Driver'),
            status: d.get('online') ? 'online' : 'offline',
            heartbeat: String(d.get('lastHeartbeatAt') ?? '—'),
          })),
        })
        return
      }

      if (section === 'requests') {
        const snap = await getDocs(
          query(
            collection(db, COL.fleets, fleetId, COL.deliveryRequests),
            where('status', 'in', ['requested', 'dispatcher_review', 'auto_nearest']),
          ),
        )
        setStatusLine(`${snap.size} open request${snap.size === 1 ? '' : 's'}`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No open requests', hint: 'Customer delivery requests will appear here.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'id', label: 'ID', mono: true },
            { key: 'route', label: 'Route' },
            { key: 'status', label: 'Status' },
          ],
          rows: snap.docs.map((d) => ({
            id: d.id.slice(0, 8),
            route: `${d.get('pickupAddress')} → ${d.get('dropoffAddress')}`,
            status: String(d.get('status') ?? '—'),
          })),
        })
        return
      }

      if (section === 'deliveries') {
        const snap = await getDocs(collection(db, COL.fleets, fleetId, COL.deliveries))
        setStatusLine(`${snap.size} active deliver${snap.size === 1 ? 'y' : 'ies'}`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No deliveries', hint: 'Assign a request to start a delivery.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'id', label: 'ID', mono: true },
            { key: 'status', label: 'Status' },
            { key: 'driver', label: 'Driver', mono: true },
          ],
          rows: snap.docs.map((d) => ({
            id: d.id.slice(0, 8),
            status: String(d.get('status') ?? '—'),
            driver: String(d.get('assignedDriverId') ?? '—').slice(0, 8),
          })),
        })
        return
      }

      if (section === 'playback') {
        const trips = await getDocs(query(collection(db, COL.fleets, fleetId, COL.trips), limit(10)))
        setStatusLine(`${trips.size} recent trip${trips.size === 1 ? '' : 's'}`)
        if (trips.empty) {
          setView({ kind: 'empty', title: 'No completed trips', hint: 'Trip routes will appear here after deliveries finish.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'id', label: 'Trip', mono: true },
            { key: 'cost', label: 'Cost' },
            { key: 'points', label: 'GPS points' },
          ],
          rows: trips.docs.map((d) => ({
            id: d.id.slice(0, 8),
            cost: `$${d.get('cost') ?? 0}`,
            points: String(d.get('pointCount') ?? 0),
          })),
        })
        const deliveryId = trips.docs[0].get('deliveryId') as string | undefined
        if (!deliveryId || !mapObj.current) return
        clearOverlays()
        const trail = await getDocs(
          query(
            collection(db, COL.fleets, fleetId, COL.locationTrail),
            where('deliveryId', '==', deliveryId),
            orderBy('ts', 'asc'),
          ),
        )
        const path = trail.docs
          .map((d) => {
            const lat = d.get('lat') as number | undefined
            const lng = d.get('lng') as number | undefined
            return lat != null && lng != null ? { lat, lng } : null
          })
          .filter(Boolean) as LatLng[]
        if (path.length) {
          const fleetMap = mapObj.current
          fleetMap.drawPolyline(path)
          const last = path[path.length - 1]
          fleetMap.setCenter(last.lat, last.lng, 14)
        }
        return
      }

      if (section === 'stops') {
        unsub = onSnapshot(query(collection(db, COL.fleets, fleetId, COL.stops), limit(50)), (snap) => {
          setStatusLine(`${snap.size} recent stop${snap.size === 1 ? '' : 's'} on map`)
          if (snap.empty) {
            setView({ kind: 'empty', title: 'No stops recorded', hint: 'Driver stops will appear on the map and in this list.' })
          } else {
            setView({
              kind: 'table',
              columns: [
                { key: 'lat', label: 'Latitude', mono: true },
                { key: 'lng', label: 'Longitude', mono: true },
              ],
              rows: snap.docs.map((d) => ({
                lat: String(d.get('lat') ?? '—'),
                lng: String(d.get('lng') ?? '—'),
              })),
            })
          }
          const fleetMap = mapObj.current
          if (!fleetMap) return
          const stops = snap.docs.flatMap((d) => {
            const lat = d.get('lat') as number | undefined
            const lng = d.get('lng') as number | undefined
            if (lat == null || lng == null) return []
            return [{ lat, lng }]
          })
          fleetMap.setStopMarkers(stops)
        })
        return
      }

      if (section === 'payments') {
        const snap = await getDocs(
          query(collection(db, COL.fleets, fleetId, COL.payments), where('visibleToDispatcher', '==', true)),
        )
        setStatusLine(`${snap.size} payment${snap.size === 1 ? '' : 's'} recorded`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No payments yet', hint: 'Accepted customer payments will show here.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'amount', label: 'Amount' },
            { key: 'delivery', label: 'Delivery', mono: true },
          ],
          rows: snap.docs.map((d) => ({
            amount: `$${d.get('amount') ?? 0}`,
            delivery: String(d.get('deliveryId') ?? '—').slice(0, 8),
          })),
        })
        return
      }

      if (section === 'totals') {
        const fleet = await getDoc(doc(db, COL.fleets, fleetId))
        const trips = Number(fleet.get('tripCount') ?? 0)
        const revenue = Number(fleet.get('totalRevenue') ?? 0)
        setStatusLine(`Fleet lifetime: ${trips} trips · $${revenue.toLocaleString()} revenue`)
        setView({ kind: 'stats', trips, revenue })
        return
      }

      if (section === 'chat') {
        setStatusLine('Fleet-wide messaging')
        unsub = onSnapshot(
          query(collection(db, COL.fleets, fleetId, COL.fleetChat), orderBy('createdAt', 'asc'), limit(100)),
          (snap) => {
            setView({
              kind: 'chat',
              messages: snap.docs.map((d) => ({
                role: String(d.get('senderRole') ?? 'unknown'),
                text: String(d.get('text') ?? ''),
              })),
            })
          },
        )
        return
      }

      if (section === 'activity') {
        const snap = await getDocs(query(collection(db, COL.fleets, fleetId, COL.activityLogs), limit(40)))
        setStatusLine(`${snap.size} recent event${snap.size === 1 ? '' : 's'}`)
        if (snap.empty) {
          setView({ kind: 'empty', title: 'No activity yet', hint: 'Fleet events and alerts will be logged here.' })
          return
        }
        setView({
          kind: 'table',
          columns: [
            { key: 'type', label: 'Type' },
            { key: 'summary', label: 'Summary' },
          ],
          rows: snap.docs.map((d) => ({
            type: String(d.get('type') ?? '—'),
            summary: String(d.get('summary') ?? '—'),
          })),
        })
        return
      }

      if (section === 'pair') {
        setStatusLine('Device pairing & fleet registration')
        setView({ kind: 'pair' })
        return
      }

      if (section === 'settings') {
        setStatusLine(`Fleet ${fleetId}`)
        setView({
          kind: 'settings',
          fleetId,
          email: user?.email ?? '—',
          name: profile?.displayName ?? '—',
        })
        return
      }
    }

    void run()
    return () => unsub?.()
  }, [section, fleetId, user, profile, mapReady])

  const createPairCode = async () => {
    if (!fleetId) return
    const code = String(Math.floor(100000 + Math.random() * 900000))
    await setDoc(doc(db, COL.pairingCodes, code), {
      fleetId,
      driverName: pairName || 'Driver',
      createdBy: user?.uid,
      createdAt: serverTimestamp(),
      expiresAt: Date.now() + 30 * 60_000,
      used: false,
    })
    setPairCode(code)
  }

  const addTruck = async () => {
    if (!fleetId || !truckLabel.trim()) return
    const id = crypto.randomUUID().slice(0, 8)
    await setDoc(doc(db, COL.fleets, fleetId, COL.trucks, id), {
      label: truckLabel.trim(),
      plate: truckLabel.trim(),
      status: 'idle',
      createdAt: serverTimestamp(),
    })
    setTruckLabel('')
    setSection('trucks')
  }

  const assignFirstRequest = async () => {
    if (!fleetId) return
    const reqs = await getDocs(
      query(
        collection(db, COL.fleets, fleetId, COL.deliveryRequests),
        where('status', 'in', ['dispatcher_review', 'auto_nearest', 'requested']),
      ),
    )
    const req = reqs.docs[0]
    if (!req) {
      setView({ kind: 'empty', title: 'Nothing to assign', hint: 'No open requests waiting for a driver.' })
      return
    }
    const drivers = await getDocs(query(collection(db, COL.fleets, fleetId, COL.drivers), where('online', '==', true)))
    const driver = drivers.docs[0]
    if (!driver) {
      setView({ kind: 'empty', title: 'No online drivers', hint: 'Nearest-driver function will retry after timeout.' })
      return
    }
    const data = { ...req.data(), status: 'assigned', assignedDriverId: driver.id, requestId: req.id, updatedAt: serverTimestamp() }
    const deliveryRef = doc(collection(db, COL.fleets, fleetId, COL.deliveries))
    await setDoc(deliveryRef, data)
    await updateDoc(req.ref, { status: 'assigned', deliveryId: deliveryRef.id, assignedDriverId: driver.id })
    setSection('deliveries')
  }

  const copyFleetId = async () => {
    if (!fleetId) return
    await navigator.clipboard.writeText(fleetId)
    setCopied(true)
    window.setTimeout(() => setCopied(false), 2000)
  }

  const sendChat = async () => {
    if (!fleetId || !chatInput.trim()) return
    await addDoc(collection(db, COL.fleets, fleetId, COL.fleetChat), {
      text: chatInput.trim(),
      senderUid: user?.uid,
      senderRole: 'dispatcher',
      createdAt: serverTimestamp(),
    })
    setChatInput('')
  }

  const renderContent = () => {
    if (view.kind === 'loading') {
      return <div className="loading-shell">Loading…</div>
    }
    if (view.kind === 'empty') {
      return (
        <div className="panel-card">
          <EmptyBlock title={view.title} hint={view.hint} />
        </div>
      )
    }
    if (view.kind === 'summary') {
      return null
    }
    if (view.kind === 'stats') {
      return (
        <div className="stat-grid">
          <div className="stat-card">
            <div className="label">Total trips</div>
            <div className="value">{view.trips.toLocaleString()}</div>
          </div>
          <div className="stat-card">
            <div className="label">Total revenue</div>
            <div className="value">${view.revenue.toLocaleString()}</div>
          </div>
        </div>
      )
    }
    if (view.kind === 'chat') {
      return (
        <div className="panel-card chat-panel">
          <div className="chat-messages">
            {view.messages.length === 0 ? (
              <EmptyBlock title="No messages yet" hint="Send the first message to your fleet." />
            ) : (
              view.messages.map((m, i) => (
                <div key={i} className={`chat-bubble ${m.role}`}>
                  <div className="role">{m.role}</div>
                  {m.text}
                </div>
              ))
            )}
          </div>
          <div className="chat-compose">
            <input
              value={chatInput}
              onChange={(e) => setChatInput(e.target.value)}
              placeholder="Message drivers…"
              onKeyDown={(e) => e.key === 'Enter' && void sendChat()}
            />
            <button className="btn-primary" onClick={() => void sendChat()}>
              Send
            </button>
          </div>
        </div>
      )
    }
    if (view.kind === 'settings') {
      return (
        <div className="panel-card">
          <div className="settings-grid">
            <div className="settings-row">
              <span className="key">Fleet ID</span>
              <span className="val mono">{view.fleetId}</span>
            </div>
            <div className="settings-row">
              <span className="key">Email</span>
              <span className="val">{view.email}</span>
            </div>
            <div className="settings-row">
              <span className="key">Display name</span>
              <span className="val">{view.name}</span>
            </div>
            <p className="muted small" style={{ margin: '8px 0 0' }}>
              Share fleet ID with customers (Customer app → Register or Profile).
            </p>
            <button className="btn-secondary" onClick={() => void copyFleetId()} style={{ width: 'fit-content' }}>
              <span style={{ display: 'inline-flex', alignItems: 'center', gap: 8 }}>
                <IconCopy className="nav-icon" />
                {copied ? 'Copied!' : 'Copy fleet ID'}
              </span>
            </button>
          </div>
        </div>
      )
    }
    if (view.kind === 'pair') {
      return (
        <div className="panel-card" style={{ padding: 20 }}>
          <h3 style={{ marginBottom: 16, color: 'var(--text-inverse)' }}>Pair driver device</h3>
          <div className="actions">
            <input value={pairName} onChange={(e) => setPairName(e.target.value)} placeholder="Driver name" />
            <button className="btn-primary" onClick={() => void createPairCode()}>
              Create pairing code
            </button>
            {pairCode && <p className="pair-code">{pairCode}</p>}
          </div>
          <h3 style={{ margin: '28px 0 16px', color: 'var(--text-inverse)' }}>Add truck</h3>
          <div className="actions">
            <input value={truckLabel} onChange={(e) => setTruckLabel(e.target.value)} placeholder="Truck plate / label" />
            <button className="btn-secondary" onClick={() => void addTruck()}>
              Add truck
            </button>
          </div>
        </div>
      )
    }
    if (view.kind === 'table') {
      return (
        <div className="panel-card">
          <table className="data-table">
            <thead>
              <tr>
                {view.columns.map((c) => (
                  <th key={c.key}>{c.label}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {view.rows.map((row, i) => (
                <tr key={i}>
                  {view.columns.map((c) => (
                    <td key={c.key} className={c.mono ? 'mono' : undefined}>
                      {c.key === 'status' ? statusChip(row[c.key]) : row[c.key]}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )
    }
    return null
  }

  return (
    <div className="dash">
      {sidebarOpen && <div className="sidebar-scrim" onClick={() => setSidebarOpen(false)} aria-hidden />}
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <div className="sidebar-brand-mark">T</div>
          <div className="sidebar-brand-text">
            <strong>TruckMgmt</strong>
            <span className="muted small">Operations</span>
          </div>
        </div>
        {fleetId && (
          <div className="fleet-badge" title="Fleet ID">
            <span className="fleet-badge-dot" />
            {fleetId}
          </div>
        )}
        <nav className="sidebar-nav">
          {NAV.map((g) => (
            <div key={g.group} className="nav-group">
              <div className="nav-group-label">{g.group}</div>
              {g.items.map((item) => (
                <button
                  key={item.id}
                  className={section === item.id ? 'nav-item active' : 'nav-item'}
                  onClick={() => {
                    setSection(item.id)
                    setSidebarOpen(false)
                  }}
                >
                  <NavIcon section={item.id} />
                  {item.label}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">
          <p className="muted small" style={{ padding: '0 10px' }}>
            {user?.email}
          </p>
          <button className="nav-item" onClick={() => signOut(auth)}>
            <IconLogout />
            Sign out
          </button>
        </div>
      </aside>

      <main className={`main${mapPrimary ? ' map-primary' : showMap ? ' map-split' : ''}`}>
        <header className="topbar">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)} aria-label="Open menu">
            <IconMenu />
          </button>
          <h1>{title}</h1>
        </header>

        <div className="status-line">
          {mapPrimary && <span className="status-dot" aria-hidden />}
          <span>{statusLine}</span>
        </div>

        {showMap && (
          <div className="map-panel-wrap">
            <div className="map-panel" ref={mapRef} />
            {mapError && <p className="map-error">{mapError}</p>}
          </div>
        )}

        {!mapPrimary && (
          <section className="content-panel">
            {renderContent()}
            {section === 'requests' && view.kind === 'table' && (
              <div className="actions">
                <button className="btn-primary" onClick={() => void assignFirstRequest()}>
                  Assign first request to online driver
                </button>
              </div>
            )}
          </section>
        )}
      </main>
    </div>
  )
}
