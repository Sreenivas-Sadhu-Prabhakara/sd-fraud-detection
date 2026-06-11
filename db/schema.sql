-- Fraud Detection service domain — Postgres schema.
-- READY TO HYDRATE, NOT YET WIRED (see platform-infra/postgres/hydrate.sh).

CREATE TABLE IF NOT EXISTS fraud_alert (
    alert_id         VARCHAR(40)  PRIMARY KEY,
    account_ref      VARCHAR(40)  NOT NULL,
    source_type      VARCHAR(12)  NOT NULL CHECK (source_type IN ('TRANSACTION','CHEQUE')),
    source_ref       VARCHAR(40),
    amount_minor     BIGINT       NOT NULL CHECK (amount_minor > 0),
    currency         CHAR(3)      NOT NULL,
    risk_score       INTEGER      NOT NULL CHECK (risk_score BETWEEN 0 AND 200),
    reasons          TEXT         NOT NULL,           -- comma-joined rule codes
    status           VARCHAR(16)  NOT NULL
        CHECK (status IN ('OPEN','CONFIRMED_FRAUD','FALSE_POSITIVE')),
    resolution_notes VARCHAR(280),
    raised_at        TIMESTAMPTZ  NOT NULL,
    resolved_at      TIMESTAMPTZ,
    CONSTRAINT resolved_has_timestamp CHECK (status = 'OPEN' OR resolved_at IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_alert_account ON fraud_alert (account_ref);
CREATE INDEX IF NOT EXISTS idx_alert_status  ON fraud_alert (status);

-- evidence base for the velocity rule
CREATE TABLE IF NOT EXISTS activity_event (
    id           BIGSERIAL    PRIMARY KEY,
    account_ref  VARCHAR(40)  NOT NULL,
    source_type  VARCHAR(12)  NOT NULL,
    source_ref   VARCHAR(40),
    amount_minor BIGINT       NOT NULL,
    currency     CHAR(3)      NOT NULL,
    observed_at  TIMESTAMPTZ  NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_activity_account_time ON activity_event (account_ref, observed_at DESC);
