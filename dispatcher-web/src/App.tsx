import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'
import { DashboardPage } from './pages/DashboardPage'

function AuthedApp() {
  const { user, loading } = useAuth()
  if (loading) {
    return (
      <div className="auth-shell">
        <div className="auth-grid-bg" aria-hidden />
        <p className="muted">Loading TruckMgmt…</p>
      </div>
    )
  }
  if (!user) {
    return (
      <Routes>
        <Route path="/" element={<LoginPage />} />
        <Route path="/register" element={<RegisterPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    )
  }
  return (
    <Routes>
      <Route path="*" element={<DashboardPage />} />
    </Routes>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthedApp />
    </BrowserRouter>
  )
}
