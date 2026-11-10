FROM gradle:9.4-jdk25 AS builder

WORKDIR /app

COPY build.gradle* settings.gradle* gradle.properties* ./
COPY buildSrc buildSrc

RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle dependencies --no-daemon || true

COPY . .
RUN --mount=type=cache,target=/home/gradle/.gradle \
    gradle bootJar --no-daemon -x test

FROM eclipse-temurin:25-jre-jammy
WORKDIR /app

COPY --from=builder /app/build/libs/app.jar app.jar


EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]