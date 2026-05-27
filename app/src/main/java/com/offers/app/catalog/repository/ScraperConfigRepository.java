package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.ScraperConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ScraperConfigRepository extends JpaRepository<ScraperConfig, Long> {

    @EntityGraph(attributePaths = "market")
    @Query("select config from ScraperConfig config")
    List<ScraperConfig> findAllWithMarket();

    @EntityGraph(attributePaths = "market")
    Optional<ScraperConfig> findWithMarketById(Long id);
}
