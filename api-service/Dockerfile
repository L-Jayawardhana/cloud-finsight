# Stage 1: build the jar with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

# Stage 2: run it on a slim JRE
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs

# Runs as root: the bind-mounted ./api-service/logs host directory (see docker-compose.yml)
# is owned by the host user, and a fixed-UID container user wouldn't reliably have write
# access to it across different developer machines.
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
