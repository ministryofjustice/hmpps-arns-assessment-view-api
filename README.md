# hmpps-arns-assessment-view-api

[![Ministry of Justice Repository Compliance Badge](https://github-community.service.justice.gov.uk/repository-standards/api/hmpps-arns-assessment-view-api/badge?style=flat)](https://github-community.service.justice.gov.uk/repository-standards/hmpps-arns-assessment-view-api)
[![Docker Repository on ghcr](https://img.shields.io/badge/ghcr.io-repository-2496ED.svg?logo=docker)](https://ghcr.io/ministryofjustice/hmpps-arns-assessment-view-api)
[![API docs](https://img.shields.io/badge/API_docs_-view-85EA2D.svg?logo=swagger)](https://arns-assessment-view-api-dev.hmpps.service.justice.gov.uk/swagger-ui/index.html)

## Description

A Spring Boot Kotlin service that maintains a PostgreSQL copy of sentence plan data from the ARNS Assessment Platform API (AAP-API) and serves it to consumers. It operates in two modes via Spring profiles:

- **API mode** (`api` profile): Always-on service that serves sentence plan data from the database to consumers.
- **Sync mode** (`sync` profile): K8s CronJob starts the app, it pulls sentence plan data from AAP-API into PostgreSQL, then exits.

The sync mode calls the Coordinator API to resolve OASys assessment PKs for each sentence plan, and queries AAP-API for assessments modified since the last sync window.

## Local Development

### Prerequisites

- Docker
- Java 25
- Gradle

### Running the application

Start all services with live reload:

```bash
make up
```

To pull the latest images:

```bash
make update
```

To stop all services:

```bash
make down
```

### Services

All services run on an internal Docker network. Only the Assessment View API is exposed to the host.

| Service | Port | Description |
|---------|------|-------------|
| Assessment View API | 8080 (exposed) | This service |
| Coordinator API | 8080 (internal) | OASys PK lookups |
| AAP API | 8080 (internal) | Assessment data source |
| HMPPS Auth | 9090 (internal) | OAuth2 token provider |
| PostgreSQL | 5432 (internal) | Database (user: `root`, password: `dev`) |

### Spring Profiles

| Profile | Purpose |
|---------|---------|
| `api`   | API server mode — serves consumer endpoints |
| `sync`  | Sync mode — pulls data from AAP-API then exits (no web server) |
| `dev`   | Local development overrides (auth URL, swagger UI) |
| `test`  | Integration test overrides |

## Testing

Unit and integration tests are split into separate Gradle tasks. Unit tests (`src/test/**/unit/`) verify isolated logic with no external dependencies — all collaborators are mocked. Integration tests (`src/test/**/integration/`) verify service behaviour with external dependencies mocked using tools like WireMock.

```bash
make unit-test         # unit tests only
make integration-test  # integration tests only
make lint              # run linter
make lint-fix          # run linter and auto-fix
```

## CI/CD

GitHub Actions workflows in `.github/workflows/`:

| Workflow | Trigger | Purpose |
|----------|---------|---------|
| `pipeline.yml` | Push to any branch | Runs unit tests, integration tests, helm lint. On `main`: builds docker image and deploys to dev. |
| `deploy_to_env.yml` | Called by pipeline | Deploys to a specific environment |
| `security_owasp.yml` | Scheduled (weekdays) | OWASP dependency vulnerability scan |
| `security_codeql_actions_scan.yml` | Scheduled (weekdays) | CodeQL static analysis |
| `security_veracode_pipeline_scan.yml` | Scheduled (weekdays) | Veracode pipeline security scan |
| `security_veracode_policy_scan.yml` | Scheduled (weekdays) | Veracode policy security scan |

## Common Kotlin Patterns

Many patterns have evolved for HMPPS Kotlin applications. Using these patterns provides consistency across our suite of
Kotlin microservices and allows you to concentrate on building your business needs rather than reinventing the
technical approach.

Documentation for these patterns can be found in the [HMPPS tech docs](https://tech-docs.hmpps.service.justice.gov.uk/common-kotlin-patterns/).
If this documentation is incorrect or needs improving please report to [#ask-prisons-digital-sre](https://moj.enterprise.slack.com/archives/C06MWP0UKDE)
or [raise a PR](https://github.com/ministryofjustice/hmpps-tech-docs).