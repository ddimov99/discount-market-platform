package com.offers.app.catalog.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import com.offers.app.scraper.config.ScraperSpiderToggleProperties;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
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
            offerIngestionService,
            spiderToggles(),
            Runnable::run
    );

    @BeforeEach
    void setUp() {
        when(scraperRunnerProvider.getIfAvailable()).thenReturn(scraperRunner);
    }

    @Test
    void runsEnabledConfigWhenItHasNeverSucceeded() {
        ScraperConfig config = scraperConfig();
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));
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
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));

        scheduler.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void skipsConfigThatAlreadyHasRunningRun() {
        ScraperConfig config = scraperConfig();
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING))
                .thenReturn(true);

        scheduler.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void skipsConfigDisabledByApplicationProperties() {
        ScraperConfig config = scraperConfig();
        ScraperScheduler schedulerWithDisabledSpider = scheduler(spiderToggles(
                "ozone-discounts",
                false
        ));
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));

        schedulerWithDisabledSpider.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void runsDbDisabledConfigWhenApplicationPropertiesEnableIt() {
        ScraperConfig config = scraperConfig();
        config.setEnabled(false);
        ScraperScheduler schedulerWithEnabledSpider = scheduler(spiderToggles(
                "ozone-discounts",
                true
        ));
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(config, ScrapeRunStatus.RUNNING))
                .thenReturn(false);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        ScraperRunResult result = new ScraperRunResult(null, 0, "", "", null);
        when(offerIngestionService.startRun(config)).thenReturn(scrapeRun);
        when(scraperRunner.run(config)).thenReturn(result);

        schedulerWithEnabledSpider.runDueScrapers();

        verify(scraperRunner).run(config);
        verify(offerIngestionService).completeRun(scrapeRun, result);
    }

    @Test
    void skipsDbDisabledConfigWhenApplicationPropertiesDoNotOverrideIt() {
        ScraperConfig config = scraperConfig();
        config.setEnabled(false);
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));

        scheduler.runDueScrapers();

        verify(scraperRunner, never()).run(config);
    }

    @Test
    void skipsConfigAlreadyRunningInThisJavaProcess() {
        ScraperConfig config = scraperConfig();
        ReflectionTestUtils.setField(config, "id", 123L);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        ScraperRunResult result = new ScraperRunResult(null, 0, "", "", null);
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(config));
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

    @Test
    void submitsDifferentDueConfigsForParallelExecution() {
        List<Runnable> submittedTasks = new ArrayList<>();
        Executor recordingExecutor = submittedTasks::add;
        ScraperScheduler asyncScheduler = new ScraperScheduler(
                scraperConfigRepository,
                scrapeRunRepository,
                scraperRunnerProvider,
                offerIngestionService,
                spiderToggles(),
                recordingExecutor
        );
        ScraperConfig ozoneConfig = scraperConfig("ozone-discounts", "ozone", 123L, 1L);
        ScraperConfig ardesConfig = scraperConfig("ardes-products", "ardes", 456L, 2L);
        when(scraperConfigRepository.findAllWithMarket()).thenReturn(List.of(ozoneConfig, ardesConfig));
        when(scrapeRunRepository.existsByScraperConfigAndStatus(ozoneConfig, ScrapeRunStatus.RUNNING))
                .thenReturn(false);
        when(scrapeRunRepository.existsByScraperConfigAndStatus(ardesConfig, ScrapeRunStatus.RUNNING))
                .thenReturn(false);

        asyncScheduler.runDueScrapers();

        assertThat(submittedTasks).hasSize(2);
        verify(scraperRunner, never()).run(any());
    }

    private ScraperConfig scraperConfig() {
        return scraperConfig("ozone-discounts", "ozone", null, null);
    }

    private ScraperConfig scraperConfig(String slug, String marketSlug, Long configId, Long marketId) {
        Market market = new Market(marketSlug, marketSlug, "https://www." + marketSlug + ".bg/");
        if (marketId != null) {
            ReflectionTestUtils.setField(market, "id", marketId);
        }
        ScraperConfig config = new ScraperConfig(
                market,
                slug,
                slug,
                "ozone_discount_scraper",
                slug.replace('-', '_'),
                60,
                300
        );
        if (configId != null) {
            ReflectionTestUtils.setField(config, "id", configId);
        }
        return config;
    }

    private ScraperScheduler scheduler(ScraperSpiderToggleProperties spiderToggleProperties) {
        return new ScraperScheduler(
                scraperConfigRepository,
                scrapeRunRepository,
                scraperRunnerProvider,
                offerIngestionService,
                spiderToggleProperties,
                Runnable::run
        );
    }

    private ScraperSpiderToggleProperties spiderToggles() {
        return new ScraperSpiderToggleProperties(Map.of());
    }

    private ScraperSpiderToggleProperties spiderToggles(String slug, boolean enabled) {
        return new ScraperSpiderToggleProperties(Map.of(
                slug,
                new ScraperSpiderToggleProperties.Spider(enabled)
        ));
    }
}
