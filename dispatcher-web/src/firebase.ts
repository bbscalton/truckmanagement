import { initializeApp } from 'firebase/app'
import { getAuth } from 'firebase/auth'
import { getFirestore } from 'firebase/firestore'

const firebaseConfig = {
  apiKey: import.meta.env.VITE_FIREBASE_API_KEY ?? 'REPLACE_ME',
  authDomain: import.meta.env.VITE_FIREBASE_AUTH_DOMAIN ?? 'truckmgmt-dev.firebaseapp.com',
  projectId: import.meta.env.VITE_FIREBASE_PROJECT_ID ?? 'truckmgmt-dev',
  storageBucket: import.meta.env.VITE_FIREBASE_STORAGE_BUCKET ?? 'truckmgmt-dev.firebasestorage.app',
  messagingSenderId: import.meta.env.VITE_FIREBASE_MESSAGING_SENDER_ID ?? '0',
  appId: import.meta.env.VITE_FIREBASE_APP_ID ?? '1:0:web:0',
}

export const app = initializeApp(firebaseConfig)
export const auth = getAuth(app)
export const db = getFirestore(app)

export const COL = {
  dispatcherProfiles: 'dispatcherProfiles',
  customerProfiles: 'customerProfiles',
  fleets: 'fleets',
  pairingCodes: 'pairingCodes',
  trucks: 'trucks',
  drivers: 'drivers',
  devices: 'devices',
  deliveryRequests: 'deliveryRequests',
  deliveries: 'deliveries',
  locationTrail: 'locationTrail',
  trips: 'trips',
  payments: 'payments',
  fleetChat: 'fleetChat',
  stops: 'stops',
  activityLogs: 'activityLogs',
  commands: 'commands',
} as const

export const MAPS_API_KEY = import.meta.env.VITE_GOOGLE_MAPS_API_KEY ?? ''
export const R2_BASE = import.meta.env.VITE_R2_BASE_URL ?? 'https://truckmgmt-media-proxy.neuereatec.workers.dev'

/** PUT — upload blob to R2 via Worker (canonical storage; not Firebase Storage). */
export const r2UploadUrl = (key: string) => `${R2_BASE}/upload/${key}`

/** GET — public media URL. */
export const r2MediaUrl = (key: string) => `${R2_BASE}/media/${key}`

/** GET — APK/binary download. */
export const r2DownloadUrl = (key: string) => `${R2_BASE}/downloads/${key}`
