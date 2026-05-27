package com.offers.app.catalog.repository;

import com.offers.app.catalog.domain.Category;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByNameAndActiveTrue(String name);

    @EntityGraph(attributePaths = "parent")
    List<Category> findAllByActiveTrueOrderBySortOrderAscNameAsc();

    @Query("""
            select category
            from Category category
            left join fetch category.parent
            where category.active = true
              and not exists (
                  select 1
                  from Category child
                  where child.parent = category
              )
            order by category.sortOrder asc, category.name asc
            """)
    List<Category> findAllActiveLeafCategoriesOrderBySortOrderAscNameAsc();
}
