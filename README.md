# cloud-cost-observability-platform
AI-Assisted Cloud Cost Observability and Optimization Platform — IEEE

## Environment Variables & Secrets

Copy `.env.example` to `.env` (and `cloud-finsight-ui/.env.example` to `cloud-finsight-ui/.env` if
running the frontend outside Docker) and fill in real values. Both `.env` files are gitignored and
must never be committed.

| Variable | Used by | How to obtain it |
|---|---|---|
| `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | postgres, api-service, collector-service, keycloak | Pick any values — these just define the local database's credentials on first startup. |
| `REDIS_PASSWORD` | redis, api-service, collector-service | Pick any value. |
| `RABBITMQ_USER` / `RABBITMQ_PASSWORD` | rabbitmq, api-service, collector-service | Pick any values. |
| `KEYCLOAK_ADMIN_USER` / `KEYCLOAK_ADMIN_PASSWORD` | keycloak | Pick any values — bootstraps the admin console login at http://localhost:8180. |
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
