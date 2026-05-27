package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    List<Offer> findByMarketAndSourceProductKeyIn(Market market, Collection<String> sourceProductKeys);

    List<Offer> findByMarketAndStaleFalse(Market market);

    @Query("""
            select offer
            from Offer offer
            join fetch offer.market market
            join fetch offer.category category
            left join fetch category.parent parent
            where offer.stale = false
              and market.active = true
            order by offer.discountPercent desc, offer.lastSeenAt desc
            """)
    List<Offer> findPublicOffers();

    @Query("""
            select offer
            from Offer offer
            join fetch offer.market market
            join fetch offer.category category
            left join fetch category.parent parent
            where offer.stale = false
              and market.active = true
              and lower(offer.brand) = lower(:brand)
            order by offer.discountPercent desc, offer.lastSeenAt desc
            """)
    List<Offer> findPublicOffersByBrand(@Param("brand") String brand);

    @Query("""
            select offer
            from Offer offer
            join fetch offer.market market
            join fetch offer.category category
            left join fetch category.parent parent
            where offer.stale = false
              and market.active = true
              and category.id = :categoryId
              and (:brand is null or lower(offer.brand) = lower(:brand))
            order by offer.discountPercent desc, offer.lastSeenAt desc
            """)
    List<Offer> findPublicOffersByCategoryId(
            @Param("categoryId") Long categoryId,
            @Param("brand") String brand
    );
}
