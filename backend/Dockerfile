# ---------- Stage 1: build ----------
FROM maven:3.9.9-eclipse-temurin-17 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Stage 2: run (JRE only) ----------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=builder /app/target/meditrack-backend.jar app.jar
RUN mkdir -p /app/uploads/reports
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
