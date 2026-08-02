import { r2MediaUrl, r2UploadUrl } from '../firebase'

export async function uploadChatMedia(fleetId: string, blob: Blob, extension: string, contentType: string): Promise<string> {
  const key = `fleets/${fleetId}/chat/${crypto.randomUUID()}.${extension}`
  const res = await fetch(r2UploadUrl(key), {
    method: 'PUT',
    headers: { 'Content-Type': contentType },
    body: blob,
  })
  if (!res.ok) throw new Error(`Upload failed (${res.status})`)
  return r2MediaUrl(key)
}
