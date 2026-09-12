package com.example.ecommerce.category.service;

import com.example.ecommerce.category.dto.request.CreateCategoryRequest;
import com.example.ecommerce.category.dto.request.UpdateCategoryRequest;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    public CategoryDetailRespone  getCategoryDetail(Long id){
        Category ans = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found."
                ));

        return categoryMapper.toCategoryRespone(ans);
    }
    @Transactional
    public CategoryDetailRespone createCategory(CreateCategoryRequest createCategoryRequest){
        String normalizedName = normalizeName(createCategoryRequest.getName());
        Long parentId = createCategoryRequest.getParentId();
        Category parent = null;
        if(parentId != null){
            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Parent category not found."
                    ));
            if(categoryRepository.existsByNameIgnoreCaseAndParentId(normalizedName, parentId)){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists.");
            }
        } else if(categoryRepository.existsByNameIgnoreCaseAndParentIsNull(normalizedName)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists.");
        }

        String slug = getSlug(normalizedName);
        if(categoryRepository.existsBySlugIgnoreCase(slug)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category slug already exists.");
        }

        Category tmp = Category.builder()
                .name(normalizedName)
                .slug(slug)
                .parent(parent)
                .build();
        Category savedCategory = categoryRepository.save(tmp);
        return categoryMapper.toCategoryRespone(savedCategory);
    }

    @Transactional
    public CategoryDetailRespone updateCategory(Long id, UpdateCategoryRequest updateCategoryRequest){
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Category not found."
                ));

        String normalizedName = normalizeName(updateCategoryRequest.getName());
        String slug = getSlug(normalizedName);
        Long parentId = updateCategoryRequest.getParentId();
        Category parent = null;

        if(parentId != null){
            if(id.equals(parentId)){
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category cannot be its own parent."
                );
            }

            parent = categoryRepository.findById(parentId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.NOT_FOUND,
                            "Parent category not found."
                    ));

            validateNoCycle(id, parentId);

            if(categoryRepository.existsByNameIgnoreCaseAndParentIdAndIdNot(normalizedName, parentId, id)){
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists.");
            }
        } else if(categoryRepository.existsByNameIgnoreCaseAndParentIsNullAndIdNot(normalizedName, id)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category already exists.");
        }

        if(categoryRepository.existsBySlugIgnoreCaseAndIdNot(slug, id)){
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Category slug already exists.");
        }

        category.setName(normalizedName);
        category.setSlug(slug);
        category.setParent(parent);

        return categoryMapper.toCategoryRespone(categoryRepository.save(category));
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

    private void validateNoCycle(Long categoryId, Long parentId){
        Map<Long, Long> parentByCategoryId = new HashMap<>();
        for(CategoryRepository.CategoryTreeView category : categoryRepository.findAllForTree()){
            parentByCategoryId.put(category.getId(), category.getParentId());
        }

        Set<Long> visited = new HashSet<>();
        Long currentId = parentId;
        while(currentId != null){
            if(currentId.equals(categoryId)){
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category parent would create a cycle."
                );
            }
            if(!visited.add(currentId)){
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Category hierarchy already contains a cycle."
                );
            }
            currentId = parentByCategoryId.get(currentId);
        }
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
