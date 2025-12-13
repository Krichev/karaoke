package com.karaoke.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {
    
    @Value("${karaoke.processing.async.core-pool-size}")
    private int corePoolSize;
    
    @Value("${karaoke.processing.async.max-pool-size}")
    private int maxPoolSize;
    
    @Value("${karaoke.processing.async.queue-capacity}")
    private int queueCapacity;
    
    @Bean(name = "audioProcessingExecutor")
    public Executor audioProcessingExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix("audio-processing-");
        executor.initialize();
        return executor;
    }
}
