package com.offers.app.catalog.scraping;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.catalog.repository.ScrapeRunRepository;
import com.offers.app.catalog.repository.ScraperConfigRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.test.util.ReflectionTestUtils;

class ScraperSchedulerTest {

    private final ScraperConfigRepository scraperConfigRepository =
            org.mockito.Mockito.mock(ScraperConfigRepository.class);
    private final ScrapeRunRepository scrapeRunRepository =
            org.mockito.Mockito.mock(ScrapeRunRepository.class);
    private final ScraperRunner scraperRunner = org.mockito.Mockito.mock(ScraperRunner.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ScraperRunner> scraperRunnerProvider =
            org.mockito.Mockito.mock(ObjectProvider.class);
    private final OfferIngestionService offerIngestionService = org.mockito.Mockito.mock(OfferIngestionService.class);

    private final ScraperScheduler scheduler = new ScraperScheduler(
            scraperConfigRepository,
            scrapeRunRepository,
            scraperRunnerProvider,
            offerIngestionService
    );

    @BeforeEach
    void setUp() {
        when(scraperRunnerProvider.getIfAvailable()).thenReturn(scraperRunner);
    }

    @Test
    void runsEnabledConfigWhenItHasNeverSucceeded() {
        ScraperConfig config = scraperConfig();
        when(scraperConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING))
                .thenReturn(false);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        when(offerIngestionService.startRun(config)).thenReturn(scrapeRun);
        ScraperRunResult result = new ScraperRunResult(null, 0, "", "", null);
        when(scraperRunner.run(config)).thenReturn(result);

        scheduler.runDueScrapers();

        verify(scraperRunner).run(config);
        verify(offerIngestionService).completeRun(scrapeRun, result);
    }

    @Test
    void skipsConfigThatIsNotDueYet() {
        ScraperConfig config = scraperConfig();
        config.setLastSuccessAt(LocalDateTime.now().minusMinutes(5));
        config.setIntervalMinutes(30);
        when(scraperConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));

        scheduler.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void skipsConfigThatAlreadyHasRunningRun() {
        ScraperConfig config = scraperConfig();
        when(scraperConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING))
                .thenReturn(true);

        scheduler.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void skipsConfigAlreadyRunningInThisJavaProcess() {
        ScraperConfig config = scraperConfig();
        ReflectionTestUtils.setField(config, "id", 123L);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        ScraperRunResult result = new ScraperRunResult(null, 0, "", "", null);
        when(scraperConfigRepository.findByEnabledTrue()).thenReturn(List.of(config));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING))
                .thenReturn(false);
        when(offerIngestionService.startRun(config)).thenReturn(scrapeRun);
        when(scraperRunner.run(config)).thenAnswer(invocation -> {
            scheduler.runDueScrapers();
            return result;
        });

        scheduler.runDueScrapers();

        verify(offerIngestionService, times(1)).startRun(config);
        verify(scraperRunner, times(1)).run(config);
        verify(offerIngestionService, times(1)).completeRun(scrapeRun, result);
    }

    private ScraperConfig scraperConfig() {
        return new ScraperConfig(
                new Market("Ozone", "ozone", "https://www.ozone.bg/"),
                "Ozone Discounts",
                "ozone-discounts",
                "ozone_discount_scraper",
                "ozone_discounts",
                60,
                300
        );
    }
}
