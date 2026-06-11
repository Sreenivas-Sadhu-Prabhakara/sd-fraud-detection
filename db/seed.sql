-- Sample data for local exploration after hydration. Idempotent.
INSERT INTO fraud_alert (alert_id, account_ref, source_type, source_ref, amount_minor,
                         currency, risk_score, reasons, status, resolution_notes, raised_at, resolved_at)
VALUES
    ('FA-SEED-0001', 'CA-SEED-0001', 'TRANSACTION', 'TX-SEED-0001', 2500000, 'INR', 95,
        'LARGE_AMOUNT,ROUND_AMOUNT', 'OPEN', NULL, now() - interval '2 hours', NULL),
    ('FA-SEED-0002', 'CA-SEED-0002', 'CHEQUE', 'CHQ-SEED-0002', 1200000, 'INR', 70,
        'LARGE_AMOUNT', 'FALSE_POSITIVE', 'verified with customer', now() - interval '1 day', now() - interval '20 hours')
ON CONFLICT (alert_id) DO NOTHING;
