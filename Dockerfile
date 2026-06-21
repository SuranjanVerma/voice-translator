# Stage 1: Build Environment
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /app

COPY pom.xml .
COPY src ./src

# Download Vosk models to /vosk-models
RUN apt-get update && apt-get install -y wget unzip && mkdir -p /vosk-models

RUN wget -q -O model-in.zip https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip && \
    unzip -q model-in.zip && \
    mv vosk-model-small-en-in-0.4 /vosk-models/model-in && \
    rm model-in.zip

RUN wget -q -O model-en.zip https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip && \
    unzip -q model-en.zip && \
    mv vosk-model-small-en-us-0.15 /vosk-models/model-en && \
    rm model-en.zip

RUN wget -q -O model-hi.zip https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip && \
    unzip -q model-hi.zip && \
    mv vosk-model-small-hi-0.22 /vosk-models/model-hi && \
    rm model-hi.zip

RUN wget -q -O model-te.zip https://alphacephei.com/vosk/models/vosk-model-small-te-0.42.zip && \
    unzip -q model-te.zip && \
    mv vosk-model-small-te-0.42 /vosk-models/model-te && \
    rm model-te.zip

# Validate models
RUN for model in model-in model-en model-hi model-te; do \
      test -f /vosk-models/$model/am/final.mdl || (echo "Model $model is invalid!" && exit 1); \
    done

# Build the app
RUN mvn clean package -DskipTests

# Stage 2: Production Environment
FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar
COPY --from=builder /vosk-models /vosk-models

ENV VOSK_MODEL_DIR=/vosk-models
EXPOSE 8080

ENTRYPOINT ["java", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]