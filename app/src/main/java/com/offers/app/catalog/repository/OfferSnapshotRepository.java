package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.OfferSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OfferSnapshotRepository extends JpaRepository<OfferSnapshot, Long> {
}
