#!/usr/bin/env bash
# Print Flyway schema history on EXP1 (remote postgres container).
set -euo pipefail
SSH_HOST="${LIGHTSAIL_SSH_HOST:-ezkey}"
ssh "${SSH_HOST}" 'docker exec ezkey-exp-postgres psql -U postgres -d ezkey_db -c "SELECT version, description, installed_on, success FROM flyway_schema_history ORDER BY installed_rank;"'
