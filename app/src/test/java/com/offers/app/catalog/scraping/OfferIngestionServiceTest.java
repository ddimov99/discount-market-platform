package com.offers.app.catalog.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offers.app.catalog.domain.Category;
import com.offers.app.catalog.domain.CategoryAlias;
import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import com.offers.app.catalog.domain.OfferSnapshot;
import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import com.offers.app.catalog.domain.StockStatus;
import com.offers.app.catalog.repository.CategoryAliasRepository;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

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
    private final CategoryAliasRepository categoryAliasRepository =
            org.mockito.Mockito.mock(CategoryAliasRepository.class);
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
            categoryAliasRepository,
            marketRepository,
            clock
    );

    @BeforeEach
    void setUp() {
        when(scrapeRunRepository.save(any(ScrapeRun.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(scraperConfigRepository.save(any(ScraperConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerRepository.save(any(Offer.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(offerSnapshotRepository.save(any(OfferSnapshot.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(marketRepository.save(any(Market.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(categoryAliasRepository.findAll()).thenReturn(List.of());
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
        Category matchedCategory = categoryWithId(10L, "Gaming Laptops", 200);
        CategoryAlias alias = new CategoryAlias(matchedCategory, "gaming laptops", "generated");

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(dto));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
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
        assertThat(savedOffer.getSourceCategory()).isEqualTo("Computers");
        assertThat(savedOffer.getSourceSubcategory()).isEqualTo("Gaming Laptops");
        assertThat(savedOffer.getCurrency()).isEqualTo("EUR");
        assertThat(savedOffer.getOldPrice()).isEqualByComparingTo(new BigDecimal("599.00"));
        assertThat(savedOffer.getSalePrice()).isEqualByComparingTo(new BigDecimal("499.00"));
        assertThat(savedOffer.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("17.00"));
        assertThat(savedOffer.getLabels()).containsExactly("-17%", "installment");
        assertThat(savedOffer.getStockStatus()).isEqualTo(StockStatus.IN_STOCK);
        assertThat(savedOffer.getLastSeenAt()).isEqualTo(now);
        assertThat(savedOffer.getLastScrapedAt()).isEqualTo(now);
        assertThat(savedOffer.isStale()).isFalse();
        assertThat(savedOffer.getCategory()).isSameAs(matchedCategory);
        assertThat(savedOffer.getCategory().getName()).isEqualTo("Gaming Laptops");
        assertThat(savedOffer.getMetadata()).containsAllEntriesOf(Map.of(
                "discount_label", "-17%",
                "sales_type", "Laptops",
                "attribute_set", "Electronics",
                "is_last_units", true,
                "source_url", "https://www.ozone.bg/"
        ));
        assertThat(savedOffer.getMetadata()).doesNotContainKeys("category", "subcategory");

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
    void resolvesMultipleOffersWithTheSameSourceSubcategoryOnceAndAppliesTheCategory() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        Category gamingLaptops = categoryWithId(10L, "Gaming Laptops", 100);
        CategoryAlias alias = new CategoryAlias(gamingLaptops, "gaming laptops", "generated");
        ScrapedOfferDto first = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/iphone/",
                "Computers",
                "Gaming Laptops",
                "Electronics > Computers > Gaming Laptops"
        );
        ScrapedOfferDto second = scrapedOffer(
                "P2",
                "https://www.ozone.bg/product/tv/",
                "Computers",
                "Gaming Laptops"
        );

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(first, second));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, org.mockito.Mockito.times(2)).save(offerCaptor.capture());
        assertThat(offerCaptor.getAllValues())
                .extracting(Offer::getCategory)
                .containsExactly(gamingLaptops, gamingLaptops);
    }

    @Test
    void resolvesBySourceCategoryWhenSourceSubcategoryIsMissing() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        Category televisions = categoryWithId(20L, "Televisions", 100);
        CategoryAlias alias = new CategoryAlias(televisions, "televisions", "generated");
        ScrapedOfferDto first = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/tv/",
                "Televisions",
                null
        );
        ScrapedOfferDto second = scrapedOffer(
                "P2",
                "https://www.ozone.bg/product/oled/",
                "Televisions",
                "   "
        );

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(first, second));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, org.mockito.Mockito.times(2)).save(offerCaptor.capture());
        assertThat(offerCaptor.getAllValues())
                .extracting(Offer::getCategory)
                .containsExactly(televisions, televisions);
    }

    @Test
    void keepsNullCategoryWhenSourceCategoryHasNoSafeLocalMatch() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        ScrapedOfferDto unmatched = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/unknown/",
                "Very Different Source Category",
                null
        );

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(unmatched));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getCategory()).isNull();
    }

    @Test
    void keepsNullCategoryForOffersWithoutSourceCategories() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        ScrapedOfferDto uncategorized = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/mystery/",
                "   ",
                null
        );

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(uncategorized));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getCategory()).isNull();
    }

    @Test
    void matchesSourceCategoryAgainstCategoryAlias() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        ScrapedOfferDto smartphone = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/iphone/",
                "Смартфони",
                null
        );
        Category mobilePhones = categoryWithId(3L, "Мобилни телефони (GSM)", 110);
        CategoryAlias alias = new CategoryAlias(mobilePhones, "смартфони", "generated");

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(smartphone));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getCategory()).isSameAs(mobilePhones);
    }

    @Test
    void keepsNullCategoryWhenExactAliasMapsToMultipleCategories() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        ScrapedOfferDto accessory = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/accessory/",
                "Аксесоари",
                null
        );
        Category phoneAccessories = categoryWithId(40L, "Phone Accessories", 100);
        Category laptopAccessories = categoryWithId(41L, "Laptop Accessories", 110);

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(accessory));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(
                new CategoryAlias(phoneAccessories, "аксесоари", "generated"),
                new CategoryAlias(laptopAccessories, "аксесоари", "generated")
        ));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getCategory()).isNull();
    }

    @Test
    void cachesCategoryAliasesAfterFirstLoad() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        Path firstOutputFile = Path.of("/tmp/offers-1.jsonl");
        Path secondOutputFile = Path.of("/tmp/offers-2.jsonl");
        Category mobilePhones = categoryWithId(3L, "Мобилни телефони (GSM)", 110);
        CategoryAlias alias = new CategoryAlias(mobilePhones, "смартфони", "generated");

        when(scrapedOfferJsonlReader.read(firstOutputFile)).thenReturn(List.of(scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/iphone-1/",
                "Смартфони",
                null
        )));
        when(scrapedOfferJsonlReader.read(secondOutputFile)).thenReturn(List.of(scrapedOffer(
                "P2",
                "https://www.ozone.bg/product/iphone-2/",
                "Смартфони",
                null
        )));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(new ScrapeRun(config), new ScraperRunResult(firstOutputFile, 0, "", "", null));
        service.completeRun(new ScrapeRun(config), new ScraperRunResult(secondOutputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository, org.mockito.Mockito.times(2)).save(offerCaptor.capture());
        assertThat(offerCaptor.getAllValues())
                .extracting(Offer::getCategory)
                .containsExactly(mobilePhones, mobilePhones);
        verify(categoryAliasRepository).findAll();
    }

    @Test
    void matchesSmallPluralDifferenceWithStringSimilarity() throws IOException {
        Market market = market();
        ScraperConfig config = scraperConfig(market);
        ScrapeRun scrapeRun = new ScrapeRun(config);
        Path outputFile = Path.of("/tmp/offers.jsonl");
        ScrapedOfferDto tablet = scrapedOffer(
                "P1",
                "https://www.ozone.bg/product/tablet/",
                "Таблети",
                null
        );
        Category tablets = categoryWithId(30L, "Таблет", 120);
        CategoryAlias alias = new CategoryAlias(tablets, "таблет", "generated");

        when(scrapedOfferJsonlReader.read(outputFile)).thenReturn(List.of(tablet));
        when(categoryAliasRepository.findAll()).thenReturn(List.of(alias));
        when(offerRepository.findByMarketAndSourceProductKeyIn(eq(market), anyCollection())).thenReturn(List.of());
        when(offerRepository.findByMarketAndStaleFalse(market)).thenReturn(List.of());

        service.completeRun(scrapeRun, new ScraperRunResult(outputFile, 0, "", "", null));

        ArgumentCaptor<Offer> offerCaptor = ArgumentCaptor.forClass(Offer.class);
        verify(offerRepository).save(offerCaptor.capture());
        assertThat(offerCaptor.getValue().getCategory()).isSameAs(tablets);
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

    private Category categoryWithId(Long id, String name, int sortOrder) {
        Category category = new Category(name, null, sortOrder);
        ReflectionTestUtils.setField(category, "id", id);
        return category;
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
        return scrapedOffer(productId, productUrl, "Electronics");
    }

    private ScrapedOfferDto scrapedOffer(String productId, String productUrl, String attributeSet) {
        return scrapedOffer(productId, productUrl, "Computers", "Gaming Laptops", attributeSet);
    }

    private ScrapedOfferDto scrapedOffer(
            String productId,
            String productUrl,
            String category,
            String subcategory
    ) {
        return scrapedOffer(productId, productUrl, category, subcategory, "Electronics");
    }

    private ScrapedOfferDto scrapedOffer(
            String productId,
            String productUrl,
            String category,
            String subcategory,
            String attributeSet
    ) {
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
                category,
                subcategory,
                "-17%",
                "Laptops",
                attributeSet,
                true,
                "https://www.ozone.bg/"
        );
    }
}
