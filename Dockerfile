# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy your source code into the cloud container
COPY pom.xml .
COPY src ./src

# Install required utilities
RUN apt-get update && apt-get install -y wget unzip

# Download the Indian English Model and unzip it
RUN wget https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip
RUN unzip vosk-model-small-en-in-0.4.zip

# Explicitly create the exact folder structure Java expects
RUN mkdir -p /app/src/main/resources/vosk-model/model-in

# Move the *contents* (using /*) directly into the folder to prevent nesting
RUN mv vosk-model-small-en-in-0.4/* /app/src/main/resources/vosk-model/model-in/

# Package the Spring Boot application
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the finished JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy the specific model directory explicitly to the production stage
COPY --from=builder /app/src/main/resources/vosk-model/model-in /app/src/main/resources/vosk-model/model-in

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]