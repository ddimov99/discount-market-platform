package com.offers.app.catalog.api;

import com.offers.app.catalog.domain.StockStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public record OfferResponse(
        Long id,
        String title,
        String productUrl,
        String imageUrl,
        String brand,
        String sourceCategory,
        String sourceSubcategory,
        String currency,
        BigDecimal oldPrice,
        BigDecimal salePrice,
        BigDecimal discountPercent,
        StockStatus stockStatus,
        List<String> labels,
        Map<String, Object> metadata,
        LocalDateTime firstSeenAt,
        LocalDateTime lastSeenAt,
        LocalDateTime lastScrapedAt,
        OfferMarketResponse market,
        OfferCategoryResponse category
) {
}
