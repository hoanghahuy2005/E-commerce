package com.example.ecommerce.vendor.dto.respone;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.FieldDefaults;

@Builder
@Data
@FieldDefaults(level = AccessLevel.PRIVATE)
public class VendorRespone {
    String name;
    String shopName;
    String address;
    String email;
    String phone;
}
