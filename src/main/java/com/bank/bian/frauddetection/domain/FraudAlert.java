package com.bank.bian.frauddetection.domain;

import java.time.Instant;
import java.util.List;

/**
 * Control record made real: "Fraud Alert Monitoring State" — one raised alert
 * and its investigation lifecycle: OPEN → CONFIRMED_FRAUD | FALSE_POSITIVE.
 */
public class FraudAlert {

    public enum Status { OPEN, CONFIRMED_FRAUD, FALSE_POSITIVE }

    public enum SourceType { TRANSACTION, CHEQUE }

    private String alertId;
    private String accountRef;
    private SourceType sourceType;
    private String sourceRef;          // transactionId or chequeId
    private long amountMinor;
    private String currency;
    private int riskScore;
    private List<String> reasons;      // triggered rule codes
    private Status status = Status.OPEN;
    private String resolutionNotes;
    private Instant raisedAt;
    private Instant resolvedAt;

    public static FraudAlert raise(String alertId, String accountRef, SourceType sourceType,
                                   String sourceRef, long amountMinor, String currency,
                                   int riskScore, List<String> reasons, Instant now) {
        FraudAlert a = new FraudAlert();
        a.alertId = alertId;
        a.accountRef = accountRef;
        a.sourceType = sourceType;
        a.sourceRef = sourceRef;
        a.amountMinor = amountMinor;
        a.currency = currency;
        a.riskScore = riskScore;
        a.reasons = List.copyOf(reasons);
        a.raisedAt = now;
        return a;
    }

    public String getAlertId() { return alertId; }
    public String getAccountRef() { return accountRef; }
    public SourceType getSourceType() { return sourceType; }
    public String getSourceRef() { return sourceRef; }
    public long getAmountMinor() { return amountMinor; }
    public String getCurrency() { return currency; }
    public int getRiskScore() { return riskScore; }
    public List<String> getReasons() { return reasons; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResolutionNotes() { return resolutionNotes; }
    public void setResolutionNotes(String resolutionNotes) { this.resolutionNotes = resolutionNotes; }
    public Instant getRaisedAt() { return raisedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
