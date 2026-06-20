# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

# Copy your source code into the cloud container
COPY pom.xml .
COPY src ./src

# Create the base directory so the move command doesn't fail
RUN mkdir -p src/main/resources/vosk-model

# Install required utilities
RUN apt-get update && apt-get install -y wget unzip

# Download the Indian English Model, extract it, rename it to 'model-in', and clean up the zip
RUN wget https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip && \
    unzip vosk-model-small-en-in-0.4.zip && \
    mv vosk-model-small-en-in-0.4 src/main/resources/vox-model/model-in || mv vosk-model-small-en-in-0.4 src/main/resources/vosk-model/model-in && \
    rm vosk-model-small-en-in-0.4.zip

# Package the Spring Boot application securely
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the finished JAR file from the builder stage
COPY --from=builder /app/target/*.jar app.jar

# CRITICAL FIX: Copy the physical model folder into the production environment
COPY --from=builder /app/src/main/resources/vosk-model /app/src/main/resources/vosk-model

# Start the application
ENTRYPOINT ["java", "-jar", "app.jar"]