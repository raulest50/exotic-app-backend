# Build stage
FROM amazoncorretto:21 AS builder

LABEL authors="Raul Esteban" version="dev-1" description="Spring Boot Docker Image"

# Create and set the working directory for build
WORKDIR /app

# Copy Gradle wrapper and build files
COPY gradlew gradlew
COPY gradle gradle
COPY build.gradle.kts build.gradle.kts
COPY settings.gradle.kts settings.gradle.kts

# Ensure gradlew has execute permissions
RUN chmod +x gradlew

# Fix the line endings in the gradlew script
RUN sed -i 's/\r$//' gradlew

# Cache dependencies
RUN ./gradlew dependencies --no-daemon

# Copy the project source code
COPY src src

# Install dependencies and build the project
# added -Pprod for production.
RUN ./gradlew clean build -x test -Pprod --no-daemon

# Runtime stage
FROM amazoncorretto:21

# Reinforce the application's timezone policy at the container level.
ENV TZ=America/Bogota
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=America/Bogota"
ENV ENABLE_MCP=false
ENV MCP_BIND_HOST=127.0.0.1
ENV MCP_PORT=8765
ENV MCP_LOG_PREFIX=[MCP]
ENV MCP_PID_FILE=/tmp/mcp.pid
ENV MCP_STARTUP_GRACE_SECONDS=2
ENV MCP_HEALTHCHECK_TIMEOUT_SECONDS=5

ARG MCP_NPM_PACKAGE=""

RUN yum install -y postgresql15 tzdata nodejs npm \
    && yum clean all \
    && rm -rf /var/cache/yum

RUN if [ -n "$MCP_NPM_PACKAGE" ]; then npm install -g "$MCP_NPM_PACKAGE"; else echo "MCP_NPM_PACKAGE not provided; skipping MCP CLI global install."; fi

# Create and set the working directory for runtime
WORKDIR /app

# Copy only the built JAR from the builder stage
COPY --from=builder /app/build/libs/exotic-app-v1.jar app.jar
COPY MCP_EPOINT MCP_EPOINT

RUN chmod +x /app/MCP_EPOINT/*.sh

# Expose the port the app runs on
EXPOSE 8080

# Command to run the application - profile controlled via SPRING_PROFILES_ACTIVE env var
ENTRYPOINT ["/app/MCP_EPOINT/docker-entrypoint.sh"]
CMD ["java", "-jar", "/app/app.jar"]
