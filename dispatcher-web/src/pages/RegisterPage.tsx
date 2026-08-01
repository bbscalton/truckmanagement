import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createUserWithEmailAndPassword } from 'firebase/auth'
import { doc, setDoc, serverTimestamp } from 'firebase/firestore'
import { auth, COL, db } from '../firebase'
import { createFleetWithShortId } from '../lib/fleetId'

export function RegisterPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fleetName, setFleetName] = useState('My Fleet')
  const [error, setError] = useState('')

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      const cred = await createUserWithEmailAndPassword(auth, email.trim(), password)
      const fleetId = await createFleetWithShortId(cred.user.uid, fleetName.trim() || 'My Fleet')
      await setDoc(doc(db, COL.dispatcherProfiles, cred.user.uid), {
        email: email.trim(),
        displayName: email.trim().split('@')[0],
        fleetIds: [fleetId],
        primaryFleetId: fleetId,
        createdAt: serverTimestamp(),
      })
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Register failed')
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-grid-bg" aria-hidden />
      <form className="auth-card" onSubmit={onSubmit}>
        <div className="auth-brand">
          <div className="auth-logo">T</div>
          <div>
            <h1>Create fleet</h1>
            <p className="muted small" style={{ margin: 0 }}>
              Set up your operations workspace
            </p>
          </div>
        </div>
        <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Work email" type="email" required autoComplete="email" />
        <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password (min 6 chars)" type="password" required autoComplete="new-password" />
        <input value={fleetName} onChange={(e) => setFleetName(e.target.value)} placeholder="Fleet name" />
        {error && <p className="error">{error}</p>}
        <button type="submit" className="btn-primary">
          Register fleet
        </button>
        <p className="muted small" style={{ textAlign: 'center', margin: 0 }}>
          Have an account? <Link to="/">Sign in</Link>
        </p>
      </form>
    </div>
  )
}
