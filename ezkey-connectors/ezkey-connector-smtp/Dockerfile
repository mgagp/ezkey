# Delivery contract v1 over SMTP (#1206 step 5).
# Build context: the repository root (docker compose sets it), since the connector inherits the
# root POM: versions, plugins and the committed THIRD-PARTY.txt shipped under META-INF/licenses/ (#1278).
# Only the parent POMs and this module are copied; Maven builds the module alone with -f.
# External bases use tag@sha256 of the multi-arch index so Dependabot can bump tag and digest.
FROM maven:3.9-eclipse-temurin-25@sha256:93b8a14ea2f412782e4e842651273b4d903e35cc496284f178fbbe2d67d00976 AS build
WORKDIR /build
COPY .mvn .mvn
COPY pom.xml THIRD-PARTY.txt ./
COPY ezkey-connectors/pom.xml ezkey-connectors/pom.xml
COPY ezkey-connectors/ezkey-connector-smtp/pom.xml ezkey-connectors/ezkey-connector-smtp/pom.xml
RUN mvn -q -B -Pno-checkstyle -f ezkey-connectors/ezkey-connector-smtp/pom.xml dependency:go-offline
COPY ezkey-connectors/ezkey-connector-smtp/src ezkey-connectors/ezkey-connector-smtp/src
RUN mvn -q -B -Pno-checkstyle -DskipTests -f ezkey-connectors/ezkey-connector-smtp/pom.xml package

FROM eclipse-temurin:25-jre-alpine@sha256:e1898fd6a11fc965a994d8ace186fd3585aeb636cf8f05f47a723fcfee0b0918
RUN addgroup -S connector && adduser -S connector -G connector
USER connector
WORKDIR /app
COPY --from=build /build/ezkey-connectors/ezkey-connector-smtp/target/ezkey-connector-smtp-*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
