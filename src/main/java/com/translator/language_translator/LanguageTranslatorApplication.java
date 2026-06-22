package com.translator.language_translator;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableCaching            // Enables Spring Cache (Caffeine)
@EnableAsync              // Allows @Async methods for non‑blocking translation calls
public class LanguageTranslatorApplication {

    public static void main(String[] args) {
        SpringApplication.run(LanguageTranslatorApplication.class, args);
    }

    /**
     * Custom ObjectMapper – used by SpeechService.
     * Spring Boot also provides one; if you don't need special configuration,
     * you can delete this bean and let auto‑configuration do its work.
     */
    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    /**
     * Optimized HTTP client for external translation APIs.
     * Supports connection pooling, per‑request timeouts, and retries.
     */
    @Bean
    public CloseableHttpClient httpClient() {
        return HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(50)               // total connections
                        .setMaxConnPerRoute(20)             // per route (each translation API)
                        .build())
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setResponseTimeout(Timeout.ofSeconds(5))   // 5-second response timeout (prevents late translation)
                        .build())
                .build();
    }

    /**
     * Thread pool executor for @Async tasks (e.g., calling external APIs).
     * Prevents WebSocket threads from being blocked.
     */
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("translator-async-");
        executor.initialize();
        return executor;
    }
}