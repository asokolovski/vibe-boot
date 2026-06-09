import type { ProjectResponse } from '../api'
import type { LoadState } from '../types'

type ProjectListProps = {
  errorMessage: string
  loadState: LoadState
  onSelectProject: (projectId: string) => void
  projects: ProjectResponse[]
  selectedProjectId: string | null
}

function ProjectList({
  errorMessage,
  loadState,
  onSelectProject,
  projects,
  selectedProjectId,
}: ProjectListProps) {
  if (loadState === 'error') {
    return (
      <div className="empty-state">
        <strong>Could not load projects.</strong>
        <span>{errorMessage}</span>
      </div>
    )
  }

  if (projects.length === 0 && loadState === 'ready') {
    return (
      <div className="empty-state">
        <strong>No projects yet.</strong>
        <span>Create the first project above.</span>
      </div>
    )
  }

  return (
    <div className="project-list">
      {projects.map((project) => (
        <button
          className={
            project.id === selectedProjectId
              ? 'project-row selected'
              : 'project-row'
          }
          key={project.id}
          onClick={() => onSelectProject(project.id)}
          type="button"
        >
          <span>
            <strong>{project.name}</strong>
            <small>{project.repositoryUrl}</small>
          </span>
          <span className="branch-label">{project.branch}</span>
        </button>
      ))}
    </div>
  )
}

export default ProjectList
