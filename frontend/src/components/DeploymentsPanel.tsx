import { useEffect, useState } from 'react'
import {
  getDeployment,
  getProjectDeployments,
  stopDeployment,
  triggerDeployment,
  type DeploymentResponse,
} from '../api'
import type { LoadState } from '../types'

type DeploymentsPanelProps = {
  onSelectedDeploymentChange: (deployment: DeploymentResponse | null) => void
  projectId: string | null
  selectedDeploymentId: string | null
}

const activeStatuses = new Set(['QUEUED', 'RUNNING'])

const formatDate = (value: string | null) => {
  if (!value) {
    return 'Not set'
  }

  return new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

const shortId = (value: string | null) => {
  if (!value) {
    return 'Not set'
  }

  return value.length > 12 ? `${value.slice(0, 12)}...` : value
}

const hasRunningContainer = (deployment: DeploymentResponse) =>
  Boolean(deployment.containerId) && deployment.status !== 'STOPPED'

function DeploymentsPanel({
  onSelectedDeploymentChange,
  projectId,
  selectedDeploymentId,
}: DeploymentsPanelProps) {
  const [deployments, setDeployments] = useState<DeploymentResponse[]>([])
  const [loadState, setLoadState] = useState<LoadState>('ready')
  const [errorMessage, setErrorMessage] = useState('')
  const [isDeploying, setIsDeploying] = useState(false)
  const [stoppingDeploymentId, setStoppingDeploymentId] = useState<string | null>(
    null,
  )

  const selectedDeployment =
    deployments.find((deployment) => deployment.id === selectedDeploymentId) ??
    null

  useEffect(() => {
    let active = true

    async function loadDeployments() {
      if (!projectId) {
        setDeployments([])
        onSelectedDeploymentChange(null)
        setLoadState('ready')
        setErrorMessage('')
        return
      }

      setLoadState('loading')
      setErrorMessage('')

      try {
        const data = await getProjectDeployments(projectId)

        if (!active) {
          return
        }

        setDeployments(data)
        onSelectedDeploymentChange(data[0] ?? null)
        setLoadState('ready')
      } catch (error) {
        if (!active) {
          return
        }

        setLoadState('error')
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }

    loadDeployments()

    return () => {
      active = false
    }
  }, [onSelectedDeploymentChange, projectId])

  useEffect(() => {
    if (
      !selectedDeployment ||
      !activeStatuses.has(selectedDeployment.status)
    ) {
      return
    }

    const intervalId = window.setInterval(async () => {
      try {
        const updatedDeployment = await getDeployment(selectedDeployment.id)

        setDeployments((current) =>
          current.map((deployment) =>
            deployment.id === updatedDeployment.id
              ? updatedDeployment
              : deployment,
          ),
        )
        onSelectedDeploymentChange(updatedDeployment)
      } catch (error) {
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }, 3000)

    return () => window.clearInterval(intervalId)
  }, [onSelectedDeploymentChange, selectedDeployment])

  const reloadDeployments = async (nextSelectedDeploymentId: string) => {
    if (!projectId) {
      return
    }

    const data = await getProjectDeployments(projectId)
    setDeployments(data)
    onSelectedDeploymentChange(
      data.find((deployment) => deployment.id === nextSelectedDeploymentId) ??
        null,
    )
    setLoadState('ready')
  }

  const handleTriggerDeployment = async () => {
    if (!projectId) {
      return
    }

    setIsDeploying(true)
    setErrorMessage('')

    try {
      const deployment = await triggerDeployment({ projectId })
      await reloadDeployments(deployment.id)
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : 'Could not trigger deployment',
      )
    } finally {
      setIsDeploying(false)
    }
  }

  const handleStopDeployment = async (deploymentId: string) => {
    setStoppingDeploymentId(deploymentId)
    setErrorMessage('')

    try {
      const updatedDeployment = await stopDeployment(deploymentId)

      setDeployments((current) =>
        current.map((deployment) =>
          deployment.id === updatedDeployment.id ? updatedDeployment : deployment,
        ),
      )
      onSelectedDeploymentChange(updatedDeployment)
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : 'Could not stop deployment',
      )
    } finally {
      setStoppingDeploymentId(null)
    }
  }

  return (
    <section className="panel deployment-panel" id="deployments">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Deployments</p>
          <h3>{deployments.length} runs</h3>
        </div>
        <button
          className="primary-action"
          disabled={!projectId || isDeploying}
          onClick={handleTriggerDeployment}
          type="button"
        >
          {isDeploying ? 'Deploying' : 'Deploy'}
        </button>
      </div>

      {errorMessage ? (
        <p className="form-error deployment-error" role="alert">
          {errorMessage}
        </p>
      ) : null}

      {!projectId ? (
        <div className="empty-state">
          <strong>No project selected.</strong>
          <span>Deployment history will appear here.</span>
        </div>
      ) : loadState === 'loading' ? (
        <div className="empty-state">
          <strong>Loading deployments.</strong>
          <span>Fetching run history for this project.</span>
        </div>
      ) : loadState === 'error' ? (
        <div className="empty-state">
          <strong>Could not load deployments.</strong>
          <span>{errorMessage}</span>
        </div>
      ) : deployments.length === 0 ? (
        <div className="empty-state">
          <strong>No deployments yet.</strong>
          <span>Deploy this project to create the first run.</span>
        </div>
      ) : (
        <div className="deployment-layout">
          <div className="deployment-list">
            {deployments.map((deployment) => (
              <button
                className={
                  deployment.id === selectedDeploymentId
                    ? 'deployment-row selected'
                    : 'deployment-row'
                }
                key={deployment.id}
                onClick={() => onSelectedDeploymentChange(deployment)}
                type="button"
              >
                <span
                  className={`deployment-status ${deployment.status.toLowerCase()}`}
                >
                  {deployment.status}
                </span>
                <span>
                  <strong>{shortId(deployment.id)}</strong>
                  <small>{formatDate(deployment.createdAt)}</small>
                </span>
              </button>
            ))}
          </div>

          {selectedDeployment ? (
            <div className="deployment-details">
              <div className="deployment-detail-header">
                <span
                  className={`deployment-status ${selectedDeployment.status.toLowerCase()}`}
                >
                  {selectedDeployment.status}
                </span>
                <button
                  className="danger-action"
                  disabled={
                    !hasRunningContainer(selectedDeployment) ||
                    stoppingDeploymentId === selectedDeployment.id
                  }
                  onClick={() => handleStopDeployment(selectedDeployment.id)}
                  type="button"
                >
                  {stoppingDeploymentId === selectedDeployment.id
                    ? 'Stopping'
                    : 'Stop'}
                </button>
              </div>

              <dl className="deployment-detail-grid">
                <div>
                  <dt>Created</dt>
                  <dd>{formatDate(selectedDeployment.createdAt)}</dd>
                </div>
                <div>
                  <dt>Started</dt>
                  <dd>{formatDate(selectedDeployment.startedAt)}</dd>
                </div>
                <div>
                  <dt>Finished</dt>
                  <dd>{formatDate(selectedDeployment.finishedAt)}</dd>
                </div>
                <div>
                  <dt>URL</dt>
                  <dd>
                    {selectedDeployment.deploymentUrl ? (
                      <a
                        href={selectedDeployment.deploymentUrl}
                        rel="noreferrer"
                        target="_blank"
                      >
                        {selectedDeployment.deploymentUrl}
                      </a>
                    ) : (
                      'Not set'
                    )}
                  </dd>
                </div>
                <div>
                  <dt>Image</dt>
                  <dd>{selectedDeployment.imageName ?? 'Not set'}</dd>
                </div>
                <div>
                  <dt>Container</dt>
                  <dd>{shortId(selectedDeployment.containerId)}</dd>
                </div>
                <div>
                  <dt>Host Port</dt>
                  <dd>{selectedDeployment.hostPort ?? 'Not set'}</dd>
                </div>
                <div>
                  <dt>Container Port</dt>
                  <dd>{selectedDeployment.containerPort ?? 'Not set'}</dd>
                </div>
              </dl>
            </div>
          ) : null}
        </div>
      )}
    </section>
  )
}

export default DeploymentsPanel
