#!/bin/sh
set -e
STORAGE="${JAVAMELODY_STORAGE_DIRECTORY:-/tmp/javamelody}"
mkdir -p "${STORAGE}"
if [ -f /opt/javamelody/applications.properties ]; then
  cp /opt/javamelody/applications.properties "${STORAGE}/applications.properties"
fi
exec java -Xmx256m -jar /opt/javamelody/collector.war
