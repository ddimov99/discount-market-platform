package com.offers.app.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "scrape_runs")
public class ScrapeRun {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "market_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_scrape_runs_market_id")
    )
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "scraper_config_id",
            foreignKey = @ForeignKey(name = "fk_scrape_runs_scraper_config_id")
    )
    private ScraperConfig scraperConfig;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ScrapeRunStatus status = ScrapeRunStatus.RUNNING;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "started_at", nullable = false, updatable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    @Column(name = "offers_seen", nullable = false)
    private int offersSeen = 0;

    @Column(name = "offers_created", nullable = false)
    private int offersCreated = 0;

    @Column(name = "offers_updated", nullable = false)
    private int offersUpdated = 0;

    @Column(name = "offers_marked_stale", nullable = false)
    private int offersMarkedStale = 0;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    public ScrapeRun(ScraperConfig scraperConfig) {
        this.scraperConfig = scraperConfig;
        this.market = scraperConfig.getMarket();
    }
}
