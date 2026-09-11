package com.example.ecommerce.category.service;

import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.entity.Category;
import com.example.ecommerce.category.repository.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {
    final CategoryRepository categoryRepository;
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
    public List<CategoryRespone> findCategory(){
        List<CategoryRespone> categoryRespones = new ArrayList<>();
        List<Category> categories = categoryRepository.findByParentIsNull();
        for(Category category : categories){
            categoryRespones.add(new CategoryRespone(category.getId(), category.getName(), category.getSlug(), getChildrenCategory(category)));
        }
        return categoryRespones;
    }
}
