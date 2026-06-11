package com.bank.bian.frauddetection.domain;

import com.bank.bian.frauddetection.events.DomainEvent;
import com.bank.bian.frauddetection.events.EventPublisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Business rules for Fraud Detection (Monitor pattern).
 *
 * The scoring model — deterministic, explainable rules; each contributes
 * points, and crossing the threshold raises an alert:
 *
 *   LARGE_AMOUNT   +70  amount >= bian.fraud.large-amount-minor (default 1_000_000
 *                       minor = ₹10,000) — alerts on its own.
 *   VELOCITY       +50  this is the (maxEvents+1)-th activity for the account
 *                       inside the rolling window (default >5 events / 10 min) —
 *                       a burst alone is suspicious but not conclusive.
 *   ROUND_AMOUNT   +25  a conspicuously round amount (multiple of 100_000 minor)
 *                       at half the large threshold or more — the classic
 *                       structuring signal; only meaningful combined with others.
 *
 *   threshold: 60 (bian.fraud.alert-threshold)
 *
 * Every activity is recorded (it feeds future velocity decisions) whether or
 * not it alerts. The evaluation result is always returned to the caller —
 * scoring is transparent, never a black box.
 *
 * Resolution: OPEN alerts are confirmed (CONFIRMED_FRAUD) or dismissed
 * (FALSE_POSITIVE) by an investigator; both are terminal.
 */
@Service
public class FraudDetectionService {

    public static final String TOPIC_ALERTS = "bian.fraud.alerts";

    public record Evaluation(int riskScore, List<String> triggeredRules,
                             boolean alertRaised, String alertId) {}

    private final AlertRepository repository;
    private final EventPublisher events;
    private final long largeAmountMinor;
    private final int velocityMaxEvents;
    private final Duration velocityWindow;
    private final int alertThreshold;
    private final Clock clock;

    @Autowired
    public FraudDetectionService(AlertRepository repository, EventPublisher events,
                                 @Value("${bian.fraud.large-amount-minor:1000000}") long largeAmountMinor,
                                 @Value("${bian.fraud.velocity-max-events:5}") int velocityMaxEvents,
                                 @Value("${bian.fraud.velocity-window-minutes:10}") int velocityWindowMinutes,
                                 @Value("${bian.fraud.alert-threshold:60}") int alertThreshold) {
        this(repository, events, largeAmountMinor, velocityMaxEvents,
                Duration.ofMinutes(velocityWindowMinutes), alertThreshold, Clock.systemUTC());
    }

    public FraudDetectionService(AlertRepository repository, EventPublisher events,
                                 long largeAmountMinor, int velocityMaxEvents,
                                 Duration velocityWindow, int alertThreshold, Clock clock) {
        this.repository = repository;
        this.events = events;
        this.largeAmountMinor = largeAmountMinor;
        this.velocityMaxEvents = velocityMaxEvents;
        this.velocityWindow = velocityWindow;
        this.alertThreshold = alertThreshold;
        this.clock = clock;
    }

    // ── scoring (the Monitor pattern's heart) ────────────────────────────────

    public Evaluation evaluate(String accountRef, FraudAlert.SourceType sourceType,
                               String sourceRef, long amountMinor, String currency) {
        if (accountRef == null || accountRef.isBlank()) {
            throw DomainException.invalid("ACCOUNT_REF_REQUIRED", "accountRef is required");
        }
        if (amountMinor <= 0) {
            throw DomainException.invalid("AMOUNT_NOT_POSITIVE", "amountMinor must be > 0");
        }

        Instant now = clock.instant();
        List<String> triggered = new ArrayList<>();
        int score = 0;

        if (amountMinor >= largeAmountMinor) {
            triggered.add("LARGE_AMOUNT");
            score += 70;
        }
        // velocity counts PRIOR activities in the window — record after scoring
        int recent = repository.countActivitiesSince(accountRef, now.minus(velocityWindow));
        if (recent >= velocityMaxEvents) {
            triggered.add("VELOCITY");
            score += 50;
        }
        if (amountMinor % 100_000 == 0 && amountMinor >= largeAmountMinor / 2) {
            triggered.add("ROUND_AMOUNT");
            score += 25;
        }

        repository.recordActivity(new ActivityEvent(accountRef, sourceType, sourceRef,
                amountMinor, currency, now));

        if (score < alertThreshold) {
            return new Evaluation(score, triggered, false, null);
        }

        FraudAlert alert = FraudAlert.raise("FA-" + UUID.randomUUID(), accountRef, sourceType,
                sourceRef, amountMinor, currency, score, triggered, now);
        repository.save(alert);
        events.publish(DomainEvent.of(TOPIC_ALERTS, "fraud.alert.raised", Map.of(
                "alertId", alert.getAlertId(),
                "accountRef", accountRef,
                "sourceType", sourceType.name(),
                "sourceRef", sourceRef == null ? "" : sourceRef,
                "amountMinor", amountMinor,
                "riskScore", score,
                "reasons", String.join(",", triggered))));
        return new Evaluation(score, triggered, true, alert.getAlertId());
    }

    // ── investigation lifecycle ──────────────────────────────────────────────

    public FraudAlert resolve(String alertId, String action, String notes) {
        FraudAlert alert = retrieve(alertId);
        if (alert.getStatus() != FraudAlert.Status.OPEN) {
            throw DomainException.rule("ALREADY_RESOLVED",
                    "alert is " + alert.getStatus() + "; resolution is terminal");
        }
        FraudAlert.Status next = switch (action == null ? "" : action.toLowerCase()) {
            case "confirm" -> FraudAlert.Status.CONFIRMED_FRAUD;
            case "dismiss" -> FraudAlert.Status.FALSE_POSITIVE;
            default -> throw DomainException.invalid("UNKNOWN_ACTION", "action must be confirm | dismiss");
        };
        alert.setStatus(next);
        alert.setResolutionNotes(notes);
        alert.setResolvedAt(clock.instant());
        repository.save(alert);
        events.publish(DomainEvent.of(TOPIC_ALERTS, "fraud.alert.resolved", Map.of(
                "alertId", alertId, "resolution", next.name(),
                "notes", notes == null ? "" : notes)));
        return alert;
    }

    // ── queries ──────────────────────────────────────────────────────────────

    public FraudAlert retrieve(String alertId) {
        return repository.findById(alertId)
                .orElseThrow(() -> DomainException.notFound("ALERT_UNKNOWN", "no alert " + alertId));
    }

    public Collection<FraudAlert> list(FraudAlert.Status status) {
        Collection<FraudAlert> all = repository.findAll();
        if (status == null) {
            return all;
        }
        return all.stream().filter(a -> a.getStatus() == status).toList();
    }
}
