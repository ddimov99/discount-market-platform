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
import java.math.BigDecimal;
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
        name = "offers",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_offers_market_source_key",
                columnNames = {"market_id", "source_product_key"}
        )
)
public class Offer {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "market_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_offers_market_id")
    )
    private Market market;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            foreignKey = @ForeignKey(name = "fk_offers_category_id")
    )
    private Category category;

    @Column(name = "source_product_id", length = 160)
    private String sourceProductId;

    @Column(name = "source_product_key", nullable = false, length = 255)
    private String sourceProductKey;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(name = "product_url", nullable = false, columnDefinition = "text")
    private String productUrl;

    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(length = 160)
    private String brand;

    @Column(nullable = false, length = 3)
    private String currency = "EUR";

    @Column(name = "old_price", precision = 12, scale = 2)
    private BigDecimal oldPrice;

    @Column(name = "sale_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal salePrice;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    @Enumerated(EnumType.STRING)
    @Column(name = "stock_status", nullable = false, length = 32)
    private StockStatus stockStatus = StockStatus.UNKNOWN;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false, columnDefinition = "text[]")
    private String[] labels = new String[0];

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "first_seen_at", nullable = false, updatable = false)
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;

    @Column(name = "last_scraped_at", nullable = false)
    private LocalDateTime lastScrapedAt;

    @Column(nullable = false)
    private boolean stale = false;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Offer(Market market, String sourceProductKey, String title, String productUrl) {
        this.market = market;
        this.sourceProductKey = sourceProductKey;
        this.title = title;
        this.productUrl = productUrl;
    }
}
