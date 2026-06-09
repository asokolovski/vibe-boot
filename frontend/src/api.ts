export type ProjectResponse = {
  id: string
  name: string
  repositoryUrl: string
  branch: string
  dockerfilePath: string
  containerPort: number
  healthCheckPath: string
  createdAt: string
}

export type CreateProjectRequest = {
  name: string
  repositoryUrl: string
  branch?: string
  dockerfilePath?: string
  containerPort?: number
  healthCheckPath?: string
}

export type ProjectEnvironmentVariableResponse = {
  id: string
  projectId: string
  key: string
  createdAt: string
}

export type AddProjectEnvironmentVariableRequest = {
  key: string
  value: string
}

export type DeploymentStatus =
  | 'QUEUED'
  | 'RUNNING'
  | 'SUCCESS'
  | 'FAILED'
  | 'STOPPED'

export type DeploymentResponse = {
  id: string
  projectId: string
  status: DeploymentStatus
  createdAt: string
  startedAt: string | null
  finishedAt: string | null
  imageName: string | null
  containerId: string | null
  hostPort: number | null
  containerPort: number | null
  deploymentUrl: string | null
}

export type TriggerDeploymentRequest = {
  projectId: string
}

export type DeploymentLogResponse = {
  message: string
  createdAt: string
}

type ApiErrorResponse = {
  message?: unknown
}

type RequestOptions = {
  method?: string
  body?: unknown
}

export class ApiError extends Error {
  status: number

  constructor(message: string, status: number) {
    super(message)
    this.name = 'ApiError'
    this.status = status
  }
}

async function readErrorMessage(response: Response) {
  try {
    const body = (await response.json()) as ApiErrorResponse

    if (typeof body.message === 'string' && body.message.trim()) {
      return body.message
    }
  } catch {
    // Fall back to the status text below when the backend did not return JSON.
  }

  return response.statusText || `Request failed with ${response.status}`
}

async function request<T>(path: string, options: RequestOptions = {}) {
  const response = await fetch(path, {
    method: options.method ?? 'GET',
    headers: options.body === undefined ? undefined : { 'Content-Type': 'application/json' },
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
  })

  if (!response.ok) {
    throw new ApiError(await readErrorMessage(response), response.status)
  }

  if (response.status === 204) {
    return undefined as T
  }

  return (await response.json()) as T
}

export function getProjects() {
  return request<ProjectResponse[]>('/api/projects')
}

export function createProject(project: CreateProjectRequest) {
  return request<ProjectResponse>('/api/projects', {
    method: 'POST',
    body: project,
  })
}

export function getProjectEnvironmentVariables(projectId: string) {
  return request<ProjectEnvironmentVariableResponse[]>(`/api/projects/${projectId}/env`)
}

export function addProjectEnvironmentVariable(
  projectId: string,
  environmentVariable: AddProjectEnvironmentVariableRequest,
) {
  return request<ProjectEnvironmentVariableResponse>(`/api/projects/${projectId}/env`, {
    method: 'POST',
    body: environmentVariable,
  })
}

export function deleteProjectEnvironmentVariable(projectId: string, envId: string) {
  return request<void>(`/api/projects/${projectId}/env/${envId}`, {
    method: 'DELETE',
  })
}

export function getProjectDeployments(projectId: string) {
  return request<DeploymentResponse[]>(`/api/projects/${projectId}/deployments`)
}

export function triggerDeployment(deployment: TriggerDeploymentRequest) {
  return request<DeploymentResponse>('/api/deployments', {
    method: 'POST',
    body: deployment,
  })
}

export function getDeployment(deploymentId: string) {
  return request<DeploymentResponse>(`/api/deployments/${deploymentId}`)
}

export function getDeploymentLogs(deploymentId: string) {
  return request<DeploymentLogResponse[]>(`/api/deployments/${deploymentId}/logs`)
}

export function stopDeployment(deploymentId: string) {
  return request<DeploymentResponse>(`/api/deployments/${deploymentId}/stop`, {
    method: 'POST',
  })
}
