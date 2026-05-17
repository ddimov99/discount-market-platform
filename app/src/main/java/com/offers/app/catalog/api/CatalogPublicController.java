package com.offers.app.catalog.api;

import com.offers.app.catalog.application.CatalogQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CatalogPublicController {

    private final CatalogQueryService catalogQueryService;

    @GetMapping("/offers")
    public List<OfferResponse> offers() {
        return catalogQueryService.getOffers();
    }

    @GetMapping("/categories")
    public List<CategoryResponse> categories() {
        return catalogQueryService.getCategories();
    }

    @GetMapping("/markets")
    public List<MarketResponse> markets() {
        return catalogQueryService.getMarkets();
    }
}
