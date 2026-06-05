# vibe-boot

Local deployment orchestration platform built with Spring Boot, PostgreSQL,
RabbitMQ, Git, and Docker.

Vibe Boot accepts a public GitHub repository, clones it into a temporary
workspace, builds a Docker image from the cloned source, starts the container
with the project's runtime environment variables, and health-checks the
deployed application.

## V3.5 Deployment Flow

```text
POST /api/deployments
    -> create a QUEUED deployment
    -> publish the deployment ID through RabbitMQ
    -> create a temporary workspace
    -> clone the configured GitHub repository into workspace/source
    -> build a Docker image from workspace/source
    -> allocate an available host port
    -> decrypt the project's environment variables
    -> start the Docker container with those environment variables
    -> run the configured health check
    -> mark the deployment SUCCESS or FAILED
    -> delete the temporary workspace
```

Each deployment receives its own workspace:

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

## V3.5 Scope

V3.5 supports:

- Public GitHub HTTPS repositories
- Configurable repository branches
- Configurable Dockerfile paths, container ports, and health-check paths
- Encrypted project environment variables
- Temporary cloned deployment workspaces
- Docker build, run, logs, stop, and health-check behavior
- Deployment logs and status history

V3.5 does not support private repositories, GitHub OAuth, deploy keys, users,
Kubernetes, reverse proxies, or production-grade secret management.

## Requirements

Install and run:

- Java
- Git CLI
- Docker CLI and Docker daemon
- PostgreSQL
- RabbitMQ

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
```

Keep `VIBEBOOT_ENCRYPTION_KEY` private and stable. Changing or losing it means
Vibe Boot will no longer be able to decrypt environment variables previously
stored with that key.

Start the app:

```bash
./run-local.sh
```

The API runs at `http://localhost:8080`.

## Run Tests

The automated tests mock Git and Docker behavior and do not require real GitHub
or Docker access:

```bash
./gradlew test
```

## API Endpoints

```text
POST   /api/projects
GET    /api/projects
GET    /api/projects/{projectId}/deployments

POST   /api/projects/{projectId}/env
GET    /api/projects/{projectId}/env
DELETE /api/projects/{projectId}/env/{envId}

POST   /api/deployments
GET    /api/deployments/{deploymentId}
GET    /api/deployments/{deploymentId}/logs
POST   /api/deployments/{deploymentId}/stop
```

## Manual API Testing

The examples below deploy the public
`https://github.com/asokolovski/systematic-trading-engine` repository. It uses
the `main` branch, a root `Dockerfile`, container port `8000`, and `/health`.

### Create A Project

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "systematic-trading-engine",
    "repositoryUrl": "https://github.com/asokolovski/systematic-trading-engine",
    "containerPort": 8000
  }'
```

Expected result: `201 Created`.

Copy the returned project `id`; it is used as `PROJECT_ID` below.

When omitted, project settings receive these defaults:

```text
branch          = main
dockerfilePath  = Dockerfile
containerPort   = 8080
healthCheckPath = /health
```

Projects are deployed from their configured GitHub repository. Local filesystem
paths and run commands are no longer part of the project API.

### Add A Project Environment Variable

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

### List Project Environment Variables

```bash
curl -i http://localhost:8080/api/projects/PROJECT_ID/env
```

The response includes IDs, keys, and creation timestamps. It never returns
plaintext or encrypted secret values.

### Delete A Project Environment Variable

```bash
curl -i -X DELETE \
  http://localhost:8080/api/projects/PROJECT_ID/env/ENV_ID
```

Expected result: `204 No Content`.

### List Projects

```bash
curl -i http://localhost:8080/api/projects
```

### Trigger A Deployment

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "PROJECT_ID"
  }'
```

Expected result: `201 Created` with a `QUEUED` deployment:

```json
{
  "id": "deployment-id",
  "projectId": "project-id",
  "status": "QUEUED",
  "imageName": null,
  "containerId": null,
  "hostPort": null,
  "containerPort": null,
  "deploymentUrl": null
}
```

Copy the returned deployment `id`; it is used as `DEPLOYMENT_ID` below.

### Get Deployment Status

```bash
curl -i http://localhost:8080/api/deployments/DEPLOYMENT_ID
```

A successful deployment eventually resembles:

```json
{
  "status": "SUCCESS",
  "imageName": "vibeboot-systematic-trading-engine:deployment-id",
  "containerId": "container-id",
  "hostPort": 49152,
  "containerPort": 8000,
  "deploymentUrl": "http://localhost:49152"
}
```

### Get Deployment Logs

```bash
curl -i http://localhost:8080/api/deployments/DEPLOYMENT_ID/logs
```

Deployment logs show each major step and include useful Git and Docker command
output. Typical successful logs include:

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

### Open The Deployed App

Use the `deploymentUrl` returned by the deployment:

```bash
curl -i http://localhost:49152/health
```

### Confirm Runtime Environment Variable Injection

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

### Stop A Deployment

```bash
curl -i -X POST \
  http://localhost:8080/api/deployments/DEPLOYMENT_ID/stop
```

Expected result: `200 OK` with status `STOPPED`.

### List Deployments For A Project

```bash
curl -i http://localhost:8080/api/projects/PROJECT_ID/deployments
```

## Environment Variable Safety

Project environment-variable values are:

- Encrypted before being stored in PostgreSQL
- Never returned by the env-var GET API
- Decrypted only when a deployment prepares to start its container
- Passed to Docker as runtime environment variables

This encryption is intended for a local educational project. It is not a
replacement for a production secret manager. Runtime values can still be
visible through Docker inspection to users with access to the Docker daemon.

## Common Deployment Failures

### Missing Or Invalid Encryption Key

Vibe Boot cannot start unless `VIBEBOOT_ENCRYPTION_KEY` is valid Base64 that
decodes to exactly 32 bytes.

Generate one with:

```bash
openssl rand -base64 32
```

### Invalid GitHub Repository URL

Projects currently require a public GitHub HTTPS URL shaped like:

```text
https://github.com/owner/repository
```

SSH URLs, private repositories, and non-GitHub repositories are not supported.

### Missing Branch

Vibe Boot runs `git clone` using the project's configured branch. A deployment
fails during cloning if that branch does not exist. The default branch is
`main`, so projects using another default branch must provide it explicitly.

### Missing Git Or Docker CLI

Vibe Boot starts `git` and `docker` as external operating-system processes.
Both commands must be installed and available on the application's `PATH`.

### Invalid Dockerfile Path

`dockerfilePath` must be relative to the cloned repository and cannot contain
`..` path traversal. The default is `Dockerfile`.

### Docker Build Failure

Inspect the deployment logs for Docker build output. Common causes include a
missing Dockerfile, invalid build instructions, and dependency-download
failures.

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
