export type NavSection =
  | 'live_map'
  | 'trucks'
  | 'drivers'
  | 'pair'
  | 'requests'
  | 'deliveries'
  | 'playback'
  | 'stops'
  | 'payments'
  | 'totals'
  | 'chat'
  | 'activity'
  | 'settings'

type IconProps = { className?: string }

const base = (className?: string) => className ?? 'nav-icon'

export function IconMap({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M3 6l6-2 6 2 6-2v14l-6 2-6-2-6 2V6z" />
      <path d="M9 4v14M15 6v14" />
    </svg>
  )
}

export function IconTruck({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M3 17h13v-5H3v5z" />
      <path d="M16 12h3l2 3v5h-5v-5z" />
      <circle cx="7" cy="17" r="2" />
      <circle cx="18" cy="17" r="2" />
    </svg>
  )
}

export function IconUsers({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="9" cy="8" r="3" />
      <path d="M3 19c0-3 2.7-5 6-5s6 2 6 5" />
      <path d="M16 11a3 3 0 1 1 0 6" />
      <path d="M21 19c0-2.2-1.8-4-4-4" />
    </svg>
  )
}

export function IconLink({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M10 13a5 5 0 0 1 0-7l1-1a5 5 0 0 1 7 7l-1 1" />
      <path d="M14 11a5 5 0 0 1 0 7l-1 1a5 5 0 0 1-7-7l1-1" />
    </svg>
  )
}

export function IconInbox({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M4 4h16v12H4z" />
      <path d="M4 13h5l2 3h2l2-3h5" />
    </svg>
  )
}

export function IconRoute({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="6" cy="19" r="2" />
      <circle cx="18" cy="5" r="2" />
      <path d="M8 19h5a4 4 0 0 0 4-4V9" />
    </svg>
  )
}

export function IconPlay({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <polygon points="8,5 19,12 8,19" />
    </svg>
  )
}

export function IconPin({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M12 21s6-5.2 6-10a6 6 0 1 0-12 0c0 4.8 6 10 6 10z" />
      <circle cx="12" cy="11" r="2" />
    </svg>
  )
}

export function IconCard({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <rect x="3" y="6" width="18" height="12" rx="2" />
      <path d="M3 10h18" />
    </svg>
  )
}

export function IconChart({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M4 19V5M4 19h16" />
      <path d="M8 16V11M12 16V8M16 16v-5" />
    </svg>
  )
}

export function IconChat({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M4 5h16v10H7l-3 3V5z" />
    </svg>
  )
}

export function IconActivity({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M4 14l4-4 4 6 4-10 4 8" />
    </svg>
  )
}

export function IconSettings({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <circle cx="12" cy="12" r="3" />
      <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
    </svg>
  )
}

export function IconMenu({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M4 7h16M4 12h16M4 17h16" />
    </svg>
  )
}

export function IconCopy({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <rect x="9" y="9" width="11" height="11" rx="2" />
      <path d="M5 15V5a2 2 0 0 1 2-2h10" />
    </svg>
  )
}

export function IconLogout({ className }: IconProps) {
  return (
    <svg className={base(className)} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75">
      <path d="M10 17l-5-5 5-5" />
      <path d="M5 12h10" />
      <path d="M14 5h4a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2h-4" />
    </svg>
  )
}

const ICONS: Record<NavSection, (p: IconProps) => JSX.Element> = {
  live_map: IconMap,
  trucks: IconTruck,
  drivers: IconUsers,
  pair: IconLink,
  requests: IconInbox,
  deliveries: IconRoute,
  playback: IconPlay,
  stops: IconPin,
  payments: IconCard,
  totals: IconChart,
  chat: IconChat,
  activity: IconActivity,
  settings: IconSettings,
}

export function NavIcon({ section, className }: { section: NavSection; className?: string }) {
  const C = ICONS[section]
  return <C className={className} />
}
