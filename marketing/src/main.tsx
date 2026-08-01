import { StrictMode, useState } from 'react'
import { createRoot } from 'react-dom/client'
import { ArchitectureTree } from './tcd/ArchitectureTree'
import './styles.css'

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
