# vibe-boot

`vibe-boot` is a local fake deployment orchestration platform built with Spring Boot and PostgreSQL.

The goal is to learn how deployment platforms are structured by building one incrementally. V1 models the backend/control-plane of a deployment platform: projects are registered, deployments are triggered, background work updates deployment state, and clients poll for status.

V1 is called **Fake Deployment Orchestrator** or **Workflow Foundations**.

Important: V1 does not deploy real applications. There is no Docker, Kubernetes, Redis, Kafka, auth, GitHub integration, or frontend. Deployment execution is fake/simulated on purpose so the core workflow can be learned first.

## Project Overview

The core workflow is:

```text
create project -> trigger deployment -> async worker processes it -> poll status
```

The API stores projects and deployment attempts in PostgreSQL. When a deployment is triggered, the app immediately creates a `QUEUED` deployment record, starts background work, and returns the initial response. The client can then poll the deployment by id and watch the status change.

## V1 Goals

V1 lets a user:

- Create a project
- List projects
- Trigger a deployment for a project
- Fetch a deployment by id
- List deployment history for a project
- Watch deployment status move through `QUEUED -> RUNNING -> SUCCESS/FAILED`

## Tech Stack

- Java
- Spring Boot
- Spring Web MVC
- Spring Data JPA
- PostgreSQL
- Hibernate
- Jakarta Validation
- Gradle
- Spring async support with `@Async` and `ThreadPoolTaskExecutor`

## Core Domain Model

### Project

A `Project` represents an application or service that can be deployed.

Fields:

- `id`
- `name`
- `repositoryUrl`
- `branch`
- `runCommand`
- `createdAt`

### Deployment

A `Deployment` represents one deployment attempt for a project.

Fields:

- `id`
- `projectId`
- `status`
- `createdAt`
- `startedAt`
- `finishedAt`

A project can have many deployment records. Each deployment record is one attempt, so deployment history is preserved instead of overwriting the project.

## Deployment Lifecycle

Deployment status is the source of truth for the lifecycle.

- `QUEUED`: deployment record has been created and is waiting to run
- `RUNNING`: async worker has started processing it
- `SUCCESS`: fake deployment completed successfully
- `FAILED`: fake deployment failed

`startedAt` and `finishedAt` are timing metadata:

- `createdAt` is set when the deployment is triggered
- `startedAt` is set when the worker marks it `RUNNING`
- `finishedAt` is set when the worker marks it `SUCCESS` or `FAILED`

## Architecture

The main request flow is layered:

```text
Controller -> Service -> Repository -> PostgreSQL
```

Main components:

- `ProjectController`: handles project HTTP endpoints
- `ProjectService`: contains project application logic
- `ProjectRepository`: persists projects with Spring Data JPA
- `DeploymentController`: handles deployment HTTP endpoints
- `DeploymentService`: creates and reads deployments
- `DeploymentRepository`: persists deployments with Spring Data JPA
- `DeploymentWorker`: simulates deployment execution in the background
- `AsyncConfig`: enables async processing and defines `deploymentTaskExecutor`
- `ApiExceptionHandler`: returns clean JSON error responses

Deployment async flow:

```text
POST /api/deployments
-> DeploymentService creates deployment as QUEUED
-> DeploymentService saves it
-> DeploymentWorker runs in the background using @Async("deploymentTaskExecutor")
-> worker marks deployment RUNNING
-> worker waits to simulate work
-> worker marks deployment SUCCESS or FAILED
-> client polls GET /api/deployments/{id}
```

Spring creates an async proxy for `DeploymentWorker`. When another Spring bean calls `runDeployment(...)`, the proxy submits that work to the configured thread pool instead of running it on the HTTP request thread.

## API Endpoints

The API runs at `http://localhost:8080`.

### Create A Project

```bash
curl -i -X POST http://localhost:8080/api/projects \
  -H "Content-Type: application/json" \
  -d '{
    "name": "vibe-payment-api",
    "repositoryUrl": "https://github.com/alexeisoko/payment-api",
    "branch": "main",
    "runCommand": "./gradlew bootRun"
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
    "projectId": "project-uuid-here"
  }'
```

Expected result: `201 Created` with an initial queued deployment response:

```json
{
  "id": "deployment-uuid",
  "projectId": "project-uuid",
  "status": "QUEUED",
  "createdAt": "2026-05-18T18:27:30.868506Z",
  "startedAt": null,
  "finishedAt": null
}
```

Copy the deployment `id`; this is your `DEPLOYMENT_ID`.

### Poll A Deployment

Immediately after triggering, call:

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE
```

Then wait a few seconds and call it again:

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE
```

You should see the status move from `QUEUED` to `RUNNING`, then eventually to `SUCCESS` or `FAILED`.

Example final deployment response:

```json
{
  "id": "deployment-uuid",
  "projectId": "project-uuid",
  "status": "SUCCESS",
  "createdAt": "2026-05-18T18:27:30.868506Z",
  "startedAt": "2026-05-18T18:27:30.899397Z",
  "finishedAt": "2026-05-18T18:27:35.912832Z"
}
```

### List Deployments For A Project

Create one or more deployments for the same project, then run:

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

## Running Locally

Database configuration comes from environment variables.

Example:

```bash
export DB_URL=jdbc:postgresql://localhost:5432/vibeboot
export DB_USERNAME=your_username
export DB_PASSWORD=your_password
```

Do not commit real database passwords.

You can run the app directly:

```bash
./gradlew bootRun
```

Or create a local `.env` file:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vibeboot
DB_USERNAME=your_username
DB_PASSWORD=your_password
```

Then run:

```bash
./run-local.sh
```

The API runs at:

```text
http://localhost:8080
```

## Running Tests

```bash
./gradlew test
```

`VibeBootApplicationTests` currently has `@SpringBootTest` commented out. That keeps the fast test suite from starting the entire Spring application context and requiring real database environment variables.

Use `@SpringBootTest` later when you want an integration-style smoke test that verifies the full Spring context starts correctly. At that point, either provide test database configuration or use a dedicated test setup so local unit tests do not depend on a developer's real PostgreSQL database.

## Validation And Error Handling

V1 validates incoming requests:

- Project creation rejects blank `name`, `repositoryUrl`, `branch`, and `runCommand`
- Deployment trigger rejects a missing or null `projectId`

Invalid request bodies return `400 Bad Request`.

Missing resources return clean `404 Not Found` JSON instead of Spring's default error page.

Example missing deployment response:

```json
{
  "message": "Deployment not found"
}
```

Example invalid deployment trigger:

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": null
  }'
```

Expected result: `400 Bad Request` with a JSON error message.

Example missing project:

```bash
curl -i -X POST http://localhost:8080/api/deployments \
  -H "Content-Type: application/json" \
  -d '{
    "projectId": "00000000-0000-0000-0000-000000000000"
  }'
```

Expected result:

```json
{
  "message": "Project not found"
}
```

Example missing deployment:

```bash
curl -i http://localhost:8080/api/deployments/00000000-0000-0000-0000-000000000000
```

Expected result:

```json
{
  "message": "Deployment not found"
}
```

Example invalid deployment id format:

```bash
curl -i http://localhost:8080/api/deployments/fake-id
```

Expected result:

```json
{
  "message": "deploymentId must be a valid UUID"
}
```

## What V1 Intentionally Does Not Include

V1 intentionally excludes:

- Real deployments
- Docker
- Kubernetes
- Redis, RabbitMQ, or Kafka
- Authentication and authorization
- GitHub OAuth or webhooks
- Frontend dashboard
- Deployment logs table

Deployment logs are deferred until deployments have meaningful internal steps to log.

## Why V1 Matters

Even though deployment execution is fake, V1 is not just CRUD.

It includes:

- State transitions
- Async background work
- Persistent deployment history
- Polling
- Clean API boundaries
- A project/deployment domain model

This gives the app the basic shape of a real deployment control plane before adding heavier infrastructure.

## V2 Preview

V2 will likely replace simple in-process `@Async` execution with a more realistic queue/worker model, probably using Redis or RabbitMQ.

That would make deployment jobs less tied to the API process and closer to how production deployment platforms usually separate request handling from background execution.
