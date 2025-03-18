# Use official Gradle 8.13 with JDK 21 as base image
FROM gradle:8.13-jdk21 AS builder

# Set working directory
WORKDIR /app

# Copy project files
COPY . .

# Build the project
RUN gradle shadowJar --no-daemon

RUN ls -la build/libs

# Runtime stage (optional, slimmer image)
FROM eclipse-temurin:21-jre

# Set working directory
WORKDIR /app

# Copy the built artifact from the builder stage
COPY --from=builder /app/build/libs/vertx-benchmark-1.0-SNAPSHOT-all.jar app.jar

# Expose port (adjust if your app needs it)
EXPOSE 8080

# Run the artifact
CMD ["java", "-jar", "app.jar"]