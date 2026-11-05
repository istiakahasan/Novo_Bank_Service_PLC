FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 3006
CMD ["sh","-c","java -Dserver.port=${PORT:-3006} -jar app.jar"]
