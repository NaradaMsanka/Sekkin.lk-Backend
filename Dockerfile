# Stage 1: Build the application using Maven
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app

# Copy pom.xml and download dependencies first (for caching)
COPY pom.xml .
RUN mvn dependency:go-offline

# Copy the rest of the source code
COPY src ./src

# Build the jar file (skipping tests for faster build)
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-alpine
# GnuPG verifies the JDK while the upstream image is built. This Java entrypoint
# does not use GnuPG or GNU coreutils; retain BusyBox and the JRE dependencies.
# Removing unused GnuPG also removes its vulnerable SQLite dependency chain.
RUN apk upgrade --no-cache && apk del --no-cache gnupg coreutils
WORKDIR /app

# Copy the built jar file from the build stage
COPY --from=build --chmod=0444 /app/target/*.jar app.jar

# Port 8080 and temporary multipart storage do not require root privileges.
USER 10001:10001

# Expose port 8080
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
