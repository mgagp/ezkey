#!/bin/bash
# Export integration_public_key from ezkey_enrollment for use as Kotlin unit test fixtures.
# Golden fixture: enrollment_id = 3 (Jean-Martin). Requires running ezkey-postgres container.
#
# Usage:
#   ./scripts/export-mobile-crypto-fixture.sh [enrollment_id]
#   ./scripts/export-mobile-crypto-fixture.sh 3              # default, Jean-Martin
#   ./scripts/export-mobile-crypto-fixture.sh 3 --write      # write to fixture file
#
# Without --write: prints integration_public_key to stdout (copy-paste into test constant).
# With --write: writes to ezkey_mobile/android/app/src/test/resources/fixtures/integration_public_key_base64.txt

ENROLLMENT_ID=${1:-3}
CONTAINER_NAME="ezkey-postgres"
WRITE_TO_FILE=false
if [[ "${2:-}" == "--write" ]] || [[ "${1:-}" == "--write" ]]; then
  WRITE_TO_FILE=true
  [[ "${1:-}" == "--write" ]] && ENROLLMENT_ID=3
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
FIXTURE_DIR="$REPO_ROOT/ezkey_mobile/android/app/src/test/resources/fixtures"
FIXTURE_FILE="$FIXTURE_DIR/integration_public_key_base64.txt"

if ! docker ps --format '{{.Names}}' | grep -q "^${CONTAINER_NAME}$"; then
  echo "Error: Container '$CONTAINER_NAME' not found or not running." >&2
  echo "Start the stack (e.g. docker compose up -d) then run this script again." >&2
  exit 1
fi

# Export integration_public_key (one line, no header for easy copy or file write)
OUTPUT=$(docker exec "$CONTAINER_NAME" psql -U postgres -d ezkey_db -t -A -c \
  "SELECT integration_public_key FROM ezkey_enrollment WHERE enrollment_id = $ENROLLMENT_ID;")

if [[ -z "$OUTPUT" ]]; then
  echo "Error: No row found for enrollment_id = $ENROLLMENT_ID." >&2
  exit 1
fi

if [[ "$WRITE_TO_FILE" == true ]]; then
  mkdir -p "$FIXTURE_DIR"
  echo -n "$OUTPUT" > "$FIXTURE_FILE"
  echo "Wrote integration_public_key (enrollment_id=$ENROLLMENT_ID) to:"
  echo "  $FIXTURE_FILE"
  echo "Length: ${#OUTPUT} chars"
else
  echo "$OUTPUT"
fi
