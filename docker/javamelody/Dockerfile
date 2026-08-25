# JavaMelody collector server (opt-in local Docker diagnostics).
# Official executable WAR: https://github.com/javamelody/javamelody/releases
FROM eclipse-temurin:21-jre-alpine

ARG JAVAMELODY_VERSION=2.8.0

RUN apk add --no-cache curl \
    && mkdir -p /opt/javamelody /tmp/javamelody

ADD https://github.com/javamelody/javamelody/releases/download/javamelody-core-${JAVAMELODY_VERSION}/javamelody-collector-server-${JAVAMELODY_VERSION}.war \
    /opt/javamelody/collector.war

WORKDIR /opt/javamelody

COPY entrypoint.sh /opt/javamelody/entrypoint.sh
RUN sed -i 's/\r$//' /opt/javamelody/entrypoint.sh && chmod +x /opt/javamelody/entrypoint.sh

EXPOSE 8080

VOLUME ["/tmp/javamelody"]

HEALTHCHECK --interval=15s --timeout=5s --start-period=40s --retries=5 \
    CMD curl -f http://localhost:8080/ || exit 1

ENTRYPOINT ["/opt/javamelody/entrypoint.sh"]
