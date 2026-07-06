import { useEffect, useState } from 'react'
import {
  ApiError,
  getCurrentUser,
  getProjects,
  logout,
  type CurrentUserResponse,
  type DeploymentResponse,
  type ProjectResponse,
} from './api'
import DeploymentsPanel from './components/DeploymentsPanel'
import LogsPanel from './components/LogsPanel'
import LoginPage from './components/LoginPage'
import ProjectCreateForm from './components/ProjectCreateForm'
import ProjectDetails from './components/ProjectDetails'
import ProjectEnvironmentPanel from './components/ProjectEnvironmentPanel'
import ProjectList from './components/ProjectList'
import WorkspaceHeader from './components/WorkspaceHeader'
import type { LoadState } from './types'
import './App.css'

type AuthState = 'checking' | 'authenticated' | 'unauthenticated'

function Dashboard({
  currentUser,
  onLogout,
}: {
  currentUser: CurrentUserResponse
  onLogout: () => void
}) {
  const [projects, setProjects] = useState<ProjectResponse[]>([])
  const [selectedProjectId, setSelectedProjectId] = useState<string | null>(null)
  const [loadState, setLoadState] = useState<LoadState>('loading')
  const [errorMessage, setErrorMessage] = useState('')
  const [isCreateProjectOpen, setIsCreateProjectOpen] = useState(false)
  const [selectedDeployment, setSelectedDeployment] =
    useState<DeploymentResponse | null>(null)

  useEffect(() => {
    let active = true

    async function loadProjects() {
      try {
        const data = await getProjects()

        if (!active) {
          return
        }

        setProjects(data)
        setSelectedProjectId((current) => current ?? data[0]?.id ?? null)
        setLoadState('ready')
      } catch (error) {
        if (!active) {
          return
        }

        setLoadState('error')
        setErrorMessage(error instanceof Error ? error.message : 'Request failed')
      }
    }

    loadProjects()

    return () => {
      active = false
    }
  }, [])

  const selectedProject =
    projects.find((project) => project.id === selectedProjectId) ?? null

  const handleProjectCreated = async (project: ProjectResponse) => {
    const data = await getProjects()

    setProjects(data)
    setSelectedProjectId(project.id)
    setSelectedDeployment(null)
    setLoadState('ready')
    setIsCreateProjectOpen(false)
  }

  const handleSelectProject = (projectId: string) => {
    setSelectedProjectId(projectId)
    setSelectedDeployment(null)
  }

  return (
    <main className="dashboard-shell">
      <section className="workspace">
        <WorkspaceHeader
          currentUser={currentUser}
          loadState={loadState}
          onLogout={onLogout}
        />

        <section className="dashboard-grid" aria-label="Dashboard overview">
          <section className="panel project-list-panel" id="projects">
            <div className="panel-header">
              <div>
                <p className="eyebrow">Projects</p>
                <h3>{projects.length} configured</h3>
              </div>
              <button
                className="primary-action"
                onClick={() => setIsCreateProjectOpen(true)}
                type="button"
              >
                New project
              </button>
            </div>

            <ProjectList
              errorMessage={errorMessage}
              loadState={loadState}
              onSelectProject={handleSelectProject}
              projects={projects}
              selectedProjectId={selectedProjectId}
            />
          </section>

          <section className="detail-column" aria-label="Project workspace">
            <ProjectDetails project={selectedProject} />
            <div className="detail-scroll">
              <ProjectEnvironmentPanel projectId={selectedProject?.id ?? null} />
              <DeploymentsPanel
                onSelectedDeploymentChange={setSelectedDeployment}
                project={selectedProject}
                selectedDeploymentId={selectedDeployment?.id ?? null}
              />
              <LogsPanel deployment={selectedDeployment} />
            </div>
          </section>
        </section>
      </section>

      {isCreateProjectOpen ? (
        <div className="modal-backdrop" role="presentation">
          <section
            aria-labelledby="create-project-title"
            aria-modal="true"
            className="modal-panel"
            role="dialog"
          >
            <div className="modal-header">
              <div>
                <p className="eyebrow">Project</p>
                <h3 id="create-project-title">Create project</h3>
              </div>
              <button
                className="secondary-action"
                onClick={() => setIsCreateProjectOpen(false)}
                type="button"
              >
                Close
              </button>
            </div>
            <ProjectCreateForm
              onCancel={() => setIsCreateProjectOpen(false)}
              onProjectCreated={handleProjectCreated}
            />
          </section>
        </div>
      ) : null}
    </main>
  )
}

function App() {
  const [authState, setAuthState] = useState<AuthState>('checking')
  const [currentUser, setCurrentUser] = useState<CurrentUserResponse | null>(null)

  useEffect(() => {
    let active = true

    async function loadCurrentUser() {
      try {
        const user = await getCurrentUser()

        if (!active) {
          return
        }

        setCurrentUser(user)
        setAuthState('authenticated')
        if (window.location.pathname === '/login') {
          window.history.replaceState(null, '', '/')
        }
      } catch (error) {
        if (!active) {
          return
        }

        if (error instanceof ApiError && error.status === 401) {
          setCurrentUser(null)
          setAuthState('unauthenticated')
          if (window.location.pathname !== '/login') {
            window.history.replaceState(null, '', '/login')
          }
          return
        }

        setCurrentUser(null)
        setAuthState('unauthenticated')
      }
    }

    loadCurrentUser()

    return () => {
      active = false
    }
  }, [])

  const handleLogout = async () => {
    await logout()
    setCurrentUser(null)
    setAuthState('unauthenticated')
    window.history.replaceState(null, '', '/login')
  }

  if (authState === 'checking') {
    return (
      <main className="login-shell">
        <section className="login-panel" aria-label="Loading session">
          <div className="brand login-brand">
            <span className="brand-mark">VB</span>
            <div>
              <h1>Vibe Boot</h1>
              <p>Deployment dashboard</p>
            </div>
          </div>
          <div className="login-copy">
            <p className="eyebrow">Session</p>
            <h2>Checking login</h2>
          </div>
        </section>
      </main>
    )
  }

  if (authState === 'unauthenticated' || currentUser === null) {
    return <LoginPage />
  }

  return <Dashboard currentUser={currentUser} onLogout={handleLogout} />
}

export default App
