# Recommendation Engine

The engine is a five-stage pipeline run once per VM, per analysis cycle
(`collector.analysis.interval-ms`, default 15 minutes), orchestrated by
[`AnalysisScheduler`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/scheduler/AnalysisScheduler.java).
Each stage is a separate, independently-testable `@Service` — the scheduler just wires their
outputs into the next stage's input and stops early if any stage produces nothing.

```
UtilisationAggregator → CandidateGenerator → TradeOffScorer → SavingsCalculator → RecommendationPackager → publish
```

## 1. Utilisation aggregation

[`UtilisationAggregator`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/UtilisationAggregator.java)

Pulls every raw `MetricSnapshot` for the VM over a rolling window
(`collector.analysis.rolling-window-days`, default 14 days) and reduces it to a
`UtilisationSummary`: per-metric p50/p95/max plus how many distinct days of data are actually
available (`daysOfDataAvailable()`) — this is what later determines the recommendation's
**confidence level**, not a fixed setting. If there's no data yet, the cycle stops here for that
VM (`summary.hasData()` is false) rather than recommending off zero information.

## 2. Candidate generation

[`CandidateGenerator`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/CandidateGenerator.java)

Looks up the VM's current SKU in the [SKU catalogue](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/SkuCatalogueService.java)
and pulls a pool of same-family candidate SKUs. For each candidate, it computes **headroom**:
what the VM's own observed p95 CPU/memory usage would become if scaled onto that candidate's
capacity (`headroomPercent` in the source scales usage by the current/candidate capacity
ratio, then expresses it as room-below-100%). A candidate only survives if **both** CPU and
memory headroom are at least `MIN_HEADROOM_PERCENT` (20%) — this is the guardrail that stops
the engine from ever recommending a SKU that would leave the workload under-resourced.
Surviving candidates are tagged `CURRENT_GEN` / `OLDER_SUPPORTED` based on generation metadata
in the catalogue.

## 3. Trade-off scoring

[`TradeOffScorer`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/TradeOffScorer.java)

Scores each surviving candidate against three weighted axes
(`collector.scoring.cost-weight` / `reliability-weight` / `performance-weight`, default
0.5/0.3/0.2) and combines them into one composite:

```
composite = costScore × costWeight + reliabilityScore × reliabilityWeight + performanceScore × performanceWeight
```

- **Cost score** — `currentPrice / candidatePrice` (a straight ratio; cheaper candidates score
  higher, uncapped).
- **Reliability score** — 1.0 for current-generation hardware, 0.85 for older-but-supported,
  further penalized (`× 0.5`) if the candidate is unsupported/deprecated.
- **Performance score** — derived from the candidate's own vCPU/memory relative to the
  workload's actual usage (see source for the exact curve).

Candidates are sorted by composite score, highest first — the top one is what gets
recommended. The same pass also assigns the recommendation's **confidence level**
(`HIGH` ≥14 days of data, `MEDIUM` ≥7 days, else `LOW`) — this reflects how much history backed
the recommendation, independent of how good the candidate looks on paper.

## 4. Savings calculation

[`SavingsCalculator`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/SavingsCalculator.java)

Pulls live retail pricing (via `PricingCacheService`, backed by the Azure Retail Prices API,
refreshed daily) for the current SKU and every candidate, and converts hourly pricing to
monthly (`× 730` hours) to compute the actual dollar and percentage savings. This is
deliberately a separate stage from scoring: the LLM-facing `/explain` endpoint is instructed to
use only these pre-computed figures and never calculate savings itself, so a wrong LLM
computation can never contradict what's shown elsewhere in the UI. It also checks
**two-instance feasibility** — whether running two instances of a smaller candidate (for
redundancy) still costs less than one instance of the current SKU.

## 5. Packaging & publishing

[`RecommendationPackager`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/service/RecommendationPackager.java)
→ [`RecommendationPublisher`](../collector-service/src/main/java/com/cloudfinsight/collectorservice/messaging/RecommendationPublisher.java)

Takes the top-scored candidate and its savings estimate, generates human-readable pros/cons
text, and persists a `Recommendation` + `RecommendationCandidate` row (collector-service owns
this write). If packaging succeeds, the recommendation is published onto RabbitMQ
(`cost-platform.recommendations` exchange, `recommendations.queue`, routing key
`recommendation.created`) for api-service's `RecommendationConsumer` to upsert into its own
copy of the same table — see [architecture.md](architecture.md) for why both services persist
this data rather than one owning it exclusively.

## Failure handling

Each stage returning "nothing" (no data, no candidates cleared headroom, no candidates could be
priced, no pricing for the current SKU) is a normal, logged early-exit for that VM — not an
error. Real failures (an exception during any stage) are caught per-VM in
`AnalysisScheduler.analyseVm`, logged, counted in `collector.errors.total{source="analysis"}`,
and don't stop the cycle from continuing to the next VM. See
[observability.md](observability.md) for the full metric catalogue this pipeline emits.
