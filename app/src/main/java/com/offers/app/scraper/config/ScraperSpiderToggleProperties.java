package com.offers.app.scraper.config;

import com.offers.app.catalog.domain.ScraperConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "scrapers")
public record ScraperSpiderToggleProperties(Map<String, Spider> spiders) {

    public ScraperSpiderToggleProperties {
        spiders = spiders == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(spiders));
    }

    public boolean isEnabled(ScraperConfig config) {
        Spider spider = spiders.get(config.getSlug());
        if (spider == null || spider.enabled() == null) {
            return config.isEnabled();
        }
        return spider.enabled();
    }

    public record Spider(Boolean enabled) {
    }
}
