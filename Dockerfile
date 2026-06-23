# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Install tools for downloading and unzipping Vosk models
RUN apt-get update && apt-get install -y wget unzip && rm -rf /var/lib/apt/lists/*

# Create model storage directory
RUN mkdir -p /opt/vosk-models

# Download English-Indian model (fallback)
RUN wget -q -O model-in.zip https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip && \
    unzip -q model-in.zip && \
    mv vosk-model-small-en-in-0.4 /opt/vosk-models/model-in && \
    rm model-in.zip

# Download US English model
RUN wget -q -O model-en.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip && \
    unzip -q model-en.zip && \
    mv vosk-model-small-en-us-0.15 /opt/vosk-models/model-en && \
    rm model-en.zip

# Download Hindi model WITH VALIDATION
RUN wget -q -O model-hi.zip https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip && \
    unzip -q model-hi.zip && \
    mv vosk-model-small-hi-0.22 /opt/vosk-models/model-hi && \
    rm model-hi.zip && \
    test -d /opt/vosk-models/model-hi && \
    test -f /opt/vosk-models/model-hi/am/final.mdl || \
    (echo "FAILED to validate Hindi model" && exit 1)

# Download Telugu model
RUN wget -q -O model-te.zip https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip && \
    unzip -q model-te.zip && \
    mv vosk-model-small-te-0.42 /opt/vosk-models/model-te && \
    rm model-te.zip

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:17-jre
WORKDIR /app

# Copy built JAR
COPY --from=builder /app/target/*.jar app.jar

# Copy Vosk models
COPY --from=builder /opt/vosk-models /opt/vosk-models

# Set the model directory for Spring Boot
ENV vosk.model.dir=/opt/vosk-models

EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]