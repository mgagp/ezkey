# JavaMelody collector server (opt-in local Docker diagnostics).
# Official executable WAR: https://github.com/javamelody/javamelody/releases
FROM eclipse-temurin:21-jre-alpine

# Align with parent POM javamelody.version (APIs use javamelody-spring-boot4-starter).
ARG JAVAMELODY_VERSION=2.9.0
# sha256 of javamelody-collector-server-2.9.0.war (GitHub release asset).
ARG JAVAMELODY_COLLECTOR_SHA256=f5004d7bbcd0298fe420f4cfedc136019d088e7f123a02b4ac5104bb228aa125

RUN apk add --no-cache curl \
    && mkdir -p /opt/javamelody /tmp/javamelody

ADD --checksum=sha256:${JAVAMELODY_COLLECTOR_SHA256} \
    https://github.com/javamelody/javamelody/releases/download/javamelody-core-${JAVAMELODY_VERSION}/javamelody-collector-server-${JAVAMELODY_VERSION}.war \
    /opt/javamelody/collector.war

WORKDIR /opt/javamelody

COPY entrypoint.sh /opt/javamelody/entrypoint.sh
RUN sed -i 's/\r$//' /opt/javamelody/entrypoint.sh && chmod +x /opt/javamelody/entrypoint.sh

EXPOSE 8080

VOLUME ["/tmp/javamelody"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["/opt/javamelody/entrypoint.sh"]
