package com.example.ecommerce.category.service;

import com.example.ecommerce.category.dto.request.CreateCategoryRequest;
import com.example.ecommerce.category.dto.respone.CategoryDetailRespone;
import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.entity.Category;
import com.example.ecommerce.category.mapper.CategoryMapper;
import com.example.ecommerce.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CategoryService {
    final CategoryRepository categoryRepository;
    final CategoryMapper categoryMapper;

    public List<CategoryRespone> findCategory(){
        List<CategoryRepository.CategoryTreeView> categories = categoryRepository.findAllForTree();
        Map<Long, CategoryRespone> categoryById = new LinkedHashMap<>();

        for(CategoryRepository.CategoryTreeView category : categories){
            categoryById.put(
                    category.getId(),
                    new CategoryRespone(
                            category.getId(),
                            category.getName(),
                            category.getSlug(),
                            new ArrayList<>()
                    )
            );
        }

        List<CategoryRespone> rootCategories = new ArrayList<>();
        for(CategoryRepository.CategoryTreeView category : categories){
            CategoryRespone currentCategory = categoryById.get(category.getId());
            Long parentId = category.getParentId();

            if(parentId == null){
                rootCategories.add(currentCategory);
                continue;
            }

            CategoryRespone parentCategory = categoryById.get(parentId);
            if(parentCategory != null){
                parentCategory.getChildren().add(currentCategory);
            }
        }

        return rootCategories;
    }
    public CategoryDetailRespone  getCategoryDetail(String id){
        Category ans = categoryRepository.findById(Long.parseLong(id))
                .orElseThrow(() -> new RuntimeException("Category not found."));

        return categoryMapper.toCategoryRespone(ans);
    }
    public CategoryDetailRespone createCategory(CreateCategoryRequest createCategoryRequest){
        String normalizedName = normalizeName(createCategoryRequest.getName());
        Category parent = null;
        if(createCategoryRequest.getParentId() != null){
            parent = categoryRepository.findById(Long.parseLong(createCategoryRequest.getParentId()))
                    .orElseThrow();
            if(categoryRepository.existsByIgnoreCaseNameAndParentId(normalizedName, Long.parseLong(createCategoryRequest.getParentId()))){
                throw new RuntimeException("Category already exists.");
            }
        }
        if(categoryRepository.existsByIgnoreCaseNameAndParentIsNull(normalizedName)){
            throw new RuntimeException("Category already exists.");
        }

        String slug = getSlug(normalizedName);
        if(categoryRepository.existsBySlugIgnoreCase(slug)){
            throw new RuntimeException("Category slug already exists.");
        }

        Category tmp = Category.builder()
                .name(normalizedName)
                .slug(slug)
                .parent(parent)
                .build();
        categoryRepository.save(tmp);
        return categoryMapper.toCategoryRespone(tmp);
    }

    @Transactional
    public void deleteCategory(Long id){
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found."
                ));

        if(categoryRepository.existsByParentId(id)){
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot delete category that has child categories."
            );
        }

        categoryRepository.delete(category);
    }

    public String getSlug(String name){
        return Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replace("đ", "d")
                .replace("Đ", "D")
                .toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-");
    }
    public String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }

        name = name.trim().toLowerCase();

        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
