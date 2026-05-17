package com.offers.app.catalog.application;

import com.offers.app.catalog.api.CategoryResponse;
import com.offers.app.catalog.api.MarketResponse;
import com.offers.app.catalog.api.OfferCategoryResponse;
import com.offers.app.catalog.api.OfferMarketResponse;
import com.offers.app.catalog.api.OfferResponse;
import com.offers.app.catalog.domain.Category;
import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import com.offers.app.catalog.repository.CategoryRepository;
import com.offers.app.catalog.repository.MarketRepository;
import com.offers.app.catalog.repository.OfferRepository;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class CatalogQueryService {

    private final OfferRepository offerRepository;
    private final CategoryRepository categoryRepository;
    private final MarketRepository marketRepository;

    public CatalogQueryService(
            OfferRepository offerRepository,
            CategoryRepository categoryRepository,
            MarketRepository marketRepository
    ) {
        this.offerRepository = offerRepository;
        this.categoryRepository = categoryRepository;
        this.marketRepository = marketRepository;
    }

    public List<OfferResponse> getOffers() {
        return offerRepository.findPublicOffers()
                .stream()
                .map(this::toOfferResponse)
                .toList();
    }

    public List<CategoryResponse> getCategories() {
        return categoryRepository.findAllByOrderByNameAsc()
                .stream()
                .map(this::toCategoryResponse)
                .toList();
    }

    public List<MarketResponse> getMarkets() {
        return marketRepository.findAllByActiveTrueOrderByNameAsc()
                .stream()
                .map(this::toMarketResponse)
                .toList();
    }

    private OfferResponse toOfferResponse(Offer offer) {
        return new OfferResponse(
                offer.getId(),
                offer.getTitle(),
                offer.getProductUrl(),
                offer.getImageUrl(),
                offer.getBrand(),
                offer.getCurrency(),
                offer.getOldPrice(),
                offer.getSalePrice(),
                offer.getDiscountPercent(),
                offer.getStockStatus(),
                labels(offer.getLabels()),
                metadata(offer.getMetadata()),
                offer.getFirstSeenAt(),
                offer.getLastSeenAt(),
                offer.getLastScrapedAt(),
                toOfferMarketResponse(offer.getMarket()),
                toOfferCategoryResponse(offer.getCategory())
        );
    }

    private CategoryResponse toCategoryResponse(Category category) {
        Category parent = category.getParent();
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getSlug(),
                parent == null ? null : parent.getId(),
                parent == null ? null : parent.getSlug()
        );
    }

    private MarketResponse toMarketResponse(Market market) {
        return new MarketResponse(
                market.getId(),
                market.getName(),
                market.getSlug(),
                market.getBaseUrl(),
                market.getLastScrapedAt()
        );
    }

    private OfferMarketResponse toOfferMarketResponse(Market market) {
        return new OfferMarketResponse(market.getId(), market.getName(), market.getSlug());
    }

    private OfferCategoryResponse toOfferCategoryResponse(Category category) {
        if (category == null) {
            return null;
        }
        return new OfferCategoryResponse(category.getId(), category.getName(), category.getSlug());
    }

    private List<String> labels(String[] labels) {
        if (labels == null || labels.length == 0) {
            return List.of();
        }
        return List.copyOf(Arrays.asList(labels));
    }

    private Map<String, Object> metadata(Map<String, Object> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(metadata));
    }
}
