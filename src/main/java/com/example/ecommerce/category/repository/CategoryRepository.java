package com.example.ecommerce.category.repository;

import com.example.ecommerce.category.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    List<Category> findByParentIsNull();
    List<Category> findByParent(Category parent);
    boolean existsByIgnoreCaseNameAndParentId(String name, Long id);
    boolean existsByIgnoreCaseNameAndParentIsNull(String name);
    boolean existsBySlugIgnoreCase(String slug);
}
