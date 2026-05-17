package com.offers.app.catalog.scraping;

import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.catalog.repository.ScrapeRunRepository;
import com.offers.app.catalog.repository.ScraperConfigRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScraperScheduler {

    private static final Logger log = LoggerFactory.getLogger(ScraperScheduler.class);

    private final ScraperConfigRepository scraperConfigRepository;
    private final ScrapeRunRepository scrapeRunRepository;
    private final ObjectProvider<ScraperRunner> scraperRunnerProvider;
    private final OfferIngestionService offerIngestionService;
    private final Set<Long> runningScraperConfigIds = ConcurrentHashMap.newKeySet();

    public ScraperScheduler(
            ScraperConfigRepository scraperConfigRepository,
            ScrapeRunRepository scrapeRunRepository,
            ObjectProvider<ScraperRunner> scraperRunnerProvider,
            OfferIngestionService offerIngestionService
    ) {
        this.scraperConfigRepository = scraperConfigRepository;
        this.scrapeRunRepository = scrapeRunRepository;
        this.scraperRunnerProvider = scraperRunnerProvider;
        this.offerIngestionService = offerIngestionService;
    }

    @Scheduled(fixedDelayString = "${scrapers.scheduler.fixed-delay-ms}")
    public void runDueScrapers() {
        LocalDateTime now = LocalDateTime.now();
        List<ScraperConfig> dueConfigs = scraperConfigRepository.findByEnabledTrue()
                .stream()
                .filter(config -> isDue(config, now))
                .filter(config -> !isAlreadyRunning(config))
                .toList();

        dueConfigs.forEach(this::runConfig);
    }

    boolean isDue(ScraperConfig config, LocalDateTime now) {
        LocalDateTime lastSuccessAt = config.getLastSuccessAt();
        return lastSuccessAt == null
                || !now.isBefore(lastSuccessAt.plusMinutes(config.getIntervalMinutes()));
    }

    private boolean isAlreadyRunning(ScraperConfig config) {
        return scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING);
    }

    private void runConfig(ScraperConfig config) {
        if (!tryAcquireInMemoryLock(config)) {
            return;
        }

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

        boolean acquired = runningScraperConfigIds.add(configId);
        if (!acquired) {
            log.info("Skipping scraper config {} because it is already running in this Java process", config.getSlug());
        }
        return acquired;
    }

    private void releaseInMemoryLock(ScraperConfig config) {
        Long configId = config.getId();
        if (configId != null) {
            runningScraperConfigIds.remove(configId);
        }
    }
}
