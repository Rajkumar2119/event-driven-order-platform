# Build from the repository root:  docker build -f payment-service/Dockerfile -t payment-service .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml .
COPY common-events common-events
COPY order-service/pom.xml order-service/pom.xml
COPY payment-service/pom.xml payment-service/pom.xml
COPY payment-service/src payment-service/src
RUN mvn -B -q -pl payment-service -am package -DskipTests \
 && cp payment-service/target/payment-service-1.0.0.jar /workspace/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
