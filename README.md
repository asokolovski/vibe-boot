# vibe-boot

Local deployment orchestration platform built with Spring Boot, PostgreSQL,
RabbitMQ, Git, Docker, and a React frontend.

This branch adds two major capabilities on top of the earlier deployment flow:

- Manual GitHub OAuth login backed by server-side HTTP sessions
- User-owned projects and deployments
- GitHub Container Registry (`ghcr.io`) image deployment support

Vibe Boot now supports two project source types:

- `GITHUB_REPOSITORY`: clone a public GitHub repository, build its Docker image,
  and run it
- `CONTAINER_IMAGE`: pull a public `ghcr.io` image and run it directly

Every authenticated user sees only their own projects, environment variables,
deployments, and deployment logs.

## What This Branch Does

The platform accepts either:

- A public GitHub repository URL such as
  `https://github.com/owner/repository`
- A public GitHub Container Registry image path such as
  `ghcr.io/owner/image`

For repository-backed projects, Vibe Boot:

- creates a temporary workspace
- clones the configured GitHub repository into `workspace/source`
- builds a Docker image from the repository's Dockerfile
- decrypts the project's runtime environment variables
- starts the container
- runs a health check
- records logs and deployment status

For container-image-backed projects, Vibe Boot:

- skips workspace creation and Git clone/build steps
- pulls the configured `ghcr.io` image
- optionally appends an image tag or digest at deploy time
- decrypts the project's runtime environment variables
- starts the container
- runs a health check
- records logs and deployment status

## Manual Auth Flow

This branch uses a custom GitHub OAuth flow instead of Spring Security's
built-in OAuth integration.

The flow is:

1. The frontend sends the browser to `/auth/github/login`.
2. The backend redirects the browser to GitHub's OAuth authorize page.
3. GitHub redirects back to `/auth/github/callback?code=...`.
4. The backend exchanges the code for an access token.
5. The backend fetches the GitHub user profile.
6. The backend creates or updates a local `users` row.
7. The backend stores the local user UUID in the server-side HTTP session.
8. The frontend calls `/api/me` to learn who is logged in.

All `/api/**` routes are protected by a manual MVC interceptor. If the session
does not contain a logged-in user ID, the backend returns `401 Unauthorized`.

## User Ownership Model

This branch introduces a local `users` table and attaches every project to the
logged-in user's UUID.

That means:

- `GET /api/projects` returns only the current user's projects
- project environment variables are scoped to the current user's projects
- deployments can only be triggered for the current user's projects
- deployment status, stop, and logs endpoints are ownership-checked

If a project or deployment exists but belongs to another user, the current user
does not get access to it.

## Supported Scope

This branch supports:

- Manual GitHub OAuth login
- Session-based authentication
- Local user persistence from GitHub identity
- User-owned projects
- Public GitHub HTTPS repositories
- Public GitHub Container Registry image paths under `ghcr.io`
- Configurable repository branches
- Configurable Dockerfile paths, container ports, and health-check paths
- Optional deploy-time image tags and image digests for container-image projects
- Encrypted project environment variables
- Temporary cloned deployment workspaces for repository-backed projects
- Docker pull, build, run, logs, stop, and health-check behavior
- Deployment logs and status history

This branch does not support:

- private GitHub repositories
- private container registries
- arbitrary Docker registries outside `ghcr.io`
- Kubernetes
- production-grade secret management
- CSRF/state validation in the OAuth flow
- multi-factor or enterprise auth flows

## Requirements

Install and run:

- Java 21
- Git CLI
- Docker CLI and Docker daemon
- PostgreSQL
- RabbitMQ

## GitHub OAuth Setup

Create a GitHub OAuth App and configure its callback URL to match this branch's
manual callback endpoint:

```text
http://localhost:8080/auth/github/callback
```

This branch does not use Spring Security's default callback route. It expects
GitHub to redirect to `/auth/github/callback`.

The app also needs:

- `GITHUB_CLIENT_ID`
- `GITHUB_CLIENT_SECRET`

The default authorize URI and scope are:

- `https://github.com/login/oauth/authorize`
- `read:user,user:email`

## Run Locally

Generate a Base64-encoded 32-byte encryption key:

```bash
openssl rand -base64 32
```

Create a `.env` file:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vibe_boot
DB_USERNAME=your_username
DB_PASSWORD=your_password
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
VIBEBOOT_ENCRYPTION_KEY=paste_the_generated_key_here
GITHUB_CLIENT_ID=your_github_oauth_app_client_id
GITHUB_CLIENT_SECRET=your_github_oauth_app_client_secret
GITHUB_REDIRECT_URI=http://localhost:8080/auth/github/callback
```

Optional GitHub OAuth overrides:

```bash
GITHUB_AUTHORIZATION_URI=https://github.com/login/oauth/authorize
GITHUB_SCOPE=read:user,user:email
```

Keep `VIBEBOOT_ENCRYPTION_KEY` private and stable. Changing or losing it means
Vibe Boot will no longer be able to decrypt environment variables previously
stored with that key.

Start the backend:

```bash
./run-local.sh
```

The backend runs at `http://localhost:8080`.

To run the frontend separately during development:

```bash
cd frontend
npm ci
npm run dev
```

The Vite dev server proxies `/api` and `/auth` to `http://localhost:8080`, so
the browser can use the same-origin frontend URL without extra CORS setup in
local development.

## Deployment Flows

### Repository-Backed Deployment

```text
POST /api/deployments
    -> create a QUEUED deployment
    -> publish the deployment ID through RabbitMQ
    -> create a temporary workspace
    -> clone the configured GitHub repository into workspace/source
    -> build a Docker image from workspace/source
    -> allocate an available host port
    -> decrypt the project's environment variables
    -> start the Docker container
    -> run the configured health check
    -> mark the deployment SUCCESS or FAILED
    -> delete the temporary workspace
```

### Container-Image Deployment

```text
POST /api/deployments
    -> create a QUEUED deployment
    -> publish the deployment ID through RabbitMQ
    -> pull the configured ghcr.io image
    -> allocate an available host port
    -> decrypt the project's environment variables
    -> start the Docker container
    -> run the configured health check
    -> mark the deployment SUCCESS or FAILED
```

For container-image projects, the image name is built from:

- the project's `containerRegistry`, for example `ghcr.io/owner/app`
- the request's optional `imageTag`
- `latest` when `imageTag` is omitted

Examples:

```text
ghcr.io/owner/app:latest
ghcr.io/owner/app:main
ghcr.io/owner/app:sha-4a928d5
ghcr.io/owner/app@sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
```

## Workspace Layout

Each repository-backed deployment receives its own workspace:

```text
/tmp/vibeboot-workspaces/
└── deployment-<deployment-id>-<random-number>/
    └── source/
        ├── Dockerfile
        └── cloned repository files
```

The workspace gives Git somewhere to clone the repository and gives Docker a
real filesystem directory from which it can run `docker build .`. The workspace
is deleted after the deployment finishes, while the built Docker image and a
successful deployment's running container remain.

## Run Tests

The automated tests mock Git and Docker behavior and do not require real GitHub
or Docker access:

```bash
./gradlew test
```

## API Endpoints

Authentication:

```text
GET    /auth/github/login
GET    /auth/github/callback
GET    /api/me
POST   /auth/logout
```

Projects:

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{projectId}/deployments
```

Project environment variables:

```text
POST   /api/projects/{projectId}/env
GET    /api/projects/{projectId}/env
DELETE /api/projects/{projectId}/env/{envId}
```

Deployments:

```text
POST   /api/deployments
GET    /api/deployments/{deploymentId}
GET    /api/deployments/{deploymentId}/logs
POST   /api/deployments/{deploymentId}/stop
```

## Manual API Testing

Because `/api/**` routes require a logged-in session, the easiest way to test
manually is to use the browser for login first and then reuse the session
cookie with `curl`.

### 1. Login Through GitHub

Open:

```text
http://localhost:8080/auth/github/login
```

After approving the OAuth app, GitHub redirects back to:

```text
http://localhost:8080/auth/github/callback?code=...
```

The backend then redirects to `/` and stores your local user ID in the session.

### 2. Inspect The Current User

In the browser or with an authenticated session cookie:

```bash
curl -i http://localhost:8080/api/me
```

Expected authenticated response shape:

```json
{
  "authenticated": true,
  "id": "user-uuid",
  "githubId": 12345678,
  "githubUsername": "octocat",
  "name": "The Octocat",
  "email": "octocat@example.com",
  "avatarUrl": "https://avatars.githubusercontent.com/u/12345678?v=4"
}
```

### 3. Create A Repository-Backed Project

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "systematic-trading-engine",
    "sourceType": "GITHUB_REPOSITORY",
    "repositoryUrl": "https://github.com/asokolovski/systematic-trading-engine",
    "containerPort": 8000
  }'
```

Expected result: `201 Created`.

Defaults when omitted:

```text
sourceType      = GITHUB_REPOSITORY
branch          = main
dockerfilePath  = Dockerfile
containerPort   = 8080
healthCheckPath = /health
```

### 4. Create A Container-Image Project

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "vb-ghcr-demo",
    "sourceType": "CONTAINER_IMAGE",
    "containerRegistry": "ghcr.io/asokolovski/vb-gha-demo-app",
    "containerPort": 3000,
    "healthCheckPath": "/health"
  }'
```

For `CONTAINER_IMAGE` projects:

- `containerRegistry` is required
- it must match a public `ghcr.io/...` path
- `repositoryUrl` is optional in the request
- the backend derives a GitHub repository URL for persistence when possible

### 5. List Your Projects

```bash
curl -i http://localhost:8080/api/projects
```

The response includes only projects owned by the current logged-in user.

### 6. Add A Project Environment Variable

```bash
curl -i -X POST http://localhost:8080/api/projects/PROJECT_ID/env \
  -H "Content-Type: application/json" \
  -d '{
    "key": "APP_ENV",
    "value": "production"
  }'
```

Expected result: `201 Created` with environment-variable metadata.

Keys must match:

```text
[A-Z_][A-Z0-9_]*
```

The plaintext value is encrypted before it is stored in PostgreSQL.

### 7. List Project Environment Variables

```bash
curl -i http://localhost:8080/api/projects/PROJECT_ID/env
```

The response includes IDs, keys, and creation timestamps. It never returns
plaintext or encrypted secret values.

### 8. Trigger A Repository Deployment

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "PROJECT_ID"
  }'
```

Expected result: `201 Created` with a `QUEUED` deployment.

### 9. Trigger A GHCR Deployment With A Tag

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "PROJECT_ID",
    "imageTag": "sha-4a928d5"
  }'
```

For container-image projects:

- omit `imageTag` to use `latest`
- pass a normal Docker tag such as `main` or `sha-4a928d5`
- or pass a digest like `sha256:<64-hex>`

### 10. Get Deployment Status

```bash
curl -i http://localhost:8080/api/deployments/DEPLOYMENT_ID
```

A successful deployment eventually resembles:

```json
{
  "id": "deployment-id",
  "projectId": "project-id",
  "status": "SUCCESS",
  "imageName": "ghcr.io/asokolovski/vb-gha-demo-app:sha-4a928d5",
  "containerId": "container-id",
  "hostPort": 49152,
  "containerPort": 3000,
  "deploymentUrl": "http://localhost:49152"
}
```

### 11. Get Deployment Logs

```bash
curl -i http://localhost:8080/api/deployments/DEPLOYMENT_ID/logs
```

Typical successful repository-backed logs include:

```text
Deployment started
Created workspace
Cloning repository
Repository cloned successfully
Building Docker image
Docker image built
Loading project environment variables
Starting Docker container
Running health check
Deployment succeeded
Workspace cleaned up
```

Typical successful container-image-backed logs include:

```text
Deployment started
Pulling Docker image
Loading project environment variables
Starting Docker container
Running health check
Deployment succeeded
```

### 12. Open The Deployed App

Use the `deploymentUrl` returned by the deployment:

```bash
curl -i http://localhost:49152/health
```

### 13. Confirm Runtime Environment Variable Injection

After adding `APP_ENV=production` and successfully deploying, use the returned
container ID:

```bash
docker exec CONTAINER_ID printenv APP_ENV
```

Expected output:

```text
production
```

This confirms that Vibe Boot decrypted the stored value and passed it into the
container at runtime.

### 14. Stop A Deployment

```bash
curl -i -X POST \
  http://localhost:8080/api/deployments/DEPLOYMENT_ID/stop
```

Expected result: `200 OK` with status `STOPPED`.

### 15. Logout

```bash
curl -i -X POST http://localhost:8080/auth/logout
```

Expected result: `204 No Content`.

## Request Validation Rules

### Repository-Backed Projects

- `sourceType` defaults to `GITHUB_REPOSITORY`
- `repositoryUrl` must match:

```text
https://github.com/owner/repository
```

### Container-Image Projects

- `sourceType` must be `CONTAINER_IMAGE`
- `containerRegistry` must match:

```text
ghcr.io/owner/image
ghcr.io/owner/image/subpath
```

### Shared Rules

- `dockerfilePath` must be relative and must not contain `..`
- `healthCheckPath` must start with `/`
- `containerPort` must be between `1` and `65535`
- deployment `imageTag` must be either a valid Docker tag or a
  `sha256:<64-hex>` digest

## Environment Variable Safety

Project environment-variable values are:

- encrypted before being stored in PostgreSQL
- never returned by the env-var GET API
- decrypted only when a deployment prepares to start its container
- passed to Docker as runtime environment variables

This encryption is intended for a local educational project. It is not a
replacement for a production secret manager. Runtime values can still be
visible through Docker inspection to users with access to the Docker daemon.

## Common Failures

### Missing Or Invalid Encryption Key

Vibe Boot cannot start unless `VIBEBOOT_ENCRYPTION_KEY` is valid Base64 that
decodes to exactly 32 bytes.

Generate one with:

```bash
openssl rand -base64 32
```

### GitHub OAuth Misconfiguration

If login fails immediately, verify:

- the GitHub OAuth app callback URL is exactly
  `http://localhost:8080/auth/github/callback`
- `GITHUB_CLIENT_ID` and `GITHUB_CLIENT_SECRET` match the GitHub app
- `GITHUB_REDIRECT_URI` matches the registered callback URL

### Unauthenticated API Calls

All `/api/**` routes require a valid logged-in session. Without one, the
backend returns `401 Unauthorized`.

### Invalid GitHub Repository URL

Repository-backed projects require a public GitHub HTTPS URL shaped like:

```text
https://github.com/owner/repository
```

SSH URLs, private repositories, and non-GitHub repositories are not supported.

### Invalid GHCR Image Path

Container-image projects currently require a public GitHub Container Registry
path shaped like:

```text
ghcr.io/owner/image
```

Other registries are not supported by this branch.

### Missing Branch

Repository-backed deployments run `git clone` using the project's configured
branch. A deployment fails during cloning if that branch does not exist. The
default branch is `main`, so projects using another default branch must provide
it explicitly.

### Missing Git Or Docker CLI

Vibe Boot starts `git` and `docker` as external operating-system processes.
Both commands must be installed and available on the application's `PATH`.

### Invalid Dockerfile Path

`dockerfilePath` must be relative to the cloned repository and cannot contain
`..` path traversal. The default is `Dockerfile`.

### Docker Pull Failure

For container-image projects, inspect deployment logs for Docker pull output.
Common causes include a missing tag, missing digest, package visibility issues,
or an invalid `ghcr.io` image path.

### Docker Build Failure

For repository-backed projects, inspect deployment logs for Docker build output.
Common causes include a missing Dockerfile, invalid build instructions, and
dependency-download failures.

### Docker Run Failure

Inspect deployment logs for Docker output. Common causes include unavailable
ports, invalid images, and container-name conflicts.

### Failed Health Check

The application must listen on its configured `containerPort` and return a
successful HTTP response from its configured `healthCheckPath`. If it does not
become healthy in time, Vibe Boot collects its container logs, stops the
container, and marks the deployment `FAILED`.

## Docs

- [V2 RabbitMQ Job Queue Architecture Note](docs/v2-rabbitmq-job-queue.md)
- [V3 Docker Integration Architecture Note](docs/v3-docker-integration.md)
