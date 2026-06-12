package com.bank.bian.frauddetection.events;

import com.bank.bian.frauddetection.domain.FraudAlert;
import com.bank.bian.frauddetection.domain.FraudDetectionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 2d-ii loop closure (profile `kafka`): the fraud flagship feed goes live.
 * Replaces the HTTP /evaluate bridge for machine traffic — same evaluation
 * path, the events now arrive on the backbone:
 *
 *   bian.accounts.current-account / bian.accounts.savings-account
 *       type=transaction.posted  → score as TRANSACTION
 *   bian.cheques.lifecycle
 *       type=cheque.lodged       → score as CHEQUE
 *
 * Malformed messages are logged and skipped — the fraud feed must never
 * wedge on one bad event.
 */
@Component
@Profile("kafka")
public class ActivityEventConsumer {

    private static final Logger log = LoggerFactory.getLogger("bian.fraud-feed");

    private final FraudDetectionService service;
    private final ObjectMapper mapper = new ObjectMapper();

    public ActivityEventConsumer(FraudDetectionService service) {
        this.service = service;
    }

    @KafkaListener(topics = {"bian.accounts.current-account", "bian.accounts.savings-account"},
            groupId = "sd-fraud-detection")
    public void onAccountEvent(String message) {
        handleAccountEvent(message);
    }

    @KafkaListener(topics = "bian.cheques.lifecycle", groupId = "sd-fraud-detection")
    public void onChequeEvent(String message) {
        handleChequeEvent(message);
    }

    /** package-visible for direct unit testing without a broker */
    void handleAccountEvent(String message) {
        try {
            JsonNode e = mapper.readTree(message);
            if (!"transaction.posted".equals(e.path("type").asText())) {
                return;
            }
            JsonNode p = e.path("payload");
            service.evaluate(
                    p.path("accountId").asText(),
                    FraudAlert.SourceType.TRANSACTION,
                    p.path("transactionId").asText(),
                    Math.abs(p.path("amountMinor").asLong()),
                    p.path("currency").asText("INR"));
        } catch (Exception ex) {
            log.warn("skipping malformed account event: {}", ex.getMessage());
        }
    }

    void handleChequeEvent(String message) {
        try {
            JsonNode e = mapper.readTree(message);
            if (!"cheque.lodged".equals(e.path("type").asText())) {
                return;
            }
            JsonNode p = e.path("payload");
            service.evaluate(
                    p.path("beneficiaryAccountRef").asText(),
                    FraudAlert.SourceType.CHEQUE,
                    p.path("chequeId").asText(),
                    p.path("amountMinor").asLong(),
                    p.path("currency").asText("INR"));
        } catch (Exception ex) {
            log.warn("skipping malformed cheque event: {}", ex.getMessage());
        }
    }
}
