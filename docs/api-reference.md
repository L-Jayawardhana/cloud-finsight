# API Reference

All endpoints are served by **api-service** under `http://localhost:8080` (or
`http://localhost:3000/api/v1/...` through the frontend's nginx proxy). Interactive docs are
also available at runtime:

- Swagger UI: `http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Authentication

Every endpoint below requires a valid Keycloak-issued JWT as a Bearer token, except
`/actuator/**` and the Swagger endpoints, which are open. `DELETE /api/v1/recommendations/{id}`
additionally requires the realm role `admin` — any other authenticated caller gets `403`.

```bash
TOKEN=$(curl -s -X POST http://localhost:8180/auth/realms/cloud-finsight/protocol/openid-connect/token \
  -d "client_id=cloud-finsight-api" \
  -d "client_secret=<see keycloak/cloud-finsight-realm.json>" \
  -d "grant_type=password" \
  -d "username=dashboard-user" \
  -d "password=TestPass123" \
  | python3 -c "import json,sys; print(json.load(sys.stdin)['access_token'])")

curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/v1/vms
```

A request with no token, an expired token, or an unrecognized issuer gets `401`. Missing
resources return `404`. Endpoints that can't complete due to an unavailable dependency (e.g.
the LLM provider) return `503`, not `500`.

---

## Dashboard

### `GET /api/v1/vms`

List the VM inventory: current SKU, region, monthly price, and rolling p95 CPU/memory
utilisation for every monitored VM.

**Response `200`**
```json
[
  {
    "id": 1,
    "name": "vm-current-gen-d2sv4",
    "sku": "Standard_D2s_v4",
    "region": "southeastasia",
    "currentMonthlyPrice": 87.60,
    "p95CpuPercent": 1.41,
    "p95MemPercent": 7.92
  }
]
```

### `GET /api/v1/cost/summary`

Aggregate spend and savings across all monitored VMs.

**Response `200`**
```json
{
  "totalMonthlySpend": 178.85,
  "totalPotentialSaving": 101.77,
  "vmCount": 2,
  "recommendationCount": 2
}
```

### `GET /api/v1/vms/{vmId}/recommendations/history`

All past recommendations for one VM, most recent first, paginated. `isCurrent` flags the VM's
active pending recommendation.

**Query params:** standard Spring `Pageable` (`page`, `size`, `sort`).

**Response `200`**
```json
{
  "content": [
    {
      "id": 74,
      "vmId": 1,
      "vmName": "vm-current-gen-d2sv4",
      "recommendationType": "DOWNSIZE",
      "confidenceLevel": "MEDIUM",
      "estimatedMonthlySavings": 49.06,
      "status": "PENDING",
      "createdAt": "2026-09-06T20:17:34.018709Z",
      "isCurrent": true
    }
  ],
  "totalElements": 1
}
```

**Response `404`** — `vmId` doesn't exist.

### `GET /api/v1/vms/{vmId}/utilisation?days=14`

Per-day p50/p95/max CPU and memory utilisation over a trailing window (default 14 days),
computed from raw metric snapshots.

**Response `200`**
```json
[
  {
    "date": "2026-09-06",
    "cpuP50": 1.1,
    "cpuP95": 1.41,
    "cpuMax": 2.3,
    "memP50": 6.8,
    "memP95": 7.92,
    "memMax": 9.1
  }
]
```

**Response `404`** — `vmId` doesn't exist.

---

## Recommendations

### `GET /api/v1/recommendations?vmId=&type=`

List the latest pending recommendation per VM, optionally filtered by `vmId` or
`type` (e.g. `DOWNSIZE`), paginated.

**Response `200`**
```json
{
  "content": [
    {
      "id": 74,
      "vmId": 1,
      "vmName": "vm-current-gen-d2sv4",
      "currentSku": "Standard_D2s_v4",
      "candidateSku": "Standard_B2s",
      "generationTag": "OLDER_SUPPORTED",
      "recommendationType": "DOWNSIZE",
      "confidenceLevel": "MEDIUM",
      "confidenceScore": 1.56,
      "estimatedMonthlySavings": 49.06,
      "savingPercent": 56.0,
      "status": "PENDING",
      "createdAt": "2026-09-06T20:17:34.018709Z",
      "pros": [
        "Estimated saving of 49.06 USD/month (56.0%)",
        "Comfortable performance headroom above observed p95 usage"
      ],
      "cons": [
        "Older-generation hardware; reliability score reduced accordingly"
      ]
    }
  ]
}
```

### `GET /api/v1/recommendations/{id}`

Full detail for one recommendation, including every scored candidate (not just the selected
one), each with its own pros/cons, cost, and reliability/performance scores.

**Response `200`**
```json
{
  "id": 74,
  "vmId": 1,
  "vmName": "vm-current-gen-d2sv4",
  "currentSku": "Standard_D2s_v4",
  "currentGenerationTag": "CURRENT",
  "currentVcpuCount": 2,
  "currentMemoryGb": 8,
  "currentMonthlyPrice": 87.60,
  "recommendationType": "DOWNSIZE",
  "confidenceLevel": "MEDIUM",
  "confidenceScore": 1.56,
  "dataCoverageDays": 14,
  "estimatedMonthlySavings": 49.06,
  "status": "PENDING",
  "summary": "...",
  "createdAt": "2026-09-06T20:17:34.018709Z",
  "candidates": [
    {
      "id": 201,
      "candidateSku": "Standard_B2s",
      "generationTag": "OLDER_SUPPORTED",
      "vcpuCount": 2,
      "memoryGb": 4,
      "estimatedMonthlyCost": 38.54,
      "reliabilityScore": 0.85,
      "performanceScore": 0.62,
      "pros": "...",
      "cons": "...",
      "selected": true,
      "twoInstanceFeasible": true,
      "twoInstanceMonthlyCost": 77.08,
      "twoInstanceMonthlySaving": 10.51
    }
  ]
}
```

**Response `404`** — recommendation doesn't exist.

### `POST /api/v1/recommendations/{id}/explain`

An LLM-generated, plain-language explanation of the recommendation. The LLM is given the
already-computed figures and never calculates savings itself. Cached in Redis for 1 hour per
recommendation id — `cached` tells you whether this response came from cache.

**Response `200`**
```json
{
  "explanation": "We recommend resizing vm-current-gen-d2sv4 from Standard_D2s_v4 to Standard_B2s...",
  "cached": false
}
```

**Response `404`** — recommendation doesn't exist. **Response `503`** — the LLM provider is
unavailable or rate-limited.

### `DELETE /api/v1/recommendations/{id}`

Hard-deletes a recommendation row. **Requires the `admin` realm role.**

**Response `204`** — deleted. **Response `403`** — caller doesn't have the `admin` role.
**Response `404`** — recommendation doesn't exist.

---

## Chat

### `POST /api/v1/chat`

Ask a free-form follow-up question about a VM's most recent recommendation. Grounded in that
recommendation's data (savings, SKU, pros/cons) and utilisation statistics — the model is
instructed to say so honestly rather than invent figures if the VM has no recommendation yet.
Maintains up to 5 turns of conversation history in Redis, keyed by `vmId`, so follow-up
questions like "what did you mean by that?" resolve correctly.

**Request**
```json
{ "vmId": 1, "message": "How confident are we in this recommendation and why?" }
```

**Response `200`**
```json
{ "reply": "The confidence level for this recommendation is MEDIUM..." }
```

**Response `404`** — `vmId` doesn't exist.

---

## Error shape

Unhandled errors (404, 401, and Spring's default error responses) follow the standard Spring
Boot shape:

```json
{
  "timestamp": "2026-09-06T20:30:54.402Z",
  "status": 404,
  "error": "Not Found",
  "path": "/api/v1/cost-summary"
}
```
