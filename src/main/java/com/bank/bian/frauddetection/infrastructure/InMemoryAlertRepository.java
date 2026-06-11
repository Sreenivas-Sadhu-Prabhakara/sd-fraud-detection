package com.bank.bian.frauddetection.infrastructure;

import com.bank.bian.frauddetection.domain.ActivityEvent;
import com.bank.bian.frauddetection.domain.AlertRepository;
import com.bank.bian.frauddetection.domain.FraudAlert;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/** Phase 2 adapter. The activity log doubles as the velocity-rule window. */
@Repository
public class InMemoryAlertRepository implements AlertRepository {

    private final Map<String, FraudAlert> alerts = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedDeque<ActivityEvent>> activities = new ConcurrentHashMap<>();

    @Override
    public void save(FraudAlert alert) {
        alerts.put(alert.getAlertId(), alert);
    }

    @Override
    public Optional<FraudAlert> findById(String alertId) {
        return Optional.ofNullable(alerts.get(alertId));
    }

    @Override
    public Collection<FraudAlert> findAll() {
        return alerts.values();
    }

    @Override
    public void recordActivity(ActivityEvent event) {
        ConcurrentLinkedDeque<ActivityEvent> deque =
                activities.computeIfAbsent(event.accountRef(), k -> new ConcurrentLinkedDeque<>());
        deque.addLast(event);
        // bound the window memory: nothing older than an hour is rule-relevant
        Instant cutoff = event.observedAt().minusSeconds(3600);
        while (!deque.isEmpty() && deque.peekFirst().observedAt().isBefore(cutoff)) {
            deque.pollFirst();
        }
    }

    @Override
    public int countActivitiesSince(String accountRef, Instant since) {
        return (int) activities.getOrDefault(accountRef, new ConcurrentLinkedDeque<>()).stream()
                .filter(e -> !e.observedAt().isBefore(since))
                .count();
    }
}
