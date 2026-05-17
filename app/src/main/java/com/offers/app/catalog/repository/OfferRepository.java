package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.Market;
import com.offers.app.catalog.domain.Offer;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OfferRepository extends JpaRepository<Offer, Long> {

    Optional<Offer> findByMarketAndSourceProductKey(Market market, String sourceProductKey);

    List<Offer> findByMarketAndSourceProductKeyIn(Market market, Collection<String> sourceProductKeys);

    List<Offer> findByMarketAndStaleFalse(Market market);

    @Query("""
            select offer
            from Offer offer
            join fetch offer.market market
            left join fetch offer.category category
            where offer.stale = false
              and market.active = true
            order by offer.discountPercent desc, offer.lastSeenAt desc
            """)
    List<Offer> findPublicOffers();
}
