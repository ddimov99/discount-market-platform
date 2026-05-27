package com.offers.app.scraper.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class ScraperExecutionConfig {

    @Bean(name = "scraperTaskExecutor")
    public ThreadPoolTaskExecutor scraperTaskExecutor(ScraperRuntimeProperties properties) {
        int maxParallelRuns = Math.max(1, properties.scheduler().maxParallelRuns());
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("scraper-");
        executor.setCorePoolSize(maxParallelRuns);
        executor.setMaxPoolSize(maxParallelRuns);
        executor.setQueueCapacity(maxParallelRuns * 4);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        return executor;
    }
}
