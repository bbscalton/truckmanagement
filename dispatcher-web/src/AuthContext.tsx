import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react'
import { onAuthStateChanged, type User } from 'firebase/auth'
import { doc, getDoc } from 'firebase/firestore'
import { auth, COL, db } from './firebase'

type DispatcherProfile = {
  email?: string
  displayName?: string
  fleetIds?: string[]
  primaryFleetId?: string
}

type AuthState = {
  user: User | null
  loading: boolean
  profile: DispatcherProfile | null
  fleetId: string | null
  refreshProfile: () => Promise<void>
}

const AuthContext = createContext<AuthState | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [profile, setProfile] = useState<DispatcherProfile | null>(null)

  const refreshProfile = async () => {
    const u = auth.currentUser
    if (!u) {
      setProfile(null)
      return
    }
    const snap = await getDoc(doc(db, COL.dispatcherProfiles, u.uid))
    setProfile(snap.exists() ? (snap.data() as DispatcherProfile) : null)
  }

  useEffect(() => {
    return onAuthStateChanged(auth, async (u) => {
      setUser(u)
      if (u) {
        const snap = await getDoc(doc(db, COL.dispatcherProfiles, u.uid))
        setProfile(snap.exists() ? (snap.data() as DispatcherProfile) : null)
      } else {
        setProfile(null)
      }
      setLoading(false)
    })
  }, [])

  const value = useMemo(
    () => ({
      user,
      loading,
      profile,
      fleetId: profile?.primaryFleetId ?? profile?.fleetIds?.[0] ?? null,
      refreshProfile,
    }),
    [user, loading, profile],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth() {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth outside provider')
  return ctx
}
