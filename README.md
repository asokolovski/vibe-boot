# vibe-boot

Fake deployment orchestration platform built with Spring Boot and PostgreSQL.

## Run Locally

Create a `.env` file with your PostgreSQL connection values:

```bash
DB_URL=jdbc:postgresql://localhost:5432/vibe_boot
DB_USERNAME=your_username
DB_PASSWORD=your_password
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

## Manual API Testing

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

### Get One Deployment

```bash
curl -i http://localhost:8080/api/deployments/PASTE_DEPLOYMENT_ID_HERE
```

Expected result: `200 OK` with the deployment response.

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
