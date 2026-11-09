# Delivery contract v0 over SMTP (#1206 step 5).
# Build context: the repository root (docker compose sets it), since the connector inherits the
# root POM: versions, plugins and the committed THIRD-PARTY.txt shipped under META-INF/licenses/ (#1278).
# Only the parent POMs and this module are copied; Maven builds the module alone with -f.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY .mvn .mvn
COPY pom.xml THIRD-PARTY.txt ./
COPY ezkey-connectors/pom.xml ezkey-connectors/pom.xml
COPY ezkey-connectors/ezkey-connector-smtp/pom.xml ezkey-connectors/ezkey-connector-smtp/pom.xml
RUN mvn -q -B -Pno-checkstyle -f ezkey-connectors/ezkey-connector-smtp/pom.xml dependency:go-offline
COPY ezkey-connectors/ezkey-connector-smtp/src ezkey-connectors/ezkey-connector-smtp/src
RUN mvn -q -B -Pno-checkstyle -DskipTests -f ezkey-connectors/ezkey-connector-smtp/pom.xml package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S connector && adduser -S connector -G connector
USER connector
WORKDIR /app
COPY --from=build /build/ezkey-connectors/ezkey-connector-smtp/target/ezkey-connector-smtp-*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
