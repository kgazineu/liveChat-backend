# syntax=docker/dockerfile:1

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:21-jre-jammy AS runtime

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system livechat \
    && useradd --system --gid livechat --home-dir /app livechat

WORKDIR /app

COPY --from=build --chown=livechat:livechat \
    /workspace/target/liveChat-back-*.jar /app/app.jar

USER livechat

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
