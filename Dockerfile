# ── Stage 1: Build the jar with Maven ──
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy Maven wrapper and pom first (better layer caching)
COPY mvnw .
COPY .mvn .mvn
COPY pom.xml .
RUN chmod +x mvnw

# Copy source and build
COPY src src
RUN ./mvnw clean package -DskipTests

# ── Stage 2: Run the jar on a lightweight JRE ──
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# Runtime data files (depot codes, route Excel files) read via relative "data/" path
COPY data data

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]