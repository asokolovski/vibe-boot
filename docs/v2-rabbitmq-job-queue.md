# V2 RabbitMQ Job Queue Architecture Note

## Goal

V2 changes how a deployment job is handed off for background work.

The user-facing API stays mostly the same:

```text
POST /api/deployments
-> returns QUEUED quickly
-> status later becomes RUNNING
-> status later becomes SUCCESS or FAILED
```

The internal architecture changes from local Spring `@Async` execution to a RabbitMQ-backed job queue.

## V1 Flow

Current V1 flow:

```text
POST /api/deployments
-> DeploymentService verifies the project exists
-> DeploymentService saves a Deployment as QUEUED
-> DeploymentService calls @Async DeploymentWorker.runDeployment(deploymentId)
-> API returns the queued DeploymentResponse
-> DeploymentWorker marks the deployment RUNNING
-> DeploymentWorker simulates deployment work
-> DeploymentWorker marks the deployment SUCCESS or FAILED
```

In V1, `DeploymentWorker` is still part of the same Spring Boot application process. The `@Async` annotation means Spring runs the method through an executor thread instead of making the HTTP request wait for the method to finish.

That is useful, but it is local to this JVM. If this app process stops, its in-memory async work stops with it.

## V2 Flow

Target V2 flow:

```text
POST /api/deployments
-> DeploymentService verifies the project exists
-> DeploymentService saves a Deployment as QUEUED
-> DeploymentService asks DeploymentQueuePublisher to publish the deploymentId
-> DeploymentQueuePublisher sends a message to RabbitMQ
-> API returns the queued DeploymentResponse

RabbitMQ
-> stores the deployment job message in a queue
-> delivers the message to a consumer

DeploymentQueueConsumer
-> receives the deploymentId message
-> calls DeploymentExecutor

DeploymentExecutor
-> loads the Deployment
-> marks the deployment RUNNING
-> performs the deployment work
-> marks the deployment SUCCESS or FAILED
```

The important change is the handoff point:

```text
V1: DeploymentService -> @Async DeploymentWorker
V2: DeploymentService -> DeploymentQueuePublisher -> RabbitMQ -> DeploymentQueueConsumer -> DeploymentExecutor
```

## Why RabbitMQ

We are not adding RabbitMQ because it is trendy(kind of lol). We are adding it because it gives the app an external job handoff mechanism.

Spring `@Async` means:

```text
Run this method in the background inside this JVM.
```

RabbitMQ means:

```text
Publish this job to a broker so some worker can process it.
```

RabbitMQ's Work Queues tutorial describes this pattern as scheduling time-consuming work to be done later by sending a task message to a queue. A worker then receives the task and executes it. This is a good fit for deployments because an HTTP request should not have to stay open while deployment work runs.

RabbitMQ's queue documentation describes a queue as a place where messages are added by publishers and delivered to consumers. In V2, the deployment job message is just the `deploymentId`.

## What Stays The Same

- `POST /api/deployments` still returns quickly.
- The initial deployment status is still `QUEUED`.
- The API response shape does not change.
- Clients can still poll `GET /api/deployments/{deploymentId}`.
- Deployment status still moves from `QUEUED` to `RUNNING`, then to `SUCCESS` or `FAILED`.
- The fake deployment execution can stay fake for now.

## What Code Paths Will Change Later

- `DeploymentService` will stop calling `DeploymentWorker.runDeployment(...)` directly.
- `DeploymentService` will call a new `DeploymentQueuePublisher`.
- A RabbitMQ configuration class will define the deployment queue.
- A new `DeploymentQueueConsumer` will receive deployment job messages.
- A new `DeploymentExecutor` will own the actual status transitions and fake execution work.
- `DeploymentWorker` and `AsyncConfig` can eventually be removed once RabbitMQ fully replaces local `@Async` processing.

## References

- RabbitMQ Work Queues tutorial for Spring AMQP: https://www.rabbitmq.com/tutorials/tutorial-two-spring-amqp
- RabbitMQ queues overview: https://www.rabbitmq.com/docs/queues
- Spring `@Async` Javadoc: https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/scheduling/annotation/Async.html
