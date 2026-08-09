import { useEffect, useState } from 'react'

type HealthResponse = {
  status?: unknown
}

export function BackendStatus() {
  const [status, setStatus] = useState('CHECKING')

  useEffect(() => {
    const controller = new AbortController()

    async function checkBackend() {
      try {
        const response = await fetch('/actuator/health', {
          headers: { Accept: 'application/json' },
          signal: controller.signal,
        })

        if (!response.ok) {
          throw new Error(`Backend health check failed with ${response.status}`)
        }

        const health = (await response.json()) as HealthResponse
        setStatus(
          typeof health.status === 'string' ? health.status : 'UNAVAILABLE',
        )
      } catch (error) {
        if (error instanceof DOMException && error.name === 'AbortError') {
          return
        }

        setStatus('UNAVAILABLE')
      }
    }

    void checkBackend()

    return () => controller.abort()
  }, [])

  const statusClass =
    status === 'UP'
      ? 'is-up'
      : status === 'CHECKING'
        ? 'is-checking'
        : 'is-down'

  return (
    <p className="backend-status" role="status" aria-live="polite">
      <span className={`status-dot ${statusClass}`} aria-hidden="true" />
      Backend status: <strong>{status}</strong>
    </p>
  )
}
