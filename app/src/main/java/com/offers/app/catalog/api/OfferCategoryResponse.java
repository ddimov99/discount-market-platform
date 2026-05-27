package com.offers.app.catalog.api;

public record OfferCategoryResponse(
        Long id,
        String name,
        Long parentId,
        String parentName
) {
}
