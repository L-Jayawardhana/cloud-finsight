# Recommendation Engine Architecture Decision

## Decision

The five-stage recommendation engine (Epic 4) runs entirely inside `collector-service` and persists
`Recommendation`/`RecommendationCandidate` entities directly to PostgreSQL via `RecommendationRepository`,
triggered by a dedicated `AnalysisScheduler` (Task 4.6) on the same scheduling pattern as
`MetricCollectionScheduler` and `PricingCollectionScheduler`.

The RabbitMQ topology declared in Task 2.4 (`finsight.recommendations.exchange`,
`finsight.recommendations.queue`) is **not** used for this. `collector-service` does not publish
recommendation messages, and `api-service` does not consume them via a `@RabbitListener`.

## Context

The original project report (Section 8, Data Flow) described a queue-based handoff: the Collector
Service computes a recommendation, publishes it to RabbitMQ, and the API Service consumes the message
and persists it to PostgreSQL. This was the intended architecture when the platform was first designed.

By the time Epic 4 (the recommendation engine itself) was being planned, `api-service` and
`collector-service` both already had full, independent repository access to the same PostgreSQL
database — established in Epic 2 and Task 3.4 — for every entity the recommendation engine needs to
read or write.

## Reasoning

- Epic 4 is explicitly scoped as the deterministic, testable analytical core of the platform: "no LLM
  involvement, all logic is testable code." Building a message producer in `collector-service` and a
  consumer in `api-service` alongside it would test the messaging layer, not the recommendation logic
  the epic is actually about.
- There is currently no concrete requirement that RabbitMQ would solve here — no real-time push
  requirement to the dashboard, no need to scale collection and serving independently. `api-service`
  can query `RecommendationRepository` directly against the same table `collector-service` writes to,
  with no added latency or failure surface compared to a queue-based handoff.
- The task descriptions for Tasks 4.1–4.5 themselves specify direct persistence via
  `RecommendationRepository`, not message publishing.

## What this means going forward

The RabbitMQ topology remains declared and unused. If a genuine requirement for decoupling emerges in
a future milestone — for example, real-time recommendation push to the dashboard, or a need to scale
`collector-service`'s analysis workload independently of `api-service` — wiring the publish/consume
path is a contained, well-scoped task on its own, and does not require re-touching the recommendation
engine's core logic.

## Related decisions

This mirrors the same class of scope decision made earlier in the project: e.g. substituting a
Service Principal for a Managed Identity in Task 1.2 when the original plan hit a real constraint, or
scoping the platform to a single auth role in Milestone 1. In each case, the deviation is deliberate,
documented, and does not compromise the acceptance criteria it was measured against — it just avoids
building infrastructure ahead of an actual, present need.