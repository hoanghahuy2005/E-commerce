package com.example.ecommerce.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.springframework.stereotype.Component;

@Builder
@Data
@AllArgsConstructor
@NoArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterDTO {
    @NotBlank(message = "Username không được để trống")
    String username;
    @NotBlank(message = "password không được để trống")
    String password;
    @NotBlank(message = "confirmPassword không được để trống")
    String confirmPassword;
    String email;
}
