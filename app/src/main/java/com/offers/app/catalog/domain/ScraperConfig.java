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
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "scraper_configs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_scraper_configs_slug",
                columnNames = "slug"
        )
)
public class ScraperConfig {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "market_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_scraper_configs_market_id")
    )
    private Market market;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 180)
    private String slug;

    @Column(nullable = false)
    private boolean enabled = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "runner_type", nullable = false, length = 32)
    private ScraperRunnerType runnerType = ScraperRunnerType.SCRAPY;

    @Column(name = "project_key", nullable = false, length = 160)
    private String projectKey;

    @Column(name = "spider_name", nullable = false, length = 160)
    private String spiderName;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "start_urls", nullable = false, columnDefinition = "text[]")
    private String[] startUrls = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> arguments = new LinkedHashMap<>();

    @Enumerated(EnumType.STRING)
    @Column(name = "output_format", nullable = false, length = 32)
    private ScraperOutputFormat outputFormat = ScraperOutputFormat.JSONL;

    @Column(name = "interval_minutes", nullable = false)
    private int intervalMinutes;

    @Column(name = "timeout_seconds", nullable = false)
    private int timeoutSeconds;

    @Column(name = "last_started_at")
    private LocalDateTime lastStartedAt;

    @Column(name = "last_finished_at")
    private LocalDateTime lastFinishedAt;

    @Column(name = "last_success_at")
    private LocalDateTime lastSuccessAt;

    @Column(name = "last_error_message", columnDefinition = "text")
    private String lastErrorMessage;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ScraperConfig(
            Market market,
            String name,
            String slug,
            String projectKey,
            String spiderName,
            int intervalMinutes,
            int timeoutSeconds
    ) {
        this.market = market;
        this.name = name;
        this.slug = slug;
        this.projectKey = projectKey;
        this.spiderName = spiderName;
        this.intervalMinutes = intervalMinutes;
        this.timeoutSeconds = timeoutSeconds;
    }
}
