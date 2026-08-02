import { useEffect, useRef, useState } from 'react'
import {
  addDoc,
  collection,
  onSnapshot,
  orderBy,
  query,
  serverTimestamp,
  limit,
} from 'firebase/firestore'
import { db, COL } from '../firebase'
import { uploadChatMedia } from '../lib/r2Upload'
import { playNotificationSound } from '../lib/sounds'

export type ChatMsg = {
  id: string
  type: string
  text: string
  mediaUrl?: string
  senderRole: string
  createdAt?: Date
  audioDurationSec?: number
}

type Props = {
  fleetId: string
  userUid?: string
  senderRole?: string
}

function formatTime(d?: Date) {
  if (!d) return ''
  return d.toLocaleTimeString([], { hour: 'numeric', minute: '2-digit' })
}

export function ChatPanel({ fleetId, userUid, senderRole = 'dispatcher' }: Props) {
  const [messages, setMessages] = useState<ChatMsg[]>([])
  const [input, setInput] = useState('')
  const [recording, setRecording] = useState(false)
  const [uploading, setUploading] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)
  const mediaRecorderRef = useRef<MediaRecorder | null>(null)
  const chunksRef = useRef<Blob[]>([])
  const lastMsgId = useRef<string | null>(null)

  useEffect(() => {
    const q = query(
      collection(db, COL.fleets, fleetId, COL.fleetChat),
      orderBy('createdAt', 'asc'),
      limit(200),
    )
    return onSnapshot(q, (snap) => {
      const msgs: ChatMsg[] = snap.docs.map((d) => ({
        id: d.id,
        type: String(d.get('type') ?? 'text'),
        text: String(d.get('text') ?? ''),
        mediaUrl: d.get('mediaUrl') as string | undefined,
        senderRole: String(d.get('senderRole') ?? 'unknown'),
        createdAt: d.get('createdAt')?.toDate?.(),
        audioDurationSec: d.get('audioDurationSec') as number | undefined,
      }))
      setMessages(msgs)
      const last = msgs[msgs.length - 1]
      if (last && last.id !== lastMsgId.current && last.senderRole !== senderRole) {
        playNotificationSound()
      }
      if (last) lastMsgId.current = last.id
      bottomRef.current?.scrollIntoView({ behavior: 'smooth' })
    })
  }, [fleetId, senderRole])

  const sendText = async () => {
    const text = input.trim()
    if (!text) return
    setInput('')
    await addDoc(collection(db, COL.fleets, fleetId, COL.fleetChat), {
      type: 'text',
      text,
      senderUid: userUid,
      senderRole,
      createdAt: serverTimestamp(),
    })
  }

  const sendMediaMessage = async (type: 'image' | 'audio', mediaUrl: string, extra: Record<string, unknown> = {}) => {
    await addDoc(collection(db, COL.fleets, fleetId, COL.fleetChat), {
      type,
      text: '',
      mediaUrl,
      senderUid: userUid,
      senderRole,
      createdAt: serverTimestamp(),
      ...extra,
    })
  }

  const onPickImage = async (file: File | undefined) => {
    if (!file) return
    setUploading(true)
    try {
      const ext = file.name.split('.').pop() || 'jpg'
      const url = await uploadChatMedia(fleetId, file, ext, file.type || 'image/jpeg')
      await sendMediaMessage('image', url)
    } finally {
      setUploading(false)
    }
  }

  const toggleVoice = async () => {
    if (recording) {
      mediaRecorderRef.current?.stop()
      setRecording(false)
      return
    }
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    const rec = new MediaRecorder(stream)
    chunksRef.current = []
    rec.ondataavailable = (e) => {
      if (e.data.size) chunksRef.current.push(e.data)
    }
    rec.onstop = async () => {
      stream.getTracks().forEach((t) => t.stop())
      const blob = new Blob(chunksRef.current, { type: 'audio/webm' })
      setUploading(true)
      try {
        const url = await uploadChatMedia(fleetId, blob, 'webm', 'audio/webm')
        const durationSec = Math.max(1, Math.round(blob.size / 8000))
        await sendMediaMessage('audio', url, { audioDurationSec: durationSec })
      } finally {
        setUploading(false)
      }
    }
    mediaRecorderRef.current = rec
    rec.start()
    setRecording(true)
  }

  return (
    <div className="panel-card chat-panel chat-panel-rich">
      <div className="chat-messages">
        {messages.length === 0 ? (
          <div className="empty-state compact">
            <h3>No messages yet</h3>
            <p>Send the first message to your fleet — text, images, or voice notes.</p>
          </div>
        ) : (
          messages.map((m) => (
            <div key={m.id} className={`chat-bubble ${m.senderRole}${m.senderRole === senderRole ? ' mine' : ''}`}>
              <div className="role">{m.senderRole}</div>
              {m.type === 'image' && m.mediaUrl && (
                <a href={m.mediaUrl} target="_blank" rel="noreferrer">
                  <img src={m.mediaUrl} alt="Shared" className="chat-image" />
                </a>
              )}
              {m.type === 'audio' && m.mediaUrl && (
                <div className="chat-audio">
                  <audio controls src={m.mediaUrl} preload="none" />
                  <span className="chat-audio-label">
                    {m.audioDurationSec ? `${m.audioDurationSec}s voice note` : 'Voice note'}
                  </span>
                </div>
              )}
              {m.text && <div className="chat-text">{m.text}</div>}
              <div className="chat-time">{formatTime(m.createdAt)}</div>
            </div>
          ))
        )}
        <div ref={bottomRef} />
      </div>
      <div className="chat-compose chat-compose-rich">
        <label className="chat-icon-btn" title="Attach image">
          📷
          <input
            type="file"
            accept="image/*"
            hidden
            onChange={(e) => void onPickImage(e.target.files?.[0])}
          />
        </label>
        <button
          type="button"
          className={`chat-icon-btn${recording ? ' recording' : ''}`}
          onClick={() => void toggleVoice()}
          title="Record voice note"
        >
          🎤
        </button>
        <input
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={uploading ? 'Uploading…' : 'Message drivers…'}
          disabled={uploading}
          onKeyDown={(e) => e.key === 'Enter' && void sendText()}
        />
        <button className="btn-primary" disabled={uploading} onClick={() => void sendText()}>
          Send
        </button>
      </div>
      {recording && <p className="recording-banner">Recording… tap 🎤 again to send</p>}
    </div>
  )
}
