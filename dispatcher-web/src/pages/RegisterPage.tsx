import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { createUserWithEmailAndPassword } from 'firebase/auth'
import { doc, setDoc, serverTimestamp } from 'firebase/firestore'
import { auth, COL, db } from '../firebase'

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
      const fleetRef = doc(db, COL.fleets, crypto.randomUUID().replace(/-/g, '').slice(0, 20))
      await setDoc(fleetRef, {
        ownerUid: cred.user.uid,
        name: fleetName.trim() || 'My Fleet',
        createdAt: serverTimestamp(),
        tripCount: 0,
        totalRevenue: 0,
      })
      await setDoc(doc(db, COL.dispatcherProfiles, cred.user.uid), {
        email: email.trim(),
        displayName: email.trim().split('@')[0],
        fleetIds: [fleetRef.id],
        primaryFleetId: fleetRef.id,
        createdAt: serverTimestamp(),
      })
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Register failed')
    }
  }

  return (
    <div className="auth-shell">
      <form className="auth-card" onSubmit={onSubmit}>
        <h1>Create fleet</h1>
        <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Email" type="email" required />
        <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" type="password" required />
        <input value={fleetName} onChange={(e) => setFleetName(e.target.value)} placeholder="Fleet name" />
        {error && <p className="error">{error}</p>}
        <button type="submit">Register</button>
        <p className="muted">
          Have an account? <Link to="/">Sign in</Link>
        </p>
      </form>
    </div>
  )
}
