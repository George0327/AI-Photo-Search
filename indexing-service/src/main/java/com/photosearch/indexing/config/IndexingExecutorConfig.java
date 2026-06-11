package com.photosearch.indexing.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class IndexingExecutorConfig {

    @Value("${app.indexing.pool-size:0}")
    private int configuredPoolSize;

    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        // Java threads are I/O-bound (Drive download + HTTP to AI service), so
        // 2× logical CPUs is the hardware-optimal default. Override via INDEXING_POOL_SIZE.
        int poolSize = configuredPoolSize > 0
                ? configuredPoolSize
                : Runtime.getRuntime().availableProcessors() * 2;

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix("indexing-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
