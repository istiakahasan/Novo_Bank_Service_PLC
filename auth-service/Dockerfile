# Render: Root Directory = auth-service (no shared module needed)
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx300m -XX:+UseSerialGC"
EXPOSE 3007
CMD ["sh","-c","java -Dserver.port=${PORT:-3007} -jar app.jar"]
