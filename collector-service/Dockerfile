# Stage 1: build the jar with Maven
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw .
RUN chmod +x mvnw && ./mvnw -B dependency:go-offline

COPY src src
RUN ./mvnw -B package -DskipTests

# Stage 2: run it on a JRE image that can also host the Azure CLI.
#
# AzureMonitorClient authenticates via DefaultAzureCredential, which in this project falls
# back to AzureCliCredential (the developer's own `az login` session) rather than a service
# principal. AzureCliCredential works by shelling out to the `az` binary, so the container
# needs `az` installed and the host's ~/.azure config bind-mounted in (see docker-compose.yml)
# - an Alpine base can't run Microsoft's az CLI install script, hence the jammy (Ubuntu) base
# here instead of the Alpine image api-service uses.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl ca-certificates gnupg lsb-release \
    && curl -sL https://aka.ms/InstallAzureCLIDeb | bash \
    && apt-get purge -y gnupg lsb-release \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*
# curl is kept (not purged) - it doubles as the healthcheck's HTTP client below.

COPY --from=build /app/target/*.jar app.jar
RUN mkdir -p /app/logs

# Runs as root (unlike api-service) so it can read the bind-mounted ~/.azure credential
# cache regardless of the host user's UID.
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
