package com.example.ecommerce.auth.controller;

import com.example.ecommerce.auth.dto.request.LoginDTO;
import com.example.ecommerce.auth.dto.request.RegisterDTO;
import com.example.ecommerce.auth.service.AuthService;
import com.example.ecommerce.common.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/login")
    public ApiResponse<Void> login(@RequestBody LoginDTO loginDTO) {

        return ApiResponse.<Void>builder()
                .success(true)
                .message("ok")
                .build();
    }
    @PostMapping("/register")
    public ApiResponse<Void> register(@RequestBody RegisterDTO registerDTO) {
        authService.registerCustomer(registerDTO);
        return ApiResponse.<Void>builder()
                .success(true)
                .message("Đăng ký thành công!")
                .build();
    }
}
