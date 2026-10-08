#!/bin/sh
# Create application.properties from template if it does not exist
# Note: If volume persists, existing file will NOT be overwritten
# To reset: Delete /app/config/application.properties before starting container
if [ ! -f /app/config/application.properties ]; then
  echo "Creating /app/config/application.properties from template..."
  cp /app/config-template.properties /app/config/application.properties
  chmod 600 /app/config/application.properties
else
  echo "Config file exists, skipping template copy (volume persistence)"
fi
# Unconditional: tighten permissions on every start (fixes existing 0644 volumes; no root needed).
chmod 600 /app/config/application.properties 2>/dev/null || true
echo "SPRING_CONFIG_ADDITIONAL_LOCATION=$SPRING_CONFIG_ADDITIONAL_LOCATION"
echo "Working directory: $(pwd)"
echo "Config file exists: $([ -f /app/config/application.properties ] && echo yes || echo no)"
exec java -jar /app/app.jar
