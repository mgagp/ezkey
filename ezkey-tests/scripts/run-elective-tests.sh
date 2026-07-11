#!/bin/bash
#
# Ezkey Elective Tests Runner
#
# Runs all elective (on-demand spot-check) tests. These tests are excluded from
# standard CI/build runs and must be invoked explicitly.
#
# Elective tests include:
#   - AuditIntegrityElectiveTest (audit log HMAC integrity, chain checkpoints)
#   - AuditLifecycleArchiveEligibilityElectiveTest (archive lifecycle eligibility spot check)
#   - EncryptionIntegrityElectiveTest (enrollment proof token encryption at rest)
#   - KeyRotationSyncWindowTest (key rotation sync window)
#   - ReencryptionFullTriggerConcurrentActivityElectiveTest (full re-encryption under concurrent DB churn)
#   - ShedLockDistributedTest (distributed locking / HA)
#
# Prerequisites:
#   - Docker stack running (e.g. ./clean-start.sh)
#   - Maven
#
# Usage:
#   ./scripts/run-elective-tests.sh
#
# From project root:
#   ezkey-tests/scripts/run-elective-tests.sh
#

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
EZKEY_TESTS_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
PROJECT_ROOT="$(cd "$EZKEY_TESTS_DIR/.." && pwd)"

echo -e "${BLUE}=== Ezkey Elective Tests Spot Check ===${NC}"
echo -e "${BLUE}[INFO]${NC} Project root: $PROJECT_ROOT"
echo -e "${BLUE}[INFO]${NC} Running elective tests (AuditIntegrityElectiveTest, AuditLifecycleArchiveEligibilityElectiveTest, EncryptionIntegrityElectiveTest, KeyRotationSyncWindowTest, ReencryptionFullTriggerConcurrentActivityElectiveTest, ShedLockDistributedTest)..."
echo

cd "$PROJECT_ROOT"
mvn test -pl ezkey-tests -P elective-tests

EXIT_CODE=$?
echo
if [ "$EXIT_CODE" -eq 0 ]; then
  echo -e "${GREEN}[SUCCESS]${NC} All elective tests passed."
else
  echo -e "${RED}[FAILURE]${NC} Elective tests failed (exit code: $EXIT_CODE)."
fi
exit "$EXIT_CODE"
