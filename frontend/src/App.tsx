import { useEffect, useState } from 'react'
import {
  getProjects,
  type DeploymentResponse,
  type ProjectResponse,
} from './api'
import DeploymentsPanel from './components/DeploymentsPanel'
import LogsPanel from './components/LogsPanel'
import ProjectCreateForm from './components/ProjectCreateForm'
import ProjectDetails from './components/ProjectDetails'
import ProjectEnvironmentPanel from './components/ProjectEnvironmentPanel'
import ProjectList from './components/ProjectList'
import WorkspaceHeader from './components/WorkspaceHeader'
import type { LoadState } from './types'
import './App.css'

function App() {
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
        <WorkspaceHeader loadState={loadState} />

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
                projectId={selectedProject?.id ?? null}
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

export default App
