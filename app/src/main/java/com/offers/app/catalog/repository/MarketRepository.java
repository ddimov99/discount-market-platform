package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.Market;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MarketRepository extends JpaRepository<Market, Long> {

    List<Market> findAllByActiveTrueOrderByNameAsc();
}
