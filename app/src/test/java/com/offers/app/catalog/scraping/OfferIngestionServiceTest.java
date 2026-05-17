package com.offers.app.catalog.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offers.app.catalog.domain.Category;
import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import com.offers.app.catalog.domain.OfferSnapshot;
import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.catalog.domain.StockStatus;
import com.offers.app.catalog.repository.CategoryRepository;
import com.offers.app.catalog.repository.MarketRepository;
import com.offers.app.catalog.repository.OfferRepository;
import com.offers.app.catalog.repository.OfferSnapshotRepository;
import com.offers.app.catalog.repository.ScrapeRunRepository;
import com.offers.app.catalog.repository.ScraperConfigRepository;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OfferIngestionServiceTest {

    private final ScrapedOfferJsonlReader scrapedOfferJsonlReader =
            org.mockito.Mockito.mock(ScrapedOfferJsonlReader.class);
    private final ScrapeRunRepository scrapeRunRepository =
            org.mockito.Mockito.mock(ScrapeRunRepository.class);
    private final ScraperConfigRepository scraperConfigRepository =
            org.mockito.Mockito.mock(ScraperConfigRepository.class);
    private final OfferRepository offerRepository =
            org.mockito.Mockito.mock(OfferRepository.class);
    private final OfferSnapshotRepository offerSnapshotRepository =
            org.mockito.Mockito.mock(OfferSnapshotRepository.class);
    private final CategoryRepository categoryRepository =
            org.mockito.Mockito.mock(CategoryRepository.class);
    private final MarketRepository marketRepository =
            org.mockito.Mockito.mock(MarketRepository.class);

    private final Clock clock = Clock.fixed(Instant.parse("2026-05-16T17:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime now = LocalDateTime.of(2026, 5, 16, 17, 0);

    private final OfferIngestionService service = new OfferIngestionService(
            scrapedOfferJsonlReader,
            scrapeRunRepository,
            scraperConfigRepository,
            offerRepository,
            offerSnapshotRepository,
            categoryRepository,
            marketRepository,
            clock
    );

    @BeforeEach
    void setUp() {
        when(scrapeRunRepository.save(any(ScrapeRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scraperConfigRepository.save(any(ScraperConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerSnapshotRepository.save(any(OfferSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void startRunClearsPreviousFinishState() {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        config.setLastFinishedAt(now.minusHours(1));
        config.setLastErrorMessage("old error");

        ScrapeRun scrapeRun = service.startRun(config);

        assertThat(config.getLastStartedAt()).isEqualTo(now);
        assertThat(config.getLastFinishedAt()).isNull();
        assertThat(config.getLastErrorMessage()).isNull();
        assertThat(scrapeRun.getStatus()).isEqualTo(ScrapeRunStatus.RUNNING);
        assertThat(scrapeRun.getScraperConfig()).isSameAs(config);
    }

    @Test
    void ingestsScrapedOffersAndUpdatesRunState() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/discount-market-scrapes/ozone-discounts-123.jsonl");
        ScrapedOfferDto dto = scrapedOffer("P1", "https://www.ozone.bg/product/laptop/");
        Offer staleOffer = new Offer(market, "OLD", "Old", "https://www.ozone.bg/product/old");

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(dto));
        when(categoryRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of(staleOffer));

        ScrapeRun completed = service.completeRun(
                scrapeRun,
                new ScraperRunResult(outputFile, 0, "ok", "", null)
        );

        assertThat(completed.getStatus()).isEqualTo(ScrapeRunStatus.SUCCESS);
        assertThat(completed.getFinishedAt()).isEqualTo(now);
        assertThat(completed.getOffersSeen()).isEqualTo(1);
        assertThat(completed.getOffersCreated()).isEqualTo(1);
        assertThat(completed.getOffersUpdated()).isZero();
        assertThat(completed.getOffersMarkedStale()).isEqualTo(1);
        assertThat(completed.getErrorMessage()).isNull();
        assertThat(market.getLastScrapedAt()).isEqualTo(now);
        assertThat(config.getLastFinishedAt()).isEqualTo(now);
        assertThat(config.getLastSuccessAt()).isEqualTo(now);
        assertThat(config.getLastErrorMessage()).isNull();
        assertThat(staleOffer.isStale()).isTrue();

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        Offer savedOffer = offerCaptor.getValue();
        assertThat(savedOffer.getSourceProductKey()).isEqualTo("P1");
        assertThat(savedOffer.getSourceProductId()).isEqualTo("P1");
        assertThat(savedOffer.getTitle()).isEqualTo("Laptop");
        assertThat(savedOffer.getProductUrl()).isEqualTo("https://www.ozone.bg/product/laptop/");
        assertThat(savedOffer.getBrand()).isEqualTo("Lenovo");
        assertThat(savedOffer.getCurrency()).isEqualTo("EUR");
        assertThat(savedOffer.getOldPrice()).isEqualByComparingTo(new BigDecimal("599.00"));
        assertThat(savedOffer.getSalePrice()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat(savedOffer.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(savedOffer.getLabels()).containsExactly("-17%", "installment");
        assertThat(savedOffer.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(savedOffer.getLastSeenAt()).isEqualTo(now);
        assertThat(savedOffer.getLastScrapedAt()).isEqualTo(now);
        assertThat(savedOffer.isStale()).isFalse();
        assertThat(savedOffer.getCategory().getName()).isEqualTo("Gaming Laptops");
        assertThat(savedOffer.getCategory().getParent().getName()).isEqualTo("Computers");
        assertThat(savedOffer.getMetadata()).containsAllEntriesOf(Map.of(
                "category", "Computers",
                "subcategory", "Gaming Laptops",
                "discount_label", "-17%",
                "sales_type", "Laptops",
                "attribute_set", "Electronics",
                "is_last_units", true,
                "source_url", "https://www.ozone.bg/"
        ));

        ArgumentCaptor<OfferSnapshot> snapshotCaptor = ArgumentCaptor.forClass(OfferSnapshot.class);
        verify(offerSnapshotRepository).save(snapshotCaptor.capture());
        OfferSnapshot snapshot = snapshotCaptor.getValue();
        assertThat(snapshot.getOffer()).isSameAs(savedOffer);
        assertThat(snapshot.getOldPrice()).isEqualByComparingTo(new BigDecimal("599.00"));
        assertThat(snapshot.getSalePrice()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat(snapshot.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(snapshot.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
    }

    @Test
    void usesNormalizedProductUrlWhenProductIdIsMissing() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(scrapedOffer(null, "HTTPS://Example.com/products/Item/?utm=1")));
        when(categoryRepository.findBySlug(any())).thenReturn(Optional.empty());
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getSourceProductKey())
                .isEqualTo("https://example.com/products/Item");
        assertThat(offerCaptor.getValue().getSourceProductId()).isNull();
    }

    @Test
    void marksRunFailedWhenRunnerFailed() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);

        ScrapeRun completed = service.completeRun(
                scrapeRun,
                new ScraperRunResult(Path.of("/tmp/missing.jsonl"), -1, "", "bad", "Scrapy timed out")
        );

        assertThat(completed.getStatus()).isEqualTo(ScrapeRunStatus.FAILED);
        assertThat(completed.getFinishedAt()).isEqualTo(now);
        assertThat(completed.getErrorMessage()).isEqualTo("Scrapy timed out");
        assertThat(config.getLastFinishedAt()).isEqualTo(now);
        assertThat(config.getLastSuccessAt()).isNull();
        assertThat(config.getLastErrorMessage()).isEqualTo("Scrapy timed out");
        verify(scrapedOfferJsonlReader, never()).read(any());
    }

    private Market market() {
        return new Market("Ozone", "ozone", "https://www.ozone.bg/");
    }

    private ScraperConfig scraperConfig(Market market) {
        return new ScraperConfig(
                market,
                "Ozone Discounts",
                "ozone-discounts",
                "ozone_discount_scraper",
                "ozone_discounts",
                60,
                300
        );
    }

    private ScrapedOfferDto scrapedOffer(String productId, String productUrl) {
        return new ScrapedOfferDto(
                productId,
                "Laptop",
                productUrl,
                "https://cdn.example/laptop.jpg",
                "Lenovo",
                "EUR",
                new BigDecimal("599.00"),
                new BigDecimal("499.00"),
                new BigDecimal("17.00"),
                List.of("-17%", "installment"),
                "in_stock",
                "Computers",
                "Gaming Laptops",
                "-17%",
                "Laptops",
                "Electronics",
                true,
                "https://www.ozone.bg/"
        );
    }
}
