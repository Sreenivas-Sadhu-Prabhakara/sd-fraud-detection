# Fraud Detection

BIAN Service Domain microservice — **Phase 2b-c DEEP build** (graduated; see `.bian-graduated`). One of the three **flagship** counterparties (fraud).

| | |
|---|---|
| **Business Area / Domain** | Risk and Compliance / Financial Crime |
| **Pattern / Control Record** | Monitor / Fraud Alert Monitoring State |
| **K8s Namespace** | `bian-risk-compliance` |

## The scoring model (deterministic, explainable)

| Rule | Points | Fires when |
|---|---|---|
| `LARGE_AMOUNT` | +70 | amount ≥ `bian.fraud.large-amount-minor` (default ₹10,000) — alerts on its own |
| `VELOCITY` | +50 | >5 activities for the account within a rolling 10-minute window |
| `ROUND_AMOUNT` | +25 | conspicuously round amount (×100 000 minor) at ≥ half the large threshold — the structuring signal |

Alert raised at score ≥ **60** (`bian.fraud.alert-threshold`). Every evaluation returns the score + triggered rules — scoring is never a black box. Every activity is recorded as future velocity evidence whether or not it alerts.

**Investigation lifecycle:** `OPEN → CONFIRMED_FRAUD | FALSE_POSITIVE` (terminal).

## Flagship wiring

Consumes `transaction.posted` (current + savings accounts) and `cheque.lodged` (cheque processing) — **via `POST /evaluate` over HTTP today**, via Kafka consumers when the backbone lands (same evaluation path). Publishes `fraud.alert.raised` / `fraud.alert.resolved` on `bian.fraud.alerts`.

## API (contracts owned by this repo: [`api/openapi.yaml`](api/openapi.yaml), [`api/events.yaml`](api/events.yaml))

```bash
mvn spring-boot:run
CR=/v1/fraud-alert-monitoring-state
curl -s -X POST localhost:8080$CR/evaluate -H 'content-type: application/json' \
  -d '{"accountRef":"CA-1","sourceType":"TRANSACTION","sourceRef":"TX-1","amountMinor":5000000}'
# → {"riskScore":95,"triggeredRules":["LARGE_AMOUNT","ROUND_AMOUNT"],"alertRaised":true,"alertId":"FA-…"}
curl -s "localhost:8080$CR?status=OPEN"
```

## Persistence & tests

In-memory port/adapter (the activity log doubles as the velocity window). Postgres staged in [`db/schema.sql`](db/schema.sql) — gated. `mvn verify` proves each rule, the sliding window (injected Clock), and the terminal resolution lifecycle.
