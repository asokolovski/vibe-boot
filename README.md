# vibe-boot

Local deployment orchestration platform built with Spring Boot, PostgreSQL, RabbitMQ, and Docker.

## Run Locally

Create a `.env` file with your PostgreSQL connection values:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vibe_boot
DB_USERNAME=your_username
DB_PASSWORD=your_password
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

Start the app:

```bash
./run-local.sh
```

The API runs at `http://localhost:8080`.

## Run Tests

```bash
./gradlew test
```

## Docs

- [V2 RabbitMQ Job Queue Architecture Note](docs/v2-rabbitmq-job-queue.md)
- [V3 Docker Integration Architecture Note](docs/v3-docker-integration.md)

## API Endpoints

```text
POST /api/projects
GET  /api/projects
GET  /api/projects/{projectId}/deployments

POST /api/deployments
GET  /api/deployments/{deploymentId}
GET  /api/deployments/{deploymentId}/logs
POST /api/deployments/{deploymentId}/stop
```

## Manual API Testing

### Create A Project

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "sample-app",
    "repositoryUrl": "local",
    "branch": "main",
    "runCommand": "unused-for-v3",
    "localPath": "/home/alexei/projects/vibe-boot/sample-app",
    "dockerfilePath": "Dockerfile",
    "containerPort": 8080,
    "healthCheckPath": "/health"
  }'
```

Expected result: `201 Created` with a project response. Copy the `id`; this is your `PROJECT_ID`.

### List Projects

```bash
curl -i http://localhost:8080/api/projects
```

Expected result: `200 OK` with an array of projects.

### Trigger A Deployment

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "PASTE_PROJECT_ID_HERE"
  }'
```

Expected result: `201 Created` with a deployment response:

```json
{
  "id": "deployment-id",
  "projectId": "project-id",
  "status": "QUEUED",
  "createdAt": "2026-05-15T00:00:00Z",
  "startedAt": null,
  "finishedAt": null,
  "imageName": null,
  "containerId": null,
  "hostPort": null,
  "containerPort": null,
  "deploymentUrl": null
}
```

Copy the deployment `id`; this is your `DEPLOYMENT_ID`.

### Get One Deployment

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE
```

Expected result: `200 OK` with the deployment response. After the worker builds and runs the Docker container, the deployment should eventually include Docker runtime fields:

```json
{
  "status": "SUCCESS",
  "imageName": "vibeboot-sample-app:deployment-id",
  "containerId": "container-id",
  "hostPort": 49152,
  "containerPort": 8080,
  "deploymentUrl": "http://localhost:49152"
}
```

### Get Deployment Logs

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE/logs
```

Expected result: `200 OK` with deployment timeline logs, including Docker build, container start, health check, success/failure, and stop messages.

### Open The Deployed App

Use the `deploymentUrl` from the deployment response:

```bash
curl -i http://localhost:49152/health
```

Expected result for the sample app: `200 OK`.

### Stop A Deployment

```bash
curl -i -X POST http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE/stop
```

Expected result: `200 OK` with status `STOPPED`. The deployment logs should include `Docker container stopped` and `Deployment stopped`.

### List Deployments For A Project

Create two deployments for the same project, then run:

```bash
curl -i http://localhost:8080/api/projects/PASTE_PROJECT_ID_HERE/deployments
```

Expected result: `200 OK` with an array of deployments for that project, newest first.

### Project With No Deployments

Create a second project, but do not trigger any deployments for it. Then run:

```bash
curl -i http://localhost:8080/api/projects/PASTE_SECOND_PROJECT_ID_HERE/deployments
```

Expected result: `200 OK` with an empty array:

```json
[]
```

### Missing Project Cases

Triggering a deployment for a project that does not exist:

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "00000000-0000-0000-0000-000000000000"
  }'
```

Listing deployments for a project that does not exist:

```bash
curl -i http://localhost:8080/api/projects/00000000-0000-0000-0000-000000000000/deployments
```

Expected result for both: an error response because the service checks that the project exists first.

### Missing Deployment Case

```bash
curl -i http://localhost:8080/api/deployments/00000000-0000-0000-0000-000000000000
```

Expected result: an error response because that deployment does not exist.
