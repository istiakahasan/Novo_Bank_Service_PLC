# Build context must be the REPOSITORY ROOT (Render: Root Directory blank, Dockerfile Path fraud-service/Dockerfile,
# Docker Build Context Directory "."), because this service depends on the shared security-starter module.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /src
COPY security-starter/ security-starter/
COPY fraud-service/ fraud-service/
WORKDIR /src/fraud-service
RUN chmod +x mvnw && ./mvnw -q -f ../security-starter/pom.xml install -DskipTests && ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /src/fraud-service/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 3005
CMD ["sh","-c","java -Dserver.port=${PORT:-3005} -jar app.jar"]
