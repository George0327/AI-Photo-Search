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

    @Value("${app.indexing.download-pool-size:0}")
    private int configuredDownloadPoolSize;

    @Bean(name = "indexingExecutor")
    public Executor indexingExecutor() {
        // Thread count is the memory budget (each thread holds a batch); a few in-flight batches saturate the GPU-bound AI service. Override via INDEXING_POOL_SIZE.
        int poolSize = configuredPoolSize > 0
                ? configuredPoolSize
                : Math.min(4, Runtime.getRuntime().availableProcessors());
        return buildExecutor(poolSize, "indexing-");
    }

    @Bean(name = "downloadExecutor")
    public Executor downloadExecutor() {
        // Drive downloads are pure I/O latency, so a chunk's photos are fetched in parallel; no extra memory beyond the in-flight chunks.
        int poolSize = configuredDownloadPoolSize > 0
                ? configuredDownloadPoolSize
                : Math.min(16, Runtime.getRuntime().availableProcessors() * 2);
        return buildExecutor(poolSize, "download-");
    }

    private static Executor buildExecutor(int poolSize, String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(poolSize);
        executor.setMaxPoolSize(poolSize);
        executor.setQueueCapacity(500);
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(120);
        executor.initialize();
        return executor;
    }
}
