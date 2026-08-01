import { doc, getDoc, setDoc, serverTimestamp } from 'firebase/firestore'
import { COL, db } from '../firebase'

const FLEET_ID_CHARS = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789'
export const FLEET_ID_LENGTH = 6

export function generateFleetId(length = FLEET_ID_LENGTH): string {
  let id = ''
  for (let i = 0; i < length; i++) {
    id += FLEET_ID_CHARS[Math.floor(Math.random() * FLEET_ID_CHARS.length)]
  }
  return id
}

export function normalizeFleetId(input: string): string {
  return input.trim().toUpperCase()
}

export async function createFleetWithShortId(
  ownerUid: string,
  name: string,
  maxAttempts = 10,
): Promise<string> {
  for (let attempt = 0; attempt < maxAttempts; attempt++) {
    const id = generateFleetId()
    const fleetRef = doc(db, COL.fleets, id)
    const existing = await getDoc(fleetRef)
    if (existing.exists()) continue

    await setDoc(fleetRef, {
      ownerUid,
      name: name.trim() || 'My Fleet',
      shortCode: id,
      createdAt: serverTimestamp(),
      tripCount: 0,
      totalRevenue: 0,
    })
    return id
  }
  throw new Error('Could not allocate a fleet ID. Please try again.')
}
