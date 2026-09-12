package com.example.ecommerce.category.mapper;

import com.example.ecommerce.category.dto.respone.CategoryDetailRespone;
import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.entity.Category;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class CategoryMapper {
    public CategoryDetailRespone toCategoryRespone(Category category){
        return CategoryDetailRespone.builder()
                .id(category.getId())
                .name(category.getName())
                .slug(category.getSlug())
                .build();
    }
}
