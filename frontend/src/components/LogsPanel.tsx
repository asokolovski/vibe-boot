import { useEffect, useState } from 'react'
import {
  getDeploymentLogs,
  type DeploymentLogResponse,
  type DeploymentResponse,
} from '../api'
import type { LoadState } from '../types'

type LogsPanelProps = {
  deployment: DeploymentResponse | null
}

const activeStatuses = new Set(['QUEUED', 'RUNNING'])

const formatDate = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit',
  }).format(new Date(value))

function LogsPanel({ deployment }: LogsPanelProps) {
  const [logs, setLogs] = useState<DeploymentLogResponse[]>([])
  const [loadState, setLoadState] = useState<LoadState>('ready')
  const [errorMessage, setErrorMessage] = useState('')

  const deploymentId = deployment?.id ?? null
  const shouldPoll =
    deployment !== null && activeStatuses.has(deployment.status)

  useEffect(() => {
    let active = true

    async function loadLogs() {
      if (!deploymentId) {
        setLogs([])
        setLoadState('ready')
        setErrorMessage('')
        return
      }

      setLoadState('loading')
      setErrorMessage('')

      try {
        const data = await getDeploymentLogs(deploymentId)

        if (!active) {
          return
        }

        setLogs(data)
        setLoadState('ready')
      } catch (error) {
        if (!active) {
          return
        }

        setLoadState('error')
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }

    loadLogs()

    return () => {
      active = false
    }
  }, [deploymentId])

  useEffect(() => {
    if (!deploymentId || !shouldPoll) {
      return
    }

    const intervalId = window.setInterval(async () => {
      try {
        const data = await getDeploymentLogs(deploymentId)
        setLogs(data)
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [deploymentId, shouldPoll])

  return (
    <section className="panel logs-panel" id="logs">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Logs</p>
          <h3>{logs.length} entries</h3>
        </div>
      </div>

      {!deployment ? (
        <div className="empty-state">
          <strong>No deployment selected.</strong>
          <span>Select a deployment to inspect its output.</span>
        </div>
      ) : loadState === 'loading' ? (
        <div className="empty-state">
          <strong>Loading logs.</strong>
          <span>Fetching deployment output.</span>
        </div>
      ) : loadState === 'error' ? (
        <div className="empty-state">
          <strong>Could not load logs.</strong>
          <span>{errorMessage}</span>
        </div>
      ) : logs.length === 0 ? (
        <div className="empty-state">
          <strong>No logs yet.</strong>
          <span>Logs will appear as the deployment runs.</span>
        </div>
      ) : (
        <div className="log-list">
          {logs.map((log) => (
            <div className="log-row" key={`${log.createdAt}-${log.message}`}>
              <time>{formatDate(log.createdAt)}</time>
              <span>{log.message}</span>
            </div>
          ))}
        </div>
      )}
    </section>
  )
}

export default LogsPanel
