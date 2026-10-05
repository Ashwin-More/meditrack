# ---------- Stage 1: Build ----------
FROM maven:3.9.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy dependency files first for better Docker layer caching
COPY pom.xml .

# Download dependencies
RUN mvn -B dependency:go-offline

# Copy application source
COPY src ./src

# Build application
RUN mvn -B clean package -DskipTests


# ---------- Stage 2: Runtime ----------
FROM eclipse-temurin:17-jre

WORKDIR /app

# Create upload directory
RUN mkdir -p /app/uploads/reports

# Copy generated JAR
COPY --from=builder /app/target/meditrack-backend.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
