package com.offers.app.scraper.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scrapers")
public record ScraperRuntimeProperties(
        Path baseDir,
        Path outputDir,
        Scrapy scrapy,
        Scheduler scheduler
) {

    public Path resolveProjectDir(String projectKey) {
        if (projectKey == null || projectKey.isBlank()) {
            throw new IllegalArgumentException("projectKey must not be blank");
        }
        if (".".equals(projectKey) || "..".equals(projectKey)) {
            throw new IllegalArgumentException("projectKey must be a project name");
        }
        if (projectKey.contains("/") || projectKey.contains("\\")) {
            throw new IllegalArgumentException("projectKey must not contain path separators");
        }

        return baseDir.resolve(projectKey).normalize();
    }

    public record Scrapy(String executable, int maxPages) {
    }

    public record Scheduler(long fixedDelayMs, int maxParallelRuns) {
    }
}
