# temp container to build using gradle
FROM maven:3.8.4-openjdk-17-slim AS builder

# Set the working directory in the container
WORKDIR /app
# Copy the pom.xml and the project files to the container
COPY pom.xml .
COPY src ./src

RUN mvn clean package -DskipTests

# package stage
FROM openjdk:17

RUN mkdir -p /srv
WORKDIR /srv
COPY --from=builder /app/target/Unifi-Webhook-To-Discord-1.0.jar /srv

USER 99:100
CMD ["java", "-jar", "Unifi-Webhook-To-Discord-1.0.jar"]
