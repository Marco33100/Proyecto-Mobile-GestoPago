package com.proyecto.servicios.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ProductSyncConfiguration {

    @Bean(name = "productSyncExecutor")
    public Executor productSyncExecutor() {
        return createExecutor(1, 1, 1, "product-sync-");
    }

    @Bean(name = "productCacheExecutor")
    public Executor productCacheExecutor() {
        return createExecutor(2, 4, 100, "product-cache-");
    }

    private Executor createExecutor(
            int corePoolSize,
            int maxPoolSize,
            int queueCapacity,
            String threadNamePrefix
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(queueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }
}
