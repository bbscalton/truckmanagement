export type TcdCheckStatus = 'ok' | 'warn' | 'fail' | 'unknown'

export type TcdCheck = {
  id: string
  label: string
  status: TcdCheckStatus
  detail?: string
}

export type ArchNode = {
  id: string
  label: string
  subtitle: string
  group: 'client' | 'firebase' | 'cloudflare' | 'ops'
  status?: TcdCheckStatus
}
