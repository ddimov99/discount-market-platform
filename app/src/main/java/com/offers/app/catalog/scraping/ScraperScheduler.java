package com.offers.app.catalog.scraping;

import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.catalog.repository.ScrapeRunRepository;
import com.offers.app.catalog.repository.ScraperConfigRepository;
import com.offers.app.scraper.config.ScraperSpiderToggleProperties;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScraperScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);

    private final ScraperConfigRepository scraperConfigRepository;
    private final ScrapeRunRepository scrapeRunRepository;
    private final ObjectProvider<ScraperRunner> scraperRunnerProvider;
    private final OfferIngestionService offerIngestionService;
    private final ScraperSpiderToggleProperties spiderToggleProperties;
    private final Executor scraperExecutor;
    private final Set<Long> runningScraperConfigIds = ConcurrentHashMap.newKeySet();
    private final Set<Long> runningMarketIds = ConcurrentHashMap.newKeySet();

    public ScraperScheduler(
            ScraperConfigRepository scraperConfigRepository,
            ScrapeRunRepository scrapeRunRepository,
            ObjectProvider<ScraperRunner> scraperRunnerProvider,
            OfferIngestionService offerIngestionService,
            ScraperSpiderToggleProperties spiderToggleProperties,
            @Qualifier("scraperTaskExecutor") Executor scraperExecutor
    ) {
        this.scraperConfigRepository = scraperConfigRepository;
        this.scrapeRunRepository = scrapeRunRepository;
        this.scraperRunnerProvider = scraperRunnerProvider;
        this.offerIngestionService = offerIngestionService;
        this.spiderToggleProperties = spiderToggleProperties;
        this.scraperExecutor = scraperExecutor;
    }

    @Scheduled(fixedDelayString = "${scrapers.scheduler.fixed-delay-ms}")
    public void runDueScrapers() {
        LocalDateTime now = LocalDateTime.now();
        List<ScraperConfig> dueConfigs = scraperConfigRepository.findAllWithMarket()
                .stream()
                .filter(spiderToggleProperties::isEnabled)
                .filter(config -> isDue(config, now))
                .filter(config -> !isAlreadyRunning(config))
                .toList();

        dueConfigs.forEach(this::submitConfig);
    }

    boolean isDue(ScraperConfig config, LocalDateTime now) {
        LocalDateTime lastSuccessAt = config.getLastSuccessAt();
        return lastSuccessAt == null
                || !now.isBefore(lastSuccessAt.plusMinutes(config.getIntervalMinutes()));
    }

    private boolean isAlreadyRunning(ScraperConfig config) {
        return scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING);
    }

    private void submitConfig(ScraperConfig config) {
        if (!tryAcquireInMemoryLock(config)) {
            return;
        }

        try {
            scraperExecutor.execute(() -> runConfig(config));
        } catch (RuntimeException ex) {
            releaseInMemoryLock(config);
            log.error("Failed to submit scraper config {} for execution", config.getSlug(), ex);
        }
    }

    private void runConfig(ScraperConfig config) {
        try {
            runLockedConfig(config);
        } finally {
            releaseInMemoryLock(config);
        }
    }

    private void runLockedConfig(ScraperConfig config) {
        ScraperRunner scraperRunner = scraperRunnerProvider.getIfAvailable();
        if (scraperRunner == null) {
            log.warn("Scraper config {} is due but no ScraperRunner bean is configured", config.getSlug());
            return;
        }

        log.info("Starting scraper config {} with spider {}", config.getSlug(), config.getSpiderName());
        ScrapeRun scrapeRun = offerIngestionService.startRun(config);
        ScraperRunResult result;
        try {
            result = scraperRunner.run(config);
        } catch (RuntimeException ex) {
            result = new ScraperRunResult(null, -1, "", "", ex.getMessage());
        }
        offerIngestionService.completeRun(scrapeRun, result);
    }

    private boolean tryAcquireInMemoryLock(ScraperConfig config) {
        Long configId = config.getId();
        if (configId == null) {
            log.warn("Scraper config {} has no id; in-memory scheduler lock is skipped", config.getSlug());
            return true;
        }

        if (!runningScraperConfigIds.add(configId)) {
            log.info("Skipping scraper config {} because it is already running in this Java process", config.getSlug());
            return false;
        }

        Long marketId = config.getMarket() == null ? null : config.getMarket().getId();
        if (marketId != null && !runningMarketIds.add(marketId)) {
            runningScraperConfigIds.remove(configId);
            log.info(
                    "Skipping scraper config {} because market {} is already being scraped in this Java process",
                    config.getSlug(),
                    config.getMarket().getSlug()
            );
            return false;
        }

        return true;
    }

    private void releaseInMemoryLock(ScraperConfig config) {
        Long configId = config.getId();
        if (configId != null) {
            runningScraperConfigIds.remove(configId);
        }
        Long marketId = config.getMarket() == null ? null : config.getMarket().getId();
        if (marketId != null) {
            runningMarketIds.remove(marketId);
        }
    }
}
