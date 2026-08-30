# ─────────────────────────────────────────────────────────────────────────────
# AISA API container.
#
# Multi-stage: the JDK, Maven and the whole dependency cache stay in the build
# stage, and the final image carries a JRE and one jar (~230 MB rather than ~800).
# ─────────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /build

# Dependencies resolve in their own layer, keyed on pom.xml alone. A change to
# application source therefore does not re-download the world on every rebuild.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -DskipTests package


FROM eclipse-temurin:25-jre-alpine AS runtime
WORKDIR /app

# Non-root. A container that runs as root gives an RCE the run of the filesystem;
# this application never needs to write anywhere outside /tmp.
RUN addgroup -S aisa && adduser -S aisa -G aisa
USER aisa

COPY --from=build --chown=aisa:aisa /build/target/aisa-api-*.jar app.jar

EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx: free tiers hand out 512 MB and the
# JVM's own default (25% of RAM) leaves most of it unused, which shows up as
# avoidable GC pressure under load.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

# Shell form on purpose, so $JAVA_OPTS is expanded. exec keeps the JVM as PID 1
# so it receives SIGTERM directly and shuts down gracefully instead of being killed.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
