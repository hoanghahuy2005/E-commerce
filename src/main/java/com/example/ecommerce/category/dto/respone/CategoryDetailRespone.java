package com.example.ecommerce.category.dto.respone;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class CategoryDetailRespone {
    long id;
    String name;
    String slug;
}
