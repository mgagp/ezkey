# Delivery contract v0 over SMTP (#1206 step 5).
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S connector && adduser -S connector -G connector
USER connector
WORKDIR /app
COPY --from=build /build/target/ezkey-connector-smtp-*.jar app.jar
EXPOSE 8091
ENTRYPOINT ["java", "-jar", "app.jar"]
