# Build with a full JDK, then copy only the runnable artifact into the final image.
FROM eclipse-temurin:21-jdk-jammy AS builder

LABEL authors="Raul Esteban" version="dev-1" description="Spring Boot Docker Image"

WORKDIR /app

COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

RUN chmod +x gradlew

RUN sed -i 's/\r$//' gradlew

RUN ./gradlew dependencies --no-daemon

COPY src src

RUN ./gradlew clean build -x test -Pprod --no-daemon

# Jammy gives us a predictable apt-based runtime for optional PostgreSQL tooling.
FROM eclipse-temurin:21-jre-jammy

ARG POSTGRES_CLIENT_MAJOR=18

ENV TZ=America/Bogota
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Bogota"

# Spring Boot is mandatory; pg_dump/pg_restore are best-effort helpers.
RUN set -eu; \
    export DEBIAN_FRONTEND=noninteractive; \
    apt-get update; \
    apt-get install -y --no-install-recommends \
        ca-certificates \
        curl \
        gnupg \
        tzdata; \
    install -d /usr/share/postgresql-common/pgdg; \
    if curl --fail --silent --show-error --location \
        --output /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
        https://www.postgresql.org/media/keys/ACCC4CF8.asc; then \
        . /etc/os-release; \
        echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list; \
        if ! apt-get update; then \
            echo "[WARN] PGDG repository metadata could not be refreshed. PostgreSQL client tooling may be unavailable."; \
        fi; \
    else \
        echo "[WARN] Could not configure the PGDG repository. PostgreSQL client tooling may be unavailable."; \
    fi; \
    if ! apt-get install -y --no-install-recommends "postgresql-client-${POSTGRES_CLIENT_MAJOR}"; then \
        echo "[WARN] PostgreSQL client ${POSTGRES_CLIENT_MAJOR} could not be installed. Export/import total will stay disabled until the image can provide pg_dump and pg_restore."; \
    fi; \
    rm -rf /var/lib/apt/lists/*

# Emit a compact capabilities summary during image build so Render logs show what the runtime contains.
RUN set -eu; \
    echo "[DIAG] Runtime PATH=$PATH"; \
    if command -v pg_dump >/dev/null 2>&1; then pg_dump --version; else echo "[WARN] pg_dump unavailable in runtime image."; fi; \
    if command -v pg_restore >/dev/null 2>&1; then pg_restore --version; else echo "[WARN] pg_restore unavailable in runtime image."; fi

RUN mkdir -p /root/.ssh && chmod 0700 /root/.ssh

WORKDIR /app

COPY --from=builder /app/build/libs/exotic-app-v1.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
