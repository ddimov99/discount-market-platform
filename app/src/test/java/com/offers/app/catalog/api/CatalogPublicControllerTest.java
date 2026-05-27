package com.offers.app.catalog.api;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.offers.app.catalog.application.CatalogQueryService;
import com.offers.app.catalog.domain.StockStatus;
import com.offers.app.config.CorsConfig;
import com.offers.app.config.CorsProperties;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(CatalogPublicController.class)
@Import(CorsConfig.class)
@EnableConfigurationProperties(CorsProperties.class)
@TestPropertySource(properties = "app.cors.allowed-origins=http://localhost:5173")
class CatalogPublicControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogQueryService catalogQueryService;

    @Test
    void returnsOffers() throws Exception {
        when(catalogQueryService.getOffers(null, null)).thenReturn(List.of(new OfferResponse(
                1L,
                "Wireless Headphones",
                "https://example.com/products/headphones",
                "https://example.com/images/headphones.jpg",
                "Example Brand",
                "Electronics",
                "Headphones",
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
                new OfferCategoryResponse(3L, "Electronics", 1L, "Root")
        )));

        mockMvc.perform(get("/api/offers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Wireless Headphones"))
                .andExpect(jsonPath("$[0].stockStatus").value("IN_STOCK"))
                .andExpect(jsonPath("$[0].sourceCategory").value("Electronics"))
                .andExpect(jsonPath("$[0].sourceSubcategory").value("Headphones"))
                .andExpect(jsonPath("$[0].market.slug").value("ozone"))
                .andExpect(jsonPath("$[0].category.name").value("Electronics"))
                .andExpect(jsonPath("$[0].category.parentName").value("Root"));
    }

    @Test
    void returnsCategories() throws Exception {
        when(catalogQueryService.getCategories()).thenReturn(List.of(new CategoryResponse(
                3L,
                "Electronics",
                1L,
                "Root",
                100,
                true
        )));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Electronics"))
                .andExpect(jsonPath("$[0].parentName").value("Root"));
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

    @Test
    void allowsConfiguredFrontendOrigin() throws Exception {
        mockMvc.perform(options("/api/offers")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS"));
    }
}
