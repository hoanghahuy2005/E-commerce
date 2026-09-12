package com.example.ecommerce.category.service;

import com.example.ecommerce.category.dto.request.CreateCategoryRequest;
import com.example.ecommerce.category.dto.respone.CategoryDetailRespone;
import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.entity.Category;
import com.example.ecommerce.category.mapper.CategoryMapper;
import com.example.ecommerce.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    final CategoryRepository categoryRepository;
    final CategoryMapper categoryMapper;

    public List<CategoryRespone> findCategory(){
        List<CategoryRespone> categoryRespones = new ArrayList<>();
        List<Category> categories = categoryRepository.findByParentIsNull();
        for(Category category : categories){
            categoryRespones.add(new CategoryRespone(category.getId(), category.getName(), category.getSlug(), getChildrenCategory(category)));
        }
        return categoryRespones;
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
    public List<CategoryRespone> getChildrenCategory(Category category){
            List<CategoryRespone> categoryRespones = new ArrayList<>();
            List<Category> children = categoryRepository.findByParent(category);
            if(children == null){
                return null;
            }
            else {
                for(Category child : children){
                    CategoryRespone childrenRespone = new CategoryRespone();
                    childrenRespone.setChildren(getChildrenCategory(child));
                    childrenRespone.setId(child.getId());
                    childrenRespone.setName(child.getName());
                    childrenRespone.setSlug(child.getSlug());
                    categoryRespones.add(childrenRespone);

                }
            }
            return categoryRespones;
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
