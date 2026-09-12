package com.example.ecommerce.category.controller;

import com.example.ecommerce.category.dto.request.CreateCategoryRequest;
import com.example.ecommerce.category.dto.request.UpdateCategoryRequest;
import com.example.ecommerce.category.dto.respone.CategoryDetailRespone;
import com.example.ecommerce.category.dto.respone.CategoryRespone;
import com.example.ecommerce.category.service.CategoryService;
import com.example.ecommerce.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/admin/categories/{id}")
    public ApiResponse<CategoryDetailRespone> findById(@PathVariable Long id) {
        CategoryDetailRespone categoryDetailRespone = categoryService.getCategoryDetail(id);
        return ApiResponse.<CategoryDetailRespone>builder()
                .success(true)
                .message("Chi tiết category đã chọn.")
                .data(categoryDetailRespone)
                .build();
    }
    @PostMapping("/admin/categories")
    public ApiResponse<CategoryDetailRespone> createNewCategories(@Valid @RequestBody CreateCategoryRequest createCategoryRequest) {
        CategoryDetailRespone categoryDetailRespone = categoryService.createCategory(createCategoryRequest);
        return ApiResponse.<CategoryDetailRespone>builder()
                .success(true)
                .message("Đã thêm category mới.")
                .data(categoryDetailRespone)
                .build();
    }

    @PutMapping("/admin/categories/{id}")
    public ApiResponse<CategoryDetailRespone> updateCategory(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCategoryRequest updateCategoryRequest) {
        return ApiResponse.<CategoryDetailRespone>builder()
                .success(true)
                .message("Đã cập nhật category thành công.")
                .data(categoryService.updateCategory(id, updateCategoryRequest))
                .build();
    }

    @DeleteMapping("/admin/categories/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Đã xóa category thành công.")
                .build();
    }
}
