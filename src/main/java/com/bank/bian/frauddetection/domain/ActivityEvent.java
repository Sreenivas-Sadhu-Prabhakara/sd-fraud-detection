package com.bank.bian.frauddetection.domain;

import java.time.Instant;

/**
 * One inbound activity to score: an account posting (transaction.posted) or a
 * cheque lodgement (cheque.lodged). Arrives via the /evaluate bridge endpoint
 * today; via Kafka consumers when the backbone is live.
 */
public record ActivityEvent(
        String accountRef,
        FraudAlert.SourceType sourceType,
        String sourceRef,
        long amountMinor,
        String currency,
        Instant observedAt
) {}
