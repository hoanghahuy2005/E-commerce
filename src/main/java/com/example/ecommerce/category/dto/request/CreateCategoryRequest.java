package com.example.ecommerce.category.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@Builder
@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
@NoArgsConstructor
public class CreateCategoryRequest {
    @NotBlank(message = "Tên category không được để trống")
    @Size(max = 100, message = "Tên category không được vượt quá 100 ký tự")
    @Pattern(
            regexp = "^[\\p{L}0-9 -]+$",
            message = "Tên category chỉ được chứa chữ, số, khoảng trắng và dấu -"
    )
    String name;
    String parentId;
}
