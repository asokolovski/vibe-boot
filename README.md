# vibe-boot

Fake deployment orchestration platform built with Spring Boot, PostgreSQL, and RabbitMQ.

## V2 RabbitMQ Deployment Queue

V1 used a local Spring `@Async` worker to fake deployment execution inside the same Spring Boot process.

V2 keeps the API behavior the same, but changes the internal job handoff:

- RabbitMQ is now the deployment job queue / message broker.
- `DeploymentQueuePublisher` publishes deployment IDs.
- `DeploymentQueueConsumer` listens for deployment messages.
- `DeploymentExecutor` owns the fake deployment lifecycle.
- Postgres remains the source of truth for projects, deployments, and deployment logs when logs are added.

The key idea:

```text
V1: run this method in the background inside this JVM
V2: publish this job to RabbitMQ so a listener can process it
```

### V2 Architecture

```text
HTTP request
-> DeploymentController
-> DeploymentService
-> Postgres saves deployment as QUEUED
-> DeploymentQueuePublisher
-> RabbitMQ exchange/routing key/queue
-> DeploymentQueueConsumer
-> DeploymentExecutor
-> Postgres updates deployment to RUNNING then SUCCESS/FAILED
```

`POST /api/deployments` still returns quickly with `status: QUEUED`. The deployment work then happens on RabbitMQ listener threads, not Tomcat request threads.

### RabbitMQ Topology

```text
Exchange:    deployment.exchange
Routing key: deployment.requested
Queue:       deployment.requested.queue

Binding:
deployment.exchange + deployment.requested -> deployment.requested.queue
```

Plain English:

- Exchange: where deployment messages are published.
- Routing key: the label for the message type.
- Binding: the rule that routes matching messages into the queue.
- Queue: holds deployment jobs until a consumer receives them.
- Consumer: receives messages on RabbitMQ listener threads and calls `DeploymentExecutor`.

The message body is only the `deploymentId`, not the full deployment object. `DeploymentExecutor` reloads the deployment from Postgres before changing state.

### V2 Design Notes

- RabbitMQ is not the source of truth.
- Postgres is the source of truth.
- RabbitMQ only dispatches deployment work.
- Messages contain deployment IDs, not full deployment objects.
- `DeploymentExecutor` reloads deployment state from Postgres.
- Multiple deployments can be processed concurrently because the RabbitMQ listener uses concurrency `2-4`.
- Future improvements could add retries, dead-letter queues, delayed retries, or a separate worker service.

## Run Locally

### Postgres

Create a local PostgreSQL database for the app. The README assumes the database is available at `localhost:5432`.

Example database name:

```text
vibe_boot
```

### RabbitMQ

Start RabbitMQ:

```bash
sudo service rabbitmq-server start
```

Check RabbitMQ status:

```bash
sudo service rabbitmq-server status
```

Check that RabbitMQ is listening on port `5672`:

```bash
ss -ltn | grep ':5672'
```

Enable the management UI:

```bash
sudo rabbitmq-plugins enable rabbitmq_management
sudo service rabbitmq-server restart
ss -ltn | grep ':15672'
```

Open:

```text
http://localhost:15672
```

Default local login:

```text
guest / guest
```

### Spring Configuration

Create a `.env` file with your PostgreSQL and RabbitMQ connection values:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vibe_boot
DB_USERNAME=your_username
DB_PASSWORD=your_password
RABBITMQ_HOST=localhost
RABBITMQ_PORT=5672
RABBITMQ_USERNAME=guest
RABBITMQ_PASSWORD=guest
```

These map to the Spring RabbitMQ properties:

```properties
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest
```

### Start The App

```bash
./run-local.sh
```

The API runs at:

```text
http://localhost:8080
```

## Run Tests

```bash
./gradlew test
```

## Docs

- [V2 RabbitMQ Job Queue Architecture Note](docs/v2-rabbitmq-job-queue.md)

## Manual V2 Test Flow

Make sure these are running before testing:

- Postgres
- RabbitMQ
- Spring Boot app

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
  "finishedAt": null
}
```

Copy the deployment `id`; this is your `DEPLOYMENT_ID`.

Behind the scenes, this publishes the deployment ID to RabbitMQ.

### Poll Deployment Status

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE
```

Expected lifecycle:

```text
QUEUED -> RUNNING -> SUCCESS/FAILED
```

The final status can be either `SUCCESS` or `FAILED` because the fake executor chooses randomly.

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

Expected result for both: an error response because the service checks that the project exists first. A later phase can add cleaner API error handling.

### Missing Deployment Case

```bash
curl -i http://localhost:8080/api/deployments/00000000-0000-0000-0000-000000000000
```

Expected result: an error response because that deployment does not exist. A later phase can add cleaner API error handling.

## RabbitMQ UI Verification

Open the management UI:

```text
http://localhost:15672
```

Useful places to inspect:

- Queues
- Messages ready
- Messages unacked
- Consumers
- Publish/ack rates

Plain English:

- Ready: messages waiting in the queue.
- Unacked: messages delivered to a consumer but not acknowledged yet.
- Consumers: active listener workers connected to the queue.
- Publish rate: how quickly messages are being published.
- Ack rate: how quickly messages are being acknowledged as handled.

For a healthy local V2 flow, `deployment.requested.queue` should usually drain quickly because the consumer receives the deployment ID and calls `DeploymentExecutor`.

## V2 Definition Of Done

- RabbitMQ runs locally.
- App connects to RabbitMQ.
- Queue, exchange, and binding are declared on startup.
- `POST /api/deployments` saves a `QUEUED` deployment.
- `DeploymentQueuePublisher` sends the deployment ID to RabbitMQ.
- `DeploymentQueueConsumer` receives the message.
- `DeploymentExecutor` updates status to `RUNNING`, then `SUCCESS` or `FAILED`.
- The old direct `@Async` worker path is removed.
- This README explains how to run and test V2.
