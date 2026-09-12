package com.example.ecommerce.category.repository;

import com.example.ecommerce.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    @Query("""
            select c.id as id, c.name as name, c.slug as slug, p.id as parentId
            from Category c
            left join c.parent p
            order by c.id
            """)
    List<CategoryTreeView> findAllForTree();

    boolean existsByNameIgnoreCaseAndParentId(String name, Long parentId);
    boolean existsByNameIgnoreCaseAndParentIsNull(String name);
    boolean existsBySlugIgnoreCase(String slug);
    boolean existsByParentId(Long parentId);
    boolean existsByNameIgnoreCaseAndParentIdAndIdNot(String name, Long parentId, Long id);
    boolean existsByNameIgnoreCaseAndParentIsNullAndIdNot(String name, Long id);
    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    interface CategoryTreeView {
        Long getId();
        String getName();
        String getSlug();
        Long getParentId();
    }
}
