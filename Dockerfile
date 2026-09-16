# Stage 1: Build application using Java 25
FROM eclipse-temurin:25-jdk-noble AS builder
WORKDIR /app

# Copy Maven wrapper and descriptor to cache dependency layer
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x ./mvnw
RUN ./mvnw dependency:go-offline -B || true

# Copy source files and compile package
COPY src ./src
RUN ./mvnw clean package -DskipTests

# Stage 2: Minimal runtime image
FROM eclipse-temurin:25-jre-noble AS runner
WORKDIR /app

# Create unprivileged application user
RUN groupadd -r preons && useradd -r -g preons -d /app preons
USER preons

# Copy application jar from builder
COPY --from=builder /app/target/preonsurl-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8081

ENV SERVER_PORT=8081 \
    SPRING_PROFILES_ACTIVE=docker \
    JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
