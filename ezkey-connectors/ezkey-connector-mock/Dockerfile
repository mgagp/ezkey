# Test double for the delivery contract v0. Never delivers anything.
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S mock && adduser -S mock -G mock
USER mock
WORKDIR /app
COPY --from=build /build/target/ezkey-connector-mock-*.jar app.jar
EXPOSE 8090
ENTRYPOINT ["java", "-jar", "app.jar"]
