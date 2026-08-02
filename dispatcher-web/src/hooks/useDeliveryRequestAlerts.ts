import { useEffect, useRef, useState } from 'react'
import {
  collection,
  onSnapshot,
  orderBy,
  query,
  where,
  limit,
  type DocumentSnapshot,
} from 'firebase/firestore'
import { db, COL } from '../firebase'
import { playNotificationSound, requestBrowserNotifications, showBrowserNotification } from '../lib/sounds'

export type PendingRequest = {
  id: string
  pickup: string
  dropoff: string
  raw: DocumentSnapshot
}

export function useDeliveryRequestAlerts(
  fleetId: string | null,
  onAssign: (requestId: string, docSnap: DocumentSnapshot) => Promise<void>,
) {
  const [pending, setPending] = useState<PendingRequest | null>(null)
  const seen = useRef(new Set<string>())

  useEffect(() => {
    void requestBrowserNotifications()
  }, [])

  useEffect(() => {
    if (!fleetId) return
    const q = query(
      collection(db, COL.fleets, fleetId, COL.deliveryRequests),
      where('status', 'in', ['dispatcher_review', 'requested', 'auto_nearest']),
      limit(50),
    )
    return onSnapshot(q, (snap) => {
      for (const change of snap.docChanges()) {
        if (change.type !== 'added') continue
        const id = change.doc.id
        if (seen.current.has(id)) continue
        seen.current.add(id)
        const pickup = String(change.doc.get('pickupAddress') ?? 'Pickup')
        const dropoff = String(change.doc.get('dropoffAddress') ?? 'Dropoff')
        playNotificationSound()
        showBrowserNotification('New delivery request', `${pickup} → ${dropoff}`)
        setPending({ id, pickup, dropoff, raw: change.doc })
      }
    })
  }, [fleetId])

  const dismiss = () => setPending(null)

  const assign = async () => {
    if (!pending) return
    await onAssign(pending.id, pending.raw)
    setPending(null)
  }

  return { pending, dismiss, assign }
}
