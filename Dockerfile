# Stage 1: Build application using Java 25
FROM eclipse-temurin:25-jdk-noble AS builder
ARG COMMIT_REF=""
WORKDIR /app

# Copy Maven wrapper and descriptor to cache dependency layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B || true

# Copy source files and compile package
COPY src ./src
# Write commit ref to classpath resources if provided during build
RUN if [ -n "$COMMIT_REF" ]; then \
        mkdir -p src/main/resources && echo "$COMMIT_REF" > src/main/resources/commit-ref.txt; \
    fi
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre-noble AS runner
ARG COMMIT_REF=""
WORKDIR /app

# Create unprivileged application user and pre-create log directory with correct permissions
RUN groupadd -r preons && useradd -r -g preons -d /app preons \
    && mkdir -p /app/logs && chown -R preons:preons /app

# Copy application jar from builder
COPY --from=builder /app/target/preonsurl-0.0.1-SNAPSHOT.jar app.jar
# Save commit ref file in runtime app folder
RUN if [ -n "$COMMIT_REF" ]; then echo "$COMMIT_REF" > /app/commit-ref.txt; fi
RUN chown -R preons:preons /app

USER preons

EXPOSE 8081
ENV SERVER_PORT=8081 \
    SPRING_PROFILES_ACTIVE=docker \
    LOG_PATH=/app/logs \
    COMMIT_REF=${COMMIT_REF} \
    GIT_COMMIT=${COMMIT_REF} \
    JAVA_OPTS="-XX:+UseG1GC -XX:+UseCompactObjectHeaders -Xms128m -Xmx512m -Xss512k -XX:MaxMetaspaceSize=160m -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
