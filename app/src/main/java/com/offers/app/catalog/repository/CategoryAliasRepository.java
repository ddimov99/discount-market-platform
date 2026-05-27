package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.CategoryAlias;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryAliasRepository extends JpaRepository<CategoryAlias, Long> {

    @EntityGraph(attributePaths = "category")
    List<CategoryAlias> findAll();
}
