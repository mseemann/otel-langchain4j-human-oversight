# Human Oversight Without Scoring

*(Working title — companion article in the "AI Observability & Compliance for Enterprise Java" series, not yet published. Link added here once it's live.)*

Companion repo for the article that merges two pieces of the same series: a human-oversight signal/alerting layer for Art. 14 EU AI Act, and a forced three-way tool-choice router that turns out to satisfy that same oversight requirement structurally — by design, not by scoring the model's prose.

Builds on [otel-langchain4j-springboot](https://github.com/mseemann/otel-langchain4j-springboot): same manual `ChatModelListener` instrumentation. Where [otel-langchain4j-toolchain-costs](https://github.com/mseemann/otel-langchain4j-toolchain-costs) branches into cost tracking, this repo branches into compliance: a single free-text chat endpoint is replaced by an endpoint that forces the model to commit to exactly one of three tools (`toolChoice(ToolChoice.REQUIRED)`), and every customer-facing answer is a fixed Java template — never model-formulated text. Human-oversight signals are derived from *which route* the model had to choose, not from scoring what it wrote.

## Prerequisites

- Java 21
- Maven
- Docker + Docker Compose
- An [Anthropic API key](https://console.anthropic.com/) — the demo calls the real Claude Haiku model, cost per test run is a few cents

## Getting started

### 1. Start the observability stack

```bash
docker compose up -d
```

Brings up the OTel Collector, Jaeger, Prometheus, and Alertmanager.

### 2. Start the Spring Boot app

```bash
export ANTHROPIC_API_KEY=sk-ant-...
mvn spring-boot:run
```

### 3. Call the three routes

```bash
# Route 1: route_status_inquiry — nothing unusual
curl "http://localhost:8080/lc4j/order-status?orderNumber=ORD-4711"

# Route 2: propose_goodwill_gesture — customerNote contains a compensation signal
curl "http://localhost:8080/lc4j/order-status?orderNumber=ORD-4711" \
  --data-urlencode "customerNote=My package arrived completely damaged, I want a refund" -G

# Route 3: escalate_to_support — the safe catch-all for ambiguity
curl "http://localhost:8080/lc4j/order-status?orderNumber=ORD-4711" \
  --data-urlencode "customerNote=Can you also check on my neighbor's package while you're at it?" -G
```

Since route selection is still a real Anthropic call, an individual response can in principle land on a different route than intended above — that's exactly what `human_oversight.possible_misroute` is there to catch (see `COMPENSATION_SIGNAL_KEYWORDS` in `LangChain4jController.java`).

### 4. Submit a human-intervention decision

Copy `traceId`/`spanId` from a flagged trace in Jaeger or from `review-queue/review-queue.jsonl`, then:

```bash
curl -X POST http://localhost:8080/lc4j/human-intervention \
  -H "Content-Type: application/json" \
  -d '{"traceId":"<32-hex-chars>","spanId":"<16-hex-chars>",
       "reviewer":"jane.doe","decision":"overridden",
       "comment":"Tracking ID corrected manually, tool had processed a typo in the order format."}'
```

### 5. Open the UIs

- Jaeger: http://localhost:16686 → service `otel-langchain4j-demo` → Find Traces
- Prometheus: http://localhost:9090 → Alerts
- Alertmanager: http://localhost:9093

## What you'll see in Jaeger

Each call to `/order-status` produces a `chat` span (the router decision) followed by exactly one `route *` span — `route route_status_inquiry`, `route propose_goodwill_gesture`, or `route escalate_to_support`, depending on what the model chose. The root span carries the full `human_oversight.*` attribute set: `route_chosen`, `possible_misroute`, `routing_failed`, `output_guard_triggered`, `output_guard_matched_pattern`, `user_flagged`, `user_comment`, `needs_review`, plus `pii_guard.tokens_substituted` from the PII layer that runs before the request ever reaches the model.

A `human_intervention` span recorded via step 4 attaches to the *same* trace it reviews — same trace ID, no separate audit log to cross-reference.

## What you'll see in Prometheus & Alertmanager

Five alerts, all in the `human-oversight` group (`prometheus-rules.yml`), all sourced from the spanmetrics connector in `otel-collector-config.yml`:

| Alert | Fires on | Severity | `for:` |
|---|---|---|---|
| `PossibleMisrouteDetected` | `possible_misroute=true` | warning | 0m |
| `RoutingFailed` | `routing_failed=true` | warning | 0m |
| `OutputGuardTriggered` | `output_guard_triggered=true` | critical | 0m |
| `GoodwillGestureRouted` | `route_chosen="propose_goodwill_gesture"` | warning | 0m |
| `HumanReviewQueueGrowing` | sustained `needs_review=true` inflow | warning | 30m |

`HumanReviewQueueGrowing` measures sustained *inflow*, not a provably growing *backlog depth* — there's no second counter for completed reviews to subtract against (see the comment in `prometheus-rules.yml`). The other four fire with no delay: each is a single, already-relevant event, not noise to confirm over a window.

Alertmanager routes `severity: critical` and `domain: human-oversight` into faster-grouped sub-routes (`alertmanager.yml`) — locally everything still lands in the same logging-only receiver, but the routing itself mirrors how a real deployment would split a compliance signal from a general ops channel.

## Project structure

- `LangChain4jController.java` — the three-way router (`/order-status`): forced tool choice, the three deterministic route handlers, the misrouting backstop, `tagHumanOversightSignals()`
- `HumanOversightController.java` — `/human-intervention`: records a reviewer decision as a span on the original trace, via `SpanContext.createFromRemoteParent(...)`
- `OutputGuard.java` — deny-list check against the final answer text before it's delivered; now a regression tripwire rather than a load-bearing control, since none of the three routes produces free text anymore
- `GoodwillGesturePolicy.java` / `OrderStatusLookup.java` — the deterministic, fixed-template logic behind two of the three routes
- `PiiTokenizer.java` — tokenizes PII out of `customerNote` before it ever reaches the model
- `otel-collector-config.yml` — PII-masking transform, the `traces/review-queue` pipeline (file-based review queue), and the spanmetrics dimensions the alerts query
- `prometheus-rules.yml` / `alertmanager.yml` — the `human-oversight` alert group and its routing
- `review-queue/` — bind-mounted target for the `file/review-queue` exporter; `review-queue.jsonl` appears here once a trace gets flagged

## More context

The background on why LangChain4j needs manual instrumentation on Spring Boot at all is covered in [otel-langchain4j-springboot](https://github.com/mseemann/otel-langchain4j-springboot) and its article. This repo's own article covers why an oversight design built for one purpose (forcing a model to commit to an irreversible value) ended up resolving an unrelated review-queue design problem (avoiding both rubber-stamping and reviewer fatigue) as a side effect, not by intent.

## License

MIT – use the code however you like.

---

**Author:** Michael Seemann · [GitHub](https://github.com/mseemann) · [Medium](https://medium.com/@mseemann.io) · [LinkedIn](https://www.linkedin.com/in/michael-seemann-1478563bb/)
