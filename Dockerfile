# Build context = repository root (default). Builds the shared security-starter first.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q -f security-starter/pom.xml install -DskipTests && ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 8080
CMD ["sh","-c","java -Dserver.port=${PORT:-8080} -jar app.jar"]
