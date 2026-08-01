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

type Section =
  | 'live_map'
  | 'trucks'
  | 'drivers'
  | 'pair'
  | 'requests'
  | 'deliveries'
  | 'playback'
  | 'stops'
  | 'payments'
  | 'totals'
  | 'chat'
  | 'activity'
  | 'settings'

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

export function DashboardPage() {
  const { user, fleetId, profile } = useAuth()
  const [section, setSection] = useState<Section>('live_map')
  const [sidebarOpen, setSidebarOpen] = useState(false)
  const [body, setBody] = useState('Loading…')
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
      setBody('No fleet linked to this account.')
      return
    }
    let unsub: Unsubscribe | undefined

    const clearOverlays = () => {
      mapObj.current?.clearLayers()
    }

    const run = async () => {
      if (section === 'live_map') {
        setBody('Satellite live map — all fleet trucks.')
        unsub = onSnapshot(collection(db, COL.fleets, fleetId, COL.drivers), (snap) => {
          const fleetMap = mapObj.current
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
        setBody(
          snap.empty
            ? 'No trucks yet. Add one below.'
            : snap.docs.map((d) => `${d.get('label')} / ${d.get('plate')} — ${d.get('status')}`).join('\n'),
        )
        return
      }

      if (section === 'drivers') {
        const snap = await getDocs(collection(db, COL.fleets, fleetId, COL.drivers))
        setBody(
          snap.empty
            ? 'No drivers paired.'
            : snap.docs
                .map((d) => `${d.get('displayName')} online=${d.get('online')} hb=${d.get('lastHeartbeatAt')}`)
                .join('\n'),
        )
        return
      }

      if (section === 'requests') {
        const snap = await getDocs(
          query(
            collection(db, COL.fleets, fleetId, COL.deliveryRequests),
            where('status', 'in', ['requested', 'dispatcher_review', 'auto_nearest']),
          ),
        )
        setBody(
          snap.empty
            ? 'No open requests.'
            : snap.docs
                .map((d) => `${d.id.slice(0, 8)} — ${d.get('pickupAddress')} → ${d.get('dropoffAddress')} [${d.get('status')}]`)
                .join('\n'),
        )
        return
      }

      if (section === 'deliveries') {
        const snap = await getDocs(collection(db, COL.fleets, fleetId, COL.deliveries))
        setBody(
          snap.empty
            ? 'No deliveries.'
            : snap.docs.map((d) => `${d.id.slice(0, 8)} — ${d.get('status')} driver=${d.get('assignedDriverId')}`).join('\n'),
        )
        return
      }

      if (section === 'playback') {
        const trips = await getDocs(query(collection(db, COL.fleets, fleetId, COL.trips), limit(10)))
        if (trips.empty) {
          setBody('No completed trips yet.')
          return
        }
        setBody(trips.docs.map((d) => `${d.id.slice(0, 8)} cost=${d.get('cost')} pts=${d.get('pointCount')}`).join('\n'))
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
          setBody(snap.empty ? 'No stops recorded.' : `${snap.size} recent stops on map.`)
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
        setBody(
          snap.empty
            ? 'No accepted payments yet.'
            : snap.docs.map((d) => `$${d.get('amount')} — delivery ${String(d.get('deliveryId')).slice(0, 8)}`).join('\n'),
        )
        return
      }

      if (section === 'totals') {
        const fleet = await getDoc(doc(db, COL.fleets, fleetId))
        setBody(`Trips: ${fleet.get('tripCount') ?? 0}\nRevenue: ${fleet.get('totalRevenue') ?? 0}`)
        return
      }

      if (section === 'chat') {
        unsub = onSnapshot(
          query(collection(db, COL.fleets, fleetId, COL.fleetChat), orderBy('createdAt', 'asc'), limit(100)),
          (snap) => {
            setBody(snap.docs.map((d) => `[${d.get('senderRole')}] ${d.get('text')}`).join('\n') || 'No messages yet.')
          },
        )
        return
      }

      if (section === 'activity') {
        const snap = await getDocs(query(collection(db, COL.fleets, fleetId, COL.activityLogs), limit(40)))
        setBody(
          snap.empty
            ? 'No activity yet.'
            : snap.docs.map((d) => `${d.get('type')}: ${d.get('summary')}`).join('\n'),
        )
        return
      }

      if (section === 'pair') {
        setBody('Create a 6-digit pairing code for a company driver device, or add a truck.')
        return
      }

      if (section === 'settings') {
        setBody(
          `Fleet ID: ${fleetId}\nShare this code with customers (Customer app → Register or Profile).\nEmail: ${user?.email}\nName: ${profile?.displayName ?? '—'}`,
        )
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
      setBody('No open requests to assign.')
      return
    }
    const drivers = await getDocs(query(collection(db, COL.fleets, fleetId, COL.drivers), where('online', '==', true)))
    const driver = drivers.docs[0]
    if (!driver) {
      setBody('No online drivers. Nearest-driver Function will retry after timeout.')
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

  return (
    <div className="dash">
      {sidebarOpen && <div className="sidebar-scrim" onClick={() => setSidebarOpen(false)} />}
      <aside className={sidebarOpen ? 'sidebar open' : 'sidebar'}>
        <div className="sidebar-brand">
          <strong>TruckMgmt</strong>
          <span className="muted small">Dispatcher</span>
        </div>
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
                  {item.label}
                </button>
              ))}
            </div>
          ))}
        </nav>
        <div className="sidebar-footer">
          <p className="muted small">{user?.email}</p>
          <button className="nav-item" onClick={() => signOut(auth)}>
            Sign out
          </button>
        </div>
      </aside>

      <main className="main">
        <header className="topbar">
          <button className="menu-btn" onClick={() => setSidebarOpen(true)}>
            Menu
          </button>
          <h1>{title}</h1>
          <span className="muted small">{fleetId}</span>
        </header>

        {showMap && (
          <div className="map-panel-wrap">
            <div className="map-panel" ref={mapRef} />
            {mapError && <p className="map-error">{mapError}</p>}
          </div>
        )}

        <section className="content-panel">
          <pre className="body-pre">{body}</pre>

          {section === 'pair' && (
            <div className="actions">
              <input value={pairName} onChange={(e) => setPairName(e.target.value)} placeholder="Driver name" />
              <button onClick={() => void createPairCode()}>Create pairing code</button>
              {pairCode && <p className="pair-code">{pairCode}</p>}
              <input value={truckLabel} onChange={(e) => setTruckLabel(e.target.value)} placeholder="Truck plate / label" />
              <button onClick={() => void addTruck()}>Add truck</button>
            </div>
          )}

          {section === 'requests' && (
            <div className="actions">
              <button onClick={() => void assignFirstRequest()}>Assign first request to online driver</button>
            </div>
          )}

          {section === 'settings' && fleetId && (
            <div className="actions">
              <button onClick={() => void copyFleetId()}>{copied ? 'Copied!' : 'Copy fleet ID'}</button>
            </div>
          )}

          {section === 'chat' && (
            <div className="actions row">
              <input value={chatInput} onChange={(e) => setChatInput(e.target.value)} placeholder="Message drivers…" />
              <button onClick={() => void sendChat()}>Send</button>
            </div>
          )}
        </section>
      </main>
    </div>
  )
}
