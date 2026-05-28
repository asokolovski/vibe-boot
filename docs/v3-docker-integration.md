# V3 Docker Integration Architecture Note

## Goal

V3 changes deployment execution from a fake simulation into real local Docker work.

V2 answered this question:

```text
How do deployment jobs get delivered to background workers?
```

V3 answers the next question:

```text
How does the platform control runtime infrastructure?
```

The API and queue shape stay familiar, but the executor stops pretending. Instead of sleeping and randomly choosing `SUCCESS` or `FAILED`, it will build a Docker image, run a container, check whether the container is alive, and store the result.

## V2 Flow

Current V2 flow:

```text
POST /api/deployments
-> DeploymentService verifies the project exists
-> DeploymentService saves a Deployment as QUEUED
-> DeploymentService publishes the deploymentId to RabbitMQ
-> DeploymentQueueConsumer receives the message
-> DeploymentExecutor marks the deployment RUNNING
-> DeploymentExecutor fake-sleeps
-> DeploymentExecutor randomly marks SUCCESS or FAILED
```

In V2, RabbitMQ made the background job handoff more realistic, but the actual deployment work is still fake. `DeploymentExecutor` proves the state machine works, but it does not create infrastructure.

## V3 Flow

Target V3 flow:

```text
POST /api/deployments
-> DeploymentService verifies the project exists
-> DeploymentService saves a Deployment as QUEUED
-> DeploymentService publishes the deploymentId to RabbitMQ
-> DeploymentQueueConsumer receives the message
-> DeploymentExecutor marks the deployment RUNNING
-> DeploymentExecutor builds a Docker image
-> DeploymentExecutor runs a Docker container
-> DeploymentExecutor health-checks the running container
-> DeploymentExecutor marks SUCCESS or FAILED
```

The big change is inside `DeploymentExecutor`:

```text
V2: fake sleep + random result
V3: Docker build + Docker run + health check + real result
```

## System Responsibilities

### Postgres

Postgres remains the source of truth.

It stores:

- Projects
- Deployment attempts
- Deployment status
- Deployment timing metadata
- Later V3 metadata such as image name, container id, ports, deployment URL, and deployment logs

If RabbitMQ or Docker loses state, the application should still be able to look at Postgres and answer:

```text
What deployments exist, and what state are they in?
```

### RabbitMQ

RabbitMQ remains the job handoff system.

It is responsible for delivering deployment work from the HTTP request path to the background worker path:

```text
DeploymentService -> RabbitMQ -> DeploymentQueueConsumer -> DeploymentExecutor
```

RabbitMQ is not the source of truth for deployment status. The message body should stay small, usually just the `deploymentId`, because the executor can reload the latest deployment and project state from Postgres.

### Docker

Docker becomes the runtime infrastructure.

It is responsible for:

- Building an image for the project
- Running a container from that image
- Exposing the app on a local host port
- Providing container ids and runtime state
- Producing build and run output that can be saved as deployment logs

In V3, Docker is the thing that makes a deployment real. The Spring Boot app does not become the deployed application. The Spring Boot app becomes the control plane that tells Docker what to run.

## What V3 Adds

V3 adds the first real infrastructure boundary:

```text
Spring Boot controls Docker.
Docker runs user/project applications.
```

The platform will need to learn how to:

- Check whether Docker is available
- Build a known-good test app image
- Run a container
- Assign host ports
- Save Docker resource metadata
- Capture useful logs
- Health-check the deployed app
- Stop or clean up containers

## What V3 Replaces

The fake execution behavior gets replaced.

Removed or changed behavior:

- No more random deployment success/failure
- No more fake sleep as the main deployment work
- No more pretending a deployment ran just because the state machine completed

New behavior:

- A build can fail because Docker build failed
- A run can fail because Docker could not start the container
- A health check can fail because the app started but did not become reachable
- `SUCCESS` means the deployed container passed the platform's health check
- `FAILED` means a real infrastructure or application step failed

## Why This Matters

V1 taught the basic control-plane workflow:

```text
create project -> trigger deployment -> background work -> poll status
```

V2 taught job delivery:

```text
publish deploymentId -> queue -> consumer -> executor
```

V3 teaches orchestration:

```text
load desired deployment -> create runtime resources -> observe result -> persist status
```

That is the jump from a fake deployment tracker to a small local deployment platform.
