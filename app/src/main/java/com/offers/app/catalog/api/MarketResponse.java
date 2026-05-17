package com.offers.app.catalog.api;

import java.time.LocalDateTime;

public record MarketResponse(
        Long id,
        String name,
        String slug,
        String baseUrl,
        LocalDateTime lastScrapedAt
) {
}
