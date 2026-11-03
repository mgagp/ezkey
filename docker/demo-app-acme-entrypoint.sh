#!/bin/sh
# Create application.properties from template if it does not exist
# Note: If volume persists, existing file will NOT be overwritten
# To reset: Delete /app/config/application.properties before starting container
CONFIG=/app/config/application.properties
# New files from cp must not be world-readable before chmod (access-code secrets live here).
umask 077
if [ ! -f "$CONFIG" ]; then
  echo "Creating /app/config/application.properties from template..."
  cp /app/config-template.properties "$CONFIG"
else
  echo "Config file exists, skipping template copy (volume persistence)"
fi
# Fail-closed: refuse to start unless mode is 0600 (plaintext access-code secrets on this volume).
# spring user is uid 100 / gid 101 in this image.
if ! chmod 600 "$CONFIG"; then
  echo "ERROR: could not chmod 600 $CONFIG (file may be owned by root; fix ownership or mode on the host volume). Refusing to start." >&2
  exit 1
fi
mode=$(stat -c '%a' "$CONFIG" 2>/dev/null || true)
if [ -z "$mode" ]; then
  mode=$(stat -f '%OLp' "$CONFIG" 2>/dev/null || true)
fi
if [ "$mode" != "600" ]; then
  echo "ERROR: $CONFIG mode is '${mode:-unknown}', expected 600. Refusing to start." >&2
  exit 1
fi
echo "SPRING_CONFIG_ADDITIONAL_LOCATION=$SPRING_CONFIG_ADDITIONAL_LOCATION"
echo "Working directory: $(pwd)"
echo "Config file exists: $([ -f "$CONFIG" ] && echo yes || echo no)"
echo "Config file mode: $mode"
exec java -jar /app/app.jar
