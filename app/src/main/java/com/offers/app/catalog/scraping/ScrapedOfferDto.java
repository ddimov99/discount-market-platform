package com.offers.app.catalog.scraping;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ScrapedOfferDto(
        @JsonProperty("product_id")
        String productId,

        @JsonProperty("title")
        String title,

        @JsonProperty("product_url")
        String productUrl,

        @JsonProperty("image_url")
        String imageUrl,

        @JsonProperty("brand")
        String brand,

        @JsonProperty("currency")
        String currency,

        @JsonProperty("old_price")
        BigDecimal oldPrice,

        @JsonProperty("sale_price")
        BigDecimal salePrice,

        @JsonProperty("discount_percent")
        BigDecimal discountPercent,

        @JsonProperty("labels")
        List<String> labels,

        @JsonProperty("stock_status")
        String stockStatus,

        @JsonProperty("category")
        String category,

        @JsonProperty("subcategory")
        String subcategory,

        @JsonProperty("discount_label")
        String discountLabel,

        @JsonProperty("sales_type")
        String salesType,

        @JsonProperty("attribute_set")
        String attributeSet,

        @JsonProperty("is_last_units")
        Boolean lastUnits,

        @JsonProperty("source_url")
        String sourceUrl
) {
}
