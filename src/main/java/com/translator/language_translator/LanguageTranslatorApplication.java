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

// 1. Import BOTH LibVosk and the LogLevel enum
import org.vosk.LibVosk;
import org.vosk.LogLevel;

import java.util.concurrent.Executor;

@SpringBootApplication
@EnableCaching
@EnableAsync
public class LanguageTranslatorApplication {

    public static void main(String[] args) {
        // 2. Use the Java Enum to restrict logging to Warnings/Errors only
        LibVosk.setLogLevel(LogLevel.WARNINGS);

        SpringApplication.run(LanguageTranslatorApplication.class, args);
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public CloseableHttpClient httpClient() {
        return HttpClientBuilder.create()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .setMaxConnTotal(10)
                        .setMaxConnPerRoute(5)
                        .build())
                .setDefaultRequestConfig(org.apache.hc.client5.http.config.RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(3))
                        .setResponseTimeout(Timeout.ofSeconds(5))
                        .build())
                .build();
    }

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("translator-async-");
        executor.initialize();
        return executor;
    }
}