package com.bank.bian.frauddetection.events;

import com.bank.bian.frauddetection.domain.FraudDetectionService;
import com.bank.bian.frauddetection.infrastructure.InMemoryAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The Kafka feed handlers, exercised directly (no broker needed). */
class ActivityEventConsumerTest {

    List<DomainEvent> published;
    ActivityEventConsumer consumer;

    @BeforeEach
    void setUp() {
        published = new ArrayList<>();
        FraudDetectionService service = new FraudDetectionService(
                new InMemoryAlertRepository(), published::add,
                1_000_000, 5, Duration.ofMinutes(10), 60, Clock.systemUTC());
        consumer = new ActivityEventConsumer(service);
    }

    @Test
    void largeTransactionEventOnTheWireRaisesAnAlert() {
        consumer.handleAccountEvent("""
            {"eventId":"EVT-1","topic":"bian.accounts.current-account","type":"transaction.posted",
             "payload":{"transactionId":"TX-9","accountId":"CA-W1","type":"DEPOSIT",
                        "amountMinor":5000000,"balanceAfterMinor":5000000,"currency":"INR"}}""");
        assertThat(published).extracting(DomainEvent::type).contains("fraud.alert.raised");
    }

    @Test
    void withdrawalAmountsAreScoredOnMagnitude() {
        consumer.handleAccountEvent("""
            {"type":"transaction.posted","payload":{"transactionId":"TX-10","accountId":"CA-W2",
             "amountMinor":-5000000,"currency":"INR"}}""");
        assertThat(published).extracting(DomainEvent::type).contains("fraud.alert.raised");
    }

    @Test
    void chequeLodgementEventIsScoredAsChequeSource() {
        consumer.handleChequeEvent("""
            {"type":"cheque.lodged","payload":{"chequeId":"CHQ-7","chequeNumber":"123456",
             "drawerAccountRef":"CA-D","beneficiaryAccountRef":"CA-B","amountMinor":2000000,"currency":"INR"}}""");
        assertThat(published).extracting(DomainEvent::type).contains("fraud.alert.raised");
    }

    @Test
    void irrelevantAndMalformedEventsAreSkippedQuietly() {
        consumer.handleAccountEvent("{\"type\":\"account.opened\",\"payload\":{}}");
        consumer.handleAccountEvent("not json at all");
        assertThat(published).isEmpty();
    }
}
