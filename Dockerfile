# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Download all 4 Vosk models directly to /vosk-models (not inside src/)
RUN apt-get update && apt-get install -y wget unzip && \
    mkdir -p /vosk-models && \
    wget -q https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip && \
    unzip -q vosk-model-small-en-in-0.4.zip && \
    mv vosk-model-small-en-in-0.4 /vosk-models/model-in && \
    rm vosk-model-small-en-in-0.4.zip && \
    wget -q https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip && \
    unzip -q vosk-model-small-en-us-0.15.zip && \
    mv vosk-model-small-en-us-0.15 /vosk-models/model-en && \
    rm vosk-model-small-en-us-0.15.zip && \
    wget -q https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip && \
    unzip -q vosk-model-small-hi-0.22.zip && \
    mv vosk-model-small-hi-0.22 /vosk-models/model-hi && \
    rm vosk-model-small-hi-0.22.zip && \
    wget -q https://alphacephei.com/vosk/models/vosk-model-te-0.171.zip && \
    unzip -q vosk-model-te-0.171.zip && \
    mv vosk-model-te-0.171 /vosk-models/model-te && \
    rm vosk-model-te-0.171.zip

# Build the app (models are never in src/main/resources)
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /vosk-models /vosk-models

ENV VOSK_MODEL_DIR=/vosk-models
EXPOSE 8080

# --enable-native-access suppresses the JNA warning
ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]