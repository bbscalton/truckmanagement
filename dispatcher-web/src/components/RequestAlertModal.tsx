import type { PendingRequest } from '../hooks/useDeliveryRequestAlerts'

type Props = {
  request: PendingRequest
  onAssign: () => void
  onDismiss: () => void
}

export function RequestAlertModal({ request, onAssign, onDismiss }: Props) {
  return (
    <div className="request-alert-scrim" role="dialog" aria-modal="true" aria-labelledby="request-alert-title">
      <div className="request-alert-modal">
        <div className="request-alert-pulse" aria-hidden />
        <h2 id="request-alert-title">New delivery request</h2>
        <p className="request-alert-route">
          <span>{request.pickup}</span>
          <span className="arrow">→</span>
          <span>{request.dropoff}</span>
        </p>
        <div className="request-alert-actions">
          <button className="btn-primary" onClick={onAssign}>
            Assign driver
          </button>
          <button className="btn-secondary" onClick={onDismiss}>
            Dismiss
          </button>
        </div>
      </div>
    </div>
  )
}
