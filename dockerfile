# temp container to build using gradle
FROM maven:3.8.4-openjdk-17-slim AS builder

# Set the working directory in the container
WORKDIR /app
# Copy the pom.xml and the project files to the container
COPY pom.xml .
COPY src ./src
COPY guides ./guides

RUN mvn clean package -DskipTests

# package stage
FROM eclipse-temurin:17-jre

LABEL org.opencontainers.image.source="https://github.com/JZomDev/UnifiWebhookToDiscord"

RUN mkdir -p /srv
WORKDIR /srv
COPY --from=builder /app/target/Unifi-Webhook-To-Discord-1.0.jar /srv

# No VOLUME here on purpose: an anonymous volume would look persistent but is
# dropped when the container is recreated. Users must map /config themselves;
# config_require_mount makes the app warn in the UI when /config is not a mount.
RUN mkdir -p /config && chown 99:100 /config
ENV config_require_mount=true
EXPOSE 45462 8080

USER 99:100
CMD ["java", "-jar", "Unifi-Webhook-To-Discord-1.0.jar"]
