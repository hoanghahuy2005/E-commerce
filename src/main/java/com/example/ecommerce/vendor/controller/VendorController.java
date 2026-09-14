package com.example.ecommerce.vendor.controller;

import com.example.ecommerce.common.dto.ApiResponse;
import com.example.ecommerce.vendor.dto.request.VendorRequest;
import com.example.ecommerce.vendor.dto.respone.VendorRespone;
import com.example.ecommerce.vendor.service.VendorService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequestMapping("/api")
public class VendorController {
    VendorService vendorService;

    @PostMapping("/auth/register-vendor")
    public ApiResponse<VendorRespone> createVendor(@Valid @RequestBody VendorRequest vendorRequest) {
        return ApiResponse.<VendorRespone>builder()
                .success(true)
                .message("Đăng ký vendor thành công!")
                .data(vendorService.createVendor(vendorRequest))
                .build();
    }

    @GetMapping("/vendors")
    public ApiResponse<List<VendorRespone>> getVendors() {
        return ApiResponse.<List<VendorRespone>>builder()
                .success(true)
                .message("Danh sách vendors:")
                .data(vendorService.getVendors())
                .build();
    }

    @GetMapping("vendors/{id}")
    public ApiResponse<VendorRespone> getVendor(@PathVariable Long id) {
        return ApiResponse.<VendorRespone>builder()
                .success(true)
                .message("Chi tiết Vendor:")
                .data(vendorService.getVendor(id))
                .build();
    }

}
