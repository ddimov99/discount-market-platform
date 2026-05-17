package com.offers.app.catalog.scraping;

import com.offers.app.catalog.domain.ScraperConfig;

public interface ScraperRunner {

    ScraperRunResult run(ScraperConfig scraperConfig);
}
