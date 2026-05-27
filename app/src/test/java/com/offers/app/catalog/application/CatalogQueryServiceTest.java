package com.offers.app.catalog.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.offers.app.catalog.api.OfferResponse;
import com.offers.app.catalog.domain.Category;
import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import com.offers.app.catalog.domain.StockStatus;
import com.offers.app.catalog.repository.CategoryRepository;
import com.offers.app.catalog.repository.MarketRepository;
import com.offers.app.catalog.repository.OfferRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CatalogQueryServiceTest {

    private final OfferRepository offerRepository = org.mockito.Mockito.mock(OfferRepository.class);
    private final CategoryRepository categoryRepository = org.mockito.Mockito.mock(CategoryRepository.class);
    private final MarketRepository marketRepository = org.mockito.Mockito.mock(MarketRepository.class);

    private final CatalogQueryService service = new CatalogQueryService(
            offerRepository,
            categoryRepository,
            marketRepository
    );

    @Test
    void usesCategoryAndBrandForOfferSearch() {
        when(offerRepository.findPublicOffersByCategoryId(3L, "Apple")).thenReturn(List.of());

        assertThat(service.getOffers(3L, " Apple ")).isEmpty();

        verify(offerRepository).findPublicOffersByCategoryId(3L, "Apple");
        verify(offerRepository, never()).findPublicOffers();
    }

    @Test
    void usesBrandOnlySearchWhenNoCategoryFilterIsPresent() {
        when(offerRepository.findPublicOffersByBrand("Samsung")).thenReturn(List.of());

        assertThat(service.getOffers(null, " Samsung ")).isEmpty();

        verify(offerRepository).findPublicOffersByBrand("Samsung");
        verify(offerRepository, never()).findPublicOffers();
    }

    @Test
    void publicOfferQueriesExcludeNullCategoryOffers() {
        Category category = new Category("Смартфони", null, 110);
        Offer resolvedOffer = offer("Resolved", category);
        Offer unresolvedOffer = offer("Unresolved", null);
        when(offerRepository.findPublicOffers()).thenReturn(List.of(unresolvedOffer, resolvedOffer));

        List<OfferResponse> offers = service.getOffers();

        assertThat(offers)
                .extracting(OfferResponse::title)
                .containsExactly("Resolved");
    }

    private Offer offer(String title, Category category) {
        Market market = new Market("Ozone", "ozone", "https://www.ozone.bg/");
        Offer offer = new Offer(market, title, title, "https://example.com/" + title);
        offer.setCategory(category);
        offer.setCurrency("EUR");
        offer.setSalePrice(new BigDecimal("100.00"));
        offer.setDiscountPercent(new BigDecimal("25.00"));
        offer.setStockStatus(StockStatus.IN_STOCK);
        offer.setLabels(new String[0]);
        offer.setMetadata(Map.of());
        offer.setLastSeenAt(LocalDateTime.parse("2026-05-16T11:00:00"));
        offer.setLastScrapedAt(LocalDateTime.parse("2026-05-16T11:05:00"));
        return offer;
    }
}
