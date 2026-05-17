package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.ScrapeRun;
import com.offers.app.catalog.domain.ScrapeRunStatus;
import com.offers.app.catalog.domain.ScraperConfig;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScrapeRunRepository extends JpaRepository<ScrapeRun, Long> {

    boolean existsByScraperConfigAndStatus(ScraperConfig scraperConfig, ScrapeRunStatus status);

    @EntityGraph(attributePaths = {"market", "scraperConfig", "scraperConfig.market"})
    Optional<ScrapeRun> findWithConfigAndMarketById(Long id);
}
