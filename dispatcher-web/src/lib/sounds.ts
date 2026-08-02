let audioCtx: AudioContext | null = null

/** Play a short attention-grabbing beep (no external asset required). */
export function playNotificationSound() {
  try {
    audioCtx ??= new AudioContext()
    const ctx = audioCtx
    const osc = ctx.createOscillator()
    const gain = ctx.createGain()
    osc.type = 'sine'
    osc.frequency.setValueAtTime(880, ctx.currentTime)
    osc.frequency.setValueAtTime(660, ctx.currentTime + 0.12)
    gain.gain.setValueAtTime(0.0001, ctx.currentTime)
    gain.gain.exponentialRampToValueAtTime(0.25, ctx.currentTime + 0.02)
    gain.gain.exponentialRampToValueAtTime(0.0001, ctx.currentTime + 0.45)
    osc.connect(gain)
    gain.connect(ctx.destination)
    osc.start()
    osc.stop(ctx.currentTime + 0.5)
  } catch {
    // Autoplay policies may block until user gesture — ignore.
  }
}

export async function requestBrowserNotifications(): Promise<boolean> {
  if (!('Notification' in window)) return false
  if (Notification.permission === 'granted') return true
  if (Notification.permission === 'denied') return false
  const perm = await Notification.requestPermission()
  return perm === 'granted'
}

export function showBrowserNotification(title: string, body: string, onClick?: () => void) {
  if (!('Notification' in window) || Notification.permission !== 'granted') return
  const n = new Notification(title, { body, tag: `truckmgmt-${Date.now()}` })
  if (onClick) n.onclick = () => {
    onClick()
    window.focus()
    n.close()
  }
}
