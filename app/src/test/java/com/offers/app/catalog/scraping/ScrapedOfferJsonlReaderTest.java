package com.offers.app.catalog.scraping;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScrapedOfferJsonlReaderTest {

    @TempDir
    private Path tempDir;

    private final ScrapedOfferJsonlReader reader = new ScrapedOfferJsonlReader(new ObjectMapper());

    @Test
    void readsScrapedOffersFromJsonl() throws IOException {
        Path jsonl = tempDir.resolve("offers.jsonl");
        Files.writeString(jsonl, """
                {"scraped_at":"2026-05-15T08:08:10+00:00","source_url":"https://www.ozone.bg/","product_id":"LTLP0010113N","title":"Laptop","product_url":"https://www.ozone.bg/product/laptop","image_url":"https://cdn.example/laptop.jpg","brand":"Lenovo","currency":"EUR","old_price":599.0,"sale_price":499.0,"discount_percent":17.0,"discount_label":"-17%","labels":["-17%"],"stock":1,"stock_status":"in_stock","is_last_units":false,"category":"Laptops","subcategory":"Office laptops","sales_type":"Laptops","attribute_set":"Electronics"}

                {"source_url":"https://www.ozone.bg/","product_id":"VGCN0000295N","title":"Console","product_url":"https://www.ozone.bg/product/console","image_url":null,"currency":"EUR","old_price":529.0,"sale_price":489.0,"discount_percent":8.0,"discount_label":"-8%","labels":["-8%","installment"],"stock_status":"in_stock","is_last_units":true,"category":"Consoles","subcategory":"Nintendo","sales_type":"Consoles","attribute_set":"Game consoles"}
                """);

        List<ScrapedOfferDto> offers = reader.read(jsonl);

        assertThat(offers).hasSize(2);
        ScrapedOfferDto first = offers.getFirst();
        assertThat(first.productId()).isEqualTo("LTLP0010113N");
        assertThat(first.title()).isEqualTo("Laptop");
        assertThat(first.productUrl()).isEqualTo("https://www.ozone.bg/product/laptop");
        assertThat(first.imageUrl()).isEqualTo("https://cdn.example/laptop.jpg");
        assertThat(first.brand()).isEqualTo("Lenovo");
        assertThat(first.currency()).isEqualTo("EUR");
        assertThat(first.oldPrice()).isEqualByComparingTo(new BigDecimal("599.0"));
        assertThat(first.salePrice()).isEqualByComparingTo(new BigDecimal("499.0"));
        assertThat(first.discountPercent()).isEqualByComparingTo(new BigDecimal("17.0"));
        assertThat(first.labels()).containsExactly("-17%");
        assertThat(first.stockStatus()).isEqualTo("in_stock");
        assertThat(first.category()).isEqualTo("Laptops");
        assertThat(first.subcategory()).isEqualTo("Office laptops");
        assertThat(first.discountLabel()).isEqualTo("-17%");
        assertThat(first.salesType()).isEqualTo("Laptops");
        assertThat(first.attributeSet()).isEqualTo("Electronics");
        assertThat(first.lastUnits()).isFalse();
        assertThat(first.sourceUrl()).isEqualTo("https://www.ozone.bg/");

        assertThat(offers.get(1).lastUnits()).isTrue();
        assertThat(offers.get(1).labels()).containsExactly("-8%", "installment");
    }

    @Test
    void reportsInvalidJsonlLineNumber() throws IOException {
        Path jsonl = tempDir.resolve("bad-offers.jsonl");
        Files.writeString(jsonl, """
                {"product_id":"ok","title":"OK"}
                not-json
                """);

        assertThatIOException()
                .isThrownBy(() -> reader.read(jsonl))
                .withMessageContaining("bad-offers.jsonl at line 2");
    }
}
