import { useState } from 'react'
import {
  createProject,
  type CreateProjectRequest,
  type ProjectResponse,
  type ProjectSourceType,
} from '../api'

type ProjectFormValues = {
  name: string
  sourceType: ProjectSourceType
  repositoryUrl: string
  containerRegistry: string
  branch: string
  dockerfilePath: string
  containerPort: string
  healthCheckPath: string
}

type ProjectCreateFormProps = {
  onCancel: () => void
  onProjectCreated: (project: ProjectResponse) => Promise<void>
}

const emptyProjectForm: ProjectFormValues = {
  name: '',
  sourceType: 'GITHUB_REPOSITORY',
  repositoryUrl: '',
  containerRegistry: '',
  branch: '',
  dockerfilePath: '',
  containerPort: '',
  healthCheckPath: '',
}

const optionalText = (value: string) => {
  const trimmed = value.trim()

  return trimmed === '' ? undefined : trimmed
}

const buildCreateProjectRequest = (
  form: ProjectFormValues,
): CreateProjectRequest => {
  const containerPort = optionalText(form.containerPort)

  if (containerPort !== undefined && Number.isNaN(Number(containerPort))) {
    throw new Error('Container port must be a number')
  }

  return {
    name: form.name.trim(),
    sourceType: form.sourceType,
    repositoryUrl:
      form.sourceType === 'GITHUB_REPOSITORY'
        ? optionalText(form.repositoryUrl)
        : undefined,
    containerRegistry:
      form.sourceType === 'CONTAINER_IMAGE'
        ? optionalText(form.containerRegistry)
        : undefined,
    branch:
      form.sourceType === 'GITHUB_REPOSITORY'
        ? optionalText(form.branch)
        : undefined,
    dockerfilePath:
      form.sourceType === 'GITHUB_REPOSITORY'
        ? optionalText(form.dockerfilePath)
        : undefined,
    containerPort:
      containerPort === undefined ? undefined : Number(containerPort),
    healthCheckPath: optionalText(form.healthCheckPath),
  }
}

function ProjectCreateForm({
  onCancel,
  onProjectCreated,
}: ProjectCreateFormProps) {
  const [projectForm, setProjectForm] =
    useState<ProjectFormValues>(emptyProjectForm)
  const [isCreatingProject, setIsCreatingProject] = useState(false)
  const [createProjectError, setCreateProjectError] = useState('')

  const handleProjectFieldChange = (name: string, value: string) => {
    setProjectForm((current) => ({
      ...current,
      [name]: value,
    }))
  }

  const handleCreateProject = async (event: { preventDefault: () => void }) => {
    event.preventDefault()
    setCreateProjectError('')

    let request: CreateProjectRequest

    try {
      request = buildCreateProjectRequest(projectForm)
    } catch (error) {
      setCreateProjectError(
        error instanceof Error ? error.message : 'Project form is invalid',
      )
      return
    }

    setIsCreatingProject(true)

    try {
      const createdProject = await createProject(request)
      await onProjectCreated(createdProject)
      setProjectForm(emptyProjectForm)
    } catch (error) {
      setCreateProjectError(
        error instanceof Error ? error.message : 'Could not create project',
      )
    } finally {
      setIsCreatingProject(false)
    }
  }

  const isContainerImageProject = projectForm.sourceType === 'CONTAINER_IMAGE'

  return (
    <form className="create-project-form" onSubmit={handleCreateProject}>
      <label className="field full">
        <span>Name</span>
        <input
          name="name"
          onChange={(event) =>
            handleProjectFieldChange(event.target.name, event.target.value)
          }
          required
          type="text"
          value={projectForm.name}
        />
      </label>

      <label className="field full">
        <span>Source</span>
        <select
          name="sourceType"
          onChange={(event) =>
            handleProjectFieldChange(event.target.name, event.target.value)
          }
          value={projectForm.sourceType}
        >
          <option value="GITHUB_REPOSITORY">GitHub repository</option>
          <option value="CONTAINER_IMAGE">Container registry</option>
        </select>
      </label>

      {isContainerImageProject ? (
        <label className="field full">
          <span>Container Registry</span>
          <input
            name="containerRegistry"
            onChange={(event) =>
              handleProjectFieldChange(event.target.name, event.target.value)
            }
            placeholder="ghcr.io/owner/image"
            required
            type="text"
            value={projectForm.containerRegistry}
          />
        </label>
      ) : (
        <>
          <label className="field full">
            <span>Repository URL</span>
            <input
              name="repositoryUrl"
              onChange={(event) =>
                handleProjectFieldChange(event.target.name, event.target.value)
              }
              placeholder="https://github.com/owner/repo"
              required
              type="url"
              value={projectForm.repositoryUrl}
            />
          </label>
          <label className="field">
            <span>Branch</span>
            <input
              name="branch"
              onChange={(event) =>
                handleProjectFieldChange(event.target.name, event.target.value)
              }
              placeholder="main"
              type="text"
              value={projectForm.branch}
            />
          </label>
          <label className="field">
            <span>Dockerfile</span>
            <input
              name="dockerfilePath"
              onChange={(event) =>
                handleProjectFieldChange(event.target.name, event.target.value)
              }
              placeholder="Dockerfile"
              type="text"
              value={projectForm.dockerfilePath}
            />
          </label>
        </>
      )}

      <label className="field">
        <span>Port</span>
        <input
          min="1"
          max="65535"
          name="containerPort"
          onChange={(event) =>
            handleProjectFieldChange(event.target.name, event.target.value)
          }
          placeholder={isContainerImageProject ? '80' : '8080'}
          type="number"
          value={projectForm.containerPort}
        />
      </label>
      <label className="field">
        <span>Health Check</span>
        <input
          name="healthCheckPath"
          onChange={(event) =>
            handleProjectFieldChange(event.target.name, event.target.value)
          }
          placeholder={isContainerImageProject ? '/' : '/health'}
          type="text"
          value={projectForm.healthCheckPath}
        />
      </label>

      <div className="form-footer">
        {createProjectError ? (
          <p className="form-error" role="alert">
            {createProjectError}
          </p>
        ) : null}
        <div className="form-actions">
          <button className="secondary-action" onClick={onCancel} type="button">
            Cancel
          </button>
          <button
            className="primary-action"
            disabled={isCreatingProject}
            type="submit"
          >
            {isCreatingProject ? 'Creating' : 'Create'}
          </button>
        </div>
      </div>
    </form>
  )
}

export default ProjectCreateForm
