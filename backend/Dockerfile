# ==============================================================================
# StockGuard Backend Multi-Stage Dockerfile
# Stage 1: Build stage using Temurin JDK 21
# Stage 2: Minimal runtime image using Temurin JRE 21
# ==============================================================================

FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper and configuration
COPY .mvn .mvn
COPY mvnw pom.xml ./

# Cache Maven dependencies offline
RUN sh ./mvnw dependency:go-offline -B

# Copy application source code
COPY src src

# Build executable JAR without running tests (tests run in CI pipeline)
RUN sh ./mvnw clean package -DskipTests

# ==============================================================================
# Stage 2: Runtime image
# ==============================================================================
FROM eclipse-temurin:21-jre-alpine AS runner
WORKDIR /app

# Add non-root application user for defense-in-depth security
RUN addgroup -S stockguard && adduser -S stockguard -G stockguard
USER stockguard

# Copy generated JAR from builder stage
COPY --from=builder /workspace/target/stockguard-backend-*.jar app.jar

ENV PORT=8080
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=60.0", "-Xss512k", "-XX:+ExitOnOutOfMemoryError", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
