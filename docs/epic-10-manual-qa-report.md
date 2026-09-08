# Epic 10 — Manual End-to-End User Journey Walkthrough

Manual QA pass against the fully dockerized stack (`docker compose up`, all nine services:
postgres, redis, rabbitmq, keycloak, api-service, collector-service, frontend, prometheus,
grafana, loki, promtail). Performed by exercising every real endpoint the frontend uses,
via curl with real Keycloak-issued JWTs, against a genuinely clean cold start.

## Scope and method

- Brought the whole stack down (`docker compose down`) and back up from a clean state
  (`docker compose up -d`) to verify startup ordering and healthchecks, not just a
  long-running dev environment.
- Acquired real access tokens from Keycloak via the resource-owner password grant (the
  `cloud-finsight-api` confidential client), using the seeded demo users, then drove every
  dashboard/recommendation/chat endpoint the same way the SPA does.
- No Chrome browser was available in this environment, so the actual click-through (visual
  layout, the newly-restored dashboard redesign rendering correctly, responsive behavior) was
  **not** verified here — only the API/data layer was. That remains open (see Known Gaps).

## Bug found and fixed during this walkthrough

**Keycloak issuer/proxy auth loop.** The first real login attempt through the dockerized
frontend (`localhost:3000`) surfaced a login redirect loop: Keycloak inferred its own token
issuer from the raw `Host` header of whatever request reached it. Behind nginx's proxy, that
header arrived as bare `localhost` with no port and no `/auth` prefix, so tokens carried the
wrong `iss` claim and were rejected by both `keycloak-js` and `api-service`, which just bounced
the user back through Keycloak's still-active session. A second issue compounded it: the SPA's
own `/auth/callback` route was being swallowed by nginx's `location /auth/` proxy rule (a 404
right after login).

This was root-caused and fixed in commit `783318e` (PR #101, merged to `main`) by:
- Giving Keycloak a stable identity (`KC_HTTP_RELATIVE_PATH=/auth`, `KC_PROXY_HEADERS`) so its
  issuer is consistent regardless of which path a request arrived through.
- Moving the SPA's callback route to `/sso/callback` to stop the collision.
- Having `api-service` validate against an allow-list of legitimate issuers
  (`app.security.allowed-issuers`) — both the `:3000/auth` proxied path and the `:8180/auth`
  direct path (used by the host-run Vite dev flow on `:5173`) — while fetching signing keys via
  a separate, internal `jwk-set-uri` decoupled from issuer validation.

This session verified that fix still holds (see below) and additionally found no other bugs
in the process — everything else tested clean on the first attempt.

## Journey verified (this session)

1. **Clean cold start.** `docker compose down` then `docker compose up -d`: every container
   reached `healthy` before its dependents started, no manual intervention needed.
2. **Auth — proxy path.** Acquired a token through `http://localhost:3000/auth/...` (the exact
   path the browser uses). Its `iss` claim correctly read
   `http://localhost:3000/auth/realms/cloud-finsight`, and `api-service` accepted it
   (`GET /api/v1/vms` via the `:3000` proxy → 200).
3. **Auth — direct path.** Acquired a second token through `http://localhost:8180/auth/...`
   (the host-run dev-flow path). Its `iss` read `http://localhost:8180/auth/realms/cloud-finsight`,
   and the same running `api-service` accepted this one too (`GET /api/v1/vms` via `:8080` → 200)
   — confirming the dual-issuer allow-list works, not just whichever path was tested last.
4. **VM inventory** (`GET /api/v1/vms`): both VMs (`vm-current-gen-d2sv4`, `vm-older-gen-d2sv3`)
   returned with real p95 CPU/memory figures and current pricing — no mock data, and no
   recurrence of the earlier bug where only one VM had live data.
5. **Cost summary** (`GET /api/v1/cost/summary`): real aggregate figures
   (`totalMonthlySpend: 178.85`, `totalPotentialSaving: 101.77`, across 2 VMs / 2 recommendations).
6. **Recommendations list** (`GET /api/v1/recommendations`): both VMs have a current `PENDING`
   `DOWNSIZE` recommendation with real savings (`52.71` and `49.06` USD/month), confidence
   levels, and generated pros/cons text.
7. **Explain** (`POST /api/v1/recommendations/{id}/explain`): first call hit the LLM
   (`cached:false`, ~7.3s, real Gemini-generated prose citing the correct SKUs and the exact
   `49.06 USD/56.0%` savings figure); an immediate second call returned the identical text from
   the Redis cache (`cached:true`, ~14ms) — cache-and-real-figures behavior both confirmed.
8. **Follow-up chat** (`POST /api/v1/chat`): three-turn conversation against `vmId: 1` —
   asked about confidence (correctly cited the real p95 figures), asked for the exact saving
   figure (correctly recalled `49.06 USD / 56.0%` from the recommendation data, not the chat
   history), then asked it to elaborate on "the reliability concern you just mentioned" — it
   correctly referenced back to its own prior turn, confirming the 5-turn Redis-backed
   conversation history is genuinely being used, not just re-grounding fresh every time.
   A chat request against a non-existent `vmId` correctly returned `404`, matching spec.
9. **Observability:**
   - Prometheus: both `api-service` and `collector-service` scrape targets `up`; custom
     application metrics flowing (e.g. `recommendations_published_total` = 12).
   - Grafana: both provisioned dashboards (`API Service Health`, `Collector Pipeline Health`)
     present and healthy, backed by the same live Prometheus data confirmed above.
   - Loki/Promtail: real structured JSON logs from `api-service` flowing through, including
     this session's own authenticated requests (`"Authenticated request from user
     'dashboard-user' to GET /api/v1/vms"`), confirming the whole log pipeline is live end to
     end, not just configured.
   - Swagger UI (`/swagger-ui/index.html`) and OpenAPI docs (`/v3/api-docs`) both reachable.
10. **Collection pipeline:** collector-service's scheduled metric collection cycle ran
    successfully against real Azure Monitor data for both VMs (confirmed via container logs),
    and `AzureCliCredential` correctly authenticates from inside the container using the
    mounted host `~/.azure` session (the design decided in Task 11.1).

## Known gaps (not covered by this session)

- **No visual/browser verification.** No Chrome instance was available here. The actual
  rendered dashboard (including the just-restored "warm paper" redesign), responsive layout,
  and clicking through the UI itself were not exercised — only the underlying API responses
  the UI consumes. PR #101's own test plan flagged this same gap. A follow-up pass with a
  browser available should do the visual click-through before considering the user journey
  fully verified end to end.
- **Grafana panel-level rendering** was checked via the API (dashboards provisioned, backed by
  live data) but not visually confirmed to render without panel errors.

## Follow-up bug found and fixed (second session)

**LLM responses contained literal markdown syntax.** Reported against a live `/explain` response
for recommendation 102: the text included literal `**bold**` asterisks (e.g. `"...from its
current **Standard_D2s_v3** SKU..."`) rendered on screen as-is, since `TypewriterText.tsx`
displays the LLM's response as plain text rather than rendering markdown. Root cause: neither
`LlmClient` system prompt (explain or chat) told Gemini to avoid markdown formatting, and the
model defaulted to its usual markdown-flavored output style.

Fixed by adding an explicit "respond in plain prose only, no markdown whatsoever" instruction to
both system prompts. Verified live against the exact recommendation from the bug report (id 102,
`vm-older-gen-d2sv3`) and against the chat endpoint — both now return clean prose with no
markdown artifacts. Existing `LlmClientTest`/`ChatServiceContextTest`/
`RecommendationServiceExplainCacheTest` unit tests still pass unchanged (none assert on exact
prompt text).

