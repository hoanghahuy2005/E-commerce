package com.example.ecommerce.category.controller;

import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.service.CategoryService;
import com.example.ecommerce.common.dto.ApiResponse;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class CategoryController {
    CategoryService categoryService;

    @GetMapping("/categories")
    public ApiResponse<List<CategoryRespone>> findAll() {
        return ApiResponse.<List<CategoryRespone>>builder()
                .success(true)
                .message("Lấy danh sách category thành công")
                .data(categoryService.findCategory())
                .build();
    }
}
