package com.bank.bian.frauddetection.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

/** Persistence port — in-memory now, Postgres when the platform hydrates. */
public interface AlertRepository {

    void save(FraudAlert alert);

    Optional<FraudAlert> findById(String alertId);

    Collection<FraudAlert> findAll();

    /** Record an observed activity (the velocity rule's evidence base). */
    void recordActivity(ActivityEvent event);

    /** Activities for one account observed at or after the given instant. */
    int countActivitiesSince(String accountRef, Instant since);
}
