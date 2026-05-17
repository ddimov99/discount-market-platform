package com.offers.app.catalog.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offers.app.catalog.application.CatalogQueryService;
import com.offers.app.catalog.domain.StockStatus;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogPublicController.class)
class CatalogPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService catalogQueryService;

    @Test
    void returnsOffers() throws Exception {
        when(catalogQueryService.getOffers()).thenReturn(List.of(new OfferResponse(
                1L,
                "Wireless Headphones",
                "https://example.com/products/headphones",
                "https://example.com/images/headphones.jpg",
                "Example Brand",
                "EUR",
                new BigDecimal("99.99"),
                new BigDecimal("59.99"),
                new BigDecimal("40.00"),
                StockStatus.IN_STOCK,
                List.of("last-units"),
                Map.of("source", "scraper"),
                LocalDateTime.parse("2026-05-16T10:00:00"),
                LocalDateTime.parse("2026-05-16T11:00:00"),
                LocalDateTime.parse("2026-05-16T11:05:00"),
                new OfferMarketResponse(2L, "Ozone", "ozone"),
                new OfferCategoryResponse(3L, "Electronics", "electronics")
        )));

        mockMvc.perform(get("/api/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Wireless Headphones"))
                .andExpect(jsonPath("$[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$[0].market.slug").value("ozone"))
                .andExpect(jsonPath("$[0].category.slug").value("electronics"));
    }

    @Test
    void returnsCategories() throws Exception {
        when(catalogQueryService.getCategories()).thenReturn(List.of(new CategoryResponse(
                3L,
                "Electronics",
                "electronics",
                1L,
                "root"
        )));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value("electronics"))
                .andExpect(jsonPath("$[0].parentSlug").value("root"));
    }

    @Test
    void returnsMarkets() throws Exception {
        when(catalogQueryService.getMarkets()).thenReturn(List.of(new MarketResponse(
                2L,
                "Ozone",
                "ozone",
                "https://www.ozone.bg/",
                LocalDateTime.parse("2026-05-16T11:05:00")
        )));

        mockMvc.perform(get("/api/markets"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Ozone"))
                .andExpect(jsonPath("$[0].baseUrl").value("https://www.ozone.bg/"));
    }
}
