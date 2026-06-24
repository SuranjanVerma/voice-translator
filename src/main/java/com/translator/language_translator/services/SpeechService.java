package com.translator.language_translator.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Service
public class SpeechService {

    private static final Logger logger = LoggerFactory.getLogger(SpeechService.class);
    private static final float SAMPLE_RATE = 16000.0f;

    private final File modelsDir;
    // Memory Optimization: Caffeine Cache automatically ejects unused models after 15 mins
    private final Cache<String, Model> modelCache;
    private final ConcurrentHashMap<String, Recognizer> activeSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    // Mapping official lightweight download URLs to avoid OOM errors on free-tier clouds
    private static final Map<String, String> MODEL_DOWNLOAD_URLS = Map.of(
            "model-en", "https://alphacephei.com/vosk/models/vosk-model-small-en-us-0.15.zip",
            "model-hi", "https://alphacephei.com/vosk/models/vosk-model-small-hi-0.22.zip",
            "model-te", "https://alphacephei.com/vosk/models/vosk-model-small-te-0.4.zip",
            "model-in", "https://alphacephei.com/vosk/models/vosk-model-small-en-in-0.4.zip"
    );

    public SpeechService(@Value("${vosk.model.dir:src/main/resources/vosk-model}") String modelDirPath,
                         ObjectMapper objectMapper) {
        this.modelsDir = new File(modelDirPath);
        if (!modelsDir.exists()) {
            modelsDir.mkdirs();
        }
        this.objectMapper = objectMapper;

        // Auto-evict models to prevent out-of-memory errors on 512MB/1GB RAM servers
        this.modelCache = Caffeine.newBuilder()
                .expireAfterAccess(15, TimeUnit.MINUTES)
                .removalListener((String key, Model model, RemovalCause cause) -> {
                    if (model != null) {
                        logger.info("Evicting unused model {} to free memory. Cause: {}", key, cause);
                        model.close();
                    }
                })
                .build();
    }

    private String mapLanguageToModelFolder(String langCode) {
        return switch (langCode) {
            case "hi-IN", "hi" -> "model-hi";
            case "te-IN", "te" -> "model-te";
            case "en-US", "en" -> "model-en";
            default          -> "model-in";
        };
    }

    private Model getModelForLanguage(String sourceLang) {
        String folderName = mapLanguageToModelFolder(sourceLang);
        return modelCache.get(folderName, this::loadOrDownloadModel);
    }

    private Model loadOrDownloadModel(String folderName) {
        File targetFolder = new File(modelsDir, folderName);

        // If folder is empty or doesn't exist, trigger zero-setup dynamic download
        if (!targetFolder.exists() || targetFolder.list() == null || targetFolder.list().length == 0) {
            downloadAndUnzipModel(folderName, targetFolder);
        }

        try {
            logger.info("Loading Vosk model into RAM: {}", targetFolder.getAbsolutePath());
            return new Model(targetFolder.getAbsolutePath());
        } catch (Exception e) {
            logger.error("Failed to load Vosk model: {}", e.getMessage());
            throw new RuntimeException("Model load failure: " + e.getMessage());
        }
    }

    private void downloadAndUnzipModel(String folderName, File targetFolder) {
        String urlStr = MODEL_DOWNLOAD_URLS.get(folderName);
        if (urlStr == null) throw new RuntimeException("No download URL for " + folderName);

        logger.info("Model missing. Downloading lightweight model from: {}", urlStr);
        try {
            File tempZip = File.createTempFile(folderName, ".zip");
            try (InputStream in = new URL(urlStr).openStream()) {
                Files.copy(in, tempZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            logger.info("Unzipping model to {}", targetFolder.getAbsolutePath());
            targetFolder.mkdirs();
            try (ZipInputStream zis = new ZipInputStream(new FileInputStream(tempZip))) {
                ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    // Bypass the root folder inside the zip so contents sit directly in targetFolder
                    String name = entry.getName().replaceFirst("^[^/]+/", "");
                    if (name.isEmpty()) continue;

                    File newFile = new File(targetFolder, name);
                    if (entry.isDirectory()) {
                        newFile.mkdirs();
                    } else {
                        newFile.getParentFile().mkdirs();
                        Files.copy(zis, newFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }
            tempZip.delete();
            logger.info("Successfully installed model: {}", folderName);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download model " + folderName, e);
        }
    }

    public String transcribe(InputStream audio, String sourceLang) throws Exception {
        Model model = getModelForLanguage(sourceLang);
        try (Recognizer recognizer = new Recognizer(model, SAMPLE_RATE)) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = audio.read(buffer)) >= 0) {
                if (bytesRead > 0) {
                    recognizer.acceptWaveForm(buffer, bytesRead);
                }
            }
            JsonNode rootNode = objectMapper.readTree(recognizer.getFinalResult());
            return rootNode.path("text").asText("");
        }
    }

    public String startSession(String sourceLang) throws Exception {
        Model model = getModelForLanguage(sourceLang);
        String sessionId = java.util.UUID.randomUUID().toString();
        activeSessions.put(sessionId, new Recognizer(model, SAMPLE_RATE));
        return sessionId;
    }

    public String processChunk(String sessionId, byte[] audioBytes, int numBytes) throws Exception {
        Recognizer recognizer = activeSessions.get(sessionId);
        if (recognizer == null) throw new RuntimeException("Session expired or missing: " + sessionId);

        if (numBytes > 0) recognizer.acceptWaveForm(audioBytes, numBytes);

        String partialText = objectMapper.readTree(recognizer.getPartialResult()).path("partial").asText("");
        return objectMapper.writeValueAsString(Map.of("text", partialText, "final", false));
    }

    public String stopSession(String sessionId) throws Exception {
        Recognizer recognizer = activeSessions.remove(sessionId);
        if (recognizer == null) return "";

        String finalText = objectMapper.readTree(recognizer.getFinalResult()).path("text").asText("");
        recognizer.close();
        return finalText;
    }

    @PreDestroy
    public void cleanup() {
        activeSessions.values().forEach(Recognizer::close);
        activeSessions.clear();
        modelCache.invalidateAll();
    }
}