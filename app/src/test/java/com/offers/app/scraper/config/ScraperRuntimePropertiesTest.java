package com.offers.app.scraper.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScraperRuntimePropertiesTest {

    private final ScraperRuntimeProperties properties = new ScraperRuntimeProperties(
            Path.of("/Users/dimitardimov/Desktop/scraping"),
            Path.of("/tmp/discount-market-scrapes"),
            new ScraperRuntimeProperties.Scrapy("scrapy", 1000),
            new ScraperRuntimeProperties.Scheduler(60000)
    );

    @Test
    void resolvesProjectDirFromBaseDirAndProjectKey() {
        assertThat(properties.resolveProjectDir("ozone_discount_scraper"))
                .isEqualTo(Path.of("/Users/dimitardimov/Desktop/scraping/ozone_discount_scraper"));
    }

    @Test
    void rejectsBlankProjectKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.resolveProjectDir(" "));
    }

    @Test
    void rejectsTraversalProjectKey() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.resolveProjectDir(".."));
    }

    @Test
    void rejectsProjectKeyWithPathSeparators() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.resolveProjectDir("/tmp/ozone_discount_scraper"));

        assertThatIllegalArgumentException()
                .isThrownBy(() -> properties.resolveProjectDir("scrapers/ozone_discount_scraper"));
    }
}
