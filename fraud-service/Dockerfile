FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY . .
RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-Xmx384m -XX:+UseSerialGC"
EXPOSE 3005
CMD ["sh","-c","java -Dserver.port=${PORT:-3005} -jar app.jar"]
