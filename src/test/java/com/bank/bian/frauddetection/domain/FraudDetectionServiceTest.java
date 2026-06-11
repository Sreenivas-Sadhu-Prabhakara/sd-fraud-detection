package com.bank.bian.frauddetection.domain;

import com.bank.bian.frauddetection.events.DomainEvent;
import com.bank.bian.frauddetection.events.EventPublisher;
import com.bank.bian.frauddetection.infrastructure.InMemoryAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The scoring rules, proven case by case. Threshold 60; LARGE 70, VELOCITY 50, ROUND 25. */
class FraudDetectionServiceTest {

    static class RecordingPublisher implements EventPublisher {
        final List<DomainEvent> events = new ArrayList<>();
        @Override public void publish(DomainEvent event) { events.add(event); }
    }

    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-06-15T10:00:00Z");
        @Override public java.time.ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    RecordingPublisher events;
    MutableClock clock;
    FraudDetectionService service;

    @BeforeEach
    void setUp() {
        events = new RecordingPublisher();
        clock = new MutableClock();
        // large >= 1_000_000 minor; velocity: >5 prior events in 10 min; threshold 60
        service = new FraudDetectionService(new InMemoryAlertRepository(), events,
                1_000_000, 5, Duration.ofMinutes(10), 60, clock);
    }

    FraudDetectionService.Evaluation eval(String account, long amount) {
        return service.evaluate(account, FraudAlert.SourceType.TRANSACTION, "TX-x", amount, "INR");
    }

    @Nested
    class Scoring {
        @Test
        void smallCleanTransactionScoresZeroNoAlert() {
            var r = eval("CA-1", 4_999);
            assertThat(r.riskScore()).isZero();
            assertThat(r.alertRaised()).isFalse();
            assertThat(events.events).isEmpty();
        }

        @Test
        void largeAmountAloneRaisesAnAlert() {
            var r = eval("CA-2", 1_000_000); // exactly the threshold amount
            assertThat(r.triggeredRules()).contains("LARGE_AMOUNT");
            assertThat(r.riskScore()).isGreaterThanOrEqualTo(70);
            assertThat(r.alertRaised()).isTrue();
            assertThat(events.events.get(0).type()).isEqualTo("fraud.alert.raised");
        }

        @Test
        void roundAmountAloneIsASignalButNotAnAlert() {
            var r = eval("CA-3", 500_000); // round, half the large threshold
            assertThat(r.triggeredRules()).containsExactly("ROUND_AMOUNT");
            assertThat(r.riskScore()).isEqualTo(25);
            assertThat(r.alertRaised()).isFalse();
        }

        @Test
        void velocityAloneIsASignalButNotAnAlert_velocityPlusRoundAlerts() {
            // 5 small odd-amount events fill the window (none trigger anything)
            for (int i = 0; i < 5; i++) {
                eval("CA-4", 1_001);
                clock.now = clock.now.plusSeconds(30);
            }
            // 6th event: VELOCITY(50) + ROUND(25) = 75 → alert
            var r = eval("CA-4", 500_000);
            assertThat(r.triggeredRules()).containsExactlyInAnyOrder("VELOCITY", "ROUND_AMOUNT");
            assertThat(r.alertRaised()).isTrue();
        }

        @Test
        void velocityWindowSlides_burstLongAgoDoesNotCount() {
            for (int i = 0; i < 5; i++) {
                eval("CA-5", 1_001);
            }
            clock.now = clock.now.plus(Duration.ofMinutes(11)); // window expired
            var r = eval("CA-5", 500_000);
            assertThat(r.triggeredRules()).containsExactly("ROUND_AMOUNT");
            assertThat(r.alertRaised()).isFalse();
        }
    }

    @Nested
    class Resolution {
        @Test
        void openAlertCanBeConfirmedOnceOnly() {
            var r = eval("CA-6", 2_000_000);
            FraudAlert resolved = service.resolve(r.alertId(), "confirm", "card stolen");
            assertThat(resolved.getStatus()).isEqualTo(FraudAlert.Status.CONFIRMED_FRAUD);
            assertThatThrownBy(() -> service.resolve(r.alertId(), "dismiss", "oops"))
                    .isInstanceOf(DomainException.class)
                    .hasMessageContaining("terminal");
        }

        @Test
        void dismissMarksFalsePositive() {
            var r = eval("CA-7", 2_000_000);
            assertThat(service.resolve(r.alertId(), "dismiss", "verified with customer").getStatus())
                    .isEqualTo(FraudAlert.Status.FALSE_POSITIVE);
        }
    }
}
