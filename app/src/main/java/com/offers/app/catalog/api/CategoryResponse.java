package com.offers.app.catalog.api;

public record CategoryResponse(
        Long id,
        String name,
        String slug,
        Long parentId,
        String parentSlug
) {
}
