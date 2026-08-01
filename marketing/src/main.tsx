import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ArchitectureTree } from './tcd/ArchitectureTree'
import './styles.css'

const R2_BASE =
  (import.meta as ImportMeta & { env?: Record<string, string> }).env?.VITE_R2_BASE_URL ??
  'https://truckmgmt-media-proxy.neuereatec.workers.dev'

const APK_DOWNLOADS = [
  { id: 'dispatcher', label: 'Dispatcher app', detail: 'Dispatch jobs and monitor fleet activity.' },
  { id: 'driver', label: 'Driver app', detail: 'Company device — routes, proof, and heartbeat sync.' },
  { id: 'customer', label: 'Customer app', detail: 'Schedule deliveries, track trucks, confirm payment.' },
] as const

function Landing() {
  const [tcd, setTcd] = useState(false)
  if (tcd) return <ArchitectureTree onBack={() => setTcd(false)} />
  return (
    <div className="landing">
      <header>
        <strong>TruckMgmt</strong>
        <button onClick={() => setTcd(true)}>Open TCD console</button>
      </header>
      <main>
        <p className="eyebrow">Fleet operations</p>
        <h1>TruckMgmt</h1>
        <p className="lead">
          Dispatcher, driver, and customer apps for scheduled deliveries, live satellite maps,
          soft company-device monitoring, and payment confirmation.
        </p>
        <section className="downloads" id="downloads">
          <h2>Download Android apps</h2>
          <p className="downloads-note">Debug builds for testing. Install on device or emulator.</p>
          <div className="download-grid">
            {APK_DOWNLOADS.map((app) => (
              <a
                key={app.id}
                className="download-card"
                href={`${R2_BASE}/downloads/${app.id}.apk`}
                download={`${app.id}.apk`}
              >
                <span className="download-label">{app.label}</span>
                <span className="download-detail">{app.detail}</span>
                <span className="download-action">Download APK</span>
              </a>
            ))}
          </div>
        </section>
        <div className="cta-row">
          <a className="cta" href="#architecture" onClick={(e) => { e.preventDefault(); setTcd(true) }}>
            View architecture
          </a>
        </div>
      </main>
    </div>
  )
}

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <Landing />
  </StrictMode>,
)
