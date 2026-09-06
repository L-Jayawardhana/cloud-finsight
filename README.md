# cloud-cost-observability-platform

AI-Assisted Cloud Cost Observability and Optimization Platform — IEEE

An observability platform for Azure VMs that watches real utilisation and live pricing, and
turns them into concrete, explainable rightsizing recommendations: which VMs are
over-provisioned, what to switch them to, how much that saves, and — via an LLM grounded in the
already-computed figures — a plain-language explanation and follow-up chat about *why*.

## Architecture

```mermaid
flowchart LR
    SPA["React SPA<br/>(nginx :3000)"] --> ApiService["api-service :8080"]
    ApiService <--> Postgres[("postgres")]
    ApiService <--> Redis[("redis")]
    CollectorService["collector-service :8081"] --> Postgres
    CollectorService -->|"publish"| RabbitMQ{{rabbitmq}}
    RabbitMQ -->|"consume"| ApiService
    CollectorService -.->|"poll"| Azure["Azure Monitor +<br/>Retail Prices"]
    ApiService -.->|"/explain, /chat"| Gemini["Gemini API"]
```

**collector-service** polls Azure for VM metrics and live pricing, runs a five-stage
recommendation engine, and publishes results onto RabbitMQ. **api-service** consumes those
recommendations, serves the dashboard, and answers `/explain` and `/chat` via Gemini. Both sit
behind Keycloak-issued JWTs; a Prometheus/Grafana/Loki stack watches both services.

Full breakdown, component responsibilities, and the data-flow narrative: **[docs/architecture.md](docs/architecture.md)**.

## Prerequisites

- Docker and Docker Compose
- [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli), logged in
  (`az login`) — collector-service needs this on the host to reach Azure Monitor (see
  [docs/local-dev-azure-auth.md](docs/local-dev-azure-auth.md))
- A [Google Gemini API key](https://aistudio.google.com/apikey) for `/explain` and `/chat`
- Java 21 + Maven, and Node 20, only if you want to run a service outside Docker

## Quick start

```bash
cp .env.example .env          # fill in real values - see Environment Variables below
az login                      # collector-service needs this to reach Azure Monitor
docker compose up -d
```

Everything is provisioned automatically: Postgres schema (Flyway), the Keycloak realm and demo
users, both Spring Boot services, the frontend, and the full Prometheus/Grafana/Loki stack.
Wait for all containers to report healthy (`docker compose ps`), then:

| | URL | Login |
|---|---|---|
| Dashboard | http://localhost:3000 | `dashboard-user` / `TestPass123` (viewer) or `admin-user` / `TestPass123` (admin) |
| Swagger UI | http://localhost:8080/swagger-ui/index.html | — |
| Grafana | http://localhost:3001 | `admin` / value of `GRAFANA_ADMIN_PASSWORD` |
| Prometheus | http://localhost:9090 | — |
| RabbitMQ management | http://localhost:15672 | value of `RABBITMQ_USER` / `RABBITMQ_PASSWORD` |
| Keycloak admin console | http://localhost:8180/auth | `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` |

New recommendations appear after collector-service's first collection + analysis cycle
completes (15 minutes by default — `collector.interval-ms` / `collector.analysis.interval-ms`
in `collector-service/src/main/resources/application.yml`).

## Environment Variables & Secrets

Copy `.env.example` to `.env` (and `cloud-finsight-ui/.env.example` to `cloud-finsight-ui/.env` if
running the frontend outside Docker) and fill in real values. Both `.env` files are gitignored and
must never be committed.

| Variable | Used by | How to obtain it |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | postgres, api-service, collector-service, keycloak | Pick any values — these just define the local database's credentials on first startup. |
| `REDIS_PASSWORD` | redis, api-service, collector-service | Pick any value. |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | rabbitmq, api-service, collector-service | Pick any values. |
| `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` | keycloak | Pick any values — bootstraps the admin console login at http://localhost:8180/auth. |
| `GEMINI_API_KEY` | api-service (`/explain`, `/chat` endpoints) | Create a free key at [Google AI Studio](https://aistudio.google.com/apikey). |
| `GRAFANA_ADMIN_USER` / `GRAFANA_ADMIN_PASSWORD` | grafana | Pick any values — bootstraps the admin login at http://localhost:3001. |

collector-service additionally needs an authenticated Azure session on the host to reach Azure
Monitor: run `az login` before `docker compose up` (see `docker-compose.yml` — it mounts
`~/.azure` into the container).

The local Keycloak realm (`keycloak/cloud-finsight-realm.json`) ships with fixed demo
credentials (`dashboard-user` / `admin-user`, both password `TestPass123`) and a fixed client
secret for `cloud-finsight-api`. These are intentionally hardcoded: the realm only exists inside
your local, disposable `docker compose` Keycloak instance, is reimported from this file on every
fresh start, and is not a credential to any real system.

## Running tests

```bash
# Backend (each service) - needs .env sourced for local Postgres/Redis/RabbitMQ credentials
cd api-service && set -a && source ../.env && set +a && ./mvnw test
cd collector-service && set -a && source ../.env && set +a && ./mvnw test
```

Both suites include real integration tests against Testcontainers (Postgres, RabbitMQ, and for
api-service a full Keycloak container) — no external services need to be running first; Docker
does need to be available for Testcontainers itself.

```bash
# Frontend
cd cloud-finsight-ui
npm run build   # tsc -b && vite build
npm run lint    # oxlint
```

## Further documentation

- [docs/architecture.md](docs/architecture.md) — system diagram, component responsibilities, data flow
- [docs/recommendation-engine.md](docs/recommendation-engine.md) — the five-stage recommendation pipeline, with code references
- [docs/api-reference.md](docs/api-reference.md) — every REST endpoint, with example requests/responses
- [docs/observability.md](docs/observability.md) — Prometheus metric catalogue, Grafana dashboards, Loki query examples
- [docs/local-dev-azure-auth.md](docs/local-dev-azure-auth.md) — how collector-service authenticates to Azure locally
- [docs/epic-10-manual-qa-report.md](docs/epic-10-manual-qa-report.md) — the latest manual end-to-end QA pass
