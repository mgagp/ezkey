#!/bin/sh
# Create application.properties from template if it does not exist
# Note: If volume persists, existing file will NOT be overwritten
# To reset: Delete /app/config/application.properties before starting container
if [ ! -f /app/config/application.properties ]; then
  echo "Creating /app/config/application.properties from template..."
  cp /app/config-template.properties /app/config/application.properties
else
  echo "Config file exists, skipping template copy (volume persistence)"
fi
# External tenant-alias mapping (#958): create empty file when missing so Edgar can append.
# application.properties stays the base; this file holds only alias → ikey/skey/label lines.
if [ ! -f /app/config/tenant-aliases.properties ]; then
  echo "Creating empty /app/config/tenant-aliases.properties for hot-reloaded evaluator mappings..."
  umask 077
  cat > /app/config/tenant-aliases.properties <<'EOF'
# External tenant-alias mappings for Acme demo (community). Hot-reloaded on /t/{alias} access.
# Format (one mapping per line): alias|integration-key|secret-key|label
# alias = 16 lowercase hex (UUID v4 without dashes, first 16 chars). Never commit secrets.
# Append with temp file + mv as user spring (uid 100). Mode 0600. See docs/lightsail/community/.
EOF
else
  echo "Tenant-alias mapping file exists, skipping create (volume persistence)"
fi
# Tighten permissions on every start (fixes existing 0644 volumes; no root needed).
# spring user is uid 100 / gid 101 in this image.
if ! chmod 600 /app/config/application.properties; then
  echo "WARN: could not chmod 600 /app/config/application.properties (file may be owned by root; fix ownership or mode on the host volume)" >&2
fi
if ! chmod 600 /app/config/tenant-aliases.properties; then
  echo "WARN: could not chmod 600 /app/config/tenant-aliases.properties (file may be owned by root; fix ownership or mode on the host volume)" >&2
fi
echo "SPRING_CONFIG_ADDITIONAL_LOCATION=$SPRING_CONFIG_ADDITIONAL_LOCATION"
echo "Working directory: $(pwd)"
echo "Config file exists: $([ -f /app/config/application.properties ] && echo yes || echo no)"
echo "Tenant-alias file exists: $([ -f /app/config/tenant-aliases.properties ] && echo yes || echo no)"
exec java -jar /app/app.jar
