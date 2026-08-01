import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { signInWithEmailAndPassword } from 'firebase/auth'
import { auth } from '../firebase'

export function LoginPage() {
  const navigate = useNavigate()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    try {
      await signInWithEmailAndPassword(auth, email.trim(), password)
      navigate('/')
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Sign-in failed')
    }
  }

  return (
    <div className="auth-shell">
      <div className="auth-grid-bg" aria-hidden />
      <form className="auth-card" onSubmit={onSubmit}>
        <div className="auth-brand">
          <div className="auth-logo">T</div>
          <div>
            <h1>TruckMgmt</h1>
            <p className="muted small" style={{ margin: 0 }}>
              Dispatcher operations console
            </p>
          </div>
        </div>
        <input value={email} onChange={(e) => setEmail(e.target.value)} placeholder="Work email" type="email" required autoComplete="email" />
        <input value={password} onChange={(e) => setPassword(e.target.value)} placeholder="Password" type="password" required autoComplete="current-password" />
        {error && <p className="error">{error}</p>}
        <button type="submit" className="btn-primary">
          Sign in
        </button>
        <p className="muted small" style={{ textAlign: 'center', margin: 0 }}>
          New fleet? <Link to="/register">Create account</Link>
        </p>
      </form>
    </div>
  )
}
