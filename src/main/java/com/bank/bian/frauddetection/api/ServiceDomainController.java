package com.bank.bian.frauddetection.api;

import com.bank.bian.frauddetection.domain.DomainException;
import com.bank.bian.frauddetection.domain.FraudAlert;
import com.bank.bian.frauddetection.domain.FraudDetectionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.Map;

/**
 * BIAN semantic API for "Fraud Detection" — Phase 2b-c, real domain.
 * Control record: Fraud Alert Monitoring State (Monitor pattern).
 *
 * /evaluate is the flagship ingest bridge: account and cheque SDs feed
 * transaction.posted / cheque.lodged shapes here over HTTP until the Kafka
 * consumers take over (same evaluation path either way).
 *
 * Contract: api/openapi.yaml (owned by this repo).
 */
@RestController
@RequestMapping("/v1")
public class ServiceDomainController {

    static final String CR = "fraud-alert-monitoring-state";

    private final FraudDetectionService service;

    public ServiceDomainController(FraudDetectionService service) {
        this.service = service;
    }

    @GetMapping("/service-domain")
    public Map<String, String> serviceDomain() {
        return Map.of(
                "serviceDomain", "Fraud Detection",
                "businessArea", "Risk and Compliance",
                "businessDomain", "Financial Crime",
                "functionalPattern", "Monitor",
                "assetType", "Fraud Alert",
                "controlRecord", "Fraud Alert Monitoring State",
                "version", "0.2.0",
                "phase", "2b-deep"
        );
    }

    // ── the ingest/scoring bridge ────────────────────────────────────────────

    public record EvaluateRequest(String accountRef, String sourceType, String sourceRef,
                                  long amountMinor, String currency) {}

    @PostMapping("/" + CR + "/evaluate")
    public FraudDetectionService.Evaluation evaluate(@RequestBody EvaluateRequest req) {
        FraudAlert.SourceType type;
        try {
            type = FraudAlert.SourceType.valueOf(
                    (req.sourceType() == null ? "TRANSACTION" : req.sourceType()).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("UNKNOWN_SOURCE_TYPE", "sourceType must be TRANSACTION | CHEQUE");
        }
        return service.evaluate(req.accountRef(), type, req.sourceRef(),
                req.amountMinor(), req.currency() == null ? "INR" : req.currency());
    }

    // ── alert lifecycle ──────────────────────────────────────────────────────

    @GetMapping("/" + CR)
    public Collection<FraudAlert> list(@RequestParam(required = false) String status) {
        if (status == null) {
            return service.list(null);
        }
        try {
            return service.list(FraudAlert.Status.valueOf(status.toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw DomainException.invalid("UNKNOWN_STATUS",
                    "status must be OPEN | CONFIRMED_FRAUD | FALSE_POSITIVE");
        }
    }

    @GetMapping("/" + CR + "/{alertId}/retrieve")
    public FraudAlert retrieve(@PathVariable String alertId) {
        return service.retrieve(alertId);
    }

    @PutMapping("/" + CR + "/{alertId}/control")
    public ResponseEntity<FraudAlert> control(@PathVariable String alertId,
                                              @RequestBody Map<String, String> body) {
        return ResponseEntity.status(HttpStatus.OK)
                .body(service.resolve(alertId, body.get("action"), body.get("notes")));
    }
}
