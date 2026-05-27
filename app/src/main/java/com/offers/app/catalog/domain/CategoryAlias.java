package com.offers.app.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "category_aliases",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_category_aliases_category_normalized",
                columnNames = {"category_id", "normalized_alias"}
        )
)
public class CategoryAlias {

    @Id
    @Setter(AccessLevel.NONE)
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "category_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_category_aliases_category_id")
    )
    private Category category;

    @Column(name = "normalized_alias", nullable = false, length = 255)
    private String normalizedAlias;

    @Column(nullable = false, length = 50)
    private String source = "generated";

    @Column(nullable = false)
    private boolean active = true;

    @CreationTimestamp
    @Setter(AccessLevel.NONE)
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public CategoryAlias(Category category, String normalizedAlias, String source) {
        this.category = category;
        this.normalizedAlias = normalizedAlias;
        this.source = source;
    }
}
