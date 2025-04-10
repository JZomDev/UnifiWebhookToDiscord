# temp container to build using gradle
FROM gradle:7.6.3-jdk11-alpine AS builder

COPY . /home/gradle/src

WORKDIR /home/gradle/src

RUN gradle fatJar --no-daemon --info

# package stage
FROM openjdk:11

RUN mkdir -p /srv
WORKDIR /srv
COPY --from=builder /home/gradle/src/build/libs/UnifiWebhookToDiscord.jar /srv

USER 99:100
CMD ["java", "-jar", "UnifiWebhookToDiscord.jar"]
