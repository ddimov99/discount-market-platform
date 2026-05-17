package com.offers.app.catalog.scraping;

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
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OfferIngestionService {

    private static final String DEFAULT_CURRENCY = "EUR";
    private static final int SOURCE_PRODUCT_KEY_MAX_LENGTH = 255;

    private final ScrapedOfferJsonlReader scrapedOfferJsonlReader;
    private final ScrapeRunRepository scrapeRunRepository;
    private final ScraperConfigRepository scraperConfigRepository;
    private final OfferRepository offerRepository;
    private final OfferSnapshotRepository offerSnapshotRepository;
    private final CategoryRepository categoryRepository;
    private final MarketRepository marketRepository;
    private final Clock clock;

    @Autowired
    public OfferIngestionService(
            ScrapedOfferJsonlReader scrapedOfferJsonlReader,
            ScrapeRunRepository scrapeRunRepository,
            ScraperConfigRepository scraperConfigRepository,
            OfferRepository offerRepository,
            OfferSnapshotRepository offerSnapshotRepository,
            CategoryRepository categoryRepository,
            MarketRepository marketRepository
    ) {
        this(
                scrapedOfferJsonlReader,
                scrapeRunRepository,
                scraperConfigRepository,
                offerRepository,
                offerSnapshotRepository,
                categoryRepository,
                marketRepository,
                Clock.systemDefaultZone()
        );
    }

    OfferIngestionService(
            ScrapedOfferJsonlReader scrapedOfferJsonlReader,
            ScrapeRunRepository scrapeRunRepository,
            ScraperConfigRepository scraperConfigRepository,
            OfferRepository offerRepository,
            OfferSnapshotRepository offerSnapshotRepository,
            CategoryRepository categoryRepository,
            MarketRepository marketRepository,
            Clock clock
    ) {
        this.scrapedOfferJsonlReader = scrapedOfferJsonlReader;
        this.scrapeRunRepository = scrapeRunRepository;
        this.scraperConfigRepository = scraperConfigRepository;
        this.offerRepository = offerRepository;
        this.offerSnapshotRepository = offerSnapshotRepository;
        this.categoryRepository = categoryRepository;
        this.marketRepository = marketRepository;
        this.clock = clock;
    }

    @Transactional
    public ScrapeRun startRun(ScraperConfig config) {
        ScraperConfig managedConfig = managedConfig(config);
        managedConfig.setLastStartedAt(now());
        managedConfig.setLastFinishedAt(null);
        managedConfig.setLastErrorMessage(null);
        return scrapeRunRepository.save(new ScrapeRun(managedConfig));
    }

    @Transactional
    public ScrapeRun completeRun(ScrapeRun scrapeRun, ScraperRunResult result) {
        ScrapeRun managedRun = managedRun(scrapeRun);
        if (!result.successful()) {
            return markFailed(managedRun, errorMessage(result));
        }

        try {
            List<ScrapedOfferDto> scrapedOffers = scrapedOfferJsonlReader.read(result.outputFile());
            return ingest(managedRun, scrapedOffers);
        } catch (IOException | RuntimeException ex) {
            return markFailed(managedRun, Objects.requireNonNullElse(ex.getMessage(), "Failed to ingest scraped offers"));
        }
    }

    private ScraperConfig managedConfig(ScraperConfig config) {
        Long configId = config.getId();
        if (configId == null) {
            return config;
        }
        return scraperConfigRepository.findWithMarketById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Scraper config not found: " + configId));
    }

    private ScrapeRun managedRun(ScrapeRun scrapeRun) {
        Long scrapeRunId = scrapeRun.getId();
        if (scrapeRunId == null) {
            return scrapeRun;
        }
        return scrapeRunRepository.findWithConfigAndMarketById(scrapeRunId)
                .orElseThrow(() -> new IllegalArgumentException("Scrape run not found: " + scrapeRunId));
    }

    private ScrapeRun ingest(ScrapeRun scrapeRun, List<ScrapedOfferDto> scrapedOffers) {
        LocalDateTime finishedAt = now();
        Market market = scrapeRun.getMarket();
        ScraperConfig config = scrapeRun.getScraperConfig();
        List<ScrapedOffer> seenOffers = scrapedOffers.stream()
                .map(this::toSeenOffer)
                .toList();
        Set<String> seenKeys = seenOffers.stream()
                .map(ScrapedOffer::sourceProductKey)
                .collect(LinkedHashSet::new, Set::add, Set::addAll);
        Map<String, Offer> existingOffers = existingOffersBySourceKey(market, seenKeys);

        Set<String> createdKeys = new LinkedHashSet<>();
        Set<String> updatedKeys = new LinkedHashSet<>();

        for (ScrapedOffer seenOffer : seenOffers) {
            Offer offer = existingOffers.get(seenOffer.sourceProductKey());
            if (offer == null) {
                offer = new Offer(market, seenOffer.sourceProductKey(), seenOffer.dto().title(), seenOffer.dto().productUrl());
                createdKeys.add(seenOffer.sourceProductKey());
            } else if (!createdKeys.contains(seenOffer.sourceProductKey())) {
                updatedKeys.add(seenOffer.sourceProductKey());
            }

            applyScrapedOffer(offer, seenOffer, market, finishedAt);
            Offer savedOffer = offerRepository.save(offer);
            existingOffers.put(seenOffer.sourceProductKey(), savedOffer);
            offerSnapshotRepository.save(snapshot(savedOffer));
        }

        int offersCreated = createdKeys.size();
        int offersUpdated = updatedKeys.size();
        int offersMarkedStale = markUnseenOffersStale(market, seenKeys);

        market.setLastScrapedAt(finishedAt);
        marketRepository.save(market);

        config.setLastFinishedAt(finishedAt);
        config.setLastSuccessAt(finishedAt);
        config.setLastErrorMessage(null);
        scraperConfigRepository.save(config);

        scrapeRun.setStatus(ScrapeRunStatus.SUCCESS);
        scrapeRun.setFinishedAt(finishedAt);
        scrapeRun.setOffersSeen(scrapedOffers.size());
        scrapeRun.setOffersCreated(offersCreated);
        scrapeRun.setOffersUpdated(offersUpdated);
        scrapeRun.setOffersMarkedStale(offersMarkedStale);
        scrapeRun.setErrorMessage(null);
        return scrapeRunRepository.save(scrapeRun);
    }

    private Map<String, Offer> existingOffersBySourceKey(Market market, Collection<String> sourceProductKeys) {
        if (sourceProductKeys.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, Offer> offers = new HashMap<>();
        offerRepository.findByMarketAndSourceProductKeyIn(market, sourceProductKeys)
                .forEach(offer -> offers.put(offer.getSourceProductKey(), offer));
        return offers;
    }

    private void applyScrapedOffer(Offer offer, ScrapedOffer seenOffer, Market market, LocalDateTime scrapedAt) {
        ScrapedOfferDto dto = seenOffer.dto();
        offer.setMarket(market);
        offer.setCategory(resolveCategory(dto.category(), dto.subcategory()));
        offer.setSourceProductId(blankToNull(dto.productId()));
        offer.setSourceProductKey(seenOffer.sourceProductKey());
        offer.setTitle(requireText(dto.title(), "title"));
        offer.setProductUrl(requireText(dto.productUrl(), "product_url"));
        offer.setImageUrl(blankToNull(dto.imageUrl()));
        offer.setBrand(blankToNull(dto.brand()));
        offer.setCurrency(currency(dto.currency()));
        offer.setOldPrice(dto.oldPrice());
        offer.setSalePrice(requireAmount(dto.salePrice(), "sale_price"));
        offer.setDiscountPercent(requireAmount(dto.discountPercent(), "discount_percent"));
        offer.setLabels(labels(dto.labels()));
        offer.setStockStatus(stockStatus(dto.stockStatus()));
        offer.setMetadata(metadata(dto));
        offer.setLastSeenAt(scrapedAt);
        offer.setLastScrapedAt(scrapedAt);
        offer.setStale(false);
    }

    private Category resolveCategory(String categoryName, String subcategoryName) {
        Category category = resolveCategory(categoryName, (Category) null);
        if (isBlank(subcategoryName)) {
            return category;
        }
        return resolveCategory(subcategoryName, category);
    }

    private Category resolveCategory(String name, Category parent) {
        if (isBlank(name)) {
            return parent;
        }

        String slug = slugify(name);
        return categoryRepository.findBySlug(slug)
                .orElseGet(() -> categoryRepository.save(new Category(name.trim(), slug, parent)));
    }

    private OfferSnapshot snapshot(Offer offer) {
        OfferSnapshot snapshot = new OfferSnapshot(offer, offer.getSalePrice(), offer.getDiscountPercent());
        snapshot.setOldPrice(offer.getOldPrice());
        snapshot.setStockStatus(offer.getStockStatus());
        return snapshot;
    }

    private int markUnseenOffersStale(Market market, Set<String> seenKeys) {
        List<Offer> staleOffers = offerRepository.findByMarketAndStaleFalse(market)
                .stream()
                .filter(offer -> !seenKeys.contains(offer.getSourceProductKey()))
                .toList();

        staleOffers.forEach(offer -> offer.setStale(true));
        if (!staleOffers.isEmpty()) {
            offerRepository.saveAll(staleOffers);
        }
        return staleOffers.size();
    }

    private ScrapeRun markFailed(ScrapeRun scrapeRun, String errorMessage) {
        LocalDateTime finishedAt = now();
        ScraperConfig config = scrapeRun.getScraperConfig();
        config.setLastFinishedAt(finishedAt);
        config.setLastErrorMessage(errorMessage);
        scraperConfigRepository.save(config);

        scrapeRun.setStatus(ScrapeRunStatus.FAILED);
        scrapeRun.setFinishedAt(finishedAt);
        scrapeRun.setErrorMessage(errorMessage);
        return scrapeRunRepository.save(scrapeRun);
    }

    private ScrapedOffer toSeenOffer(ScrapedOfferDto dto) {
        requireText(dto.title(), "title");
        requireText(dto.productUrl(), "product_url");
        requireAmount(dto.salePrice(), "sale_price");
        requireAmount(dto.discountPercent(), "discount_percent");
        return new ScrapedOffer(sourceProductKey(dto), dto);
    }

    private String sourceProductKey(ScrapedOfferDto dto) {
        String productId = blankToNull(dto.productId());
        if (productId != null) {
            return fitSourceProductKey(productId);
        }
        return fitSourceProductKey(normalizedProductUrl(requireText(dto.productUrl(), "product_url")));
    }

    private String normalizedProductUrl(String productUrl) {
        String trimmed = productUrl.trim();
        try {
            URI uri = new URI(trimmed).normalize();
            String scheme = lower(uri.getScheme());
            String host = lower(uri.getHost());
            String path = uri.getRawPath() == null ? "" : uri.getRawPath();
            if (path.endsWith("/") && path.length() > 1) {
                path = path.substring(0, path.length() - 1);
            }
            if (scheme == null || host == null) {
                return trimmed;
            }
            return new URI(scheme, uri.getRawUserInfo(), host, uri.getPort(), path, null, null).toString();
        } catch (URISyntaxException ex) {
            return trimmed;
        }
    }

    private String fitSourceProductKey(String sourceProductKey) {
        String trimmed = sourceProductKey.trim();
        if (trimmed.length() <= SOURCE_PRODUCT_KEY_MAX_LENGTH) {
            return trimmed;
        }

        String hash = sha256Hex(trimmed).substring(0, 32);
        return trimmed.substring(0, SOURCE_PRODUCT_KEY_MAX_LENGTH - hash.length() - 1) + "-" + hash;
    }

    private String sha256Hex(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte current : digest) {
                hex.append(String.format("%02x", current));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private String currency(String currency) {
        String value = blankToNull(currency);
        return value == null ? DEFAULT_CURRENCY : value;
    }

    private String[] labels(List<String> labels) {
        if (labels == null || labels.isEmpty()) {
            return new String[0];
        }
        return labels.stream()
                .filter(label -> !isBlank(label))
                .map(String::trim)
                .toArray(String[]::new);
    }

    private StockStatus stockStatus(String stockStatus) {
        String value = blankToNull(stockStatus);
        if (value == null) {
            return StockStatus.UNKNOWN;
        }

        return switch (value.toLowerCase(Locale.ROOT).replace('-', '_')) {
            case "in_stock" -> StockStatus.IN_STOCK;
            case "out_of_stock" -> StockStatus.OUT_OF_STOCK;
            default -> StockStatus.UNKNOWN;
        };
    }

    private Map<String, Object> metadata(ScrapedOfferDto dto) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        putIfNotNull(metadata, "category", blankToNull(dto.category()));
        putIfNotNull(metadata, "subcategory", blankToNull(dto.subcategory()));
        putIfNotNull(metadata, "discount_label", blankToNull(dto.discountLabel()));
        putIfNotNull(metadata, "sales_type", blankToNull(dto.salesType()));
        putIfNotNull(metadata, "attribute_set", blankToNull(dto.attributeSet()));
        putIfNotNull(metadata, "is_last_units", dto.lastUnits());
        putIfNotNull(metadata, "source_url", blankToNull(dto.sourceUrl()));
        return metadata;
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private BigDecimal requireAmount(BigDecimal amount, String fieldName) {
        if (amount == null) {
            throw new IllegalArgumentException("Scraped offer is missing " + fieldName);
        }
        return amount;
    }

    private String requireText(String value, String fieldName) {
        String text = blankToNull(value);
        if (text == null) {
            throw new IllegalArgumentException("Scraped offer is missing " + fieldName);
        }
        return text;
    }

    private String slugify(String value) {
        StringBuilder slug = new StringBuilder();
        boolean previousSeparator = false;
        for (int index = 0; index < value.length(); index++) {
            char current = Character.toLowerCase(value.charAt(index));
            if (Character.isLetterOrDigit(current)) {
                slug.append(current);
                previousSeparator = false;
            } else if (!previousSeparator && !slug.isEmpty()) {
                slug.append('-');
                previousSeparator = true;
            }
        }
        if (!slug.isEmpty() && slug.charAt(slug.length() - 1) == '-') {
            slug.deleteCharAt(slug.length() - 1);
        }
        if (slug.isEmpty()) {
            return "category-" + sha256Hex(value).substring(0, 12);
        }
        return slug.toString();
    }

    private String errorMessage(ScraperRunResult result) {
        String errorMessage = blankToNull(result.errorMessage());
        if (errorMessage != null) {
            return errorMessage;
        }
        return "Scraper failed with exit code " + result.exitCode();
    }

    private String lower(String value) {
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }

    private String blankToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private record ScrapedOffer(String sourceProductKey, ScrapedOfferDto dto) {
    }
}
