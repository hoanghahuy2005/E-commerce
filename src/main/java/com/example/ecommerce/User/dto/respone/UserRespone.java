package com.example.ecommerce.user.dto.respone;

import java.time.LocalDateTime;


import com.example.ecommerce.user.entity.Role;
import lombok.AllArgsConstructor;

import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;

@FieldDefaults(level = AccessLevel.PRIVATE)
@AllArgsConstructor
public class UserRespone {
    Long id;
    String name;
    String email;
    Role role;
    LocalDateTime createdAt;
}
