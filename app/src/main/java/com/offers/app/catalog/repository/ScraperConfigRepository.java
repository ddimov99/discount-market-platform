package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.ScraperConfig;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScraperConfigRepository extends JpaRepository<ScraperConfig, Long> {

    @EntityGraph(attributePaths = "market")
    List<ScraperConfig> findByEnabledTrue();

    @EntityGraph(attributePaths = "market")
    Optional<ScraperConfig> findWithMarketById(Long id);
}
