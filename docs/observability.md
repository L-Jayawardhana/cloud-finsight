# Observability

The platform's operational visibility is three layers, all provisioned automatically by
`docker compose up` — no manual dashboard setup needed:

- **Prometheus** (`http://localhost:9090`) scrapes both Spring Boot services'
  `/actuator/prometheus` endpoints every 15s.
- **Grafana** (`http://localhost:3001`, default `admin`/`admin` unless overridden in `.env`)
  ships with two dashboards and one alert rule, provisioned from
  [`grafana/provisioning`](../grafana/provisioning) and [`grafana/dashboards`](../grafana/dashboards).
- **Loki + Promtail** (`http://localhost:3100`) tail both services' JSON log files and make them
  queryable from Grafana's Explore view.

## Metric catalogue

Both services expose the full set of Spring Boot Actuator/Micrometer defaults (JVM heap,
threads, HTTP request rate/latency/status, Hikari connection pool, Redis, RabbitMQ client)
under `/actuator/prometheus`. On top of those, this project adds the following custom metrics:

### collector-service

| Metric | Type | Tags | What it means |
|---|---|---|---|
| `collector.cycle.success` | Counter | — | A metric-collection (Azure Monitor) cycle completed without error |
| `collector.cycle.failure` | Counter | — | A metric-collection cycle failed |
| `collector.cycle.duration.seconds` | Timer | `cycle=analysis` | Wall-clock duration of one full recommendation analysis cycle across all VMs |
| `recommendation.engine.last.run.seconds.ago` | Gauge | — | Seconds since the analysis engine last completed a full cycle (`-1` if it has never run) — the metric to alert on if the engine silently stops |
| `collector.azure.api.calls.total` | Counter | `client={monitor,pricing}`, `endpoint={metrics,memory,retail-prices}` | Outbound calls to Azure Monitor / Retail Prices, broken down by which client and endpoint |
| `collector.errors.total` | Counter | `source={azure_monitor,azure_pricing,analysis,rabbitmq_publish}` | Any caught failure, tagged by which stage/dependency caused it — this is what the built-in alert rule watches |
| `recommendations.published.total` | Counter | `result={success,failure}` | Recommendation messages published to RabbitMQ, and whether the broker confirmed them |
| `rabbitmq.queue.messages` | Gauge | — | Current depth of `recommendations.queue`, polled live from the broker (not a push metric) |

### api-service

| Metric | Type | Tags | What it means |
|---|---|---|---|
| `llm.calls.success` / `llm.calls.failure` | Counter | — | Outbound calls to the Gemini API (via `/explain` and `/chat`), split by outcome |
| `llm.api.call.duration.seconds` | Timer | — | Latency of outbound LLM calls, including failed ones |
| `recommendations.consumed.total` | Counter | — | Recommendation messages successfully consumed off RabbitMQ and upserted |
| `recommendations.dlq.total` | Counter | — | Messages that failed to deserialize and were routed to the dead-letter queue instead |
| `rabbitmq.queue.messages` | Gauge | — | Same live queue-depth gauge as collector-service, from api-service's own connection |

Query any of these directly from Prometheus, e.g.:

```
rate(collector_errors_total[5m])
sum by (client, endpoint) (rate(collector_azure_api_calls_total[5m]))
histogram_quantile(0.95, rate(http_server_requests_seconds_bucket[5m]))
```

(Micrometer's `.` separators become `_` once scraped by Prometheus.)

## Grafana dashboards

### API Service Health

Request rate · error rate (5xx) · p95 request latency · JVM heap used · active JVM threads ·
Redis cache hit rate · RabbitMQ queue depth (as a consumer-lag proxy).

### Collector Pipeline Health

Analysis cycle duration (avg) · recommendation engine runs/min · Azure API calls (rate, by
client/endpoint) · collector errors (rate, by source) · RabbitMQ queue depth · time since last
engine run · a live tail of collector-service `ERROR`-level logs (via the Loki datasource,
embedded directly in the dashboard).

### Alerting

One rule ships out of the box (`grafana/provisioning/alerting/rules.yml`): **Collector error
rate high** fires (severity `warning`) when `sum(rate(collector_errors_total[5m]))` exceeds 3.
Add further rules the same way — a YAML file under `grafana/provisioning/alerting/`.

## Log aggregation (Loki + Promtail)

Both services write structured JSON logs (via Logstash's Logback encoder) to
`<service>/logs/<service>.log`, which is bind-mounted read-only into the `promtail` container
and shipped to Loki with labels `service=api-service` / `service=collector-service`. Each log
line is queryable with full field access:

```json
{
  "@timestamp": "2026-09-06T20:34:58.42Z",
  "message": "Authenticated request from user 'dashboard-user' to GET /api/v1/vms",
  "logger_name": "com.cloudfinsight.apiservice.SecurityConfig",
  "thread_name": "http-nio-8080-exec-7",
  "level": "INFO"
}
```

Example LogQL queries (Grafana Explore, or `curl` against
`http://localhost:3100/loki/api/v1/query_range`):

```logql
{service="api-service"}                                  # everything from api-service
{service="collector-service"} |= "ERROR"                 # plain-text grep for ERROR lines
{service="api-service"} | json | level="ERROR"            # parse JSON, filter by level field
{service="api-service"} | json | logger_name=~"SecurityConfig.*"
sum(rate({service="collector-service"} |= "ERROR" [5m]))  # ERROR rate over time, for a panel
```
