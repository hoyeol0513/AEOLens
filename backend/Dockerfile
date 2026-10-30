FROM eclipse-temurin:17-jdk AS build

WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -Pdynamic-analysis dependency:go-offline
COPY src src
RUN ./mvnw -Pdynamic-analysis package -DskipTests

FROM mcr.microsoft.com/playwright/java:v1.64.0-noble
WORKDIR /app
COPY --from=build /app/target/aeolens-api-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
