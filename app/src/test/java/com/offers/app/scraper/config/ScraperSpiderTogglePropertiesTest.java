package com.offers.app.scraper.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.ScraperConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class ScraperSpiderTogglePropertiesTest {

    @Test
    void fallsBackToDatabaseEnabledFlagWhenNoPropertyExists() {
        ScraperConfig config = scraperConfig("ozone-discounts");
        config.setEnabled(false);

        assertThat(new ScraperSpiderToggleProperties(Map.of()).isEnabled(config)).isFalse();
    }

    @Test
    void propertyOverridesDatabaseEnabledFlag() {
        ScraperConfig config = scraperConfig("technopolis-products");
        config.setEnabled(false);
        ScraperSpiderToggleProperties properties = new ScraperSpiderToggleProperties(Map.of(
                "technopolis-products",
                new ScraperSpiderToggleProperties.Spider(true)
        ));

        assertThat(properties.isEnabled(config)).isTrue();
    }

    @Test
    void bindsSpiderTogglesBySlug() {
        new ApplicationContextRunner()
                .withUserConfiguration(PropertiesTestConfig.class)
                .withPropertyValues(
                        "scrapers.spiders.[technopolis-products].enabled=true"
                )
                .run(context -> {
                    ScraperSpiderToggleProperties properties = context.getBean(ScraperSpiderToggleProperties.class);

                    assertThat(properties.spiders())
                            .containsEntry(
                                    "technopolis-products",
                                    new ScraperSpiderToggleProperties.Spider(true)
                            );
                });
    }

    private ScraperConfig scraperConfig(String slug) {
        return new ScraperConfig(
                new Market(slug, slug, "https://example.com/"),
                slug,
                slug,
                "ozone_discount_scraper",
                slug.replace('-', '_'),
                60,
                300
        );
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(ScraperSpiderToggleProperties.class)
    static class PropertiesTestConfig {
    }
}
