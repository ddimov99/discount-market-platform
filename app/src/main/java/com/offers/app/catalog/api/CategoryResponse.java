package com.offers.app.catalog.api;

public record CategoryResponse(
        Long id,
        String name,
        Long parentId,
        String parentName,
        int sortOrder,
        boolean active
) {
}
