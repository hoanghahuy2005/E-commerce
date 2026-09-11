package com.example.ecommerce.category.dto.respone;


import com.example.ecommerce.category.entity.Category;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
public class CategoryRespone {
    long id;
    String name;
    String slug;
    List<CategoryRespone> children;
}
