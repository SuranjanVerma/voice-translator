# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Install tools for downloading and unzipping Vosk models
RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

# Create model storage directory (must match vosk.model.dir)
RUN mkdir -p /opt/vosk-models

# Download Vosk models and place them in /opt/vosk-models
RUN wget -q -O model-in.zip https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip && \
    unzip -q model-in.zip && \
    mv vosk-model-small-en-in-0.4 /opt/vosk-models/model-in && \
    rm model-in.zip

RUN wget -q -O model-en.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip && \
    unzip -q model-en.zip && \
    mv vosk-model-small-en-us-0.15 /opt/vosk-models/model-en && \
    rm model-en.zip

RUN wget -q -O model-hi.zip https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip && \
    unzip -q model-hi.zip && \
    mv vosk-model-small-hi-0.22 /opt/vosk-models/model-hi && \
    rm model-hi.zip

RUN wget -q -O model-te.zip https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip && \
    unzip -q model-te.zip && \
    mv vosk-model-small-te-0.42 /opt/vosk-models/model-te && \
    rm model-te.zip

# Validate that all models exist
RUN for model in model-in model-en model-hi model-te; do \
      test -f /opt/vosk-models/$model/am/final.mdl || (echo "Model $model is invalid!" && exit 1); \
    done

# Build the application (skip tests for speed)
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy the built JAR
COPY --from=builder /app/target/*.jar app.jar

# Copy the downloaded Vosk models
COPY --from=builder /opt/vosk-models /opt/vosk-models

# Set the Spring Boot property so the app finds the models
ENV vosk.model.dir=/opt/vosk-models

EXPOSE 8080

# Native access is required for Vosk
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]