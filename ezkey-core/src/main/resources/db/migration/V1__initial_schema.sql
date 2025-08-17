CREATE TABLE ezkey_integration (
    integration_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_logo VARCHAR(255),
    integration_active BOOLEAN DEFAULT TRUE NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE ezkey_integration_i18n (
    integration_i18n_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_id INT NOT NULL REFERENCES ezkey_integration(integration_id) ON DELETE CASCADE,
    integration_i18n_lang VARCHAR(8) NOT NULL,
    integration_i18n_name VARCHAR(255) NOT NULL,
    integration_i18n_description VARCHAR(255) NOT NULL,
    CONSTRAINT unique_integration_lang UNIQUE(integration_i18n_id, integration_i18n_lang)
);

CREATE TABLE ezkey_enrollment (
    enrollment_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    integration_id INT NOT NULL REFERENCES ezkey_integration(integration_id),
    enrollment_name VARCHAR(64) NOT NULL,
    enrollment_read BOOLEAN DEFAULT FALSE,
    enrollment_verified BOOLEAN DEFAULT FALSE,
    enrollment_valid BOOLEAN DEFAULT FALSE,
    enrollment_active BOOLEAN DEFAULT FALSE,
    enrollment_challenge INT DEFAULT NULL,
    enrollment_proof_token TEXT NOT NULL,
    auth_attempt_challenge_required BOOLEAN DEFAULT FALSE,
    integration_private_key TEXT NOT NULL,
    integration_public_key TEXT NOT NULL,
    device_public_key TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE ezkey_auth_attempt (
    auth_attempt_id INT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    enrollment_id INT NOT NULL REFERENCES ezkey_enrollment(enrollment_id),
    auth_attempt_read BOOLEAN DEFAULT FALSE,
    auth_attempt_responded BOOLEAN DEFAULT FALSE,
    auth_attempt_valid BOOLEAN DEFAULT FALSE,
    auth_attempt_accepted BOOLEAN DEFAULT FALSE,
    auth_attempt_challenge INT,
    auth_attempt_proof_token TEXT NULL,
    device_proof_token_valid BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);