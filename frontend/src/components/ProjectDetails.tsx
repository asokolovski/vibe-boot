import type { ProjectResponse } from '../api'

type ProjectDetailsProps = {
  project: ProjectResponse | null
}

const formatDate = (value: string) =>
  new Intl.DateTimeFormat(undefined, {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))

function ProjectDetails({ project }: ProjectDetailsProps) {
  return (
    <section className="panel project-detail-panel">
      <div className="panel-header">
        <div>
          <p className="eyebrow">Selected Project</p>
          <h3>{project?.name ?? 'None selected'}</h3>
        </div>
      </div>

      {project ? (
        <dl className="detail-grid">
          <div>
            <dt>Repository</dt>
            <dd>{project.repositoryUrl}</dd>
          </div>
          <div>
            <dt>Branch</dt>
            <dd>{project.branch}</dd>
          </div>
          <div>
            <dt>Dockerfile</dt>
            <dd>{project.dockerfilePath}</dd>
          </div>
          <div>
            <dt>Container Port</dt>
            <dd>{project.containerPort}</dd>
          </div>
          <div>
            <dt>Health Check</dt>
            <dd>{project.healthCheckPath}</dd>
          </div>
          <div>
            <dt>Created</dt>
            <dd>{formatDate(project.createdAt)}</dd>
          </div>
        </dl>
      ) : (
        <div className="empty-state">
          <strong>No project selected.</strong>
          <span>Project details will appear here.</span>
        </div>
      )}
    </section>
  )
}

export default ProjectDetails
