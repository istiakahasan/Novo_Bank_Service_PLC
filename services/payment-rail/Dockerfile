# syntax=docker/dockerfile:1
# Build context: the REPOSITORY ROOT (docker-compose.yml sets context: . and dockerfile: services/payment-rail/Dockerfile).
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
ENV GH_PACKAGES_USER=${GH_PACKAGES_USER} GH_PACKAGES_TOKEN=${GH_PACKAGES_TOKEN}
COPY services/settings.xml /root/.m2/settings.xml

# 1) the shared in-repo library (cached layers: pom first, then sources)
COPY security-starter/pom.xml security-starter/pom.xml
RUN mvn -B -q -f security-starter/pom.xml dependency:go-offline
COPY security-starter/src security-starter/src
RUN mvn -B -q -f security-starter/pom.xml install -DskipTests

# 2) the service
COPY payment-rail-service/pom.xml payment-rail-service/pom.xml
RUN mvn -B -q -f payment-rail-service/pom.xml dependency:go-offline
COPY payment-rail-service/src payment-rail-service/src
RUN mvn -B -q -f payment-rail-service/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/payment-rail-service/target/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
EXPOSE 3012
ENTRYPOINT ["java", "-jar", "app.jar"]
