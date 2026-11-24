# Build with a full JDK, then copy only the runnable artifact into the final image.
FROM eclipse-temurin:21-jdk-jammy@sha256:b2711c65970c1227197c29c46e669bfdbb137524e9da0e664703a140ec25a040 AS builder

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

# PostgreSQL 18 tooling is required for complete export/import and recovery.
FROM eclipse-temurin:21-jre-jammy@sha256:a8bff88f760e87e0c8844f8461f807c203fd5e8f78c951c843024647e965eaf5

ENV TZ=America/Bogota
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Bogota"

RUN set -eu; \
    export DEBIAN_FRONTEND=noninteractive; \
    apt-get update; \
    apt-get install -y --no-install-recommends ca-certificates curl gnupg tzdata; \
    install -d /usr/share/postgresql-common/pgdg; \
    curl --fail --silent --show-error --location \
        --output /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc \
        https://www.postgresql.org/media/keys/ACCC4CF8.asc; \
    fingerprint=$(gpg --batch --show-keys --with-colons /usr/share/postgresql-common/pgdg/apt.postgresql.org.asc | awk -F: '$1 == "fpr" {print $10; exit}'); \
    test "$fingerprint" = B97B0AFCAA1A47F044F244A07FCC7D46ACCC4CF8; \
    . /etc/os-release; \
    echo "deb [signed-by=/usr/share/postgresql-common/pgdg/apt.postgresql.org.asc] https://apt.postgresql.org/pub/repos/apt ${VERSION_CODENAME}-pgdg main" > /etc/apt/sources.list.d/pgdg.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends postgresql-client-18; \
    pg_dump --version | grep -E '^pg_dump \(PostgreSQL\) 18\.'; \
    pg_restore --version | grep -E '^pg_restore \(PostgreSQL\) 18\.'; \
    rm -rf /var/lib/apt/lists/*; \
    groupadd --gid 1000 exotic; \
    useradd --uid 1000 --gid 1000 --no-create-home --shell /usr/sbin/nologin exotic

WORKDIR /app

COPY --from=builder /app/build/libs/exotic-app-v1.jar app.jar

USER 1000:1000

EXPOSE 8080

CMD ["java", "-jar", "/app/app.jar"]
