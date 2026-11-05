# syntax=docker/dockerfile:1
# Build context: the REPOSITORY ROOT (docker-compose.yml sets context: . and dockerfile: services/auth/Dockerfile).
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
ARG GH_PACKAGES_USER
ARG GH_PACKAGES_TOKEN
ENV GH_PACKAGES_USER=${GH_PACKAGES_USER} GH_PACKAGES_TOKEN=${GH_PACKAGES_TOKEN}
COPY services/settings.xml /root/.m2/settings.xml

# this service has no in-repo library dependency
COPY auth-server/pom.xml auth-server/pom.xml
RUN mvn -B -q -f auth-server/pom.xml dependency:go-offline
COPY auth-server/src auth-server/src
RUN mvn -B -q -f auth-server/pom.xml package -DskipTests

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=builder /build/auth-server/target/*.jar app.jar
USER app
ENV JAVA_TOOL_OPTIONS="-Xmx256m -XX:+UseSerialGC"
EXPOSE 3008
ENTRYPOINT ["java", "-jar", "app.jar"]
