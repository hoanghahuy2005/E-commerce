package com.example.ecommerce.vendor.mapper;

import com.example.ecommerce.vendor.dto.respone.VendorRespone;
import com.example.ecommerce.vendor.entity.Vendor;
import org.springframework.stereotype.Component;

@Component
public class VendorMapper {
    public VendorRespone toVendorResponse(Vendor vendor) {
        return VendorRespone.builder()
                .name(vendor.getUser().getName())
                .shopName(vendor.getShopName())
                .phone(vendor.getUser().getPhoneNumber())
                .email(vendor.getUser().getEmail())
                .address(vendor.getUser().getAddress())
                .build();
    }
}
