-- Minimal schema for AuthAttemptRepository #727 Postgres IT (no encryption listener deps).
CREATE TABLE IF NOT EXISTS ezkey_auth_attempt (
    auth_attempt_id                          SERIAL PRIMARY KEY,
    enrollment_id                            INT NOT NULL,
    auth_attempt_status                      VARCHAR(32) NOT NULL,
    auth_attempt_challenge                   INT,
    auth_attempt_proof_token                 TEXT NOT NULL,
    auth_attempt_proof_token_encryption_key_id BIGINT,
    auth_attempt_proof_token_hash            VARCHAR(128),
    device_proof_token_hash                  VARCHAR(128),
    context_title                            VARCHAR(200),
    context_message                          VARCHAR(2000),
    waiter_secret_hash                       VARCHAR(64),
    session_issued_at                        TIMESTAMPTZ,
    created_at                               TIMESTAMPTZ NOT NULL,
    expires_at                               TIMESTAMPTZ NOT NULL
);
