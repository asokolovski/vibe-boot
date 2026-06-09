import { useEffect, useState } from 'react'
import {
  addProjectEnvironmentVariable,
  deleteProjectEnvironmentVariable,
  getProjectEnvironmentVariables,
  type ProjectEnvironmentVariableResponse,
} from '../api'
import type { LoadState } from '../types'

type ProjectEnvironmentPanelProps = {
  projectId: string | null
}

const formatDate = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))

function ProjectEnvironmentPanel({ projectId }: ProjectEnvironmentPanelProps) {
  const [envVars, setEnvVars] = useState<ProjectEnvironmentVariableResponse[]>(
    [],
  )
  const [loadState, setLoadState] = useState<LoadState>('ready')
  const [errorMessage, setErrorMessage] = useState('')
  const [key, setKey] = useState('')
  const [value, setValue] = useState('')
  const [isSaving, setIsSaving] = useState(false)
  const [deletingEnvId, setDeletingEnvId] = useState<string | null>(null)

  useEffect(() => {
    let active = true

    async function loadEnvVars() {
      setKey('')
      setValue('')

      if (!projectId) {
        setEnvVars([])
        setLoadState('ready')
        setErrorMessage('')
        return
      }

      setLoadState('loading')
      setErrorMessage('')

      try {
        const data = await getProjectEnvironmentVariables(projectId)

        if (!active) {
          return
        }

        setEnvVars(data)
        setLoadState('ready')
      } catch (error) {
        if (!active) {
          return
        }

        setLoadState('error')
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }

    loadEnvVars()

    return () => {
      active = false
    }
  }, [projectId])

  const reloadEnvVars = async () => {
    if (!projectId) {
      setEnvVars([])
      return
    }

    const data = await getProjectEnvironmentVariables(projectId)
    setEnvVars(data)
    setLoadState('ready')
  }

  const handleAddEnvVar = async (event: { preventDefault: () => void }) => {
    event.preventDefault()

    if (!projectId) {
      return
    }

    setIsSaving(true)
    setErrorMessage('')

    try {
      await addProjectEnvironmentVariable(projectId, {
        key: key.trim(),
        value,
      })
      await reloadEnvVars()
      setKey('')
      setValue('')
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : 'Could not add env var')
    } finally {
      setIsSaving(false)
    }
  }

  const handleDeleteEnvVar = async (envId: string) => {
    if (!projectId) {
      return
    }

    setDeletingEnvId(envId)
    setErrorMessage('')

    try {
      await deleteProjectEnvironmentVariable(projectId, envId)
      await reloadEnvVars()
    } catch (error) {
      setErrorMessage(
        error instanceof Error ? error.message : 'Could not delete env var',
      )
    } finally {
      setDeletingEnvId(null)
    }
  }

  return (
    <section className="panel environment-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Environment</p>
          <h3>{envVars.length} variables</h3>
        </div>
      </div>

      {projectId ? (
        <>
          <form className="env-var-form" onSubmit={handleAddEnvVar}>
            <label className="field">
              <span>Key</span>
              <input
                name="key"
                onChange={(event) => setKey(event.target.value.toUpperCase())}
                placeholder="APP_ENV"
                required
                type="text"
                value={key}
              />
            </label>
            <label className="field">
              <span>Value</span>
              <input
                name="value"
                onChange={(event) => setValue(event.target.value)}
                placeholder="Hidden after save"
                required
                type="password"
                value={value}
              />
            </label>
            <button
              className="primary-action"
              disabled={isSaving}
              type="submit"
            >
              {isSaving ? 'Saving' : 'Add'}
            </button>
          </form>

          <p className="panel-note">Saved values are encrypted and hidden.</p>

          {errorMessage && loadState !== 'error' ? (
            <p className="form-error" role="alert">
              {errorMessage}
            </p>
          ) : null}

          {loadState === 'loading' ? (
            <div className="empty-state">
              <strong>Loading variables.</strong>
              <span>Fetching saved keys for this project.</span>
            </div>
          ) : loadState === 'error' ? (
            <div className="empty-state">
              <strong>Could not load variables.</strong>
              <span>{errorMessage}</span>
            </div>
          ) : envVars.length === 0 ? (
            <div className="empty-state">
              <strong>No variables yet.</strong>
              <span>Add runtime keys for this project.</span>
            </div>
          ) : (
            <div className="env-var-list">
              {envVars.map((envVar) => (
                <div className="env-var-row" key={envVar.id}>
                  <span>
                    <strong>{envVar.key}</strong>
                    <small>Created {formatDate(envVar.createdAt)}</small>
                  </span>
                  <button
                    className="danger-action"
                    disabled={deletingEnvId === envVar.id}
                    onClick={() => handleDeleteEnvVar(envVar.id)}
                    type="button"
                  >
                    {deletingEnvId === envVar.id ? 'Deleting' : 'Delete'}
                  </button>
                </div>
              ))}
            </div>
          )}
        </>
      ) : (
        <div className="empty-state">
          <strong>No project selected.</strong>
          <span>Environment variables will appear here.</span>
        </div>
      )}
    </section>
  )
}

export default ProjectEnvironmentPanel
