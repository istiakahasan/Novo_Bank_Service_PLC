# Render: Docker runtime, Root Directory = auth-server (Render has no native Java runtime;
# these are the same commands as "mvn clean package -DskipTests" and "java -jar target/*.jar").
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx320m -XX:+UseSerialGC"
EXPOSE 3008
CMD ["sh","-c","java -Dserver.port=${PORT:-3008} -jar app.jar"]
